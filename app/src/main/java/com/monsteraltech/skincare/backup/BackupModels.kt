package com.monsteraltech.skincare.backup

import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.body.mole.data.local.AnalysisEntity
import com.monsteraltech.skincare.body.mole.data.local.MoleEntity

internal data class BackupPayload(
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val moles: List<MoleEntity>,
    val analyses: List<AnalysisEntity>,
    val imageEntries: Map<String, String>,
    val accountSettings: AccountSettings
)

data class BackupSummary(
    val moleCount: Int,
    val analysisCount: Int,
    val imageCount: Int
)
