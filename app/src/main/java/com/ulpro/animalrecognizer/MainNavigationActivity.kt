package com.ulpro.animalrecognizer

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import cn.pedant.SweetAlert.SweetAlertDialog

class MainNavigationActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    private lateinit var bottomNavHelper: BottomNavigationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_navigation)

        ServerConfig.initialize(this)

        // ✅ Autenticación basada en token
        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            redirectToLoginActivity()
            return
        }

        // ✅ Si hay internet, validamos sesión con el servidor (solo entra si activo=true)
        // ✅ Si no hay internet, permitimos modo sin conexión
        if (hasInternet()) {
            ServerConnection(this).verifySession(token) { result ->
                when (result) {
                    is VerifyResult.Active -> {
                        // Guardar paquete_predeterminado si llegó
                        result.paquetePredeterminado?.let { UserPrefs.savePaquete(this, it) }
                        setupUi(savedInstanceState)
                    }

                    is VerifyResult.Inactive -> {
                        TokenStore.clearToken(this)
                        redirectToLoginActivity()
                    }

                    is VerifyResult.ServerError -> {
                        // servidor respondió con error => NO permitir
                        redirectToLoginActivity()
                    }

                    is VerifyResult.NetworkError -> {
                        // no se pudo conectar aunque “hay internet” => permitir offline
                        setupUi(savedInstanceState)
                    }
                }
            }
        } else {
            setupUi(savedInstanceState)
        }
    }

    private fun setupUi(savedInstanceState: Bundle?) {
        bottomNavHelper = BottomNavigationHelper(this, supportFragmentManager)
        bottomNavHelper.setup()

        supportFragmentManager.addOnBackStackChangedListener(this)

        if (savedInstanceState == null) {
            val fragmentToOpen = intent.getStringExtra("open_fragment")
            if (fragmentToOpen == "SettingsFragment") {
                bottomNavHelper.switchFragment(SettingsFragment(), "SETTINGS_FRAGMENT")
            } else {
                bottomNavHelper.showInitialFragment()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    SweetAlertDialog(this@MainNavigationActivity, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("¿Deseas salir?")
                        .setContentText("La aplicación se cerrará.")
                        .setConfirmText("Sí")
                        .setCancelText("No")
                        .setConfirmClickListener { finishAffinity() }
                        .setCancelClickListener { it.dismissWithAnimation() }
                        .show()
                }
            }
        })
    }

    override fun onBackStackChanged() {
        bottomNavHelper.updateButtonState()
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun redirectToLoginActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
