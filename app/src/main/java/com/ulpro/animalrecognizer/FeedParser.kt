package com.ulpro.animalrecognizer

import org.json.JSONArray

object FeedParser {

    fun parsePosts(array: JSONArray): List<FeedPost> {
        val list = mutableListOf<FeedPost>()

        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)

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

            list.add(
                FeedPost(
                    avistamiento_id = o.getInt("avistamiento_id"),
                    descripcion = o.getString("descripcion"),
                    fecha = o.getString("fecha"),
                    usuario = FeedUser(
                        nombre_usuario = o.getJSONObject("usuario").getString("nombre_usuario"),
                        foto = o.getJSONObject("usuario").optString("foto", null)
                    ),
                    media = mediaList,
                    likes = FeedLikes(
                        total = o.getJSONObject("likes").getInt("total"),
                        yo_di_like = o.getJSONObject("likes").getBoolean("yo_di_like")
                    ),
                    comentarios = FeedComments(
                        total = o.getJSONObject("comentarios").getInt("total")
                    )
                )
            )
        }
        return list
    }
}
