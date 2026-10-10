package com.grumpy_phoenix.speedrun_split_tracker.domain

import android.os.SystemClock

/** Monotonic time source in milliseconds. Swappable for tests. */
fun interface Clock {
    fun nowMs(): Long
}

/** Uses elapsedRealtime, which is monotonic and keeps counting during deep sleep. */
object SystemElapsedClock : Clock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()
}
