package com.grumpy_phoenix.speedrun_split_tracker.data.lss

import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import org.w3c.dom.Element
import java.io.InputStream
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The parts of a LiveSplit splits file (.lss) this app understands. */
data class LssRun(
    val gameName: String,
    val categoryName: String,
    val segmentNames: List<String>,
    /** Every attempt, in any order. */
    val runs: List<RunRecord>,
)

class LssFormatException(message: String) : Exception(message)

/**
 * Reads and writes LiveSplit splits files. Only real time is used; game time, icons,
 * comparisons other than the personal best and auto splitter settings are ignored.
 */
object LssCodec {

    private const val PERSONAL_BEST = "Personal Best"

    fun parse(input: InputStream): LssRun {
        val factory = DocumentBuilderFactory.newInstance()
        // Not every platform parser knows this feature; a .lss file never needs a DTD anyway.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val root = try {
            factory.newDocumentBuilder().parse(input).documentElement
        } catch (e: Exception) {
            throw LssFormatException("Not a valid XML file")
        }
        if (root.tagName != "Run") throw LssFormatException("Not a LiveSplit splits file")

        val segmentElements = root.child("Segments")?.children("Segment").orEmpty()
        val segmentNames = segmentElements.map { it.child("Name")?.textContent?.trim().orEmpty() }
        // Per segment: attempt id -> duration, with null for a skipped split.
        val histories = segmentElements.map { segment ->
            segment.child("SegmentHistory")?.children("Time").orEmpty()
                .mapNotNull { time -> time.getAttribute("id").toIntOrNull()?.let { it to time.realTime() } }
                .toMap()
        }

        val attempts = root.child("AttemptHistory")?.children("Attempt").orEmpty()
        val runs = attempts.mapNotNull { attempt ->
            val id = attempt.getAttribute("id").toIntOrNull() ?: return@mapNotNull null
            val splits = cumulativeSplits(histories.map { history ->
                if (history.containsKey(id)) Present(history[id]) else Missing
            })
            RunRecord(
                startedAtEpochMs = parseLssDate(attempt.getAttribute("started")) ?: 0L,
                segmentNames = segmentNames,
                splitTimesMs = splits,
                completed = attempt.realTime() != null && splits.size == segmentNames.size,
            )
        }.ifEmpty { listOfNotNull(personalBestRun(segmentElements, segmentNames)) }

        return LssRun(
            gameName = root.child("GameName")?.textContent?.trim().orEmpty(),
            categoryName = root.child("CategoryName")?.textContent?.trim().orEmpty(),
            segmentNames = segmentNames,
            runs = runs,
        )
    }

    fun write(run: LssRun, output: OutputStream) {
        val segmentCount = run.segmentNames.size
        val attempts = run.runs.sortedBy { it.startedAtEpochMs }.withIndex().map { (i, r) -> i + 1 to r }
        // Runs recorded with a different segment list can't be mapped onto these segments.
        val matching = attempts.filter { (_, r) -> r.segmentNames.size == segmentCount }
        val personalBest = matching.map { it.second }
            .filter { it.completed && it.splitTimesMs.size == segmentCount }
            .minByOrNull { it.splitTimesMs.last() }

        val xml = StringBuilder()
        xml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        xml.append("""<Run version="1.7.0">""").append('\n')
        xml.append("  <GameIcon />\n")
        xml.append("  <GameName>${escape(run.gameName)}</GameName>\n")
        xml.append("  <CategoryName>${escape(run.categoryName)}</CategoryName>\n")
        xml.append("  <Offset>00:00:00</Offset>\n")
        xml.append("  <AttemptCount>${attempts.size}</AttemptCount>\n")
        xml.append("  <AttemptHistory>\n")
        for ((id, r) in attempts) {
            val ended = r.startedAtEpochMs + (r.splitTimesMs.lastOrNull() ?: 0L)
            xml.append("""    <Attempt id="$id" started="${formatLssDate(r.startedAtEpochMs)}" """)
            xml.append("""isStartedSynced="True" ended="${formatLssDate(ended)}" isEndedSynced="True"""")
            if (r.completed && r.splitTimesMs.isNotEmpty()) {
                xml.append(">\n      ${realTime(r.splitTimesMs.last())}\n    </Attempt>\n")
            } else {
                xml.append(" />\n")
            }
        }
        xml.append("  </AttemptHistory>\n")
        xml.append("  <Segments>\n")
        run.segmentNames.forEachIndexed { index, name ->
            val best = matching.mapNotNull { (_, r) -> r.segmentDurationMs(index) }.minOrNull()
            xml.append("    <Segment>\n")
            xml.append("      <Name>${escape(name)}</Name>\n")
            xml.append("      <Icon />\n")
            xml.append("      <SplitTimes>\n")
            val pbSplit = personalBest?.splitTimesMs?.get(index)
            if (pbSplit != null) {
                xml.append("""        <SplitTime name="$PERSONAL_BEST">${realTime(pbSplit)}</SplitTime>""").append('\n')
            } else {
                xml.append("""        <SplitTime name="$PERSONAL_BEST" />""").append('\n')
            }
            xml.append("      </SplitTimes>\n")
            xml.append(if (best != null) "      <BestSegmentTime>${realTime(best)}</BestSegmentTime>\n" else "      <BestSegmentTime />\n")
            xml.append("      <SegmentHistory>\n")
            for ((id, r) in matching) {
                val duration = r.segmentDurationMs(index) ?: continue
                xml.append("""        <Time id="$id">${realTime(duration)}</Time>""").append('\n')
            }
            xml.append("      </SegmentHistory>\n")
            xml.append("    </Segment>\n")
        }
        xml.append("  </Segments>\n")
        xml.append("  <AutoSplitterSettings />\n")
        xml.append("</Run>\n")
        output.write(xml.toString().toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private sealed interface HistoryEntry
    /** The attempt reached this segment; [durationMs] is null when the split was skipped. */
    private data class Present(val durationMs: Long?) : HistoryEntry
    private data object Missing : HistoryEntry

    /**
     * Turns per-segment durations into cumulative split times, stopping at the first segment
     * the attempt never reached. A skipped split gets no time of its own: it ends where the
     * previous one did, and the next split's duration covers both.
     */
    private fun cumulativeSplits(entries: List<HistoryEntry>): List<Long> {
        val splits = mutableListOf<Long>()
        var total = 0L
        var lastTimed = 0
        for (entry in entries) {
            if (entry !is Present) break
            entry.durationMs?.let { total += it; lastTimed = splits.size + 1 }
            splits += total
        }
        // Skips right before a reset aren't real progress.
        return splits.take(lastTimed)
    }

    /** Fallback for files without attempt history: the personal best as one finished run. */
    private fun personalBestRun(segments: List<Element>, names: List<String>): RunRecord? {
        if (segments.isEmpty()) return null
        val splits = segments.map { segment ->
            segment.child("SplitTimes")?.children("SplitTime").orEmpty()
                .firstOrNull { it.getAttribute("name") == PERSONAL_BEST }?.realTime() ?: return null
        }
        return RunRecord(startedAtEpochMs = 0L, segmentNames = names, splitTimesMs = splits, completed = true)
    }

    private fun Element.realTime(): Long? = child("RealTime")?.textContent?.let(::parseLssTime)

    private fun Element.children(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
    }

    private fun Element.child(tag: String): Element? = children(tag).firstOrNull()

    private fun realTime(ms: Long) = "<RealTime>${formatLssTime(ms)}</RealTime>"

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
