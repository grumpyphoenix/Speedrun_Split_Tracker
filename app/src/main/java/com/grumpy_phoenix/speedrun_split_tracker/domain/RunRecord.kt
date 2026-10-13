package com.grumpy_phoenix.speedrun_split_tracker.domain

import java.util.UUID

/**
 * One attempt, finished or not. Shaped so it can later become a Room entity
 * (split times as a child table or a converted column).
 */
data class RunRecord(
    val id: String = UUID.randomUUID().toString(),
    val startedAtEpochMs: Long,
    val segmentNames: List<String>,
    /** Cumulative split times; shorter than [segmentNames] when the run was reset early. */
    val splitTimesMs: List<Long>,
    val completed: Boolean,
) {
    /** Duration of the segment at [index], or null if the run never split it. */
    fun segmentDurationMs(index: Int): Long? {
        val end = splitTimesMs.getOrNull(index) ?: return null
        val begin = if (index == 0) 0L else splitTimesMs[index - 1]
        return end - begin
    }
}
