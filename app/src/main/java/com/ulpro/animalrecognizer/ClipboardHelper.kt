package com.ulpro.animalrecognizer


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

class ClipboardHelper(private val context: Context) {

    // Método para copiar texto al portapapeles
    fun copyToClipboard(label: String, text: String) {
        // Obtener el ClipboardManager
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Crear un ClipData con el texto
        val clipData = ClipData.newPlainText(label, text)

        // Copiar al portapapeles
        clipboardManager.setPrimaryClip(clipData)

        // Mostrar un mensaje de confirmación
        Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }
}
