package es.monsteraltech.skincare_tfm.body.mole

data class Mole(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val analysisResult: String = "",
    val analysisCount: Int = 0,
    val imageList: List<Int> = emptyList()
)