package com.ulpro.animalrecognizer


import android.content.Intent
import android.content.SharedPreferences
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
import androidx.core.content.edit
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlin.compareTo

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var lastClickTime: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        val firstRunPreferences = getSharedPreferences("firstRunConfig", MODE_PRIVATE)
        val isFirstRun = firstRunPreferences.getBoolean("isFirstRun", true)
        val animalLogo: ImageView = findViewById(R.id.AnimalLogo)
        if (isFirstRun) {
            val serverPreferences = getSharedPreferences("serverConfig", MODE_PRIVATE)
            serverPreferences.edit {
                putString("serverUrl", "http://192.168.100.126/avistamiento/")
                apply()
            }
            Toast.makeText(this, "Ajustes del servidor aplicados", Toast.LENGTH_SHORT).show()
            firstRunPreferences.edit {
                putBoolean("isFirstRun", false)
                apply()
            }
        }

        ServerConfig.initialize(this)
        sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)
        requestEnableNotifications()

        if (isUserLoggedIn()) {
            redirectToProgressActivity()
        }

        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val correo = emailEditText.text.toString()
            val contrasena = passwordEditText.text.toString()

            if (correo.isNotEmpty() && contrasena.isNotEmpty()) {
                val loadingDialog = SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE).apply {
                    titleText = "Verificando..."
                    setCancelable(false)
                    show()
                }

                val serverConnection = ServerConnection(this)
                serverConnection.login(correo, contrasena) { success, result ->
                    loadingDialog.dismissWithAnimation()

                    if (success && result != null) {
                        val (usuarioId, nombreUsuario) = result.split("|")
                        saveUserSession(correo, usuarioId, nombreUsuario)

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
                        Toast.makeText(this, "Error en el inicio de sesión: ${result ?: "Desconocido"}", Toast.LENGTH_SHORT).show()
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

    private fun isUserLoggedIn(): Boolean {
        val email = sharedPreferences.getString("userEmail", null)
        val userId = sharedPreferences.getString("usuario_id", null)
        return email != null && userId != null
    }

    private fun saveUserSession(email: String, userId: String, nombreUsuario: String) {
        sharedPreferences.edit {
            putString("userEmail", email)
            putString("usuario_id", userId)
            putString("nombre_usuario", nombreUsuario)
            apply()
        }
    }

    private fun redirectToProgressActivity() {
        val intent = Intent(this, ProgressActivity::class.java)
        startActivity(intent)
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
}