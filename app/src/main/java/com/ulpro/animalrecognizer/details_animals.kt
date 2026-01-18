package com.ulpro.animalrecognizer

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class details_animals : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private val imageUrlList = mutableListOf<String>()
    private var currentIndex = 0

    private lateinit var textToSpeech: TextToSpeech
    private var currentText = ""
    private var currentPosition = 0

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details_animals)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        imageView = findViewById(R.id.imageViewAnimal)

        imageView.setOnClickListener {
            if (imageUrlList.isNotEmpty()) {
                val dialog = Dialog(this)
                dialog.setContentView(R.layout.dialog_fullscreen_image)
                Glide.with(this)
                    .load(imageUrlList[currentIndex])
                    .into(dialog.findViewById(R.id.fullScreenImageView))
                dialog.show()
            }
        }

        setupImageNavigationButtons()
        setupTextToSpeech()

        val animalId = intent.getIntExtra("animalId", -1)
        if (animalId != -1) {
            fetchAnimalDetails(animalId)
        } else {
            showErrorDialog("ID de animal inválido")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    // =========================================================
    // API
    // =========================================================
    private fun fetchAnimalDetails(animalId: Int) {

        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            redirectToLogin()
            return
        }

        val loading = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Cargando..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {

            val request = Request.Builder()
                .url("${ServerConfig.BASE_URL}/api/animales/view_animal.php")
                .post(
                    FormBody.Builder()
                        .add("id", animalId.toString())
                        .add("token", token)
                        .build()
                )
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    withContext(Dispatchers.Main) { loading.dismissWithAnimation() }

                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            showErrorDialog("Error ${response.code}")
                        }
                        return@use
                    }

                    val json = JSONObject(body)
                    val animal = json.getJSONObject("animal")
                    val recomendaciones = json.optJSONArray("recomendaciones")

                    withContext(Dispatchers.Main) {
                        populateTextFieldsFromApi(animal)
                        setupRecommendationsFromApi(recomendaciones)
                        populateImages(animal.optJSONArray("imagenes"))
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading.dismissWithAnimation()
                    showErrorDialog(e.message ?: "Error de red")
                }
            }
        }
    }

    // =========================================================
    // UI POPULATION
    // =========================================================
    private fun populateTextFieldsFromApi(animal: JSONObject) {

        findViewById<TextView>(R.id.tvNombreComun).text =
            animal.optString("nombre_comun", "N/A")

        findViewById<TextView>(R.id.tvNombreIngles).text =
            animal.optString("nombre_ingles", "N/A")

        findViewById<TextView>(R.id.tvNombreCientifico).text =
            animal.optString("nombre_cientifico", "N/A")

        findViewById<TextView>(R.id.tvPaisOrigen).text =
            animal.optString("pais_origen", "N/A")

        val descripcion = animal
            .optJSONObject("descripcion")
            ?.optJSONObject("descripcion")
            ?.optString("texto", "Descripción no disponible")
            ?: "Descripción no disponible"

        findViewById<TextView>(R.id.tvDescripcion).text = descripcion

        animal.optJSONObject("taxonomia")?.let {
            findViewById<TextView>(R.id.tvReino).text = it.optString("reino", "N/A")
            findViewById<TextView>(R.id.tvFilo).text = it.optString("filo", "N/A")
            findViewById<TextView>(R.id.tvClase).text = it.optString("clase", "N/A")
            findViewById<TextView>(R.id.tvOrden).text = it.optString("orden", "N/A")
            findViewById<TextView>(R.id.tvFamilia).text = it.optString("familia", "N/A")
            findViewById<TextView>(R.id.tvGenero).text = it.optString("genero", "N/A")
        }

        animal.optJSONObject("distribucion")?.let { dist ->
            val core = dist.optJSONObject("distribucion")

            findViewById<TextView>(R.id.tvPaisesExtant).text =
                core?.optJSONArray("paises_extant")?.toList()?.joinToString(", ") ?: "N/A"

            findViewById<TextView>(R.id.tvPaisesExtinct).text =
                core?.optJSONArray("paises_extinct")?.toList()?.joinToString(", ") ?: "N/A"

            findViewById<TextView>(R.id.tvHabitat).text =
                dist.optJSONArray("habitat")?.toList()?.joinToString(", ") ?: "N/A"

            findViewById<TextView>(R.id.tvEstatusConservacion).text =
                dist.optString("estatus_conservacion", "N/A")

            findViewById<TextView>(R.id.tvFuenteConservacion).text =
                dist.optJSONObject("fuente")?.optString("nombre", "N/A") ?: "N/A"
        }
    }

    // =========================================================
    // IMÁGENES
    // =========================================================
    private fun populateImages(arr: JSONArray?) {
        imageUrlList.clear()
        currentIndex = 0

        if (arr == null) return

        val ordered = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .sortedByDescending { it.optInt("es_principal", 0) }
            .map { it.optString("url_archivo") }

        imageUrlList.addAll(ordered)

        if (imageUrlList.isNotEmpty()) {
            showCurrentImage()
        }
    }

    private fun showCurrentImage() {
        Glide.with(this)
            .load(imageUrlList[currentIndex])
            .into(imageView)
    }

    private fun setupImageNavigationButtons() {
        findViewById<Button>(R.id.buttonPrevious).setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showCurrentImage()
            }
        }
        findViewById<Button>(R.id.buttonNext).setOnClickListener {
            if (currentIndex < imageUrlList.size - 1) {
                currentIndex++
                showCurrentImage()
            }
        }
    }

    // =========================================================
    // RECOMENDACIONES
    // =========================================================
    private fun setupRecommendationsFromApi(recs: JSONArray?) {

        val rv = findViewById<RecyclerView>(R.id.rvRecomendaciones)
        rv.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        if (recs == null || recs.length() == 0) {
            rv.visibility = View.GONE
            return
        }

        val items = mutableListOf<RecommendationItem>()
        for (i in 0 until recs.length()) {
            val obj = recs.getJSONObject(i)
            items.add(
                RecommendationItem(
                    id = obj.getInt("id"),
                    name = obj.getString("nombre"),
                    imageUrl = obj.getString("imagen_principal")
                )
            )
        }

        rv.adapter = RecommendationAdapter(items) { id ->
            startActivity(
                Intent(this, details_animals::class.java)
                    .putExtra("animalId", id)
            )
        }
    }

    // =========================================================
    // TEXT TO SPEECH
    // =========================================================
    private fun setupTextToSpeech() {

        val btn = findViewById<ImageButton>(R.id.btnLeerDescripcion)

        textToSpeech = TextToSpeech(this) {
            textToSpeech.language = Locale("es", "ES")
        }

        btn.setOnClickListener {
            val text = findAllText(findViewById(R.id.main))
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop()
                btn.setImageResource(R.drawable.ic_play_circle_black_24dp)
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
                btn.setImageResource(R.drawable.ic_pause_black_24dp)
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                runOnUiThread {
                    btn.setImageResource(R.drawable.ic_play_circle_black_24dp)
                }
            }
            override fun onError(id: String?) {}
        })
    }

    private fun findAllText(vg: ViewGroup): String {
        val sb = StringBuilder()
        for (i in 0 until vg.childCount) {
            val v = vg.getChildAt(i)
            if (v is TextView) sb.append(v.text).append("\n")
            else if (v is ViewGroup) sb.append(findAllText(v))
        }
        return sb.toString()
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private fun JSONArray.toList(): List<String> =
        (0 until length()).mapNotNull { optString(it, null) }

    private fun redirectToLogin() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showErrorDialog(msg: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText("Error")
            .setContentText(msg)
            .setConfirmText("Cerrar")
            .show()
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
