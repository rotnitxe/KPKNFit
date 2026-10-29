package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.TrainingVolumeResult
import com.example.kpkn.domain.training.TrainingWindow
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.programdetail.design.programVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.progress.ProgressVolumeEmpty
import com.example.kpkn.screens.programdetail.design.progress.ProgressVolumeHistoryLink
import com.example.kpkn.screens.programdetail.design.progress.ProgressVolumeModule
import com.example.kpkn.screens.programdetail.design.progress.RangesOrigin
import com.example.kpkn.screens.programdetail.design.progress.lowerFirst
import com.example.kpkn.screens.programdetail.design.progress.rangesOriginLabel
import com.example.kpkn.screens.progress.LocalMuscleVolumePanelOpener
import java.time.LocalDate
import java.time.ZoneId

internal data class PersonalizedVolumeTarget(
    val muscleName: String,
    val minEffective: Int,
    val maxAdaptive: Int,
    val maxRecoverable: Int,
)

/**
 * Los músculos de «Series por músculo» en el orden del lienzo (g-volume): pierna, torso, brazos y gemelo. Los nueve primeros salen
 * siempre (el lienzo enseña «Pantorrillas 0 / 0»); el resto, solo si tiene series planificadas o hechas.
 */
internal val canvasMuscleOrder = listOf(
    "Cuádriceps", "Isquiosurales", "Glúteos", "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Pantorrillas",
    "Trapecio", "Romboides", "Erectores Espinales", "Aductores", "Abdomen", "Core", "Antebrazo", "Cuello",
)
private const val CANVAS_ALWAYS_SHOWN = 9

/** Las indirectas cuentan la mitad: el modo del lienzo («Indirectas: ½»), fijo. */
private const val INDIRECT_HALF = 1

/**
 * Las filas de «Series por músculo»: [planned] y [done] por músculo canónico, las zonas de [targets] (null si no está calibrado) y el
 * orden del lienzo, con las prioridades del usuario delante. [hasLogs] = hay registros: sin ellos no hay cifra de lo hecho.
 */
internal fun volumeMuscleRows(
    planned: Map<String, Double>,
    done: Map<String, Double>,
    targets: Map<String, PersonalizedVolumeTarget>?,
    priorityByMuscle: Map<String, Double>,
    hasLogs: Boolean,
): List<ProgramVolumeMuscle> = canvasMuscleOrder.withIndex()
    .mapNotNull { (index, name) ->
        val plan = planned[name] ?: 0.0
        val real = done[name] ?: 0.0
        if (index >= CANVAS_ALWAYS_SHOWN && plan <= 0.0 && real <= 0.0) return@mapNotNull null
        ProgramVolumeMuscle(
            name = name,
            sets = plan,
            zones = targets?.get(name)?.let { ProgramVolumeZones(it.minEffective, it.maxAdaptive, it.maxRecoverable) },
            real = real.takeIf { hasLogs },
            scaleMax = 0.0,
        )
    }
    .sortedByDescending { priorityByMuscle[it.name] ?: 0.0 }

/**
 * «Series por músculo» de la pestaña Progreso (lienzo v6, g-volume) para las semanas del alcance de arriba: [weeks] y, si
 * [averageByWeek], su promedio semanal. Lo hecho y lo planificado comparten ventana; las indirectas cuentan la mitad. Sin programa activo
 * o sin sesiones, el módulo dice por qué y ofrece la salida. Debajo, «Ver lo que has hecho en 4 semanas».
 */
@Composable
internal fun VolumeView(
    program: Program,
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    isProgramActive: Boolean,
    hasCreatedSessions: Boolean,
    onActivateProgram: () -> Unit,
    onGoCreateSession: () -> Unit,
    programLogs: List<WorkoutLog>,
    rangesOrigin: RangesOrigin,
    onOpenRanges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val volume = remember(program, weeks, averageByWeek, programLogs, zone, today) {
        calculateProgramVolume(
            program = program,
            weeks = weeks,
            averageByWeek = averageByWeek,
            logs = programLogs,
            exerciseIndex = catalogExerciseIndex(),
            indirectMode = INDIRECT_HALF,
            adjustByIntensity = false,
            zone = zone,
            today = today,
        )
    }
    val calibrated = program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
    val targets = remember(program.volumeRecommendations, calibrated) {
        if (calibrated) mergePersonalizedTargets(program.volumeRecommendations) else null
    }
    // Prioridades del usuario (bolsa de orden del programa, por músculo): van delante; luego, el orden del lienzo.
    val priorityByMuscle = remember(program.planOrderPriorities) {
        program.planOrderPriorities.orEmpty().entries
            .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.key) }
            .mapValues { (_, entries) -> entries.sumOf { it.value }.toDouble() }
    }
    val hasLogs = programLogs.isNotEmpty()
    val muscles = remember(volume, targets, priorityByMuscle, hasLogs) {
        volumeMuscleRows(
            planned = volume.planned.weeklyEntries().associate { it.muscleName to it.weeklySets },
            done = volume.completed.muscles.mapValues { it.value.sets },
            targets = targets,
            priorityByMuscle = priorityByMuscle,
            hasLogs = hasLogs,
        )
    }
    var doneViewChoice by rememberSaveable(program.id) { mutableStateOf(true) }
    var expandedMuscleName by rememberSaveable(program.id) { mutableStateOf<String?>(null) }
    val doneView = hasLogs && doneViewChoice
    // El desglose explica la misma cifra que la barra: lo hecho en «Hecho» y lo planificado en «Plan».
    val breakdown = remember(expandedMuscleName, volume, doneView) {
        expandedMuscleName?.let { muscle ->
            val result = if (doneView) volume.completed else volume.planned
            programVolumeBreakdown(muscle, result.muscles[muscle])
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !isProgramActive -> ProgressVolumeEmpty(inactive = true, onAction = onActivateProgram)
            !hasCreatedSessions -> ProgressVolumeEmpty(inactive = false, onAction = onGoCreateSession)
            else -> ProgressVolumeModule(
                muscles = muscles,
                doneView = doneView,
                onDoneViewChange = if (hasLogs) ({ doneViewChoice = it }) else null,
                rangesLabel = lowerFirst(rangesOriginLabel(rangesOrigin)),
                onOpenRanges = onOpenRanges,
                expandedMuscle = expandedMuscleName,
                onToggleMuscle = { name -> expandedMuscleName = if (expandedMuscleName == name) null else name },
                breakdown = breakdown,
            )
        }
        // Enlace al panel de lo REGISTRADO; sin navegación provista no se muestra.
        LocalMuscleVolumePanelOpener.current?.let { openPanel ->
            ProgressVolumeHistoryLink(onClick = { openPanel(program.id) }, program = program, logs = programLogs)
        }
    }
}

internal fun mergePersonalizedTargets(
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

/** Proyección compartida por el plan, lo hecho y sus desgloses; calibrar no altera la contabilidad. */
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
