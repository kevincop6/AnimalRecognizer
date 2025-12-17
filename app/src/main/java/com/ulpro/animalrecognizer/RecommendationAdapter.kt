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

        Glide.with(holder.itemView)
            .load(item.imageUrl)
            .into(holder.imageView)

        holder.itemView.setOnClickListener { onItemClick(item.id) }
    }

    override fun getItemCount(): Int = items.size
}

data class RecommendationItem(
    val id: Int,
    val name: String,
    val imageUrl: String
)
