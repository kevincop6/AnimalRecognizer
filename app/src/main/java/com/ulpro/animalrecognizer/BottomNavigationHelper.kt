package com.ulpro.animalrecognizer

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import cn.pedant.SweetAlert.SweetAlertDialog

class BottomNavigationHelper(
    private val activity: Activity,
    private val fragmentManager: FragmentManager
) {

    fun setup(
        activeButtonId: Int,
        activeColor: Int = R.color.black,
        inactiveColor: Int = R.color.dark_gray
    ) {
        val homeButton = activity.findViewById<LinearLayout>(R.id.homeButton)
        val galleryButton = activity.findViewById<LinearLayout>(R.id.galleryButton)
        val profileButton = activity.findViewById<LinearLayout>(R.id.profileButton)
        val settingsButton = activity.findViewById<LinearLayout>(R.id.SettingsButton)

        val buttons = listOf(homeButton, galleryButton, profileButton, settingsButton)

        // Marcar activo
        buttons.forEach { button ->
            if (button.id == activeButtonId) {
                markActiveButton(button, activeColor, inactiveColor)
            }
        }

        // Activar el listener para el stack de fragmentos
        setupBackStackListener()

        // Asignar acciones
        homeButton.setOnClickListener {
            val progressDialog = SweetAlertDialog(activity, SweetAlertDialog.PROGRESS_TYPE)
            progressDialog.titleText = "Cargando..."
            progressDialog.setCancelable(false)
            progressDialog.show()

            val fragment = AvistamientosFragment()
            fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()

            Handler(Looper.getMainLooper()).postDelayed({
                progressDialog.dismissWithAnimation()
                fragment.loadAnimalsBasedOnWorkerState()
            }, 3000)
        }

        profileButton.setOnClickListener {
            val progressDialog = SweetAlertDialog(activity, SweetAlertDialog.PROGRESS_TYPE)
            progressDialog.titleText = "Cargando..."
            progressDialog.setCancelable(false)
            progressDialog.show()
            replaceFragment(ProfileFragment())

            Handler(Looper.getMainLooper()).postDelayed({
                progressDialog.dismissWithAnimation()
            }, 3000)
        }

        settingsButton.setOnClickListener {
            val progressDialog = SweetAlertDialog(activity, SweetAlertDialog.PROGRESS_TYPE)
            progressDialog.titleText = "Cargando..."
            progressDialog.setCancelable(false)
            progressDialog.show()
            replaceFragment(SettingsFragment())

            Handler(Looper.getMainLooper()).postDelayed({
                progressDialog.dismissWithAnimation()
            }, 3000)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        fragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null) // Agregar al stack de retroceso
            .commit()

        when (fragment) {
            is AvistamientosFragment -> markActiveButton(activity.findViewById(R.id.homeButton), R.color.orange, R.color.dark_gray)
            is ProfileFragment -> markActiveButton(activity.findViewById(R.id.profileButton), R.color.orange, R.color.dark_gray)
            is SettingsFragment -> markActiveButton(activity.findViewById(R.id.SettingsButton), R.color.orange, R.color.dark_gray)
            else -> {
                // Resalta otros botones si es necesario
            }
        }
    }
    fun setupBackStackListener() {
        fragmentManager.addOnBackStackChangedListener {
            val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainer)
            when (currentFragment) {
                is AvistamientosFragment -> markActiveButton(activity.findViewById(R.id.homeButton), R.color.orange, R.color.dark_gray)
                is ProfileFragment -> markActiveButton(activity.findViewById(R.id.profileButton), R.color.orange, R.color.dark_gray)
                is SettingsFragment -> markActiveButton(activity.findViewById(R.id.SettingsButton), R.color.orange, R.color.dark_gray)
                else -> {
                    // Manejo de otros fragmentos si es necesario
                }
            }
        }
    }
    fun markActiveButton(buttonLayout: LinearLayout, activeColor: Int, inactiveColor: Int) {
        val imageView = buttonLayout.findViewById<ImageView>(R.id.homeIcon) ?:
        buttonLayout.findViewById<ImageView>(R.id.galleryIcon) ?:
        buttonLayout.findViewById<ImageView>(R.id.profileIcon) ?:
        buttonLayout.findViewById<ImageView>(R.id.SettingsIcon)

        val textView = buttonLayout.findViewById<TextView>(R.id.homeText) ?:
        buttonLayout.findViewById<TextView>(R.id.galleryText) ?:
        buttonLayout.findViewById<TextView>(R.id.profileText) ?:
        buttonLayout.findViewById<TextView>(R.id.Settingsext)

        // Cambiar colores del botón activo
        imageView?.setColorFilter(ContextCompat.getColor(activity, activeColor))
        textView?.setTextColor(ContextCompat.getColor(activity, activeColor))

        // Cambiar colores de los botones inactivos
        val parent = buttonLayout.parent as LinearLayout
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) as LinearLayout
            if (child != buttonLayout) {
                val childImage = child.findViewById<ImageView>(R.id.homeIcon) ?:
                child.findViewById<ImageView>(R.id.galleryIcon) ?:
                child.findViewById<ImageView>(R.id.profileIcon) ?:
                child.findViewById<ImageView>(R.id.SettingsIcon)

                val childText = child.findViewById<TextView>(R.id.homeText) ?:
                child.findViewById<TextView>(R.id.galleryText) ?:
                child.findViewById<TextView>(R.id.profileText) ?:
                child.findViewById<TextView>(R.id.Settingsext)

                childImage?.setColorFilter(ContextCompat.getColor(activity, inactiveColor))
                childText?.setTextColor(ContextCompat.getColor(activity, inactiveColor))
            }
        }
    }
}