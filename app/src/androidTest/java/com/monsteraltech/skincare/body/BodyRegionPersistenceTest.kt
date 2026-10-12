package com.monsteraltech.skincare.body

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.monsteraltech.skincare.R
import com.monsteraltech.skincare.body.domain.BodyRegionId
import com.monsteraltech.skincare.body.mole.data.local.*
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.body.presentation.BodyRegionContract
import com.monsteraltech.skincare.body.presentation.BodyRegions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyRegionPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val mapper = LocalMoleMapper()

    @Test fun versionTwoMigratesAllRegionsAndKeepsEveryObservationAndReference() = runBlocking {
        val name = "regions-v2-${UUID.randomUUID()}.db"
        val schema = JSONObject(instrumentation.context.assets
            .open("com.monsteraltech.skincare.body.mole.data.local.SkinCareDatabase/2.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        var migrated: SkinCareDatabase? = null
        try {
            val old = helper.writableDatabase
            val colors = listOf("#FF000000", "#FFED1C24", "#FFFFC90E", "#FF22B14C", "#FF3F48CC", "#FFED00FF", "#123456", "")
            val expected = listOf(BodyRegionId.HEAD, BodyRegionId.RIGHT_ARM, BodyRegionId.TORSO,
                BodyRegionId.LEFT_ARM, BodyRegionId.RIGHT_LEG, BodyRegionId.LEFT_LEG, BodyRegionId.UNKNOWN, BodyRegionId.TORSO)
            val observations = colors.indices.map { index ->
                mapper.toEntity(AnalysisData(id = "a$index", moleId = "m$index", imageUrl = "private/image-$index.jpg",
                    analysisMetadata = mapOf("sentinel" to "unchanged")), "fixture")
            }
            colors.forEachIndexed { index, color ->
                val mole = mapper.toEntity(MoleData(id = "m$index", userId = "fixture", bodyPartColorCode = color,
                    bodyPart = if (index == 7) "Torso" else "Original label $index", imageUrl = observations[index].imagePath,
                    aiResult = "result", analysisCount = 1, currentAnalysisId = observations[index].id))
                insert(old, "moles", mole, true)
                insert(old, "mole_analyses", observations[index], false)
            }
            helper.close()
            val opened = Room.databaseBuilder(context, SkinCareDatabase::class.java, name)
                .addMigrations(BodyRegionMigration.FROM_2_TO_3).build().also { migrated = it }
            expected.forEachIndexed { index, region ->
                val mole = checkNotNull(opened.moleDao().getMole("fixture", "m$index"))
                assertEquals(region.value, mole.bodyRegionId)
                assertEquals(colors[index], mole.bodyPartColorCode)
                assertEquals(observations[index].imagePath, mole.imagePath)
                assertEquals(observations[index].id, mole.currentAnalysisId)
                assertEquals(observations[index], opened.moleDao().getAnalysis("fixture", "m$index", "a$index"))
                assertEquals(1, mole.analysisCount)
            }
        } finally { helper.close(); migrated?.close(); context.deleteDatabase(name) }
    }

    @Test fun regionQueriesIgnoreChangedLabelsAndPalettesAndRemainOwnerScoped() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        try {
            val base = mapper.toEntity(MoleData(id = "target", userId = "owner", bodyPart = "Localized label", bodyPartColorCode = "#ABCDEF", bodyRegionId = BodyRegionId.RIGHT_ARM))
            db.moleDao().insertMole(base)
            db.moleDao().insertMole(base.copy(id = "wrong-site", bodyPartColorCode = "#FFED1C24", bodyRegionId = BodyRegionId.LEFT_ARM.value))
            db.moleDao().insertMole(base.copy(id = "wrong-owner", userId = "other"))
            assertEquals(listOf("target"), db.moleDao().getMolesForBodyRegion("owner", BodyRegionId.RIGHT_ARM.value).map { it.id })
        } finally { db.close() }
    }

    @Test fun appendingAnObservationKeepsTheParentRegionInsteadOfTheCaptureSelector() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        try {
            val store = LocalObservationStore(db)
            val mole = mapper.toEntity(MoleData(id = "m", userId = "owner", bodyPart = "Torso", bodyPartColorCode = "#FFFFC90E", bodyRegionId = BodyRegionId.TORSO))
            val first = mapper.toEntity(AnalysisData(id = "first", moleId = "m"), "owner")
            store.create(mole, first)
            val next = mapper.toEntity(AnalysisData(id = "next", moleId = "m", analysisMetadata = mapOf("bodyRegionId" to "head", "userTotal" to 4)), "owner")
            store.append("owner", "m", next)
            assertEquals("torso", db.moleDao().getMole("owner", "m")!!.bodyRegionId)
            val saved = mapper.toModel(db.moleDao().getAnalysis("owner", "m", "next")!!)
            assertEquals("torso", saved.analysisMetadata["bodyRegionId"])
            assertEquals(4.0, saved.analysisMetadata["userTotal"])
            assertEquals(first, db.moleDao().getAnalysis("owner", "m", "first"))
        } finally { db.close() }
    }

    @Test fun newIntentIdentityWinsAndOldColorOnlyArgumentsRemainSupported() {
        assertEquals(BodyRegionId.TORSO, BodyRegionContract.read(Intent().putExtra("BODY_PART_COLOR", "#FFFFC90E")))
        assertEquals(BodyRegionId.LEFT_ARM, BodyRegionContract.read(Intent().putExtra("BODY_PART_COLOR", "#FFFFC90E")
            .putExtra(BodyRegionContract.EXTRA_REGION_ID, "left_arm")))
        assertEquals(BodyRegionId.UNKNOWN, BodyRegionContract.read(Intent().putExtra("BODY_PART_COLOR", "#FFFFC90E")
            .putExtra(BodyRegionContract.EXTRA_REGION_ID, "invalid")))
    }

    @Test fun everyCurrentMaskRegionMapsToItsStableId() {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.front_body_mask,
            BitmapFactory.Options().apply { inScaled = false })
        try {
            val found = mutableSetOf<BodyRegionId>()
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) BodyRegions.fromMaskColor(bitmap.getPixel(x, y))?.let(found::add)
            assertEquals(BodyRegions.selectable.map { it.id }.toSet(), found)
        } finally { bitmap.recycle() }
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, row: Any, legacy: Boolean) {
        val values = JSONObject(Gson().toJson(row))
        if (legacy) values.remove("bodyRegionId")
        val keys = values.keys().asSequence().toList()
        db.execSQL("INSERT INTO $table (${keys.joinToString(",")}) VALUES (${keys.joinToString(",") { "?" }})", keys.map { values.get(it) }.toTypedArray())
    }
}
