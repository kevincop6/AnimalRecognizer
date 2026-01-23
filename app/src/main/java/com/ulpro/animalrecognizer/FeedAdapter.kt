package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide

class FeedAdapter(
    private val items: MutableList<FeedPost>
) : RecyclerView.Adapter<FeedAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgUser: ImageView = v.findViewById(R.id.imgUser)
        val tvUser: TextView = v.findViewById(R.id.tvUser)
        val pagerImages: ViewPager2 = v.findViewById(R.id.pagerImages)
        val tvImageCounter: TextView = v.findViewById(R.id.tvImageCounter)
        val tvLikes: TextView = v.findViewById(R.id.tvLikes)
        val tvDescription: TextView = v.findViewById(R.id.tvDescription)
        val tvComments: TextView = v.findViewById(R.id.tvComments)

        var pageCallback: ViewPager2.OnPageChangeCallback? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed_post, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val post = items[pos]

        // ---------- USER ----------
        h.tvUser.text = post.usuario.nombre_usuario
        Glide.with(h.itemView)
            .load(post.usuario.foto)
            .placeholder(R.drawable.ic_user_placeholder)
            .into(h.imgUser)

        // ---------- TEXT ----------
        val likesText = NumberFormatter.compact(post.likes.total)
        val commentsText = NumberFormatter.compact(post.comentarios.total)

        h.tvLikes.text = "$likesText Me gusta"
        h.tvComments.text = "Ver $commentsText comentarios"

        // ---------- IMAGES ----------
        val totalImages = post.media.size
        h.pagerImages.adapter = FeedImagePagerAdapter(post.media)

        // Limpiar callback previo (MUY IMPORTANTE)
        h.pageCallback?.let {
            h.pagerImages.unregisterOnPageChangeCallback(it)
        }

        if (totalImages > 1) {
            h.tvImageCounter.visibility = View.VISIBLE
            h.tvImageCounter.text = "1/$totalImages"

            val callback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    h.tvImageCounter.text = "${position + 1}/$totalImages"
                }
            }

            h.pagerImages.registerOnPageChangeCallback(callback)
            h.pageCallback = callback

        } else {
            h.tvImageCounter.visibility = View.GONE
            h.pageCallback = null
        }
    }

    override fun getItemCount() = items.size

    fun addPosts(newPosts: List<FeedPost>) {
        val start = items.size
        items.addAll(newPosts)
        notifyItemRangeInserted(start, newPosts.size)
    }
}
