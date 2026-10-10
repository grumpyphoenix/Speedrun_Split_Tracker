package com.grumpy_phoenix.speedrun_split_tracker.data.db

import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class RunMappingTest {

    private fun roundTrip(record: RunRecord) =
        RunWithSplits(record.toEntity(), record.toSplitEntities().reversed()).toRecord()

    @Test
    fun completedRunSurvivesRoundTrip() {
        val record = RunRecord(
            id = "a",
            startedAtEpochMs = 1_000,
            segmentNames = listOf("Tutorial", "Forest", "Boss"),
            splitTimesMs = listOf(10, 25, 40),
            completed = true,
        )
        assertEquals(record, roundTrip(record))
    }

    @Test
    fun resetRunKeepsAllSegmentsButOnlyReachedSplits() {
        val record = RunRecord(
            id = "b",
            startedAtEpochMs = 2_000,
            segmentNames = listOf("Tutorial", "Forest", "Boss"),
            splitTimesMs = listOf(10),
            completed = false,
        )
        val splits = record.toSplitEntities()
        assertEquals(listOf(10L, null, null), splits.map { it.splitTimeMs })
        assertEquals(record, roundTrip(record))
    }
}
