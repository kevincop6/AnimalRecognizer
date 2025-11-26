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

class AdapterAnimalesFragment(
    private val animalList: MutableList<Animal>
) : RecyclerView.Adapter<AdapterAnimalesFragment.AnimalViewHolder>(), Filterable {

    private var filteredAnimalList: MutableList<Animal> = animalList.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_animal_fragment, parent, false)
        return AnimalViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = filteredAnimalList[position]
        holder.animalNameTextView.text = animal.name

        // Cargar imagen usando Glide
        Glide.with(holder.itemView.context)
            .load(animal.imageBase64)
            .placeholder(R.drawable.placeholder_image)
            .into(holder.animalImageView)

        // Manejar el clic en el elemento
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, details_animals::class.java).apply {
                putExtra("animalId", animal.id)
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = filteredAnimalList.size

    fun addAnimals(newAnimals: List<Animal>) {
        val uniqueAnimals = newAnimals.filter { newAnimal ->
            animalList.none { it.id == newAnimal.id }
        }
        animalList.addAll(uniqueAnimals)
        filteredAnimalList = animalList.toMutableList()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase() ?: ""
                val filtered = if (query.isEmpty()) {
                    animalList
                } else {
                    animalList.filter { it.name.lowercase().contains(query) }.toMutableList()
                }
                return FilterResults().apply { values = filtered }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredAnimalList = results?.values as? MutableList<Animal> ?: mutableListOf()
                notifyDataSetChanged()
            }
        }
    }

    fun clearAnimals() {
        animalList.clear()
        filteredAnimalList.clear()
        notifyDataSetChanged()
    }

    class AnimalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val animalNameTextView: TextView = itemView.findViewById(R.id.animalNameTextView)
        val animalImageView: ImageView = itemView.findViewById(R.id.animalImageView)
    }
}