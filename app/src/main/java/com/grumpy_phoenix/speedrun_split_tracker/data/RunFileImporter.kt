package com.grumpy_phoenix.speedrun_split_tracker.data

import androidx.room.withTransaction
import com.grumpy_phoenix.speedrun_split_tracker.data.db.AppDatabase
import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunFileEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunFileSegmentEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toSplitEntities
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord

/** Creates a run file together with its segments and runs. */
fun interface RunFileImporter {
    /** Returns the id of the new run file. */
    suspend fun import(folderId: Long?, name: String, segmentNames: List<String>, runs: List<RunRecord>): Long
}

/** Writes everything in one transaction, so a failed import leaves no half-filled run file behind. */
class RoomRunFileImporter(private val db: AppDatabase) : RunFileImporter {
    override suspend fun import(
        folderId: Long?,
        name: String,
        segmentNames: List<String>,
        runs: List<RunRecord>,
    ): Long = db.withTransaction {
        val runFileId = db.libraryDao().insertRunFile(RunFileEntity(folderId = folderId, name = name))
        db.segmentDao().insertAll(
            segmentNames.mapIndexed { index, segment -> RunFileSegmentEntity(runFileId, index, segment) },
        )
        for (run in runs) db.runDao().save(run.toEntity(runFileId), run.toSplitEntities())
        runFileId
    }
}
