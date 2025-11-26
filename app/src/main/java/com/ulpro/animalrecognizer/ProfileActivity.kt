package com.ulpro.animalrecognizer

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import com.ulpro.animalrecognizer.databinding.ActivityProfileBinding
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var binding: ActivityProfileBinding
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private val items = mutableListOf<GalleryItem>() // Cambiado a GalleryItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        val userId = intent.getStringExtra("usuario_id")
        if (!userId.isNullOrEmpty() && userId.toIntOrNull() != null) {
            fetchUserData(userId.toInt())
            fetchAportes(page = 1, userId = userId.toInt(), limit = 15)
        }
        val profileImage = findViewById<ImageView>(R.id.profile_image)
        profileImage.setOnClickListener {
            val drawable = profileImage.drawable
            if (drawable != null) {
                val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val imageBytes = outputStream.toByteArray()
                val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)

                // Guarda la imagen en un archivo temporal
                val imagePath = saveImageToFile(this, base64Image)

                val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                    putExtra("imagePath", imagePath) // Pasa la ruta del archivo
                    putExtra("isSingleImage", true)
                }
                startActivity(intent)
            } else {
                showErrorDialog("Error", "No se pudo obtener la imagen del perfil.")
            }
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
                    runOnUiThread { updateUI(json) }
                }
            }
        })
    }

    private fun updateUI(json: JSONObject) {
        val userName = findViewById<TextView>(R.id.user_name)
        val userOccupation = findViewById<TextView>(R.id.user_occupation)
        val userBio = findViewById<TextView>(R.id.user_bio)
        val profileImage = findViewById<ImageView>(R.id.profile_image)

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

    private fun showErrorDialog(title: String, message: String) {
        SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
            .setTitleText(title)
            .setContentText(message)
            .show()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.gallery_grid)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        val adapter = GalleryAdapter(items) { item ->
            val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                putExtra("imageBase64", item.imageBase64)
                putExtra("description", item.descripcion)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val margin = 4 // Tamaño del margen en píxeles
                outRect.set(margin, margin, margin, margin)
            }
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
        fetchAportes(page = currentPage, userId = getUserId(), limit = 15)
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
                            GalleryItem( // Cambiado a GalleryItem
                                id = item.getInt("id"),
                                name = item.getString("name"),
                                imageBase64 = item.getString("imageBase64"),
                                descripcion = item.getString("descripcion")
                            )
                        )
                    }

                    runOnUiThread {
                        val recyclerView = findViewById<RecyclerView>(R.id.gallery_grid)
                        recyclerView.adapter?.notifyDataSetChanged()
                    }
                    isLoading = false
                }
            }
        })
    }

    private fun getUserId(): Int {
        val sharedPreferences = getSharedPreferences("userSession", MODE_PRIVATE)
        return sharedPreferences.getString("usuario_id", null)?.toInt() ?: 0
    }
}