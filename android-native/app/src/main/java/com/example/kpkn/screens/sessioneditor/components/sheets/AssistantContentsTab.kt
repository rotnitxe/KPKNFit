package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState

/** Authored counts and explicitly estimated time; never a score of the athlete. */
@Composable
internal fun AssistantContentsTab(uiState: SessionEditorUiState) {
    val session = uiState.activeVariantSession ?: uiState.session
    val exercises = session?.allExercises().orEmpty()
    Column {
        Text("Contenido del programa")
        Text("${exercises.size} ejercicios · ${exercises.sumOf { it.sets.size }} series planificadas")
        Text("Duración estimada: ${uiState.estimatedDurationMinutes} min")
        uiState.assistantReport?.volumenPorMusculo.orEmpty().forEach { (muscle, count) ->
            Text("$muscle: $count series ponderadas por papel muscular")
        }
        Text("Los recuentos describen tu plan; no indican recuperación ni seguridad.")
    }
}
