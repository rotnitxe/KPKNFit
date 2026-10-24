package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.screens.programdetail.design.volumeScopeLabelOf
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver
import com.example.kpkn.domain.exercises.MuscleHeadResolution
import com.example.kpkn.domain.exercises.exerciseDisplayName
import com.example.kpkn.domain.training.CanonicalMuscleVolumeEntry
import com.example.kpkn.domain.training.DiscomfortEntry
import com.example.kpkn.domain.training.ExerciseDiscomfortAssociationEntry
import com.example.kpkn.domain.training.ProgramAnalyticsReport
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.programdetail.design.ProgramVolumeAssociation
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCalibrationInvite
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCountControls
import com.example.kpkn.screens.programdetail.design.ProgramVolumeDiscomfort
import com.example.kpkn.screens.programdetail.design.ProgramVolumeInactive
import com.example.kpkn.screens.programdetail.design.ProgramVolumeLine
import com.example.kpkn.screens.programdetail.design.ProgramVolumeModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeNoSessions
import com.example.kpkn.screens.programdetail.design.ProgramVolumeScope
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSubMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSummaryModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.programdetail.design.programVolumeSplit
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal data class CanonicalMuscleVolumeUi(
    val muscleId: String,
    val muscleName: String,
    val weeklySets: Double,
)

internal data class PersonalizedVolumeTarget(
    val muscleName: String,
    val minEffective: Int,
    val maxAdaptive: Int,
    val maxRecoverable: Int,
)

private data class VolumeScopeOption(
    val id: String,
    val label: String,
    val detail: String,
    val weeks: List<ProgramWeek>,
    val averageByWeek: Boolean,
)

private data class IndexedVolumeWeek(
    val globalIndex: Int,
    val blockId: String,
    val blockName: String,
    val week: ProgramWeek,
)

private val canonicalMuscleCatalog = listOf(
    "Cuello",
    "Trapecio",
    "Deltoides",
    "Pectorales",
    "Bíceps",
    "Tríceps",
    "Antebrazo",
    "Dorsales",
    "Erectores Espinales",
    "Core",
    "Abdomen",
    "Glúteos",
    "Aductores",
    "Cuádriceps",
    "Isquiosurales",
    "Pantorrillas",
    "Romboides",
)

/**
 * Pestaña Volumen de Program Detail (P4). La lógica de volumen no cambia: alcances, objetivos personalizados, conteo de series, sobrecarga
 * y desglose se calculan igual. Lo que cambia es cómo se pinta: un módulo con una fila por músculo (barra por zonas, estado y desglose),
 * un resumen con el reparto y las molestias, y los vacíos honestos.
 */
