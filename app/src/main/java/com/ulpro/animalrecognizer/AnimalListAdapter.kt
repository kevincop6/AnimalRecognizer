package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AnimalListAdapter(private val animals: MutableList<Animal>) : RecyclerView.Adapter<AnimalListAdapter.AnimalViewHolder>(), Filterable {

    private var animalsFiltered: MutableList<Animal> = animals.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_animal, parent, false)
        return AnimalViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = animalsFiltered[position]
        holder.animalName.text = animal.name
        Glide.with(holder.itemView.context)
            .load(animal.imageBase64)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.animalImage)

        holder.animalImage.setOnClickListener {
            Toast.makeText(holder.itemView.context, "Animal ID: ${animal.id}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int {
        return animalsFiltered.size
    }

    fun addAnimals(newAnimals: List<Animal>) {
        val startPosition = animals.size
        animals.addAll(newAnimals)
        animalsFiltered = animals.toMutableList()
        notifyItemRangeInserted(startPosition, newAnimals.size)
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charString = constraint?.toString() ?: ""
                animalsFiltered = if (charString.isEmpty()) {
                    animals
                } else {
                    val filteredList = animals.filter {
                        it.name.contains(charString, true)
                    }
                    filteredList.toMutableList()
                }
                val filterResults = FilterResults()
                filterResults.values = animalsFiltered
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                val filteredList = results?.values as? MutableList<Animal>
                if (filteredList != null) {
                    animalsFiltered = filteredList
                    notifyDataSetChanged()
                }
            }
        }
    }

    class AnimalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val animalName: TextView = itemView.findViewById(R.id.animalName)
        val animalImage: ImageView = itemView.findViewById(R.id.animalImage)
    }
}