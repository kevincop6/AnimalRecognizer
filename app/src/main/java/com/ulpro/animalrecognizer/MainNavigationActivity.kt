package com.ulpro.animalrecognizer

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import cn.pedant.SweetAlert.SweetAlertDialog

class MainNavigationActivity : AppCompatActivity(),
    FragmentManager.OnBackStackChangedListener {

    private lateinit var bottomNavHelper: BottomNavigationHelper

    private val sessionCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionCheckIntervalMs = 60_000L

    private var uiReady = false

    private val sessionCheckRunnable = object : Runnable {
        override fun run() {
            val token = TokenStore.getToken(this@MainNavigationActivity)
            if (token.isNullOrBlank()) {
                logoutAndRedirect()
                return
            }

            if (!hasInternet()) {
                sessionCheckHandler.postDelayed(this, sessionCheckIntervalMs)
                return
            }

            ServerConnection(this@MainNavigationActivity)
                .verifySession(token) { result ->
                    when (result) {
                        is VerifyResult.Active -> {
                            result.paquetePredeterminado?.let {
                                UserPrefs.savePaquete(this@MainNavigationActivity, it)
                            }
                            sessionCheckHandler.postDelayed(this, sessionCheckIntervalMs)
                        }

                        is VerifyResult.Inactive,
                        is VerifyResult.ServerError -> {
                            logoutAndRedirect()
                        }

                        is VerifyResult.NetworkError -> {
                            sessionCheckHandler.postDelayed(this, sessionCheckIntervalMs)
                        }
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_navigation)

        ServerConfig.initialize(this)

        val token = TokenStore.getToken(this)
        if (token.isNullOrBlank()) {
            logoutAndRedirect()
            return
        }

        if (hasInternet()) {
            ServerConnection(this).verifySession(token) { result ->
                when (result) {
                    is VerifyResult.Active -> {
                        result.paquetePredeterminado?.let {
                            UserPrefs.savePaquete(this, it)
                        }
                        setupUi(savedInstanceState)
                    }

                    is VerifyResult.Inactive,
                    is VerifyResult.ServerError -> {
                        logoutAndRedirect()
                    }

                    is VerifyResult.NetworkError -> {
                        setupUi(savedInstanceState)
                    }
                }
            }
        } else {
            setupUi(savedInstanceState)
        }
    }

    override fun onStart() {
        super.onStart()
        if (uiReady) {
            sessionCheckHandler.removeCallbacks(sessionCheckRunnable)
            sessionCheckHandler.post(sessionCheckRunnable)
        }
    }

    override fun onStop() {
        super.onStop()
        sessionCheckHandler.removeCallbacks(sessionCheckRunnable)
    }

    private fun setupUi(savedInstanceState: Bundle?) {
        bottomNavHelper = BottomNavigationHelper(this, supportFragmentManager)
        bottomNavHelper.setup()

        supportFragmentManager.addOnBackStackChangedListener(this)

        if (savedInstanceState == null) {
            // 🔹 siempre arrancamos en Home (Avistamientos)
            bottomNavHelper.showInitialFragment()
        }

        uiReady = true
        sessionCheckHandler.removeCallbacks(sessionCheckRunnable)
        sessionCheckHandler.post(sessionCheckRunnable)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        SweetAlertDialog(
                            this@MainNavigationActivity,
                            SweetAlertDialog.WARNING_TYPE
                        )
                            .setTitleText("¿Deseas salir?")
                            .setContentText("La aplicación se cerrará.")
                            .setConfirmText("Sí")
                            .setCancelText("No")
                            .setConfirmClickListener {
                                finishAffinity()
                            }
                            .setCancelClickListener {
                                it.dismissWithAnimation()
                            }
                            .show()
                    }
                }
            }
        )
    }

    override fun onBackStackChanged() {
        bottomNavHelper.updateButtonState()
    }

    private fun logoutAndRedirect() {
        sessionCheckHandler.removeCallbacks(sessionCheckRunnable)
        TokenStore.clearToken(this)
        UserPrefs.clear(this)
        redirectToLoginActivity()
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