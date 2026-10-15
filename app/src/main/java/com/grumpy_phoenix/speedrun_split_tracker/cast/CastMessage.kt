package com.grumpy_phoenix.speedrun_split_tracker.cast

import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Custom channel between the app and the TV receiver (docs/receiver/index.html). */
const val CAST_NAMESPACE = "urn:x-cast:com.grumpy_phoenix.speedrun_split_tracker"

/**
 * Snapshot of what the TV shows. The receiver keeps counting from [elapsedMs] on its own while
 * [phase] is Running, so the phone only sends when something changes.
 */
@Serializable
data class CastMessage(
    val title: String,
    /** Null while no timer is open; the receiver then shows only the title. */
    val phase: TimerPhase?,
    val segments: List<String>,
    val splitTimesMs: List<Long>,
    val elapsedMs: Long,
)

private val json = Json { encodeDefaults = true }

fun castMessageJson(title: String?, timer: TimerState?, nowMs: Long): String =
    json.encodeToString(
        CastMessage.serializer(),
        CastMessage(
            title = title.orEmpty(),
            phase = timer?.phase,
            segments = timer?.segments.orEmpty(),
            splitTimesMs = timer?.splitTimesMs.orEmpty(),
            elapsedMs = timer?.elapsedMs(nowMs) ?: 0L,
        ),
    )
