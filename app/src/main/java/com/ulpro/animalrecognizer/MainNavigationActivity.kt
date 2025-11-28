package com.ulpro.animalrecognizer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog

class MainNavigationActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_navigation)

        sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)



        val fragmentToOpen = intent.getStringExtra("open_fragment")
        val allowedFragments = listOf("SettingsFragment") // Lista de fragmentos permitidos

        if (allowedFragments.contains(fragmentToOpen)) {
            // Deshabilitar todos los botones de navegación
            findViewById<View>(R.id.homeButton).isEnabled = false
            findViewById<View>(R.id.profileButton).isEnabled = false
            findViewById<View>(R.id.SettingsButton).isEnabled = false

            // Abrir SettingsFragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsFragment())
                .commit()
        }else {
            if (isUserLoggedIn()) {
                val bottomNavigationHelper = BottomNavigationHelper(this, supportFragmentManager)
                bottomNavigationHelper.setup(R.id.homeButton)

                // Mostrar el AvistamientosFragment al iniciar la actividad
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AvistamientosFragment())
                    .addToBackStack(null) // Agregar al stack de retroceso
                    .commit()
                // Marcar el botón de inicio como activo
                bottomNavigationHelper.markActiveButton(
                    findViewById(R.id.homeButton),
                    R.color.orange,
                    R.color.dark_gray
                )
            } else {
                redirectToLoginActivity()  // Si no hay sesión, vuelve al login
            }
        }
    }
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        // Si hay más de un fragmento en la pila, simplemente volvemos al anterior.
        // Se considera 1 como el estado inicial (el primer fragmento).
        if (supportFragmentManager.backStackEntryCount > 1) {
            supportFragmentManager.popBackStack()
        } else {
            // Si estamos en el primer fragmento o no hay ninguno, mostramos el diálogo para salir.
            SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText("¿Estás seguro de que deseas salir?")
                .setContentText("Se cerrará la aplicación.")
                .setConfirmText("Sí")
                .setCancelText("No")
                .setConfirmClickListener {
                    finishAffinity()
                }
                .setCancelClickListener { dialog ->
                    dialog.dismissWithAnimation() // Cierra el diálogo
                }
                .show()
        }
    }
    private fun isUserLoggedIn(): Boolean {
        val usuarioId = sharedPreferences.getString("usuario_id", null) // Verificar clave correcta
        val email = sharedPreferences.getString("userEmail", null)
        return !usuarioId.isNullOrEmpty() && !email.isNullOrEmpty()  // Evita cadenas vacías
    }

    // Redirigir al usuario a AvistamientosActivity
    private fun redirectToAvistamientosActivity() {
        val intent = Intent(this, MainNavigationActivity::class.java)
        startActivity(intent)
        finish()  // Cierra ProgressActivity para evitar que el usuario regrese
    }

    // Redirigir al usuario a MainActivity (login)
    private fun redirectToLoginActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()  // Cierra ProgressActivity para evitar navegación hacia atrás
    }
}