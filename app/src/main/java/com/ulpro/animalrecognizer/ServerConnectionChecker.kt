package com.ulpro.animalrecognizer

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

class ServerConnectionChecker(private val context: Context) {

    fun checkConnection(callback: (Boolean) -> Unit) {
        val serverUrl = "${ServerConfig.BASE_URL}verify_connection.php"  // Endpoint para verificar la conexión
        ConnectionTask(callback).execute(serverUrl)
    }

    private inner class ConnectionTask(private val callback: (Boolean) -> Unit) : AsyncTask<String, Void, Boolean>() {

        override fun doInBackground(vararg params: String?): Boolean {
            val url = URL(params[0])
            var connection: HttpURLConnection? = null
            return try {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"  // O POST dependiendo de la API
                connection.connectTimeout = 5000  // 5 segundos de tiempo de espera
                connection.readTimeout = 5000
                connection.connect()

                // Leer la respuesta del servidor
                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                // Verificar si la respuesta es válida (por ejemplo, 'success')
                if (responseCode == HttpURLConnection.HTTP_OK && responseMessage.contains("success")) {
                    return true
                } else {
                    return false
                }
            } catch (e: Exception) {
                Log.e("ServerConnectionChecker", "Error al conectar con la API", e)
                false
            } finally {
                connection?.disconnect()
            }
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                Toast.makeText(context, "Conexión exitosa al servidor", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No se pudo conectar al servidor", Toast.LENGTH_SHORT).show()
            }

            callback(result)
        }
    }
}
