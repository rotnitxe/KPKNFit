package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.sheets.resolveCatalogExerciseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exact v2 editorial payload for one persisted exercise occurrence. */
internal data class ResolvedCatalogExerciseV2(
    val definition: ExerciseDefinitionV2,
    val configuration: ExerciseConfigurationV2,
    val legacyInfo: ExerciseMuscleInfo,
)

/**
 * Resolves the same definition/configuration pair used by the v2 picker.
 *
 * The persisted ids are authoritative. A legacy catalog row is accepted only
 * as an identity bridge when the Exercise predates the v2 fields; prose and
 * involvement still come from the current v2 catalog whenever that bridge is
 * exact.
 */
internal fun resolveCatalogExerciseV2(
    exercise: Exercise,
    catalog: ExerciseCatalogV2?,
    legacyInfo: ExerciseMuscleInfo?,
): ResolvedCatalogExerciseV2? {
    catalog ?: return null

    val revision = exercise.catalogRevision
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogRevision?.takeIf { it.isNotBlank() }
    if (revision != null && revision != catalog.catalogRevision) return null

    val definitionId = exercise.catalogDefinitionId
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogDefinitionId?.takeIf { it.isNotBlank() }
    val configurationId = exercise.catalogConfigurationId
        ?.takeIf { it.isNotBlank() }
        ?: legacyInfo?.catalogConfigurationId?.takeIf { it.isNotBlank() }

    val definition = if (definitionId != null) {
        catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .firstOrNull { it.id == definitionId }
    } else {
        val lookupKeys = listOfNotNull(exercise.exerciseDbId, exercise.id, legacyInfo?.id)
            .map { it.trim().lowercase() }
        catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .firstOrNull { def ->
                def.id.lowercase() in lookupKeys || def.configurations.any { it.id.lowercase() in lookupKeys }
            } ?: run {
                val cleanTargetName = exercise.name.trim().lowercase()
                catalog.families
                    .asSequence()
                    .flatMap { it.definitions.asSequence() }
                    .firstOrNull { def ->
                        def.canonicalName.trim().lowercase() == cleanTargetName
                    }
            }
    } ?: return null

    val configuration = (if (configurationId != null) {
        definition.configurations.firstOrNull { it.id == configurationId }
    } else null)
        ?: definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
        ?: definition.configurations.firstOrNull()
        ?: return null

    val exactInfo = catalog.toLegacySelection(
        ExerciseSelectionV2(
            definitionId = definition.id,
            configurationId = configuration.id,
            catalogRevision = catalog.catalogRevision,
        ),
    ) ?: legacyInfo ?: ExerciseMuscleInfo(
        id = configuration.id,
        name = definition.canonicalName,
        description = definition.description,
        involvedMuscles = emptyList(),
    )

    return ResolvedCatalogExerciseV2(
        definition = definition,
        configuration = configuration,
        legacyInfo = exactInfo,
    )
}

/**
 * Implemento que nombra la línea bajo el título de la ficha (CF1): el elegido ahora y, si no hay ninguno, el de la configuración por defecto.
 * «default» y los valores vacíos no cuentan. Usa el mismo rótulo que la tarjeta del catálogo.
 */
internal fun catalogFichaMaterialLabel(
    selectedImplementation: String?,
    defaultImplementation: String?,
    definitionId: String,
): String? {
    val value = selectedImplementation?.takeIf { it.isNotBlank() && it != "default" }
        ?: defaultImplementation?.takeIf { it.isNotBlank() && it != "default" }
    return value?.let { exerciseCatalogOptionLabel(it, definitionId) }
}

/**
 * Modelo de la ficha de un ejercicio que ya está en la sesión (ER1f), armado como lo arma el catálogo (`ExercisePickerV2Catalog`): la
 * misma etiqueta de músculo, de implemento y de patrón; las mismas imágenes; la configuración como línea de lectura. Sin ejes elegibles:
 * desde el editor la ficha no cambia el ejercicio («Cambiar» vive en sus acciones rápidas).
 */
