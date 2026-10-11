package com.monsteraltech.skincare.body.mole.dialog
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.domain.model.MoleListEntry
import com.monsteraltech.skincare.body.mole.util.ImageLoadingUtil
import com.monsteraltech.skincare.databinding.ItemMoleSelectorBinding
import java.text.SimpleDateFormat
import java.util.Locale
class MoleSelectorAdapter(
    private val onMoleClick: (MoleListEntry) -> Unit
) : ListAdapter<MoleListEntry, MoleSelectorAdapter.MoleViewHolder>(MoleDiffCallback()) {
    private var searchQuery: String = ""
    fun updateSearchQuery(query: String) {
        searchQuery = query.lowercase()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoleViewHolder {
        val binding = ItemMoleSelectorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoleViewHolder(binding, onMoleClick)
    }
    override fun onBindViewHolder(holder: MoleViewHolder, position: Int) {
        holder.bind(getItem(position), searchQuery)
    }
    class MoleViewHolder(
        private val binding: ItemMoleSelectorBinding,
        private val onMoleClick: (MoleListEntry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        fun bind(mole: MoleListEntry, searchQuery: String = "") {
            binding.apply {
                moleTitle.text = highlightSearchTerm(mole.title, searchQuery)
                moleBodyPart.text = highlightSearchTerm(mole.bodyPart, searchQuery)
                moleDescription.text = if (mole.description.isNotEmpty()) {
                    highlightSearchTerm(mole.description, searchQuery)
                } else {
                    binding.root.context.getString(R.string.mole_no_description)
                }
                setupMetadata(mole)
                loadMoleImage(mole)
                root.setOnClickListener {
                    onMoleClick(mole)
                }
                root.contentDescription = "Lunar ${mole.title} en ${mole.bodyPart}, " +
                        "${mole.analysisCount} análisis, " +
                        "último análisis ${formatLastAnalysisDate(mole)}"
            }
        }
        private fun highlightSearchTerm(text: String, searchQuery: String): SpannableString {
            val spannableString = SpannableString(text)
            if (searchQuery.isNotEmpty()) {
                val searchTerms = searchQuery.split(" ").filter { it.isNotBlank() }
                searchTerms.forEach { term ->
                    val startIndex = text.lowercase().indexOf(term.lowercase())
                    if (startIndex >= 0) {
                        val endIndex = startIndex + term.length
                        spannableString.setSpan(
                            StyleSpan(Typeface.BOLD),
                            startIndex,
                            endIndex,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            return spannableString
        }
        private fun setupMetadata(mole: MoleListEntry) {
            binding.apply {
                analysisCountText.text = binding.root.context.getString(R.string.mole_analysis_count, mole.analysisCount)
                lastAnalysisText.text = formatLastAnalysisDate(mole)
                createdDateText.text = binding.root.context.getString(R.string.mole_created_date, dateFormat.format(java.util.Date(mole.createdAtEpochMillis)))
            }
        }
        private fun formatLastAnalysisDate(mole: MoleListEntry): String {
            return if (mole.lastAnalysisAtEpochMillis != null) {
                binding.root.context.getString(R.string.mole_last_analysis, dateFormat.format(java.util.Date(mole.lastAnalysisAtEpochMillis)))
            } else {
                binding.root.context.getString(R.string.mole_no_previous_analysis)
            }
        }
        private fun loadMoleImage(mole: MoleListEntry) {
            Glide.with(binding.moleImage).clear(binding.moleImage)
            if (mole.imageReference.isNotEmpty()) {
                Glide.with(binding.moleImage)
                    .load(if (mole.imageReference.startsWith("http")) {
                        mole.imageReference
                    } else {
                        ImageLoadingUtil.getImageSource(binding.root.context, mole.imageReference)
                    })
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(binding.moleImage)
            } else {
                binding.moleImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
    }
    private class MoleDiffCallback : DiffUtil.ItemCallback<MoleListEntry>() {
        override fun areItemsTheSame(oldItem: MoleListEntry, newItem: MoleListEntry): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: MoleListEntry, newItem: MoleListEntry): Boolean {
            return oldItem == newItem
        }
    }
}
