package com.ulpro.animalrecognizer

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

// Modelo de datos para la solicitud
data class Message(val role: String, val content: String)
data class ChatRequest(val model: String, val messages: List<Message>, val stream: Boolean = false)
data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: Message)

// Interfaz Retrofit para DeepSeek
interface DeepSeekApiService {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): ChatResponse
}

class DeepSeekTranslator(apiKey: String) {
    private val api: DeepSeekApiService
    private val key = "Bearer $apiKey"

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(DeepSeekApiService::class.java)
    }

    suspend fun translate(text: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatRequest(
                    model = "deepseek-chat",
                    messages = listOf(
                        Message(role = "system", content = "You are a translator. Translate the given text into $targetLanguage."),
                        Message(role = "user", content = text)
                    )
                )
                val response = api.getChatCompletion(key, request)
                response.choices.firstOrNull()?.message?.content ?: text
            } catch (e: HttpException) {
                text // Devuelve el texto original si hay un error HTTP
            } catch (e: IOException) {
                text // Devuelve el texto original si hay un problema de red
            } catch (e: Exception) {
                text // Devuelve el texto original si ocurre cualquier otro error
            }
        }
    }
}
