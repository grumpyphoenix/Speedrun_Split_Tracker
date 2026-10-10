package com.grumpy_phoenix.speedrun_split_tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Upsert
    suspend fun upsertRun(run: RunEntity)

    @Query("DELETE FROM run_splits WHERE runId = :runId")
    suspend fun deleteSplits(runId: String)

    @Insert
    suspend fun insertSplits(splits: List<RunSplitEntity>)

    /** Inserts the run, or replaces the stored run (and all its splits) with the same id. */
    @Transaction
    suspend fun save(run: RunEntity, splits: List<RunSplitEntity>) {
        upsertRun(run)
        deleteSplits(run.id)
        insertSplits(splits)
    }

    @Transaction
    @Query("SELECT * FROM runs ORDER BY startedAtEpochMs")
    fun runs(): Flow<List<RunWithSplits>>
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segment_drafts ORDER BY position")
    suspend fun getAll(): List<SegmentDraftEntity>

    @Query("DELETE FROM segment_drafts")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(segments: List<SegmentDraftEntity>)

    @Transaction
    suspend fun replaceAll(segments: List<SegmentDraftEntity>) {
        deleteAll()
        insertAll(segments)
    }
}