internal fun sessionExerciseDetailModel(resolved: ResolvedCatalogExerciseV2): CatalogDetailModel {
    val definition = resolved.definition
    val configuration = resolved.configuration
    val default = definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
    val selectedImplementation = configuration.selectedOptions["implement"]
    val pattern = exercisePatternOf(definition.canonicalName)
    val muscleLabel = default?.profile?.primaryMuscles?.firstOrNull()?.let(::exerciseCatalogMuscleLabel)
    val fichaMaterialLabel = catalogFichaMaterialLabel(
        selectedImplementation = selectedImplementation,
        defaultImplementation = default?.selectedOptions?.get("implement"),
        definitionId = definition.id,
    )
    return CatalogDetailModel(
        name = definition.canonicalName,
        caption = listOfNotNull(
            muscleLabel,
            fichaMaterialLabel,
            pattern.label.takeIf { pattern != ExercisePattern.GENERIC },
        ).joinToString(" · ").takeIf { it.isNotBlank() },
        description = definition.description.takeIf { it.isNotBlank() },
        imageVariants = exerciseCatalogImageVariants(
            definitionId = definition.id,
            catalogConfigurationId = configuration.id,
            selectedImplementation = selectedImplementation,
        ),
        selectedImplementation = selectedImplementation,
        axes = emptyList(),
        configurationLine = exerciseCatalogConfigurationSummary(configuration, definition.id),
        configurationIncomplete = false,
        muscleInfo = resolved.legacyInfo.takeIf { it.involvedMuscles.isNotEmpty() },
        joints = configuration.profile.jointInvolvement,
        patternId = configuration.profile.movementPatternId,
        isSelected = true,
        canSelect = false,
        setupCues = configuration.profile.setupCues,
        executionCues = configuration.profile.executionCues,
        identityKeys = setOf(definition.id, configuration.id),
        context = ExerciseFichaContext.SESSION,
    )
}

/** Ficha de respaldo sin catálogo v2 (aún cargando, o un ejercicio sin ficha editorial): nombre, descripción y músculos conocidos. */
internal fun sessionExerciseFallbackDetailModel(exercise: Exercise, info: ExerciseMuscleInfo?): CatalogDetailModel = CatalogDetailModel(
    name = exercise.name,
    caption = null,
    description = info?.description?.takeIf { it.isNotBlank() },
    imageVariants = emptyList(),
    selectedImplementation = null,
    axes = emptyList(),
    configurationLine = null,
    configurationIncomplete = false,
    muscleInfo = info,
    joints = emptyList(),
    patternId = info?.movementPattern,
    isSelected = true,
    canSelect = false,
    setupCues = info?.setupCues.orEmpty(),
    executionCues = info?.executionCues.orEmpty(),
    identityKeys = com.example.kpkn.domain.exercises.exerciseFichaIdentityKeys(exercise),
    context = ExerciseFichaContext.SESSION,
)

/**
 * La ficha de un ejercicio de la sesión, abierta desde «Ficha» de sus acciones rápidas (ER1f). Carga el catálogo v2 igual que esa hoja y
 * pinta la misma `ExerciseCatalogInfoDialog` sin el pie «Añadir/Quitar».
 */
@Composable
internal fun SessionExerciseDetailSheet(exercise: Exercise, onDismiss: () -> Unit, onShowHistory: (() -> Unit)? = null) {
    val context = LocalContext.current
    var catalogEntry by remember { mutableStateOf(CatalogV2ProcessCache.peek()) }
    LaunchedEffect(context) {
        if (catalogEntry == null) {
            catalogEntry = runCatching {
                withContext(Dispatchers.IO) { CatalogV2ProcessCache.getOrLoad(context) }
            }.getOrNull()
        }
    }
    val model = remember(exercise, catalogEntry) {
        val legacyInfo = resolveCatalogExerciseInfo(exercise, catalogExerciseIndex())
        val resolved = resolveCatalogExerciseV2(exercise, catalogEntry?.catalog, legacyInfo)
        if (resolved != null) {
            sessionExerciseDetailModel(resolved)
        } else {
            sessionExerciseFallbackDetailModel(exercise, legacyInfo)
        }
    }
    ExerciseFicha(
        model = model.copy(identityKeys = model.identityKeys + com.example.kpkn.domain.exercises.exerciseFichaIdentityKeys(exercise)),
        onShowHistory = onShowHistory,
        onDismiss = onDismiss,
        onToggleSelection = {},
        onSelectOption = { _, _ -> },
        onImplementationSettled = {},
        showFooter = false,
    )
}
