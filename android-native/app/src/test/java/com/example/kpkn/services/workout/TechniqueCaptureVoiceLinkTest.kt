package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueCaptureVoiceLinkTest {

    private fun handlers(
        onStart: () -> TechniqueCaptureStartResult = { TechniqueCaptureStartResult.STARTED },
        onStop: () -> TechniqueCaptureStopResult = { TechniqueCaptureStopResult.STOPPED },
    ) = TechniqueCaptureVoiceLink.Handlers(start = onStart, stop = onStop)

    @Test
    fun withoutARecorderTheCommandsAreUnavailable() {
        assertEquals(TechniqueCaptureStartResult.UNAVAILABLE, TechniqueCaptureVoiceLink.tryStart())
        assertEquals(TechniqueCaptureStartResult.UNAVAILABLE, TechniqueCaptureVoiceLink.tryStartAnyway())
        assertEquals(TechniqueCaptureStopResult.UNAVAILABLE, TechniqueCaptureVoiceLink.tryStop())
        assertFalse(TechniqueCaptureVoiceLink.recorderOpen.value)
    }

    @Test
    fun attachedRecorderReceivesStartAndStop() {
        val linkHandlers = handlers()
        TechniqueCaptureVoiceLink.attach(linkHandlers)
        try {
            assertTrue(TechniqueCaptureVoiceLink.recorderOpen.value)
            assertEquals(TechniqueCaptureStartResult.STARTED, TechniqueCaptureVoiceLink.tryStart())
            assertEquals(TechniqueCaptureStartResult.STARTED, TechniqueCaptureVoiceLink.tryStartAnyway())
            assertEquals(TechniqueCaptureStopResult.STOPPED, TechniqueCaptureVoiceLink.tryStop())
        } finally {
            TechniqueCaptureVoiceLink.detach(linkHandlers)
        }
        assertFalse(TechniqueCaptureVoiceLink.recorderOpen.value)
    }

    @Test
    fun repeatedCommandsAreAnsweredByTheRecorderWithoutASecondCapture() {
        var recording = false
        var startAttempts = 0
        var captures = 0
        var stops = 0
        fun start(): TechniqueCaptureStartResult {
            startAttempts++
            if (recording) return TechniqueCaptureStartResult.ALREADY_RECORDING
            recording = true
            captures++
            return TechniqueCaptureStartResult.STARTED
        }
        val linkHandlers = TechniqueCaptureVoiceLink.Handlers(
            start = ::start,
            startAnyway = ::start,
            stop = {
                stops += 1
                recording = false
                TechniqueCaptureStopResult.STOPPED
            },
        )
        TechniqueCaptureVoiceLink.attach(linkHandlers)
        try {
            assertEquals(TechniqueCaptureStartResult.STARTED, TechniqueCaptureVoiceLink.tryStart())
            assertEquals(TechniqueCaptureStartResult.ALREADY_RECORDING, TechniqueCaptureVoiceLink.tryStartAnyway())
            assertEquals(
                TechniqueCaptureStartResult.ALREADY_RECORDING,
                TechniqueCaptureVoiceLink.tryStart(),
            )
            assertEquals(3, startAttempts)
            assertEquals(1, captures)
            assertEquals(TechniqueCaptureStopResult.STOPPED, TechniqueCaptureVoiceLink.tryStop())
            assertEquals(1, stops)
        } finally {
            TechniqueCaptureVoiceLink.detach(linkHandlers)
        }
    }

    @Test
    fun theSpokenGuideIsNotInterpretedAsACommand() {
        val linkHandlers = handlers()
        TechniqueCaptureVoiceLink.attach(linkHandlers)
        try {
            val now = 1_000L
            TechniqueCaptureVoiceLink.noteGuideSpoken(
                "Encuadre correcto. Puedes comenzar.",
                nowMs = now,
            )
            assertTrue(
                TechniqueCaptureVoiceLink.isGuideEcho(
                    "encuadre correcto puedes comenzar",
                    nowMs = now + 1_000L,
                ),
            )
            assertFalse(
                TechniqueCaptureVoiceLink.isGuideEcho(
                    "registrar serie",
                    nowMs = now + 1_000L,
                ),
            )
            assertFalse(
                TechniqueCaptureVoiceLink.isGuideEcho(
                    "encuadre correcto puedes comenzar",
                    nowMs = now + 9_000L,
                ),
            )
        } finally {
            TechniqueCaptureVoiceLink.detach(linkHandlers)
        }
    }

    @Test
    fun outsideTheRecorderTheGuideCannotSwallowACommand() {
        TechniqueCaptureVoiceLink.noteGuideSpoken("Encuadre listo", nowMs = 1_000L)
        assertFalse(
            TechniqueCaptureVoiceLink.isGuideEcho("Encuadre listo", nowMs = 1_100L),
        )
    }

    @Test
    fun closingTheRecorderClearsTheGuideEcho() {
        val linkHandlers = handlers()
        TechniqueCaptureVoiceLink.attach(linkHandlers)
        TechniqueCaptureVoiceLink.noteGuideSpoken("Encuadre listo", nowMs = 1_000L)
        TechniqueCaptureVoiceLink.setRecorderOpen(false)
        assertFalse(TechniqueCaptureVoiceLink.isGuideEcho("Encuadre listo", nowMs = 1_100L))
        TechniqueCaptureVoiceLink.detach(linkHandlers)
    }

    @Test
    fun voiceStateReflectsTheWorkoutScreen() {
        try {
            TechniqueCaptureVoiceLink.setVoiceEnabled(true)
            assertTrue(TechniqueCaptureVoiceLink.voiceEnabled.value)
            TechniqueCaptureVoiceLink.setVoiceEnabled(false)
            assertFalse(TechniqueCaptureVoiceLink.voiceEnabled.value)
        } finally {
            TechniqueCaptureVoiceLink.setVoiceEnabled(false)
        }
    }
}
