package com.ulpro.animalrecognizer

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class FetchAnimalsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val prefs = applicationContext.getSharedPreferences(
        "animals_cache_meta",
        Context.MODE_PRIVATE
    )

    private val channelId = "animals_download_channel"
    private val notificationId = 3001

    override suspend fun doWork(): Result {
        ServerConfig.initialize(applicationContext)

        val token = TokenStore.getToken(applicationContext)
            ?: return Result.failure()

        if (!hasInternet()) {
            // Sin internet: si ya hay archivo guardado, se permite continuar
            return if (getActiveAnimalsFile().exists()) Result.success()
            else Result.retry()
        }

        val paquete = UserPrefs.getPaquete(applicationContext)
            ?.trim()
            ?.lowercase()
            ?: "nacional"

        val provincia = when (paquete) {
            "geolocalizacion" -> resolveProvinceByGpsOrNull() ?: "nacional"
            "nacional" -> "nacional"
            else -> paquete
        }

        val provinciaFinal = normalizeAllowedProvince(provincia) ?: "nacional"

        val conteos = fetchConteos() ?: return Result.retry()

        val conteoKey = mapProvinceToConteoKey(provinciaFinal)
        val conteoActual = conteos.optInt(conteoKey, -1)
        if (conteoActual < 0) return Result.retry()

        val lastCountKey = "last_count_$provinciaFinal"
        val lastCount = prefs.getInt(lastCountKey, -1)

        val outFile = File(
            applicationContext.filesDir,
            "animals_${provinciaFinal.replace(" ", "_")}.json"
        )

        if (lastCount == conteoActual && outFile.exists()) {
            saveActiveFile(outFile.name)
            return Result.success()
        }

        createNotificationChannelIfNeeded()
        setForegroundAsync(
            createForegroundInfo(
                indeterminate = true,
                progress = 0,
                title = "Descargando animales",
                text = "Provincia: $provinciaFinal"
            )
        )

        val ok = downloadAnimals(
            token = token,
            provincia = provinciaFinal,
            outFile = outFile
        )

        if (!ok) return Result.retry()

        prefs.edit()
            .putInt(lastCountKey, conteoActual)
            .apply()

        saveActiveFile(outFile.name)

        setForegroundAsync(
            createForegroundInfo(
                indeterminate = false,
                progress = 100,
                title = "Descarga completa",
                text = "Animales actualizados"
            )
        )

        return Result.success()
    }

    // -------------------------
    // Helpers
    // -------------------------

    private fun hasInternet(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchConteos(): JSONObject? {
        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/animales/count_animal.php"

        return try {
            client.newCall(Request.Builder().url(url).get().build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val json = JSONObject(resp.body?.string().orEmpty())
                    json.optJSONObject("conteos")
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadAnimals(
        token: String,
        provincia: String,
        outFile: File
    ): Boolean {
        val url = ServerConfig.BASE_URL.trimEnd('/') +
                "/api/animales/descargar_provincia.php"

        val body = FormBody.Builder()
            .add("token", token)
            .add("provincia", provincia)
            .build()

        return try {
            client.newCall(
                Request.Builder().url(url).post(body).build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return false

                val responseBody = resp.body ?: return false
                val total = responseBody.contentLength()
                val determinate = total > 0

                responseBody.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        var downloaded = 0L

                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read

                            if (determinate) {
                                val pct = ((downloaded * 100) / total)
                                    .toInt().coerceIn(0, 100)

                                setForegroundAsync(
                                    createForegroundInfo(
                                        indeterminate = false,
                                        progress = pct,
                                        title = "Descargando animales",
                                        text = "$provincia ($pct%)"
                                    )
                                )
                            }
                        }
                    }
                }

                outFile.readText().trim().startsWith("[")
            }
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------
    // GPS (NO pedir permisos)
    // -------------------------

    @SuppressLint("MissingPermission")
    private suspend fun resolveProvinceByGpsOrNull(): String? {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE)
                as LocationManager

        val location = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null

        val geocoder = Geocoder(applicationContext, Locale("es", "CR"))
        val admin = geocoder
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.adminArea ?: return null

        return normalizeAllowedProvince(admin)
    }

    private fun normalizeAllowedProvince(value: String): String? {
        val v = value.lowercase()
            .replace("á", "a").replace("é", "e")
            .replace("í", "i").replace("ó", "o")
            .replace("ú", "u")

        return when {
            v.contains("san jose") -> "san jose"
            v.contains("alajuela") -> "alajuela"
            v.contains("cartago") -> "cartago"
            v.contains("heredia") -> "heredia"
            v.contains("guanacaste") -> "guanacaste"
            v.contains("puntarenas") -> "puntarenas"
            v.contains("limon") -> "limon"
            v.contains("nacional") -> "nacional"
            else -> null
        }
    }

    private fun mapProvinceToConteoKey(provincia: String): String =
        when (provincia) {
            "san jose" -> "San Jose"
            "alajuela" -> "Alajuela"
            "cartago" -> "Cartago"
            "heredia" -> "Heredia"
            "guanacaste" -> "Guanacaste"
            "puntarenas" -> "Puntarenas"
            "limon" -> "Limón"
            else -> "nacional"
        }

    // -------------------------
    // Archivo activo
    // -------------------------

    private fun saveActiveFile(filename: String) {
        prefs.edit().putString("active_animals_file", filename).apply()
    }

    private fun getActiveAnimalsFile(): File {
        val name = prefs.getString("active_animals_file", null)
        return if (name != null)
            File(applicationContext.filesDir, name)
        else
            File("")
    }

    // -------------------------
    // Notificación
    // -------------------------

    private fun createForegroundInfo(
        indeterminate: Boolean,
        progress: Int,
        title: String,
        text: String
    ): ForegroundInfo {

        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (indeterminate) {
            notif.setProgress(0, 0, true)
        } else {
            notif.setProgress(100, progress, false)
        }

        return ForegroundInfo(
            notificationId,
            notif.build(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }


    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Descarga de animales",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}