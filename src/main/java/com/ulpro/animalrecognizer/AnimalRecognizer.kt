package com.ulpro.animalrecognizer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.airbnb.lottie.utils.Utils
import org.opencv.core.*
import org.opencv.features2d.*
import org.opencv.imgproc.Imgproc
import org.opencv.android.Utils
import java.util.Base64

private fun recognizeImage(selectedImage: Bitmap) {
    // Convierte la imagen a Mat (formato OpenCV)
    val selectedMat = convertBitmapToMat(selectedImage)
    val selectedDescriptors = extractORBFeatures(selectedMat)

    if (selectedDescriptors.empty()) {
        Toast.makeText(this, "No se encontraron características en la imagen", Toast.LENGTH_SHORT).show()
        return
    }

    var bestMatch: String? = null
    var bestMatchScore = 0

    for (animal in cachedAnimals) {
        val imageBase64 = animal.imageBase64.replace("\\s".toRegex(), "")

        try {
            val imageBytes = Base64.getDecoder().decode(imageBase64)
            val cachedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val cachedMat = convertBitmapToMat(cachedBitmap)
            val cachedDescriptors = extractORBFeatures(cachedMat)

            val matches = compareFeatures(selectedDescriptors, cachedDescriptors)
            if (matches > bestMatchScore) {
                bestMatchScore = matches
                bestMatch = animal.name
            }
        } catch (e: Exception) {
            Log.e("ActivityCompareImages", "Error decoding image: ${e.message}", e)
        }
    }

    if (bestMatch != null && bestMatchScore > 20) { // Ajusta el umbral de coincidencia
        Toast.makeText(this, "Animal reconocido: $bestMatch", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "No se encontró coincidencia", Toast.LENGTH_SHORT).show()
    }
}

private fun convertBitmapToMat(bitmap: Bitmap): Mat {
    val mat = Mat()
    val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    Utils.bitmapToMat(bmp32, mat)
    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
    return mat
}

private fun extractORBFeatures(image: Mat): Mat {
    val orb = ORB.create()
    val keypoints = MatOfKeyPoint()
    val descriptors = Mat()
    orb.detectAndCompute(image, Mat(), keypoints, descriptors)
    return descriptors
}

private fun compareFeatures(descriptors1: Mat, descriptors2: Mat): Int {
    val bfMatcher = BFMatcher(NORM_HAMMING, true)
    val matches = MatOfDMatch()
    bfMatcher.match(descriptors1, descriptors2, matches)
    return matches.rows()
}
