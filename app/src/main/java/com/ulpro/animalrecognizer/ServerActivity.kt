package com.ulpro.animalrecognizer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * ServerActivity
 * Pantalla de configuración que permite al usuario cambiar la URL
 * base del servidor (útil al cambiar de red local o de entorno).
 *
 * Flujo:
 * 1. Carga la URL actual desde ServerConfig al abrir la pantalla.
 * 2. El usuario edita la URL en el campo de texto.
 * 3. Al guardar: valida → persiste con commit() → reinicia la app.
 */
class ServerActivity : AppCompatActivity() {

    // =========================================================
    // Referencias a las vistas del layout activity_server.xml
    // =========================================================
    private lateinit var tilServerUrl: TextInputLayout   // Contenedor con label y mensaje de error
    private lateinit var etServerUrl: TextInputEditText  // Campo de texto editable con la URL
    private lateinit var btnSave: MaterialButton         // Botón para guardar y reiniciar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)

        // ---------------------------------------------------------
        // Vincular vistas con sus ID del layout XML
        // ---------------------------------------------------------
        tilServerUrl = findViewById(R.id.tilServerUrl)
        etServerUrl  = findViewById(R.id.etServerUrl)
        btnSave      = findViewById(R.id.btnSave)

        // ---------------------------------------------------------
        // Cargar la URL actualmente guardada en el campo de texto.
        // Try/catch como seguridad por si MyApplication no está
        // registrado en el Manifest y initialize() nunca se llamó.
        // ---------------------------------------------------------
        val currentUrl = try {
            ServerConfig.BASE_URL
        } catch (e: UninitializedPropertyAccessException) {
            // Fallback directo a SharedPreferences — solo ocurre si
            // MyApplication no está registrado en AndroidManifest.xml
            getSharedPreferences("serverConfig", Context.MODE_PRIVATE)
                .getString("serverUrl", "http://192.168.100.41/AnimalRecognizer-API/")
                ?: "http://192.168.100.41/AnimalRecognizer-API/"
        }

        // Mostrar URL actual para que el usuario la vea y edite
        etServerUrl.setText(currentUrl)

        // Asignar listener al botón Guardar
        btnSave.setOnClickListener {
            guardarYReiniciar()
        }
    }

    // =============================================================
    // guardarYReiniciar()
    // Valida la URL, la persiste con commit() (síncrono) y reinicia
    // la app completa para que todos los módulos usen la nueva URL.
    // =============================================================
    private fun guardarYReiniciar() {

        // Obtener texto ingresado eliminando espacios accidentales
        val nuevaUrl = etServerUrl.text.toString().trim()

        // Validación 1: campo vacío
        if (nuevaUrl.isEmpty()) {
            tilServerUrl.error = "La URL no puede estar vacía"
            return
        }

        // Validación 2: debe iniciar con http:// o https://
        if (!nuevaUrl.startsWith("http://") && !nuevaUrl.startsWith("https://")) {
            tilServerUrl.error = "La URL debe iniciar con http:// o https://"
            return
        }

        // Pasó validaciones — limpiar error previo visible
        tilServerUrl.error = null

        // ---------------------------------------------------------
        // Guardar URL con commit() SÍNCRONO dentro de updateBaseUrl().
        // El dato queda físicamente en disco ANTES de que exit(0)
        // mate el proceso — ese era el bug original con apply().
        // ---------------------------------------------------------
        ServerConfig.updateBaseUrl(this, nuevaUrl)

        Toast.makeText(this, "URL guardada. Reiniciando...", Toast.LENGTH_SHORT).show()

        // ---------------------------------------------------------
        // Reiniciar la app desde el Activity raíz (launcher).
        // FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK limpian
        // todo el back stack para un arranque completamente limpio.
        // ---------------------------------------------------------
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)

        // Matar el proceso DESPUÉS de commit() — escritura garantizada
        Runtime.getRuntime().exit(0)
    }
}