@Composable
fun VolumeView(
    program: Program,
    isProgramActive: Boolean,
    hasCreatedSessions: Boolean,
    onActivateProgram: () -> Unit,
    onGoCreateSession: () -> Unit,
    onApplyVolumeCalibration: (ProgramMode, AthleteProfileScore, List<VolumeRecommendation>) -> Unit,
    programDiscomforts: List<DiscomfortEntry>,
    exerciseDiscomfortAssociations: List<ExerciseDiscomfortAssociationEntry>,
    analyticsReport: ProgramAnalyticsReport? = null,
    programLogs: List<WorkoutLog> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val volumeScopeOptions = remember(program) { buildVolumeScopeOptions(program) }
    var selectedVolumeScopeId by rememberSaveable(program.id) { mutableStateOf("") }
    var indirectMode by rememberSaveable(program.id) { mutableStateOf(0) }
    LaunchedEffect(volumeScopeOptions) {
        if (volumeScopeOptions.none { it.id == selectedVolumeScopeId }) {
            selectedVolumeScopeId = volumeScopeOptions.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedVolumeScope = remember(volumeScopeOptions, selectedVolumeScopeId) {
        volumeScopeOptions.firstOrNull { it.id == selectedVolumeScopeId } ?: volumeScopeOptions.firstOrNull()
    }
    val canonicalVolumes = remember(program, selectedVolumeScope, indirectMode) {
        mergeCanonicalVolumeCatalog(
            calculateDisplayWeeklyMuscleVolume(
                weeks = selectedVolumeScope?.weeks ?: program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks },
                averageByWeek = selectedVolumeScope?.averageByWeek ?: false,
                indirectMode = indirectMode,
            )
        )
    }
    val personalizedTargets = remember(program.volumeRecommendations) {
        mergePersonalizedTargets(program.volumeRecommendations)
    }
    val isVolumeCalibrated = remember(program.volumeRecommendations, program.athleteProfileScore) {
        program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
    }
    val completedVolumes = remember(programLogs) {
        VolumeCalculator.calculateCompletedWeeklyMuscleVolume(
            logs = programLogs,
            exerciseList = catalogExerciseIndex().values.toList(),
            aliases = catalogSearchRedirects(),
            weeksCount = program.volumeRecommendations.firstOrNull()?.let {
                (programLogs.size / 3).coerceAtLeast(1)
            } ?: 1
        )
    }
    val totalWeeklySets = remember(canonicalVolumes) { canonicalVolumes.sumOf { it.weeklySets } }
    val activeMuscles = remember(canonicalVolumes) { canonicalVolumes.count { it.weeklySets > 0.0 } }
    val topMuscle = remember(canonicalVolumes) { canonicalVolumes.maxByOrNull { it.weeklySets } }

    var showCalibrationSheet by rememberSaveable { mutableStateOf(false) }
    var expandedMuscleName by rememberSaveable(program.id) { mutableStateOf<String?>(null) }

    // Los alcances tienen tipo (Semana, Bloque, Programa): el selector recorre los de un mismo tipo y el segmento cambia de tipo.
    val scopeKinds = remember(volumeScopeOptions) { volumeScopeOptions.map { it.kindKey() }.distinct() }
    val selectedKey = selectedVolumeScope?.kindKey()
    val selectedKind = selectedKey?.let { scopeKinds.indexOf(it) }?.coerceAtLeast(0) ?: 0
    val optionsOfKind = volumeScopeOptions.filter { it.kindKey() == selectedKey }
    val position = optionsOfKind.indexOfFirst { it.id == selectedVolumeScope?.id }

    // Prioridades del usuario (bolsa de orden del programa, por músculo): van delante; luego, por series.
    val priorityByMuscle = remember(program.planOrderPriorities) {
        program.planOrderPriorities.orEmpty().entries
            .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.key) }
            .mapValues { (_, entries) -> entries.sumOf { it.value }.toDouble() }
    }
    val muscleRows = remember(canonicalVolumes, personalizedTargets, isVolumeCalibrated, completedVolumes, priorityByMuscle) {
        val fallbackMax = max(canonicalVolumes.maxOfOrNull { it.weeklySets } ?: 0.0, 1.0)
        canonicalVolumes
            .sortedWith(
                compareByDescending<CanonicalMuscleVolumeUi> { priorityByMuscle[it.muscleName] ?: 0.0 }
                    .thenByDescending { it.weeklySets },
            )
            .map { entry ->
                val target = if (isVolumeCalibrated) personalizedTargets[entry.muscleName] else null
                val zones = target?.let { ProgramVolumeZones(it.minEffective, it.maxAdaptive, it.maxRecoverable) }
                val canonicalName = VolumeCalculator.normalizeCanonicalMuscleGroup(entry.muscleName)
                val completed = completedVolumes.find { it.muscleName == canonicalName }?.weeklySets ?: 0.0
                ProgramVolumeMuscle(
                    name = entry.muscleName,
                    sets = entry.weeklySets,
                    zones = zones,
                    real = completed.takeIf { it > 0.0 },
                    scaleMax = if (zones != null) {
                        max(entry.weeklySets, zones.maxRecoverable.toDouble()).coerceAtLeast(1.0)
                    } else {
                        fallbackMax.coerceAtLeast(1.0)
                    },
                )
            }
    }

    // El desglose solo se calcula para la fila desplegada.
    val breakdown = remember(expandedMuscleName, selectedVolumeScope, indirectMode) {
        expandedMuscleName?.let { muscle ->
            val weeks = selectedVolumeScope?.weeks ?: emptyList()
            val averageByWeek = selectedVolumeScope?.averageByWeek ?: false
            val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
            ProgramVolumeBreakdown(
                exercises = calculateExerciseBreakdownForMuscle(muscle, weeks, averageByWeek, indirectMode)
                    .map { ProgramVolumeLine(name = it.exerciseName, sets = it.weeklySetsContribution) },
                subMuscles = calculateSubMuscleBreakdown(
                    canonicalMuscle = muscle,
                    sessions = weeks.flatMap { it.sessions },
                    exerciseIndex = catalogExerciseIndex(),
                    divisor = divisor,
                    indirectMode = indirectMode,
                ).map { sub ->
                    ProgramVolumeSubMuscle(
                        name = sub.subMuscleName,
                        sets = sub.weeklySets,
                        exercises = sub.exercises.map { ProgramVolumeLine(name = it.first, sets = it.second) },
                    )
                },
            )
        }
    }

    val split = remember(canonicalVolumes) {
        programVolumeSplit(canonicalVolumes.map { it.muscleName to it.weeklySets })
    }
    val discomfortRows = remember(programDiscomforts) {
        programDiscomforts.take(4).map { ProgramVolumeDiscomfort(name = it.name, count = it.count) }
    }
    val associationRows = remember(exerciseDiscomfortAssociations) {
        exerciseDiscomfortAssociations.take(6).map {
            ProgramVolumeAssociation(exercise = it.exerciseName, discomfort = it.discomfortLabel, count = it.count)
        }
    }

    val scope = ProgramVolumeScope(
        label = volumeScopeLabelOf(selectedVolumeScope?.label ?: "Sin alcance", simpleProgram = program.isSimpleProgram),
        detail = selectedVolumeScope?.detail.orEmpty(),
        kinds = scopeKinds.map(::volumeKindLabel),
        selectedKind = selectedKind,
        canPrevious = position > 0,
        canNext = position >= 0 && position < optionsOfKind.lastIndex,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isProgramActive) {
            ProgramVolumeInactive(onActivate = onActivateProgram)
            Spacer(Modifier.height(120.dp))
            return@Column
        }

        if (!hasCreatedSessions) {
            ProgramVolumeNoSessions(onAddSession = onGoCreateSession)
            Spacer(Modifier.height(120.dp))
            return@Column
        }

        if (!isVolumeCalibrated || program.athleteProfileScore == null) {
            ProgramVolumeCalibrationInvite(
                onCalibrate = { showCalibrationSheet = true },
                note = if (!isVolumeCalibrated) "Sin calibrar: las zonas aparecen al calibrar" else null,
            )
        }

        ProgramVolumeModule(
            muscles = muscleRows,
            scope = scope,
            onSelectKind = { index ->
                scopeKinds.getOrNull(index)?.let { key ->
                    volumeScopeOptions.firstOrNull { it.kindKey() == key }?.let { selectedVolumeScopeId = it.id }
                }
            },
            onPreviousScope = {
                if (position > 0) selectedVolumeScopeId = optionsOfKind[position - 1].id
            },
            onNextScope = {
                if (position in 0 until optionsOfKind.lastIndex) selectedVolumeScopeId = optionsOfKind[position + 1].id
            },
            expandedMuscle = expandedMuscleName,
            onToggleMuscle = { name -> expandedMuscleName = if (expandedMuscleName == name) null else name },
            breakdown = breakdown,
            onCalibrate = { showCalibrationSheet = true },
            controls = {
                ProgramVolumeCountControls(
                    indirectMode = indirectMode,
                    onIndirectMode = { indirectMode = it },
                )
            },
        )

        ProgramVolumeSummaryModule(
            totalSets = totalWeeklySets,
            activeMuscles = activeMuscles,
            topMuscle = topMuscle?.muscleName,
            split = split,
            discomforts = discomfortRows,
            associations = associationRows,
        )

        analyticsReport?.let { report ->
            VolumeAnalyticsCard(report = report)
        }

        Spacer(Modifier.height(120.dp))
    }

    if (showCalibrationSheet) {
        VolumeCalibrationSheet(
            currentMode = program.mode,
            onDismiss = { showCalibrationSheet = false },
            onSave = { result ->
                onApplyVolumeCalibration(result.mode, result.score, result.recommendations)
                showCalibrationSheet = false
            },
        )
    }
}

