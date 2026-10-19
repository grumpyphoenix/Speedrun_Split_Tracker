package com.grumpy_phoenix.speedrun_split_tracker.data

import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAttemptCodecTest {

    private val attempt = ActiveAttempt(
        runFile = RunFile(id = 3, folderId = 9, name = "Hollow Knight"),
        attemptId = "abc",
        startedAtEpochMs = 1_700_000_000_000L,
        timer = TimerState(
            segments = listOf("Crossroads", "Hornet"),
            phase = TimerPhase.Paused,
            startMs = 500L,
            pausedTotalMs = 2_000L,
            pauseStartMs = 9_000L,
            splitTimesMs = listOf(4_000L),
        ),
    )

    @Test
    fun roundTripsOnTheSameBoot() {
        val saved = decodeActiveAttempt(encodeActiveAttempt(attempt, bootCount = 12), currentBootCount = 12)
        assertEquals(SavedAttempt(attempt, sameBoot = true), saved)
    }

    @Test
    fun otherBootMarksTheClockAsInvalid() {
        val saved = decodeActiveAttempt(encodeActiveAttempt(attempt, bootCount = 12), currentBootCount = 13)
        assertEquals(attempt, saved!!.attempt)
        assertFalse(saved.sameBoot)
    }

    @Test
    fun unknownBootCountIsNotTrusted() {
        val saved = decodeActiveAttempt(encodeActiveAttempt(attempt, bootCount = -1), currentBootCount = -1)
        assertFalse(saved!!.sameBoot)
    }

    @Test
    fun topLevelRunFileKeepsNullFolder() {
        val topLevel = attempt.copy(runFile = RunFile(id = 1, folderId = null, name = "x"))
        val saved = decodeActiveAttempt(encodeActiveAttempt(topLevel, 1), 1)
        assertTrue(saved!!.sameBoot)
        assertNull(saved.attempt.runFile.folderId)
    }

    @Test
    fun unreadableDataIsIgnored() {
        assertNull(decodeActiveAttempt("not json", 1))
        assertNull(decodeActiveAttempt("""{"bootCount":1}""", 1))
    }
}
