package es.monsteraltech.skincare_tfm.body.mole

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import es.monsteraltech.skincare_tfm.R
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.ImagePagerAdapter
import es.monsteraltech.skincare_tfm.body.mole.ViewPageImages.ZoomOutPageTransformer

class MoleDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mole_detail)

        // Obtener los datos del intent
        val title = intent.getStringExtra("LUNAR_TITLE")
        val description = intent.getStringExtra("LUNAR_DESCRIPTION")
        val imageList = intent.getIntegerArrayListExtra("LUNAR_IMAGE_LIST") ?: listOf()

        // Vincular los datos con las vistas
        val titleTextView: TextView = findViewById(R.id.detailTitle)
        val descriptionTextView: TextView = findViewById(R.id.detailDescription)
        val viewPager: ViewPager2 = findViewById(R.id.detailImagePager)
        val tabLayout: TabLayout = findViewById(R.id.tabLayout)

        titleTextView.text = title
        descriptionTextView.text = description

        val pagerAdapter = ImagePagerAdapter(imageList)
        viewPager.adapter = pagerAdapter

        viewPager.setPageTransformer(ZoomOutPageTransformer())

        TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
    }

}