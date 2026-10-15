package com.example.kpkn.screens.sessioneditor

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.catalogv2.CatalogV2SelectionGateIssue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Quietud antes del guardado automático en el programa (ER1a §5). */
internal const val PROGRAM_AUTO_SAVE_QUIET_MS = 1_200L

/** Aviso fijo del guardado automático bloqueado; [exerciseId] lleva al ejercicio cuando el cortafuegos lo señala. */
data class SessionAutoSaveBlock(val message: String, val exerciseId: String?)

/**
 * Temporizador del guardado automático en el programa. Cada cambio ([touch]) reinicia la cuenta de calma; cuando la persona se
 * queda quieta [quietMillis], lanza una pasada. Las pasadas van en serie: una edición que llega mientras se guarda no corta ese
 * guardado, y se guarda en la pasada siguiente. [flush] guarda ya lo pendiente y espera (al salir o al cambiar de sesión o de día).
 */
internal class ProgramAutoSaveScheduler(
    private val scope: CoroutineScope,
    private val quietMillis: Long = PROGRAM_AUTO_SAVE_QUIET_MS,
    private val pass: suspend () -> Boolean,
) {
    private val mutex = Mutex()
    private var timer: Job? = null

    /** Un cambio: reinicia la cuenta. */
    fun touch() {
        timer?.cancel()
        timer = scope.launch {
            delay(quietMillis)
            timer = null
            // Lanzada fuera del temporizador: otra edición cancela la cuenta, nunca la pasada que ya guarda.
            scope.launch { runPass() }
        }
    }

    /** Guarda ya lo pendiente y devuelve si quedó guardado (o no había nada). */
    suspend fun flush(): Boolean {
        cancel()
        return runPass()
    }

    fun cancel() {
        timer?.cancel()
        timer = null
    }

    private suspend fun runPass(): Boolean = mutex.withLock { pass() }
}

/** Un cambio del editor: el guardado en el programa se hace cuando la persona deja de tocar (ER1a §5). */
fun SessionEditorViewModel.scheduleProgramAutoSave() {
    programAutoSave.touch()
}

/** Guarda lo pendiente en el programa y espera el resultado: al salir, al cambiar de sesión o de día, y al confirmar una transferencia. */
suspend fun SessionEditorViewModel.flushProgramAutoSaveAndAwait(): Boolean = programAutoSave.flush()

/** Guarda lo pendiente sin esperar: al pasar la app a segundo plano. */
fun SessionEditorViewModel.flushProgramAutoSaveInBackground() {
    viewModelScope.launch { programAutoSave.flush() }
}

/**
 * Una pasada del guardado automático: el mismo «Guardar» del original (`saveSession`: normaliza, reconcilia, cortafuegos, validación y
 * escritura en Room), sin refrescar la pantalla ni cerrar la hoja abierta. Si el cortafuegos o la validación bloquean, deja el aviso
 * fijo y lo escrito sigue en pantalla; si pasa, quita el aviso.
 */
internal suspend fun SessionEditorViewModel.runProgramAutoSavePass(): Boolean {
    val state = currentUiState
    if (state.session == null || !state.hasUnsavedChanges) {
        setAutoSaveBlock(null)
        return true
    }
    lastGateBlockExerciseId = null
    val result = saveSession(scope = SessionSaveScope.SESSION_ONLY, skipRefresh = true, autoSave = true)
    if (result.success) {
        setAutoSaveBlock(null)
    } else {
        setAutoSaveBlock(SessionAutoSaveBlock(message = result.message, exerciseId = lastGateBlockExerciseId))
    }
    return result.success
}

/** Primer ejercicio del catálogo que bloquea el guardado: el aviso lleva a él. Null si el bloqueo es de la propia sesión. */
internal fun catalogGateBlockExerciseId(issues: List<CatalogV2SelectionGateIssue>): String? =
    issues.firstOrNull { !it.exerciseId.startsWith("session:") }?.exerciseId
