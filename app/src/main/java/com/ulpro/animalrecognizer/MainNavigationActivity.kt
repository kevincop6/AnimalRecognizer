package com.ulpro.animalrecognizer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog

class MainNavigationActivity : AppCompatActivity() {

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

        if (savedInstanceState == null) {
            val fragmentToOpen = intent.getStringExtra("open_fragment")
            if (fragmentToOpen == "SettingsFragment") {
                bottomNavHelper.switchFragment(SettingsFragment(), "SETTINGS_FRAGMENT")
                bottomNavHelper.markActiveButton(findViewById(R.id.SettingsButton), R.color.orange, R.color.dark_gray)
            } else {
                bottomNavHelper.showInitialFragment()
            }
        }
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

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 1) {
            super.onBackPressed()
            bottomNavHelper.updateButtonStateBasedOnStack()
        } else {
            SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText("¿Deseas salir?")
                .setContentText("La aplicación se cerrará.")
                .setConfirmText("Sí")
                .setCancelText("No")
                .setConfirmClickListener { finishAffinity() }
                .setCancelClickListener { it.dismissWithAnimation() }
                .show()
        }
    }
}
