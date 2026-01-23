package com.ulpro.animalrecognizer

import kotlin.math.floor

object NumberFormatter {

    fun compact(value: Int): String {
        return when {
            value < 1_000 -> value.toString()

            value < 1_000_000 -> {
                val v = value / 1_000.0
                if (v % 1 == 0.0) "${v.toInt()}k"
                else "${floor(v * 10) / 10}k"
            }

            else -> {
                val v = value / 1_000_000.0
                if (v % 1 == 0.0) "${v.toInt()}M"
                else "${floor(v * 10) / 10}M"
            }
        }
    }
}