package com.grumpy_phoenix.speedrun_split_tracker.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2MovesOldDataIntoImportedRunFile() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO segment_drafts (position, name) VALUES (0, 'Forest'), (1, 'Cave'), (2, 'Tower'), (3, 'Boss')")
            execSQL("INSERT INTO runs (id, startedAtEpochMs, completed) VALUES ('r1', 1791640800000, 1)")
            execSQL(
                "INSERT INTO run_splits (runId, position, segmentName, splitTimeMs) " +
                    "VALUES ('r1', 0, 'Forest', 10000), ('r1', 1, 'Boss', 25000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        val fileId = db.query("SELECT id, folderId, name FROM run_files").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(true, it.isNull(1))
            assertEquals(IMPORTED_RUN_FILE_NAME, it.getString(2))
            it.getLong(0)
        }
        db.query("SELECT name FROM run_file_segments WHERE runFileId = ? ORDER BY position", arrayOf(fileId)).use {
            val names = generateSequence { if (it.moveToNext()) it.getString(0) else null }.toList()
            assertEquals(listOf("Forest", "Cave", "Tower", "Boss"), names)
        }
        db.query("SELECT COUNT(*) FROM run_file_segments WHERE runFileId <> ?", arrayOf(fileId)).use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT runFileId, startedAtEpochMs, completed FROM runs WHERE id = 'r1'").use {
            it.moveToFirst()
            assertEquals(fileId, it.getLong(0))
            assertEquals(1791640800000L, it.getLong(1))
            assertEquals(1, it.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM run_splits WHERE runId = 'r1'").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun migrate1To2WithoutDataCreatesNoRunFile() {
        helper.createDatabase(DB_NAME, 1).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM run_files").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate2To3RemovesOrphanedSegments() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL("INSERT INTO run_files (id, folderId, name) VALUES (1, NULL, 'Imported')")
            // Rows the first release of MIGRATION_1_2 left behind, pointing at run files that never existed.
            execSQL(
                "INSERT INTO run_file_segments (runFileId, position, name) " +
                    "VALUES (1, 0, 'Forest'), (1, 1, 'Cave'), (3, 3, 'Tower'), (4, 4, 'Boss')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

        db.query("SELECT runFileId, name FROM run_file_segments ORDER BY runFileId, position").use {
            val rows = generateSequence { if (it.moveToNext()) it.getLong(0) to it.getString(1) else null }.toList()
            assertEquals(listOf(1L to "Forest", 1L to "Cave"), rows)
        }
        // A new run file can now take any segment position.
        db.execSQL("INSERT INTO run_files (id, folderId, name) VALUES (3, NULL, 'Portal')")
        db.execSQL("INSERT INTO run_file_segments (runFileId, position, name) VALUES (3, 3, 'Chamber 03')")
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
