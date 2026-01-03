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
    private var isLoading = false
    private var hayMas = true
    private var currentQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_usuarios, container, false)

        recyclerView = view.findViewById(R.id.rvUsers)
        val progressBar: ProgressBar = view.findViewById(R.id.loadingProgressBar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UsersAdapterFragment(userList)
        recyclerView.adapter = adapter

        // 👉 CLICK EN USUARIO → ABRIR PERFIL
        adapter.setOnItemClickListener { userId ->
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("open_profile_user_id", userId)
            startActivity(intent)
        }

        // 🔹 Scroll infinito controlado
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                if (dy <= 0) return
                if (isLoading || !hayMas) return

                val lm = rv.layoutManager as LinearLayoutManager
                val visible = lm.childCount
                val total = lm.itemCount
                val firstVisible = lm.findFirstVisibleItemPosition()

                if ((visible + firstVisible) >= total && firstVisible >= 0) {
                    fetchUsers(currentQuery, progressBar)
                }
            }
        })

        // 🔹 Observa texto desde SearchActivity
        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->

            val newQuery = searchText.trim()
            if (newQuery == currentQuery) return@observe

            client.dispatcher.cancelAll()

            userList.clear()
            adapter.notifyDataSetChanged()

            currentPage = 1
            hayMas = true
            currentQuery = newQuery

            if (currentQuery.isNotEmpty()) {
                fetchUsers(currentQuery, progressBar)
            }
        }

        return view
    }

    // --------------------------------------------------------------------

    private fun fetchUsers(query: String, progressBar: ProgressBar) {
        if (isLoading || !hayMas || query.isBlank()) return

        isLoading = true
        progressBar.visibility = View.VISIBLE

        val token = TokenStore.getToken(requireContext())
        if (token.isNullOrBlank()) {
            progressBar.visibility = View.GONE
            isLoading = false
            Toast.makeText(requireContext(), "Sesión inválida", Toast.LENGTH_SHORT).show()
            return
        }

        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/usuarios/search_users.php"

        val body = FormBody.Builder()
            .add("token", token)
            .add("pagina", currentPage.toString())
            .add("busqueda", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false
                    Toast.makeText(
                        requireContext(),
                        "Error al obtener usuarios",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)

                val usuarios = json.optJSONArray("usuarios")
                hayMas = json.optBoolean("hay_mas", false)

                requireActivity().runOnUiThread {

                    if (usuarios == null) {
                        // Sin resultados o error controlado
                        progressBar.visibility = View.GONE
                        isLoading = false
                        hayMas = false
                        return@runOnUiThread
                    }

                    for (i in 0 until usuarios.length()) {
                        val u = usuarios.getJSONObject(i)

                        userList.add(
                            User(
                                id = u.getString("id"),
                                name = u.getString("nombre_completo"),
                                username = u.getString("nombre_usuario"),
                                imageUrl = u.optString("foto_perfil"),
                                likes = u.optInt("likes", 0)
                            )
                        )
                    }

                    adapter.notifyDataSetChanged()

                    if (hayMas) {
                        currentPage++
                    }

                    progressBar.visibility = View.GONE
                    isLoading = false
                }
            }
        })
    }
}
