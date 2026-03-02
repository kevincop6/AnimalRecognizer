package com.ulpro.animalrecognizer

import android.content.Context

/**
 * Objeto singleton que centraliza la configuración del servidor.
 * Gestiona BASE_URL tanto en memoria como en SharedPreferences
 * para persistencia entre reinicios de la app.
 */
object ServerConfig {

    // Constantes para evitar strings hardcodes en múltiples lugares
    private const val PREFS_NAME  = "serverConfig"
    private const val KEY_URL     = "serverUrl"
    private const val DEFAULT_URL = "http://192.168.100.41/AnimalRecognizer-API/"

    /**
     * BASE_URL
     * Variable en memoria con la URL activa del servidor.
     * - late init: se asigna en initialize() antes del primer uso.
     * - private set: solo este objeto puede modificarla.
     */
    lateinit var BASE_URL: String
        private set

    /**
     * Initialize()
     * Lee la URL persistida en SharedPreferences y la carga en BASE_URL.
     * Debe llamarse en MyApplication.onCreate() para que BASE_URL esté
     * disponible antes que cualquier Activity o Worker.
     *
     * @param context Contexto de la aplicación
     */
    fun initialize(context: Context) {
        BASE_URL = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL)
            ?: DEFAULT_URL
    }

    /**
     * updateBaseUrl()
     * Persiste la nueva URL en SharedPreferences de forma SÍNCRONA.
     *
     * FIX CRÍTICO: Se usa commit() en lugar de apply() porque:
     * - apply() → asíncrono → el proceso muere con exit(0) antes
     *              de que el dato llegue al disco → URL no se guarda.
     * - commit() → bloqueante → garantiza escritura en disco antes
     *              de que exit(0) se ejecute → URL siempre se guarda.
     *
     * @param context Contexto necesario para SharedPreferences
     * @param newUrl  Nueva URL ingresada por el usuario
     */
    fun updateBaseUrl(context: Context, newUrl: String) {
        // commit() bloquea hasta confirmar escritura física en disco
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, newUrl)
            .commit() // <-- CRÍTICO: síncrono, NO apply()

        // Actualizar en memoria para uso inmediato sin releer disco
        BASE_URL = newUrl
    }
}
