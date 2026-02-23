package com.ulpro.animalrecognizer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class UsuariosFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsersAdapterFragment
    private val userList = mutableListOf<User>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

        // Click en usuario
        adapter.setOnItemClickListener { userId ->
            val intent = Intent(requireContext(), ProfileActivity::class.java)
            intent.putExtra("open_profile_user_id", userId)
            startActivity(intent)
        }

        // Observa texto de búsqueda
        sharedViewModel.searchText.observe(viewLifecycleOwner) { text ->
            val query = text.trim()

            if (query.isEmpty() || query == currentQuery) return@observe

            userList.clear()
            adapter.notifyDataSetChanged()

            currentQuery = query
            currentPage = 1
            hayMas = true

            fetchUsers(progressBar)
        }

        // Scroll infinito
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                if (dy <= 0 || isLoading || !hayMas) return

                val lm = rv.layoutManager as LinearLayoutManager
                val visible = lm.childCount
                val total = lm.itemCount
                val firstVisible = lm.findFirstVisibleItemPosition()

                if (visible + firstVisible >= total && firstVisible >= 0) {
                    fetchUsers(progressBar)
                }
            }
        })

        return view
    }

    // ------------------------------------------------------------
    // PETICIÓN AL API
    // ------------------------------------------------------------

    private fun fetchUsers(progressBar: ProgressBar) {
        if (isLoading || !hayMas || currentQuery.isEmpty()) return

        val token = TokenStore.getToken(requireContext())
        if (token.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Token no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        progressBar.visibility = View.VISIBLE

        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/usuarios/search_users.php"

        // ✅ POST clásico (application/x-www-form-urlencoded)
        val body = FormBody.Builder()
            .add("token", token)
            .add("pagina", currentPage.toString())
            .add("buscar", currentQuery) // 🔑 CLAVE CORRECTA
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
                        "Error de red: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                Log.d(
                    "USUARIOS_API",
                    "HTTP ${response.code}\n$responseBody"
                )

                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    isLoading = false

                    // ❌ ERROR HTTP → leer JSON del backend
                    if (!response.isSuccessful) {
                        mostrarErrorDesdeJson(response.code, responseBody)
                        hayMas = false
                        return@runOnUiThread
                    }

                    // ❌ Respuesta vacía
                    if (responseBody.isNullOrBlank()) {
                        Toast.makeText(
                            requireContext(),
                            "Respuesta vacía del servidor",
                            Toast.LENGTH_SHORT
                        ).show()
                        hayMas = false
                        return@runOnUiThread
                    }

                    // ✅ Procesar respuesta correcta
                    try {
                        val json = JSONObject(responseBody)

                        val usuarios = json.optJSONArray("usuarios")
                        hayMas = json.optBoolean("hay_mas", false)

                        if (usuarios == null) {
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

                    } catch (e: Exception) {
                        hayMas = false
                        Toast.makeText(
                            requireContext(),
                            "Error procesando respuesta del servidor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    progressBar.visibility = View.GONE
                    isLoading = false
                }
            }
        })
    }

    // ------------------------------------------------------------
    // MANEJO DE ERRORES JSON DEL BACKEND
    // ------------------------------------------------------------

    private fun mostrarErrorDesdeJson(
        responseCode: Int,
        responseBody: String?
    ) {
        val mensaje = try {
            if (!responseBody.isNullOrBlank()) {
                val json = JSONObject(responseBody)
                json.optString("error", "Error HTTP $responseCode")
            } else {
                "Error HTTP $responseCode"
            }
        } catch (e: Exception) {
            "Error HTTP $responseCode"
        }

        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()

        // 🔐 Opcional: cerrar sesión si token inválido
        if (responseCode == 401) {
            TokenStore.clearToken(requireContext())
        }
    }
}
