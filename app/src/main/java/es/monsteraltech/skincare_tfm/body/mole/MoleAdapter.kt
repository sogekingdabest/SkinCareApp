package es.monsteraltech.skincare_tfm.body.mole
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import java.io.File
class MoleAdapter(
    private val moleList: List<Mole>,
    private val onClick: (Mole) -> Unit,
    private val onFilterChanged: ((Boolean) -> Unit)? = null
) : RecyclerView.Adapter<MoleAdapter.MoleViewHolder>(), Filterable {
    private var filteredMoleList: List<Mole> = moleList
    private val firebaseDataManager = FirebaseDataManager()
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
                val fullPath = firebaseDataManager.getFullImagePath(
                    holder.imageView.context,
                    mole.imageUrl
                )
                Glide.with(holder.imageView.context)
                    .load(File(fullPath))
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
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence?): FilterResults {
                val query = charSequence.toString().lowercase()
                val filtered = if (query.isEmpty()) {
                    moleList
                } else {
                    moleList.filter {
                        it.title.lowercase().contains(query) ||
                                it.description.lowercase().contains(query) ||
                                it.analysisResult.lowercase().contains(query)
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = filtered
                return filterResults
            }
            override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults?) {
                val oldList = filteredMoleList
                filteredMoleList = filterResults?.values as List<Mole>
                val diffCallback = MoleDiffCallback(oldList, filteredMoleList)
                val diffResult = DiffUtil.calculateDiff(diffCallback)
                diffResult.dispatchUpdatesTo(this@MoleAdapter)
                onFilterChanged?.invoke(filteredMoleList.isEmpty())
            }
        }
    }
}