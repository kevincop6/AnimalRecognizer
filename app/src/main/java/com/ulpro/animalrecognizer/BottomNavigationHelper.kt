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

    private var activeFragment: Fragment? = null

    // Fragment tags
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
            val fragment = getOrCreateFragment(avistamientosTag) { AvistamientosFragment() }
            switchFragment(fragment, avistamientosTag)
            markActiveButton(homeButton, R.color.orange, R.color.dark_gray)
        }

        galleryButton.setOnClickListener {
            val fragment = getOrCreateFragment(aportesTag) { AportesFragment() }
            switchFragment(fragment, aportesTag)
            markActiveButton(galleryButton, R.color.orange, R.color.dark_gray)
        }

        profileButton.setOnClickListener {
            val fragment = getOrCreateFragment(profileTag) { ProfileFragment() }
            switchFragment(fragment, profileTag)
            markActiveButton(profileButton, R.color.orange, R.color.dark_gray)
        }

        settingsButton.setOnClickListener {
            val fragment = getOrCreateFragment(settingsTag) { SettingsFragment() }
            switchFragment(fragment, settingsTag)
            markActiveButton(settingsButton, R.color.orange, R.color.dark_gray)
        }
    }

    fun showInitialFragment() {
        val initialFragment = getOrCreateFragment(avistamientosTag) { AvistamientosFragment() }
        switchFragment(initialFragment, avistamientosTag, isInitial = true)
        markActiveButton(homeButton, R.color.orange, R.color.dark_gray)
    }

    private fun getOrCreateFragment(tag: String, creator: () -> Fragment): Fragment {
        return fragmentManager.findFragmentByTag(tag) ?: creator()
    }

    internal fun switchFragment(fragment: Fragment, tag: String, isInitial: Boolean = false) {
        val transaction = fragmentManager.beginTransaction()

        activeFragment?.let {
            if (it != fragment) {
                transaction.hide(it)
            }
        }

        if (!fragment.isAdded) {
            transaction.add(R.id.fragmentContainer, fragment, tag)
        }
        transaction.show(fragment)

        if (isInitial) {
            transaction.addToBackStack(tag)
        }

        transaction.commit()
        activeFragment = fragment
    }

    fun markActiveButton(buttonLayout: LinearLayout, activeColor: Int, inactiveColor: Int) {
        val allButtons = listOf(homeButton, galleryButton, profileButton, settingsButton)

        allButtons.forEach { button ->
            val icon = button.findViewById<ImageView>(R.id.homeIcon)
                ?: button.findViewById(R.id.galleryIcon)
                ?: button.findViewById(R.id.profileIcon)
                ?: button.findViewById(R.id.SettingsIcon)

            val text = button.findViewById<TextView>(R.id.homeText)
                ?: button.findViewById(R.id.galleryText)
                ?: button.findViewById(R.id.profileText)
                ?: button.findViewById(R.id.Settingsext)

            val color = if (button == buttonLayout) activeColor else inactiveColor
            icon?.setColorFilter(ContextCompat.getColor(activity, color))
            text?.setTextColor(ContextCompat.getColor(activity, color))
        }
    }

    fun updateButtonStateBasedOnStack() {
        val currentFragment = activeFragment
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
