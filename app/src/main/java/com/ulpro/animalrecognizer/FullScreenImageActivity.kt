package com.ulpro.animalrecognizer

import android.os.Bundle
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ulpro.animalrecognizer.GalleryAdapter.ImageDataStore
import kotlin.compareTo
import kotlin.dec
import kotlin.inc
import kotlin.math.abs
import kotlin.text.compareTo

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var imageList: List<GalleryItem>
    private var currentPosition: Int = 0
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)
        val isSingleImage = intent.getBooleanExtra("isSingleImage", false)
        val imageView = findViewById<ImageView>(R.id.fullScreenImageView)
        val descriptionView = findViewById<TextView>(R.id.image_description)
        val prevButton = findViewById<ImageButton>(R.id.prev_button)
        val nextButton = findViewById<ImageButton>(R.id.next_button)
        val closeButton = findViewById<ImageButton>(R.id.close_button)
        closeButton.setOnClickListener {
            finish() // Cierra solo la actividad actual
        }

        if (isSingleImage) {
            // Carga la imagen desde la ruta del archivo
            val imagePath = intent.getStringExtra("imagePath")
            if (imagePath != null) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                imageView.setImageBitmap(bitmap)
            }
            // Oculta botones y descripción
            prevButton.visibility = View.GONE
            nextButton.visibility = View.GONE
            descriptionView.visibility = View.GONE
        } else {
            // Recupera la lista desde ImageDataStore
            imageList = ImageDataStore.imageList
            currentPosition = intent.getIntExtra("currentPosition", 0)
            updateImage(imageView, descriptionView)

            // Configura botones de navegación
            prevButton.setOnClickListener {
                if (currentPosition > 0) {
                    currentPosition--
                    updateImage(imageView, descriptionView)
                }
            }

            nextButton.setOnClickListener {
                if (currentPosition < imageList.size - 1) {
                    currentPosition++
                    updateImage(imageView, descriptionView)
                }
            }
        }

        // Configura el detector de gestos
        gestureDetector = GestureDetector(this, object : GestureDetector.OnGestureListener {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onShowPress(e: MotionEvent) {}

            override fun onSingleTapUp(e: MotionEvent): Boolean = false

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = false

            override fun onLongPress(e: MotionEvent) {}

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false // Manejo de nulos para e1
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onSwipeRight() {
        if (!::imageList.isInitialized || currentPosition <= 0) return
        currentPosition--
        updateImage(findViewById(R.id.fullScreenImageView), findViewById(R.id.image_description))
    }

    private fun onSwipeLeft() {
        if (!::imageList.isInitialized || currentPosition >= imageList.size - 1) return
        currentPosition++
        updateImage(findViewById(R.id.fullScreenImageView), findViewById(R.id.image_description))
    }

    private fun updateImage(imageView: ImageView, descriptionView: TextView) {
        val currentItem = imageList[currentPosition]
        val imageBytes = Base64.decode(currentItem.imageBase64.split(",")[1], Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        imageView.setImageBitmap(bitmap)
        descriptionView.text = currentItem.descripcion
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }
}