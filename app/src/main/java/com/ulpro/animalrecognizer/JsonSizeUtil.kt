package com.ulpro.animalrecognizer

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object JsonSizeUtil {

    fun getJsonSize(url: String): String {
        return try {
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connect()

            val inputStream = urlConnection.inputStream
            val jsonResponse = inputStream.bufferedReader().use { it.readText() }

            val sizeInBytes = jsonResponse.toByteArray().size.toLong()
            formatSize(sizeInBytes)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun formatSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", sizeInBytes.toDouble() / (1 shl 30)) // GB
            sizeInBytes >= 1L shl 20 -> String.format(Locale.US, "%.2f MB", sizeInBytes.toDouble() / (1 shl 20)) // MB
            sizeInBytes >= 1L shl 10 -> String.format(Locale.US, "%.2f KB", sizeInBytes.toDouble() / (1 shl 10)) // KB
            else -> String.format(Locale.US, "%d Bytes", sizeInBytes) // Bytes
        }
    }
}
