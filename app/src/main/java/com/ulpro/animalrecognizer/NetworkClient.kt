package com.ulpro.animalrecognizer.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val CACHE_SIZE = (10 * 1024 * 1024).toLong() // 10 MB

    fun getClient(cacheDir: File): OkHttpClient {
        val cache = Cache(cacheDir, CACHE_SIZE)
        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}