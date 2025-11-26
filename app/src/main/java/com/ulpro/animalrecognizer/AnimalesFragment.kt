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
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

class AnimalesFragment : Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdapterAnimalesFragment
    private var animalList: MutableList<Animal> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_animales, container, false)
        recyclerView = view.findViewById(R.id.rvAnimals)
        val progressBar: ProgressBar = view.findViewById(R.id.loadingProgressBar)

        // Configurar el LayoutManager
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Configurar el adaptador
        adapter = AdapterAnimalesFragment(animalList)
        recyclerView.adapter = adapter

        // Cargar animales desde SharedPreferences
        loadCachedAnimals(progressBar)

        // Observar cambios en el texto de búsqueda
        sharedViewModel.searchText.observe(viewLifecycleOwner) { searchText ->
            if (searchText.isBlank()) {
                adapter.filter.filter("")
                adapter.notifyDataSetChanged()
            } else {
                adapter.filter.filter(searchText)
                adapter.notifyDataSetChanged()
            }
        }

        return view
    }

    private fun loadCachedAnimals(progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE

        val sharedPreferences = requireContext().getSharedPreferences("userSession", Context.MODE_PRIVATE)
        val cachedAnimals = sharedPreferences.getString("cached_animals", null)

        if (cachedAnimals != null) {
            try {
                val animalsArray = JSONArray(cachedAnimals)
                for (i in 0 until animalsArray.length()) {
                    val animalJson = animalsArray.getJSONObject(i)
                    val animal = Animal(
                        id = animalJson.getInt("id"),
                        name = animalJson.getString("name"),
                        imageBase64 = animalJson.getString("imageBase64")
                    )

                    // Verificar si el animal ya existe en la lista
                    if (animalList.none { it.id == animal.id }) {
                        animalList.add(animal)
                    }
                }
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error al procesar los datos almacenados", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "No hay animales almacenados en caché", Toast.LENGTH_SHORT).show()
        }

        progressBar.visibility = View.GONE
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