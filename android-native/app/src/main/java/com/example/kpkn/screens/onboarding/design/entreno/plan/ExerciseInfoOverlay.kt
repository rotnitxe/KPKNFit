package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.muscleLabel
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * La ficha de un ejercicio: se abre al tocarlo en el detalle del programa y enseña lo que el CATÁLOGO dice de él (su imagen
 * técnica, la descripción de su definición y sus músculos y articulaciones). Aquí no se escribe ni se resume ningún texto: lo
 * que el catálogo no trae se dice que falta.
 */

/** Marcas de prueba de la ficha: la hoja, su cierre, la imagen, el aviso «Sin imagen» y sus bloques de texto. */
internal const val EXERCISE_INFO_TAG = "setup-exercise-info"
internal const val EXERCISE_INFO_CLOSE_TAG = "setup-exercise-info-close"
internal const val EXERCISE_INFO_IMAGE_TAG = "setup-exercise-info-image"
internal const val EXERCISE_INFO_NOIMAGE_TAG = "setup-exercise-info-noimage"
internal const val EXERCISE_INFO_DESCRIPTION_TAG = "setup-exercise-info-description"
internal const val EXERCISE_INFO_MUSCLES_TAG = "setup-exercise-info-muscles"
internal const val EXERCISE_INFO_JOINTS_TAG = "setup-exercise-info-joints"

/** Rótulos de la ficha y avisos de dato ausente. Lo que viene del catálogo no pasa por aquí. */
internal object ExerciseInfoCopy {
    const val SECTION_HOW = "Cómo se hace"
    const val SECTION_MUSCLES = "Músculos"
    const val SECTION_JOINTS = "Articulaciones"
    const val IMAGE_MISSING = "Sin imagen"
    const val DESCRIPTION_MISSING = "Sin descripción en el catálogo."
    const val NOT_IN_CATALOG = "Este ejercicio no está en el catálogo."
    const val MUSCLES_MISSING = "El catálogo no indica músculos para este ejercicio."
    const val JOINTS_MISSING = "El catálogo no indica articulaciones para este ejercicio."
    const val CATALOG_FAILED = "No se pudo cargar el catálogo."
    const val IN_YOUR_PROGRAM = "En tu programa: "
    const val MUSCLES_PRIMARY = "Principales"
    const val MUSCLES_SECONDARY = "Secundarios"
    const val MUSCLES_STABILIZERS = "Estabilizadores"
    const val SEE_EXERCISE = "Ver ficha de "
}

/**
 * Lo que la ficha enseña de un ejercicio, leído del catálogo. Las listas van con sus etiquetas de la app; una vacía no se pinta.
 */
internal data class ExerciseInfo(
    /** El nombre de la definición del catálogo, tal cual (sin recortar). */
    val catalogName: String,
    /** La descripción de la definición, sin tocar; puede estar vacía. */
    val description: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val stabilizerMuscles: List<String>,
    /** Las articulaciones de la configuración exacta, en el orden del catálogo. */
    val joints: List<JointInvolvementV2>,
    /** El recurso de la imagen técnica de la configuración, o null si no tiene. */
    val imageResId: Int?,
)

/** La definición del catálogo que contiene la configuración [configurationId], o null si ninguna la tiene. */
internal fun definitionIdOfConfiguration(catalog: ExerciseCatalogV2, configurationId: String): String? =
    catalog.families.asSequence()
        .flatMap { it.definitions.asSequence() }
        .firstOrNull { definition -> definition.configurations.any { it.id == configurationId } }
        ?.id

/**
 * La ficha de [exercise] según [catalog]. Un ejercicio cambiado enseña la de su sustituto (`swappedConfigId`). Null si no tiene
 * identidad de catálogo o el catálogo no resuelve esa configuración exacta: la ficha lo dice y no inventa nada.
 */
