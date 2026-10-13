package com.grumpy_phoenix.speedrun_split_tracker.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grumpy_phoenix.speedrun_split_tracker.domain.Folder
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme

/** A dialog the library screen is showing. */
private sealed interface LibraryDialog {
    data object NewFolder : LibraryDialog
    data object NewRunFile : LibraryDialog
    data class RenameFolder(val folder: Folder) : LibraryDialog
    data class RenameRunFile(val runFile: RunFile) : LibraryDialog
    data class DeleteFolder(val folder: Folder) : LibraryDialog
    data class DeleteRunFile(val runFile: RunFile) : LibraryDialog
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenFolder: (Folder) -> Unit,
    onOpenRunFile: (RunFile) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateRunFile: (String) -> Unit,
    onRenameFolder: (id: Long, name: String) -> Unit,
    onRenameRunFile: (id: Long, name: String) -> Unit,
    onDeleteFolder: (id: Long) -> Unit,
    onDeleteRunFile: (id: Long) -> Unit,
    onImportLss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val dismiss = { dialog = null }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.path.isNotEmpty()) {
                TextButton(onClick = onNavigateUp) { Text("←") }
            }
            Text(
                text = state.path.lastOrNull()?.name ?: "Library",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onImportLss) { Text("Import LSS") }
        }
        if (state.path.size > 1) {
            Text(
                text = state.path.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(state.folders, key = { "folder-${it.id}" }) { folder ->
                LibraryRow(
                    icon = "📁",
                    name = folder.name,
                    onClick = { onOpenFolder(folder) },
                    onRename = { dialog = LibraryDialog.RenameFolder(folder) },
                    onDelete = { dialog = LibraryDialog.DeleteFolder(folder) },
                )
            }
            items(state.runFiles, key = { "file-${it.id}" }) { runFile ->
                LibraryRow(
                    icon = "⏱",
                    name = runFile.name,
                    onClick = { onOpenRunFile(runFile) },
                    onRename = { dialog = LibraryDialog.RenameRunFile(runFile) },
                    onDelete = { dialog = LibraryDialog.DeleteRunFile(runFile) },
                )
            }
            if (state.folders.isEmpty() && state.runFiles.isEmpty()) {
                item {
                    Text(
                        text = "Empty. Create a folder or a run file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { dialog = LibraryDialog.NewFolder },
                modifier = Modifier.weight(1f),
            ) {
                Text("New folder")
            }
            OutlinedButton(
                onClick = { dialog = LibraryDialog.NewRunFile },
                modifier = Modifier.weight(1f),
            ) {
                Text("New run file")
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LibraryDialog.NewFolder -> NameDialog("New folder", "", dismiss) { onCreateFolder(it) }
        LibraryDialog.NewRunFile -> NameDialog("New run file", "", dismiss) { onCreateRunFile(it) }
        is LibraryDialog.RenameFolder ->
            NameDialog("Rename folder", d.folder.name, dismiss) { onRenameFolder(d.folder.id, it) }
        is LibraryDialog.RenameRunFile ->
            NameDialog("Rename run file", d.runFile.name, dismiss) { onRenameRunFile(d.runFile.id, it) }
        is LibraryDialog.DeleteFolder -> ConfirmDeleteDialog(
            title = "Delete folder \"${d.folder.name}\"?",
            text = "All subfolders, run files and their runs inside it are deleted too.",
            onDismiss = dismiss,
            onConfirm = { onDeleteFolder(d.folder.id) },
        )
        is LibraryDialog.DeleteRunFile -> ConfirmDeleteDialog(
            title = "Delete run file \"${d.runFile.name}\"?",
            text = "Its segments and all recorded runs are deleted too.",
            onDismiss = dismiss,
            onConfirm = { onDeleteRunFile(d.runFile.id) },
        )
    }
}

@Composable
private fun LibraryRow(
    icon: String,
    name: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp),
    ) {
        Text(icon, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("⋮") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(0, initialName.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.text)
                    onDismiss()
                },
                enabled = name.text.isNotBlank(),
            ) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    Speedrun_Split_TrackerTheme {
        LibraryScreen(
            state = LibraryUiState(
                path = listOf(Folder(1, null, "Super Mario 64")),
                folders = listOf(Folder(2, 1, "Practice")),
                runFiles = listOf(RunFile(3, 1, "16 Star"), RunFile(4, 1, "70 Star")),
            ),
            onOpenFolder = {},
            onOpenRunFile = {},
            onNavigateUp = {},
            onCreateFolder = {},
            onCreateRunFile = {},
            onRenameFolder = { _, _ -> },
            onRenameRunFile = { _, _ -> },
            onDeleteFolder = {},
            onDeleteRunFile = {},
            onImportLss = {},
        )
    }
}
