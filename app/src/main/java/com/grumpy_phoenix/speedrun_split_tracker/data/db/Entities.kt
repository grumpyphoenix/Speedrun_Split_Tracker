package com.grumpy_phoenix.speedrun_split_tracker.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord

/** A folder in the library tree; [parentId] is null for top-level folders. */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val name: String,
)

/** Holds a segment list and the attempts run with it; [folderId] is null at the top level. */
@Entity(
    tableName = "run_files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("folderId")],
)
data class RunFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long?,
    val name: String,
)

/** The editable segment list of a run file. */
@Entity(
    tableName = "run_file_segments",
    primaryKeys = ["runFileId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RunFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["runFileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RunFileSegmentEntity(
    val runFileId: Long,
    val position: Int,
    val name: String,
)

@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(
            entity = RunFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["runFileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runFileId")],
)
data class RunEntity(
    @PrimaryKey val id: String,
    val runFileId: Long,
    val startedAtEpochMs: Long,
    val completed: Boolean,
)

/**
 * One segment of a run; [splitTimeMs] is null when the run was reset before reaching it.
 * Segment names are a snapshot, so editing the run file's segments leaves old runs untouched.
 */
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

data class RunWithSplits(
    @Embedded val run: RunEntity,
    @Relation(parentColumn = "id", entityColumn = "runId")
    val splits: List<RunSplitEntity>,
)

fun RunRecord.toEntity(runFileId: Long) = RunEntity(id, runFileId, startedAtEpochMs, completed)

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
