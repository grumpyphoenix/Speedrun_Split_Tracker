package com.grumpy_phoenix.speedrun_split_tracker.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grumpy_phoenix.speedrun_split_tracker.ui.SegmentDraft
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme

@Composable
fun SegmentSetupScreen(
    drafts: List<SegmentDraft>,
    canStart: Boolean,
    onAdd: () -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onRemove: (id: Long) -> Unit,
    onMove: (id: Long, offset: Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Set by "Add segment" so the new (last) row grabs focus once it is composed.
    var focusNewSegment by remember { mutableStateOf(false) }

    LaunchedEffect(drafts.size) {
        if (focusNewSegment && drafts.isNotEmpty()) listState.animateScrollToItem(drafts.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Segments", style = MaterialTheme.typography.headlineMedium)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(drafts, key = { _, draft -> draft.id }) { index, draft ->
                val focusRequester = remember { FocusRequester() }
                if (focusNewSegment && index == drafts.lastIndex) {
                    LaunchedEffect(draft.id) {
                        focusRequester.requestFocus()
                        focusNewSegment = false
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { onRename(draft.id, it) },
                        label = { Text("Segment ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    TextButton(onClick = { onMove(draft.id, -1) }, enabled = index > 0) {
                        Text("↑")
                    }
                    TextButton(
                        onClick = { onMove(draft.id, 1) },
                        enabled = index < drafts.lastIndex,
                    ) {
                        Text("↓")
                    }
                    TextButton(onClick = { onRemove(draft.id) }, enabled = drafts.size > 1) {
                        Text("✕")
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                focusNewSegment = true
                onAdd()
            },
            modifier = Modifier.fillMaxWidth()) {
            Text("Add segment")
        }
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Start")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentSetupScreenPreview() {
    Speedrun_Split_TrackerTheme {
        SegmentSetupScreen(
            drafts = listOf(
                SegmentDraft(0, "Tutorial"),
                SegmentDraft(1, "Forest"),
                SegmentDraft(2, "Final Boss"),
            ),
            canStart = true,
            onAdd = {},
            onRename = { _, _ -> },
            onRemove = {},
            onMove = { _, _ -> },
            onStart = {},
        )
    }
}
