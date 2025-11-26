package com.ulpro.animalrecognizer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.ulpro.animalrecognizer.databinding.ActivityProfileBinding
import okhttp3.OkHttpClient
import android.graphics.Rect
import android.util.Base64
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ulpro.animalrecognizer.databinding.FragmentProfileBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.apply
import kotlin.text.clear

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


private val client = OkHttpClient()
private var currentPage = 1
private var totalPages = 1
private var isLoading = false
private val items = mutableListOf<GalleryItem>()
 // Cambiado a GalleryItem

class ProfileFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var binding: FragmentProfileBinding
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
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProfileBinding.bind(view)

        setupRecyclerView()
        val sharedPreferences = requireActivity().getSharedPreferences("userSession", MODE_PRIVATE)
        val userId = sharedPreferences.getString("usuario_id", null) ?: ""
        if (userId.isNotEmpty()) {
            fetchUserData(userId.toInt())
            fetchAportes(page = 1, userId = userId.toInt(), limit = 12)
        }
        val profileImage = view.findViewById<ImageView>(R.id.profile_image)
        profileImage.setOnClickListener {
            val drawable = profileImage.drawable
            if (drawable != null) {
                val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)

                // Guarda la imagen en un archivo temporal
                val imagePath = saveImageToFile(requireContext(), base64Image)

                val intent = Intent(requireContext(), FullScreenImageActivity::class.java).apply {
                    putExtra("imagePath", imagePath) // Pasa la ruta del archivo
                    putExtra("isSingleImage", true)
                }
                startActivity(intent)
            } else {
                showErrorDialog("Error", "No se pudo obtener la imagen del perfil.")
            }
        }
        binding.logOutButton.setOnClickListener {
            setupLogOutButton()
        }
    }
    private fun fetchUserData(userId: Int) {
        val url = "${ServerConfig.BASE_URL}get_user.php"
        val requestBody = FormBody.Builder()
            .add("id", userId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val json = JSONObject(responseBody)
                    requireActivity().runOnUiThread { updateUI(json) }
                }
            }
        })
    }

    private fun updateUI(json: JSONObject) {
        binding.apply {
            userName.text = json.optString("nombre", "Desconocido")
            userOccupation.text = json.optString("rol", "Sin rol")
            val fullText = json.optString("bio", "")
            userBio.text = "$fullText...más"
            userBio.setOnClickListener {
                if (userBio.maxLines == 2) {
                    userBio.maxLines = Int.MAX_VALUE
                    userBio.text = fullText
                } else {
                    userBio.maxLines = 2
                    userBio.text = "$fullText...más"
                }
            }
            val photoData = json.optString("photo", "").trim()

            if (photoData.startsWith("data:image/") && photoData.contains(",")) {
                val parts = photoData.split(",", limit = 2)
                if (parts.size == 2) {
                    try {
                        val base64String = parts[1]
                        val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            profileImage.setImageBitmap(bitmap)
                        } else {
                            showErrorDialog("Error", "El bitmap es nulo.")
                            profileImage.setImageResource(R.drawable.ic_default_profile)
                        }
                    } catch (e: Exception) {
                        showErrorDialog("Error al decodificar", e.message ?: "Error desconocido.")
                        profileImage.setImageResource(R.drawable.ic_default_profile)
                    }
                } else {
                    showErrorDialog("Formato incorrecto", "El formato de la URI de datos es incorrecto.")
                    profileImage.setImageResource(R.drawable.ic_default_profile)
                }
            } else {
                showErrorDialog("Foto no válida", "La foto está vacía o no tiene un formato Base64 válido.")
                profileImage.setImageResource(R.drawable.ic_default_profile)
            }
        }
    }
    private fun showErrorDialog(title: String, message: String) {
        SweetAlertDialog(requireContext(), SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .show()
    }

    private fun setupRecyclerView() {
        binding.galleryGrid.layoutManager = GridLayoutManager(requireContext(), 3)
        val adapter = GalleryAdapter(items) { item ->
            val intent = Intent(requireContext(), FullScreenImageActivity::class.java).apply {
                putExtra("imageBase64", item.imageBase64)
                putExtra("description", item.descripcion)
            }
            startActivity(intent)
        }
        binding.galleryGrid.adapter = adapter

        binding.galleryGrid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val margin = 4 // Tamaño del margen en píxeles
                outRect.set(margin, margin, margin, margin)
            }
        })

        binding.galleryGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && currentPage < totalPages) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadNextPage()
                    }
                }
            }
        })
    }

    private fun loadNextPage() {
        isLoading = true
        currentPage++
        fetchAportes(page = currentPage, userId = getUserId(), limit = 12)
    }
    private fun fetchAportes(page: Int, userId: Int, limit: Int) {
        val url = "${ServerConfig.BASE_URL}get_aportes.php"
        val requestBody = FormBody.Builder()
            .add("page", page.toString())
            .add("usuario_id", userId.toString())
            .add("limit", limit.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                isLoading = false
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val json = JSONObject(responseBody)
                    currentPage = json.getInt("current_page")
                    totalPages = json.getInt("total_pages")
                    val data = json.getJSONArray("data")

                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        items.add(
                            GalleryItem(
                                id = item.getInt("id"),
                                name = item.getString("name"),
                                imageBase64 = item.getString("imageBase64"),
                                descripcion = item.getString("descripcion")
                            )
                        )
                    }

                    requireActivity().runOnUiThread {
                        binding.galleryGrid.adapter?.notifyDataSetChanged()
                    }
                    isLoading = false
                }
            }
        })
    }

    private fun getUserId(): Int {
        val sharedPreferences = requireActivity().getSharedPreferences("userSession", MODE_PRIVATE)
        return sharedPreferences.getString("usuario_id", null)?.toInt() ?: 0
    }
    private fun setupLogOutButton() {
        val logOutButton = binding.logOutButton // Usa el binding para acceder al botón
        logOutButton.setOnClickListener {
            // Borrar datos de sesión
            requireActivity().getSharedPreferences("userSession", Context.MODE_PRIVATE).edit().apply {
                clear() // Limpia todos los datos almacenados
                apply() // Aplica los cambios
            }

            // Redirigir al usuario a la actividad de inicio de sesión
            val intent = Intent(requireActivity(), MainActivity::class.java)
            startActivity(intent)
            requireActivity().finish() // Cierra la actividad actual para evitar navegación hacia atrás
        }
    }
}