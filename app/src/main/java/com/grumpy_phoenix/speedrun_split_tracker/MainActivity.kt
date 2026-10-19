package com.grumpy_phoenix.speedrun_split_tracker

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.grumpy_phoenix.speedrun_split_tracker.cast.TimerCaster
import com.grumpy_phoenix.speedrun_split_tracker.ui.cast.CastButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grumpy_phoenix.speedrun_split_tracker.data.PrefsActiveAttemptStore
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomLibraryRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.RoomRunFileImporter
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
import kotlinx.coroutines.flow.merge
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// FragmentActivity because the Cast button opens its device picker as a DialogFragment.
class MainActivity : FragmentActivity() {
    private lateinit var caster: TimerCaster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        caster = TimerCaster(this, SystemElapsedClock, lifecycleScope)
        setContent {
            Speedrun_Split_TrackerTheme {
                val vm: RunViewModel = viewModel(factory = viewModelFactory)
                val libraryVm: LibraryViewModel = viewModel(factory = viewModelFactory)
                val state by vm.state.collectAsState()
                val library by libraryVm.state.collectAsState()
                val castAvailable by caster.available.collectAsState()
                LaunchedEffect(vm) {
                    vm.state
                        .map { s -> s.runFile?.name to s.timer.takeIf { s.screen == Screen.Timer } }
                        .distinctUntilChanged()
                        .collect { (title, timer) -> caster.publish(title, timer) }
                }
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(vm, libraryVm) {
                    merge(vm.messages, libraryVm.messages).collect { snackbarHostState.showSnackbar(it) }
                }
                val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
                    if (uri != null) libraryVm.importLss(displayName(uri)) { openInput(uri) }
                }
                // octet-stream, because providers append ".xml" to an "application/xml" document.
                val exportLauncher = rememberLauncherForActivityResult(CreateDocument("application/octet-stream")) { uri ->
                    if (uri != null) vm.exportLss { openOutput(uri) }
                }
                // safeDrawing includes the keyboard, so content shrinks above it instead of hiding behind it.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                onImportLss = { importLauncher.launch(arrayOf("*/*")) },
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
                                onExportLss = {
                                    val name = state.runFile?.name.orEmpty().ifBlank { "splits" }
                                    exportLauncher.launch(name.replace(Regex("""[\\/:*?"<>|]"""), "_") + ".lss")
                                },
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
                                topBarAction = { if (castAvailable) CastButton() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        caster.release()
        super.onDestroy()
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun openInput(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    // "wt" truncates, so overwriting a longer file leaves no stale tail behind.
    private fun openOutput(uri: Uri): OutputStream =
        contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")

    private companion object {
        val viewModelFactory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = AppDatabase.get(app)
                RunViewModel(
                    clock = SystemElapsedClock,
                    repository = RoomRunRepository(db.runDao()),
                    segmentRepository = RoomSegmentRepository(db.segmentDao()),
                    activeAttemptStore = PrefsActiveAttemptStore(app),
                )
            }
            initializer {
                val db = AppDatabase.get(checkNotNull(this[APPLICATION_KEY]))
                LibraryViewModel(RoomLibraryRepository(db.libraryDao()), RoomRunFileImporter(db))
            }
        }
    }
}
