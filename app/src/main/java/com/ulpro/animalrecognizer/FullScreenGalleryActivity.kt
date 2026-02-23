package com.ulpro.animalrecognizer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.ulpro.animalrecognizer.databinding.ActivityFullScreenGalleryBinding

class FullScreenGalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenGalleryBinding
    private lateinit var adapter: FullScreenImageAdapter
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFullScreenGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fullscreen real
        window.setDecorFitsSystemWindows(false)

        val images =
            intent.getStringArrayListExtra("images") ?: arrayListOf()

        currentIndex =
            intent.getIntExtra("startIndex", 0)

        adapter = FullScreenImageAdapter(
            images = images,
            onTap = {
                finishWithResult()
            }
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(currentIndex, false)

        // Guardar índice actual
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentIndex = position
                }
            }
        )
    }

    private fun finishWithResult() {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra("index", currentIndex)
        )
        finish()
    }

    override fun onBackPressed() {
        finishWithResult()
    }
}
