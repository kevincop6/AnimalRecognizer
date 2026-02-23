package com.ulpro.animalrecognizer

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.bumptech.glide.Glide
import com.ulpro.animalrecognizer.databinding.ActivityProfileBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val client = OkHttpClient()

    private val items = mutableListOf<GalleryItem>()

    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    // 👤 Usuario visualizado
    private var perfilUsuarioId: String? = null

    // 👥 Estado seguimiento
    private var esPropietario = true      // por defecto TRUE (seguridad)
    private var siguiendo = false
    private var likeado = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --------------------------------------------------
        // RECIBIR ID DE USUARIO
        // --------------------------------------------------
        perfilUsuarioId = intent.getStringExtra("open_profile_user_id")

        if (perfilUsuarioId.isNullOrBlank()) {
            showError("Error", "Usuario no válido")
            finish()
            return
        }

        setupRecyclerView()
        loadProfile(1)
    }

    // --------------------------------------------------
    // PETICIÓN AL API
    // --------------------------------------------------
    private fun loadProfile(page: Int) {

        if (isLoading) return

        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            showError("Error", "Token no disponible")
            return
        }

        isLoading = true

        val request = Request.Builder()
            .url(ServerConfig.BASE_URL.trimEnd('/') + "/api/usuarios/profile_view.php")
            .post(
                FormBody.Builder()
                    .add("token", token)
                    .add("pagina", page.toString())
                    .add("usuario_id", perfilUsuarioId!!)
                    .build()
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    isLoading = false
                    showError("Error de red", e.message ?: "No se pudo conectar")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()

                runOnUiThread {
                    isLoading = false

                    if (!response.isSuccessful || bodyStr.isNullOrBlank()) {
                        showError("Error", "Error del servidor (${response.code})")
                        return@runOnUiThread
                    }

                    try {
                        renderProfile(JSONObject(bodyStr))
                    } catch (e: Exception) {
                        showError("Error", "Respuesta inválida del servidor")
                    }
                }
            }
        })
    }

    // --------------------------------------------------
    // RENDER PERFIL + CONTADORES + FOLLOW
    // --------------------------------------------------
    private fun renderProfile(json: JSONObject) {

        val perfil = json.getJSONObject("perfil")

        // ---------- DATOS PRINCIPALES ----------
        binding.userName.text = "@${perfil.optString("usuario")}"
        binding.userOccupation.text = perfil.optString("nombre")

        val bio = perfil.optString("bio").trim()
        binding.userBio.text =
            if (bio.isNotEmpty())
                bio
            else
                ""

        Glide.with(this)
            .load(perfil.optString("foto_perfil"))
            .placeholder(R.drawable.ic_default_profile)
            .error(R.drawable.ic_default_profile)
            .into(binding.profileImage)

        // ---------- CONTADORES ----------
        binding.userLikes.text =
            perfil.optInt("likes", 0).toString()

        binding.statFollowers.text =
            perfil.optInt("seguidores", 0).toString()

        val paginacion = json.optJSONObject("paginacion")
        binding.statPosts.text =
            paginacion?.optInt("total_publicaciones", 0)?.toString() ?: "0"

        // ---------- ESTADO PROPIETARIO / FOLLOW ----------
        esPropietario = perfil.optBoolean("es_propietario", true)
        siguiendo = perfil.optBoolean("siguiendo", false)
        likeado = perfil.optBoolean("likeado", false)
        if (!esPropietario) {
            // 🔓 PERFIL DE OTRO USUARIO
            binding.followContainer.visibility = View.VISIBLE
            updateFollowButton()
            updateLikeButton()
            binding.followContainer.setOnClickListener {
                toggleInteraccion("follow")
            }
            binding.likeContainer.setOnClickListener {
                toggleInteraccion("like")
            }
        } else {
            // 🔒 MI PROPIO PERFIL
            binding.followContainer.visibility = View.GONE
        }

        // ---------- PUBLICACIONES ----------
        items.clear()
        json.optJSONArray("publicaciones")?.let { publicaciones ->
            for (i in 0 until publicaciones.length()) {
                val p = publicaciones.getJSONObject(i)
                items.add(
                    GalleryItem(
                        id = p.getInt("id"),
                        titulo = p.getString("titulo"),
                        imageUrl = p.getString("imagen"),
                        descripcion = p.getString("descripcion")
                    )
                )
            }
        }

        binding.galleryGrid.adapter?.notifyDataSetChanged()

        // ---------- PAGINACIÓN ----------
        if (paginacion != null) {
            currentPage = paginacion.optInt("pagina_actual", 1)
            totalPages = paginacion.optInt("total_paginas", 1)
        }
    }

    // --------------------------------------------------
    // UI SEGUIR / SIGUIENDO
    // --------------------------------------------------
    private fun updateFollowButton() {
        if (siguiendo) {
            binding.btnFollow.setImageResource(R.drawable.ic_follow_check_24dp)
            binding.txtFollow.text = "Siguiendo"
        } else {
            binding.btnFollow.setImageResource(R.drawable.ic_follow_add_24dp)
            binding.txtFollow.text = "Seguir"
        }
    }
    // --------------------------------------------------
