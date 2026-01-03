package com.ulpro.animalrecognizer

import android.content.Context
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadProfile(1)

        // 🔐 CERRAR SESIÓN (SOLO SI EXISTE EN EL XML)
        binding.logOutButton.setOnClickListener {
            cerrarSesion()
        }
    }

    // --------------------------------------------------
    // CERRAR SESIÓN (Activity)
    // --------------------------------------------------
    private fun cerrarSesion() {
        getSharedPreferences("userSession", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // --------------------------------------------------
    private fun loadProfile(page: Int) {
        val token = TokenStore.getToken(this) ?: return

        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/usuarios/profile_view.php"

        val body = FormBody.Builder()
            .add("token", token)
            .add("pagina", page.toString())
            .build()

        client.newCall(
            Request.Builder().url(url).post(body).build()
        ).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showError("Error", e.message ?: "Error de red")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: return)
                runOnUiThread { renderProfile(json) }
            }
        })
    }

    private fun renderProfile(json: JSONObject) {
        val perfil = json.getJSONObject("perfil")

        binding.userName.text = perfil.optString("nombre")
        binding.userOccupation.text = "@${perfil.optString("usuario")}"
        binding.userLikes.text = "❤️ ${perfil.optInt("likes")} likes"

        val bio = perfil.optString("bio").trim()
        binding.userBio.text =
            if (bio.isNotEmpty()) bio
            else "Este usuario aún no ha agregado una biografía."

        Glide.with(this)
            .load(perfil.optString("foto_perfil"))
            .placeholder(R.drawable.ic_default_profile)
            .error(R.drawable.ic_default_profile)
            .into(binding.profileImage)

        val publicaciones = json.getJSONArray("publicaciones")
        items.clear()

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

        binding.galleryGrid.adapter?.notifyDataSetChanged()

        val pag = json.getJSONObject("paginacion")
        currentPage = pag.getInt("pagina_actual")
        totalPages = pag.getInt("total_paginas")
    }

    private fun setupRecyclerView() {
        val rv = binding.galleryGrid
        rv.layoutManager = GridLayoutManager(this, 3)

        rv.adapter = GalleryAdapter(items) { item ->
            ImageDataStore.imageList = items
            val intent = Intent(this, FullScreenImageActivity::class.java)
            intent.putExtra("currentPosition", items.indexOf(item))
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

    private fun showError(title: String, msg: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(msg)
            .show()
    }
}
