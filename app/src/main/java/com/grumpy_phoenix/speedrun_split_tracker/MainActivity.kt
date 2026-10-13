package com.grumpy_phoenix.speedrun_split_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomLibraryRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomRunRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomSegmentRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.db.AppDatabase
import com.grumpy_phoenix.speedrun_split_tracker.domain.SystemElapsedClock
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.ui.RunViewModel
import com.grumpy_phoenix.speedrun_split_tracker.ui.Screen
import com.grumpy_phoenix.speedrun_split_tracker.ui.library.LibraryScreen
import com.grumpy_phoenix.speedrun_split_tracker.ui.library.LibraryViewModel
import com.grumpy_phoenix.speedrun_split_tracker.ui.runfile.RunFileScreen
import com.grumpy_phoenix.speedrun_split_tracker.ui.theme.Speedrun_Split_TrackerTheme
import com.grumpy_phoenix.speedrun_split_tracker.ui.timer.TimerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Speedrun_Split_TrackerTheme {
                val vm: RunViewModel = viewModel(factory = viewModelFactory)
                val libraryVm: LibraryViewModel = viewModel(factory = viewModelFactory)
                val state by vm.state.collectAsState()
                val library by libraryVm.state.collectAsState()
                // safeDrawing includes the keyboard, so content shrinks above it instead of hiding behind it.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    when (state.screen) {
                        Screen.Library -> {
                            BackHandler(enabled = library.path.isNotEmpty()) { libraryVm.navigateUp() }
                            LibraryScreen(
                                state = library,
                                onOpenFolder = libraryVm::openFolder,
                                onOpenRunFile = vm::openRunFile,
                                onNavigateUp = { libraryVm.navigateUp() },
                                onCreateFolder = libraryVm::createFolder,
                                onCreateRunFile = libraryVm::createRunFile,
                                onRenameFolder = libraryVm::renameFolder,
                                onRenameRunFile = libraryVm::renameRunFile,
                                onDeleteFolder = libraryVm::deleteFolder,
                                onDeleteRunFile = libraryVm::deleteRunFile,
                                modifier = contentModifier,
                            )
                        }

                        Screen.RunFile -> {
                            BackHandler(onBack = vm::closeRunFile)
                            RunFileScreen(
                                name = state.runFile?.name.orEmpty(),
                                drafts = state.drafts,
                                runs = state.runs,
                                canStart = state.canStart,
                                onBack = vm::closeRunFile,
                                onAddSegment = vm::addSegment,
                                onRenameSegment = vm::renameSegment,
                                onRemoveSegment = vm::removeSegment,
                                onMoveSegment = vm::moveSegment,
                                onStart = vm::startRun,
                                onDeleteRun = vm::deleteRun,
                                modifier = contentModifier,
                            )
                        }

                        Screen.Timer -> {
                            // Swallow back during a run so a stray gesture can't end the attempt.
                            BackHandler {
                                val phase = state.timer.phase
                                if (phase == TimerPhase.NotStarted || phase == TimerPhase.Finished) {
                                    vm.backToSetup()
                                }
                            }
                            TimerScreen(
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
    }

    private companion object {
        val viewModelFactory = viewModelFactory {
            initializer {
                val db = AppDatabase.get(checkNotNull(this[APPLICATION_KEY]))
                RunViewModel(
                    clock = SystemElapsedClock,
                    repository = RoomRunRepository(db.runDao()),
                    segmentRepository = RoomSegmentRepository(db.segmentDao()),
                )
            }
            initializer {
                val db = AppDatabase.get(checkNotNull(this[APPLICATION_KEY]))
                LibraryViewModel(RoomLibraryRepository(db.libraryDao()))
            }
        }
    }
}
