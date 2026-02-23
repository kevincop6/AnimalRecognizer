package com.ulpro.animalrecognizer

data class FeedPost(
    val avistamiento_id: Int,
    val titulo: String,
    val descripcion: String,
    val fecha: String,
    val usuario: FeedUser,
    val animal: FeedAnimal,
    val media: List<FeedMedia>,
    val likes: FeedLikes,
    val comentarios: FeedComments,
    val es_recomendado: Boolean,
    val ya_visto: Boolean
)

data class FeedUser(
    val id: Int,
    val nombre_usuario: String,
    val nombre_completo: String,
    val foto: String?,
    val siguiendo: Boolean
)

data class FeedAnimal(
    val id: Int,
    val nombre_comun: String,
    val nombre_cientifico: String
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
