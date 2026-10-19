package com.grumpy_phoenix.speedrun_split_tracker.data

import android.content.Context
import android.provider.Settings
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunRecord
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The attempt on the timer screen, kept so a run survives the process being killed. */
data class ActiveAttempt(
    val runFile: RunFile,
    val attemptId: String,
    val startedAtEpochMs: Long,
    val timer: TimerState,
) {
    /** The attempt as it stands, for storing it as an unfinished run. */
    fun toRecord() = RunRecord(
        id = attemptId,
        startedAtEpochMs = startedAtEpochMs,
        segmentNames = timer.segments,
        splitTimesMs = timer.splitTimesMs,
        completed = timer.phase == TimerPhase.Finished,
    )
}

/**
 * A stored attempt. Its times come from the monotonic clock, which restarts at boot, so they
 * only still apply when [sameBoot] is true.
 */
data class SavedAttempt(val attempt: ActiveAttempt, val sameBoot: Boolean)

interface ActiveAttemptStore {
    fun load(): SavedAttempt?
    fun save(attempt: ActiveAttempt)
    fun clear()
}

class InMemoryActiveAttemptStore(private var saved: SavedAttempt? = null) : ActiveAttemptStore {
    override fun load(): SavedAttempt? = saved
    override fun save(attempt: ActiveAttempt) {
        saved = SavedAttempt(attempt, sameBoot = true)
    }
    override fun clear() {
        saved = null
    }
}

/** Keeps the attempt as JSON in SharedPreferences, so it needs no database migration. */
class PrefsActiveAttemptStore(context: Context) : ActiveAttemptStore {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("active_attempt", Context.MODE_PRIVATE)

    override fun load(): SavedAttempt? {
        val json = prefs.getString(KEY, null) ?: return null
        return decodeActiveAttempt(json, bootCount())
    }

    override fun save(attempt: ActiveAttempt) {
        prefs.edit().putString(KEY, encodeActiveAttempt(attempt, bootCount())).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun bootCount(): Int = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private companion object {
        const val KEY = "attempt"
    }
}

@Serializable
private data class StoredAttempt(
    val bootCount: Int,
    val runFileId: Long,
    val runFileFolderId: Long?,
    val runFileName: String,
    val attemptId: String,
    val startedAtEpochMs: Long,
    val segments: List<String>,
    val phase: TimerPhase,
    val startMs: Long,
    val pausedTotalMs: Long,
    val pauseStartMs: Long?,
    val splitTimesMs: List<Long>,
)

private val json = Json { ignoreUnknownKeys = true }

fun encodeActiveAttempt(attempt: ActiveAttempt, bootCount: Int): String = with(attempt) {
    json.encodeToString(
        StoredAttempt.serializer(),
        StoredAttempt(
            bootCount = bootCount,
            runFileId = runFile.id,
            runFileFolderId = runFile.folderId,
            runFileName = runFile.name,
            attemptId = attemptId,
            startedAtEpochMs = startedAtEpochMs,
            segments = timer.segments,
            phase = timer.phase,
            startMs = timer.startMs,
            pausedTotalMs = timer.pausedTotalMs,
            pauseStartMs = timer.pauseStartMs,
            splitTimesMs = timer.splitTimesMs,
        ),
    )
}

/** Returns null for unreadable data, so a bad entry can never keep the app from starting. */
fun decodeActiveAttempt(text: String, currentBootCount: Int): SavedAttempt? {
    val s = try {
        json.decodeFromString(StoredAttempt.serializer(), text)
    } catch (e: SerializationException) {
        return null
    } catch (e: IllegalArgumentException) {
        return null
    }
    val attempt = ActiveAttempt(
        runFile = RunFile(s.runFileId, s.runFileFolderId, s.runFileName),
        attemptId = s.attemptId,
        startedAtEpochMs = s.startedAtEpochMs,
        timer = TimerState(
            segments = s.segments,
            phase = s.phase,
            startMs = s.startMs,
            pausedTotalMs = s.pausedTotalMs,
            pauseStartMs = s.pauseStartMs,
            splitTimesMs = s.splitTimesMs,
        ),
    )
    // An unknown boot count (-1) can't prove the clock is still valid.
    val sameBoot = currentBootCount >= 0 && s.bootCount == currentBootCount
    return SavedAttempt(attempt, sameBoot)
}
