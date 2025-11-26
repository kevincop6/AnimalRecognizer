package com.ulpro.animalrecognizer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3 // Número de fragmentos

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AnimalesFragment()
            1 -> AportesFragment()
            2 -> UsuariosFragment()
            else -> throw IllegalStateException("Posición inválida")
        }
    }
}