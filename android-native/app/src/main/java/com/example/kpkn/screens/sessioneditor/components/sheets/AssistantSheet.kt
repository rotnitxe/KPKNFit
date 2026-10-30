package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.SessionTemplateFacetsCache
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.assistant.ASSISTANT_FULL_HEIGHT
import com.example.kpkn.screens.sessioneditor.components.assistant.ASSISTANT_MEDIUM_HEIGHT
import com.example.kpkn.screens.sessioneditor.components.assistant.AsColors
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantCallbacks
import com.example.kpkn.screens.sessioneditor.components.assistant.AssistantSheetContent
import com.example.kpkn.ui.components.KpknSheet
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La hoja «Asistente de sesión»: cuatro pestañas (Métricas · Sugerencias · Plantillas · Reglas) en una hoja plana de dos alturas, media
 * (62 %) y completa (93 %), que se cambian con el asa y se conservan al cambiar de pestaña. Diseño del lienzo «Pulido KPKN v1», paquete
 * `as`: el contenido vive en `components/assistant/`.
 */
@Composable
internal fun AssistantGlassOverlay(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    callbacks: AssistantCallbacks,
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
) {
    // Los reglas abiertas desde el anillo de minutos piden la hoja completa; el resto abre a media altura.
    var full by rememberSaveable { mutableStateOf(uiState.assistantEntry?.tab == com.example.kpkn.screens.sessioneditor.AssistantTab.RULES) }
    val facetsIndex = remember { exerciseCatalogSnapshot().associateBy { it.id.lowercase() } }
    val facets by produceState(emptyMap<String, SessionTemplateFacets>(), templates) {
        value = withContext(Dispatchers.Default) { runCatching { SessionTemplateFacetsCache.getOrBuild(templates, facetsIndex) }.getOrDefault(emptyMap()) }
    }
    // pulido:as-A13 as-root.in as-head/tabs/body — el contenido entra 0,3 s con 0,16 s de retardo (KpknSheet: 120 ms + 340 ms; calibrar en la pasada C)
    // pulido:as-T09 as-scrim — el fondo cambia de opacidad en 0,22 s · pulido:as-X06 as-root.closed as-sheet — al cerrar la hoja baja al 102 %
    // pulido:as-T13 as-sheet — la altura cambia en 0,32 s cubic-bezier(.2,.8,.2,1) · pulido:as-A15 as-root.in as-sheet — sube 0,42 s · pulido:as-A14 — fondo en 0,22 s
    // pulido:as-S05 as-sheet — sombra 0 -14px 44px #000000cc + filete interior blanco al 6 %
    KpknSheet(
        onDismissRequest = callbacks.onDismiss,
        showDragHandle = false,
        maxHeightFraction = ASSISTANT_FULL_HEIGHT,
        stableHeightFraction = if (full) ASSISTANT_FULL_HEIGHT else ASSISTANT_MEDIUM_HEIGHT,
        surfaceColor = AsColors.sheet,
        backdropScrim = AsColors.scrim,
    ) {
        AssistantSheetContent(
            uiState = uiState,
            templates = templates,
            archivedUserTemplates = archivedUserTemplates,
            templatesReady = templatesReady,
            corruptTemplateIds = corruptTemplateIds,
            exerciseIndex = exerciseIndex,
            facetsById = facets,
            callbacks = callbacks,
            fullHeight = full,
            onFullHeight = { full = it },
        )
    }
}

internal fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = catalogLookup,
    catalogConfigurationId = exercise.catalogConfigurationId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.name,
)
