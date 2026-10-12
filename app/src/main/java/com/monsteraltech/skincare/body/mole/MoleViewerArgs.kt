package com.monsteraltech.skincare.body.mole

import android.content.Context
import android.content.Intent

/** Only identities cross screens. Titles, images and scores always come from Room. */
data class MoleViewerArgs(val moleId: String, val analysisId: String? = null) {
    fun toIntent(context: Context, destination: Class<*> = MoleViewerActivity::class.java): Intent =
        Intent(context, destination).putExtra(EXTRA_MOLE_ID, moleId).apply {
            analysisId?.let { putExtra(EXTRA_ANALYSIS_ID, it) }
        }

    companion object {
        const val EXTRA_MOLE_ID = "MOLE_ID"
        const val EXTRA_ANALYSIS_ID = "ANALYSIS_ID"
        fun fromIntent(intent: Intent) = MoleViewerArgs(
            intent.getStringExtra(EXTRA_MOLE_ID).orEmpty(),
            // Retain old links by identity; never trust their embedded snapshots.
            (intent.getStringExtra(EXTRA_ANALYSIS_ID)
                ?: intent.getStringExtra("HISTORICAL_ANALYSIS_ID"))?.takeIf(String::isNotBlank)
        )
    }
}
