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
            assertEquals(TechniqueCaptureStopResult.STOPPED, TechniqueCaptureVoiceLink.tryStop())
        } finally {
            TechniqueCaptureVoiceLink.detach(linkHandlers)
        }
        assertFalse(TechniqueCaptureVoiceLink.recorderOpen.value)
    }

    @Test
    fun repeatedCommandsAreAnsweredByTheRecorderWithoutASecondCapture() {
        var starts = 0
        var stops = 0
        val linkHandlers = handlers(
            onStart = {
                starts += 1
                if (starts == 1) TechniqueCaptureStartResult.STARTED
                else TechniqueCaptureStartResult.ALREADY_RECORDING
            },
            onStop = {
                stops += 1
                TechniqueCaptureStopResult.STOPPED
            },
        )
        TechniqueCaptureVoiceLink.attach(linkHandlers)
        try {
            assertEquals(TechniqueCaptureStartResult.STARTED, TechniqueCaptureVoiceLink.tryStart())
            assertEquals(
                TechniqueCaptureStartResult.ALREADY_RECORDING,
                TechniqueCaptureVoiceLink.tryStart(),
            )
            assertEquals(2, starts)
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
