package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class FullScreenImageAdapter(
    private val images: List<String>,
    private val onTap: (() -> Unit)? = null
) : RecyclerView.Adapter<FullScreenImageAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.photoView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val url = images.getOrNull(position)

        // Limpieza previa (IMPORTANTE al reciclar)
        holder.photoView.setOnPhotoTapListener(null)
        holder.photoView.setImageDrawable(null)

        if (!url.isNullOrBlank()) {
            Glide.with(holder.photoView)
                .load(url)
                .fitCenter()
                .into(holder.photoView)
        }

        // Tap simple → cerrar fullscreen
        holder.photoView.setOnPhotoTapListener { _, _, _ ->
            onTap?.invoke()
        }
    }

    override fun getItemCount(): Int = images.size
}
