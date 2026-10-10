package com.grumpy_phoenix.speedrun_split_tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun formatsUnderAnHour() {
        assertEquals("0:00.00", formatTime(0))
        assertEquals("0:01.23", formatTime(1_234))
        assertEquals("12:34.56", formatTime(754_560))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:00:00.00", formatTime(3_600_000))
        assertEquals("2:03:04.05", formatTime(7_384_050))
    }

    @Test
    fun clampsNegativeToZero() {
        assertEquals("0:00.00", formatTime(-500))
    }
}
