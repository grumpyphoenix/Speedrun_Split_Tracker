package com.grumpy_phoenix.speedrun_split_tracker.data.lss

import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LssCodecTest {

    private fun sample() = checkNotNull(javaClass.classLoader?.getResourceAsStream("livesplit-sample.lss"))

    private fun parse(xml: String) = LssCodec.parse(ByteArrayInputStream(xml.toByteArray()))

    private fun roundTrip(run: LssRun): LssRun {
        val out = ByteArrayOutputStream()
        LssCodec.write(run, out)
        return LssCodec.parse(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun parsesNamesAndSegments() {
        val lss = sample().use(LssCodec::parse)
        assertEquals("Super Mario 64", lss.gameName)
        assertEquals("16 Star", lss.categoryName)
        assertEquals(listOf("BoB", "WF & Friends", "Bowser"), lss.segmentNames)
        assertEquals(4, lss.runs.size)
    }

    @Test
    fun parsesCompletedAttemptAsCumulativeRealTime() {
        val run = sample().use(LssCodec::parse).runs[0]
        assertTrue(run.completed)
        assertEquals(listOf(310_250L, 610_250L, 960_500L), run.splitTimesMs)
        assertEquals(parseLssDate("10/01/2026 18:00:00"), run.startedAtEpochMs)
    }

    @Test
    fun resetAttemptStopsAtFirstMissingSegment() {
        val run = sample().use(LssCodec::parse).runs[1]
        assertFalse(run.completed)
        assertEquals(listOf(290_000L), run.splitTimesMs)
    }

    @Test
    fun skippedSplitEndsWhereThePreviousOneDid() {
        val run = sample().use(LssCodec::parse).runs[2]
        assertTrue(run.completed)
        assertEquals(listOf(300_000L, 300_000L, 950_000L), run.splitTimesMs)
    }

    @Test
    fun attemptResetBeforeFirstSplitHasNoSplits() {
        val run = sample().use(LssCodec::parse).runs[3]
        assertFalse(run.completed)
        assertEquals(emptyList<Long>(), run.splitTimesMs)
    }

    @Test
    fun skipRightBeforeResetIsDropped() {
        val lss = parse(
            """
            <Run><Segments>
              <Segment><Name>A</Name><SegmentHistory><Time id="1"><RealTime>00:00:10</RealTime></Time></SegmentHistory></Segment>
              <Segment><Name>B</Name><SegmentHistory><Time id="1" /></SegmentHistory></Segment>
              <Segment><Name>C</Name><SegmentHistory /></Segment>
            </Segments>
            <AttemptHistory><Attempt id="1" started="10/01/2026 18:00:00" /></AttemptHistory></Run>
            """.trimIndent(),
        )
        assertEquals(listOf(10_000L), lss.runs.single().splitTimesMs)
    }

    @Test
    fun withoutAttemptHistoryThePersonalBestBecomesOneRun() {
        val lss = parse(
            """
            <Run><Segments>
              <Segment><Name>A</Name><SplitTimes><SplitTime name="Personal Best"><RealTime>00:01:00</RealTime></SplitTime></SplitTimes></Segment>
              <Segment><Name>B</Name><SplitTimes><SplitTime name="Personal Best"><RealTime>00:02:30</RealTime></SplitTime></SplitTimes></Segment>
            </Segments></Run>
            """.trimIndent(),
        )
        val run = lss.runs.single()
        assertTrue(run.completed)
        assertEquals(listOf(60_000L, 150_000L), run.splitTimesMs)
    }

    @Test(expected = LssFormatException::class)
    fun rejectsOtherXml() {
        parse("<Layout />")
    }

    @Test(expected = LssFormatException::class)
    fun rejectsNonXml() {
        parse("not xml at all")
    }

    @Test
    fun roundTripKeepsRuns() {
        val segments = listOf("Tutorial", "Forest <1>", "Boss")
        val finished = RunRecord(
            startedAtEpochMs = 1_791_640_800_000,
            segmentNames = segments,
            splitTimesMs = listOf(41_200, 95_430, 180_010),
            completed = true,
        )
        val reset = RunRecord(
            startedAtEpochMs = 1_791_727_200_000,
            segmentNames = segments,
            splitTimesMs = listOf(40_000),
            completed = false,
        )
        val parsed = roundTrip(LssRun("My Game", "Any%", segments, listOf(reset, finished)))

        assertEquals("My Game", parsed.gameName)
        assertEquals("Any%", parsed.categoryName)
        assertEquals(segments, parsed.segmentNames)
        // Written oldest first.
        assertEquals(
            listOf(finished, reset).map { Triple(it.startedAtEpochMs, it.splitTimesMs, it.completed) },
            parsed.runs.map { Triple(it.startedAtEpochMs, it.splitTimesMs, it.completed) },
        )
    }

    @Test
    fun runWithOtherSegmentListKeepsOnlyItsAttempt() {
        val segments = listOf("A", "B")
        val old = RunRecord(
            startedAtEpochMs = 1_000,
            segmentNames = listOf("A", "B", "C"),
            splitTimesMs = listOf(1_000, 2_000, 3_000),
            completed = true,
        )
        val parsed = roundTrip(LssRun("Game", "", segments, listOf(old)))
        val run = parsed.runs.single()
        assertEquals(emptyList<Long>(), run.splitTimesMs)
        assertFalse(run.completed)
    }

    @Test
    fun writesPersonalBestAndBestSegments() {
        val segments = listOf("A", "B")
        val slow = RunRecord(startedAtEpochMs = 1, segmentNames = segments, splitTimesMs = listOf(10_000, 30_000), completed = true)
        val fast = RunRecord(startedAtEpochMs = 2, segmentNames = segments, splitTimesMs = listOf(15_000, 25_000), completed = true)
        val out = ByteArrayOutputStream()
        LssCodec.write(LssRun("G", "", segments, listOf(slow, fast)), out)
        val xml = out.toString(Charsets.UTF_8.name())

        // PB is the faster finish; best segments mix both runs.
        assertTrue(xml.contains("""<SplitTime name="Personal Best"><RealTime>00:00:15.0000000</RealTime></SplitTime>"""))
        assertTrue(xml.contains("""<SplitTime name="Personal Best"><RealTime>00:00:25.0000000</RealTime></SplitTime>"""))
        assertEquals(2, Regex("<BestSegmentTime><RealTime>00:00:10.0000000</RealTime></BestSegmentTime>").findAll(xml).count())
    }

    @Test
    fun timeFormat() {
        assertEquals(3_723_456L, parseLssTime("01:02:03.4560000"))
        assertEquals(3_723_000L, parseLssTime("01:02:03"))
        assertEquals(90_000_000L, parseLssTime("1.01:00:00.0000000"))
        assertEquals(-1_500L, parseLssTime("-00:00:01.5"))
        assertNull(parseLssTime("soon"))
        assertEquals("01:02:03.4560000", formatLssTime(3_723_456))
        assertEquals("1.01:00:00.0000000", formatLssTime(90_000_000))
        assertEquals(90_000_000L, parseLssTime(formatLssTime(90_000_000)))
    }

    @Test
    fun dateFormatIsUtc() {
        assertEquals(0L, parseLssDate("01/01/1970 00:00:00"))
        assertEquals("01/01/1970 00:00:00", formatLssDate(0))
        assertNull(parseLssDate(""))
    }
}
