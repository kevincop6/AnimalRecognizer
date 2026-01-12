package com.ulpro.animalrecognizer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.util.concurrent.Executors

class LiveScanActivity : AppCompatActivity() {

    // UI
    private lateinit var previewView: PreviewView
    private lateinit var focusBox: View
    private lateinit var bottomSheet: LinearLayout
    private lateinit var alertPill: LinearLayout
    private lateinit var txtAlert: TextView
    private lateinit var txtConfidence: TextView
    private lateinit var txtAnimalName: TextView
    private lateinit var txtDescription: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnGallery: ImageButton

    // Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Animations
    private lateinit var scanPulse: Animation
    private lateinit var sheetIn: Animation
    private lateinit var pillPop: Animation

    // Galería (SIN genéricos ambiguos)
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap != null) {
                showMockResult()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_scan)

        bindViews()
        loadAnimations()
        setupActions()
        startCamera()

        bottomSheet.visibility = View.GONE
        focusBox.startAnimation(scanPulse)
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        focusBox = findViewById(R.id.focusBox)
        bottomSheet = findViewById(R.id.bottomSheet)
        alertPill = findViewById(R.id.alertPill)
        txtAlert = findViewById(R.id.txtAlert)
        txtConfidence = findViewById(R.id.txtConfidence)
        txtAnimalName = findViewById(R.id.txtAnimalName)
        txtDescription = findViewById(R.id.txtDescription)
        btnClose = findViewById(R.id.btnClose)
        btnGallery = findViewById(R.id.btnGallery)
    }

    private fun loadAnimations() {
        scanPulse = AnimationUtils.loadAnimation(this, R.anim.scan_pulse)
        sheetIn = AnimationUtils.loadAnimation(this, R.anim.sheet_in)
        pillPop = AnimationUtils.loadAnimation(this, R.anim.pill_pop)
    }

    private fun setupActions() {
        btnClose.setOnClickListener { finish() }
        btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ----------------------------------------------------------------
    // CameraX (sin análisis todavía)
    // ----------------------------------------------------------------
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { image ->
                image.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    // ----------------------------------------------------------------
    // Galería helper
    // ----------------------------------------------------------------
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val input: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }

    // ----------------------------------------------------------------
    // Resultado MOCK (solo UI + animaciones)
    // ----------------------------------------------------------------
    private fun showMockResult() {
        focusBox.clearAnimation()

        txtAlert.text = "⚠️ poisonous"
        txtAlert.setTextColor(ContextCompat.getColor(this, R.color.red))

        txtAnimalName.text = "Bothriechis schlegelii"
        txtConfidence.text = "94%"
        txtDescription.text =
            "Victims who have been bitten may experience dizziness, nausea, headache, localized swelling and bleeding."

        bottomSheet.visibility = View.VISIBLE
        bottomSheet.startAnimation(sheetIn)
        alertPill.startAnimation(pillPop)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}