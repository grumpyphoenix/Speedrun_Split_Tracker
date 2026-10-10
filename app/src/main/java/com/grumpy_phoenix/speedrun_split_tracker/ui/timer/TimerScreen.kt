package com.grumpy_phoenix.speedrun_split_tracker.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import com.grumpy_phoenix.speedrun_split_tracker.domain.formatTime
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme

@Composable
fun TimerScreen(
    timer: TimerState,
    nowMs: () -> Long,
    onStart: () -> Unit,
    onSplit: () -> Unit,
    onUndo: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onEditSegments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recompute every frame while running; otherwise the time is static.
    val elapsedMs by produceState(timer.elapsedMs(nowMs()), timer) {
        value = timer.elapsedMs(nowMs())
        if (timer.phase == TimerPhase.Running) {
            while (true) {
                withFrameMillis { value = timer.elapsedMs(nowMs()) }
            }
        }
    }

    // Keep the display awake while a run is in progress.
    val view = LocalView.current
    val runInProgress = timer.phase == TimerPhase.Running || timer.phase == TimerPhase.Paused
    DisposableEffect(view, runInProgress) {
        view.keepScreenOn = runInProgress
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = formatTime(elapsedMs),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            textAlign = TextAlign.End,
            color = if (timer.phase == TimerPhase.Paused) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
            itemsIndexed(timer.segments) { index, name ->
                SegmentRow(
                    name = name,
                    splitMs = timer.splitTimesMs.getOrNull(index),
                    segmentMs = timer.segmentDurationMs(index)
                        ?: liveSegmentMs(timer, index, elapsedMs),
                    isCurrent = timer.hasStarted && index == timer.currentSegmentIndex,
                )
            }
        }

        TimerControls(
            phase = timer.phase,
            canUndo = timer.splitTimesMs.isNotEmpty(),
            onStart = onStart,
            onSplit = onSplit,
            onUndo = onUndo,
            onPause = onPause,
            onResume = onResume,
            onReset = onReset,
            onEditSegments = onEditSegments,
        )
    }
}

/** Running time of the in-progress segment; null for segments not reached yet. */
private fun liveSegmentMs(timer: TimerState, index: Int, elapsedMs: Long): Long? {
    if (!timer.hasStarted || index != timer.currentSegmentIndex) return null
    return elapsedMs - (timer.splitTimesMs.lastOrNull() ?: 0L)
}

@Composable
private fun SegmentRow(name: String, splitMs: Long?, segmentMs: Long?, isCurrent: Boolean) {
    val background = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = segmentMs?.let(::formatTime) ?: "-",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            text = splitMs?.let(::formatTime) ?: "-",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TimerControls(
    phase: TimerPhase,
    canUndo: Boolean,
    onStart: () -> Unit,
    onSplit: () -> Unit,
    onUndo: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onEditSegments: () -> Unit,
) {
    val bigButton = Modifier.fillMaxWidth().height(96.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (phase) {
            TimerPhase.NotStarted -> {
                Button(onClick = onStart, modifier = bigButton) { Text("Start", fontSize = 24.sp) }
                OutlinedButton(onClick = onEditSegments, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit segments")
                }
            }

            TimerPhase.Running, TimerPhase.Paused -> {
                Button(
                    onClick = onSplit,
                    enabled = phase == TimerPhase.Running,
                    modifier = bigButton,
                ) {
                    Text("Split", fontSize = 24.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.weight(1f)) {
                        Text("Undo")
                    }
                    if (phase == TimerPhase.Running) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Text("Pause")
                        }
                    } else {
                        OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Text("Resume")
                        }
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                        Text("Reset")
                    }
                }
            }

            TimerPhase.Finished -> {
                Button(onClick = onReset, modifier = bigButton) { Text("New attempt", fontSize = 24.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                        Text("Undo split")
                    }
                    OutlinedButton(onClick = onEditSegments, modifier = Modifier.weight(1f)) {
                        Text("Edit segments")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerScreenPreview() {
    val timer = TimerState(
        segments = listOf("Tutorial", "Forest", "Final Boss"),
        phase = TimerPhase.Paused,
        startMs = 0L,
        pauseStartMs = 95_430L,
        splitTimesMs = listOf(41_200L),
    )
    Speedrun_Split_TrackerTheme {
        TimerScreen(
            timer = timer,
            nowMs = { 95_430L },
            onStart = {},
            onSplit = {},
            onUndo = {},
            onPause = {},
            onResume = {},
            onReset = {},
            onEditSegments = {},
        )
    }
}