/** Tipo de un alcance según su identificador: programa completo, bloque o semana. */
private fun VolumeScopeOption.kindKey(): String = id.substringBefore(':')

/** Rótulo del tipo de alcance en el segmento. */
private fun volumeKindLabel(key: String): String = when (key) {
    "macro" -> "Programa"
    "block" -> "Bloque"
    else -> "Semana"
}

private fun mergeCanonicalVolumeCatalog(
    entries: List<CanonicalMuscleVolumeEntry>,
): List<CanonicalMuscleVolumeUi> {
    val byName = entries.associateBy { it.muscleName }
    return canonicalMuscleCatalog.map { muscleName ->
        val existing = byName[muscleName]
        CanonicalMuscleVolumeUi(
            muscleId = existing?.muscleId ?: muscleName.lowercase().replace(" ", "-"),
            muscleName = muscleName,
            weeklySets = existing?.weeklySets ?: 0.0,
        )
    }
}

private fun buildVolumeScopeOptions(program: Program): List<VolumeScopeOption> {
    val indexedWeeks = program.indexedVolumeWeeks()
    if (indexedWeeks.isEmpty()) return emptyList()

    if (program.isSimpleTemporalProgram) {
        return indexedWeeks.map { item ->
            VolumeScopeOption(
                id = "week:${item.week.id}",
                label = "S${item.globalIndex + 1} · ${weekScopeName(item.week)}",
                detail = "Conteo exacto de esa semana.",
                weeks = listOf(item.week),
                averageByWeek = false,
            )
        }
    }

    val options = mutableListOf<VolumeScopeOption>()
    options += VolumeScopeOption(
        id = "macro:average",
        label = "Programa completo",
        detail = "Promedio semanal de ${indexedWeeks.size} semanas.",
        weeks = indexedWeeks.map { it.week },
        averageByWeek = true,
    )

    indexedWeeks
        .groupBy { it.blockId }
        .values
        .filter { it.isNotEmpty() }
        .forEach { blockWeeks ->
            val first = blockWeeks.first()
            options += VolumeScopeOption(
                id = "block:${first.blockId}",
                label = first.blockName,
                detail = "Promedio semanal del bloque (${blockWeeks.size} semanas).",
                weeks = blockWeeks.map { it.week },
                averageByWeek = true,
            )
        }

    indexedWeeks.forEach { item ->
        options += VolumeScopeOption(
            id = "week:${item.week.id}",
            label = "S${item.globalIndex + 1} · ${weekScopeName(item.week)}",
            detail = "Conteo exacto de esa semana.",
            weeks = listOf(item.week),
            averageByWeek = false,
        )
    }

    return options.distinctBy { it.id }
}

