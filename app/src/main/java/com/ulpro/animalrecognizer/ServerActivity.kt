package com.ulpro.animalrecognizer
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ServerActivity : AppCompatActivity() {

    // =========================================================
    // Referencias a las vistas del layout activity_server.xml
    // =========================================================
    private lateinit var tilServerUrl: TextInputLayout
    private lateinit var etServerUrl: TextInputEditText
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        // ---------------------------------------------------------
        // Vincular vistas con sus IDs del layout
        // ---------------------------------------------------------
        tilServerUrl = findViewById(R.id.tilServerUrl)
        etServerUrl  = findViewById(R.id.etServerUrl)
        btnSave      = findViewById(R.id.btnSave)

        // ---------------------------------------------------------
        // Al abrir la pantalla, cargar la URL guardada actualmente
        // en SharedPreferences a través de ServerConfig.
        // Si BASE_URL no fue inicializada aún, se lee directo
        // desde SharedPreferences para evitar UninitializedPropertyAccessException
        // ---------------------------------------------------------
        val currentUrl = try {
            ServerConfig.BASE_URL
        } catch (e: UninitializedPropertyAccessException) {
            // Fallback: leer directo de SharedPreferences si
            // ServerConfig aún no fue inicializado (edge case)
            getSharedPreferences("serverConfig", Context.MODE_PRIVATE)
                .getString("serverUrl", "http://192.168.100.41/AnimalRecognizer-API/")
                ?: "http://192.168.100.41/AnimalRecognizer-API/"
        }

        // Rellenar el campo con la URL actual
        etServerUrl.setText(currentUrl)

        // ---------------------------------------------------------
        // Listener del botón Guardar
        // ---------------------------------------------------------
        btnSave.setOnClickListener {
            guardarYReiniciar()
        }
    }

    // =============================================================
    // guardarYReiniciar()
    // Valida el campo, guarda la nueva URL mediante ServerConfig
    // y reinicia la aplicación desde cero para que todos los
    // módulos (Retrofit, etc.) usen la nueva BASE_URL
    // =============================================================
    private fun guardarYReiniciar() {

        // Obtener texto ingresado y limpiar espacios accidentales
        val nuevaUrl = etServerUrl.text.toString().trim()

        // ---------------------------------------------------------
        // Validación 1: campo vacío
        // ---------------------------------------------------------
        if (nuevaUrl.isEmpty()) {
            tilServerUrl.error = "La URL no puede estar vacía"
            return
        }

        // ---------------------------------------------------------
        // Validación 2: debe iniciar con http:// o https://
        // ---------------------------------------------------------
        if (!nuevaUrl.startsWith("http://") && !nuevaUrl.startsWith("https://")) {
            tilServerUrl.error = "La URL debe iniciar con http:// o https://"
            return
        }

        // Sí pasa validaciones, limpiar error previo
        tilServerUrl.error = null

        // ---------------------------------------------------------
        // Guardar nueva URL usando ServerConfig
        // Esto actualiza SharedPreferences Y la variable BASE_URL
        // en memoria al mismo tiempo
        // ---------------------------------------------------------
        ServerConfig.updateBaseUrl(this, nuevaUrl)

        Toast.makeText(this, "URL guardada. Reiniciando...", Toast.LENGTH_SHORT).show()

        // ---------------------------------------------------------
        // Reiniciar la aplicación completa
        // Se obtiene el Intent de entrada (launcher) de la app,
        // se limpian todos los Activities del back stack con
        // FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK
        // y se lanza desde cero
        // ---------------------------------------------------------
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)

        // Finalizar el proceso actual para forzar reinicio limpio
        Runtime.getRuntime().exit(0)
    }
}
