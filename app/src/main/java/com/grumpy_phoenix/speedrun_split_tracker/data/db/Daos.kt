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

    /** Cascades to the run's splits. */
    @Query("DELETE FROM runs WHERE id = :runId")
    suspend fun delete(runId: String)

    /** Newest first. */
    @Transaction
    @Query("SELECT * FROM runs WHERE runFileId = :runFileId ORDER BY startedAtEpochMs DESC")
    fun runs(runFileId: Long): Flow<List<RunWithSplits>>
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM run_file_segments WHERE runFileId = :runFileId ORDER BY position")
    suspend fun getAll(runFileId: Long): List<RunFileSegmentEntity>

    @Query("DELETE FROM run_file_segments WHERE runFileId = :runFileId")
    suspend fun deleteAll(runFileId: Long)

    @Insert
    suspend fun insertAll(segments: List<RunFileSegmentEntity>)

    @Transaction
    suspend fun replaceAll(runFileId: Long, segments: List<RunFileSegmentEntity>) {
        deleteAll(runFileId)
        insertAll(segments)
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM folders WHERE parentId IS :parentId ORDER BY name COLLATE NOCASE")
    fun folders(parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM run_files WHERE folderId IS :folderId ORDER BY name COLLATE NOCASE")
    fun runFiles(folderId: Long?): Flow<List<RunFileEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun folder(id: Long): FolderEntity?

    @Query("SELECT * FROM run_files WHERE id = :id")
    suspend fun runFile(id: Long): RunFileEntity?

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert
    suspend fun insertRunFile(runFile: RunFileEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE run_files SET name = :name WHERE id = :id")
    suspend fun renameRunFile(id: Long, name: String)

    /** Cascades to subfolders, run files, runs and splits. */
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    /** Cascades to the file's segments, runs and splits. */
    @Query("DELETE FROM run_files WHERE id = :id")
    suspend fun deleteRunFile(id: Long)
}
