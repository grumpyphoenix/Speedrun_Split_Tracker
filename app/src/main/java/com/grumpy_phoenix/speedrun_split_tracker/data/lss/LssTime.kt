package com.grumpy_phoenix.speedrun_split_tracker.data.lss

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** LiveSplit attempt timestamps, always UTC. */
private val attemptDateFormat =
    DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.US).withZone(ZoneOffset.UTC)

/**
 * Parses a .NET TimeSpan as LiveSplit writes it: `[-][d.]hh:mm:ss[.fffffff]`.
 * Returns null for anything else.
 */
fun parseLssTime(text: String): Long? {
    val match = Regex("""(-)?(?:(\d+)\.)?(\d+):(\d+):(\d+)(?:\.(\d+))?""").matchEntire(text.trim()) ?: return null
    val (sign, days, hours, minutes, seconds, fraction) = match.destructured
    val fractionMs = fraction.padEnd(3, '0').take(3).toLong()
    val ms = ((days.ifEmpty { "0" }.toLong() * 24 + hours.toLong()) * 60 + minutes.toLong()) * 60_000 +
        seconds.toLong() * 1_000 + fractionMs
    return if (sign.isEmpty()) ms else -ms
}

/** Formats as `hh:mm:ss.fffffff`, with a `d.` prefix from one day up. */
fun formatLssTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val days = total / 86_400_000
    val hours = (total / 3_600_000) % 24
    val minutes = (total / 60_000) % 60
    val seconds = (total / 1_000) % 60
    val ticks = (total % 1_000) * 10_000
    val time = "%02d:%02d:%02d.%07d".format(Locale.US, hours, minutes, seconds, ticks)
    return if (days > 0) "$days.$time" else time
}

fun parseLssDate(text: String): Long? =
    runCatching { Instant.from(attemptDateFormat.parse(text.trim())).toEpochMilli() }.getOrNull()

fun formatLssDate(epochMs: Long): String = attemptDateFormat.format(Instant.ofEpochMilli(epochMs))
