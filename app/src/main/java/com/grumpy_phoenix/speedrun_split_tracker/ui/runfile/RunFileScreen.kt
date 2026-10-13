package com.grumpy_phoenix.speedrun_split_tracker.ui.runfile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import com.grumpy_phoenix.speedrun_split_tracker.domain.formatTime
import com.grumpy_phoenix.speedrun_split_tracker.ui.SegmentDraft
import com.grumpy_phoenix.speedrun_split_tracker.ui.setup.SegmentEditor
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val startedAtFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

/** A run file: its editable segments and the runs recorded with it. */
@Composable
fun RunFileScreen(
    name: String,
    drafts: List<SegmentDraft>,
    runs: List<RunRecord>,
    canStart: Boolean,
    onBack: () -> Unit,
    onAddSegment: () -> Unit,
    onRenameSegment: (id: Long, name: String) -> Unit,
    onRemoveSegment: (id: Long) -> Unit,
    onMoveSegment: (id: Long, offset: Int) -> Unit,
    onStart: () -> Unit,
    onDeleteRun: (runId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Segments") })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Runs (${runs.size})") },
            )
        }
        val contentModifier = Modifier.weight(1f).padding(top = 8.dp)
        if (selectedTab == 0) {
            SegmentEditor(
                drafts = drafts,
                onAdd = onAddSegment,
                onRename = onRenameSegment,
                onRemove = onRemoveSegment,
                onMove = onMoveSegment,
                modifier = contentModifier,
            )
        } else {
            RunHistoryPager(runs = runs, onDeleteRun = onDeleteRun, modifier = contentModifier)
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

/** Swipe through the recorded runs, newest first. */
@Composable
private fun RunHistoryPager(
    runs: List<RunRecord>,
    onDeleteRun: (runId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (runs.isEmpty()) {
        Text(
            text = "No runs yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp),
        )
        return
    }
    val pagerState = rememberPagerState { runs.size }
    Column(modifier = modifier) {
        Text(
            text = "Run ${runs.size - pagerState.currentPage} of ${runs.size}  ·  swipe for older runs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalPager(
            state = pagerState,
            key = { runs[it].id },
            modifier = Modifier.weight(1f).padding(top = 8.dp),
        ) { page ->
            RunPage(runs[page], onDelete = { onDeleteRun(runs[page].id) })
        }
    }
}

@Composable
private fun RunPage(run: RunRecord, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val startedAt = Instant.ofEpochMilli(run.startedAtEpochMs).atZone(ZoneId.systemDefault())
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = startedAtFormatter.format(startedAt),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        if (run.completed) {
            Text(
                text = formatTime(run.splitTimesMs.lastOrNull() ?: 0L),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = "Reset – ${run.splitTimesMs.size}/${run.segmentNames.size} segments",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            itemsIndexed(run.segmentNames) { index, segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(segment, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = run.segmentDurationMs(index)?.let(::formatTime) ?: "-",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text(
                        text = run.splitTimesMs.getOrNull(index)?.let(::formatTime) ?: "-",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this run?") },
            text = { Text("Started ${startedAtFormatter.format(startedAt)}. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RunPagePreview() {
    Speedrun_Split_TrackerTheme {
        RunPage(
            RunRecord(
                startedAtEpochMs = 1_791_640_800_000,
                segmentNames = listOf("Tutorial", "Forest", "Final Boss"),
                splitTimesMs = listOf(41_200, 95_430),
                completed = false,
            ),
            onDelete = {},
        )
    }
}
