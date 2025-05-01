package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.ImagePagerAdapter
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.UrlImagePagerAdapter
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.ZoomOutPageTransformer

class MoleDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mole_detail)

        // Obtener los datos del intent
        val title = intent.getStringExtra("LUNAR_TITLE") ?: ""
        val description = intent.getStringExtra("LUNAR_DESCRIPTION") ?: ""
        val analysisResult = intent.getStringExtra("LUNAR_ANALYSIS_RESULT") ?: ""
        val imageUrl = intent.getStringExtra("LUNAR_IMAGE_URL")
        val imageList = intent.getIntegerArrayListExtra("LUNAR_IMAGE_LIST")

        // Combinar la descripción con el resultado del análisis
        val fullDescription = if (analysisResult.isNotEmpty()) {
            "$description\n\nResultado del análisis:\n$analysisResult"
        } else {
            description
        }

        // Vincular los datos con las vistas
        val titleTextView: TextView = findViewById(R.id.detailTitle)
        val descriptionTextView: TextView = findViewById(R.id.detailDescription)
        val viewPager: ViewPager2 = findViewById(R.id.detailImagePager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)

        titleTextView.text = title
        descriptionTextView.text = fullDescription

        // Configurar ViewPager con las imágenes
        if (imageUrl != null && imageUrl.isNotEmpty()) {
            // Si tenemos una URL o ruta local, usar el adaptador para URL
            val urlList = listOf(imageUrl)
            val urlPagerAdapter = UrlImagePagerAdapter(urlList)
            viewPager.adapter = urlPagerAdapter
        } else if (imageList != null && imageList.isNotEmpty()) {
            // Si tenemos una lista de recursos locales, usar el adaptador existente
            val pagerAdapter = ImagePagerAdapter(imageList)
            viewPager.adapter = pagerAdapter
        }

        viewPager.setPageTransformer(ZoomOutPageTransformer())

        // Sólo mostramos los indicadores si hay más de una imagen
        if ((imageUrl != null && imageUrl.isNotEmpty()) || (imageList != null && imageList.size > 1)) {
            TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
        } else {
            tabLayout.visibility = android.view.View.GONE
        }
    }
}