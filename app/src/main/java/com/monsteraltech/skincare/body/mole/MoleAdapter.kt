package com.monsteraltech.skincare.body.mole
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
class MoleAdapter(
    moleList: List<Mole>,
    private val onClick: (Mole) -> Unit
) : RecyclerView.Adapter<MoleAdapter.MoleViewHolder>() {
    private var filteredMoleList: List<Mole> = moleList.toList()
    class MoleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = itemView.findViewById(R.id.moleTitle)
        val descriptionTextView: TextView = itemView.findViewById(R.id.moleDescription)
        val imageView: ImageView = itemView.findViewById(R.id.moleImage)
        val analysisCountText: TextView = itemView.findViewById(R.id.analysisCountText)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoleViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mole, parent, false)
        return MoleViewHolder(itemView)
    }
    override fun onBindViewHolder(holder: MoleViewHolder, position: Int) {
        val mole = filteredMoleList[position]
        Glide.with(holder.imageView).clear(holder.imageView)
        holder.titleTextView.text = mole.title
        if (mole.description.isNotEmpty()) {
            holder.descriptionTextView.text = mole.description
        } else {
            holder.descriptionTextView.text = "Sin descripción"
        }
        if (mole.analysisCount > 0) {
            holder.analysisCountText.visibility = View.VISIBLE
            holder.analysisCountText.text = when (mole.analysisCount) {
                1 -> "1 análisis realizado"
                else -> "${mole.analysisCount} análisis realizados"
            }
        } else {
            holder.analysisCountText.visibility = View.GONE
        }
        if (mole.imageUrl.isNotEmpty()) {
            if (mole.imageUrl.startsWith("http")) {
                Glide.with(holder.imageView.context)
                    .load(mole.imageUrl)
                    .placeholder(R.drawable.cat)
                    .error(R.drawable.cat)
                    .centerCrop()
                    .into(holder.imageView)
            } else {
                val imageSource = ImageLoadingUtil.getImageSource(holder.imageView.context, mole.imageUrl)
                Glide.with(holder.imageView.context)
                    .load(imageSource)
                    .placeholder(R.drawable.cat)
                    .error(R.drawable.cat)
                    .centerCrop()
                    .into(holder.imageView)
            }
        } else if (mole.imageList.isNotEmpty()) {
            holder.imageView.setImageResource(mole.imageList[0])
        } else {
            holder.imageView.setImageResource(R.drawable.cat)
        }
        holder.itemView.setOnClickListener { onClick(mole) }
    }
    override fun getItemCount() = filteredMoleList.size
    fun submitList(items: List<Mole>) {
        val previous = filteredMoleList
        filteredMoleList = items.toList()
        DiffUtil.calculateDiff(MoleDiffCallback(previous, filteredMoleList)).dispatchUpdatesTo(this)
    }
}
