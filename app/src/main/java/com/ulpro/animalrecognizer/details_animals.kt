package com.ulpro.animalrecognizer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
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
import kotlin.math.abs

class details_animals : AppCompatActivity() {

    companion object {
        private const val REQUEST_FULLSCREEN = 1001
    }

    private lateinit var imageView: ImageView
    private lateinit var rvGallery: RecyclerView
    private lateinit var galleryAdapter: GalleryThumbAdapter
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var tvDescripcion: TextView
    private lateinit var textToSpeech: TextToSpeech

    private val imageUrlList = mutableListOf<String>()
    private var currentIndex = 0
    private var descripcion: String = ""
    private var descripcionExpandida = false
    private var descripcionInicializada = false
    private lateinit var btnFavorite: ImageButton
    private var isFavorite = false
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentCard)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }



        imageView = findViewById(R.id.imageViewAnimal)
        rvGallery = findViewById(R.id.rvGallery)
        btnFavorite = findViewById(R.id.btnFavorite)
        tvDescripcion = findViewById<TextView>(R.id.tvDescripcion)
        setupGallery()
        setupImageSwipe()
        setupTextToSpeech()


        // Tap en imagen principal -> fullscreen en índice actual
        imageView.setOnClickListener {
            openFullScreenGallery(currentIndex)
        }

        val animalId = intent.getIntExtra("animalId", -1)
        if (animalId != -1) {
            fetchAnimalDetails(animalId)
        } else {
            showErrorDialog("ID de animal inválido")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        tvDescripcion.setOnClickListener {
            toggleDescripcion(descripcion)
        }
    }


    private fun toggleDescripcion(textoBase: String) {
        val tvDescripcion = findViewById<TextView>(R.id.tvDescripcion)
        val extraDetails = findViewById<LinearLayout>(R.id.extraDetails)

        fun buildTextoConBoton(
            texto: String,
            boton: String,
            maxLines: Int
        ): SpannableString {

            val width = (tvDescripcion.width
                    - tvDescripcion.paddingStart
                    - tvDescripcion.paddingEnd).coerceAtLeast(1)

            fun layoutOf(text: CharSequence): StaticLayout =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, tvDescripcion.paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(
                        tvDescripcion.lineSpacingExtra,
                        tvDescripcion.lineSpacingMultiplier
                    )
                    .setIncludePad(tvDescripcion.includeFontPadding)
                    .build()

            val fullText = "$texto  $boton"
            if (layoutOf(fullText).lineCount <= maxLines) {
                return SpannableString(fullText)
            }

            var low = 0
            var high = texto.length
            var best = 0

            while (low <= high) {
                val mid = (low + high) / 2
                val candidate = texto.substring(0, mid).trimEnd() + "…  $boton"
                if (layoutOf(candidate).lineCount <= maxLines) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            return SpannableString(
                texto.substring(0, best).trimEnd() + "…  $boton"
            )
        }

        // =========================
        // PRIMERA EJECUCIÓN → SIEMPRE COLAPSADO
        // =========================
        if (!descripcionInicializada) {
            descripcionInicializada = true
            descripcionExpandida = false

            tvDescripcion.post {
                val spannable = buildTextoConBoton(
                    textoBase,
                    "Mostrar más",
                    6
                )

                val inicio = spannable.lastIndexOf("Mostrar más")
                spannable.setSpan(
                    ForegroundColorSpan(getColor(R.color.primary)),
                    inicio, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    inicio, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                tvDescripcion.text = spannable
                tvDescripcion.maxLines = Int.MAX_VALUE
                tvDescripcion.ellipsize = null
                extraDetails.visibility = View.GONE
            }
            return
        }

        // =========================
        // TOGGLE REAL
        // =========================
        descripcionExpandida = !descripcionExpandida

        if (descripcionExpandida) {
            val textoFinal = "$textoBase  Mostrar menos"
            val spannable = SpannableString(textoFinal)

            val inicio = textoFinal.lastIndexOf("Mostrar menos")
            spannable.setSpan(
                ForegroundColorSpan(getColor(R.color.primary)),
                inicio, spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                inicio, spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            tvDescripcion.text = spannable
            tvDescripcion.maxLines = Int.MAX_VALUE
            tvDescripcion.ellipsize = null
            extraDetails.visibility = View.VISIBLE

        } else {
            tvDescripcion.post {
                val spannable = buildTextoConBoton(
                    textoBase,
                    "Mostrar más",
                    6
                )

                val inicio = spannable.lastIndexOf("Mostrar más")
                spannable.setSpan(
                    ForegroundColorSpan(getColor(R.color.primary)),
                    inicio, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    inicio, spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                tvDescripcion.text = spannable
                tvDescripcion.maxLines = Int.MAX_VALUE
                tvDescripcion.ellipsize = null
                extraDetails.visibility = View.GONE
            }
        }
    }

    // =========================================================
    // GALERÍA (MINIATURAS)
    // =========================================================
    private fun setupGallery() {
        galleryAdapter = GalleryThumbAdapter { position ->
            // Mostrar esa imagen en principal + marcar selección
            setImageAt(position)
            // Abrir fullscreen arrancando en esa miniatura
            openFullScreenGallery(position)
        }

        rvGallery.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvGallery.adapter = galleryAdapter
    }

    private fun setImageAt(position: Int) {
        if (position !in imageUrlList.indices) return
        currentIndex = position
        showCurrentImage()
        galleryAdapter.setSelected(position)
        rvGallery.smoothScrollToPosition(position)
    }

    private fun showCurrentImage() {
        if (imageUrlList.isEmpty() || currentIndex !in imageUrlList.indices) return

        imageView.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUrlList[currentIndex])
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(imageView)
    }

    // =========================================================
    // SWIPE EN IMAGEN PRINCIPAL (IZQ/DER)
    // =========================================================
    private fun setupImageSwipe() {

        gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                private val threshold = 120
                private val velocityThreshold = 120

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    if (e1 == null) return false
                    if (imageUrlList.isEmpty()) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    if (abs(diffX) > abs(diffY)
                        && abs(diffX) > threshold
                        && abs(velocityX) > velocityThreshold
                    ) {
                        if (diffX < 0) {
                            // swipe left -> siguiente
                            if (currentIndex < imageUrlList.lastIndex) setImageAt(currentIndex + 1)
                        } else {
                            // swipe right -> anterior
                            if (currentIndex > 0) setImageAt(currentIndex - 1)
                        }
                        return true
                    }
                    return false
                }
            }
        )

        imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    // =========================================================
    // FULLSCREEN (VIEWPAGER + ZOOM)
    // =========================================================
    private fun openFullScreenGallery(startIndex: Int) {
        if (imageUrlList.isEmpty()) return

        val i = Intent(this, FullScreenGalleryActivity::class.java)
        i.putStringArrayListExtra("images", ArrayList(imageUrlList))
        i.putExtra("startIndex", startIndex)
        startActivityForResult(i, REQUEST_FULLSCREEN)
    }

    @Deprecated("Using startActivityForResult for simplicity with existing codebase.")
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_FULLSCREEN && resultCode == RESULT_OK) {
            val index = data?.getIntExtra("index", currentIndex) ?: currentIndex
            setImageAt(index)
        }
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
                        withContext(Dispatchers.Main) { showErrorDialog("Error ${response.code}") }
                        return@use
                    }

                    val json = JSONObject(body)
                    val animal = json.getJSONObject("animal")
                    val recomendaciones = json.optJSONArray("recomendaciones")

                    withContext(Dispatchers.Main) {
                        populateTextFieldsFromApi(animal)
                        populateImages(animal.optJSONArray("imagenes"))
                        setupRecommendationsFromApi(recomendaciones)
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
    // UI POPULATION (TEXTOS + CARDS)
    // =========================================================
    private fun populateTextFieldsFromApi(animal: JSONObject) {

        findViewById<TextView>(R.id.tvNombreComun).text =
            animal.optString("nombre_comun", "N/A")

        findViewById<TextView>(R.id.tvNombreIngles).text =
            animal.optString("nombre_ingles", "N/A")

        // (Según tu XML actual, tvNombreCientifico está en overlay; si luego lo movés al card,
        // este findViewById seguirá funcionando mientras exista el ID)
        findViewById<TextView>(R.id.tvNombreCientifico).text =
            animal.optString("nombre_cientifico", "N/A")

        findViewById<TextView>(R.id.tvPaisOrigen).text =
            animal.optString("pais_origen", "N/A")

        descripcion = animal
            .optJSONObject("descripcion")
            ?.optJSONObject("descripcion")
            ?.optString("texto", "Descripción no disponible")
            ?: "Descripción no disponible"

        isFavorite = animal.optBoolean("es_favorito", false)
        updateFavoriteUI()
        toggleDescripcion(descripcion)

        // ---------- TAXONOMÍA ----------
        animal.optJSONObject("taxonomia")?.let { tax ->
            findViewById<TextView>(R.id.tvReino).text = tax.optString("reino", "N/A")
            findViewById<TextView>(R.id.tvFilo).text = tax.optString("filo", "N/A")
            findViewById<TextView>(R.id.tvOrden).text = tax.optString("orden", "N/A")
            findViewById<TextView>(R.id.tvFamilia).text = tax.optString("familia", "N/A")
            findViewById<TextView>(R.id.tvGenero).text = tax.optString("genero", "N/A")

            // Card: Clase
            bindInfoCard(
                includeRootId = R.id.cardClase,
                iconRes = R.drawable.ic_category_24dp,
                valueText = tax.optString("clase", "N/A"),
                labelText = "Clase"
            )
        }

        // ---------- DISTRIBUCIÓN / HÁBITAT / ESTADO ----------
        animal.optJSONObject("distribucion")?.let { dist ->
            val core = dist.optJSONObject("distribucion")

            findViewById<TextView>(R.id.tvPaisesExtant).text =
                core?.optJSONArray("paises_extant")?.toList()?.joinToString(", ") ?: "N/A"

            findViewById<TextView>(R.id.tvPaisesExtinct).text =
                core?.optJSONArray("paises_extinct")?.toList()?.joinToString(", ") ?: "N/A"

            findViewById<TextView>(R.id.tvFuenteConservacion).text =
                dist.optJSONObject("fuente")?.optString("nombre", "N/A") ?: "N/A"

            findViewById<TextView>(R.id.tvhabitat).text =
                dist.optJSONArray("habitat")?.toList()?.joinToString(", ") ?: "N/A"

            val estadoTxt =
                dist.optString("estatus_conservacion", "N/A")


            // Card: Estado
            bindInfoCard(
                includeRootId = R.id.cardEstado,
                iconRes = R.drawable.ic_info_24,
                valueText = estadoTxt,
                labelText = "Estado"
            )
        }
    }
    private fun updateFavoriteUI() {
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.baseline_favorite_24)
            btnFavorite.setColorFilter(getColor(R.color.red))
        } else {
            btnFavorite.setImageResource(R.drawable.ic_favorite_border_24dp)
            btnFavorite.setColorFilter(getColor(R.color.white))
        }
    }
    // Helper para tus cards: item_info_card
    private fun bindInfoCard(
        includeRootId: Int,
        iconRes: Int,
        valueText: String,
        labelText: String
    ) {
        val root = findViewById<View>(includeRootId)
        val icon = root.findViewById<ImageView>(R.id.icon)
        val value = root.findViewById<TextView>(R.id.value)
        val label = root.findViewById<TextView>(R.id.label)

        icon.setImageResource(iconRes)
        value.text = valueText
        label.text = labelText
    }

    // =========================================================
    // IMÁGENES (LISTA + ADAPTER)
    // =========================================================
    private fun populateImages(arr: JSONArray?) {
        imageUrlList.clear()
        currentIndex = 0

        if (arr == null || arr.length() == 0) {
            galleryAdapter.submitList(emptyList())
            return
        }

        val ordered = (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .sortedByDescending { it.optInt("es_principal", 0) }
            .mapNotNull { obj ->
                obj.optString("url_archivo", null)?.takeIf { it.isNotBlank() }
            }

        imageUrlList.addAll(ordered)

        galleryAdapter.submitList(imageUrlList)
        if (imageUrlList.isNotEmpty()) setImageAt(0)
        imageView.post { showCurrentImage() }
    }

    // =========================================================
    // RECOMENDACIONES
    // =========================================================
    private fun setupRecommendationsFromApi(recs: JSONArray?) {

        val rv = findViewById<RecyclerView>(R.id.rvRecomendaciones)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        if (recs == null || recs.length() == 0) {
            rv.visibility = View.GONE
            return
        }

        val items = mutableListOf<RecommendationItem>()
        for (i in 0 until recs.length()) {
            val obj = recs.getJSONObject(i)
            items.add(
                RecommendationItem(
                    id = obj.optInt("id", -1),
                    name = obj.optString("nombre", ""),
                    imageUrl = obj.optString("imagen_principal", "")
                )
            )
        }

        rv.visibility = View.VISIBLE
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
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop()
                btn.setImageResource(R.drawable.ic_play_circle_black_24dp)
            } else {
                textToSpeech.speak(
                    findAllText(findViewById(R.id.main)),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "tts"
                )
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
        if (::textToSpeech.isInitialized) textToSpeech.shutdown()
        super.onDestroy()
    }
}
