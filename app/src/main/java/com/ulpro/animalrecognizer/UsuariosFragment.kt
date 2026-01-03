package com.ulpro.animalrecognizer

import android.content.Intent
import android.os.Bundle
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

class UsuariosFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsersAdapterFragment
    private val userList: MutableList<User> = mutableListOf()

    private val client = OkHttpClient()
    private var currentPage = 1
    private val limit = 5
    private var totalPages = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_usuarios, container, false)

        recyclerView = view.findViewById(R.id.rvUsers)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UsersAdapterFragment(userList)
        recyclerView.adapter = adapter

        // 👉 CLICK EN USUARIO → ABRIR PERFIL
        adapter.setOnItemClickListener { userId ->
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("open_profile_user_id", userId)
            startActivity(intent)
        }

        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->
            client.dispatcher.cancelAll()
            userList.clear()
            currentPage = 1
            adapter.notifyDataSetChanged()

            if (searchText.isNotBlank()) {
                fetchUsers(searchText)
            }
        }

        return view
    }

    private fun fetchUsers(query: String) {
        val progressBar: ProgressBar = requireView().findViewById(R.id.loadingProgressBar)
        progressBar.visibility = View.VISIBLE

        val url =
            "${ServerConfig.BASE_URL}get_users.php?busqueda=$query&pagina=$currentPage&limite=$limit"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Error al obtener usuarios",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)

                if (!json.getBoolean("success")) {
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE
                    }
                    return
                }

                totalPages = json.getInt("total_paginas")
                val usuarios = json.getJSONArray("usuarios")

                requireActivity().runOnUiThread {
                    for (i in 0 until usuarios.length()) {
                        val u = usuarios.getJSONObject(i)
                        userList.add(
                            User(
                                id = u.getString("id"),
                                name = u.getString("nombre"),
                                username = u.getString("nombre_usuario"),
                                imageUrl = u.optString("foto_perfil")
                            )
                        )
                    }

                    adapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE

                    if (currentPage < totalPages) {
                        currentPage++
                        fetchUsers(query)
                    }
                }
            }
        })
    }
}
