package com.monsteraltech.skincare.analysis

import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput
import com.monsteraltech.skincare.body.domain.BodyRegionId

data class AnalysisAnnotations(
    val title: String,
    val description: String,
    val bodyPart: String,
    val bodyPartColorCode: String?,
    val userInput: UserAbcdeInput?,
    val bodyRegionId: BodyRegionId? = null
)
