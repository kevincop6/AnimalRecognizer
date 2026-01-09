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

    // 👤 ID del usuario a visualizar
    private var perfilUsuarioId: String? = null

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

        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/usuarios/profile_view.php"

        val body = FormBody.Builder()
            .add("token", token)
            .add("pagina", page.toString())
            .add("usuario_id", perfilUsuarioId!!)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
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
                        showError(
                            "Error",
                            "Error del servidor (${response.code})"
                        )
                        return@runOnUiThread
                    }

                    try {
                        val json = JSONObject(bodyStr)
                        renderProfile(json)
                    } catch (e: Exception) {
                        showError("Error", "Respuesta inválida del servidor")
                    }
                }
            }
        })
    }

    // --------------------------------------------------
    // RENDER PERFIL + CONTADORES
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
                "Este usuario aún no ha agregado una biografía."

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

        // ---------- PUBLICACIONES ----------
        val publicaciones = json.optJSONArray("publicaciones")
        items.clear()

        if (publicaciones != null) {
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
    // RECYCLER VIEW
    // --------------------------------------------------
    private fun setupRecyclerView() {

        val rv = binding.galleryGrid
        rv.layoutManager = GridLayoutManager(this, 3)

        rv.adapter = GalleryAdapter(items) { item ->

            val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                putExtra("avistamiento_id", item.id) // 👈 el ID del post
            }

            startActivity(intent)
        }

        rv.addItemDecoration(object : RecyclerView.ItemDecoration() {
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
