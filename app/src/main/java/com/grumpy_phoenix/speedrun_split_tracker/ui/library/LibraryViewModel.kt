@file:OptIn(ExperimentalCoroutinesApi::class)

package com.grumpy_phoenix.speedrun_split_tracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grumpy_phoenix.speedrun_split_tracker.data.LibraryRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.RunFileImporter
import com.grumpy_phoenix.speedrun_split_tracker.data.lss.LssCodec
import com.grumpy_phoenix.speedrun_split_tracker.data.lss.LssFormatException
import com.grumpy_phoenix.speedrun_split_tracker.domain.Folder
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

data class LibraryUiState(
    /** Folders from the top level down to the one being shown; empty at the top level. */
    val path: List<Folder> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val runFiles: List<RunFile> = emptyList(),
) {
    val currentFolderId: Long? get() = path.lastOrNull()?.id
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val importer: RunFileImporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val path = MutableStateFlow<List<Folder>>(emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the user, such as the outcome of an import. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = path.flatMapLatest { path ->
        val folderId = path.lastOrNull()?.id
        combine(repository.folders(folderId), repository.runFiles(folderId)) { folders, files ->
            LibraryUiState(path, folders, files)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    fun openFolder(folder: Folder) {
        path.value += folder
    }

    /** Goes to the parent folder; returns false when already at the top level. */
    fun navigateUp(): Boolean {
        if (path.value.isEmpty()) return false
        path.value = path.value.dropLast(1)
        return true
    }

    fun createFolder(name: String) = launchWithName(name) {
        repository.createFolder(path.value.lastOrNull()?.id, it)
    }

    fun createRunFile(name: String) = launchWithName(name) {
        repository.createRunFile(path.value.lastOrNull()?.id, it)
    }

    fun renameFolder(id: Long, name: String) = launchWithName(name) { repository.renameFolder(id, it) }

    fun renameRunFile(id: Long, name: String) = launchWithName(name) { repository.renameRunFile(id, it) }

    fun deleteFolder(id: Long) {
        viewModelScope.launch { repository.deleteFolder(id) }
    }

    fun deleteRunFile(id: Long) {
        viewModelScope.launch { repository.deleteRunFile(id) }
    }

    /**
     * Imports a LiveSplit splits file as a new run file in the current folder. It is named after
     * the game and category, or after [fileName] when the file names neither.
     */
    fun importLss(fileName: String?, open: () -> InputStream) {
        val folderId = path.value.lastOrNull()?.id
        viewModelScope.launch {
            val message = try {
                val lss = withContext(ioDispatcher) { open().use(LssCodec::parse) }
                if (lss.segmentNames.isEmpty()) throw LssFormatException("The file has no segments")
                val name = listOf(lss.gameName, lss.categoryName).filter { it.isNotBlank() }.joinToString(" – ")
                    .ifEmpty { fileName?.substringBeforeLast('.')?.trim().orEmpty() }
                    .ifEmpty { "Imported" }
                importer.import(folderId, name, lss.segmentNames, lss.runs)
                "Imported \"$name\" with ${lss.runs.size} runs"
            } catch (e: LssFormatException) {
                "Import failed: ${e.message}"
            } catch (e: IOException) {
                "Import failed: the file could not be read"
            }
            _messages.send(message)
        }
    }

    /** Ignores blank names; trims the rest. */
    private fun launchWithName(name: String, action: suspend (String) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { action(trimmed) }
    }
}
