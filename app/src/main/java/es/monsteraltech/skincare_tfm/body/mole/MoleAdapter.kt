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

class MoleAdapter(private val moleList: List<Mole>, private val onClick: (Mole) -> Unit) :
    RecyclerView.Adapter<MoleAdapter.MoleViewHolder>(), Filterable {

    private var filteredMoleList: List<Mole> = moleList

    class MoleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = itemView.findViewById(R.id.moleTitle)
        val descriptionTextView: TextView = itemView.findViewById(R.id.moleDescription)
        val imageView: ImageView = itemView.findViewById(R.id.moleImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoleViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mole, parent, false)
        return MoleViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MoleViewHolder, position: Int) {
        val mole = filteredMoleList[position]
        holder.titleTextView.text = mole.title
        holder.descriptionTextView.text = mole.description

        // Cargar imagen desde URL con Glide si tenemos URL
        if (mole.imageUrl.isNotEmpty()) {
            Glide.with(holder.imageView.context)
                .load(mole.imageUrl)
                .placeholder(R.drawable.cat) // Imagen de placeholder mientras carga
                .error(R.drawable.cat) // Imagen si hay error
                .centerCrop()
                .into(holder.imageView)
        } else if (mole.imageList.isNotEmpty()) {
            // Soporte para el método anterior (recursos locales)
            holder.imageView.setImageResource(mole.imageList[0])
        } else {
            // Si no hay imagen disponible
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

                // Usa DiffUtil para calcular los cambios
                val diffCallback = MoleDiffCallback(oldList, filteredMoleList)
                val diffResult = DiffUtil.calculateDiff(diffCallback)

                // Notifica los cambios al adaptador y anima las transiciones
                diffResult.dispatchUpdatesTo(this@MoleAdapter)
            }
        }
    }
}