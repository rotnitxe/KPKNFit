package com.example.kpkn.screens.workout.technique

import com.example.kpkn.data.media.WorkoutMediaFinalizePolicy
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.workout.TechniqueRecordingResult
import com.example.kpkn.screens.workout.awaitTechniqueRecordingResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueRecorderStageTest {
    private val media = WorkoutMedia("saved", WorkoutMediaKind.VIDEO, "video.mp4", createdAtMs = 1L)

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

    @Test fun rec_no_inicia_otra_toma_mientras_el_guardado_esta_demorado() = runBlocking {
        val completion = TechniqueRecorderCompletion()
        val write = CompletableDeferred<WorkoutMedia?>()
        var starts = 0
        var folds = 0
        lateinit var callback: (TechniqueRecordingResult) -> Unit
        fun start() = completion.start({}, { folds++ }) { starts++; callback = it }
        start()
        val observer = async(start = CoroutineStart.UNDISPATCHED) {
            callback(awaitTechniqueRecordingResult { write.await() })
        }
        // CameraX ya emitió Finalize, pero la persistencia sigue esperando en IO.
        start()
        assertTrue(completion.pending)
        assertEquals(1, starts)
        assertEquals(0, folds)
        assertFalse(observer.isCompleted)
        write.complete(media)
        observer.await()
        assertFalse(completion.pending)
        assertEquals(1, folds)
    }

    @Test fun guardado_pliega_solo_despues_de_persistir() = runBlocking {
        val completion = TechniqueRecorderCompletion()
        var folds = 0
        lateinit var callback: (TechniqueRecordingResult) -> Unit
        completion.start({}, { folds++ }) { callback = it }
        callback(awaitTechniqueRecordingResult { media })
        assertEquals(1, folds)
        assertEquals(null, completion.errorMessage)
    }

    @Test fun descartado_no_pliega_y_permite_volver_a_grabar() = runBlocking {
        val completion = TechniqueRecorderCompletion()
        var starts = 0
        var folds = 0
        lateinit var callback: (TechniqueRecordingResult) -> Unit
        fun start() = completion.start({}, { folds++ }) { starts++; callback = it }
        start()
        callback(awaitTechniqueRecordingResult { null })
        assertEquals(0, folds)
        assertFalse(completion.pending)
        assertEquals(TechniqueRecordingResult.Discarded.errorMessage, completion.errorMessage)
        start()
        assertEquals(2, starts)
    }

    @Test fun error_de_persistencia_no_pliega_y_conserva_el_aviso() = runBlocking {
        val completion = TechniqueRecorderCompletion()
        var folds = 0
        lateinit var callback: (TechniqueRecordingResult) -> Unit
        completion.start({}, { folds++ }) { callback = it }
        val result = awaitTechniqueRecordingResult { throw IOException("sin espacio") }
        assertTrue(result is TechniqueRecordingResult.Error)
        callback(result)
        assertEquals(0, folds)
        assertFalse(completion.pending)
        assertEquals("No se pudo guardar el vídeo. Puedes reintentar.", completion.errorMessage)
    }

    @Test fun limite_de_duracion_retenido_pliega_cuando_el_repositorio_lo_guarda() = runBlocking {
        assertTrue(WorkoutMediaFinalizePolicy.shouldRetainFile(
            fileLengthBytes = 1024L, hasError = true,
            errorCode = WorkoutMediaFinalizePolicy.ERROR_DURATION_LIMIT_REACHED,
        ))
        val completion = TechniqueRecorderCompletion()
        var folds = 0
        lateinit var callback: (TechniqueRecordingResult) -> Unit
        completion.start({}, { folds++ }) { callback = it }
        callback(awaitTechniqueRecordingResult { media })
        assertEquals(1, folds)
    }

    @Test fun fichero_retenido_no_equivale_a_guardado_si_falla_la_persistencia() = runBlocking {
        val result = awaitTechniqueRecordingResult { throw IOException("Room") }
        assertTrue(result is TechniqueRecordingResult.Error)
    }

    @Test fun cancelar_el_observador_no_se_convierte_en_resultado_guardado() = runBlocking {
        var cancelled = false
        try {
            awaitTechniqueRecordingResult { throw kotlinx.coroutines.CancellationException() }
        } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun un_finalize_tardio_no_cierra_la_escena_siguiente() {
        var folds = 0
        val previous = TechniqueRecorderCompletion()
        lateinit var previousCallback: (TechniqueRecordingResult) -> Unit
        previous.start({}, { folds++ }) { previousCallback = it }
        previous.close()
        previousCallback(TechniqueRecordingResult.Saved(media))
        assertEquals(0, folds)
        TechniqueRecorderCompletion().start({}, { folds++ }) { it(TechniqueRecordingResult.Saved(media)) }
        assertEquals(1, folds)
    }

    @Test fun callback_duplicado_de_un_intento_fallido_no_cierra_el_reintento() {
        val completion = TechniqueRecorderCompletion()
        var folds = 0
        lateinit var previous: (TechniqueRecordingResult) -> Unit
        completion.start({}, { folds++ }) { previous = it }
        previous(TechniqueRecordingResult.Error())
        lateinit var current: (TechniqueRecordingResult) -> Unit
        completion.start({}, { folds++ }) { current = it }
        previous(TechniqueRecordingResult.Saved(media))
        assertTrue(completion.pending)
        assertEquals(0, folds)
        current(TechniqueRecordingResult.Saved(media))
        assertEquals(1, folds)
    }
}
