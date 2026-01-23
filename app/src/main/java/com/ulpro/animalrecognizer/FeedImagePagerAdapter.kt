package com.ulpro.animalrecognizer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class FeedImagePagerAdapter(
    private val images: List<FeedMedia>
) : RecyclerView.Adapter<FeedImagePagerAdapter.VH>() {

    class VH(val img: PhotoView) : RecyclerView.ViewHolder(img)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val img = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feed_image, parent, false) as PhotoView
        return VH(img)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        Glide.with(holder.img)
            .load(images[position].url_archivo)
            .into(holder.img)
    }

    override fun getItemCount() = images.size
}
