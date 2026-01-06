package com.ulpro.animalrecognizer

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import cn.pedant.SweetAlert.SweetAlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class AvistamientosFragment : Fragment() {

    // ---------------------------
    // UI
    // ---------------------------
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AnimalAdapter
    private lateinit var selectedImageView: ImageView
    private lateinit var recognitionMenuButton: MaterialButton

    // ---------------------------
    // Data
    // ---------------------------
    private val allAnimals = mutableListOf<Animal>()
    private var currentPage = 1
    private val itemsPerPage = 8
    private var isLoading = false
    private var hasMoreItems = true

    private var lastBitmap: Bitmap? = null
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_avistamientos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ServerConfig.initialize(requireContext())

        // ---------------------------
        // Views
        // ---------------------------
        selectedImageView = view.findViewById(R.id.selectedImageView)
        recognitionMenuButton = view.findViewById(R.id.recognitionMenuButton)

        view.findViewById<TextView>(R.id.bienvenidoTextView).text =
            getString(R.string.bienvenido, "Usuario")

        // ---------------------------
        // Permissions
        // ---------------------------
        checkPermissions()

        // ---------------------------
        // Text To Speech
        // ---------------------------
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(
                    requireContext(),
                    "Error al inicializar TextToSpeech",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ---------------------------
        // RecyclerView
        // ---------------------------
        recyclerView = view.findViewById(R.id.recyclerView_animals)
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        adapter = AnimalAdapter(mutableListOf())
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (!rv.canScrollHorizontally(1) && !isLoading && hasMoreItems) {
                    loadNextPage()
                }
            }
        })

        // ---------------------------
        // Dropdown button
        // ---------------------------
        recognitionMenuButton.setOnClickListener {
            showRecognitionMenu(it)
        }

        // ---------------------------
        // Search
        // ---------------------------
        view.findViewById<EditText>(R.id.searchView_avistamientos).setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // ---------------------------
        // Load animals
        // ---------------------------
        loadAnimalsBasedOnWorkerState()
    }

    override fun onResume() {
        super.onResume()
        loadAnimalsBasedOnWorkerState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    // ======================================================
    // BITMAP FIX (GALERÍA -> HARDWARE BITMAP)
    // ======================================================
    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }
    }

    // ======================================================
    // DROPDOWN MENU
    // ======================================================
    private fun showRecognitionMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_recognition, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {

                R.id.action_gallery -> {
                    if (hasGalleryPermission()) openGallery()
                    else requestGalleryPermission()
                    true
                }

                R.id.action_camera -> {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        takePictureLauncher.launch(null)
                    } else {
                        ActivityCompat.requestPermissions(
                            requireActivity(),
                            arrayOf(Manifest.permission.CAMERA),
                            100
                        )
                    }
                    true
                }

                R.id.action_retry -> {
                    lastBitmap?.let {
                        // Asegurar bitmap "software" incluso al reintentar
                        val safe = ensureSoftwareBitmap(it)
                        classifyImageLocally(safe)
                    } ?: Toast.makeText(
                        requireContext(),
                        "No hay imagen para reintentar",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    // ======================================================
    // PERMISSIONS
    // ======================================================
    private fun hasGalleryPermission(): Boolean {
        val perm =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        return ContextCompat.checkSelfPermission(requireContext(), perm) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestGalleryPermission() {
        val perm =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        ActivityCompat.requestPermissions(requireActivity(), arrayOf(perm), 101)
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }

        val galleryPerm =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), galleryPerm)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(galleryPerm)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), needed.toTypedArray(), 1)
        }
    }

    // ======================================================
    // GALLERY / CAMERA
    // ======================================================
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val safeBitmap = ensureSoftwareBitmap(it)
                selectedImageView.setImageBitmap(safeBitmap)
                classifyImageLocally(safeBitmap)
            } ?: Toast.makeText(
                requireContext(),
                "No se capturó ninguna imagen",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                val uri: Uri = result.data!!.data!!

                val source = ImageDecoder.createSource(requireActivity().contentResolver, uri)

                // IMPORTANTE: ImageDecoder puede devolver HARDWARE bitmap
                val rawBitmap = ImageDecoder.decodeBitmap(source)
                val safeBitmap = ensureSoftwareBitmap(rawBitmap)

                selectedImageView.setImageBitmap(safeBitmap)
                classifyImageLocally(safeBitmap)
            }
        }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // ======================================================
    // ANIMALS LOADING (WORKER / CACHE)
    // ======================================================
    private fun loadAnimalsBasedOnWorkerState() {
        val wm = WorkManager.getInstance(requireContext())
        wm.getWorkInfosForUniqueWorkLiveData("fetch_animals_weekly")
            .observe(viewLifecycleOwner) {
                loadAnimalsFromActiveFile()
            }
    }

    private fun loadAnimalsFromActiveFile() {
        val file = getActiveAnimalsFile()
        if (!file.exists()) return

        try {
            val arr = JSONArray(file.readText())
            allAnimals.clear()
            adapter.clear()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optInt("id", -1)
                val nombre = obj.optString("nombre", "")
                val imagenUrl = obj.optString("imagen_url", "")
                if (id != -1 && nombre.isNotBlank()) {
                    allAnimals.add(Animal(id, nombre, imagenUrl))
                }
            }

            currentPage = 1
            hasMoreItems = true
            loadNextPage()
        } catch (_: Exception) {
        }
    }

    private fun getActiveAnimalsFile(): File {
        val prefs = requireContext().getSharedPreferences("animals_cache_meta", Context.MODE_PRIVATE)
        val fileName = prefs.getString("active_animals_file", null)
        return if (fileName.isNullOrBlank()) File("") else File(requireContext().filesDir, fileName)
    }

    private fun loadNextPage() {
        if (isLoading || !hasMoreItems) return
        isLoading = true

        val start = (currentPage - 1) * itemsPerPage
        val end = minOf(start + itemsPerPage, allAnimals.size)

        if (start < allAnimals.size) {
            adapter.addAnimals(allAnimals.subList(start, end))
            currentPage++
            if (end >= allAnimals.size) hasMoreItems = false
        }

        isLoading = false
    }

    // ======================================================
    // IMAGE CLASSIFICATION (TFLITE)
    // ======================================================
    private fun classifyImageLocally(bitmap: Bitmap) {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        lastBitmap = safeBitmap

        val loadingDialog = SweetAlertDialog(
            requireContext(),
            SweetAlertDialog.PROGRESS_TYPE
        ).apply {
            titleText = "Analizando imagen..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val interpreter = loadModel()
                val mapping = loadClassMapping()
                val input = preprocessImage(safeBitmap)
                val (name, confidence) = predictImage(interpreter, input, mapping)

                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    handlePredictionResult(name, confidence)
                }

                interpreter.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    showErrorDialog("Error al clasificar: ${e.message}")
                }
            }
        }
    }

    private fun handlePredictionResult(className: String, confidence: Float) {
        val file = getActiveAnimalsFile()
        if (!file.exists()) {
            showNotFoundDialog(className)
            return
        }

        val arr = JSONArray(file.readText())
        val found = (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .firstOrNull { it.optString("nombre", "").equals(className, true) }

        if (found != null) showAnimalFoundDialog(found, className, confidence)
        else showNotFoundDialog(className)
    }

    private fun showAnimalFoundDialog(animalData: JSONObject, className: String, confidence: Float) {
        val nombre = animalData.optString("nombre", className)
        val imagenUrl = animalData.optString("imagen_url", "")
        val id = animalData.optInt("id", -1)

        val confidencePct = confidence * 100f

        val imageView = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            minimumHeight = 400
            if (imagenUrl.isNotBlank()) {
                Glide.with(this).load(imagenUrl).into(this)
            }
        }

        val textView = TextView(requireContext()).apply {
            text = "Nombre: $nombre\nConfianza: ${"%.1f".format(confidencePct)}%"
            textSize = 16f
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(imageView)
            addView(textView)
        }

        textToSpeech.speak(
            "Especie identificada: $nombre",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "animal_found"
        )

        SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            setCustomView(layout)
            confirmText = "Ver detalles"
            setConfirmClickListener {
                dismissWithAnimation()
                startActivity(
                    Intent(requireContext(), details_animals::class.java)
                        .putExtra("animalId", id)
                )
            }
            show()
        }
    }

    private fun showNotFoundDialog(name: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE).apply {
            titleText = "No encontrado"
            contentText = "La especie \"$name\" no está registrada."
            confirmText = "Cerrar"
            show()
        }
    }

    private fun showErrorDialog(message: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE).apply {
            titleText = "Error"
            contentText = message
            confirmText = "Cerrar"
            show()
        }
    }

    // ======================================================
    // TFLITE HELPERS
    // ======================================================
    private fun loadModel(): Interpreter {
        val afd = requireContext().assets.openFd("model.tflite")
        val input = FileInputStream(afd.fileDescriptor)
        val buffer = input.channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
        return Interpreter(buffer)
    }

    private fun loadClassMapping(): Map<String, String> {
        val json = requireContext().assets.open("classes.json")
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json).getJSONObject("class_mapping")
        return obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val size = 224

        // Por seguridad (si entra cualquier bitmap HARDWARE por otra ruta)
        val safeBitmap = ensureSoftwareBitmap(bitmap)

        val resized = Bitmap.createScaledBitmap(safeBitmap, size, size, true)

        val buffer = ByteBuffer
            .allocateDirect(4 * size * size * 3)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        resized.getPixels(pixels, 0, size, 0, 0, size, size)

        for (px in pixels) {
            buffer.putFloat(((px shr 16) and 0xFF) / 255f * 2f - 1f)
            buffer.putFloat(((px shr 8) and 0xFF) / 255f * 2f - 1f)
            buffer.putFloat((px and 0xFF) / 255f * 2f - 1f)
        }

        buffer.rewind()
        return buffer
    }

    private fun predictImage(
        interpreter: Interpreter,
        input: ByteBuffer,
        mapping: Map<String, String>
    ): Pair<String, Float> {

        val output = Array(1) { FloatArray(mapping.size) }
        interpreter.run(input, output)

        val idx = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val name = mapping[idx.toString()] ?: "Desconocido"
        val confidence = output[0][idx]

        return Pair(name, confidence)
    }
}
