package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryThumbAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<GalleryThumbAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()
    private var selectedPosition = 0

    // =========================================================
    // VIEW HOLDER
    // =========================================================
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        val overlaySelected: View? = itemView.findViewById(R.id.overlaySelected)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos)
                }
            }
        }
    }

    // =========================================================
    // ADAPTER OVERRIDES
    // =========================================================
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_thumb, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val url = items[position]

        Glide.with(holder.itemView)
            .load(url)
            .centerCrop()
            .into(holder.imgThumb)

        val isSelected = position == selectedPosition

        // Overlay visual (si existe)
        holder.overlaySelected?.visibility =
            if (isSelected) View.VISIBLE else View.GONE

        // Fallback visual
        holder.itemView.alpha = if (isSelected) 1f else 0.6f
    }

    override fun getItemCount(): Int = items.size

    // =========================================================
    // API PÚBLICA (IMPORTANTE)
    // =========================================================

    /** Lista completa de imágenes (para fullscreen) */
    fun getImages(): ArrayList<String> =
        ArrayList(items)

    /** Posición actualmente seleccionada */
    fun getSelectedPosition(): Int = selectedPosition

    fun submitList(list: List<String>) {
        items.clear()
        items.addAll(list)
        selectedPosition = 0
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        if (position == selectedPosition) return
        val old = selectedPosition
        selectedPosition = position
        notifyItemChanged(old)
        notifyItemChanged(selectedPosition)
    }
}
