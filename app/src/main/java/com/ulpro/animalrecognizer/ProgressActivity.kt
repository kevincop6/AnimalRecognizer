package com.ulpro.animalrecognizer

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View
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

        val gifImageView: ImageView = findViewById(R.id.logoImageView)
        val centerContainer: LinearLayout = findViewById(R.id.centerContainer)
        val haloView: View = findViewById(R.id.haloView)

        // 1) Cargar GIF (logo_animado)
        Glide.with(this)
            .asGif()
            .load(R.drawable.logo_animado)
            .into(gifImageView)

        // 2) Animación de entrada (fade + scale) del contenedor
        val fadeScaleIn = AnimationUtils.loadAnimation(this, R.anim.fade_scale_in)
        centerContainer.startAnimation(fadeScaleIn)
        centerContainer.alpha = 1f

        // 3) Pulso suave continuo (en el logo) - acompaña tus 6s sin “terminar”
        val pulseSoft = AnimationUtils.loadAnimation(this, R.anim.pulse_soft)
        gifImageView.startAnimation(pulseSoft)

        // 4) Halo “respirando” detrás del logo (muy sutil)
        val haloBreathe = AnimationUtils.loadAnimation(this, R.anim.halo_breathe)
        haloView.startAnimation(haloBreathe)
        haloView.alpha = 0.12f

        // 5) Mantener animación mínimo 6s antes de decidir a dónde ir (TU LÓGICA)
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
                    result.paquetePredeterminado?.let { UserPrefs.savePaquete(this, it) }
                    scheduleFetchAnimalsWorker()
                    redirectToAvistamientosActivity()
                }
                is VerifyResult.Inactive -> {
                    TokenStore.clearToken(this)
                    redirectToLoginActivity()
                }
                is VerifyResult.ServerError -> {
                    redirectToLoginActivity()
                }
                is VerifyResult.NetworkError -> {
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
