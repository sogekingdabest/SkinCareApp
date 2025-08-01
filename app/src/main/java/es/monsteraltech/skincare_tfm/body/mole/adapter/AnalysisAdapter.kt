package es.monsteraltech.skincare_tfm.body.mole.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.model.MoleAnalysisResult
import java.text.SimpleDateFormat
import java.util.Locale

class AnalysisAdapter(
    private var analysisList: List<MoleAnalysisResult>,
    private val onItemClick: (MoleAnalysisResult) -> Unit
) : RecyclerView.Adapter<AnalysisAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.analysisDatText)
        val riskLevelText: TextView = view.findViewById(R.id.riskLevelText)
        val analysisDescription: TextView = view.findViewById(R.id.analysisDescription)
        val confidenceText: TextView = view.findViewById(R.id.confidenceText)
        val recommendationText: TextView = view.findViewById(R.id.recommendationText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val analysis = analysisList[position]

        // Formatear la fecha
        val date = analysis.analysisDate.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.dateText.text = dateFormat.format(date)

        // Configurar nivel de riesgo con color adecuado
        holder.riskLevelText.text = analysis.riskLevel
        when (analysis.riskLevel) {
            "LOW" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(Color.parseColor("#4CAF50")) // Verde
            }
            "Medio" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(Color.parseColor("#FF9800")) // Naranja
            }
            "HIGH" -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(Color.parseColor("#F44336")) // Rojo
            }
            else -> {
                holder.riskLevelText.setBackgroundResource(R.drawable.rounded_risk_background)
                holder.riskLevelText.setTextColor(Color.WHITE)
                holder.riskLevelText.background.setTint(Color.parseColor("#757575")) // Gris
            }
        }

        // Mostrar descripción del análisis
        if (analysis.analysisText.isNotEmpty()) {
            holder.analysisDescription.text = analysis.analysisText
            holder.analysisDescription.visibility = View.VISIBLE
        } else {
            holder.analysisDescription.visibility = View.GONE
        }

        // Mostrar nivel de confianza
        val confidencePercent = (analysis.confidence * 100).toInt()
        holder.confidenceText.text = "$confidencePercent%"

        // Mostrar recomendación
        if (analysis.recommendedAction.isNotEmpty()) {
            holder.recommendationText.text = "Recomendación: ${analysis.recommendedAction}"
            holder.recommendationText.visibility = View.VISIBLE
        } else {
            holder.recommendationText.visibility = View.GONE
        }

        // Configurar clic en el ítem
        holder.itemView.setOnClickListener {
            onItemClick(analysis)
        }
    }

    override fun getItemCount() = analysisList.size

    fun updateData(newList: List<MoleAnalysisResult>) {
        analysisList = newList
        notifyDataSetChanged()
    }
}