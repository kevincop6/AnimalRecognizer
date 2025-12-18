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
                val intent = Intent(this, MainNavigationActivity::class.java)
                intent.putExtra("open_fragment", "SettingsFragment")
                startActivity(intent)
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
}