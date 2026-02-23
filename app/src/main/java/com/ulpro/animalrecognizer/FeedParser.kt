package com.ulpro.animalrecognizer

import org.json.JSONArray

object FeedParser {

    fun parsePosts(array: JSONArray): List<FeedPost> {
        val list = mutableListOf<FeedPost>()

        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)

            // ---------- MEDIA ----------
            val mediaArr = o.getJSONArray("media")
            val mediaList = mutableListOf<FeedMedia>()
            for (j in 0 until mediaArr.length()) {
                val m = mediaArr.getJSONObject(j)
                mediaList.add(
                    FeedMedia(
                        url_archivo = m.getString("url_archivo"),
                        es_principal = m.getInt("es_principal")
                    )
                )
            }

            // ---------- USUARIO ----------
            val u = o.getJSONObject("usuario")
            val user = FeedUser(
                id = u.getInt("id"),
                nombre_usuario = u.getString("nombre_usuario"),
                nombre_completo = u.getString("nombre_completo"),
                foto = u.optString("foto", null),
                siguiendo = u.getBoolean("siguiendo")
            )

            // ---------- ANIMAL ----------
            val a = o.getJSONObject("animal")
            val animal = FeedAnimal(
                id = a.getInt("id"),
                nombre_comun = a.getString("nombre_comun"),
                nombre_cientifico = a.getString("nombre_cientifico")
            )

            // ---------- POST ----------
            list.add(
                FeedPost(
                    avistamiento_id = o.getInt("avistamiento_id"),
                    titulo = o.getString("titulo"),
                    descripcion = o.getString("descripcion"),
                    fecha = o.getString("fecha"),
                    usuario = user,
                    animal = animal,
                    media = mediaList,
                    likes = FeedLikes(
                        total = o.getJSONObject("likes").getInt("total"),
                        yo_di_like = o.getJSONObject("likes").getBoolean("yo_di_like")
                    ),
                    comentarios = FeedComments(
                        total = o.getJSONObject("comentarios").getInt("total")
                    ),
                    es_recomendado = o.getBoolean("es_recomendado"),
                    ya_visto = o.getBoolean("ya_visto")
                )
            )
        }

        return list
    }
}
