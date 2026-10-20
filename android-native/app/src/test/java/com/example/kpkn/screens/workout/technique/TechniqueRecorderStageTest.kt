package com.example.kpkn.screens.workout.technique

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueRecorderStageTest {
    @Test fun plegar_detiene_y_espera_finalize_antes_de_continuar() = runBlocking {
        val recording = MutableStateFlow(true)
        val events = mutableListOf<String>()
        val exit = async(start = CoroutineStart.UNDISPATCHED) {
            stopTechniqueRecorderBeforeExit({ events += "stop" }, recording)
            events += "fold"
        }
        assertEquals(listOf("stop"), events)
        assertFalse(exit.isCompleted)
        recording.value = false
        exit.await()
        assertEquals(listOf("stop", "fold"), events)
    }

    @Test fun plegar_en_reposo_no_espera_una_grabacion_inexistente() = runBlocking {
        var stopped = false
        stopTechniqueRecorderBeforeExit({ stopped = true }, MutableStateFlow(false))
        assertTrue(stopped)
    }
}
