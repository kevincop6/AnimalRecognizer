package com.ulpro.animalrecognizer

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import android.text.Editable
import android.text.TextWatcher
import kotlin.toString
import android.content.Context
// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val serverAddressEditText = view.findViewById<EditText>(R.id.serverAddressEditText)
        val jsonWeightTextView = view.findViewById<EditText>(R.id.jsonWeightTextView)

        // Inicializar el EditText con la URL del servidor actual
        serverAddressEditText.setText(ServerConfig.BASE_URL)
        serverAddressEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // No se necesita implementar
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // No se necesita implementar
            }

            override fun afterTextChanged(s: Editable?) {
                val serverAddress = serverAddressEditText.text.toString()
                if (serverAddress.isNotEmpty()) {
                    ServerConfig.updateBaseUrl(requireContext(), serverAddress)
                    Toast.makeText(requireContext(), "Servidor actualizado a: $serverAddress", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Por favor, ingrese una dirección de servidor", Toast.LENGTH_SHORT).show()
                }
            }
        })

        sharedPreferences = requireActivity().getSharedPreferences("userSession", Context.MODE_PRIVATE)
        val usuarioId = sharedPreferences.getString("usuario_id", null)
        if (usuarioId == null) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "${ServerConfig.BASE_URL}size.php"

        // Llamar a la función asíncrona para obtener el tamaño del JSON
        fetchJsonSize(url, jsonWeightTextView)
    }
    private fun fetchJsonSize(urlString: String, jsonWeightTextView: TextView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val humanSize = jsonResponse.getString("human_size") // Obtener el valor de human_size

                    withContext(Dispatchers.Main) {
                        jsonWeightTextView.text = getString(R.string.json_size, humanSize) // Mostrar el tamaño
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        jsonWeightTextView.text = getString(R.string.error_code, responseCode) // Mostrar error
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    jsonWeightTextView.text = getString(R.string.error_message, e.message ?: "Desconocido") // Mostrar mensaje de error
                }
            }
        }
    }
}