package com.ulpro.animalrecognizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulpro.animalrecognizer.databinding.FragmentCategoriasBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class fragment_categorias : Fragment() {

    private var _binding: FragmentCategoriasBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CategoriasAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CategoriasAdapter(mutableListOf()) { categoria ->
            // 👉 aquí luego navegas a animales filtrados por clase
            // categoria.nombre
        }

        binding.rvCategorias.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategorias.adapter = adapter

        cargarCategorias()
    }

    private fun cargarCategorias() {
        val token = TokenStore.getToken(requireContext())

        if (token.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Sesión inválida", Toast.LENGTH_SHORT).show()
            return
        }

        val url = ServerConfig.BASE_URL.trimEnd('/') + "/api/animales/leer_clases.php"

        CoroutineScope(Dispatchers.IO).launch {

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val body = FormBody.Builder()
                .add("token", token)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    throw Exception("Error del servidor")
                }

                val json = JSONObject(responseBody)
                val status = json.optBoolean("status", false)

                if (!status) {
                    throw Exception("Respuesta inválida")
                }

                val clasesArray = json
                    .getJSONObject("data")
                    .getJSONArray("clases")

                val categorias = mutableListOf<Categoria>()

                for (i in 0 until clasesArray.length()) {
                    val obj = clasesArray.getJSONObject(i)
                    categorias.add(
                        Categoria(
                            nombre = obj.getString("nombre"),
                            imagen = obj.getString("imagen")
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    adapter.setData(categorias)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Error cargando categorías",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
