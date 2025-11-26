package com.ulpro.animalrecognizer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlin.collections.addAll

class AnimalAdapter(private val animals: MutableList<Animal>) : RecyclerView.Adapter<AnimalAdapter.AnimalViewHolder>(), Filterable {

    private var animalsFiltered: MutableList<Animal> = animals.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_popular_animal, parent, false)
        return AnimalViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = animalsFiltered[position]
        holder.animalName.text = animal.name
        val bitmap = decodeBase64ToBitmap(animal.imageBase64)
        if (bitmap != null) {
            holder.animalImage.setImageBitmap(bitmap)
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

    override fun getItemCount(): Int {
        return animalsFiltered.size
    }

    fun addAnimals(newAnimals: List<Animal>) {
        val uniqueAnimals = newAnimals.filter { newAnimal ->
            animals.none { it.id == newAnimal.id }
        }
        val startPosition = animals.size
        animals.addAll(uniqueAnimals)
        animalsFiltered = animals.toMutableList()
        notifyItemRangeInserted(startPosition, uniqueAnimals.size)
    }

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str.substring(base64Str.indexOf(",") + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        }
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
    fun clearAnimals() {
        animals.clear()
        notifyDataSetChanged() // Notifica al adaptador que los datos han cambiado
    }
}