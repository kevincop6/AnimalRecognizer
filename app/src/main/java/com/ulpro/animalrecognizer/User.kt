package com.ulpro.animalrecognizer

data class User(
    val id: String,
    val name: String,
    val username: String,
    val imageUrl: String?,
    val likes: Int = 0
)