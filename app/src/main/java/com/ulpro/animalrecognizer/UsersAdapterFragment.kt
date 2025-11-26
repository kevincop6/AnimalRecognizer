package com.ulpro.animalrecognizer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class User(val id: String, val name: String, val username: String, val imageBitmap: Bitmap?)

class UsersAdapterFragment(
    private val userList: List<User>
) : RecyclerView.Adapter<UsersAdapterFragment.UserViewHolder>() {

    private var onItemClickListener: ((String) -> Unit)? = null

    fun setOnItemClickListener(listener: (String) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.items_usuarios_fragment, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user)
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(user.id)
        }
    }

    override fun getItemCount(): Int = userList.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userNameTextView: TextView = itemView.findViewById(R.id.userNameTextView)
        private val userUsernameTextView: TextView = itemView.findViewById(R.id.userUsernameTextView)
        private val userImageView: ImageView = itemView.findViewById(R.id.userImageView)

        fun bind(user: User) {
            userNameTextView.text = user.name
            userUsernameTextView.text = user.username
            user.imageBitmap?.let {
                userImageView.setImageBitmap(it)
            }
        }
    }
}