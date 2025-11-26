package com.ulpro.animalrecognizer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.TimeUnit

class UpdateAnimalsWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val channelId = "update_animals_channel"
    private val notificationId = 1
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        createNotificationChannel()
        showNotification("Actualización de Animales", "Iniciando tarea de fondo", 0)

        val url = "${ServerConfig.BASE_URL}get_animals.php"
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (responseBody.isNullOrEmpty()) {
                Log.e("UpdateAnimalsWorker", "Respuesta vacía o nula del servidor")
                showNotification("Error", "Respuesta vacía del servidor", 0)
                return Result.failure()
            }

            val animalsArray = JSONArray(responseBody)
            if (animalsArray.length() == 0) {
                Log.e("UpdateAnimalsWorker", "El JSON recibido está vacío")
                showNotification("Error", "JSON vacío recibido", 0)
                return Result.failure()
            }

            val animals = mutableListOf<Animal>()
            for (i in 0 until animalsArray.length()) {
                val animalJson = animalsArray.getJSONObject(i)

                val id = animalJson.optInt("id", -1)
                val name = animalJson.optString("name", "Desconocido")
                val imageBase64 = animalJson.optString("imageBase64", "")

                if (id == -1 || name.isEmpty() || imageBase64.isEmpty()) {
                    Log.e("UpdateAnimalsWorker", "Datos incompletos en el JSON para el animal en índice $i")
                    continue
                }

                animals.add(Animal(id, name, imageBase64))
            }

            // Aquí puedes guardar los datos en caché o en una base de datos local
            saveAnimalsToCache(animals)

            showNotification("Actualización completada", "Se actualizaron ${animals.size} animales", 100)
            Result.success()
        } catch (e: JSONException) {
            Log.e("UpdateAnimalsWorker", "Error al procesar el JSON: ${e.message}")
            showNotification("Error", "JSON inválido: ${e.message}", 0)
            Result.failure()
        } catch (e: Exception) {
            Log.e("UpdateAnimalsWorker", "Error inesperado: ${e.message}")
            showNotification("Error", "Error inesperado: ${e.message}", 0)
            Result.failure()
        }
    }

    private fun saveAnimalsToCache(animals: List<Animal>) {
        val sharedPreferences = applicationContext.getSharedPreferences("userSession", Context.MODE_PRIVATE)
        val animalsJson = JSONArray().apply {
            animals.forEach { animal ->
                put(animal.toJson()) // Llamada correcta al método toJson
            }
        }
        sharedPreferences.edit().apply {
            putString("cached_animals", animalsJson.toString())
            putLong("last_update", System.currentTimeMillis())
            apply()
        }
    }

    private fun createNotificationChannel() {
        val name = "Actualizar animales"
        val descriptionText = "Canal para notificaciones de actualización de animales"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, text: String, progress: Int) {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(100, progress, false)

            try {
                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(notificationId, builder.build())
                }
            } catch (e: SecurityException) {
                Log.e("UpdateAnimalsWorker", "No se pudo mostrar la notificación: ${e.message}")
            }
        } else {
            Log.e("UpdateAnimalsWorker", "Permiso POST_NOTIFICATIONS no concedido")
        }
    }
}