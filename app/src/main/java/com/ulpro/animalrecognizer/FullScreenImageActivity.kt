package com.ulpro.animalrecognizer

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import kotlin.math.abs

class FullScreenImageActivity : AppCompatActivity() {

    private var imageList: List<GalleryItem> = emptyList()
    private var currentPosition = 0
    private lateinit var gestureDetector: GestureDetector

    private lateinit var imageView: ImageView
    private lateinit var descriptionView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        imageView = findViewById(R.id.fullScreenImageView)
        descriptionView = findViewById(R.id.image_description)

        val prevButton = findViewById<ImageButton>(R.id.prev_button)
        val nextButton = findViewById<ImageButton>(R.id.next_button)
        val closeButton = findViewById<ImageButton>(R.id.close_button)

        closeButton.setOnClickListener { finish() }

        imageList = ImageDataStore.imageList
        if (imageList.isEmpty()) {
            finish()
            return
        }

        currentPosition = intent.getIntExtra("currentPosition", 0)
            .coerceIn(0, imageList.size - 1)

        showImage()

        prevButton.setOnClickListener {
            if (currentPosition > 0) {
                currentPosition--
                showImage()
            }
        }

        nextButton.setOnClickListener {
            if (currentPosition < imageList.size - 1) {
                currentPosition++
                showImage()
            }
        }

        setupGestures()
    }

    private fun showImage() {
        val item = imageList[currentPosition]

        Glide.with(this)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(imageView)

        descriptionView.text = item.descripcion
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    if (abs(diffX) > abs(diffY) && abs(diffX) > 100) {
                        if (diffX > 0 && currentPosition > 0) {
                            currentPosition--
                            showImage()
                        } else if (diffX < 0 && currentPosition < imageList.size - 1) {
                            currentPosition++
                            showImage()
                        }
                        return true
                    }
                    return false
                }
            })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }
}
