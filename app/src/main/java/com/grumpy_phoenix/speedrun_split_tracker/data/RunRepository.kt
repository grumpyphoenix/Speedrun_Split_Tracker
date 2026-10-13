package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunDao
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toRecord
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toSplitEntities
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Stores every attempt, finished or reset, per run file. */
interface RunRepository {
    /** Inserts the run, or replaces the stored run with the same id. */
    suspend fun save(runFileId: Long, run: RunRecord)

    suspend fun delete(runId: String)

    /** Runs of one run file, newest first. */
    fun runs(runFileId: Long): Flow<List<RunRecord>>
}

class InMemoryRunRepository : RunRepository {
    private val store = MutableStateFlow<List<Pair<Long, RunRecord>>>(emptyList())

    override suspend fun save(runFileId: Long, run: RunRecord) {
        store.update { runs -> runs.filterNot { it.second.id == run.id } + (runFileId to run) }
    }

    override suspend fun delete(runId: String) = store.update { runs -> runs.filterNot { it.second.id == runId } }

    override fun runs(runFileId: Long): Flow<List<RunRecord>> = store.map { runs ->
        runs.filter { it.first == runFileId }.map { it.second }.sortedByDescending { it.startedAtEpochMs }
    }
}

class RoomRunRepository(private val dao: RunDao) : RunRepository {
    override suspend fun save(runFileId: Long, run: RunRecord) =
        dao.save(run.toEntity(runFileId), run.toSplitEntities())

    override suspend fun delete(runId: String) = dao.delete(runId)

    override fun runs(runFileId: Long): Flow<List<RunRecord>> =
        dao.runs(runFileId).map { runs -> runs.map { it.toRecord() } }
}
