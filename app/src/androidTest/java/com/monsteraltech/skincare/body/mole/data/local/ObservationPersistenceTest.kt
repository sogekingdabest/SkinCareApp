package com.monsteraltech.skincare.body.mole.data.local

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import com.google.gson.Gson
import com.monsteraltech.skincare.analysis.comparison.LocalObservationImageDecoder
import com.monsteraltech.skincare.body.mole.data.repository.LocalMoleObservationRepository
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.ABCDEScores
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.data.identity.ActiveProfile
import com.monsteraltech.skincare.data.image.privateImageStore
import java.io.File
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObservationPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val mapper = LocalMoleMapper()
    private lateinit var database: SkinCareDatabase
    private lateinit var store: LocalObservationStore

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        store = LocalObservationStore(database)
    }

    @After fun close() = database.close()

    private fun mole(id: String = "m") = mapper.toEntity(MoleData(id = id, userId = "owner", title = "Fixture"))
    private fun analysis(id: String, parent: String = "m", time: Long = 1000, image: String = "$id.jpg") =
        mapper.toEntity(AnalysisData(id = id, moleId = parent, description = id, analysisResult = "result-$id",
            imageUrl = image, riskLevel = "LOW", recommendation = "fixture", createdAt = Instant.ofEpochMilli(time),
            abcdeScores = ABCDEScores(totalScore = 4f, evolutionScore = 1f)), "owner")

    @Test fun firstAndSubsequentObservationsKeepTheirIDsImagesAndScores() = runBlocking {
        val first = analysis("first")
        val second = analysis("second", time = 2000)
        store.create(mole(), first)
        assertEquals(listOf(first), database.moleDao().getAnalysisHistory("owner", "m"))
        store.append("owner", "m", second)
        val snapshot = store.current("owner", "m")
        assertEquals(second, snapshot.analysis)
        assertEquals(2, snapshot.mole.analysisCount)
        assertEquals(1000L, snapshot.mole.firstAnalysisAtEpochMillis)
        assertEquals(2000L, snapshot.mole.lastAnalysisAtEpochMillis)
        assertEquals(listOf(second, first), database.moleDao().getAnalysisHistory("owner", "m"))
        assertEquals(first, database.moleDao().getAnalysis("owner", "m", "first"))
    }

    @Test fun currentPointerAndCountSurviveConcurrentSavesWithIdenticalTimestamps() = runBlocking {
        store.create(mole(), analysis("first"))
        coroutineScope {
            val a = async(Dispatchers.IO) { store.append("owner", "m", analysis("a")) }
            val b = async(Dispatchers.IO) { store.append("owner", "m", analysis("b")) }
            a.await(); b.await()
        }
        val snapshot = store.current("owner", "m")
        val history = database.moleDao().getAnalysisHistory("owner", "m")
        assertEquals(3, snapshot.mole.analysisCount)
        assertEquals(setOf("first", "a", "b"), history.map { it.id }.toSet())
        assertEquals(snapshot.analysis?.id, history.first().id)
    }

    @Test fun duplicateIDsCannotOverwriteAnObservationOrLeaveHalfCreatedMoles() = runBlocking {
        val first = analysis("first")
        store.create(mole(), first)
        val before = store.current("owner", "m")
        try { store.append("owner", "m", first.copy(imagePath = "different.jpg")); fail("Expected duplicate ID") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(before, store.current("owner", "m"))
        try { store.create(mole("other"), first.copy(moleId = "other")); fail("Expected duplicate ID") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNull(database.moleDao().getMole("owner", "other"))
        assertEquals(first, database.moleDao().getAnalysis("owner", "m", "first"))
    }

    @Test fun annotationsRemainAttachedToTheSelectedObservation() = runBlocking {
        store.create(mole(), analysis("first"))
        store.append("owner", "m", analysis("second"))
        val before = store.current("owner", "m")
        store.annotate("owner", "m", "first", mapOf("userTotal" to 2))
        assertEquals(before, store.current("owner", "m"))
        val edited = database.moleDao().getAnalysis("owner", "m", "first")!!
        assertEquals("first.jpg", edited.imagePath)
        assertEquals(1000L, edited.createdAtEpochMillis)
        assertEquals(2.0, mapper.toModel(edited).analysisMetadata["userTotal"])
        store.annotate("owner", "m", "second", mapOf("userTotal" to 3))
        val current = store.current("owner", "m")
        assertEquals(current.analysis?.analysisMetadataJson, current.mole.analysisMetadataJson)
    }

    @Test fun wrongOwnerCannotReadAppendOrAnnotateAnObservation() = runBlocking {
        store.create(mole(), analysis("first"))
        try { store.current("another", "m"); fail("Expected owner mismatch") } catch (_: NoSuchElementException) { }
        try { store.append("another", "m", analysis("other").copy(userId = "another")); fail("Expected owner mismatch") }
        catch (_: NoSuchElementException) { }
        try { store.annotate("another", "m", "first", mapOf("userTotal" to 2)); fail("Expected owner mismatch") }
        catch (_: NoSuchElementException) { }
        assertEquals(1, database.moleDao().countAnalyses("owner", "m"))
    }

    @Test fun persistedObservationReferenceDecodesThroughTheComparisonPipeline() = runBlocking(Dispatchers.IO) {
        val images = privateImageStore(context)
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 100, 180)) }
        val capture = File.createTempFile("observation_", ".jpg", context.cacheDir)
        var reference: String? = null
        var decoded: Bitmap? = null
        try {
            capture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val saved = images.save(capture, "observation-test-${UUID.randomUUID()}").also { reference = it }
            store.create(mole(), analysis("capture", image = saved))
            val profile = object : ActiveProfile {
                override val ownerId = "owner"
                override val isLocal = true
                override fun activateLocalProfile() = ownerId
                override fun activateAccountProfile() = Unit
                override fun clear() = Unit
            }
            val observation = LocalMoleObservationRepository(store, profile, Dispatchers.IO).getLatestObservation("m").getOrThrow()!!
            assertEquals("capture", observation.id)
            assertEquals(saved, observation.imageReference)
            decoded = LocalObservationImageDecoder(context, images, Dispatchers.IO).decode(observation.imageReference)
            assertEquals(64, decoded!!.width)
            assertEquals(48, decoded.height)
        } finally {
            decoded?.recycle(); bitmap.recycle(); capture.delete(); reference?.let(images::delete)
        }
    }

    @Test fun realVersionOneDatabaseMigratesWithoutLosingHistoricalData() = runBlocking {
        val name = "observation_migration_${UUID.randomUUID()}.db"
        val first = analysis("m_analysis_1", image = "legacy-first.jpg")
        val legacy = mapper.toEntity(MoleData(id = "m", userId = "owner", analysisCount = 2,
            aiResult = "last-result", imageUrl = "legacy-current.jpg", aiProbability = 0.7,
            lastAnalysisDate = Instant.ofEpochMilli(2000), firstAnalysisDate = Instant.ofEpochMilli(1000)))
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        var migrated: SkinCareDatabase? = null
        try {
            val old = helper.writableDatabase
            insertLegacy(old, "moles", legacy)
            insertLegacy(old, "mole_analyses", first)
            helper.close()
            val opened = Room.databaseBuilder(context, SkinCareDatabase::class.java, name)
                .addMigrations(ObservationMigration.FROM_1_TO_2).build().also { migrated = it }
            val migratedStore = LocalObservationStore(opened)
            val snapshot = migratedStore.current("owner", "m")
            assertEquals(LegacyObservation.id(legacy), snapshot.analysis!!.id)
            assertEquals("legacy-current.jpg", snapshot.analysis!!.imagePath)
            assertEquals(0.7f, snapshot.analysis!!.aiProbability, 0.001f)
            assertEquals(first, opened.moleDao().getAnalysis("owner", "m", first.id))
            assertEquals(2, opened.moleDao().getAnalysisHistory("owner", "m").size)
            opened.moleDao().materializeLegacyCurrentAnalyses()
            assertEquals(snapshot, migratedStore.current("owner", "m"))
            migratedStore.append("owner", "m", analysis("third", time = 3000))
            assertEquals(3, opened.moleDao().getAnalysisHistory("owner", "m").size)
            assertEquals(snapshot.analysis, opened.moleDao().getAnalysis("owner", "m", snapshot.analysis!!.id))
        } finally {
            helper.close(); migrated?.close(); context.deleteDatabase(name)
        }
    }

    private fun insertLegacy(db: SupportSQLiteDatabase, table: String, row: Any) {
        val values = JSONObject(Gson().toJson(row))
        val keys = values.keys().asSequence().toList()
        val args = keys.map { values.get(it) }.toTypedArray()
        db.execSQL("INSERT INTO $table (${keys.joinToString(",")}) VALUES (${keys.joinToString(",") { "?" }})", args)
    }
}
