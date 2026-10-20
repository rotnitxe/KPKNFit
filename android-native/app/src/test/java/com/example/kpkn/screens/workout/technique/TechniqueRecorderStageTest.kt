package com.example.kpkn.screens.workout.technique

import com.example.kpkn.data.media.WorkoutMediaFinalizePolicy
import java.io.File
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

    @Test fun alcanzar_el_limite_con_video_retenido_tambien_termina_la_escena() {
        assertTrue(WorkoutMediaFinalizePolicy.shouldRetainFile(
            fileLengthBytes = 1024L, hasError = true,
            errorCode = WorkoutMediaFinalizePolicy.ERROR_DURATION_LIMIT_REACHED,
        ))
        var finished = 0
        val onFinalized = TechniqueRecorderCompletion().callback { finished++ }
        // El controlador entrega null si CameraX marca ERROR_DURATION_LIMIT_REACHED.
        onFinalized(null)
        assertEquals(1, finished)
    }

    @Test fun un_finalize_tardio_no_cierra_la_escena_siguiente() {
        var finished = 0
        val previous = TechniqueRecorderCompletion()
        val previousCallback = previous.callback { finished++ }
        previous.close()
        previousCallback(null)
        assertEquals(0, finished)
        val current = TechniqueRecorderCompletion()
        val currentCallback = current.callback { finished++ }
        currentCallback(File("video.mp4"))
        assertEquals(1, finished)
    }

}
