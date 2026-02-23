package com.ulpro.animalrecognizer

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageAdapter: AvistamientoImagePagerAdapter

    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvLikes: TextView
    private lateinit var tvViews: TextView
    private lateinit var tvComments: TextView
    private lateinit var tvAnimal: TextView
    private lateinit var icLike: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // ---------------- Views ----------------
        viewPager = findViewById(R.id.imagePager)
        tvTitle = findViewById(R.id.tv_post_title)
        tvDate = findViewById(R.id.tv_post_date)
        tvDescription = findViewById(R.id.tv_post_description)
        tvLikes = findViewById(R.id.tv_likes)
        tvViews = findViewById(R.id.tv_views)
        tvComments = findViewById(R.id.tv_comments)
        tvAnimal = findViewById(R.id.tv_animal)
        icLike = findViewById(R.id.ic_like)

        val btnPrev = findViewById<ImageButton>(R.id.btn_prev)
        val btnNext = findViewById<ImageButton>(R.id.btn_next)
        val btnClose = findViewById<ImageButton>(R.id.btn_close)

        btnClose.setOnClickListener { finish() }

        val avistamientoId = intent.getIntExtra("avistamiento_id", -1)
        if (avistamientoId <= 0) {
            finish()
            return
        }

        fetchAvistamiento(avistamientoId)

        btnPrev.setOnClickListener {
            if (::imageAdapter.isInitialized && viewPager.currentItem > 0) {
                viewPager.currentItem--
            }
        }

        btnNext.setOnClickListener {
            if (::imageAdapter.isInitialized &&
                viewPager.currentItem < imageAdapter.itemCount - 1
            ) {
                viewPager.currentItem++
            }
        }
    }

    // --------------------------------------------------
    // 🌐 API: avistamientos/detalle.php
    // --------------------------------------------------
    private fun fetchAvistamiento(avistamientoId: Int) {
        CoroutineScope(Dispatchers.IO).launch {

            val token = TokenStore.getToken(this@FullScreenImageActivity) ?: return@launch

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val body = FormBody.Builder()
                .add("token", token)
                .add("avistamiento_id", avistamientoId.toString())
                .build()

            val request = Request.Builder()
                .url(
                    ServerConfig.BASE_URL.trimEnd('/') +
                            "/api/avistamientos/detalle.php"
                )
                .post(body)
                .build()

            try {
                val response = client.newCall(request).execute()
                val jsonStr = response.body?.string() ?: return@launch
                if (!response.isSuccessful) return@launch

                val root = JSONObject(jsonStr)
                val av = root.getJSONObject("avistamiento")
                val metrics = root.getJSONObject("metricas")

                // --------- Imágenes ----------
                val imageUrls = mutableListOf<String>()
                val imgs = av.getJSONArray("imagenes")
                for (i in 0 until imgs.length()) {
                    imageUrls.add(imgs.getString(i))
                }

                withContext(Dispatchers.Main) {

                    // --------- Texto ----------
                    tvTitle.text = av.optString("titulo", "")
                    tvDescription.text = av.optString("descripcion", "")
                    tvDate.text = formatDate(av.optString("fecha", ""))

                    // --------- Animal ----------
                    val animalObj = av.getJSONObject("animal")
                    tvAnimal.text = animalObj.optString("nombre_comun", "")

                    // --------- Métricas ----------
                    tvLikes.text = metrics.getInt("likes").toString()
                    tvViews.text = metrics.getInt("vistas").toString()
                    tvComments.text = metrics.getInt("comentarios").toString()

                    val dioLike = metrics.getBoolean("usuario_dio_like")
                    icLike.setColorFilter(
                        getColor(
                            if (dioLike) R.color.primary_dark
                            else R.color.text_secondary
                        )
                    )

                    // --------- ViewPager ----------
                    imageAdapter = AvistamientoImagePagerAdapter(imageUrls)
                    viewPager.adapter = imageAdapter
                }

            } catch (_: Exception) {
            }
        }
    }

    // --------------------------------------------------
    // 🗓 Fecha legible
    // --------------------------------------------------
    private fun formatDate(raw: String): String {
        // Esperado: yyyy-MM-dd HH:mm:ss
        return try {
            val parts = raw.split(" ")
            val date = parts[0].split("-")
            "${date[2]}/${date[1]}/${date[0]}"
        } catch (_: Exception) {
            raw
        }
    }
}
