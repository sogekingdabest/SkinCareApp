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
    private var evolutionComparisons: List<EvolutionComparison?> = emptyList(),
    private val onItemClick: (AnalysisData) -> Unit,
    private val onCompareClick: ((AnalysisData, AnalysisData) -> Unit)? = null
) : RecyclerView.Adapter<AnalysisHistoryAdapter.ViewHolder>() {

    private val firebaseDataManager = FirebaseDataManager()
    private val lazyImageLoader = LazyImageLoader(context)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val analysisImageView: ImageView = view.findViewById(R.id.analysisImageView)
        val dateText: TextView = view.findViewById(R.id.analysisDateText)
        val riskLevelText: TextView = view.findViewById(R.id.riskLevelText)
        val analysisDescription: TextView = view.findViewById(R.id.analysisDescription)
        val confidenceText: TextView = view.findViewById(R.id.confidenceText)
        val recommendationText: TextView = view.findViewById(R.id.recommendationText)
        val abcdeScoresText: TextView = view.findViewById(R.id.abcdeScoresText)
        val evolutionIndicator: TextView = view.findViewById(R.id.evolutionIndicator)
        val evolutionSummary: TextView = view.findViewById(R.id.evolutionSummary)
        val compareButton: com.google.android.material.button.MaterialButton = view.findViewById(R.id.compareButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val analysis = analysisList[position]
        val evolution = if (position < evolutionComparisons.size) evolutionComparisons[position] else null

        // Formatear la fecha
        val date = analysis.createdAt.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
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
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(holder.analysisImageView)
                } else {
                    val fullPath = firebaseDataManager.getFullImagePath(context, analysis.imageUrl)
                    Glide.with(context)
                        .load(File(fullPath))
                        .placeholder(R.drawable.ic_launcher_background)
                        .into(holder.analysisImageView)
                }
            }
        }

        // Configurar nivel de riesgo con color adecuado
        holder.riskLevelText.text = analysis.riskLevel
        when (analysis.riskLevel.uppercase()) {
            "LOW" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
            "MODERATE", "MEDIO" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
            }
            "HIGH" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            }
            else -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
        }

        // Mostrar descripción del análisis
        if (analysis.analysisResult.isNotEmpty()) {
            holder.analysisDescription.text = analysis.analysisResult
            holder.analysisDescription.visibility = View.VISIBLE
        } else {
            holder.analysisDescription.visibility = View.GONE
        }

        // Mostrar nivel de confianza
        val confidencePercent = (analysis.aiConfidence * 100).toInt()
        holder.confidenceText.text = "$confidencePercent%"

        // Mostrar recomendación
        if (analysis.recommendation.isNotEmpty()) {
            holder.recommendationText.text = "Recomendación: ${analysis.recommendation}"
            holder.recommendationText.visibility = View.VISIBLE
        } else {
            holder.recommendationText.visibility = View.GONE
        }

        // Mostrar puntuaciones ABCDE
        val abcdeText = buildString {
            append("ABCDE: ")
            append("A:${String.format("%.1f", analysis.abcdeScores.asymmetryScore)} ")
            append("B:${String.format("%.1f", analysis.abcdeScores.borderScore)} ")
            append("C:${String.format("%.1f", analysis.abcdeScores.colorScore)} ")
            append("D:${String.format("%.1f", analysis.abcdeScores.diameterScore)} ")
            if (analysis.abcdeScores.evolutionScore != null) {
                append("E:${String.format("%.1f", analysis.abcdeScores.evolutionScore)} ")
            }
            append("Total:${String.format("%.1f", analysis.abcdeScores.totalScore)}")
        }
        holder.abcdeScoresText.text = abcdeText

        // Configurar indicadores de evolución
        if (evolution != null) {
            holder.evolutionIndicator.visibility = View.VISIBLE
            holder.evolutionSummary.visibility = View.VISIBLE

            // Configurar indicador visual según la tendencia
            when (evolution.overallTrend) {
                EvolutionComparison.EvolutionTrend.IMPROVING -> {
                    holder.evolutionIndicator.text = "↗"
                    holder.evolutionIndicator.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                }
                EvolutionComparison.EvolutionTrend.WORSENING -> {
                    holder.evolutionIndicator.text = "↘"
                    holder.evolutionIndicator.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                }
                EvolutionComparison.EvolutionTrend.STABLE -> {
                    holder.evolutionIndicator.text = "→"
                    holder.evolutionIndicator.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
                EvolutionComparison.EvolutionTrend.MIXED -> {
                    holder.evolutionIndicator.text = "↕"
                    holder.evolutionIndicator.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                }
            }

            // Mostrar resumen de evolución
            holder.evolutionSummary.text = evolution.getEvolutionSummary()
        } else {
            holder.evolutionIndicator.visibility = View.GONE
            holder.evolutionSummary.visibility = View.GONE
        }

        // Configurar botón de comparación
        if (evolution != null && onCompareClick != null) {
            holder.compareButton.visibility = View.VISIBLE
            holder.compareButton.setOnClickListener {
                onCompareClick.invoke(analysis, evolution.previousAnalysis)
            }
        } else {
            holder.compareButton.visibility = View.GONE
        }

        // Configurar clic en el ítem
        holder.itemView.setOnClickListener {
            onItemClick(analysis)
        }
    }

    override fun getItemCount() = analysisList.size

    fun updateData(newList: List<AnalysisData>, newEvolutions: List<EvolutionComparison?> = emptyList()) {
        analysisList = newList
        evolutionComparisons = newEvolutions
        notifyDataSetChanged()
    }

    /**
     * Añade nuevos elementos al final de la lista (para paginación)
     */
    fun addData(newList: List<AnalysisData>, newEvolutions: List<EvolutionComparison?> = emptyList()) {
        val oldSize = analysisList.size
        analysisList = analysisList + newList
        evolutionComparisons = evolutionComparisons + newEvolutions
        notifyItemRangeInserted(oldSize, newList.size)
    }

    /**
     * Limpia la caché de imágenes cuando el adapter se destruye
     */
    fun onDestroy() {
        lazyImageLoader.cancelAllLoads()
    }
}