package com.ulpro.animalrecognizer

import android.content.Intent
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class GalleryAdapter(
    private val items: List<GalleryItem>,
    private val onItemClick: (GalleryItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val imageBytes = Base64.decode(item.imageBase64.split(",")[1], Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        holder.imageView.setImageBitmap(bitmap)

        holder.itemView.setOnClickListener {
            // Almacena la lista en ImageDataStore
            ImageDataStore.imageList = items

            val intent = Intent(holder.itemView.context, FullScreenImageActivity::class.java).apply {
                putExtra("currentPosition", position) // Solo pasa la posición actual
            }
            holder.itemView.context.startActivity(intent)
        }
    }
    object ImageDataStore {
        var imageList: List<GalleryItem> = emptyList()
    }
    override fun getItemCount(): Int = items.size
}