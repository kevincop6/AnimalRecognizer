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

        if (!esPropietario) {
            // 🔓 PERFIL DE OTRO USUARIO
            binding.followContainer.visibility = View.VISIBLE
            updateFollowButton()

            binding.followContainer.setOnClickListener {
                toggleFollow()
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
    // TOGGLE FOLLOW
    // --------------------------------------------------
    private fun toggleFollow() {

        val token = TokenStore.getToken(this) ?: return

        val request = Request.Builder()
            .url(ServerConfig.BASE_URL.trimEnd('/') + "/api/usuarios/toggle_follow.php")
            .post(
                FormBody.Builder()
                    .add("token", token)
                    .add("usuario_id", perfilUsuarioId!!)
                    .build()
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showError("Error", "No se pudo actualizar el seguimiento")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: return

                runOnUiThread {
                    try {
                        val json = JSONObject(bodyStr)
                        siguiendo = json.optBoolean("siguiendo", siguiendo)

                        binding.statFollowers.text =
                            json.optInt(
                                "seguidores",
                                binding.statFollowers.text.toString().toInt()
                            ).toString()

                        updateFollowButton()
                    } catch (_: Exception) {
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
