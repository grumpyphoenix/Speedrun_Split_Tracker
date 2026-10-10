package com.grumpy_phoenix.speedrun_split_tracker.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val completed: Boolean,
)

/** One segment of a run; [splitTimeMs] is null when the run was reset before reaching it. */
@Entity(
    tableName = "run_splits",
    primaryKeys = ["runId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RunSplitEntity(
    val runId: String,
    val position: Int,
    val segmentName: String,
    val splitTimeMs: Long?,
)

/** The segment list currently being edited on the setup screen. */
@Entity(tableName = "segment_drafts")
data class SegmentDraftEntity(
    @PrimaryKey val position: Int,
    val name: String,
)

data class RunWithSplits(
    @Embedded val run: RunEntity,
    @Relation(parentColumn = "id", entityColumn = "runId")
    val splits: List<RunSplitEntity>,
)

fun RunRecord.toEntity() = RunEntity(id, startedAtEpochMs, completed)

fun RunRecord.toSplitEntities() = segmentNames.mapIndexed { index, name ->
    RunSplitEntity(id, index, name, splitTimesMs.getOrNull(index))
}

fun RunWithSplits.toRecord(): RunRecord {
    val ordered = splits.sortedBy { it.position }
    return RunRecord(
        id = run.id,
        startedAtEpochMs = run.startedAtEpochMs,
        segmentNames = ordered.map { it.segmentName },
        splitTimesMs = ordered.mapNotNull { it.splitTimeMs },
        completed = run.completed,
    )
}
