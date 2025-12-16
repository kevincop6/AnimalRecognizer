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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cn.pedant.SweetAlert.SweetAlertDialog
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AvistamientosFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AnimalAdapter
    private val allAnimals = mutableListOf<Animal>()
    private var currentPage = 1
    private val itemsPerPage = 8
    private var isLoading = false
    private var hasMoreItems = true

    private lateinit var selectedImageView: ImageView
    private var lastBitmap: Bitmap? = null
    private lateinit var textToSpeech: TextToSpeech

    private val serverUrl by lazy {
        ServerConfig.initialize(requireContext())
        ServerConfig.BASE_URL.trimEnd('/') + "/animal_detector.php"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_avistamientos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ServerConfig.initialize(requireContext())

        selectedImageView = view.findViewById(R.id.selectedImageView)

        // Bienvenida (ya no guardas nombre -> dejamos genérico)
        view.findViewById<TextView>(R.id.bienvenidoTextView).text = getString(R.string.bienvenido, "Usuario")

        // Permisos
        checkPermissions()

        // TTS
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(requireContext(), "Error al inicializar TextToSpeech", Toast.LENGTH_SHORT).show()
            }
        }

        // RecyclerView
        recyclerView = view.findViewById(R.id.recyclerView_animals)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
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

        // Botones
        view.findViewById<Button>(R.id.selectImageButton).setOnClickListener {
            if (hasGalleryPermission()) openGallery() else requestGalleryPermission()
        }

        view.findViewById<Button>(R.id.retryButton).setOnClickListener {
            lastBitmap?.let { classifyImageLocally(it) }
                ?: Toast.makeText(requireContext(), "No hay imagen para reintentar", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.camaraButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePictureLauncher.launch(null)
            } else {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 100)
            }
        }

        // Search abre otra Activity
        view.findViewById<EditText>(R.id.searchView_avistamientos).setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // Cargar animales desde el archivo activo (y observar worker si quieres)
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

    // ---------------------------
    // Permisos
    // ---------------------------
    private fun hasGalleryPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestGalleryPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(requireActivity(), arrayOf(perm), 101)
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }

        val galleryPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(requireContext(), galleryPerm) != PackageManager.PERMISSION_GRANTED) {
            needed.add(galleryPerm)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), needed.toTypedArray(), 1)
        }
    }

    // ---------------------------
    // Gallery / Camera
    // ---------------------------
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                selectedImageView.setImageBitmap(bitmap)
                classifyImageLocally(bitmap)
            } else {
                Toast.makeText(requireContext(), "No se capturó ninguna imagen", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                val imageUri: Uri = result.data!!.data!!
                val source = ImageDecoder.createSource(requireActivity().contentResolver, imageUri)
                val bitmap = ImageDecoder.decodeBitmap(source)
                selectedImageView.setImageBitmap(bitmap)
                classifyImageLocally(bitmap)
            }
        }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // ---------------------------
    // Animales: cargar desde archivo activo
    // ---------------------------
    private fun loadAnimalsBasedOnWorkerState() {
        // Si sigues usando un uniqueWorkName, ajusta el nombre aquí.
        // Si no quieres observar worker, puedes llamar directo a loadAnimalsFromActiveFile().
        val wm = WorkManager.getInstance(requireContext())

        wm.getWorkInfosForUniqueWorkLiveData("fetch_animals_weekly")
            .observe(viewLifecycleOwner) { workInfos ->
                val state = workInfos.firstOrNull()?.state
                when (state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> loadAnimalsFromActiveFile()
                    WorkInfo.State.FAILED -> {
                        Toast.makeText(requireContext(), "Error al actualizar los animales", Toast.LENGTH_SHORT).show()
                        loadAnimalsFromActiveFile()
                    }
                    else -> loadAnimalsFromActiveFile()
                }
            }
    }

    private fun loadAnimalsFromActiveFile() {
        val file = getActiveAnimalsFile()
        if (!file.exists()) {
            Toast.makeText(requireContext(), "No hay animales descargados aún", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonText = file.readText()
            val arr = JSONArray(jsonText)

            allAnimals.clear()
            adapter.clear() // asegúrate que tu adapter tenga clear(); si no, recrea adapter

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optInt("id", -1)
                val nombre = obj.optString("nombre", "")
                val imagenUrl = obj.optString("imagen_url", "")

                if (id != -1 && nombre.isNotBlank()) {
                    // Ajusta esto a tu data class Animal real:
                    // Si tu Animal es (id, name, imageBase64) cámbialo a (id, nombre, imagenUrl) en tu modelo.
                    allAnimals.add(Animal(id, nombre, imagenUrl))
                }
            }

            currentPage = 1
            hasMoreItems = true
            adapter.addAnimals(emptyList())
            loadNextPage()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error leyendo animales: ${e.message}", Toast.LENGTH_SHORT).show()
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
            val toLoad = allAnimals.subList(start, end)
            adapter.addAnimals(toLoad)
            currentPage++
            if (end >= allAnimals.size) hasMoreItems = false
        }

        isLoading = false
    }

    // ---------------------------
    // Clasificación local (mantengo tu lógica base)
    // ---------------------------
    private fun classifyImageLocally(bitmap: Bitmap) {
        lastBitmap = bitmap

        val loadingDialog = SweetAlertDialog(requireContext(), SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Analizando imagen..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val interpreter = loadModel()
                val classMapping = loadClassMapping()
                val input = preprocessImage(bitmap)
                val (className, confidence) = predictImage(interpreter, input, classMapping)

                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    handlePredictionResult(className, confidence)
                }

                interpreter.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    showErrorDialog("Error al clasificar: ${e.message ?: "Error desconocido"}")
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

        try {
            val arr = JSONArray(file.readText())
            val found = (0 until arr.length())
                .asSequence()
                .map { arr.getJSONObject(it) }
                .firstOrNull { obj ->
                    val nombre = obj.optString("nombre", "")
                    // Match simple (puedes mejorar con normalización)
                    nombre.equals(className, ignoreCase = true)
                }

            if (found != null) {
                showAnimalFoundDialog(found, className, confidence)
            } else {
                showNotFoundDialog(className)
            }
        } catch (e: Exception) {
            showErrorDialog("Error leyendo el caché: ${e.message}")
        }
    }

    private fun showAnimalFoundDialog(animalData: JSONObject, className: String, confidence: Float) {
        val nombre = animalData.optString("nombre", className)
        val imagenUrl = animalData.optString("imagen_url", "")
        val id = animalData.optInt("id", -1)

        val confidencePct = confidence * 100f

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val imageView = ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            minimumHeight = 400
        }

        if (imagenUrl.isNotBlank()) {
            Glide.with(this).load(imagenUrl).into(imageView)
        }

        val textView = TextView(requireContext()).apply {
            text = "Nombre: $nombre\nConfianza: ${"%.2f".format(confidencePct)}%"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }

        layout.addView(imageView)
        layout.addView(textView)

        val speakText = "Especie identificada: $nombre. Confianza ${"%.0f".format(confidencePct)} por ciento."
        textToSpeech.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "animal_found")

        SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            setCustomView(layout)
            confirmText = "Ver detalles"
            setConfirmClickListener {
                dismissWithAnimation()
                Intent(requireContext(), details_animals::class.java).apply {
                    putExtra("animalId", id)
                    startActivity(this)
                }
            }
            show()
        }
    }

    private fun showNotFoundDialog(name: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE).apply {
            titleText = "Especie no registrada"
            contentText = "La especie \"$name\" no está en los registros."
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

    // ---------------------------
    // TFLite helpers (igual que tu base)
    // ---------------------------
    private fun loadModel(): Interpreter {
        val afd = requireContext().assets.openFd("model.tflite")
        val input = FileInputStream(afd.fileDescriptor)
        val buffer = input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        return Interpreter(buffer)
    }

    private fun loadClassMapping(): Map<String, String> {
        val jsonString = requireContext().assets.open("classes.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val mapping = jsonObject.getJSONObject("class_mapping")
        return mapping.keys().asSequence().associateWith { k -> mapping.getString(k) }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val resized = Bitmap.createScaledBitmap(
            if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, true) else bitmap,
            inputSize, inputSize, true
        )

        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            byteBuffer.putFloat(r * 2f - 1f)
            byteBuffer.putFloat(g * 2f - 1f)
            byteBuffer.putFloat(b * 2f - 1f)
        }

        return byteBuffer
    }

    private fun predictImage(
        interpreter: Interpreter,
        inputBuffer: ByteBuffer,
        classMapping: Map<String, String>
    ): Pair<String, Float> {
        val output = Array(1) { FloatArray(classMapping.size) }
        interpreter.run(inputBuffer, output)

        val preds = output[0]
        val maxIndex = preds.indices.maxByOrNull { preds[it] } ?: 0
        val confidence = preds[maxIndex]
        val name = classMapping[maxIndex.toString()] ?: "Desconocido"
        return name to confidence
    }
}
