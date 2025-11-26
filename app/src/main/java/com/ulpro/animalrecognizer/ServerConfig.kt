package com.ulpro.animalrecognizer

import android.content.Context
import androidx.core.content.edit

object ServerConfig {

    lateinit var BASE_URL: String
        private set

    fun initialize(context: Context) {
        val sharedPreferences = context.getSharedPreferences("serverConfig", Context.MODE_PRIVATE)
        BASE_URL = sharedPreferences.getString("serverUrl", "http://192.168.100.42/avistamiento/") ?: "http://192.168.100.42/avistamiento/"
    }

    fun updateBaseUrl(context: Context, newUrl: String) {
        val sharedPreferences = context.getSharedPreferences("serverConfig", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putString("serverUrl", newUrl)
            apply()
        }
        BASE_URL = newUrl
    }
}