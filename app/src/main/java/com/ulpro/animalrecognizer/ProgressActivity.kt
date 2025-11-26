package com.ulpro.animalrecognizer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import java.util.concurrent.TimeUnit

class ProgressActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)
// Configurar CrashHandler
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)
        val gifImageView: ImageView = findViewById(R.id.logoImageView)

        // Cargar el GIF desde una URL
        Glide.with(this)
            .asGif() // Especificar que es un GIF
            .load(R.drawable.animal_animation)
            .into(gifImageView)
        // Simular carga de datos con Handler
        Handler(Looper.getMainLooper()).postDelayed({
            if (isUserLoggedIn()) {
                // Configurar restricciones para que solo se ejecute con conexión a Internet
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

// Crear un PeriodicWorkRequest para que se ejecute una vez por semana
                val fetchAnimalsWorkRequest = PeriodicWorkRequestBuilder<FetchAnimalsWorker>(7, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()

// Encolar el trabajo
                WorkManager.getInstance(this).enqueue(fetchAnimalsWorkRequest)
                redirectToAvistamientosActivity()  // Redirige a Avistamientos si la sesión es válida
            } else {
                redirectToLoginActivity()  // Si no hay sesión, vuelve al login
            }
        }, 3000)  // 3 segundos de espera
        // Verificar si el archivo del modelo existe en assets

    }

    // Función para verificar si el usuario está logueado
    private fun isUserLoggedIn(): Boolean {
        val usuarioId = sharedPreferences.getString("usuario_id", null) // Verificar clave correcta
        val email = sharedPreferences.getString("userEmail", null)
        return !usuarioId.isNullOrEmpty() && !email.isNullOrEmpty()  // Evita cadenas vacías
    }

    // Redirigir al usuario a AvistamientosActivity
    private fun redirectToAvistamientosActivity() {
        val intent = Intent(this, MainNavigationActivity::class.java)
        startActivity(intent)
        finish()  // Cierra ProgressActivity para evitar que el usuario regrese
    }

    // Redirigir al usuario a MainActivity (login)
    private fun redirectToLoginActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()  // Cierra ProgressActivity para evitar navegación hacia atrás
    }
}


