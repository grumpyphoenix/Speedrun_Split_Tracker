package com.grumpy_phoenix.speedrun_split_tracker.domain

/** Formats as m:ss.cc, or h:mm:ss.cc from one hour up. Negative values are clamped to zero. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val centis = (total / 10) % 100
    val seconds = (total / 1_000) % 60
    val minutes = (total / 60_000) % 60
    val hours = total / 3_600_000
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
    } else {
        "%d:%02d.%02d".format(minutes, seconds, centis)
    }
}
