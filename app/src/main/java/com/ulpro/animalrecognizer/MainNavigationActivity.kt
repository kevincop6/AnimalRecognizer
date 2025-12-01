package com.ulpro.animalrecognizer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import cn.pedant.SweetAlert.SweetAlertDialog

class MainNavigationActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var bottomNavHelper: BottomNavigationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_navigation)

        sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)

        if (!isUserLoggedIn()) {
            redirectToLoginActivity()
            return
        }

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

    private fun isUserLoggedIn(): Boolean {
        val email = sharedPreferences.getString("userEmail", null)
        return !email.isNullOrEmpty()
    }

    private fun redirectToLoginActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
