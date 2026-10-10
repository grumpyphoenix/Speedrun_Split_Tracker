package com.grumpy_phoenix.speedrun_split_tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SplitTimerTest {
    private val segments = listOf("A", "B", "C")

    @Test
    fun notStarted_elapsedIsZero_andSplitIsNoOp() {
        val state = TimerState(segments)
        assertEquals(0L, state.elapsedMs(5_000))
        assertSame(state, state.split(5_000))
    }

    @Test
    fun running_elapsedCountsFromStart() {
        val state = TimerState(segments).start(1_000)
        assertEquals(TimerPhase.Running, state.phase)
        assertEquals(2_500L, state.elapsedMs(3_500))
    }

    @Test
    fun splits_recordCumulativeTimes_andSegmentDurations() {
        val state = TimerState(segments).start(0).split(10_000).split(25_000)
        assertEquals(listOf(10_000L, 25_000L), state.splitTimesMs)
        assertEquals(15_000L, state.segmentDurationMs(1))
        assertEquals(null, state.segmentDurationMs(2))
        assertEquals(2, state.currentSegmentIndex)
    }

    @Test
    fun lastSplit_finishesRun_andFreezesTime() {
        val state = TimerState(segments).start(0).split(1_000).split(2_000).split(3_000)
        assertEquals(TimerPhase.Finished, state.phase)
        assertEquals(3_000L, state.elapsedMs(99_000))
        assertSame(state, state.split(4_000))
    }

    @Test
    fun undoSplit_removesLastSplit() {
        val state = TimerState(segments).start(0).split(1_000).split(2_000).undoSplit()
        assertEquals(listOf(1_000L), state.splitTimesMs)
        assertEquals(TimerPhase.Running, state.phase)
    }

    @Test
    fun undoFinalSplit_resumesRunWithoutLosingTime() {
        val state = TimerState(segments).start(0).split(1_000).split(2_000).split(3_000).undoSplit()
        assertEquals(TimerPhase.Running, state.phase)
        assertEquals(5_000L, state.elapsedMs(5_000))
    }

    @Test
    fun pause_excludesPausedTimeFromElapsed() {
        val paused = TimerState(segments).start(0).pause(4_000)
        assertEquals(4_000L, paused.elapsedMs(9_000))
        assertSame(paused, paused.split(9_000))

        val resumed = paused.resume(9_000)
        assertEquals(6_000L, resumed.elapsedMs(11_000))
        assertEquals(listOf(6_000L), resumed.split(11_000).splitTimesMs)
    }

    @Test
    fun reset_returnsToNotStartedWithSameSegments() {
        val state = TimerState(segments).start(0).split(1_000).reset()
        assertEquals(TimerState(segments), state)
    }

    @Test
    fun emptySegments_cannotStart() {
        val state = TimerState(emptyList())
        assertSame(state, state.start(0))
    }
}
