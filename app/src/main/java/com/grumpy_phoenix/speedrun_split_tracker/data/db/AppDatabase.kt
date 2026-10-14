package com.grumpy_phoenix.speedrun_split_tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Survives app updates. When changing the schema, bump [version] and add a Migration —
 * never fall back to destructive migration, or stored runs are lost.
 */
@Database(
    entities = [
        FolderEntity::class,
        RunFileEntity::class,
        RunFileSegmentEntity::class,
        RunEntity::class,
        RunSplitEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun segmentDao(): SegmentDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "speedrun.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

/** Name of the run file that receives the segments and runs stored before folders existed. */
const val IMPORTED_RUN_FILE_NAME = "Imported"

/**
 * Introduces folders and run files. The old global segment list and all existing runs
 * move into a top-level run file named [IMPORTED_RUN_FILE_NAME].
 *
 * Foreign keys are not enforced during migrations (Room enables them on open), so
 * rebuilding `runs` does not cascade into `run_splits`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`parentId` INTEGER, `name` TEXT NOT NULL, FOREIGN KEY(`parentId`) REFERENCES " +
                "`folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `run_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`folderId` INTEGER, `name` TEXT NOT NULL, FOREIGN KEY(`folderId`) REFERENCES " +
                "`folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_run_files_folderId` ON `run_files` (`folderId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `run_file_segments` (`runFileId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`runFileId`, `position`), " +
                "FOREIGN KEY(`runFileId`) REFERENCES `run_files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        val hasOldData = db.hasRows("runs") || db.hasRows("segment_drafts")
        if (hasOldData) {
            db.execSQL(
                "INSERT INTO `run_files` (`folderId`, `name`) VALUES (NULL, ?)",
                arrayOf(IMPORTED_RUN_FILE_NAME),
            )
            // Read the id once: last_insert_rowid() inside INSERT ... SELECT changes after every row.
            val runFileId = db.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
            db.execSQL(
                "INSERT INTO `run_file_segments` (`runFileId`, `position`, `name`) " +
                    "SELECT ?, `position`, `name` FROM `segment_drafts`",
                arrayOf(runFileId),
            )
        }

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `runs_new` (`id` TEXT NOT NULL, `runFileId` INTEGER NOT NULL, " +
                "`startedAtEpochMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`runFileId`) REFERENCES `run_files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `runs_new` (`id`, `runFileId`, `startedAtEpochMs`, `completed`) " +
                "SELECT `id`, (SELECT MIN(`id`) FROM `run_files`), `startedAtEpochMs`, `completed` FROM `runs`",
        )
        db.execSQL("DROP TABLE `runs`")
        db.execSQL("ALTER TABLE `runs_new` RENAME TO `runs`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runs_runFileId` ON `runs` (`runFileId`)")
        db.execSQL("DROP TABLE `segment_drafts`")
    }
}

/**
 * Removes segments whose run file doesn't exist. The first release of [MIGRATION_1_2] attached each old
 * segment to the run file id equal to its row number, and those rows clash with new run files.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `run_file_segments` WHERE `runFileId` NOT IN (SELECT `id` FROM `run_files`)")
    }
}

private fun SupportSQLiteDatabase.hasRows(table: String): Boolean =
    query("SELECT EXISTS(SELECT 1 FROM `$table`)").use { it.moveToFirst() && it.getInt(0) == 1 }
