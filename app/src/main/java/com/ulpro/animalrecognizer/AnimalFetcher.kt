package com.ulpro.animalrecognizer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class FetchAnimalsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val sharedPreferences = applicationContext.getSharedPreferences("userSession", Context.MODE_PRIVATE)
        val url = "${ServerConfig.BASE_URL}get_animals.php"

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.retry()
            }

            val responseBody = response.body?.string() ?: return Result.failure()
            val animalsArray = JSONArray(sharedPreferences.getString("cached_animals", "[]"))

            // Procesar y guardar los datos
            val newAnimals = JSONArray(responseBody)
            for (i in 0 until newAnimals.length()) {
                animalsArray.put(newAnimals.getJSONObject(i))
            }
            saveToCache(sharedPreferences, animalsArray)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun saveToCache(sharedPreferences: android.content.SharedPreferences, animalsArray: JSONArray) {
        sharedPreferences.edit {
            putString("cached_animals", animalsArray.toString())
        }
    }
}