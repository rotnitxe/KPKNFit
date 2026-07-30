package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutVoiceRescueContractTest {
    @Test
    fun equalPriorityNeverReplacesActiveSpeech() {
        val bus = WorkoutSpeechBus()
        var interrupted = false
        assertTrue(bus.tryAcquire(WorkoutSpeechPriority.HIGH) { interrupted = true })
        assertFalse(bus.tryAcquire(WorkoutSpeechPriority.HIGH) { interrupted = true })
        assertFalse(interrupted)
    }

    @Test
    fun completionGateIsExactlyOnceAcrossThreads() {
        val calls = AtomicInteger(0)
        val gate = WorkoutVoiceUtteranceGuard.createCompletionGate { calls.incrementAndGet() }
        val pool = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(32)
        repeat(32) {
            pool.execute {
                gate()
                ready.countDown()
            }
        }
        ready.await()
        pool.shutdownNow()
        assertEquals(1, calls.get())
    }

    @Test
    fun grammarContainsStageSpecificMetricPhrases() {
        val grammar = WorkoutVoiceGrammarBuilder.build(VoicePipelineStage.LISTENING, null)
        assertTrue(grammar.contains("cinco repeticiones"))
        assertTrue(grammar.contains("reservas cinco"))
    }

    @Test
    fun fullSetAndTagRemainOneStructuredCommand() {
        val command = WorkoutVoiceCommandParser.parseCommand(
            transcript = "cinco repeticiones cincuenta kilos rir uno etiqueta pesada",
            isTimeMode = false,
            isUnilateral = false,
            hasPendingConfirmation = false,
            isRestTimerActive = false,
            unitMode = UnitModeV2.REPS,
            tagNames = setOf("Pesada"),
        )
        assertTrue(command is VoiceSessionCommand.RegisterSet)
        val interpretation = (command as VoiceSessionCommand.RegisterSet).interpretation
        assertEquals(5, interpretation.metricValue)
        assertEquals(50.0, interpretation.weightKg ?: 0.0, 0.001)
        assertEquals(1.0, interpretation.intensityValue ?: 0.0, 0.001)
        assertEquals("Pesada", interpretation.tagName)
    }
}
