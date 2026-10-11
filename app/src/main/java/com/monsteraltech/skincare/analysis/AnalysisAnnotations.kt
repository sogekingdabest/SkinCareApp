package com.monsteraltech.skincare.analysis

import com.monsteraltech.skincare.body.mole.model.UserAbcdeInput

data class AnalysisAnnotations(
    val title: String,
    val description: String,
    val bodyPart: String,
    val bodyPartColorCode: String?,
    val userInput: UserAbcdeInput?
)
