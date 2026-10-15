package com.grumpy_phoenix.speedrun_split_tracker.cast

import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastMessageTest {

    private val segments = listOf("Tutorial", "Forest", "Final Boss")

    private fun decode(json: String) = Json.decodeFromString(CastMessage.serializer(), json)

    @Test
    fun idleWithoutTimerCarriesOnlyTitle() {
        val message = decode(castMessageJson("Celeste Any%", timer = null, nowMs = 5_000L))
        assertEquals(CastMessage("Celeste Any%", null, emptyList(), emptyList(), 0L), message)
    }

    @Test
    fun missingTitleBecomesEmpty() {
        assertEquals("", decode(castMessageJson(null, null, 0L)).title)
    }

    @Test
    fun notStartedSendsSegmentsAndZero() {
        val message = decode(castMessageJson("Run", TimerState(segments), nowMs = 9_000L))
        assertEquals(TimerPhase.NotStarted, message.phase)
        assertEquals(segments, message.segments)
        assertEquals(0L, message.elapsedMs)
    }

    @Test
    fun runningSendsElapsedAtSendTime() {
        val timer = TimerState(segments).start(1_000L).split(31_000L)
        val message = decode(castMessageJson("Run", timer, nowMs = 46_000L))
        assertEquals(TimerPhase.Running, message.phase)
        assertEquals(listOf(30_000L), message.splitTimesMs)
        assertEquals(45_000L, message.elapsedMs)
    }

    @Test
    fun pausedSendsFrozenElapsed() {
        val timer = TimerState(segments).start(0L).pause(20_000L)
        val message = decode(castMessageJson("Run", timer, nowMs = 90_000L))
        assertEquals(TimerPhase.Paused, message.phase)
        assertEquals(20_000L, message.elapsedMs)
    }

    @Test
    fun finishedSendsFinalTime() {
        val timer = TimerState(segments).start(0L).split(10_000L).split(20_000L).split(35_000L)
        val message = decode(castMessageJson("Run", timer, nowMs = 99_000L))
        assertEquals(TimerPhase.Finished, message.phase)
        assertEquals(35_000L, message.elapsedMs)
    }

    @Test
    fun specialCharactersSurviveTheRoundTrip() {
        val names = listOf("\"Quoted\" \\ back", "Grüße & Übermut", "Line\nbreak")
        val message = decode(castMessageJson("Ä \"Run\"", TimerState(names), nowMs = 0L))
        assertEquals("Ä \"Run\"", message.title)
        assertEquals(names, message.segments)
    }

    @Test
    fun phaseIsSentByName() {
        val json = castMessageJson("Run", TimerState(segments).start(0L), nowMs = 0L)
        assert(json.contains("\"phase\":\"Running\"")) { json }
        assertNull(decode(castMessageJson("Run", null, 0L)).phase)
    }
}