// UI LIKE / NO LIKE
// --------------------------------------------------
    private fun updateLikeButton() {
        if (likeado) {
            binding.btnLike.setImageResource(R.drawable.ic_favorite_24dp)
            binding.txtLike.text = "Te gusta"
        } else {
            binding.btnLike.setImageResource(R.drawable.ic_favorite_border_24dp)
            binding.txtLike.text = "Me gusta"
        }
    }
    // --------------------------------------------------
    // TOGGLE Interaccion
    // --------------------------------------------------
    private fun toggleInteraccion(accion: String) {

        val token = TokenStore.getToken(this) ?: return
        val usuarioObjetivoId = perfilUsuarioId ?: return

        val request = Request.Builder()
            .url(ServerConfig.BASE_URL.trimEnd('/') + "/api/usuarios/toggle_interaccion.php")
            .post(
                FormBody.Builder()
                    .add("token", token)
                    .add("usuario_objetivo_id", usuarioObjetivoId)
                    .add("accion", accion) // "follow" o "like"
                    .build()
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showError("Error", "No se pudo actualizar la interacción")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()

                runOnUiThread {
                    try {
                        if (!response.isSuccessful || bodyStr.isNullOrBlank()) {
                            showError("Error", "Error del servidor (${response.code})")
                            return@runOnUiThread
                        }

                        val json = JSONObject(bodyStr)
                        val estado = json.optBoolean("estado", false)

                        when (accion) {

                            "follow" -> {
                                siguiendo = estado

                                // Si el backend devuelve el conteo (ideal), úsalo
                                if (json.has("seguidores")) {
                                    binding.statFollowers.text = json.optInt("seguidores", 0).toString()
                                } else {
                                    // Fallback: ajustar visualmente (opcional)
                                    val actuales = binding.statFollowers.text.toString().toIntOrNull() ?: 0
                                    val nuevos = if (estado) actuales + 1 else maxOf(0, actuales - 1)
                                    binding.statFollowers.text = nuevos.toString()
                                }

                                updateFollowButton()
                            }

                            "like" -> {
                                likeado = estado

                                // Si el backend devuelve el conteo (ideal), úsalo
                                if (json.has("likes")) {
                                    binding.userLikes.text = json.optInt("likes", 0).toString()
                                } else {
                                    // Fallback: ajustar visualmente (opcional)
                                    val actuales = binding.userLikes.text.toString().toIntOrNull() ?: 0
                                    val nuevos = if (estado) actuales + 1 else maxOf(0, actuales - 1)
                                    binding.userLikes.text = nuevos.toString()
                                }

                                updateLikeButton()
                            }
                        }

                    } catch (_: Exception) {
                        // Respuesta inválida => no crashea
                    }
                }
            }
        })
    }



    // --------------------------------------------------
    // RECYCLER VIEW
    // --------------------------------------------------
    private fun setupRecyclerView() {

        binding.galleryGrid.layoutManager = GridLayoutManager(this, 3)

        binding.galleryGrid.adapter = GalleryAdapter(items) { item ->
            startActivity(
                Intent(this, FullScreenImageActivity::class.java)
                    .putExtra("avistamiento_id", item.id)
            )
        }

        binding.galleryGrid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.set(4, 4, 4, 4)
            }
        })
    }

    // --------------------------------------------------
    // ERROR UI
    // --------------------------------------------------
    private fun showError(title: String, msg: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(msg)
            .setConfirmText("Cerrar")
            .show()
    }
}
