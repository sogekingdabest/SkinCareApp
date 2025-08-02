package es.monsteraltech.skincare_tfm.body.mole.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.AnalysisData
import es.monsteraltech.skincare_tfm.body.mole.model.EvolutionComparison
import es.monsteraltech.skincare_tfm.body.mole.performance.LazyImageLoader
import es.monsteraltech.skincare_tfm.data.FirebaseDataManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AnalysisHistoryAdapter(
    private val context: Context,
    private var analysisList: List<AnalysisData>,
    private val onItemClick: (AnalysisData) -> Unit
) : RecyclerView.Adapter<AnalysisHistoryAdapter.ViewHolder>() {

    private val firebaseDataManager = FirebaseDataManager()
    private val lazyImageLoader = LazyImageLoader(context)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val analysisImageView: ImageView = view.findViewById(R.id.analysisImageView)
        val riskIndicator: View = view.findViewById(R.id.riskIndicator)
        val combinedScoreText: TextView = view.findViewById(R.id.combinedScoreText)
        val riskLevelText: TextView = view.findViewById(R.id.riskLevelText)
        val aiProbabilityText: TextView = view.findViewById(R.id.aiProbabilityText)
        val dateText: TextView = view.findViewById(R.id.analysisDateText)
        val recommendationText: TextView = view.findViewById(R.id.recommendationText)
        val abcdeTotalScore: TextView = view.findViewById(R.id.abcdeTotalScore)
        val viewDetailsButton: com.google.android.material.button.MaterialButton = view.findViewById(R.id.viewDetailsButton)
        
        // ABCDE Progress indicators
        val asymmetryProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.asymmetryProgress)
        val borderProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.borderProgress)
        val colorProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.colorProgress)
        val diameterProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.diameterProgress)
        val evolutionProgress: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.evolutionProgress)
        
        // ABCDE Score TextViews
        val asymmetryScore: TextView = view.findViewById(R.id.asymmetryScore)
        val borderScore: TextView = view.findViewById(R.id.borderScore)
        val colorScore: TextView = view.findViewById(R.id.colorScore)
        val diameterScore: TextView = view.findViewById(R.id.diameterScore)
        val evolutionScore: TextView = view.findViewById(R.id.evolutionScore)
        
        // ABCDE Layouts
        val asymmetryLayout: android.widget.LinearLayout = view.findViewById(R.id.asymmetryLayout)
        val borderLayout: android.widget.LinearLayout = view.findViewById(R.id.borderLayout)
        val colorLayout: android.widget.LinearLayout = view.findViewById(R.id.colorLayout)
        val diameterLayout: android.widget.LinearLayout = view.findViewById(R.id.diameterLayout)
        val evolutionLayout: android.widget.LinearLayout = view.findViewById(R.id.evolutionLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_history_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val analysis = analysisList[position]

        // Formatear la fecha en formato español DD/MM/YYYY
        val date = analysis.createdAt.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES"))
        holder.dateText.text = dateFormat.format(date)

        // Cargar imagen del análisis con lazy loading optimizado
        val recyclerView = holder.itemView.parent as? RecyclerView
        if (recyclerView != null) {
            lazyImageLoader.loadImageLazy(
                imageView = holder.analysisImageView,
                imageUrl = analysis.imageUrl,
                position = position,
                recyclerView = recyclerView,
                config = LazyImageLoader.Configs.forAnalysisHistory()
            )
        } else {
            // Fallback si no hay RecyclerView
            if (analysis.imageUrl.isNotEmpty()) {
                if (analysis.imageUrl.startsWith("http")) {
                    Glide.with(context)
                        .load(analysis.imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(holder.analysisImageView)
                } else {
                    val fullPath = firebaseDataManager.getFullImagePath(context, analysis.imageUrl)
                    Glide.with(context)
                        .load(File(fullPath))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(holder.analysisImageView)
                }
            }
        }

        // Mostrar score combinado
        val combinedScore = analysis.combinedScore
        if (combinedScore > 0f) {
            holder.combinedScoreText.text = String.format("%.1f%%", combinedScore * 100)
        } else {
            holder.combinedScoreText.text = "--"
        }

        // Mostrar probabilidad y confianza de IA
        if (analysis.aiProbability > 0f && analysis.aiConfidence > 0f) {
            holder.aiProbabilityText.text = "IA: ${String.format("%.1f%%", analysis.aiProbability * 100)} (Confianza: ${String.format("%.1f%%", analysis.aiConfidence * 100)})"
            holder.aiProbabilityText.visibility = View.VISIBLE
        } else {
            holder.aiProbabilityText.visibility = View.GONE
        }

        // Mostrar nivel de riesgo y configurar indicador
        displayRiskLevel(holder, analysis.riskLevel)

        // Mostrar puntuaciones ABCDE detalladas
        displayABCDEScoresDetailed(holder, analysis)

        // Mostrar recomendación
        if (analysis.recommendation.isNotEmpty()) {
            holder.recommendationText.text = analysis.recommendation
            holder.recommendationText.visibility = View.VISIBLE
        } else {
            holder.recommendationText.visibility = View.GONE
        }



        // Configurar botón de ver detalles
        holder.viewDetailsButton.setOnClickListener {
            onItemClick(analysis)
        }

        // Configurar clic en el ítem completo
        holder.itemView.setOnClickListener {
            onItemClick(analysis)
        }
    }

    private fun displayRiskLevel(holder: ViewHolder, riskLevel: String) {
        if (riskLevel.isNotEmpty()) {
            holder.riskLevelText.text = "Riesgo: $riskLevel"
            holder.riskIndicator.visibility = View.VISIBLE
            
            // Cambiar color según el nivel de riesgo
            val colorRes = when (riskLevel.uppercase()) {
                "LOW", "BAJO" -> android.R.color.holo_green_dark
                "MODERATE", "MODERADO" -> android.R.color.holo_orange_dark
                "HIGH", "ALTO" -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            val color = ContextCompat.getColor(context, colorRes)
            holder.riskLevelText.setTextColor(color)
            holder.riskIndicator.setBackgroundColor(color)
        } else {
            holder.riskLevelText.text = "Riesgo: --"
            holder.riskIndicator.visibility = View.GONE
        }
    }

    private fun displayABCDEScoresDetailed(holder: ViewHolder, analysis: AnalysisData) {
        val scores = analysis.abcdeScores
        
        // Solo mostrar si hay puntuaciones válidas
        if (scores.totalScore > 0f || scores.asymmetryScore > 0f || 
            scores.borderScore > 0f || scores.colorScore > 0f || scores.diameterScore > 0f) {
            
            // A - Asimetría
            if (scores.asymmetryScore >= 0f) {
                holder.asymmetryLayout.visibility = View.VISIBLE
                holder.asymmetryScore.text = "${String.format("%.1f", scores.asymmetryScore)}/2"
                holder.asymmetryProgress.progress = ((scores.asymmetryScore / 2f) * 100).toInt()
            } else {
                holder.asymmetryLayout.visibility = View.GONE
            }
            
            // B - Bordes
            if (scores.borderScore >= 0f) {
                holder.borderLayout.visibility = View.VISIBLE
                holder.borderScore.text = "${String.format("%.1f", scores.borderScore)}/8"
                holder.borderProgress.progress = ((scores.borderScore / 8f) * 100).toInt()
            } else {
                holder.borderLayout.visibility = View.GONE
            }
            
            // C - Color
            if (scores.colorScore >= 0f) {
                holder.colorLayout.visibility = View.VISIBLE
                holder.colorScore.text = "${String.format("%.1f", scores.colorScore)}/6"
                holder.colorProgress.progress = ((scores.colorScore / 6f) * 100).toInt()
            } else {
                holder.colorLayout.visibility = View.GONE
            }
            
            // D - Diámetro
            if (scores.diameterScore >= 0f) {
                holder.diameterLayout.visibility = View.VISIBLE
                holder.diameterScore.text = "${String.format("%.1f", scores.diameterScore)}/5"
                holder.diameterProgress.progress = ((scores.diameterScore / 5f) * 100).toInt()
            } else {
                holder.diameterLayout.visibility = View.GONE
            }
            
            // E - Evolución (opcional)
            scores.evolutionScore?.let { evolutionScoreValue ->
                if (evolutionScoreValue > 0f) {
                    holder.evolutionLayout.visibility = View.VISIBLE
                    holder.evolutionScore.text = "${String.format("%.1f", evolutionScoreValue)}/3"
                    holder.evolutionProgress.progress = ((evolutionScoreValue / 3f) * 100).toInt()
                } else {
                    holder.evolutionLayout.visibility = View.GONE
                }
            } ?: run {
                holder.evolutionLayout.visibility = View.GONE
            }
            
            // Score total ABCDE
            if (scores.totalScore > 0f) {
                holder.abcdeTotalScore.text = "Score ABCDE Total: ${String.format("%.1f", scores.totalScore)}"
            } else {
                holder.abcdeTotalScore.text = "Score ABCDE Total: --"
            }
            
        } else {
            // Ocultar todos los layouts si no hay datos
            holder.asymmetryLayout.visibility = View.GONE
            holder.borderLayout.visibility = View.GONE
            holder.colorLayout.visibility = View.GONE
            holder.diameterLayout.visibility = View.GONE
            holder.evolutionLayout.visibility = View.GONE
            holder.abcdeTotalScore.text = "Score ABCDE Total: --"
        }
    }

    override fun getItemCount() = analysisList.size

    fun updateData(newList: List<AnalysisData>) {
        analysisList = newList
        notifyDataSetChanged()
    }

    /**
     * Añade nuevos elementos al final de la lista (para paginación)
     */
    fun addData(newList: List<AnalysisData>) {
        val oldSize = analysisList.size
        analysisList = analysisList + newList
        notifyItemRangeInserted(oldSize, newList.size)
    }

    /**
     * Limpia la caché de imágenes cuando el adapter se destruye
     */
    fun onDestroy() {
        lazyImageLoader.cancelAllLoads()
    }
}