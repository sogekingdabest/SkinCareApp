package com.monsteraltech.skincare.body.mole

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.performance.LazyImageLoader
import com.monsteraltech.skincare.testing.LifecycleTestActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyImageLifecycleTest {
    private fun checkDelayedLoad(rebind: Boolean) {
        ActivityScenario.launch(LifecycleTestActivity::class.java).use { scenario ->
            lateinit var recycler: RecyclerView
            lateinit var image: ImageView
            lateinit var loader: LazyImageLoader
            lateinit var confirmedDrawable: Drawable
            scenario.onActivity { host ->
                recycler = RecyclerView(host).apply {
                    layoutManager = LinearLayoutManager(host)
                    adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 1
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                            image = ImageView(parent.context).apply { layoutParams = RecyclerView.LayoutParams(200, 200) }
                            return object : RecyclerView.ViewHolder(image) { }
                        }
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
                    }
                }
                host.setContentView(recycler)
                recycler.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
                recycler.layout(0, 0, 500, 500)
                loader = LazyImageLoader(host, host.lifecycleScope)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(0, recycler.findContainingViewHolder(image)?.adapterPosition)
                assertEquals(0, (recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
                loader.loadImageLazy(image, "missing-fixture.jpg", recycler,
                    LazyImageLoader.LazyLoadConfig(loadDelay = 200, errorRes = R.drawable.ic_image_placeholder_small))
                if (rebind) loader.loadImageLazy(image, null, recycler)
                else { loader.cancelLoad(image); image.setImageResource(R.drawable.ic_image_error) }
                confirmedDrawable = image.drawable
            }
            // Deliberately exceed the real loading delay: a stale request would replace this drawable.
            kotlinx.coroutines.runBlocking { kotlinx.coroutines.delay(350) }
            scenario.onActivity {
                assertSame(confirmedDrawable, image.drawable)
                loader.cancelAllLoads()
            }
        }
    }
    @Test fun rebindingToAnEmptyImageCancelsThePreviousRowsDelayedLoad() = checkDelayedLoad(rebind = true)
    @Test fun recyclingTheImageCancelsItsDelayedLoad() = checkDelayedLoad(rebind = false)
}
