package es.monsteraltech.skincare_tfm.body.mole.ViewPageImages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R

class UrlImagePagerAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<UrlImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.pagerImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.pager_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]

        Glide.with(holder.imageView.context)
            .load(imageUrl)
            .placeholder(R.drawable.cat)
            .error(R.drawable.cat)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int {
        return imageUrls.size
    }
}