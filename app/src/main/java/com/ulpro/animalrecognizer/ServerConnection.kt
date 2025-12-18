package com.ulpro.animalrecognizer

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ServerConnection(private val context: Context) {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun endpoint(path: String): String {
        return ServerConfig.BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun <T> onMain(block: () -> T) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post { block() }
    }

    fun login(
        usuarioOCorreo: String,
        password: String,
        callback: (Boolean, Any?) -> Unit
    ) {
        val url = endpoint("/api/usuarios/login.php")
        val dispositivo = "Android ${Build.VERSION.RELEASE} - ${Build.MODEL}"

        val formBody = FormBody.Builder()
            .add("usuario_o_correo", usuarioOCorreo)
            .add("password", password)
            .add("dispositivo", dispositivo)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onMain {
                    callback(false, "Error de red: ${e.localizedMessage ?: "desconocido"}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()

                val json = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    onMain { callback(false, "Respuesta inválida del servidor (${response.code})") }
                    return
                }

                // Error: {"error":"..."}
                if (json.has("error")) {
                    onMain { callback(false, json.optString("error", "Error desconocido")) }
                    return
                }

                // Éxito: {"mensaje":"...", "token":"...", "usuario":{...}}
                val token = json.optString("token", "")
                val mensaje = json.optString("mensaje", "")

                if (token.isNotBlank()) {
                    onMain { callback(true, token) } // ✅ SOLO token
                } else {
                    onMain {
                        callback(false, if (mensaje.isNotBlank()) mensaje else "Respuesta incompleta del servidor")
                    }
                }
            }
        })
    }

    fun verifySession(
        token: String,
        callback: (VerifyResult) -> Unit
    ) {
        val url = endpoint("/api/usuarios/verificar_sesion.php")

        val formBody = FormBody.Builder()
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onMain {
                    callback(VerifyResult.NetworkError("No se pudo conectar al servidor"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()

                val json = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    onMain { callback(VerifyResult.ServerError("Respuesta inválida del servidor (${response.code})")) }
                    return
                }

                // {"error":"..."} (405/500 u otros)
                if (json.has("error")) {
                    onMain { callback(VerifyResult.ServerError(json.optString("error", "Error desconocido"))) }
                    return
                }

                // {"activo": false, "mensaje":"No se envió el campo 'token'..." } (400)
                // {"activo": true/false, "mensaje":"..." }
                if (json.has("activo")) {
                    val activo = json.optBoolean("activo", false)
                    val mensaje = json.optString("mensaje", "")

                    if (activo) {
                        val usuarioJson = json.optJSONObject("usuario")
                        val paquete = usuarioJson?.optString("paquete_predeterminado", null)

                        onMain {
                            callback(
                                VerifyResult.Active(
                                    mensaje = mensaje,
                                    paquetePredeterminado = paquete
                                )
                            )
                        }
                    } else {
                        onMain {
                            callback(VerifyResult.Inactive(mensaje))
                        }
                    }
                    return
                }


                onMain { callback(VerifyResult.ServerError("Respuesta incompleta del servidor")) }
            }
        })
    }
}

sealed class VerifyResult {
    data class Active(val mensaje: String, val paquetePredeterminado: String?) : VerifyResult()
    data class Inactive(val mensaje: String) : VerifyResult()
    data class NetworkError(val mensaje: String) : VerifyResult()
    data class ServerError(val mensaje: String) : VerifyResult()
}
