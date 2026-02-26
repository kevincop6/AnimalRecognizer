package com.ulpro.animalrecognizer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import cn.pedant.SweetAlert.SweetAlertDialog

class MainActivity : AppCompatActivity() {

    private var lastClickTime: Long = 0

    // ✅ Dialogo bloqueante para verificación de sesión
    private var sessionCheckDialog: SweetAlertDialog? = null
    // Contador de intentos fallidos y tiempo de bloqueo
    // Se guardan en SharedPreferences para persistir aunque la app se cierre
    private val PREFS_PIN = "pin_config"
    private val KEY_INTENTOS = "intentos_fallidos"
    private val KEY_BLOQUEO_HASTA = "bloqueado_hasta"
    private val MAX_INTENTOS = 3
    private val DIAS_BLOQUEO = 3L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        // Config por primera vez (tu lógica original)
        val firstRunPreferences = getSharedPreferences("firstRunConfig", MODE_PRIVATE)
        val isFirstRun = firstRunPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            val serverPreferences = getSharedPreferences("serverConfig", MODE_PRIVATE)
            serverPreferences.edit()
                .putString("serverUrl", "http://192.168.100.41/AnimalRecognizer-API/")
                .apply()

            Toast.makeText(this, "Ajustes del servidor aplicados", Toast.LENGTH_SHORT).show()

