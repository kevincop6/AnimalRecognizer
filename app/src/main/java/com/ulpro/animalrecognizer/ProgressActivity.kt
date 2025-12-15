package com.ulpro.animalrecognizer

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import java.util.concurrent.TimeUnit

class ProgressActivity : AppCompatActivity() {

    private val minAnimationMs = 6000L
    private val workName = "fetch_animals_weekly"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Cargar GIF
        val gifImageView: ImageView = findViewById(R.id.logoImageView)
        Glide.with(this)
            .asGif()
            .load(R.drawable.logo_animado)
            .into(gifImageView)

        // Mantener animación mínimo 6s antes de decidir a dónde ir
        Handler(Looper.getMainLooper()).postDelayed({
            decideNextScreen()
        }, minAnimationMs)
    }

    private fun decideNextScreen() {
        ServerConfig.initialize(this)

        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            redirectToLoginActivity()
            return
        }

        // Sin internet => permitir modo sin conexión
        if (!hasInternet()) {
            scheduleFetchAnimalsWorker()
            redirectToAvistamientosActivity()
            return
        }

        // Con internet => verificar sesión por POST (solo permite si activo=true)
        ServerConnection(this).verifySession(token) { result ->
            when (result) {
                is VerifyResult.Active -> {
                    // Guardar paquete_predeterminado si viene (si ya lo haces en otro lado, esto es opcional)
                    result.paquetePredeterminado?.let { UserPrefs.savePaquete(this, it) }

                    scheduleFetchAnimalsWorker()
                    redirectToAvistamientosActivity()
                }

                is VerifyResult.Inactive -> {
                    TokenStore.clearToken(this)
                    redirectToLoginActivity()
                }

                is VerifyResult.ServerError -> {
                    // Hubo respuesta con error => NO permitir
                    redirectToLoginActivity()
                }

                is VerifyResult.NetworkError -> {
                    // No se pudo conectar aunque hay red "teórica" => trátalo como sin conexión
                    scheduleFetchAnimalsWorker()
                    redirectToAvistamientosActivity()
                }
            }
        }
    }

    private fun scheduleFetchAnimalsWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<FetchAnimalsWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun redirectToAvistamientosActivity() {
        startActivity(Intent(this, MainNavigationActivity::class.java))
        finish()
    }

    private fun redirectToLoginActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
