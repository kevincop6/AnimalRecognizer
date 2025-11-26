package com.ulpro.animalrecognizer

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecommendationAdapter(
    private val items: List<RecommendationItem>,
    private val onItemClick: (Int) -> Unit // Callback para manejar clics
) : RecyclerView.Adapter<RecommendationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
        val textViewName: TextView = view.findViewById(R.id.textViewName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textViewName.text = item.name

        // Decodificar imagen Base64
        val imageBytes = Base64.decode(item.imageBase64.split(",")[1], Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        holder.imageView.setImageBitmap(bitmap)

        // Configurar clic en el ítem
        holder.itemView.setOnClickListener {
            onItemClick(item.id) // Llamar al callback con el ID del ítem
        }
    }

    override fun getItemCount(): Int = items.size
}

data class RecommendationItem(val id: Int, val name: String, val imageBase64: String)