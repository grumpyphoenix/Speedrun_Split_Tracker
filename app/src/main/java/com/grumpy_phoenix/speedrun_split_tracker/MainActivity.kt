package com.grumpy_phoenix.speedrun_split_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomRunRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomSegmentRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.db.AppDatabase
import com.grumpy_phoenix.speedrun_split_tracker.domain.SystemElapsedClock
import com.grumpy_phoenix.speedrun_split_tracker.ui.RunViewModel
import com.grumpy_phoenix.speedrun_split_tracker.ui.Screen
import com.grumpy_phoenix.speedrun_split_tracker.ui.setup.SegmentSetupScreen
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme
import com.grumpy_phoenix.speedrun_split_tracker.ui.timer.TimerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Speedrun_Split_TrackerTheme {
                val vm: RunViewModel = viewModel(factory = runViewModelFactory)
                val state by vm.state.collectAsState()
                // safeDrawing includes the keyboard, so content shrinks above it instead of hiding behind it.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    when (state.screen) {
                        Screen.Setup -> SegmentSetupScreen(
                            drafts = state.drafts,
                            canStart = state.canStart,
                            onAdd = vm::addSegment,
                            onRename = vm::renameSegment,
                            onRemove = vm::removeSegment,
                            onMove = vm::moveSegment,
                            onStart = vm::startRun,
                            modifier = contentModifier,
                        )

                        Screen.Timer -> TimerScreen(
                            timer = state.timer,
                            nowMs = vm.clock::nowMs,
                            onStart = vm::startTimer,
                            onSplit = vm::split,
                            onUndo = vm::undoSplit,
                            onPause = vm::pause,
                            onResume = vm::resume,
                            onReset = vm::reset,
                            onEditSegments = vm::backToSetup,
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val runViewModelFactory = viewModelFactory {
            initializer {
                val db = AppDatabase.get(checkNotNull(this[APPLICATION_KEY]))
                RunViewModel(
                    clock = SystemElapsedClock,
                    repository = RoomRunRepository(db.runDao()),
                    segmentRepository = RoomSegmentRepository(db.segmentDao()),
                )
            }
        }
    }
}
