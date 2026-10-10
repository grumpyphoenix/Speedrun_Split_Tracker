package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunDao
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toRecord
import com.grumpy_phoenix.speedrun_split_tracker.data.db.toSplitEntities
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Stores every attempt, finished or reset. */
interface RunRepository {
    /** Inserts the run, or replaces the stored run with the same id. */
    suspend fun save(run: RunRecord)
    fun runs(): Flow<List<RunRecord>>
}

class InMemoryRunRepository : RunRepository {
    private val store = MutableStateFlow<List<RunRecord>>(emptyList())

    override suspend fun save(run: RunRecord) {
        store.update { runs ->
            val index = runs.indexOfFirst { it.id == run.id }
            if (index < 0) runs + run else runs.toMutableList().apply { set(index, run) }
        }
    }

    override fun runs(): Flow<List<RunRecord>> = store.asStateFlow()
}

class RoomRunRepository(private val dao: RunDao) : RunRepository {
    override suspend fun save(run: RunRecord) = dao.save(run.toEntity(), run.toSplitEntities())

    override fun runs(): Flow<List<RunRecord>> =
        dao.runs().map { runs -> runs.map { it.toRecord() } }
}