private fun Program.indexedVolumeWeeks(): List<IndexedVolumeWeek> {
    val result = mutableListOf<IndexedVolumeWeek>()
    macrocycles.forEach { macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    result += IndexedVolumeWeek(
                        globalIndex = result.size,
                        blockId = block.id,
                        blockName = block.name,
                        week = week,
                    )
                }
            }
        }
    }
    return result
}
private fun weekScopeName(week: ProgramWeek): String {
    val date = week.startDate?.let(::parseIsoDateExact)?.format(monthDayFormatter)
    return if (date != null && !week.name.contains(date)) {
        "${week.name} · $date"
    } else {
        week.name
    }
}

private fun mergePersonalizedTargets(
    recommendations: List<VolumeRecommendation>,
): Map<String, PersonalizedVolumeTarget> {
    val baseMap = recommendations
        .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
        .mapValues { (muscleName, groupedRecommendations) ->
            PersonalizedVolumeTarget(
                muscleName = muscleName,
                minEffective = groupedRecommendations.sumOf { it.minEffectiveVolume },
                maxAdaptive = groupedRecommendations.sumOf { it.maxAdaptiveVolume },
                maxRecoverable = groupedRecommendations.sumOf { it.maxRecoverableVolume },
            )
        }
        .toMutableMap()

    if (recommendations.isNotEmpty()) {
        // 1. Romboides -> Trapecio
        if (!baseMap.containsKey("Romboides")) {
            baseMap["Trapecio"]?.let { target ->
                baseMap["Romboides"] = target.copy(muscleName = "Romboides")
            } ?: run {
                baseMap["Romboides"] = PersonalizedVolumeTarget("Romboides", 4, 15, 20)
            }
        }
        // 2. Cuello -> Trapecio
        if (!baseMap.containsKey("Cuello")) {
            baseMap["Trapecio"]?.let { target ->
                baseMap["Cuello"] = target.copy(muscleName = "Cuello")
            } ?: run {
                baseMap["Cuello"] = PersonalizedVolumeTarget("Cuello", 4, 15, 20)
            }
        }
        // 3. Antebrazo -> Bíceps
        if (!baseMap.containsKey("Antebrazo")) {
            baseMap["Bíceps"]?.let { target ->
                baseMap["Antebrazo"] = target.copy(muscleName = "Antebrazo")
            } ?: run {
                baseMap["Antebrazo"] = PersonalizedVolumeTarget("Antebrazo", 6, 16, 20)
            }
        }
        // 4. Aductores -> Isquiosurales (or Cuádriceps)
        if (!baseMap.containsKey("Aductores")) {
            val similarTarget = baseMap["Isquiosurales"] ?: baseMap["Cuádriceps"]
            similarTarget?.let { target ->
                baseMap["Aductores"] = target.copy(muscleName = "Aductores")
            } ?: run {
                baseMap["Aductores"] = PersonalizedVolumeTarget("Aductores", 6, 18, 22)
            }
        }
        // 5. Core -> Abdomen
        if (!baseMap.containsKey("Core")) {
            baseMap["Abdomen"]?.let { target ->
                baseMap["Core"] = target.copy(muscleName = "Core")
            } ?: run {
                baseMap["Core"] = PersonalizedVolumeTarget("Core", 4, 12, 16)
            }
        }
    }

    return baseMap
}

