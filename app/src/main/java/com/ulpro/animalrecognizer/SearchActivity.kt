package com.ulpro.animalrecognizer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import org.json.JSONArray
import java.util.Locale
import kotlin.collections.get


class SearchActivity : AppCompatActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()
    private val VOICE_RECOGNITION_REQUEST_CODE = 100
    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        val voiceSearchButton: ImageButton = findViewById(R.id.voiceSearchButton)
        val searchEditText: EditText = findViewById(R.id.searchEditText)
        sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No se necesita implementar
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sharedViewModel.updateSearchText(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
                // No se necesita implementar
            }
        })

        // Configurar ViewPager y TabLayout
        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)
        viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.animales)
                1 -> getString(R.string.aportes)
                2 -> getString(R.string.usuarios)
                else -> null
            }
        }.attach()
        voiceSearchButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") // Configura el idioma a español
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.diga_algo))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Habilita resultados parciales
            }
            try {
                startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.reconocimiento_no_disponible), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            var spokenText = results?.get(0) ?: ""

            // Cargar animales en caché
            val cachedAnimals = sharedPreferences.getString("cached_animals", null)
            val scientificNamesCorrections = mutableMapOf<String, String>()

            if (cachedAnimals != null) {
                val animalsArray = JSONArray(cachedAnimals)
                for (i in 0 until animalsArray.length()) {
                    val animalJson = animalsArray.getJSONObject(i)
                    val scientificName = animalJson.getString("name").lowercase()
                    scientificNamesCorrections[scientificName] = animalJson.getString("name")
                }
            }

            // Expresiones regulares
            val latinAndSpanishRegex = Regex("\\b[a-zA-ZñÑüÜ]+\\b")
            val emailRegex = Regex("\\b[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}\\b")
            val usernameRegex = Regex("\\b[a-zA-Z0-9._-]{3,}\\b")

            // Postprocesar el texto
            val uniqueWords = mutableSetOf<String>()
            spokenText = spokenText.let { text ->
                scientificNamesCorrections.entries.fold(text) { acc, (incorrect, correct) ->
                    acc.replace(incorrect, correct, ignoreCase = true)
                }
            }.let { text ->
                // Procesar correos electrónicos sin espacios
                val emails = emailRegex.findAll(text).map { it.value }.toSet()
                uniqueWords.addAll(emails)

                // Procesar nombres de usuario y palabras en latín/español
                uniqueWords.addAll(usernameRegex.findAll(text).map { it.value })
                uniqueWords.addAll(latinAndSpanishRegex.findAll(text).map { it.value })

                uniqueWords.joinToString(" ")
            }

            // Eliminar tildes del texto
            spokenText = removeAccents(spokenText)

            val searchEditText: EditText = findViewById(R.id.searchEditText)
            searchEditText.setText(spokenText)
            sharedViewModel.updateSearchText(spokenText) // Realiza la búsqueda automáticamente
        }
    }

    // Función para eliminar tildes del texto
    private fun removeAccents(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}