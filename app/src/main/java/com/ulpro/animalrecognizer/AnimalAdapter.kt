package com.ulpro.animalrecognizer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AnimalAdapter(private val animals: MutableList<Animal>) :
    RecyclerView.Adapter<AnimalAdapter.AnimalViewHolder>(), Filterable {

    private var animalsFiltered: MutableList<Animal> = animals.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_popular_animal, parent, false)
        return AnimalViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = animalsFiltered[position]

        holder.animalName.text = animal.nombre

        val imageUrl = animal.imagenUrl
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(holder.animalImage)
        } else {
            holder.animalImage.setImageResource(R.drawable.placeholder_image)
        }

        holder.animalImage.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, details_animals::class.java).apply {
                putExtra("animalId", animal.id)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = animalsFiltered.size

    fun addAnimals(newAnimals: List<Animal>) {
        val uniqueAnimals = newAnimals.filter { newAnimal ->
            animals.none { it.id == newAnimal.id }
        }
        val startPosition = animals.size
        animals.addAll(uniqueAnimals)
        animalsFiltered = animals.toMutableList()
        notifyItemRangeInserted(startPosition, uniqueAnimals.size)
    }

    fun clear() {
        animals.clear()
        animalsFiltered.clear()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val q = constraint?.toString()?.trim().orEmpty()
                animalsFiltered = if (q.isEmpty()) {
                    animals.toMutableList()
                } else {
                    animals.filter { it.nombre.contains(q, ignoreCase = true) }.toMutableList()
                }
                return FilterResults().apply { values = animalsFiltered }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                animalsFiltered = (results?.values as? MutableList<Animal>) ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

    class AnimalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val animalName: TextView = itemView.findViewById(R.id.animalName)
        val animalImage: ImageView = itemView.findViewById(R.id.animalImage)
    }
}
