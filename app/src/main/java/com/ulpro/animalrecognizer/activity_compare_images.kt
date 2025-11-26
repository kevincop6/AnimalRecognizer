package com.ulpro.animalrecognizer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ActivityCompareImages : AppCompatActivity() {

    private lateinit var selectedImageView: ImageView
    private val serverUrl = "${ServerConfig.BASE_URL}animal_detector.php"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null && result.data!!.data != null) {
            val imageUri: Uri = result.data!!.data!!
            val source = ImageDecoder.createSource(contentResolver, imageUri)
            val bitmap = ImageDecoder.decodeBitmap(source)
            selectedImageView.setImageBitmap(bitmap)
            uploadImage(bitmap)
        }
    }

    private var lastBitmap: Bitmap? = null // Variable para almacenar el último Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compare_images)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        val selectImageButton = findViewById<Button>(R.id.selectImageButton)
        val retryButton = findViewById<Button>(R.id.retryButton) // Botón de reintento
        selectedImageView = findViewById(R.id.selectedImageView)

        selectImageButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
            } else {
                openGallery()
            }
        }

        retryButton.setOnClickListener {
            lastBitmap?.let {
                uploadImage(it) // Reintenta con el último Bitmap almacenado
            } ?: Toast.makeText(this, "No hay imagen para reintentar", Toast.LENGTH_SHORT).show()
        }


    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openGallery()
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImage(bitmap: Bitmap) {
        lastBitmap = bitmap // Almacena el Bitmap antes de enviarlo
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val file = File(cacheDir, "temp_image.jpg")
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

                if (!response.isSuccessful || responseBody == null) {
                    withContext(Dispatchers.Main) {
                        SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.ERROR_TYPE)
                            .setTitleText("Error")
                            .setContentText("Error en la solicitud: ${response.message}")
                            .setConfirmText("Cerrar")
                            .show()
                    }
                    return@launch
                }

                val jsonResponse = JSONObject(responseBody)
                val status = jsonResponse.getString("status")
                val animal = jsonResponse.optString("Animal", "Desconocido")
                val confidence = jsonResponse.optDouble("confidence", 0.0) * 100
                val message = jsonResponse.optString("message", "Sin mensaje")

                withContext(Dispatchers.Main) {
                    when (status) {
                        "success" -> {
                            SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.SUCCESS_TYPE)
                                .setTitleText("¡Éxito!")
                                .setContentText("$message\nAnimal: $animal\nConfianza: ${"%.2f".format(Locale.US, confidence)}%")
                                .setConfirmText("Aceptar")
                                .show()
                        }
                        "unknown" -> {
                            SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.WARNING_TYPE)
                                .setTitleText("Desconocido")
                                .setContentText("$message\nAnimal: $animal\nConfianza: ${"%.2f".format(Locale.US, confidence)}%")
                                .setConfirmText("Aceptar")
                                .show()
                        }
                        "error" -> {
                            SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.ERROR_TYPE)
                                .setTitleText("Error")
                                .setContentText("$message\nAnimal: $animal")
                                .setConfirmText("Cerrar")
                                .show()
                        }
                        else -> {
                            SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.ERROR_TYPE)
                                .setTitleText("Error")
                                .setContentText("Respuesta desconocida del servidor")
                                .setConfirmText("Cerrar")
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    SweetAlertDialog(this@ActivityCompareImages, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText(e.message ?: "Error desconocido")
                        .setConfirmText("Cerrar")
                        .show()
                }
            }
        }
    }

    private fun showErrorDialog(errorMessage: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setMessage(errorMessage)
        builder.setPositiveButton("Copy") { dialog, _ ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Error Message", errorMessage)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        builder.setNegativeButton("Close") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
}