@file:OptIn(FlowPreview::class)

package com.grumpy_phoenix.speedrun_split_tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grumpy_phoenix.speedrun_split_tracker.data.RunRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.SegmentRepository
import com.grumpy_phoenix.speedrun_split_tracker.domain.Clock
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { Library, RunFile, Timer }

/** A segment name being edited; the id keeps list items stable while reordering. */
data class SegmentDraft(val id: Long, val name: String)

data class RunUiState(
    val screen: Screen = Screen.Library,
    /** The run file being edited or run; null while browsing the library. */
    val runFile: RunFile? = null,
    val drafts: List<SegmentDraft> = listOf(SegmentDraft(id = 0, name = "")),
    /** Stored attempts of [runFile], newest first. */
    val runs: List<RunRecord> = emptyList(),
    val timer: TimerState = TimerState(segments = emptyList()),
) {
    val canStart: Boolean get() = drafts.any { it.name.isNotBlank() }
}

class RunViewModel(
    val clock: Clock,
    private val repository: RunRepository,
    private val segmentRepository: SegmentRepository,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    private var nextDraftId = 1L
    private var runsJob: Job? = null

    init {
        viewModelScope.launch {
            // Persist edits once typing pauses, rather than on every keystroke.
            // runFile and drafts always change together, so a pair never mixes two files.
            _state.mapNotNull { s -> s.runFile?.let { it.id to s.drafts.map { d -> d.name } } }
                .distinctUntilChanged()
                .debounce(SEGMENT_SAVE_DEBOUNCE_MS)
                .collect { (runFileId, names) -> segmentRepository.save(runFileId, names) }
        }
    }

    // Identity of the current attempt, so re-saving (e.g. after undoing the final split) replaces it.
    private var attemptId = UUID.randomUUID().toString()
    private var attemptStartedAtEpochMs = 0L

    // --- Run file ---

    fun openRunFile(runFile: RunFile) {
        viewModelScope.launch {
            val names = segmentRepository.load(runFile.id).ifEmpty { listOf("") }
            _state.update {
                it.copy(
                    screen = Screen.RunFile,
                    runFile = runFile,
                    drafts = names.map { name -> SegmentDraft(nextDraftId++, name) },
                )
            }
        }
        runsJob?.cancel()
        runsJob = viewModelScope.launch {
            repository.runs(runFile.id).collect { runs -> _state.update { it.copy(runs = runs) } }
        }
    }

    /** Saves pending segment edits right away and returns to the library. */
    fun closeRunFile() {
        val s = _state.value
        s.runFile?.let { file ->
            val names = s.drafts.map { it.name }
            viewModelScope.launch { segmentRepository.save(file.id, names) }
        }
        runsJob?.cancel()
        _state.value = RunUiState()
    }

    fun deleteRun(runId: String) {
        viewModelScope.launch { repository.delete(runId) }
    }

    // --- Segment setup ---

    fun addSegment() = _state.update {
        it.copy(drafts = it.drafts + SegmentDraft(nextDraftId++, ""))
    }

    fun renameSegment(id: Long, name: String) = _state.update { s ->
        s.copy(drafts = s.drafts.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun removeSegment(id: Long) = _state.update { s ->
        s.copy(drafts = s.drafts.filterNot { it.id == id })
    }

    fun moveSegment(id: Long, offset: Int) = _state.update { s ->
        val from = s.drafts.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in s.drafts.indices) return@update s
        val reordered = s.drafts.toMutableList().apply { add(to, removeAt(from)) }
        s.copy(drafts = reordered)
    }

    fun startRun() {
        val s = _state.value
        val file = s.runFile ?: return
        if (!s.canStart) return
        val names = s.drafts.map { it.name }
        viewModelScope.launch { segmentRepository.save(file.id, names) }
        val segments = names.map { it.trim() }.filter { it.isNotEmpty() }
        _state.update { it.copy(screen = Screen.Timer, timer = TimerState(segments)) }
    }

    // --- Timer ---

    fun startTimer() {
        if (_state.value.timer.phase != TimerPhase.NotStarted) return
        attemptId = UUID.randomUUID().toString()
        attemptStartedAtEpochMs = wallClockMs()
        updateTimer { it.start(clock.nowMs()) }
    }

    fun split() {
        updateTimer { it.split(clock.nowMs()) }
        if (_state.value.timer.phase == TimerPhase.Finished) saveAttempt()
    }

    fun undoSplit() = updateTimer { it.undoSplit() }

    fun pause() = updateTimer { it.pause(clock.nowMs()) }

    fun resume() = updateTimer { it.resume(clock.nowMs()) }

    /** Ends the current attempt (recording it if it was started) and readies a new one. */
    fun reset() {
        saveAttempt()
        updateTimer { it.reset() }
    }

    fun backToSetup() {
        reset()
        _state.update { it.copy(screen = Screen.RunFile) }
    }

    private fun updateTimer(transform: (TimerState) -> TimerState) =
        _state.update { it.copy(timer = transform(it.timer)) }

    private fun saveAttempt() {
        val s = _state.value
        val file = s.runFile ?: return
        val timer = s.timer
        if (!timer.hasStarted) return
        val record = RunRecord(
            id = attemptId,
            startedAtEpochMs = attemptStartedAtEpochMs,
            segmentNames = timer.segments,
            splitTimesMs = timer.splitTimesMs,
            completed = timer.phase == TimerPhase.Finished,
        )
        viewModelScope.launch { repository.save(file.id, record) }
    }

    private companion object {
        const val SEGMENT_SAVE_DEBOUNCE_MS = 500L
    }
}
