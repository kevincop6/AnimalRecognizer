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

    // -----------------------------
    // TAGS
    // -----------------------------
    private val homeTag = "AVISTAMIENTOS_FRAGMENT"
    private val categoriesTag = "CATEGORIES_FRAGMENT"
    private val favoritesTag = "FAVORITES_FRAGMENT"
    private val profileTag = "PROFILE_FRAGMENT"

    // -----------------------------
    // BOTONES
    // -----------------------------
    private val homeButton: LinearLayout by lazy { activity.findViewById(R.id.homeButton) }
    private val categoriesButton: LinearLayout by lazy { activity.findViewById(R.id.categoriesButton) }
    private val favoritesButton: LinearLayout by lazy { activity.findViewById(R.id.favoritesButton) }
    private val profileButton: LinearLayout by lazy { activity.findViewById(R.id.profileButton) }
    private val scanFab: FloatingActionButton by lazy { activity.findViewById(R.id.scanFab) }

    // -----------------------------
    // SETUP
    // -----------------------------
    fun setup() {

        // HOME
        homeButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is AvistamientosFragment) return@setOnClickListener
            switchFragment(
                getOrCreateFragment(homeTag) { AvistamientosFragment() },
                homeTag
            )
        }

        // CATEGORÍAS
        categoriesButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is fragment_categorias) return@setOnClickListener
            switchFragment(
                getOrCreateFragment(categoriesTag) { fragment_categorias() },
                categoriesTag
            )
        }

        // FAVORITOS (pendiente)
        favoritesButton.setOnClickListener {
            // TODO: implementar FavoritesFragment
        }

        // PERFIL
        profileButton.setOnClickListener {
            if (fragmentManager.primaryNavigationFragment is ProfileFragment) return@setOnClickListener
            switchFragment(
                getOrCreateFragment(profileTag) { ProfileFragment() },
                profileTag
            )
        }

        // FAB CENTRAL (NO ES FRAGMENT)
        scanFab.setOnClickListener {
            activity.startActivity(
                Intent(activity, LiveScanActivity::class.java)
            )
        }
    }

    // -----------------------------
    // FRAGMENT INICIAL
    // -----------------------------
    fun showInitialFragment() {
        switchFragment(
            getOrCreateFragment(homeTag) { AvistamientosFragment() },
            homeTag,
            addToBackStack = false
        )
    }

    // -----------------------------
    // FRAGMENT MANAGEMENT
    // -----------------------------
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
    // UI STATE
    // -----------------------------
    fun updateButtonState() {
        val current = fragmentManager.primaryNavigationFragment

        val activeButton = when (current) {
            is AvistamientosFragment -> homeButton
            is fragment_categorias -> categoriesButton
            is ProfileFragment -> profileButton
            else -> null
        }

        markButtons(activeButton)
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

            val iconId = when (button) {
                homeButton -> R.id.homeIcon
                categoriesButton -> R.id.categoriesIcon
                favoritesButton -> R.id.favoritesIcon
                else -> R.id.profileIcon
            }

            val textId = when (button) {
                homeButton -> R.id.homeText
                categoriesButton -> R.id.categoriesText
                favoritesButton -> R.id.favoritesText
                else -> R.id.profileText
            }

            val icon = button.findViewById<ImageView>(iconId)
            val text = button.findViewById<TextView>(textId)

            val colorRes = if (button == activeButton) activeColor else inactiveColor
            val color = ContextCompat.getColor(activity, colorRes)

            icon.setColorFilter(color)
            text.setTextColor(color)
        }
    }
}
