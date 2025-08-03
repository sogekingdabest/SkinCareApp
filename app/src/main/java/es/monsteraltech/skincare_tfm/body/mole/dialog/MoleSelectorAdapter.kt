package es.monsteraltech.skincare_tfm.body.mole.dialog

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
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.MoleData
import es.monsteraltech.skincare_tfm.body.mole.util.ImageLoadingUtil
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import es.monsteraltech.skincare_tfm.databinding.ItemMoleSelectorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter para mostrar la lista de lunares en el selector modal.
 * Muestra miniaturas, nombres y metadatos de cada lunar con resaltado de búsqueda.
 */
class MoleSelectorAdapter(
    private val onMoleClick: (MoleData) -> Unit
) : ListAdapter<MoleData, MoleSelectorAdapter.MoleViewHolder>(MoleDiffCallback()) {

    private var searchQuery: String = ""
    private val firebaseDataManager = FirebaseDataManager()

    fun updateSearchQuery(query: String) {
        searchQuery = query.lowercase()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoleViewHolder {
        val binding = ItemMoleSelectorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MoleViewHolder(binding, onMoleClick, firebaseDataManager)
    }

    override fun onBindViewHolder(holder: MoleViewHolder, position: Int) {
        holder.bind(getItem(position), searchQuery)
    }

    class MoleViewHolder(
        private val binding: ItemMoleSelectorBinding,
        private val onMoleClick: (MoleData) -> Unit,
        private val firebaseDataManager: FirebaseDataManager
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun bind(mole: MoleData, searchQuery: String = "") {
            binding.apply {
                // Configurar información básica con resaltado de búsqueda
                moleTitle.text = highlightSearchTerm(mole.title, searchQuery)
                moleBodyPart.text = highlightSearchTerm(mole.bodyPart, searchQuery)
                moleDescription.text = if (mole.description.isNotEmpty()) {
                    highlightSearchTerm(mole.description, searchQuery)
                } else {
                    binding.root.context.getString(R.string.mole_no_description)
                }

                // Configurar metadatos
                setupMetadata(mole)

                // Cargar imagen miniatura
                loadMoleImage(mole)

                // Configurar click listener
                root.setOnClickListener {
                    onMoleClick(mole)
                }

                // Configurar accesibilidad
                root.contentDescription = "Lunar ${mole.title} en ${mole.bodyPart}, " +
                        "${mole.analysisCount} análisis, " +
                        "último análisis ${formatLastAnalysisDate(mole)}"
            }
        }

        /**
         * Resalta los términos de búsqueda en el texto
         */
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

        private fun setupMetadata(mole: MoleData) {
            binding.apply {
                // Contador de análisis
                analysisCountText.text = binding.root.context.getString(R.string.mole_analysis_count, mole.analysisCount)

                // Fecha del último análisis
                lastAnalysisText.text = formatLastAnalysisDate(mole)

                // Fecha de creación
                createdDateText.text = binding.root.context.getString(R.string.mole_created_date, dateFormat.format(mole.createdAt.toDate()))
            }
        }

        private fun formatLastAnalysisDate(mole: MoleData): String {
            return if (mole.lastAnalysisDate != null) {
                binding.root.context.getString(R.string.mole_last_analysis, dateFormat.format(mole.lastAnalysisDate.toDate()))
            } else {
                binding.root.context.getString(R.string.mole_no_previous_analysis)
            }
        }

        private fun loadMoleImage(mole: MoleData) {
            if (mole.imageUrl.isNotEmpty()) {
                // Usar ImageLoadingUtil para carga con fallbacks y reintentos
                ImageLoadingUtil.loadImageWithFallback(
                    context = binding.root.context,
                    imageView = binding.moleImage,
                    imageUrl = mole.imageUrl,
                    config = ImageLoadingUtil.Configs.thumbnail().copy(
                        placeholderRes = R.drawable.ic_image_placeholder,
                        errorPlaceholderRes = R.drawable.ic_image_error,
                        onLoadError = { exception ->
                            // Log silencioso del error, no mostrar al usuario
                            android.util.Log.w("MoleSelectorAdapter", "Error cargando imagen miniatura", exception)
                        }
                    ),
                    coroutineScope = CoroutineScope(Dispatchers.Main)
                )
                
                // Aplicar transformación circular después de la carga
                Glide.with(binding.root.context)
                    .load(if (mole.imageUrl.startsWith("http")) {
                        mole.imageUrl
                    } else {
                        File(firebaseDataManager.getFullImagePath(binding.root.context, mole.imageUrl))
                    })
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(binding.moleImage)
            } else {
                // Imagen por defecto si no hay URL
                binding.moleImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
    }

    private class MoleDiffCallback : DiffUtil.ItemCallback<MoleData>() {
        override fun areItemsTheSame(oldItem: MoleData, newItem: MoleData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MoleData, newItem: MoleData): Boolean {
            return oldItem == newItem
        }
    }
}