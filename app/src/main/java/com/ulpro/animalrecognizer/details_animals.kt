package com.ulpro.animalrecognizer

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.plusAssign
import kotlin.text.append

class details_animals : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private val imageList = mutableListOf<Bitmap>() // Lista global de imágenes
    private var currentIndex = 0 // Índice global
    private lateinit var textToSpeech: TextToSpeech
    private var currentText = ""
    private var currentPosition = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details_animals)
        val buttonReadAll = findViewById<ImageButton>(R.id.buttonReadDescription)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val animalId = intent.getIntExtra("animalId", -1)
        if (animalId != -1) {
            fetchAnimalDetails(animalId)
        } else {
            showErrorDialog("ID de animal no válido")
        }

        val buttonShowHideDetails = findViewById<Button>(R.id.buttonShowHideDetails)
        val detailsLayout = findViewById<LinearLayout>(R.id.detailsLayout)
        val textViewDescription = findViewById<TextView>(R.id.textViewDescription)

        buttonShowHideDetails.setOnClickListener {
            if (textViewDescription.maxLines == 5) {
                textViewDescription.maxLines = Int.MAX_VALUE
            } else {
                textViewDescription.maxLines = 5
            }
            if (detailsLayout.visibility == View.GONE) {
                detailsLayout.visibility = View.VISIBLE
                buttonShowHideDetails.text = getString(R.string.hide_details)
            } else {
                detailsLayout.visibility = View.GONE
                buttonShowHideDetails.text = getString(R.string.show_details)
            }
        }

        imageView = findViewById(R.id.imageView)
        imageView.setOnClickListener {
            if (imageList.isNotEmpty()) {
                val dialog = Dialog(this)
                dialog.setContentView(R.layout.dialog_fullscreen_image)
                val fullScreenImageView = dialog.findViewById<ImageView>(R.id.fullScreenImageView)
                fullScreenImageView.setImageBitmap(imageList[currentIndex])
                dialog.show()
            }
        }
        // Inicializar TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale("es", "ES"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    showErrorDialog("El idioma español no está soportado en este dispositivo.")
                }
            }
        }

        buttonReadAll.setOnClickListener {
            val allText = getAllTextFromTextViews(findViewById(R.id.main))
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop()
                buttonReadAll.setImageResource(R.drawable.ic_play_circle_black_24dp)
            } else {
                if (currentText != allText) {
                    currentText = allText
                }
                val textToRead = currentText.substring(currentPosition)
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "reading")
                textToSpeech.speak(textToRead, TextToSpeech.QUEUE_FLUSH, params, "reading")
                buttonReadAll.setImageResource(R.drawable.ic_pause_black_24dp)
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // No se requiere acción al iniciar
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    currentPosition = 0 // Reinicia la posición al finalizar
                    buttonReadAll.setImageResource(R.drawable.ic_refresh_24dp)
                }
            }

            override fun onError(utteranceId: String?) {
                // Manejo de errores si es necesario
            }
        })
    }
    private fun getAllTextFromTextViews(viewGroup: ViewGroup): String {
        val stringBuilder = StringBuilder()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView && child.id != R.id.buttonShowHideDetails &&
                child.id != R.id.textViewGallery && child.id != R.id.HorizontalViewGallery) {
                stringBuilder.append(child.text).append("\n")
            } else if (child is ViewGroup && child.id != R.id.HorizontalViewGallery) {
                stringBuilder.append(getAllTextFromTextViews(child))
            }
        }
        return stringBuilder.toString()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
    private fun fetchRecommendations(animalId: Int) {
        val url = "${ServerConfig.BASE_URL}recommendation.php?id=$animalId"
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewRecommendations)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // Tiempo de espera para conectar
                .readTimeout(10, TimeUnit.SECONDS)   // Tiempo de espera para leer datos
                .writeTimeout(10, TimeUnit.SECONDS)  // Tiempo de espera para escribir datos
                .build()

            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonArray = org.json.JSONArray(responseBody)
                    val items = mutableListOf<RecommendationItem>()

                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val id = jsonObject.getInt("id")
                        val name = jsonObject.getString("name")
                        val imageBase64 = jsonObject.getString("imageBase64")
                        items.add(RecommendationItem(id, name, imageBase64))
                    }

                    withContext(Dispatchers.Main) {
                        recyclerView.adapter = RecommendationAdapter(items) { animalId ->
                            val intent = Intent(this@details_animals, details_animals::class.java)
                            intent.putExtra("animalId", animalId)
                            startActivity(intent)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showErrorDialog("Error al obtener recomendaciones.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Error: ${e.message}")
                }
            }
        }
    }
    private fun fetchAnimalDetails(animalId: Int) {
        fetchRecommendations(animalId)
        val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Cargando detalles..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // Tiempo de espera para conectar
                .readTimeout(10, TimeUnit.SECONDS)   // Tiempo de espera para leer datos
                .writeTimeout(10, TimeUnit.SECONDS)  // Tiempo de espera para escribir datos
                .build()

            val url = "${ServerConfig.BASE_URL}details_animals.php?id=$animalId"
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()

                    if (!response.isSuccessful || responseBody == null) {
                        showErrorDialog("Error en la solicitud: ${response.message}")
                        return@withContext
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.getBoolean("success")

                    if (success) {
                        val data = jsonResponse.getJSONObject("data")
                        populateUI(data)
                    } else {
                        showErrorDialog("No se encontraron detalles para el ID proporcionado.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    showErrorDialog("Error: ${e.message}")
                }
            }
        }
    }

    private fun populateUI(data: JSONObject) {
        // Taxonomía
        val taxonomia = data.optJSONObject("taxonomia")
        if (taxonomia != null) {
            findViewById<TextView>(R.id.textViewTitle).text = taxonomia.optString("nombre_es", "N/A")
            findViewById<TextView>(R.id.textViewSubtitle).text = taxonomia.optString("nombre_in", "N/A")
            findViewById<TextView>(R.id.textViewNombreEspanol).text = taxonomia.optString("nombre_es", "N/A")
            findViewById<TextView>(R.id.textViewNombreIngles).text = taxonomia.optString("nombre_in", "N/A")
            findViewById<TextView>(R.id.textViewNombreCientifico).text = taxonomia.optString("nombre_ci", "N/A")
            findViewById<TextView>(R.id.textViewReino).text = taxonomia.optString("reino", "N/A")
            findViewById<TextView>(R.id.textViewFilo).text = taxonomia.optString("filo", "N/A")
            findViewById<TextView>(R.id.textViewClase).text = taxonomia.optString("clase", "N/A")
            findViewById<TextView>(R.id.textViewOrden).text = taxonomia.optString("orden", "N/A")
            findViewById<TextView>(R.id.textViewFamilia).text = taxonomia.optString("familia", "N/A")
            findViewById<TextView>(R.id.textViewGenero).text = taxonomia.optString("genero", "N/A")
        }

        // Distribución
        val distribucion = data.optJSONObject("distribucion")
        if (distribucion != null) {
            val distribucionData = distribucion.optJSONObject("distribucion")
            val paisesExtant = distribucionData?.optJSONArray("paises_extant")?.let { jsonArray ->
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } ?: emptyList()
            val paisesExtinct = distribucionData?.optJSONArray("paises_extinct")?.let { jsonArray ->
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } ?: emptyList()
            val habitat = distribucion.optJSONArray("habitat")?.let { jsonArray ->
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } ?: emptyList()

            findViewById<TextView>(R.id.textViewDistribution).text =
                "Existente: ${paisesExtant.joinToString(", ")}\nExtinto: ${paisesExtinct.joinToString(", ")}"
            findViewById<TextView>(R.id.textViewHabitat).text = habitat.joinToString(", ")
            findViewById<TextView>(R.id.textViewConservationStatus).text =
                distribucion.optString("estatus_conservacion", "N/A")

            // Fuente
            val fuente = distribucion.optJSONObject("fuente")
            if (fuente != null) {
                val nombreFuente = fuente.optString("nombre", "N/A")
                findViewById<TextView>(R.id.textViewSource).text = nombreFuente
            }
        }

        // Descripción
        val descripcion = data.optJSONObject("descripcion")?.optJSONObject("descripcion")
        findViewById<TextView>(R.id.textViewDescription).text =
            descripcion?.optString("texto", "Descripción no disponible") ?: "Descripción no disponible"

        // Decodificar imágenes Base64 y almacenarlas en la lista global
        val imagenes = data.optJSONObject("imagenes")?.optJSONArray("imagenes")
        if (imagenes != null) {
            for (i in 0 until imagenes.length()) {
                val base64Image = imagenes.getJSONObject(i).optString("base64", "")
                if (base64Image.isNotEmpty()) {
                    val imageBytes = Base64.decode(base64Image.split(",")[1], Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    imageList.add(bitmap)
                }
            }
        }

        val buttonPrevious = findViewById<Button>(R.id.buttonPrevious)
        val buttonNext = findViewById<Button>(R.id.buttonNext)

        // Función para actualizar la imagen mostrada
        fun updateImage() {
            if (imageList.isNotEmpty()) {
                imageView.setImageBitmap(imageList[currentIndex])
            }
        }

        // Configurar botones para navegar entre imágenes
        buttonPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updateImage()
            }
        }

        buttonNext.setOnClickListener {
            if (currentIndex < imageList.size - 1) {
                currentIndex++
                updateImage()
            }
        }

        // Mostrar la primera imagen al iniciar
        updateImage()
    }

    private fun showErrorDialog(message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText("Error")
            .setContentText(message)
            .setConfirmText("Cerrar")
            .show()
    }
}