package com.ulpro.animalrecognizer

import android.content.Context
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
import org.json.JSONArray

class AnimalesFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdapterAnimalesFragment
    private var animalList: MutableList<Animal> = mutableListOf()

    // Paginación
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutManager: LinearLayoutManager
    private var isLoading = false
    private var currentPage = 0
    private val pageSize = 5
    private var allCachedAnimalsJson: JSONArray? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_animales, container, false)
        recyclerView = view.findViewById(R.id.rvAnimals)
        progressBar = view.findViewById(R.id.loadingProgressBar)

        // Configurar el LayoutManager
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager

        // Configurar el adaptador
        adapter = AdapterAnimalesFragment(animalList)
        recyclerView.adapter = adapter

        // Cargar animales y configurar paginación
        setupPagination()

        // Listener para carga bajo demanda (scroll)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                    && firstVisibleItemPosition >= 0
                    && totalItemCount < (allCachedAnimalsJson?.length() ?: 0)) {
                    loadCachedAnimalsPage()
                }
            }
        })


        // Observar cambios en el texto de búsqueda
        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->
            adapter.filter.filter(searchText)
        }

        return view
    }

    private fun setupPagination() {
        val sharedPreferences = requireContext().getSharedPreferences("userSession", Context.MODE_PRIVATE)
        val cachedAnimals = sharedPreferences.getString("cached_animals", null)

        if (cachedAnimals != null) {
            try {
                allCachedAnimalsJson = JSONArray(cachedAnimals)
                // Cargar la primera página
                loadCachedAnimalsPage()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error al procesar los datos almacenados", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "No hay animales almacenados en caché", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCachedAnimalsPage() {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        val allAnimals = allCachedAnimalsJson ?: return
        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, allAnimals.length())

        if (startIndex < endIndex) {
            try {
                val newAnimals = mutableListOf<Animal>()
                for (i in startIndex until endIndex) {
                    val animalJson = allAnimals.getJSONObject(i)
                    val animal = Animal(
                        id = animalJson.getInt("id"),
                        name = animalJson.getString("name"),
                        imageBase64 = animalJson.getString("imageBase64")
                    )
                    newAnimals.add(animal)
                }
                adapter.addAnimals(newAnimals)
                currentPage++
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error al procesar los datos almacenados", Toast.LENGTH_SHORT).show()
            }
        }

        progressBar.visibility = View.GONE
        isLoading = false
    }

    companion object {
        private const val ARG_PARAM1 = "param1"
        private const val ARG_PARAM2 = "param2"

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AnimalesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}