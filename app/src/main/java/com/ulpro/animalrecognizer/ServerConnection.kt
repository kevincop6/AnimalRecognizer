package com.ulpro.animalrecognizer
import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ServerConnection(private val context: Context) {

    fun login(correo: String, contraseña: String, callback: (Boolean, String?) -> Unit) {
        val loginUrl = "${ServerConfig.BASE_URL}usuarios.php"
        LoginTask(correo, contraseña, callback).execute(loginUrl)
    }

    private inner class LoginTask(
        private val correo: String,
        private val contraseña: String,
        private val callback: (Boolean, String?) -> Unit
    ) : AsyncTask<String, Void, Pair<Boolean, String?>>() {

        override fun doInBackground(vararg params: String?): Pair<Boolean, String?> {
            val url = URL(params[0])
            val connection = url.openConnection() as HttpURLConnection
            var resultMessage: String? = null

            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                val postData = "correo=$correo&contraseña=$contraseña"
                val outputStream = OutputStreamWriter(connection.outputStream)
                outputStream.write(postData)
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }

                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.optBoolean("success", false)

                    return if (success) {
                        val usuarioId = jsonResponse.optString("usuario_id", "")
                        val nombreUsuario = jsonResponse.optString("nombre_usuario", "")
                        Pair(true, if (usuarioId.isNotEmpty() && nombreUsuario.isNotEmpty()) "$usuarioId|$nombreUsuario" else null)
                    } else {
                        val errorMessage = jsonResponse.optString("error", "Error desconocido")
                        Pair(false, errorMessage)
                    }
                } else {
                    resultMessage = "Error: Código de respuesta $responseCode"
                    return Pair(false, resultMessage)
                }
            } catch (e: Exception) {
                Log.e("ServerConnection", "Error en la conexión", e)
                resultMessage = "Error en la conexión: ${e.message}"
                return Pair(false, resultMessage)
            } finally {
                connection.disconnect()
            }
        }

        override fun onPostExecute(result: Pair<Boolean, String?>) {
            if (result.first) {
                callback(true, result.second)
            } else {
                Toast.makeText(context, "Error: ${result.second}", Toast.LENGTH_SHORT).show()
                callback(false, result.second)
            }
        }
    }
}
