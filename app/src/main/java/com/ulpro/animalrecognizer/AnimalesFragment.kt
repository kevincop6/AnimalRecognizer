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
import java.io.File

class AnimalesFragment : Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdapterAnimalesFragment
    private var animalList: MutableList<Animal> = mutableListOf()

    private lateinit var progressBar: ProgressBar
    private lateinit var layoutManager: LinearLayoutManager

    private var isLoading = false
    private var currentPage = 0
    private val pageSize = 5
    private var allCachedAnimalsJson: JSONArray? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_animales, container, false)

        recyclerView = view.findViewById(R.id.rvAnimals)
        progressBar = view.findViewById(R.id.loadingProgressBar)

        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager

        adapter = AdapterAnimalesFragment(animalList)
        recyclerView.adapter = adapter

        setupPagination()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                val totalAvailable = allCachedAnimalsJson?.length() ?: 0

                if (!isLoading &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount &&
                    firstVisibleItemPosition >= 0 &&
                    totalItemCount < totalAvailable
                ) {
                    loadCachedAnimalsPage()
                }
            }
        })

        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->
            adapter.filter.filter(searchText)
        }

        return view
    }

    private fun setupPagination() {
        val file = getActiveAnimalsFile()
        if (!file.exists()) {
            Toast.makeText(requireContext(), "No hay animales descargados aún", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonText = file.readText()
            allCachedAnimalsJson = JSONArray(jsonText)
            currentPage = 0
            adapter.clearAnimals()
            loadCachedAnimalsPage()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al procesar los datos almacenados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCachedAnimalsPage() {
        if (isLoading) return
        val allAnimals = allCachedAnimalsJson ?: return

        isLoading = true
        progressBar.visibility = View.VISIBLE

        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, allAnimals.length())

        if (startIndex < endIndex) {
            try {
                val newAnimals = mutableListOf<Animal>()

                for (i in startIndex until endIndex) {
                    val obj = allAnimals.getJSONObject(i)

                    val id = obj.optInt("id", -1)
                    val nombre = obj.optString("nombre", "")
                    val imagenUrl = obj.optString("imagen_url", "")

                    if (id != -1 && nombre.isNotBlank()) {
                        newAnimals.add(
                            Animal(
                                id = id,
                                nombre = nombre,
                                imagenUrl = imagenUrl
                            )
                        )
                    }
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

    private fun getActiveAnimalsFile(): File {
        val prefs = requireContext().getSharedPreferences("animals_cache_meta", Context.MODE_PRIVATE)
        val fileName = prefs.getString("active_animals_file", null)
        return if (fileName.isNullOrBlank()) File("") else File(requireContext().filesDir, fileName)
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
