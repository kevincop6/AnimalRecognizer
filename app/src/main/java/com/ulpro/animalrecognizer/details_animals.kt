package com.ulpro.animalrecognizer

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
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
    private val imageList = mutableListOf<Bitmap>()
    private var currentIndex = 0

    private lateinit var textToSpeech: TextToSpeech
    private var currentText = ""
    private var currentPosition = 0

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details_animals)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        imageView = findViewById(R.id.imageView)

        // Fullscreen al tocar la imagen
        imageView.setOnClickListener {
            if (imageList.isNotEmpty()) {
                val dialog = Dialog(this)
                dialog.setContentView(R.layout.dialog_fullscreen_image)
                val fullScreenImageView = dialog.findViewById<ImageView>(R.id.fullScreenImageView)
                fullScreenImageView.setImageBitmap(imageList[currentIndex])
                dialog.show()
            }
        }

        // Botones prev/next
        setupImageNavigationButtons()

        // Mostrar / ocultar detalles
        val buttonShowHideDetails = findViewById<Button>(R.id.buttonShowHideDetails)
        val detailsLayout = findViewById<LinearLayout>(R.id.detailsLayout)
        val textViewDescription = findViewById<TextView>(R.id.textViewDescription)

        buttonShowHideDetails.setOnClickListener {
            textViewDescription.maxLines = if (textViewDescription.maxLines == 5) Int.MAX_VALUE else 5

            if (detailsLayout.visibility == View.GONE) {
                detailsLayout.visibility = View.VISIBLE
                buttonShowHideDetails.text = getString(R.string.hide_details)
            } else {
                detailsLayout.visibility = View.GONE
                buttonShowHideDetails.text = getString(R.string.show_details)
            }
        }

        // TTS
        val buttonReadAll = findViewById<ImageButton>(R.id.buttonReadDescription)
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
                    currentPosition = 0
                }
                val textToRead = currentText.substring(currentPosition)

                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "reading")
                textToSpeech.speak(textToRead, TextToSpeech.QUEUE_FLUSH, params, "reading")
                buttonReadAll.setImageResource(R.drawable.ic_pause_black_24dp)
            }
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    currentPosition = 0
                    buttonReadAll.setImageResource(R.drawable.ic_refresh_24dp)
                }
            }

            override fun onError(utteranceId: String?) {}
        })

        val animalId = intent.getIntExtra("animalId", -1)
        if (animalId != -1) {
            fetchAnimalDetails(animalId)
        } else {
            showErrorDialog("ID de animal no válido")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    // =========================================================
    // POST /api/animales/view_animal.php  (id + token)
    // =========================================================
    private fun fetchAnimalDetails(animalId: Int) {
        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            showErrorDialog("Sesión no válida. Inicia sesión nuevamente.")
            redirectToLogin()
            return
        }

        val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Cargando detalles..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val url = ServerConfig.BASE_URL.trimEnd('/') + "/api/animales/view_animal.php"

            val formBody = FormBody.Builder()
                .add("id", animalId.toString())
                .add("token", token)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    val rawBody = response.body?.string().orEmpty()

                    withContext(Dispatchers.Main) { loadingDialog.dismissWithAnimation() }

                    when (code) {
                        200 -> {
                            val json = JSONObject(rawBody)
                            val animal = json.getJSONObject("animal")
                            val recomendaciones = json.optJSONArray("recomendaciones")

                            withContext(Dispatchers.Main) {
                                populateTextFieldsFromApi(animal)
                                setupRecommendationsFromApi(recomendaciones)
                            }

                            // ✅ Cargar primero imagen principal, luego las demás
                            animal.optJSONArray("urls_archivos_media")?.let { media ->
                                populateImagesPrioritizingPrincipal(media)
                            } ?: run {
                                withContext(Dispatchers.Main) {
                                    imageList.clear()
                                    currentIndex = 0
                                }
                            }
                        }

                        401 -> {
                            TokenStore.clearToken(this@details_animals)
                            val msg = try { JSONObject(rawBody).optString("mensaje", "Acceso denegado.") }
                            catch (_: Exception) { "Acceso denegado." }
                            withContext(Dispatchers.Main) {
                                showErrorDialog(msg)
                                redirectToLogin()
                            }
                        }

                        else -> {
                            val msg = try { JSONObject(rawBody).optString("mensaje", "Error ($code)") }
                            catch (_: Exception) { "Error ($code)" }
                            withContext(Dispatchers.Main) { showErrorDialog(msg) }
                        }
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

    // =========================================================
    // UI: Textos según JSON real
    // =========================================================
    private fun populateTextFieldsFromApi(animal: JSONObject) {
        findViewById<TextView>(R.id.textViewTitle).text = animal.optString("nombre_comun", "N/A")
        findViewById<TextView>(R.id.textViewSubtitle).text = animal.optString("nombre_cientifico", "N/A")

        findViewById<TextView>(R.id.textViewNombreEspanol).text = animal.optString("nombre_comun", "N/A")
        findViewById<TextView>(R.id.textViewNombreIngles).text = animal.optString("nombre_ingles", "N/A")
        findViewById<TextView>(R.id.textViewNombreCientifico).text = animal.optString("nombre_cientifico", "N/A")

        animal.optJSONObject("taxonomia")?.let { tax ->
            findViewById<TextView>(R.id.textViewReino).text = tax.optString("reino", "N/A")
            findViewById<TextView>(R.id.textViewFilo).text = tax.optString("filo", "N/A")
            findViewById<TextView>(R.id.textViewClase).text = tax.optString("clase", "N/A")
            findViewById<TextView>(R.id.textViewOrden).text = tax.optString("orden", "N/A")
            findViewById<TextView>(R.id.textViewFamilia).text = tax.optString("familia", "N/A")
            findViewById<TextView>(R.id.textViewGenero).text = tax.optString("genero", "N/A")
        }

        animal.optJSONObject("distribucion")?.let { dist ->
            val core = dist.optJSONObject("distribucion")
            val extant = core?.optJSONArray("paises_extant")?.toStringList().orEmpty()
            val extinct = core?.optJSONArray("paises_extinct")?.toStringList().orEmpty()
            val habitat = dist.optJSONArray("habitat")?.toStringList().orEmpty()

            findViewById<TextView>(R.id.textViewDistribution).text =
                "Existente: ${if (extant.isEmpty()) "N/A" else extant.joinToString(", ")}\n" +
                        "Extinto: ${if (extinct.isEmpty()) "N/A" else extinct.joinToString(", ")}"

            findViewById<TextView>(R.id.textViewHabitat).text =
                if (habitat.isEmpty()) "N/A" else habitat.joinToString(", ")

            findViewById<TextView>(R.id.textViewConservationStatus).text =
                dist.optString("estatus_conservacion", "N/A")

            dist.optJSONObject("fuente")?.optString("nombre", null)?.let { fuenteNombre ->
                findViewById<TextView>(R.id.textViewSource).text = fuenteNombre
            }
        }

        val desc = animal.optJSONObject("descripcion")
            ?.optJSONObject("descripcion")
            ?.optString("texto", "Descripción no disponible")
            ?: "Descripción no disponible"

        findViewById<TextView>(R.id.textViewDescription).text = desc
    }

    // =========================================================
    // ✅ Principal primero, luego el resto (para UI instantánea)
    // =========================================================
    private fun populateImagesPrioritizingPrincipal(mediaArray: JSONArray) {
        imageList.clear()
        currentIndex = 0

        val entries = (0 until mediaArray.length()).mapNotNull { i ->
            val obj = mediaArray.optJSONObject(i) ?: return@mapNotNull null
            val url = obj.optString("url_archivo", "").trim()
            if (url.isBlank()) return@mapNotNull null
            val principal = obj.optInt("es_principal", 0)
            MediaEntry(index = i, isPrincipal = principal == 1, url = url)
        }

        val principal = entries.firstOrNull { it.isPrincipal } ?: entries.firstOrNull()
        val rest = entries.filter { it != principal }

        // 1) cargar principal primero
        if (principal == null) return

        CoroutineScope(Dispatchers.IO).launch {
            val principalBmp = downloadBitmap(normalizeUrl(principal.url))
            if (principalBmp != null) {
                withContext(Dispatchers.Main) {
                    imageList.add(principalBmp)
                    currentIndex = 0
                    imageView.setImageBitmap(principalBmp)
                }
            }

            // 2) luego cargar las demás
            for (e in rest) {
                val bmp = downloadBitmap(normalizeUrl(e.url)) ?: continue
                withContext(Dispatchers.Main) {
                    imageList.add(bmp)
                }
            }
        }
    }

    private data class MediaEntry(val index: Int, val isPrincipal: Boolean, val url: String)

    // =========================================================
    // Destacados: recomendaciones[] (URL + click => details_animals)
    // =========================================================
    private fun setupRecommendationsFromApi(recs: JSONArray?) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewRecommendations)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        if (recs == null || recs.length() == 0) {
            recyclerView.adapter = RecommendationAdapter(emptyList()) { }
            return
        }

        val items = mutableListOf<RecommendationItem>()
        for (i in 0 until recs.length()) {
            val obj = recs.optJSONObject(i) ?: continue
            val id = obj.optInt("id", -1)
            val nombre = obj.optString("nombre", "").trim()
            val img = obj.optString("imagen_principal", "").trim()
            if (id == -1 || nombre.isBlank() || img.isBlank()) continue

            items.add(
                RecommendationItem(
                    id = id,
                    name = nombre,
                    imageUrl = normalizeUrl(img)
                )
            )
        }

        recyclerView.adapter = RecommendationAdapter(items) { clickedId ->
            val intent = Intent(this@details_animals, details_animals::class.java)
            intent.putExtra("animalId", clickedId)
            startActivity(intent)
        }
    }

    // =========================================================
    // Imágenes prev/next
    // =========================================================
    private fun setupImageNavigationButtons() {
        val buttonPrevious = findViewById<Button>(R.id.buttonPrevious)
        val buttonNext = findViewById<Button>(R.id.buttonNext)

        fun updateImage() {
            if (imageList.isNotEmpty()) {
                imageView.setImageBitmap(imageList[currentIndex])
            }
        }

        buttonPrevious.setOnClickListener {
            if (imageList.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                updateImage()
            }
        }

        buttonNext.setOnClickListener {
            if (imageList.isNotEmpty() && currentIndex < imageList.size - 1) {
                currentIndex++
                updateImage()
            }
        }
    }

    // =========================================================
    // TTS: excluir destacados
    // =========================================================
    private fun getAllTextFromTextViews(viewGroup: ViewGroup): String {
        val sb = StringBuilder()
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            if (child.id == R.id.HorizontalViewGallery || child.id == R.id.recyclerViewRecommendations) {
                continue
            }

            if (child is TextView && child.id != R.id.textViewGallery) {
                sb.append(child.text).append("\n")
            } else if (child is ViewGroup) {
                sb.append(getAllTextFromTextViews(child))
            }
        }
        return sb.toString()
    }

    // =========================================================
    // Helpers
    // =========================================================
    private fun normalizeUrl(pathOrUrl: String): String {
        val s = pathOrUrl.trim()
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
            s
        } else {
            ServerConfig.BASE_URL.trimEnd('/') + "/" + s.trimStart('/')
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { idx ->
            optString(idx, null)?.takeIf { it.isNotBlank() }
        }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showErrorDialog(message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText("Error")
            .setContentText(message)
            .setConfirmText("Cerrar")
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}
