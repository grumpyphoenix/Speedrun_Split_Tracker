@file:OptIn(FlowPreview::class)

package com.grumpy_phoenix.speedrun_split_tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grumpy_phoenix.speedrun_split_tracker.data.RunRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.SegmentRepository
import com.grumpy_phoenix.speedrun_split_tracker.domain.Clock
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { Setup, Timer }

/** A segment name being edited; the id keeps list items stable while reordering. */
data class SegmentDraft(val id: Long, val name: String)

data class RunUiState(
    val screen: Screen = Screen.Setup,
    val drafts: List<SegmentDraft> = listOf(SegmentDraft(id = 0, name = "")),
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

    init {
        viewModelScope.launch {
            restoreSegments()
            // Persist edits once typing pauses, rather than on every keystroke.
            _state.map { s -> s.drafts.map { it.name } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(SEGMENT_SAVE_DEBOUNCE_MS)
                .collect { segmentRepository.save(it) }
        }
    }

    private suspend fun restoreSegments() {
        val names = segmentRepository.load()
        if (names.isEmpty()) return
        _state.update { s ->
            // Don't clobber edits made while loading.
            if (s.drafts != RunUiState().drafts) return@update s
            s.copy(drafts = names.map { SegmentDraft(nextDraftId++, it) })
        }
    }

    // Identity of the current attempt, so re-saving (e.g. after undoing the final split) replaces it.
    private var attemptId = UUID.randomUUID().toString()
    private var attemptStartedAtEpochMs = 0L

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
        if (!s.canStart) return
        val names = s.drafts.map { it.name }
        viewModelScope.launch { segmentRepository.save(names) }
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
        _state.update { it.copy(screen = Screen.Setup) }
    }

    private fun updateTimer(transform: (TimerState) -> TimerState) =
        _state.update { it.copy(timer = transform(it.timer)) }

    private fun saveAttempt() {
        val timer = _state.value.timer
        if (!timer.hasStarted) return
        val record = RunRecord(
            id = attemptId,
            startedAtEpochMs = attemptStartedAtEpochMs,
            segmentNames = timer.segments,
            splitTimesMs = timer.splitTimesMs,
            completed = timer.phase == TimerPhase.Finished,
        )
        viewModelScope.launch { repository.save(record) }
    }

    private companion object {
        const val SEGMENT_SAVE_DEBOUNCE_MS = 500L
    }
}
