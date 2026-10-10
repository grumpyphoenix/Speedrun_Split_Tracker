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
)