internal data class ExerciseVolumeBreakdown(
    val exerciseName: String,
    val weeklySetsContribution: Double,
    val totalSets: Int
)

private fun countEffectiveSets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>): Int {
    val counted = exerciseSets.count { set ->
        !set.isIneffective && ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    return if (counted == 0) exerciseSets.count { !it.isIneffective } else counted
}

private fun calculateExerciseBreakdownForMuscle(
    muscleName: String,
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    indirectMode: Int = 0,
): List<ExerciseVolumeBreakdown> {
    val exerciseIndex = catalogExerciseIndex()
    val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
    val breakdownMap = mutableMapOf<String, Pair<Double, Int>>()

    weeks.flatMap { it.sessions }.flatMap { it.allExercises() }.forEach { exercise ->
        val countedSets = countEffectiveSets(exercise.sets)
        if (countedSets > 0) {
            val musclesToCount = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)
                .filter { involvement -> roleIncludedInMode(involvement.role, indirectMode) }
            val contributions = VolumeCalculator.buildPerExerciseMuscleContributions(musclesToCount)
            val multiplier = contributions[muscleName]
            if (multiplier != null && multiplier > 0.0) {
                // Agrupamos por exerciseDbId (fallback a nombre) para evitar fusionar
                // ejercicios distintos que casualmente compartan el mismo nombre.
                val key = exercise.exerciseDbId?.takeIf { it.isNotBlank() } ?: exercise.name
                val currentVal = breakdownMap[key] ?: (0.0 to 0)
                breakdownMap[key] = (currentVal.first + countedSets * multiplier) to (currentVal.second + countedSets)
            }
        }
    }

    return breakdownMap.entries
        .map { (key, pair) ->
            // Recuperamos el nombre del ejercicio para mostrar en UI
            val displayName = weeks.flatMap { it.sessions }.flatMap { it.allExercises() }
                .firstOrNull { (it.exerciseDbId?.takeIf { id -> id.isNotBlank() } ?: it.name) == key }
                ?.let { exerciseDisplayName(it, exerciseIndex) } ?: key
            ExerciseVolumeBreakdown(
                exerciseName = displayName,
                weeklySetsContribution = (pair.first / divisor * 10.0).toInt() / 10.0,
                totalSets = pair.second
            )
        }
        .filter { it.weeklySetsContribution > 0.0 }
        .sortedByDescending { it.weeklySetsContribution }
}

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private data class SubMuscleContribution(
    val subMuscleName: String,
    val weeklySets: Double,
    val exercises: List<Pair<String, Double>> // exerciseName to sets
)

private fun resolveSpecificSubMuscle(muscle: String, emphasis: String?): String {
    return MuscleHeadResolution.resolveDisplayHead(muscle, emphasis) ?: muscle
}

