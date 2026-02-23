package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class RecommendationAdapter(
    private val items: List<RecommendationItem>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgRecomendacion)
        val name: TextView = view.findViewById(R.id.tvNombreRecomendacion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recomendacion_animal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = item.name

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .into(holder.image)

        holder.itemView.setOnClickListener {
            onItemClick(item.id)
        }
    }

    override fun getItemCount(): Int = items.size
}

/**
 * Modelo alineado EXACTAMENTE con:
 * {
 *   "id": 11,
 *   "nombre": "Leptodeira ornata",
 *   "imagen_principal": "https://..."
 * }
 */
data class RecommendationItem(
    val id: Int,
    val name: String,
    val imageUrl: String
)
