package com.example.kpkn.screens.workout.technique

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.workout.WorkoutMediaCaptureRequest

/**
 * Petición para abrir el grabador de técnica a pantalla completa (VT8a).
 *
 * @property captureRequest datos de la captura que se pasarán al controlador al grabar.
 * @property exercise ejercicio de la serie para resolver perfil, silueta y calibración de disco.
 * @property setLabel etiqueta visible en la barra superior (p. ej. "Serie 2").
 */
data class TechniqueRecorderRequest(
    val captureRequest: WorkoutMediaCaptureRequest,
    val exercise: Exercise? = null,
    val setLabel: String = "",
)

/**
 * Holder estable que vive en [WorkoutScreen] para gestionar la visibilidad del grabador
 * de técnica a pantalla completa.
 */
@Stable
class TechniqueRecorderHolder {
    var request by mutableStateOf<TechniqueRecorderRequest?>(null)
}
