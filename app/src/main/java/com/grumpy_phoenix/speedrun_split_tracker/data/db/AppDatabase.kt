package com.grumpy_phoenix.speedrun_split_tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Survives app updates. When changing the schema, bump [version] and add a Migration —
 * never fall back to destructive migration, or stored runs are lost.
 */
@Database(
    entities = [RunEntity::class, RunSplitEntity::class, SegmentDraftEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "speedrun.db",
            ).build().also { instance = it }
        }
    }
}
