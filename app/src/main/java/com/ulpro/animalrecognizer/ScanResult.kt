package com.ulpro.animalrecognizer

data class ScanResult(
    val name: String,
    val confidence: Float,
    val dangerous: Boolean,
    val description: String
)