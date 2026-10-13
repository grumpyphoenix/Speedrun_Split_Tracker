package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.data.db.FolderEntity
import com.grumpy_phoenix.speedrun_split_tracker.data.db.LibraryDao
import com.grumpy_phoenix.speedrun_split_tracker.data.db.RunFileEntity
import com.grumpy_phoenix.speedrun_split_tracker.domain.Folder
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The folder tree and the run files in it. Deleting cascades to everything inside. */
interface LibraryRepository {
    fun folders(parentId: Long?): Flow<List<Folder>>
    fun runFiles(folderId: Long?): Flow<List<RunFile>>
    suspend fun folder(id: Long): Folder?
    suspend fun runFile(id: Long): RunFile?
    suspend fun createFolder(parentId: Long?, name: String): Long
    suspend fun createRunFile(folderId: Long?, name: String): Long
    suspend fun renameFolder(id: Long, name: String)
    suspend fun renameRunFile(id: Long, name: String)
    suspend fun deleteFolder(id: Long)
    suspend fun deleteRunFile(id: Long)
}

class InMemoryLibraryRepository : LibraryRepository {
    private val folders = MutableStateFlow<List<Folder>>(emptyList())
    private val runFiles = MutableStateFlow<List<RunFile>>(emptyList())
    private var nextId = 1L

    override fun folders(parentId: Long?) =
        folders.map { all -> all.filter { it.parentId == parentId }.sortedBy { it.name.lowercase() } }

    override fun runFiles(folderId: Long?) =
        runFiles.map { all -> all.filter { it.folderId == folderId }.sortedBy { it.name.lowercase() } }

    override suspend fun folder(id: Long) = folders.value.find { it.id == id }

    override suspend fun runFile(id: Long) = runFiles.value.find { it.id == id }

    override suspend fun createFolder(parentId: Long?, name: String): Long {
        val id = nextId++
        folders.update { it + Folder(id, parentId, name) }
        return id
    }

    override suspend fun createRunFile(folderId: Long?, name: String): Long {
        val id = nextId++
        runFiles.update { it + RunFile(id, folderId, name) }
        return id
    }

    override suspend fun renameFolder(id: Long, name: String) =
        folders.update { all -> all.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun renameRunFile(id: Long, name: String) =
        runFiles.update { all -> all.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun deleteFolder(id: Long) {
        val doomed = mutableSetOf(id)
        while (true) {
            val children = folders.value.filter { it.parentId in doomed && it.id !in doomed }
            if (children.isEmpty()) break
            doomed += children.map { it.id }
        }
        folders.update { all -> all.filterNot { it.id in doomed } }
        runFiles.update { all -> all.filterNot { it.folderId in doomed } }
    }

    override suspend fun deleteRunFile(id: Long) = runFiles.update { all -> all.filterNot { it.id == id } }
}

class RoomLibraryRepository(private val dao: LibraryDao) : LibraryRepository {
    override fun folders(parentId: Long?) =
        dao.folders(parentId).map { list -> list.map { it.toFolder() } }

    override fun runFiles(folderId: Long?) =
        dao.runFiles(folderId).map { list -> list.map { it.toRunFile() } }

    override suspend fun folder(id: Long) = dao.folder(id)?.toFolder()

    override suspend fun runFile(id: Long) = dao.runFile(id)?.toRunFile()

    override suspend fun createFolder(parentId: Long?, name: String) =
        dao.insertFolder(FolderEntity(parentId = parentId, name = name))

    override suspend fun createRunFile(folderId: Long?, name: String) =
        dao.insertRunFile(RunFileEntity(folderId = folderId, name = name))

    override suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name)

    override suspend fun renameRunFile(id: Long, name: String) = dao.renameRunFile(id, name)

    override suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    override suspend fun deleteRunFile(id: Long) = dao.deleteRunFile(id)

    private fun FolderEntity.toFolder() = Folder(id, parentId, name)

    private fun RunFileEntity.toRunFile() = RunFile(id, folderId, name)
}
