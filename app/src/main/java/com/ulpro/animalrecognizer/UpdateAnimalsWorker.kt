package com.ulpro.animalrecognizer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class UpdateAnimalsWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val channelId = "update_animals_channel"
    private val notificationId = 4001

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val prefs = applicationContext.getSharedPreferences("animals_cache_meta", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        ServerConfig.initialize(applicationContext)

        createNotificationChannelIfNeeded()
        showNotification("Animales", "Iniciando actualización…", indeterminate = true, progress = 0)

        val token = TokenStore.getToken(applicationContext)
        if (token.isNullOrBlank()) {
            showNotification("Animales", "No hay sesión activa", indeterminate = false, progress = 0)
            return Result.failure()
        }

        if (!hasInternet()) {
            // Sin internet: si ya hay archivo activo, éxito; si no, retry
            return if (getActiveAnimalsFile().exists()) Result.success() else Result.retry()
        }

        val paquete = (UserPrefs.getPaquete(applicationContext) ?: "nacional").trim().lowercase()
        val provincia = normalizeAllowedProvince(
            when (paquete) {
                "nacional" -> "nacional"
                "geolocalizacion" -> "nacional" // Worker no debería depender de GPS aquí
                else -> paquete
            }
        ) ?: "nacional"

        // 1) Obtener conteos
        val conteos = fetchConteos() ?: run {
            showNotification("Animales", "No se pudo obtener conteos", indeterminate = false, progress = 0)
            return Result.retry()
        }

        val conteoKey = mapProvinceToConteoKey(provincia)
        val conteoActual = conteos.optInt(conteoKey, -1)
        if (conteoActual < 0) {
            showNotification("Animales", "Conteo inválido para $provincia", indeterminate = false, progress = 0)
            return Result.retry()
        }

        val lastCountKey = "last_count_$provincia"
        val lastCount = prefs.getInt(lastCountKey, -1)

        val outFile = File(applicationContext.filesDir, "animals_${provincia.replace(" ", "_")}.json")

        // 2) Si no cambió y existe archivo, no descargamos
        if (lastCount == conteoActual && outFile.exists()) {
            saveActiveFile(outFile.name)
            showNotification("Animales", "Sin cambios ($provincia)", indeterminate = false, progress = 100)
            return Result.success()
        }

        // 3) Descargar por POST provincia + token
        val ok = downloadProvinciaJson(token = token, provincia = provincia, outFile = outFile)
        if (!ok) {
            showNotification("Animales", "Error descargando ($provincia)", indeterminate = false, progress = 0)
            return Result.retry()
        }

        // 4) Guardar metadata
        prefs.edit {
            putInt(lastCountKey, conteoActual)
        }
        saveActiveFile(outFile.name)

        showNotification("Animales", "Actualización completa ($provincia)", indeterminate = false, progress = 100)
        return Result.success()
    }

    // -------------------
    // Network
    // -------------------
    private fun hasInternet(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // -------------------
    // Conteos
    // -------------------
    private fun fetchConteos(): JSONObject? {
        val url = ServerConfig.BASE_URL.trimEnd('/') + "/api/animales/count_animal.php"
        val req = Request.Builder().url(url).get().build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optJSONObject("conteos")
            }
        } catch (e: Exception) {
            Log.e("UpdateAnimalsWorker", "fetchConteos error: ${e.message}")
            null
        }
    }

    private fun mapProvinceToConteoKey(provincia: String): String =
        when (provincia.lowercase()) {
            "san jose" -> "San Jose"
            "alajuela" -> "Alajuela"
            "cartago" -> "Cartago"
            "heredia" -> "Heredia"
            "guanacaste" -> "Guanacaste"
            "puntarenas" -> "Puntarenas"
            "limon" -> "Limón"
            "nacional" -> "nacional"
            else -> "nacional"
        }

    private fun normalizeAllowedProvince(value: String): String? {
        val v = value.trim().lowercase()
            .replace("á","a").replace("é","e")
            .replace("í","i").replace("ó","o")
            .replace("ú","u")

        return when (v) {
            "san jose" -> "san jose"
            "alajuela" -> "alajuela"
            "cartago" -> "cartago"
            "heredia" -> "heredia"
            "guanacaste" -> "guanacaste"
            "puntarenas" -> "puntarenas"
            "limon" -> "limon"
            "nacional" -> "nacional"
            else -> null
        }
    }

    // -------------------
    // Descargar JSON
    // -------------------
    private fun downloadProvinciaJson(token: String, provincia: String, outFile: File): Boolean {
        val url = ServerConfig.BASE_URL.trimEnd('/') + "/api/animales/descargar_provincia.php"

        val formBody = FormBody.Builder()
            .add("token", token)
            .add("provincia", provincia)
            .build()

        val req = Request.Builder().url(url).post(formBody).build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false

                val total = body.contentLength()
                val determinate = total > 0L

                showNotification("Animales", "Descargando ($provincia)…", indeterminate = !determinate, progress = 0)

                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (determinate) {
                                val pct = ((downloaded.toDouble() / total.toDouble()) * 100.0)
                                    .roundToInt().coerceIn(0, 100)
                                showNotification("Animales", "Descargando ($provincia) $pct%", indeterminate = false, progress = pct)
                            }
                        }
                    }
                }

                // Validación mínima: debe ser JSONArray
                val text = outFile.readText().trim()
                text.startsWith("[")
            }
        } catch (e: Exception) {
            Log.e("UpdateAnimalsWorker", "downloadProvinciaJson error: ${e.message}")
            false
        }
    }

    // -------------------
    // Archivo activo
    // -------------------
    private fun saveActiveFile(filename: String) {
        prefs.edit { putString("active_animals_file", filename) }
    }

    private fun getActiveAnimalsFile(): File {
        val name = prefs.getString("active_animals_file", null)
        return if (name.isNullOrBlank()) File("") else File(applicationContext.filesDir, name)
    }

    // -------------------
    // Notificaciones
    // -------------------
    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Actualización de animales",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun showNotification(title: String, text: String, indeterminate: Boolean, progress: Int) {
        // En Android 13+ se requiere POST_NOTIFICATIONS; si no está, solo log
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("UpdateAnimalsWorker", "POST_NOTIFICATIONS no concedido; no se mostrará notificación")
                return
            }
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress in 0..99)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (indeterminate) builder.setProgress(0, 0, true)
        else builder.setProgress(100, progress.coerceIn(0, 100), false)

        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }
}