            firstRunPreferences.edit()
                .putBoolean("isFirstRun", false)
                .apply()
        }

        ServerConfig.initialize(this)
        requestEnableNotifications()

        val animalLogo: ImageView = findViewById(R.id.AnimalLogo)
        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.loginButton)

        // ✅ Bloqueo/Desbloqueo UI mientras valida sesión automática
        fun setUiEnabled(enabled: Boolean) {
            emailEditText.isEnabled = enabled
            passwordEditText.isEnabled = enabled
            loginButton.isEnabled = enabled
            animalLogo.isEnabled = enabled
        }

        // ✅ Mostrar loader bloqueante SOLO si hay token (o sea, hay algo que verificar)
        val existingToken = TokenStore.getToken(this)
        if (!existingToken.isNullOrBlank()) {
            setUiEnabled(false)
            sessionCheckDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                titleText = "Verificando sesión..."
                setCancelable(false) // ✅ no se puede cerrar ni tocar fuera
                show()
            }
        }

        // ✅ Sesión basada SOLO en token cifrado
        checkLoginStatus { loggedIn ->
            runOnUiThread {
                // cerrar loader y habilitar UI siempre que termine
                sessionCheckDialog?.dismissWithAnimation()
                sessionCheckDialog = null
                setUiEnabled(true)

                if (loggedIn) {
                    redirectToProgressActivity()
                }
            }
        }

        loginButton.setOnClickListener {
            val usuarioOCorreo = emailEditText.text.toString().trim()
            val contrasena = passwordEditText.text.toString()

            if (usuarioOCorreo.isNotEmpty() && contrasena.isNotEmpty()) {
                val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                    titleText = "Verificando..."
                    setCancelable(false)
                    show()
                }

                ServerConnection(this).login(usuarioOCorreo, contrasena) { success, result ->
                    runOnUiThread {
                        loadingDialog.dismissWithAnimation()

                        if (success) {
                            val token = result as String
                            // ✅ guardar SOLO token (cifrado)
                            TokenStore.saveToken(this, token)

                            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE).apply {
                                titleText = "Acceso concedido"
                                confirmText = "Aceptar"
                                setConfirmClickListener {
                                    dismissWithAnimation()
                                    redirectToProgressActivity()
                                }
                                show()
                            }
                        } else {
                            val msg = (result as? String) ?: "Error desconocido"
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Por favor ingresa los datos", Toast.LENGTH_SHORT).show()
            }
        }

        animalLogo.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime < 300) {
                mostrarDialogoPin()
            }
            lastClickTime = currentTime
        }
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkLoginStatus(done: (Boolean) -> Unit) {
        val token = TokenStore.getToken(this)

        // Sin token => no sesión (no hay nada que verificar)
        if (token.isNullOrBlank()) {
            done(false)
            return
        }

        // Sin internet => permitir modo sin conexión
        if (!hasInternet()) {
            done(true)
            return
        }

        // Con internet => verificar por POST
        ServerConnection(this).verifySession(token) { result ->
            runOnUiThread {
                when (result) {
                    is VerifyResult.Active -> {
                        // ✅ guardar paquete para uso posterior
                        result.paquetePredeterminado?.let {
                            UserPrefs.savePaquete(this, it)
                        }
                        done(true)
                    }

                    is VerifyResult.Inactive -> {
                        TokenStore.clearToken(this)
                        UserPrefs.clear(this)
                        done(false)
                    }

                    is VerifyResult.ServerError -> {
                        done(false)
                    }

                    is VerifyResult.NetworkError -> {
                        // único caso offline permitido
                        done(true)
                    }
                }
            }
        }
    }

    private fun redirectToProgressActivity() {
        startActivity(Intent(this, ProgressActivity::class.java))
        finish()
    }

    private fun requestEnableNotifications() {
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

        if (!notificationManagerCompat.areNotificationsEnabled()) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } else {
            Log.d("NotificationCheck", "Las notificaciones están habilitadas.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // evitar window leak por si la activity muere en medio de la verificación
        sessionCheckDialog?.dismissWithAnimation()
        sessionCheckDialog = null
    }

    private fun mostrarDialogoPin() {

        val prefs = getSharedPreferences(PREFS_PIN, Context.MODE_PRIVATE)

        // ---------------------------------------------------------
        // Verificar si está bloqueado antes de mostrar el diálogo
        // ---------------------------------------------------------
        val bloqueadoHasta = prefs.getLong(KEY_BLOQUEO_HASTA, 0L)
        val ahora = System.currentTimeMillis()

        if (bloqueadoHasta > ahora) {
            // Calcular días/horas restantes para mostrar al usuario
            val msRestantes = bloqueadoHasta - ahora
            val diasRestantes = (msRestantes / (1000 * 60 * 60 * 24)).toInt()
            val horasRestantes = ((msRestantes % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toInt()

            // Mostrar diálogo de bloqueo activo — no permite intentar
            SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE).apply {
                titleText = "Acceso bloqueado"
                contentText = "Demasiados intentos fallidos.\nIntenta en $diasRestantes día(s) y $horasRestantes hora(s)."
                confirmText = "Entendido"
                showCancelButton(false)
                setConfirmClickListener { dismissWithAnimation() }
                show()
            }
            return // Salir sin mostrar el campo PIN
        }

        // ---------------------------------------------------------
        // Si no está bloqueado, mostrar diálogo de PIN normalmente
        // ---------------------------------------------------------
        val etPin = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Ingresa el PIN"
            setPadding(40, 20, 40, 20)
        }

        mostrarDialogoPinInterno(etPin, prefs)
    }

    // =============================================================
// mostrarDialogoPinInterno()
// Función recursiva — se vuelve a llamar a sí misma si el PIN
// es incorrecto y no se alcanzó el límite de intentos.
// prefs: SharedPreferences para leer/escribir intentos y bloqueo
// =============================================================
    private fun mostrarDialogoPinInterno(
        etPin: EditText,
        prefs: android.content.SharedPreferences
    ) {
        // Limpiar campo antes de mostrar
        etPin.setText("")

        // Leer intentos actuales acumulados
        val intentosActuales = prefs.getInt(KEY_INTENTOS, 0)
        val intentosRestantes = MAX_INTENTOS - intentosActuales

        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE).apply {
            titleText = "Acceso restringido"
            // Mostrar cuántos intentos le quedan al usuario
            contentText = "Solo para desarrolladores\nIntentos restantes: $intentosRestantes"
            setCustomView(etPin)
            confirmText = "Entrar"
            cancelText = "Cancelar"
            showCancelButton(true)

            setConfirmClickListener {
                val PIN_CORRECTO = "1234"

                if (etPin.text.toString() == PIN_CORRECTO) {

                    // -------------------------------------------------
                    // PIN CORRECTO → resetear intentos y abrir ServerActivity
                    // -------------------------------------------------
                    prefs.edit().apply {
                        putInt(KEY_INTENTOS, 0)        // Resetear contador
                        putLong(KEY_BLOQUEO_HASTA, 0L)  // Limpiar bloqueo
                        apply()
                    }

                    changeAlertType(SweetAlertDialog.SUCCESS_TYPE)
                    titleText = "Acceso concedido"
                    contentText = ""
                    showCancelButton(false)
                    setConfirmClickListener {
                        dismissWithAnimation()
                        startActivity(Intent(this@MainActivity, ServerActivity::class.java))
                    }

                } else {

                    // -------------------------------------------------
                    // PIN INCORRECTO → incrementar contador
                    // -------------------------------------------------
                    val nuevosIntentos = intentosActuales + 1
                    prefs.edit().putInt(KEY_INTENTOS, nuevosIntentos).apply()

                    if (nuevosIntentos >= MAX_INTENTOS) {

                        // ---------------------------------------------
                        // LÍMITE ALCANZADO → aplicar bloqueo de 3 días
                        // ---------------------------------------------
                        val tiempoBloqueo = System.currentTimeMillis() +
                                (DIAS_BLOQUEO * 24 * 60 * 60 * 1000L)

                        prefs.edit().apply {
                            putLong(KEY_BLOQUEO_HASTA, tiempoBloqueo)
                            putInt(KEY_INTENTOS, 0) // Resetear para el próximo ciclo
                            apply()
                        }

                        changeAlertType(SweetAlertDialog.ERROR_TYPE)
                        titleText = "Acceso bloqueado"
                        contentText = "Superaste los $MAX_INTENTOS intentos.\nBloqueado por $DIAS_BLOQUEO días."
                        showCancelButton(false)
                        setConfirmClickListener { dismissWithAnimation() }

                    } else {

                        // ---------------------------------------------
                        // AÚN HAY INTENTOS → mostrar error y volver
                        // a llamar mostrarDialogoPinInterno() de nuevo
                        // para que el usuario siga intentando
                        // ---------------------------------------------
                        val restantes = MAX_INTENTOS - nuevosIntentos

                        changeAlertType(SweetAlertDialog.ERROR_TYPE)
                        titleText = "PIN incorrecto"
                        contentText = "Te quedan $restantes intento(s)"
                        showCancelButton(false)
                        setConfirmClickListener {
                            dismissWithAnimation()
                            // Llamada recursiva con un nuevo EditText limpio
                            val nuevoEtPin = EditText(this@MainActivity).apply {
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                                hint = "Ingresa el PIN"
                                setPadding(40, 20, 40, 20)
                            }
                            mostrarDialogoPinInterno(nuevoEtPin, prefs)
                        }
                    }
                }
            }

            setCancelClickListener { dismissWithAnimation() }

            show()
        }
    }


}