package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class UsersAdapterFragment(
    private val users: List<User>
) : RecyclerView.Adapter<UsersAdapterFragment.ViewHolder>() {

    private var onItemClick: ((String) -> Unit)? = null

    fun setOnItemClickListener(listener: (String) -> Unit) {
        onItemClick = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.userImageView)
        val nameText: TextView = view.findViewById(R.id.userNameTextView)
        val usernameText: TextView = view.findViewById(R.id.userUsernameTextView)
        val likesText: TextView = view.findViewById(R.id.userLikesTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.items_usuarios_fragment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        holder.nameText.text = user.name
        holder.usernameText.text = "@${user.username}"
        holder.likesText.text = user.likes.toString()

        Glide.with(holder.itemView)
            .load(user.imageUrl)
            .placeholder(R.drawable.ic_default_profile)
            .error(R.drawable.ic_default_profile)
            .circleCrop()
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(user.id)
        }
    }

    override fun getItemCount(): Int = users.size
}