package com.ulpro.animalrecognizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.ulpro.animalrecognizer.ml.AnimalImageClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

class LiveScanActivity : AppCompatActivity() {

    // =====================
    // UI
    // =====================
    private lateinit var rootView: View
    private lateinit var previewView: PreviewView
    private lateinit var focusBox: View

    private lateinit var speciesCard: View
    private lateinit var imgAnimal: ImageView
    private lateinit var txtAnimalName: TextView
    private lateinit var btnOpenDetails: ImageButton

    private lateinit var cameraControls: View
    private lateinit var btnClose: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnShutter: ImageButton

    // =====================
    // Camera
    // =====================
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    // =====================
    // Animations
    // =====================
    private lateinit var scanPulse: Animation

    // =====================
    // ML
    // =====================
    private lateinit var classifier: AnimalImageClassifier

    // =====================
    // Gallery Launcher
    // Contrato que abre el selector de imágenes del sistema.
    // Solo actúa si el uri recibido no es nulo.
    // =====================
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { loadBitmapFromUri(it)?.let { bmp -> classifyBitmap(bmp) } }
        }

    // =====================================================================
    // PERMISSION LAUNCHER
    // Contrato moderno para solicitar UN permiso en tiempo de ejecución.
    // La lambda recibe `isGranted: Boolean` con el resultado del usuario.
    // Se registra aquí (antes de onCreate) como exige el ciclo de vida.
    // =====================================================================
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // ✅ El usuario concedió el permiso → iniciar la cámara normalmente
                startCamera()
            } else {
                // ❌ El usuario rechazó el permiso → decidir qué mostrar
                handleCameraPermissionDenied()
            }
        }

    // ======================================================
    // LIFECYCLE
    // ======================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_scan)

        ServerConfig.initialize(this)
        classifier = AnimalImageClassifier(this)

        bindViews()
        loadAnimations()
        setupActions()
        setupOutsideTouchToDismiss()

        speciesCard.visibility = View.GONE
        focusBox.startAnimation(scanPulse)

        // 🔑 En lugar de llamar startCamera() directamente,
        // primero verificamos/solicitamos el permiso de cámara.
        checkAndRequestCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
        cameraExecutor.shutdown()
    }

    // ======================================================
    // PERMISSION LOGIC
    // ======================================================

    /**
     * Verifica si el permiso CAMERA ya fue concedido.
     * - Si ya fue concedido: inicia la cámara directamente.
     * - Si NO: verifica si debe mostrar una explicación (shouldShowRequestPermissionRationale)
     *   antes de lanzar el diálogo del sistema, o lanza el diálogo directo.
     *
     * shouldShowRequestPermissionRationale devuelve `true` solo si el usuario
     * ya rechazó el permiso UNA vez (sin marcar "No volver a preguntar").
     */
    private fun checkAndRequestCameraPermission() {
        when {
            // Caso 1: El permiso ya está concedido → iniciar cámara sin interrupciones
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }

            // Caso 2: El usuario rechazó el permiso antes → mostrar explicación contextual
            // antes de volver a solicitarlo, para que entienda por qué es necesario.
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }

            // Caso 3: Primera vez que se solicita (o el usuario marcó "No volver a preguntar")
            // → lanzar directamente el diálogo del sistema.
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Muestra un AlertDialog explicando POR QUÉ la app necesita la cámara.
     * Esto mejora la UX: el usuario entiende el contexto antes de que
     * aparezca el diálogo del sistema operativo.
     *
     * - "Permitir" → lanza el diálogo del sistema para conceder el permiso.
     * - "Cancelar" → cierra la Activity, ya que sin cámara no hay funcionalidad.
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de cámara requerido")
            .setMessage(
                "AnimalRecognizer necesita acceso a la cámara para identificar " +
                        "la fauna local de la Península de Osa en tiempo real. " +
                        "Sin este permiso la aplicación no puede funcionar."
            )
            .setPositiveButton("Permitir") { dialog, _ ->
                dialog.dismiss()
                // Lanzar el diálogo oficial del sistema operativo
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                // Sin cámara la Activity no tiene sentido → cerrar
                Toast.makeText(
                    this,
                    "El permiso de cámara es necesario para usar el escáner.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            // Evitar que el usuario cierre el diálogo tocando fuera sin decidir
            .setCancelable(false)
            .show()
    }

    /**
     * Maneja el caso en que el usuario DENEGÓ el permiso desde el launcher.
     *
     * Si shouldShowRequestPermissionRationale devuelve `false` aquí, significa
     * que el usuario marcó "No volver a preguntar", por lo que el diálogo
     * del sistema ya no aparecerá: hay que redirigir a Ajustes manualmente.
     *
     * Si devuelve `true`, solo lo rechazó una vez sin marcar esa opción:
     * mostramos un mensaje simple y cerramos.
     */
    private fun handleCameraPermissionDenied() {
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // El usuario marcó "No volver a preguntar" → solo Ajustes puede habilitarlo
            AlertDialog.Builder(this)
                .setTitle("Permiso bloqueado")
                .setMessage(
                    "El permiso de cámara fue bloqueado permanentemente. " +
                            "Ve a Ajustes → Aplicaciones → AnimalRecognizer → Permisos " +
                            "para habilitarlo manualmente."
                )
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    // Abrir la pantalla de ajustes de la app directamente
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cerrar") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            // Solo rechazó una vez, sin bloqueo permanente
            Toast.makeText(
                this,
                "Permiso denegado. La cámara es necesaria para escanear animales.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    // ======================================================
    // UI SETUP
    // ======================================================
    private fun bindViews() {
        rootView       = findViewById(android.R.id.content)
        previewView    = findViewById(R.id.previewView)
        focusBox       = findViewById(R.id.focusBox)

        speciesCard    = findViewById(R.id.speciesCard)
        imgAnimal      = findViewById(R.id.imgAnimal)
        txtAnimalName  = findViewById(R.id.txtAnimalName)
        btnOpenDetails = findViewById(R.id.btnOpenDetails)

        cameraControls = findViewById(R.id.cameraControls)
        btnClose       = findViewById(R.id.btnClose)
        btnGallery     = findViewById(R.id.btnGallery)
        btnShutter     = findViewById(R.id.btnShutter)
    }

    private fun loadAnimations() {
        scanPulse = AnimationUtils.loadAnimation(this, R.anim.scan_pulse)
    }

    private fun setupActions() {
        btnClose.setOnClickListener { finish() }

        btnGallery.setOnClickListener {
            hideSpeciesCard()
            pickImageLauncher.launch("image/*")
        }

        btnShutter.setOnClickListener {
            hideSpeciesCard()
            capturePhoto()
        }
    }

    // ======================================================
    // TOCAR FUERA DE LA TARJETA → OCULTAR
    // ======================================================
    private fun setupOutsideTouchToDismiss() {
        rootView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                speciesCard.visibility == View.VISIBLE
            ) {
                val loc   = IntArray(2)
                speciesCard.getLocationOnScreen(loc)

                val left   = loc[0]
                val top    = loc[1]
                val right  = left + speciesCard.width
                val bottom = top  + speciesCard.height

                val x = event.rawX.toInt()
                val y = event.rawY.toInt()

                if (x < left || x > right || y < top || y > bottom) {
                    hideSpeciesCard()
                }
            }
            false
        }
    }

    // ======================================================
    // CAMERAX
    // Solo se llama cuando el permiso CAMERA ya fue concedido.
    // ======================================================
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        imageCapture?.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = imageProxyToBitmap(image)
                    image.close()
                    bmp?.let { runOnUiThread { classifyBitmap(it) } }
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes  = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ======================================================
    // CORE FLOW (LOCAL)
    // ======================================================
    private fun classifyBitmap(bitmap: Bitmap) {
        focusBox.clearAnimation()

        CoroutineScope(Dispatchers.IO).launch {
            val result = classifier.classify(bitmap)
            val animal = findAnimalByName(result.className)

            runOnUiThread {
                if (animal != null) showSpeciesCard(animal)
            }
        }
    }

    // ======================================================
    // UI CARD
    // ======================================================
    private fun showSpeciesCard(animal: Animal) {
        txtAnimalName.text = animal.nombre

        if (!animal.imagenUrl.isNullOrBlank()) {
            Glide.with(this).load(animal.imagenUrl).into(imgAnimal)
        } else {
            imgAnimal.setImageResource(R.drawable.ic_animal_placeholder)
        }

        cameraControls.visibility = View.INVISIBLE
        cameraControls.isEnabled  = false

        btnOpenDetails.setOnClickListener {
            startActivity(
                Intent(this, details_animals::class.java)
                    .putExtra("animalId", animal.id)
            )
        }

        speciesCard.visibility = View.VISIBLE
        speciesCard.alpha      = 0f
        speciesCard.animate().alpha(1f).setDuration(220).start()
    }

    private fun hideSpeciesCard() {
        speciesCard.visibility    = View.GONE
        cameraControls.visibility = View.VISIBLE
        cameraControls.isEnabled  = true
        focusBox.startAnimation(scanPulse)
    }

    // ======================================================
    // CACHE LOCAL
    // ======================================================
    private fun findAnimalByName(name: String): Animal? {
        val file = getActiveAnimalsFile()
        if (!file.exists()) return null

        val arr = JSONArray(file.readText())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("nombre", "").equals(name, true)) {
                return Animal(
                    obj.getInt("id"),
                    obj.getString("nombre"),
                    obj.optString("imagen_url")
                )
            }
        }
        return null
    }

    private fun getActiveAnimalsFile(): File {
        val prefs = getSharedPreferences("animals_cache_meta", MODE_PRIVATE)
        val name  = prefs.getString("active_animals_file", null)
        return if (name.isNullOrBlank()) File("") else File(filesDir, name)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? =
        try {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
}