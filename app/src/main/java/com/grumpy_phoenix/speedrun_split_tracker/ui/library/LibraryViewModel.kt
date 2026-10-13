@file:OptIn(ExperimentalCoroutinesApi::class)

package com.grumpy_phoenix.speedrun_split_tracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grumpy_phoenix.speedrun_split_tracker.data.LibraryRepository
import com.grumpy_phoenix.speedrun_split_tracker.domain.Folder
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** Folders from the top level down to the one being shown; empty at the top level. */
    val path: List<Folder> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val runFiles: List<RunFile> = emptyList(),
) {
    val currentFolderId: Long? get() = path.lastOrNull()?.id
}

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val path = MutableStateFlow<List<Folder>>(emptyList())

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

    /** Ignores blank names; trims the rest. */
    private fun launchWithName(name: String, action: suspend (String) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { action(trimmed) }
    }
}
