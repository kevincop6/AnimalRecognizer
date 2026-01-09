package com.ulpro.animalrecognizer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.bumptech.glide.Glide
import com.ulpro.animalrecognizer.databinding.FragmentProfileBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private val client = OkHttpClient()

    private val items = mutableListOf<GalleryItem>()
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadProfile(1)

        // Logout
        binding.logOutButton.setOnClickListener {
            logout()
        }
    }

    // --------------------------------------------------
    // API PERFIL
    // --------------------------------------------------
    private fun loadProfile(page: Int) {
        val token = TokenStore.getToken(requireContext())
        if (token.isNullOrBlank()) return

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
                showError("Error de red", e.message ?: "Error")
            }

            override fun onResponse(call: Call, response: Response) {
                val json = JSONObject(response.body?.string() ?: return)
                requireActivity().runOnUiThread {
                    renderProfile(json)
                }
            }
        })
    }

    // --------------------------------------------------
    // RENDER PERFIL + PUBLICACIONES
    // --------------------------------------------------
    private fun renderProfile(json: JSONObject) {

        val perfil = json.getJSONObject("perfil")

        // ---------- DATOS DEL PERFIL ----------
        binding.userName.text = "@${perfil.optString("usuario")}"
        binding.userOccupation.text = perfil.optString("nombre")
        binding.userLikes.text = perfil.optInt("likes", 0).toString()
        binding.statFollowers.text = perfil.optInt("seguidores", 0).toString()

        val bio = perfil.optString("bio").trim()
        binding.userBio.text =
            if (bio.isNotEmpty()) bio
            else "Este usuario aún no ha agregado una biografía."

        Glide.with(this)
            .load(perfil.optString("foto_perfil"))
            .placeholder(R.drawable.ic_default_profile)
            .error(R.drawable.ic_default_profile)
            .into(binding.profileImage)

        // ---------- PUBLICACIONES ----------
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

        // ---------- CONTADOR DE POSTS ----------
        val paginacion = json.getJSONObject("paginacion")
        binding.statPosts.text =
            paginacion.optInt("total_publicaciones", 0).toString()

        // ---------- PAGINACIÓN ----------
        currentPage = paginacion.getInt("pagina_actual")
        totalPages = paginacion.getInt("total_paginas")
    }

    // --------------------------------------------------
    // RECYCLER VIEW
    // --------------------------------------------------
    private fun setupRecyclerView() {
        binding.galleryGrid.layoutManager = GridLayoutManager(requireContext(), 3)

        binding.galleryGrid.adapter = GalleryAdapter(items) { item ->

            val intent = Intent(
                requireContext(),
                FullScreenImageActivity::class.java
            ).apply {
                putExtra("avistamiento_id", item.id) // 👈 ID del post
            }

            startActivity(intent)
        }

        binding.galleryGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (!isLoading && currentPage < totalPages) {
                    if (lm.childCount + lm.findFirstVisibleItemPosition() >= lm.itemCount) {
                        loadProfile(currentPage + 1)
                    }
                }
            }
        })
    }

    // --------------------------------------------------
    private fun logout() {
        val ctx = requireContext()

        TokenStore.clearToken(ctx)
        UserPrefs.clear(ctx)

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun showError(title: String, msg: String) {
        requireActivity().runOnUiThread {
            SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE)
                .setTitleText(title)
                .setContentText(msg)
                .show()
        }
    }
}

