package com.ulpro.animalrecognizer

import android.app.Application
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

/**
 * Clase Application personalizada.
 * Android la instancia ANTES que cualquier Activity o Worker.
 *
 * Responsabilidades:
 * 1. Inicializar ServerConfig para que BASE_URL esté disponible
 *    desde el primer instante, incluyendo tras reinicio con exit(0).
 * 2. Programar el worker de actualización de animales en background.
 */
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ---------------------------------------------------------
        // PASO 1: Inicializar ServerConfig PRIMERO.
        // Lee la URL guardada en SharedPreferences y la asigna a
        // BASE_URL en memoria antes de que cualquier módulo la use.
        // Si no se llama aquí, BASE_URL lanzaría
        // UninitializedPropertyAccessException al primer acceso.
        // ---------------------------------------------------------
        ServerConfig.initialize(this)

        // ---------------------------------------------------------
        // PASO 2: Programar worker de actualización en background.
        // Va DESPUÉS de initialize() para que si el worker usa
        // BASE_URL internamente, ya la encuentre disponible.
        // ---------------------------------------------------------
        val workRequest = OneTimeWorkRequest.Builder(UpdateAnimalsWorker::class.java).build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }
}
