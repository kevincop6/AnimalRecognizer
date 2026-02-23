package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CategoriasAdapter(
    private val categorias: MutableList<Categoria>,
    private val onClick: (Categoria) -> Unit
) : RecyclerView.Adapter<CategoriasAdapter.CategoriaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria_card, parent, false)
        return CategoriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        val categoria = categorias[position]

        holder.nombre.text = categoria.nombre

        Glide.with(holder.itemView.context)
            .load(categoria.imagen)
            .centerCrop()
            .into(holder.imagen)

        holder.itemView.setOnClickListener {
            onClick(categoria)
        }
    }

    override fun getItemCount(): Int = categorias.size

    fun setData(nuevasCategorias: List<Categoria>) {
        categorias.clear()
        categorias.addAll(nuevasCategorias)
        notifyDataSetChanged()
    }

    class CategoriaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imagen: ImageView = itemView.findViewById(R.id.imgCategoria)
        val nombre: TextView = itemView.findViewById(R.id.tvNombreCategoria)
    }
}
