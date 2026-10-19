package com.grumpy_phoenix.speedrun_split_tracker.ui

import com.grumpy_phoenix.speedrun_split_tracker.data.ActiveAttempt
import com.grumpy_phoenix.speedrun_split_tracker.data.InMemoryActiveAttemptStore
import com.grumpy_phoenix.speedrun_split_tracker.data.InMemoryRunRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.InMemorySegmentRepository
import com.grumpy_phoenix.speedrun_split_tracker.data.SavedAttempt
import com.grumpy_phoenix.speedrun_split_tracker.domain.RunFile
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerPhase
import com.grumpy_phoenix.speedrun_split_tracker.domain.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunViewModelRestoreTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val runFile = RunFile(id = 7, folderId = null, name = "Celeste Any%")
    private val store = InMemoryActiveAttemptStore()
    private val runs = InMemoryRunRepository()
    private val segments = InMemorySegmentRepository()
    private var now = 1_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking { segments.save(runFile.id, listOf("Forest", "Castle", "Boss")) }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = RunViewModel(
        clock = { now },
        repository = runs,
        segmentRepository = segments,
        activeAttemptStore = store,
        wallClockMs = { 1_700_000_000_000L },
        ioDispatcher = dispatcher,
    )

    private fun startedRun(): RunViewModel = newViewModel().apply {
        openRunFile(runFile)
        startRun()
        startTimer()
        now += 40_000
        split()
    }

    @Test
    fun runInProgressIsRestoredByANewViewModel() {
        val before = startedRun().state.value

        val restored = newViewModel().state.value

        assertEquals(Screen.Timer, restored.screen)
        assertEquals(runFile, restored.runFile)
        assertEquals(before.timer, restored.timer)
        assertEquals(listOf("Forest", "Castle", "Boss"), restored.drafts.map { it.name })
    }

    @Test
    fun finishingARestoredRunSavesItUnderTheOriginalAttempt() {
        startedRun()
        val attemptId = store.load()!!.attempt.attemptId

        newViewModel().apply {
            now += 30_000
            split()
            now += 30_000
            split()
        }

        assertNull(store.load())
        val saved = runBlocking { runs.runs(runFile.id).first() }.single()
        assertEquals(attemptId, saved.id)
        assertEquals(listOf(40_000L, 70_000L, 100_000L), saved.splitTimesMs)
    }

    @Test
    fun pausedRunIsStoredToo() {
        startedRun().pause()
        assertEquals(TimerPhase.Paused, store.load()!!.attempt.timer.phase)
    }

    @Test
    fun resetClearsTheStoredAttempt() {
        val vm = startedRun()
        assertNotNull(store.load())
        vm.reset()
        assertNull(store.load())
    }

    @Test
    fun attemptFromBeforeARebootIsKeptAsAResetRun() {
        val attempt = ActiveAttempt(
            runFile = runFile,
            attemptId = "a1",
            startedAtEpochMs = 5L,
            timer = TimerState(
                segments = listOf("Forest", "Castle"),
                phase = TimerPhase.Running,
                startMs = 100L,
                splitTimesMs = listOf(12_000L),
            ),
        )
        val rebootStore = InMemoryActiveAttemptStore(SavedAttempt(attempt, sameBoot = false))

        val vm = RunViewModel(
            clock = { now },
            repository = runs,
            segmentRepository = segments,
            activeAttemptStore = rebootStore,
            ioDispatcher = dispatcher,
        )

        assertEquals(Screen.Library, vm.state.value.screen)
        assertNull(rebootStore.load())
        val saved = runBlocking { runs.runs(runFile.id).first() }.single()
        assertEquals("a1", saved.id)
        assertEquals(listOf(12_000L), saved.splitTimesMs)
        assertFalse(saved.completed)
    }
}
