package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunFileSegmentEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.SegmentDao

/** The editable segment list of each run file. */
interface SegmentRepository {
    suspend fun load(runFileId: Long): List<String>
    suspend fun save(runFileId: Long, names: List<String>)
}

class InMemorySegmentRepository : SegmentRepository {
    private val names = mutableMapOf<Long, List<String>>()

    override suspend fun load(runFileId: Long): List<String> = names[runFileId].orEmpty()

    override suspend fun save(runFileId: Long, names: List<String>) {
        this.names[runFileId] = names
    }
}

class RoomSegmentRepository(private val dao: SegmentDao) : SegmentRepository {
    override suspend fun load(runFileId: Long): List<String> = dao.getAll(runFileId).map { it.name }

    override suspend fun save(runFileId: Long, names: List<String>) = dao.replaceAll(
        runFileId,
        names.mapIndexed { index, name -> RunFileSegmentEntity(runFileId, index, name) },
    )
}
