package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.data.db.SegmentDao
import com.grumpy_phoenix.speedrun_split_tracker.data.db.SegmentDraftEntity

/** Remembers the segment list from the setup screen between app starts. */
interface SegmentRepository {
    suspend fun load(): List<String>
    suspend fun save(names: List<String>)
}

class InMemorySegmentRepository : SegmentRepository {
    private var names = emptyList<String>()

    override suspend fun load(): List<String> = names

    override suspend fun save(names: List<String>) {
        this.names = names
    }
}

class RoomSegmentRepository(private val dao: SegmentDao) : SegmentRepository {
    override suspend fun load(): List<String> = dao.getAll().map { it.name }

    override suspend fun save(names: List<String>) =
        dao.replaceAll(names.mapIndexed { index, name -> SegmentDraftEntity(index, name) })
}
