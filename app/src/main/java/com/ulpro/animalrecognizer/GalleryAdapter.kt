package com.ulpro.animalrecognizer

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryAdapter(
    private val items: List<GalleryItem>,
    private val onItemClick: (GalleryItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gallery_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = items[position]

        // ----------------------------------
        // MARGEN ENTRE ITEMS (GRID)
        // ----------------------------------
        val spacing = dpToPx(holder.itemView, 4)

        val layoutParams =
            holder.itemView.layoutParams as ViewGroup.MarginLayoutParams

        layoutParams.setMargins(
            spacing,
            spacing,
            spacing,
            spacing
        )

        holder.itemView.layoutParams = layoutParams

        // ----------------------------------
        // CARGA DE IMAGEN
        // ----------------------------------
        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .centerCrop()
            .into(holder.imageView)

        // ----------------------------------
        // CLICK
        // ----------------------------------
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // ----------------------------------
        // ANIMACIÓN SUAVE
        // ----------------------------------
        holder.itemView.startAnimation(
            AnimationUtils.loadAnimation(
                holder.itemView.context,
                R.anim.gallery_item_enter
            )
        )
    }

    override fun getItemCount(): Int = items.size

    // --------------------------------------------------
    // UTIL: DP → PX
    // --------------------------------------------------
    private fun dpToPx(view: View, dp: Int): Int {
        return (dp * view.resources.displayMetrics.density).toInt()
    }
}
