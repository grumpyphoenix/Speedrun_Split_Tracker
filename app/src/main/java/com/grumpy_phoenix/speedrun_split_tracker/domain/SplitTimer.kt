package com.grumpy_phoenix.speedrun_split_tracker.domain

enum class TimerPhase { NotStarted, Running, Paused, Finished }

/**
 * Immutable state of a single attempt. All transitions return a new state;
 * transitions that are invalid for the current phase return the state unchanged.
 */
data class TimerState(
    val segments: List<String>,
    val phase: TimerPhase = TimerPhase.NotStarted,
    val startMs: Long = 0L,
    val pausedTotalMs: Long = 0L,
    val pauseStartMs: Long? = null,
    /** Cumulative run time at the end of each finished segment. */
    val splitTimesMs: List<Long> = emptyList(),
) {
    val hasStarted: Boolean get() = phase != TimerPhase.NotStarted

    /** Index of the segment currently being run; equals segments.size once finished. */
    val currentSegmentIndex: Int get() = splitTimesMs.size

    fun elapsedMs(nowMs: Long): Long = when (phase) {
        TimerPhase.NotStarted -> 0L
        TimerPhase.Running -> nowMs - startMs - pausedTotalMs
        TimerPhase.Paused -> (pauseStartMs ?: nowMs) - startMs - pausedTotalMs
        TimerPhase.Finished -> splitTimesMs.lastOrNull() ?: 0L
    }

    /** Duration of the segment at [index], or null if it has not been split yet. */
    fun segmentDurationMs(index: Int): Long? {
        val end = splitTimesMs.getOrNull(index) ?: return null
        val begin = if (index == 0) 0L else splitTimesMs[index - 1]
        return end - begin
    }

    fun start(nowMs: Long): TimerState =
        if (phase == TimerPhase.NotStarted && segments.isNotEmpty()) {
            copy(phase = TimerPhase.Running, startMs = nowMs)
        } else this

    fun split(nowMs: Long): TimerState {
        if (phase != TimerPhase.Running) return this
        val splits = splitTimesMs + elapsedMs(nowMs)
        val nextPhase = if (splits.size >= segments.size) TimerPhase.Finished else TimerPhase.Running
        return copy(splitTimesMs = splits, phase = nextPhase)
    }

    /** Removes the last split. Undoing the final split resumes the run as if it never stopped. */
    fun undoSplit(): TimerState = when {
        splitTimesMs.isEmpty() -> this
        phase == TimerPhase.Running || phase == TimerPhase.Paused ->
            copy(splitTimesMs = splitTimesMs.dropLast(1))
        phase == TimerPhase.Finished ->
            copy(splitTimesMs = splitTimesMs.dropLast(1), phase = TimerPhase.Running)
        else -> this
    }

    fun pause(nowMs: Long): TimerState =
        if (phase == TimerPhase.Running) {
            copy(phase = TimerPhase.Paused, pauseStartMs = nowMs)
        } else this

    fun resume(nowMs: Long): TimerState {
        if (phase != TimerPhase.Paused) return this
        val pausedFor = nowMs - (pauseStartMs ?: nowMs)
        return copy(
            phase = TimerPhase.Running,
            pausedTotalMs = pausedTotalMs + pausedFor,
            pauseStartMs = null,
        )
    }

    fun reset(): TimerState = TimerState(segments)
}
