package com.ulpro.animalrecognizer

import android.content.Context
import androidx.core.content.edit

object UserPrefs {
    private const val PREFS = "user_data"
    private const val KEY_PAQUETE = "paquete_predeterminado"

    fun savePaquete(context: Context, paquete: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_PAQUETE, paquete)
        }
    }

    fun getPaquete(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PAQUETE, null)
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }
}
