package com.grumpy_phoenix.speedrun_split_tracker.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grumpy_phoenix.speedrun_split_tracker.data.db.AppDatabase
import com.grumpy_phoenix.speedrun_split_tracker.data.db.FolderEntity
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRunFileImporterTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun importCreatesRunFileWithSegmentsAndRuns() = runBlocking {
        val folderId = db.libraryDao().insertFolder(FolderEntity(parentId = null, name = "SM64"))
        val segments = listOf("BoB", "WF")
        val runs = listOf(
            RunRecord(startedAtEpochMs = 1, segmentNames = segments, splitTimesMs = listOf(10, 20), completed = true),
            RunRecord(startedAtEpochMs = 2, segmentNames = segments, splitTimesMs = listOf(12), completed = false),
        )

        val id = RoomRunFileImporter(db).import(folderId, "16 Star", segments, runs)

        val file = db.libraryDao().runFile(id)!!
        assertEquals(folderId, file.folderId)
        assertEquals("16 Star", file.name)
        assertEquals(segments, RoomSegmentRepository(db.segmentDao()).load(id))
        val stored = RoomRunRepository(db.runDao()).runs(id).first()
        assertEquals(runs.sortedByDescending { it.startedAtEpochMs }, stored)
    }
}
