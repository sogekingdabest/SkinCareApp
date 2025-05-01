package es.monsteraltech.skincare_tfm.body.mole.ViewPageImages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import java.io.File

class UrlImagePagerAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<UrlImagePagerAdapter.ImageViewHolder>() {

    private val firebaseDataManager = FirebaseDataManager()

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

        if (imageUrl.startsWith("http")) {
            // Es una URL remota (caso legacy)
            Glide.with(holder.imageView.context)
                .load(imageUrl)
                .placeholder(R.drawable.cat)
                .error(R.drawable.cat)
                .into(holder.imageView)
        } else {
            // Es una ruta local
            val fullPath = firebaseDataManager.getFullImagePath(
                holder.imageView.context,
                imageUrl
            )
            Glide.with(holder.imageView.context)
                .load(File(fullPath))
                .placeholder(R.drawable.cat)
                .error(R.drawable.cat)
                .into(holder.imageView)
        }
    }

    override fun getItemCount(): Int {
        return imageUrls.size
    }
}