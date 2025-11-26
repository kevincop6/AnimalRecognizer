package com.ulpro.animalrecognizer

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.content.Intent

class UsuariosFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsersAdapterFragment
    private val userList: MutableList<User> = mutableListOf()
    private val client = OkHttpClient() // Cliente reutilizable para cancelar solicitudes
    private var currentPage = 1
    private val limit = 5
    private var totalPages = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_usuarios, container, false)
        recyclerView = view.findViewById(R.id.rvUsers)

        // Configurar el RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UsersAdapterFragment(userList)
        recyclerView.adapter = adapter

        // Realizar la búsqueda
        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->
            client.dispatcher.cancelAll() // Cancela solicitudes en curso
            userList.clear()
            adapter.notifyDataSetChanged() // Limpia la lista antes de nuevas búsquedas

            if (searchText.isNotBlank()) {
                fetchUsers(searchText)
            }
        }
        adapter.setOnItemClickListener { userId ->
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            intent.putExtra("usuario_id", userId)
            startActivity(intent)
        }
        return view
    }

    private fun fetchUsers(query: String) {
        val progressBar: ProgressBar = requireView().findViewById(R.id.loadingProgressBar)
        progressBar.visibility = View.VISIBLE

        val url = "${ServerConfig.BASE_URL}get_users.php?busqueda=$query&pagina=$currentPage&limite=$limit"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error al obtener los datos", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.getBoolean("success")) {
                        totalPages = jsonObject.getInt("total_paginas") // Actualiza el total de páginas
                        val usuariosArray = jsonObject.getJSONArray("usuarios")
                        requireActivity().runOnUiThread {
                            for (i in 0 until usuariosArray.length()) {
                                val userJson = usuariosArray.getJSONObject(i)
                                val base64Image = userJson.optString("foto_perfil", "")

                                if (base64Image.isNotEmpty() && base64Image.contains("base64,")) {
                                    try {
                                        val base64Data = base64Image.substringAfter("base64,")
                                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                                        val user = User(
                                            id = userJson.getString("id"), // Agrega el ID del usuario
                                            name = userJson.getString("nombre"),
                                            username = userJson.getString("nombre_usuario"),
                                            imageBitmap = bitmap
                                        )
                                        userList.add(user)
                                    } catch (e: IllegalArgumentException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE

                            // Cargar la siguiente página si es necesario
                            if (currentPage < totalPages) {
                                currentPage++
                                fetchUsers(query) // Llama recursivamente para cargar la siguiente página
                            }
                        }
                    } else {
                        requireActivity().runOnUiThread {
                            progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        })
    }

    // Función de extensión para validar si una cadena es Base64
    private fun String.isBase64(): Boolean {
        return try {
            Base64.decode(this, Base64.DEFAULT)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}