package com.ulpro.animalrecognizer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class CachedImagesAdapter(private var images: MutableList<Bitmap>) : RecyclerView.Adapter<CachedImagesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cached_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageBitmap(images[position])
    }

    override fun getItemCount(): Int = images.size

    fun updateImages(newImages: List<Bitmap>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun addImage(image: Bitmap) {
        images.add(image)
        notifyItemInserted(images.size - 1)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.cachedImageView)
    }
}
