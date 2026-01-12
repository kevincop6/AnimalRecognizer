package com.ulpro.animalrecognizer

import android.app.Activity
import android.content.Intent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BottomNavigationHelper(
    private val activity: Activity,
    private val fragmentManager: FragmentManager
) {

    // Tags
    private val homeTag = "AVISTAMIENTOS_FRAGMENT"
    private val categoriesTag = "CATEGORIES_FRAGMENT"
    private val favoritesTag = "FAVORITES_FRAGMENT"
    private val profileTag = "PROFILE_FRAGMENT"

    // Buttons
    private val homeButton: LinearLayout by lazy { activity.findViewById(R.id.homeButton) }
    private val categoriesButton: LinearLayout by lazy { activity.findViewById(R.id.categoriesButton) }
    private val favoritesButton: LinearLayout by lazy { activity.findViewById(R.id.favoritesButton) }
    private val profileButton: LinearLayout by lazy { activity.findViewById(R.id.profileButton) }
    private val scanFab: FloatingActionButton by lazy { activity.findViewById(R.id.scanFab) }

    fun setup() {

        homeButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is AvistamientosFragment) return@setOnClickListener
            switchFragment(getOrCreateFragment(homeTag) { AvistamientosFragment() }, homeTag)
        }

        categoriesButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is AnimalesFragment) return@setOnClickListener
            switchFragment(getOrCreateFragment(categoriesTag) { AnimalesFragment() }, categoriesTag)
        }

        favoritesButton.setOnClickListener {

        }

        profileButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is ProfileFragment) return@setOnClickListener
            switchFragment(getOrCreateFragment(profileTag) { ProfileFragment() }, profileTag)
        }

        // FAB central (NO fragment)
        scanFab.setOnClickListener {
            activity.startActivity(
                Intent(activity, LiveScanActivity::class.java)
            )
        }
    }

    fun showInitialFragment() {
        switchFragment(
            getOrCreateFragment(homeTag) { AvistamientosFragment() },
            homeTag,
            addToBackStack = false
        )
    }

    private fun getOrCreateFragment(tag: String, creator: () -> Fragment): Fragment {
        return fragmentManager.findFragmentByTag(tag) ?: creator()
    }

    private fun switchFragment(
        fragment: Fragment,
        tag: String,
        addToBackStack: Boolean = true
    ) {
        val ft = fragmentManager.beginTransaction()

        fragmentManager.primaryNavigationFragment?.let {
            ft.hide(it)
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
        updateButtonState()
    }

    // -----------------------------
    // UI State
    // -----------------------------

    fun updateButtonState() {
        val current = fragmentManager.primaryNavigationFragment

        markButtons(
            activeButton = when (current) {
                is AvistamientosFragment -> homeButton
                is AnimalesFragment -> categoriesButton
                is ProfileFragment -> profileButton
                else -> null
            }
        )
    }

    private fun markButtons(activeButton: LinearLayout?) {
        val activeColor = R.color.primary
        val inactiveColor = R.color.text_secondary

        val buttons = listOf(
            homeButton,
            categoriesButton,
            favoritesButton,
            profileButton
        )

        buttons.forEach { button ->
            val icon = button.findViewById<ImageView>(
                when (button) {
                    homeButton -> R.id.homeIcon
                    categoriesButton -> R.id.categoriesIcon
                    favoritesButton -> R.id.favoritesIcon
                    else -> R.id.profileIcon
                }
            )

            val text = button.findViewById<TextView>(
                when (button) {
                    homeButton -> R.id.homeText
                    categoriesButton -> R.id.categoriesText
                    favoritesButton -> R.id.favoritesText
                    else -> R.id.profileText
                }
            )

            val colorRes = if (button == activeButton) activeColor else inactiveColor
            val color = ContextCompat.getColor(activity, colorRes)

            icon.setColorFilter(color)
            text.setTextColor(color)
        }
    }
}
