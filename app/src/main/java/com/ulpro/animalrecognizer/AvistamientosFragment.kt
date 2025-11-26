package com.ulpro.animalrecognizer

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.toString
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.Exception
import kotlin.toString
import android.widget.LinearLayout
import android.speech.tts.UtteranceProgressListener
import kotlin.inc
import kotlin.text.compareTo
import kotlin.times

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private lateinit var recyclerView: RecyclerView
private lateinit var adapter: AnimalAdapter
private lateinit var sharedPreferences: SharedPreferences
private var allAnimals = mutableListOf<Animal>()
private var currentPage = 1
private val itemsPerPage = 8
private var isLoading = false
private var hasMoreItems = true
private lateinit var selectedImageView: ImageView
private val serverUrl = "${ServerConfig.BASE_URL}animal_detector.php"
private var lastBitmap: Bitmap? = null // Variable para almacenar el último Bitmap
private lateinit var textToSpeech: TextToSpeech
/**
 * A simple [Fragment] subclass.
 * Use the [AvistamientosFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AvistamientosFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflar el diseño del fragmento
        return inflater.inflate(R.layout.fragment_avistamientos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
// Cargar animales desde el caché al iniciar el fragmento
        loadAnimalsBasedOnWorkerState()
        // Verificar permisos al iniciar el fragmento
        checkPermissions()
        sharedPreferences = requireActivity().getSharedPreferences("userSession", MODE_PRIVATE)

        // Configuración del texto de bienvenida
        val userEmailTextView: TextView = view.findViewById(R.id.bienvenidoTextView)
        val nombreUsuario = sharedPreferences.getString("nombre_usuario", "Usuario")
        userEmailTextView.text = getString(R.string.bienvenido, nombreUsuario)
        selectedImageView = view.findViewById(R.id.selectedImageView)
        val selectImageButton = view.findViewById<Button>(R.id.selectImageButton)
        val retryButton = view.findViewById<Button>(R.id.retryButton) // Botón de reintento
        selectImageButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
            } else {
                openGallery()
            }
        }
        retryButton.setOnClickListener {
            lastBitmap?.let {
                classifyImageLocally(it) // Reintenta con el último Bitmap almacenado
            } ?: Toast.makeText(requireContext(), "No hay imagen para reintentar", Toast.LENGTH_SHORT).show()
        }

        // Configuración del RecyclerView
        recyclerView = view.findViewById(R.id.recyclerView_animals)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        adapter = AnimalAdapter(mutableListOf())
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!recyclerView.canScrollHorizontally(1) && !isLoading && hasMoreItems) {
                    isLoading = true // Evita múltiples llamadas simultáneas
                    loadNextPage()
                }
            }
        })



        // Configuración del EditText para búsqueda
        val searchEditText: EditText = view.findViewById(R.id.searchView_avistamientos)
        searchEditText.setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }


        val camaraButton = view.findViewById<Button>(R.id.camaraButton)
        camaraButton.setOnClickListener {
            takePictureLauncher.launch(null) // Abrir la cámara
        }
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(requireContext(), "Error al inicializar TextToSpeech", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            selectedImageView.setImageBitmap(bitmap) // Mostrar la imagen capturada
            classifyImageLocally(bitmap) // Subir la imagen capturada
        } else {
            Toast.makeText(requireContext(), "No se capturó ninguna imagen", Toast.LENGTH_SHORT).show()
        }
    }
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsNeeded.toTypedArray(), 1)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) { // Manejo de permisos
            var cameraPermissionGranted = false
            var readMediaPermissionGranted = false

            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.CAMERA -> {
                        cameraPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                    Manifest.permission.READ_MEDIA_IMAGES -> {
                        readMediaPermissionGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                }
            }

            if (!cameraPermissionGranted || !readMediaPermissionGranted) {
                Toast.makeText(requireContext(), "Permisos denegados", Toast.LENGTH_SHORT).show()
            }
        }
    }
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null) // Abrir la cámara
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }
    private fun loadNextPage() {
        if (isLoading || !hasMoreItems) return

        isLoading = true

        val start = (currentPage - 1) * itemsPerPage
        val end = minOf(start + itemsPerPage, allAnimals.size)

        if (start < allAnimals.size) {
            val animalsToLoad = allAnimals.subList(start, end)
            adapter.addAnimals(animalsToLoad)

            currentPage++
            if (end >= allAnimals.size) {
                hasMoreItems = false
            }
        }

        isLoading = false
    }

    fun loadAnimalsBasedOnWorkerState() {
        val workManager = WorkManager.getInstance(requireContext())
        workManager.getWorkInfosForUniqueWorkLiveData("UpdateAnimalsWorker").observe(viewLifecycleOwner) { workInfos ->
            val workInfo = workInfos.firstOrNull()
            when (workInfo?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    // Si el Worker está activo, cargar los datos en caché
                    loadCachedAnimals()
                }
                WorkInfo.State.FAILED -> {
                    // Si el Worker falló, notificar el error
                    Toast.makeText(requireContext(), "Error al actualizar los datos", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Si el Worker no está activo y no falló, cargar los datos completos
                    loadCompleteAnimals()
                }
            }
        }
    }

    private fun loadCachedAnimals() {
        val cachedAnimals = sharedPreferences.getString("cached_animals", null)
        if (cachedAnimals != null) {
            Thread {
                val animalsArray = JSONArray(cachedAnimals)
                val newAnimals = mutableListOf<Animal>()

                for (i in 0 until animalsArray.length()) {
                    val animalJson = animalsArray.getJSONObject(i)
                    val animal = Animal(
                        animalJson.getInt("id"),
                        animalJson.getString("name"),
                        animalJson.getString("imageBase64")
                    )
                    if (allAnimals.none { it.id == animal.id }) {
                        allAnimals.add(animal)
                        newAnimals.add(animal)
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    if (newAnimals.isNotEmpty()) {
                        adapter.addAnimals(newAnimals)
                    }
                }
            }.start()
        } else {
            Toast.makeText(requireContext(), "No hay animales almacenados disponibles", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCompleteAnimals() {
        // Implementa aquí la lógica para cargar los datos completos desde el servidor o base de datos
        loadCachedAnimals()
    }
    private fun uploadImage(bitmap: Bitmap) {
        lastBitmap = bitmap // Almacena el Bitmap antes de enviarlo

        val loadingDialog = SweetAlertDialog(requireContext(), SweetAlertDialog.PROGRESS_TYPE)
        loadingDialog.titleText = "Subiendo imagen..."
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val file = File(requireContext().cacheDir, "temp_image.jpg")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            fos.close()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "imagen",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()

                    if (!response.isSuccessful || responseBody == null) {
                        SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE)
                            .setTitleText("Error")
                            .setContentText("Error en la solicitud: ${response.message}")
                            .setConfirmText("Cerrar")
                            .show()
                        return@withContext
                    }

                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")
                    val animalName = jsonResponse.optString("Animal", "Desconocido")

                    if (status == "success") {
                        val cachedAnimals = sharedPreferences.getString("cached_animals", null)
                        if (cachedAnimals != null) {
                            val animalsArray = JSONArray(cachedAnimals)
                            val foundAnimal = (0 until animalsArray.length())
                                .map { animalsArray.getJSONObject(it) }
                                .find { it.getString("name").equals(animalName, ignoreCase = true) }

                            if (foundAnimal != null) {
                                val animalId = foundAnimal.getInt("id")
                                SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE)
                                    .setTitleText("¡Animal encontrado!")
                                    .setContentText("Nombre: $animalName")
                                    .setConfirmText("Ver detalles")
                                    .setConfirmClickListener {
                                        val intent = Intent(requireContext(), details_animals::class.java)
                                        intent.putExtra("animalId", animalId) // Enviar el animalId
                                        startActivity(intent)
                                    }
                                    .show()
                            } else {
                                SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE)
                                    .setTitleText("No encontrado en caché")
                                    .setContentText("El animal $animalName no está en el caché.")
                                    .setConfirmText("Cerrar")
                                    .show()
                            }
                        } else {
                            SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE)
                                .setTitleText("Caché vacío")
                                .setContentText("No hay animales almacenados en caché.")
                                .setConfirmText("Cerrar")
                                .show()
                        }
                    } else {
                        SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE)
                            .setTitleText("Error")
                            .setContentText("Respuesta del servidor: $status")
                            .setConfirmText("Cerrar")
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingDialog.dismissWithAnimation()
                    SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText(e.message ?: "Error desconocido")
                        .setConfirmText("Cerrar")
                        .show()
                }
            }
        }
    }

    private fun classifyImageLocally(bitmap: Bitmap) {
        lastBitmap = bitmap // Almacena el Bitmap antes de enviarlo
        val loadingDialog = SweetAlertDialog(requireContext(), SweetAlertDialog.PROGRESS_TYPE).apply {
            titleText = "Analizando imagen..."
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Convertir el bitmap a formato mutable si es necesario
                val processableBitmap = convertToProcessableBitmap(bitmap)

                // 1. Cargar modelo y clases desde assets
                val interpreter = loadModel()
                val classMapping = loadClassMapping()

                // 2. Preprocesar la imagen
                val processedImage = preprocessImage(processableBitmap)

                // 3. Hacer la predicción
                val predictionResult = predictImage(interpreter, processedImage, classMapping)
                val className = predictionResult.first
                val confidence = predictionResult.second

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

    // Función para cargar el modelo TensorFlow Lite
    private fun loadModel(): Interpreter {
        val assetFileDescriptor = requireContext().assets.openFd("model.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val modelBuffer = inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
        return Interpreter(modelBuffer)
    }

    // Función para cargar el mapeo de clases desde el JSON
    private fun loadClassMapping(): Map<String, String> {
        val inputStream = requireContext().assets.open("classes.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)

        val classMappingObject = jsonObject.getJSONObject("class_mapping")
        return classMappingObject.keys().asSequence().associate { key ->
            key to classMappingObject.getString(key)
        }
    }

    // Función para convertir bitmaps HARDWARE a procesables
    private fun convertToProcessableBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }
    }

    // Función de preprocesamiento de imagen
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        val intValues = IntArray(inputSize * inputSize)

        if (resizedBitmap.config == Bitmap.Config.HARDWARE) {
            throw IllegalStateException("El bitmap redimensionado sigue siendo HARDWARE")
        }

        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixelValue in intValues) {
            byteBuffer.putFloat((Color.red(pixelValue) / 255.0f) * 2.0f - 1.0f)
            byteBuffer.putFloat((Color.green(pixelValue) / 255.0f) * 2.0f - 1.0f)
            byteBuffer.putFloat((Color.blue(pixelValue) / 255.0f) * 2.0f - 1.0f)
        }

        return byteBuffer
    }

    // Función de predicción - devuelve explícitamente Pair<String, Float>
    private fun predictImage(
        interpreter: Interpreter,
        inputBuffer: ByteBuffer,
        classMapping: Map<String, String>
    ): Pair<String, Float> {
        val output = Array(1) { FloatArray(classMapping.size) }
        interpreter.run(inputBuffer, output)

        val predictions = output[0]
        val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
        val confidence = predictions[maxIndex]

        return classMapping[maxIndex.toString()]?.let { it to confidence } ?: ("Desconocido" to 0f)
    }

    // Función para manejar el resultado de la predicción
    private fun handlePredictionResult(className: String, confidence: Float) {
        val cachedAnimals = sharedPreferences.getString("cached_animals", null)

        if (cachedAnimals.isNullOrEmpty()) {
            showNotFoundDialog(className)
            return
        }

        try {
            val animalsArray = JSONArray(cachedAnimals)
            val foundAnimal = (0 until animalsArray.length())
                .asSequence()
                .map { animalsArray.getJSONObject(it) }
                .firstOrNull { animal ->
                    className.equals(animal.optString("scientific_name", ""), ignoreCase = true) ||
                            className.equals(animal.optString("name", ""), ignoreCase = true)
                }

            if (foundAnimal != null) {
                showAnimalFoundDialog(foundAnimal, className, confidence)
            } else {
                showNotFoundDialog(className)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorDialog("Error al procesar los datos del caché: ${e.message}")
        }
    }

    // Función para mostrar diálogo de animal encontrado con manejo seguro de Base64
    private fun showAnimalFoundDialog(animalData: JSONObject, scientificName: String, confidence: Float) {
        val confidencePercentage = confidence * 100
        val confidenceLevel = when {
            confidencePercentage >= 80 -> "alta confianza"
            confidencePercentage in 60.0..79.99 -> "confianza media"
            confidencePercentage in 40.0..59.99 -> "confianza mínima"
            else -> "Imagen no clara"
        }

        // 1. Extracción y validación de la cadena Base64
        val imageBase64 = animalData.optString("imageBase64", "").takeIf { it.isNotBlank() } ?: run {
            println("Advertencia: No se encontró imagen Base64 en los datos")
            showDialogWithoutImage(scientificName, confidencePercentage, confidenceLevel, animalData)
            return
        }

        // 2. Decodificación segura de Base64 a Bitmap
        val decodedBitmap = decodeBase64ToBitmap(imageBase64) ?: run {
            println("Error: No se pudo decodificar la imagen Base64")
            showDialogWithoutImage(scientificName, confidencePercentage, confidenceLevel, animalData)
            return
        }

        // Texto a leer
        val textToSpeak = "¡Especie identificada! ${formatScientificName(scientificName)} cuenta con una Confianza del ${"%.2f".format(confidencePercentage)}% la cual es una $confidenceLevel"

        // Leer el texto en voz alta
        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)

        // Crear un diseño personalizado para el diálogo
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Configurar la imagen redimensionada
        val scaledBitmap = Bitmap.createScaledBitmap(decodedBitmap, 600, 400, true)
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(scaledBitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Configurar el texto con "Nombre" y "Confianza"
        val textView = TextView(requireContext()).apply {
            text = """
        Nombre: ${formatScientificName(scientificName)}
        Confianza: ${"%.2f".format(confidencePercentage)}% ($confidenceLevel)
        """.trimIndent()
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setPadding(0, 16, 0, 0)
        }

        // Agregar la imagen y el texto al diseño
        layout.addView(imageView)
        layout.addView(textView)

        // Mostrar el diálogo con el diseño personalizado
        SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            setCustomView(layout)
            confirmText = "Ver detalles"
            setConfirmClickListener {
                Intent(requireContext(), details_animals::class.java).apply {
                    putExtra("animalId", animalData.optInt("id", -1))
                    putExtra("scientificName", scientificName)
                    startActivity(this)
                }
            }
            show()
        }
    }

    // Función para decodificar Base64 a Bitmap de forma segura
    private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
        return try {
            // Limpiar la cadena Base64 (eliminar metadatos si existen)
            val pureBase64 = base64String.substringAfterLast(",")

            // Validar formato Base64
            if (!pureBase64.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
                println("Error: Formato Base64 inválido")
                return null
            }

            // Decodificar con manejo de memoria
            val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)

            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, this)

                // Calcular muestreo para reducir memoria
                inSampleSize = calculateInSampleSize(this, 800, 800)
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.RGB_565

                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, this)
            }
        } catch (e: IllegalArgumentException) {
            println("Error de formato Base64: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            System.gc()
            println("Error de memoria al decodificar imagen")
            try {
                // Intentar con configuración más reducida
                BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }.let { options ->
                    val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
                }
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            println("Error desconocido al decodificar: ${e.message}")
            null
        }
    }

    // Función para calcular el factor de muestreo
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // Función para crear archivo temporal
    private fun createTempImageFile(bitmap: Bitmap): String? {
        return try {
            val file = File.createTempFile("temp_img_", ".jpg", requireContext().cacheDir).apply {
                createNewFile()
            }

            FileOutputStream(file).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                    throw Exception("Error al comprimir la imagen")
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            println("Error al crear archivo temporal: ${e.message}")
            null
        } catch (e: SecurityException) {
            println("Error de permisos: ${e.message}")
            null
        }
    }

    // Función para mostrar diálogo sin imagen (fallback)
    private fun showDialogWithoutImage(
        scientificName: String,
        confidencePercentage: Float,
        confidenceLevel: String,
        animalData: JSONObject
    ) {
        val textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val textToSpeak = """
            ¡Especie identificada!
            Nombre: ${formatScientificName(scientificName)}
            Confianza: ${"%.2f".format(confidencePercentage)}% ($confidenceLevel)
            """.trimIndent()
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            contentText = """
        Nombre: ${formatScientificName(scientificName)}
        Confianza: ${"%.2f".format(confidencePercentage)}% ($confidenceLevel)
        
        Nota: No se pudo cargar la imagen de referencia
        """.trimIndent()
            confirmText = "Ver detalles"
            setConfirmClickListener {
                Intent(requireContext(), details_animals::class.java).apply {
                    putExtra("animalId", animalData.optInt("id", -1))
                    putExtra("scientificName", scientificName)
                    startActivity(this)
                }
            }
            show()
        }
    }

    // Función para mostrar diálogo con imagen en memoria (sin archivo temporal)
    private fun showDialogWithImageInMemory(
        bitmap: Bitmap,
        scientificName: String,
        confidencePercentage: Float,
        confidenceLevel: String,
        animalData: JSONObject
    ) {
        val dialog = SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            contentText = """
        Nombre: ${formatScientificName(scientificName)}
        Confianza: ${"%.2f".format(confidencePercentage)}% ($confidenceLevel)
        """.trimIndent()
            confirmText = "Ver detalles"
            setConfirmClickListener {
                Intent(requireContext(), details_animals::class.java).apply {
                    putExtra("animalId", animalData.optInt("id", -1))
                    putExtra("scientificName", scientificName)

                    // Pasar la imagen como Base64 como último recurso
                    encodeBitmapToBase64(bitmap)?.let { putExtra("imageBase64", it) }

                    startActivity(this)
                }
            }
        }

        // Configurar imagen redimensionada para el diálogo
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 600, 400, true)
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(scaledBitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        dialog.setCustomView(imageView)
        dialog.show()
    }

    // Función para mostrar diálogo completo con imagen y datos
    private fun showDialogWithImage(
        bitmap: Bitmap,
        scientificName: String,
        confidencePercentage: Float,
        confidenceLevel: String,
        animalData: JSONObject
    ) {
        val dialog = SweetAlertDialog(requireContext(), SweetAlertDialog.SUCCESS_TYPE).apply {
            titleText = "¡Especie identificada!"
            confirmText = "Ver detalles"
            setConfirmClickListener {
                Intent(requireContext(), details_animals::class.java).apply {
                    putExtra("animalId", animalData.optInt("id", -1))
                    putExtra("scientificName", scientificName)
                    startActivity(this)
                }
            }
        }

        // Crear un diseño personalizado para combinar imagen y texto
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Configurar la imagen redimensionada
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 600, 400, true)
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(scaledBitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        // Configurar el texto con "Nombre" y "Confianza"
        val textView = TextView(requireContext()).apply {
            text = """
        Nombre: ${formatScientificName(scientificName)}
        Confianza: ${"%.2f".format(confidencePercentage)}% ($confidenceLevel)
        """.trimIndent()
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setPadding(0, 16, 0, 0)
        }

        // Agregar la imagen y el texto al diseño
        layout.addView(imageView)
        layout.addView(textView)

        // Establecer el diseño personalizado en el diálogo
        dialog.setCustomView(layout)
        dialog.show()
    }

    // Función auxiliar para convertir Bitmap a Base64 (como último recurso)
    private fun encodeBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                val bytes = baos.toByteArray()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            println("Error al convertir bitmap a Base64: ${e.message}")
            null
        }
    }

    // Función para formatear nombres científicos
    private fun formatScientificName(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            if (word.all { it.isLowerCase() }) word else word.toLowerCase().capitalize()
        }
    }

    // Función para mostrar diálogo de no encontrado
    private fun showNotFoundDialog(scientificName: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.WARNING_TYPE).apply {
            titleText = "Especie no registrada"
            contentText = "La especie ${formatScientificName(scientificName)} no está en los registros."
            confirmText = "Cerrar"
            show()
        }
    }

    // Función para mostrar errores
    private fun showErrorDialog(message: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE).apply {
            titleText = "Error"
            contentText = message
            confirmText = "Cerrar"
            show()
        }
    }
    override fun onResume() {
        super.onResume()
        loadAnimalsBasedOnWorkerState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos de TextToSpeech
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }


}