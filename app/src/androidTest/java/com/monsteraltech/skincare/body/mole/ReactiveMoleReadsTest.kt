package com.monsteraltech.skincare.body.mole

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.monsteraltech.skincare.account.AccountSettings
import com.monsteraltech.skincare.backup.BackupPayload
import com.monsteraltech.skincare.backup.BackupRecordRestorer
import com.monsteraltech.skincare.body.mole.data.local.*
import com.monsteraltech.skincare.body.mole.data.repository.LocalMoleReadRepository
import com.monsteraltech.skincare.body.mole.domain.model.*
import com.monsteraltech.skincare.body.mole.domain.repository.AnalysisHistoryPage
import com.monsteraltech.skincare.body.mole.model.AnalysisData
import com.monsteraltech.skincare.body.mole.model.MoleData
import com.monsteraltech.skincare.data.image.FilePrivateImageStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ReactiveMoleReadsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val mapper = LocalMoleMapper()
    private lateinit var database: SkinCareDatabase
    private lateinit var root: File
    private lateinit var images: FilePrivateImageStore
    private lateinit var reader: LocalMoleReadRepository
    private lateinit var observations: LocalObservationStore
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, SkinCareDatabase::class.java).build()
        root = Files.createTempDirectory(context.cacheDir.toPath(), "reactive_reads_").toFile()
        images = FilePrivateImageStore(File(root, "private"), File(root, "legacy"), File(root, "cache"))
        reader = LocalMoleReadRepository(database, images, Dispatchers.IO)
        observations = LocalObservationStore(database)
    }
    @After fun close() {
        database.close()
        check(root.canonicalFile.toPath().startsWith(context.cacheDir.canonicalFile.toPath()))
        root.deleteRecursively()
    }
    private fun mole(id: String, owner: String = "fixture", title: String = id, zone: String = "zone") =
        mapper.toEntity(MoleData(id = id, userId = owner, title = title, bodyPart = "Brazo", bodyPartColorCode = zone,
            createdAt = Timestamp(Date(1000)), updatedAt = Timestamp(Date(2000))))
    private suspend fun seed() = database.withTransaction {
        (1..70).forEach { database.moleDao().insertMole(mole("m${it.toString().padStart(3, '0')}", title = if (it == 70) "ÁREA NEEDLE" else "Entry $it")) }
        database.moleDao().insertMole(mole("other-owner", owner = "other", title = "NEEDLE"))
        database.moleDao().insertMole(mole("other-zone", zone = "elsewhere"))
    }
    private suspend fun <T> Channel<T>.next(predicate: (T) -> Boolean): T = withTimeout(5000) {
        var value = receive()
        while (!predicate(value)) value = receive()
        value
    }

    @Test fun recordsBeyondFiftyRemainAvailableAndSearchableWithStableOrdering() = runBlocking {
        seed()
        val page = reader.observeMoles("fixture", MoleListQuery(bodyPartColor = "zone"), 50).first()
        assertEquals(70, page.totalCount); assertEquals(50, page.items.size); assertTrue(page.hasMore)
        assertEquals("m001", page.items.first().id); assertEquals("m050", page.items.last().id)
        val expanded = reader.observeMoles("fixture", MoleListQuery(bodyPartColor = "zone"), 100).first()
        assertEquals(70, expanded.items.size)
        val searched = reader.observeMoles("fixture", MoleListQuery("área", bodyPartColor = "zone", selector = true), 50).first()
        assertEquals(listOf("m070"), searched.items.map { it.id })
        assertEquals(1, searched.matchingCount)
        assertEquals(71, database.moleDao().getAllMoles("fixture").size)
    }

    @Test fun aSameCountMetadataUpdateRefreshesTheActiveListWithoutResubscription() = runBlocking {
        database.moleDao().insertMole(mole("m"))
        val updates = Channel<MoleListPage>(Channel.UNLIMITED)
        val job = launch { reader.observeMoles("fixture", MoleListQuery(), 50).collect { updates.send(it) } }
        try {
            assertEquals("m", updates.next { it.totalCount == 1 }.items.single().title)
            database.moleDao().upsertMole(mole("m", title = "Renamed"))
            assertEquals("Renamed", updates.next { it.items.singleOrNull()?.title == "Renamed" }.items.single().title)
            database.moleDao().insertMole(mole("private-other", owner = "other"))
            database.moleDao().upsertMole(mole("m", title = "Renamed again"))
            val last = updates.next { it.items.singleOrNull()?.title == "Renamed again" }
            assertEquals(1, last.totalCount)
            assertEquals(listOf("m"), last.items.map { it.id })
        } finally { job.cancelAndJoin() }
    }

    @Test fun restorationWritesRefreshAnAlreadyObservedList() = runBlocking {
        val updates = Channel<MoleListPage>(Channel.UNLIMITED)
        val job = launch { reader.observeMoles("fixture", MoleListQuery(), 50).collect { updates.send(it) } }
        try {
            assertEquals(0, updates.next { it.totalCount == 0 }.totalCount)
            BackupRecordRestorer(database, images).merge("fixture",
                BackupPayload(2, 1000, listOf(mole("restored")), emptyList(), emptyMap(), AccountSettings()), emptyMap())
            assertEquals("restored", updates.next { it.totalCount == 1 }.items.single().id)
        } finally { job.cancelAndJoin() }
    }

    @Test fun historyHeaderCurrentSelectionAndAnnotationsStayInOneReactiveSnapshot() = runBlocking {
        fun analysis(id: String, time: Long) = mapper.toEntity(AnalysisData(id = id, moleId = "m", createdAt = Timestamp(Date(time))), "fixture")
        observations.create(mole("m"), analysis("first", 1000))
        val updates = Channel<AnalysisHistoryPage>(Channel.UNLIMITED)
        val job = launch { reader.observeHistory("fixture", "m", 1).collect { updates.send(it) } }
        try {
            assertEquals("first", updates.next { it.totalCount == 1 }.analyses.single().id)
            observations.append("fixture", "m", analysis("second", 2000))
            val second = updates.next { it.totalCount == 2 }
            assertEquals(2, second.mole.analysisCount)
            assertEquals("second", second.analyses.single().id)
            assertTrue(second.hasMore)
            observations.annotate("fixture", "m", "second", mapOf("userTotal" to 3))
            val edited = updates.next { it.analyses.single().analysisMetadata["userTotal"] == 3.0 }
            assertEquals(2, edited.totalCount)
            assertEquals(2, edited.mole.analysisCount)
            val full = reader.observeHistory("fixture", "m", 50).first()
            assertEquals(listOf("second", "first"), full.analyses.map { it.id })
        } finally { job.cancelAndJoin() }
    }
}
