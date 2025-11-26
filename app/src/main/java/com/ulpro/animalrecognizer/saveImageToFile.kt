package com.ulpro.animalrecognizer

import android.content.Context
import java.io.File
import java.io.FileOutputStream

fun saveImageToFile(context: Context, base64Image: String): String {
    val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
    val file = File(context.cacheDir, "temp_image.png")
    FileOutputStream(file).use { it.write(imageBytes) }
    return file.absolutePath
}