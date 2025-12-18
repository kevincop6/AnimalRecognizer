package com.ulpro.animalrecognizer

import android.app.Activity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class BottomNavigationHelper(
    private val activity: Activity,
    private val fragmentManager: FragmentManager
) {

    private val avistamientosTag = "AVISTAMIENTOS_FRAGMENT"
    private val aportesTag = "APORTES_FRAGMENT"
    private val profileTag = "PROFILE_FRAGMENT"
    private val settingsTag = "SETTINGS_FRAGMENT"

    private val homeButton: LinearLayout by lazy { activity.findViewById(R.id.homeButton) }
    private val galleryButton: LinearLayout by lazy { activity.findViewById(R.id.galleryButton) }
    private val profileButton: LinearLayout by lazy { activity.findViewById(R.id.profileButton) }
    private val settingsButton: LinearLayout by lazy { activity.findViewById(R.id.SettingsButton) }

    fun setup() {
        homeButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is AvistamientosFragment) return@setOnClickListener
            val fragment = getOrCreateFragment(avistamientosTag) { AvistamientosFragment() }
            switchFragment(fragment, avistamientosTag)
        }
        galleryButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is AportesFragment) return@setOnClickListener
            val fragment = getOrCreateFragment(aportesTag) { AportesFragment() }
            switchFragment(fragment, aportesTag)
        }
        profileButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is ProfileFragment) return@setOnClickListener
            val fragment = getOrCreateFragment(profileTag) { ProfileFragment() }
            switchFragment(fragment, profileTag)
        }
        settingsButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is SettingsFragment) return@setOnClickListener
            val fragment = getOrCreateFragment(settingsTag) { SettingsFragment() }
            switchFragment(fragment, settingsTag)
        }
    }

    fun showInitialFragment() {
        val initialFragment = getOrCreateFragment(avistamientosTag) { AvistamientosFragment() }
        switchFragment(initialFragment, avistamientosTag, addToBackStack = false)
    }

    private fun getOrCreateFragment(tag: String, creator: () -> Fragment): Fragment {
        return fragmentManager.findFragmentByTag(tag) ?: creator()
    }

    internal fun switchFragment(fragment: Fragment, tag: String, addToBackStack: Boolean = true) {
        val ft = fragmentManager.beginTransaction()
        val currentFragment = fragmentManager.primaryNavigationFragment
        if (currentFragment != null) {
            ft.hide(currentFragment)
        }

        if (fragment.isAdded) {
            ft.show(fragment)
        } else {
            ft.add(R.id.fragmentContainer, fragment, tag)
        }

        ft.setPrimaryNavigationFragment(fragment)
        ft.setReorderingAllowed(true)
        if (addToBackStack) {
            ft.addToBackStack(tag)
        }
        ft.commit()
        updateButtonState() // Actualiza inmediatamente el estado del botón
    }

    fun markActiveButton(buttonLayout: LinearLayout, activeColor: Int, inactiveColor: Int) {
        val allButtons = listOf(homeButton, galleryButton, profileButton, settingsButton)

        allButtons.forEach { button ->
            val icon = button.findViewById<ImageView>(R.id.homeIcon)
                ?: button.findViewById<ImageView>(R.id.galleryIcon)
                ?: button.findViewById<ImageView>(R.id.profileIcon)
                ?: button.findViewById<ImageView>(R.id.SettingsIcon)

            val text = button.findViewById<TextView>(R.id.homeText)
                ?: button.findViewById<TextView>(R.id.galleryText)
                ?: button.findViewById<TextView>(R.id.profileText)
                ?: button.findViewById<TextView>(R.id.Settingsext)

            val color = if (button == buttonLayout) activeColor else inactiveColor
            icon.setColorFilter(ContextCompat.getColor(activity, color))
            text.setTextColor(ContextCompat.getColor(activity, color))
        }
    }

    fun updateButtonState() {
        val currentFragment = fragmentManager.primaryNavigationFragment
        val buttonToMark = when (currentFragment) {
            is AvistamientosFragment -> homeButton
            is AportesFragment -> galleryButton
            is ProfileFragment -> profileButton
            is SettingsFragment -> settingsButton
            else -> null
        }
        buttonToMark?.let {
            markActiveButton(it, R.color.orange, R.color.dark_gray)
        }
    }
}