internal fun exerciseInfoOf(exercise: SetupPlanRevealExercise, catalog: ExerciseCatalogV2): ExerciseInfo? {
    val swapped = exercise.swappedConfigId
    val configurationId = swapped ?: exercise.catalogConfigurationId
    val definitionId = if (swapped != null) definitionIdOfConfiguration(catalog, swapped) else exercise.catalogDefinitionId
    // Sin identidad no se busca por nombre: un ejercicio propio con el nombre de uno del catálogo no es ese ejercicio.
    if (definitionId == null) return null
    val resolved = resolveCatalogExerciseV2(
        exercise = Exercise(
            id = exercise.instanceId,
            name = exercise.name,
            catalogRevision = exercise.catalogRevision,
            catalogDefinitionId = definitionId,
            catalogConfigurationId = configurationId,
        ),
        catalog = catalog,
        legacyInfo = null,
    ) ?: return null
    // El resolutor cae a la configuración predeterminada si no encuentra la pedida: aquí eso sería enseñar otro ejercicio.
    if (configurationId != null && resolved.configuration.id != configurationId) return null
    val profile = resolved.configuration.profile
    return ExerciseInfo(
        catalogName = resolved.definition.canonicalName.trim(),
        description = resolved.definition.description,
        primaryMuscles = profile.primaryMuscles.map(::muscleNameOf),
        secondaryMuscles = profile.secondaryMuscles.map(::muscleNameOf),
        stabilizerMuscles = profile.stabilizerMuscles.map(::muscleNameOf),
        joints = profile.jointInvolvement,
        imageResId = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = resolved.definition.id,
            exerciseDbId = null,
            exerciseId = exercise.instanceId,
            catalogConfigurationId = resolved.configuration.id,
        ),
    )
}

/** El nombre de un músculo con la etiqueta de la app; sin ella, su identificador tal cual (como hace el adaptador del catálogo). */
private fun muscleNameOf(id: String): String = muscleLabel(id) ?: id

/** La ficha del detalle: el ejercicio que enseña y si está abierta. Conserva el último mientras se cierra. */
internal class ExerciseInfoState {
    var exercise by mutableStateOf<SetupPlanRevealExercise?>(null)
        private set
    var open by mutableStateOf(false)
        private set

    fun show(exercise: SetupPlanRevealExercise) {
        this.exercise = exercise
        open = true
    }

    fun close() {
        open = false
    }
}


/**
 * El wizard abre la ficha común sin pie ni historial, solo con una configuración editorial exacta.
 * Conserva la firma del detalle; la ficha administra sus propios insets, movimiento y cierre.
 */
@Composable
internal fun ExerciseInfoLayer(state: ExerciseInfoState, reduced: Boolean, topInset: Dp) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf(CatalogV2ProcessCache.peek()?.catalog) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(state.open) {
        if (state.open && catalog == null) {
            failed = false
            val loaded = runCatching {
                withContext(Dispatchers.IO) { CatalogV2ProcessCache.getOrLoad(context).catalog }
            }.getOrNull()
            catalog = loaded
            failed = loaded == null
        }
    }
    val exercise = state.exercise
    val snapshot = catalog
    val info = remember(exercise, snapshot) { exercise?.let { ex -> snapshot?.let { exerciseInfoOf(ex, it) } } }
    // La ficha solo existe si la configuración exacta se resuelve; nunca se sustituye por otra o por texto inventado.
    LaunchedEffect(state.open, exercise, catalog, failed) {
        if (state.open && ((catalog != null && info == null) || failed)) state.close()
    }
    val model = remember(exercise, snapshot, info) {
        if (exercise != null && snapshot != null && info != null) {
            val configuration = exercise.swappedConfigId ?: exercise.catalogConfigurationId
            val definition = exercise.swappedConfigId?.let { definitionIdOfConfiguration(snapshot, it) } ?: exercise.catalogDefinitionId
            resolveCatalogExerciseV2(
                Exercise(id = exercise.instanceId, name = exercise.name, catalogRevision = exercise.catalogRevision,
                    catalogDefinitionId = definition, catalogConfigurationId = configuration),
                snapshot, null,
            )?.let { com.example.kpkn.screens.sessioneditor.components.sessionExerciseDetailModel(it) }
        } else null
    }
    if (state.open && exercise != null && model != null) {
        com.example.kpkn.screens.sessioneditor.components.ExerciseFicha(
            model = model.copy(
                context = com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContext.WIZARD,
                contextLine = ExerciseInfoCopy.IN_YOUR_PROGRAM + exercise.name,
                identityKeys = emptySet(),
            ),
            onDismiss = state::close,
            showFooter = false,
        )
    }
}
