package com.monsteraltech.skincare.body.mole.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MoleListSearch {
    fun matches(entry: MoleListEntry, query: MoleListQuery): Boolean {
        val text = query.text.lowercase()
        if (text.isBlank()) return true
        if (!query.selector) return listOf(entry.title, entry.description, entry.analysisResult).any { text in it.lowercase() }
        val dates = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fields = listOf(entry.title, entry.description, entry.bodyPart, entry.analysisCount.toString(),
            dates.format(Date(entry.createdAtEpochMillis)), entry.lastAnalysisAtEpochMillis?.let { dates.format(Date(it)) }.orEmpty())
        return text.trim().split(Regex("\\s+")).any { term -> fields.any { term in it.lowercase() } }
    }
}
