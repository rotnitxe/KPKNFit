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
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.screens.programdetail.design.volumeScopeLabelOf
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.training.CanonicalMuscleVolumeEntry
import com.example.kpkn.domain.training.DiscomfortEntry
import com.example.kpkn.domain.training.ExerciseDiscomfortAssociationEntry
import com.example.kpkn.domain.training.ProgramAnalyticsReport
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.TrainingVolumeResult
import com.example.kpkn.domain.training.TrainingWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.programdetail.MuscleOvertrainingStatus
import com.example.kpkn.screens.programdetail.design.ProgramVolumeAssociation
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCalibrationInvite
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCountControls
import com.example.kpkn.screens.programdetail.design.ProgramVolumeDiscomfort
import com.example.kpkn.screens.programdetail.design.ProgramVolumeInactive
import com.example.kpkn.screens.programdetail.design.ProgramVolumeModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeNoSessions
import com.example.kpkn.screens.programdetail.design.ProgramVolumeScope
import com.example.kpkn.screens.programdetail.design.ProgramVolumeSummaryModule
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.programdetail.design.programVolumeSplit
import com.example.kpkn.screens.programdetail.design.programVolumeBreakdown
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.ZoneId
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
 * Plan y realización comparten ventana, modo y ajuste por intensidad. Los desgloses explican la misma cifra que la barra.
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
    muscleCdbsStatus: Map<String, MuscleOvertrainingStatus> = emptyMap(),
    programLogs: List<WorkoutLog> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val volumeScopeOptions = remember(program) { buildVolumeScopeOptions(program) }
    var selectedVolumeScopeId by rememberSaveable(program.id) { mutableStateOf("") }
    var indirectMode by rememberSaveable(program.id) { mutableStateOf(0) }
    var adjustVolumeByIntensity by rememberSaveable(program.id) { mutableStateOf(false) }
    LaunchedEffect(volumeScopeOptions) {
        if (volumeScopeOptions.none { it.id == selectedVolumeScopeId }) {
            selectedVolumeScopeId = volumeScopeOptions.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedVolumeScope = remember(volumeScopeOptions, selectedVolumeScopeId) {
        volumeScopeOptions.firstOrNull { it.id == selectedVolumeScopeId } ?: volumeScopeOptions.firstOrNull()
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val volume = remember(program, selectedVolumeScope, programLogs, indirectMode, adjustVolumeByIntensity, zone, today) {
        calculateProgramVolume(
            program = program,
            weeks = selectedVolumeScope?.weeks.orEmpty(),
            averageByWeek = selectedVolumeScope?.averageByWeek ?: false,
            logs = programLogs,
            exerciseIndex = catalogExerciseIndex(),
            indirectMode = indirectMode,
            adjustByIntensity = adjustVolumeByIntensity,
            zone = zone,
            today = today,
        )
    }
    val canonicalVolumes = remember(volume) { mergeCanonicalVolumeCatalog(volume.planned.weeklyEntries()) }
    val personalizedTargets = remember(program.volumeRecommendations) {
        mergePersonalizedTargets(program.volumeRecommendations)
    }
    val isVolumeCalibrated = remember(program.volumeRecommendations, program.athleteProfileScore) {
        program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
    }
    val completedVolumes = volume.completed
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
    val muscleRows = remember(canonicalVolumes, personalizedTargets, isVolumeCalibrated, completedVolumes, muscleCdbsStatus, priorityByMuscle) {
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
                val status = muscleCdbsStatus[canonicalName]
                val completed = completedVolumes.muscles[canonicalName]?.sets ?: 0.0
                ProgramVolumeMuscle(
                    name = entry.muscleName,
                    sets = entry.weeklySets,
                    zones = zones,
                    real = completed.takeIf { programLogs.isNotEmpty() },
                    overtrained = status?.isOvertrained == true,
                    overreaching = status?.isOverreaching == true,
                    scaleMax = if (zones != null) {
                        max(entry.weeklySets, zones.maxRecoverable.toDouble()).coerceAtLeast(1.0)
                    } else {
                        fallbackMax.coerceAtLeast(1.0)
                    },
                )
            }
    }

    // El cálculo ya contiene los contribuyentes; solo se proyecta la fila desplegada.
    val breakdown = remember(expandedMuscleName, volume) {
        expandedMuscleName?.let { muscle ->
            programVolumeBreakdown(muscle, volume.planned.muscles[muscle])
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
        detail = selectedVolumeScope?.detail.orEmpty() + " ${volume.window.start} – ${volume.window.endInclusive}.",
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
            unassignedCount = completedVolumes.unassignedCount,
            controls = {
                ProgramVolumeCountControls(
                    indirectMode = indirectMode,
                    onIndirectMode = { indirectMode = it },
                    adjustByIntensity = adjustVolumeByIntensity,
                    onAdjustByIntensity = { adjustVolumeByIntensity = it },
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

internal data class ProgramVolumeCalculation(
    val window: TrainingWindow,
    val planned: TrainingVolumeResult,
    val completed: TrainingVolumeResult,
)

/** Proyección compartida por el plan, «Real» y sus desgloses; calibrar no altera la contabilidad. */
internal fun calculateProgramVolume(
    program: Program,
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    logs: List<WorkoutLog>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    indirectMode: Int = 0,
    adjustByIntensity: Boolean = false,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): ProgramVolumeCalculation {
    val window = TrainingWindow.forProgramScope(program, weeks, averageByWeek, zone, today)
    val mode = when (indirectMode) {
        2 -> VolumeCountingMode.STABILIZER
        1 -> VolumeCountingMode.SECONDARY
        else -> VolumeCountingMode.DIRECT
    }
    return ProgramVolumeCalculation(
        window = window,
        planned = VolumeCalculator.calculateVolume(
            sessions = weeks.flatMap { it.sessions },
            exerciseIndex = exerciseIndex,
            divisor = window.weekDivisor,
            mode = mode,
            adjustByIntensity = adjustByIntensity,
        ),
        completed = VolumeCalculator.calculateCompletedVolume(
            logs = logs,
            exerciseIndex = exerciseIndex,
            window = window,
            mode = mode,
            adjustByIntensity = adjustByIntensity,
            programId = program.id,
        ),
    )
}

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
