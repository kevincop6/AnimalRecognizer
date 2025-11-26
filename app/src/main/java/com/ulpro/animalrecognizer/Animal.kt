package com.ulpro.animalrecognizer

data class Animal(val id: Int, val name: String, val imageBase64: String) {
    fun toJson(): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("id", id)
            put("name", name)
            put("imageBase64", imageBase64)
        }
    }
}