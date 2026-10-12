package com.monsteraltech.skincare.body.mole

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.mole.data.local.*
import com.monsteraltech.skincare.body.mole.model.*
import com.monsteraltech.skincare.body.mole.repository.MoleRepository
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.image.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoricalViewerNavigationTest {
    @Test fun historicalHeaderAndImageComeFromTheSelectedStoredObservation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        val images = privateImageStore(context)
        val store = LocalObservationStore(db)
        val mapper = LocalMoleMapper()
        val owner = object : ActiveProfile {
            override val ownerId = "viewer-${UUID.randomUUID()}"
            override val isLocal = true
            override fun activateLocalProfile() = ownerId
            override fun activateAccountProfile() = Unit
            override fun clear() = Unit
        }
        val references = mutableListOf<String>()
        fun photo(color: Int): String {
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val temporary = File.createTempFile("viewer-fixture", ".png", context.cacheDir)
            try {
                temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                return images.save(temporary, owner.ownerId).also { references.add(it) }
            } finally { bitmap.recycle(); temporary.delete() }
        }
        val repository = MoleRepository(db, owner, images, PrivateImagePersistence(images, Dispatchers.IO), store)
        val model = MoleViewerViewModel(context, repository, owner)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, state: Bundle?) {
                if (activity is MoleViewerActivity) {
                    ViewModelProvider(activity as ViewModelStoreOwner, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = model as T
                    })[MoleViewerViewModel::class.java]
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        val application = context.applicationContext as Application
        try {
            val first = AnalysisData(id = "first", moleId = "m", description = "First observation", imageUrl = photo(Color.BLUE))
            store.create(mapper.toEntity(MoleData(id = "m", userId = owner.ownerId, title = "Canonical title")), mapper.toEntity(first, owner.ownerId))
            store.append(owner.ownerId, "m", mapper.toEntity(first.copy(id = "current", description = "Current observation", imageUrl = photo(Color.RED)), owner.ownerId))
            application.registerActivityLifecycleCallbacks(callbacks)
            val intent = MoleViewerArgs("m", "first").toIntent(context).putExtra("LUNAR_TITLE", "stale title")
            ActivityScenario.launch<MoleViewerActivity>(intent).use { scenario ->
                val deadline = System.nanoTime() + 5_000_000_000L
                var shown = false
                while (!shown && System.nanoTime() < deadline) {
                    scenario.onActivity { activity ->
                        val bitmap = (activity.findViewById<ImageView>(R.id.resultImageView).drawable as? BitmapDrawable)?.bitmap
                        shown = bitmap != null && bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == Color.BLUE
                        if (shown) {
                            assertEquals("Canonical title", activity.findViewById<TextView>(R.id.detailTitle).text.toString())
                            assertEquals("First observation", activity.findViewById<TextView>(R.id.detailDescription).text.toString())
                        }
                    }
                    if (!shown) Thread.sleep(25)
                }
                assertTrue("The historical blue image must replace the current red image", shown)
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            references.forEach(images::delete)
            db.close()
        }
    }
}