private fun com.example.kpkn.data.models.ExerciseSet.effectiveTargetRpe(): Double {
    if (isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

private fun countDisplaySets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>): Double {
    var total = 0.0
    val activeSets = exerciseSets.filterNot { it.isIneffective }
    val counted = activeSets.filter { set ->
        ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    val targetList = if (counted.isEmpty()) activeSets else counted
    total = targetList.size.toDouble()
    return total
}

private fun calculateDisplayWeeklyMuscleVolume(
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    indirectMode: Int,
): List<CanonicalMuscleVolumeEntry> {
    val sessions = weeks.flatMap { it.sessions }
    val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
    val roleSeparated = VolumeCalculator.calculateRoleSeparatedMuscleVolume(
        sessions = sessions,
        exerciseList = catalogExerciseIndex().values.toList(),
        aliases = catalogSearchRedirects(),
        divisor = divisor,
    )

    return roleSeparated.map { (muscleName, volume) ->
        val selectedSets = when (indirectMode) {
            2 -> volume.totalSets
            1 -> volume.directSets + volume.secondarySets
            else -> volume.directSets
        }
        CanonicalMuscleVolumeEntry(
            muscleId = muscleName.lowercase().replace(" ", "-"),
            muscleName = muscleName,
            weeklySets = (selectedSets * 10.0).toInt() / 10.0
        )
    }.sortedByDescending { it.weeklySets }
}

/** Which roles count for a given volume-alcance mode: 0 = direct, 1 = +secondary, 2 = +stabilizer. */
private fun roleIncludedInMode(
    role: com.example.kpkn.data.models.MuscleRole,
    indirectMode: Int,
): Boolean = when (role) {
    com.example.kpkn.data.models.MuscleRole.PRIMARY -> true
    com.example.kpkn.data.models.MuscleRole.SECONDARY -> indirectMode >= 1
    com.example.kpkn.data.models.MuscleRole.STABILIZER -> indirectMode >= 2
    com.example.kpkn.data.models.MuscleRole.NEUTRALIZER -> false
}

private fun calculateSubMuscleBreakdown(
    canonicalMuscle: String,
    sessions: List<com.example.kpkn.data.models.Session>,
    exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    divisor: Double,
    indirectMode: Int,
): List<SubMuscleContribution> {
    val targetSubMuscles = when (canonicalMuscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio", "Glúteo Menor")
        "Pectorales" -> listOf("Pectoral Superior", "Pectoral Medio", "Pectoral Inferior")
        "Trapecio" -> listOf("Trapecio Superior", "Trapecio Medio", "Trapecio Inferior")
        "Pantorrillas" -> listOf("Gastrocnemio", "Sóleo")
        else -> return emptyList()
    }

    val subMuscleVolumes = targetSubMuscles.associateWith { mutableMapOf<String, Double>() }.toMutableMap()

    for (session in sessions) {
        for (exercise in session.allExercises()) {
            val effectiveSets = countDisplaySets(exercise.sets)
            if (effectiveSets <= 0.0) continue
            val musclesToCount = ExerciseMuscleResolver.effectiveMusclesForVolume(exercise, exerciseIndex)

            musclesToCount.forEach { involvement ->
                if (roleIncludedInMode(involvement.role, indirectMode)) {
                    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
                    if (canonical == canonicalMuscle) {
                        val subMuscle = resolveSpecificSubMuscle(involvement.muscle, involvement.emphasis)
                        val map = subMuscleVolumes[subMuscle]
                        if (map != null) {
                            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
                            val displayName = exerciseDisplayName(exercise, exerciseIndex)
                            val current = map[displayName] ?: 0.0
                            if (effectiveSets * contribution > current) {
                                map[displayName] = effectiveSets * contribution
                            }
                        }
                    }
                }
            }
        }
    }

    return targetSubMuscles.map { subName ->
        val exerciseMap = subMuscleVolumes[subName] ?: emptyMap()
        val totalSets = exerciseMap.values.sum()
        SubMuscleContribution(
            subMuscleName = subName,
            weeklySets = (totalSets / divisor * 10.0).toInt() / 10.0,
            exercises = exerciseMap.entries
                .map { it.key to (it.value / divisor * 10.0).toInt() / 10.0 }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second }
        )
    }
}
