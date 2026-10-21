package com.example.kpkn.screens.sessioneditor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reloj virtual: el temporizador del guardado automático (1,2 s de calma) y sus pasadas en serie (ER1a §5). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditorProgramAutoSaveSchedulerTest {

    @Test
    fun aChangeIsSavedOnlyAfterTheQuietPeriod() = runTest {
        var passes = 0
        val scheduler = ProgramAutoSaveScheduler(scope = this, pass = {
            passes++
            true
        })
        scheduler.touch()
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS - 1)
        runCurrent()
        assertEquals(0, passes)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, passes)
    }

    @Test
    fun fiveChangesInARowGiveOneSave() = runTest {
        var passes = 0
        val scheduler = ProgramAutoSaveScheduler(scope = this, pass = {
            passes++
            true
        })
        repeat(5) {
            scheduler.touch()
            advanceTimeBy(300)
        }
        runCurrent()
        assertEquals(0, passes)
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS + 100)
        runCurrent()
        assertEquals(1, passes)
    }

    @Test
    fun flushSavesNowAndDropsThePendingTimer() = runTest {
        var passes = 0
        val scheduler = ProgramAutoSaveScheduler(scope = this, pass = {
            passes++
            true
        })
        scheduler.touch()
        assertTrue(scheduler.flush())
        assertEquals(1, passes)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, passes)
    }

    @Test
    fun aChangeMadeDuringAPassIsSavedInTheNextPass() = runTest {
        val seen = mutableListOf<Int>()
        var count = 0
        val scheduler = ProgramAutoSaveScheduler(scope = this, pass = {
            count++
            seen += count
            // Un guardado en curso: una edición que llega ahora no lo corta.
            delay(500)
            true
        })
        scheduler.touch()
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS)
        runCurrent()
        advanceTimeBy(100)
        scheduler.touch()
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS + 1_000)
        runCurrent()
        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun aBlockedPassIsRetriedByTheNextChange() = runTest {
        var passes = 0
        val scheduler = ProgramAutoSaveScheduler(scope = this, pass = {
            passes++
            // Primera pasada bloqueada (cortafuegos); la siguiente pasa.
            passes > 1
        })
        scheduler.touch()
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS + 100)
        runCurrent()
        assertEquals(1, passes)
        scheduler.touch()
        advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS + 100)
        runCurrent()
        assertEquals(2, passes)
    }
}
