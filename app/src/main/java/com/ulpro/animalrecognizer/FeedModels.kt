package com.ulpro.animalrecognizer

data class FeedPost(
    val avistamiento_id: Int,
    val descripcion: String,
    val fecha: String,
    val usuario: FeedUser,
    val media: List<FeedMedia>,
    val likes: FeedLikes,
    val comentarios: FeedComments
)

data class FeedUser(
    val nombre_usuario: String,
    val foto: String?
)

data class FeedMedia(
    val url_archivo: String,
    val es_principal: Int
)

data class FeedLikes(
    val total: Int,
    val yo_di_like: Boolean
)

data class FeedComments(
    val total: Int
)