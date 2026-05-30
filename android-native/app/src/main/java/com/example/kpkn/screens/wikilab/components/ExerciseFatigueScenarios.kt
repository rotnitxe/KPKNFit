package com.example.kpkn.screens.wikilab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.AugeMetrics
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.roundToInt

private val RingMuscular = Color(0xFFFF5252)
private val RingSystem = Color(0xFF448AFF)
private val RingSpinal = Color(0xFFFFD740)

private data class ScenarioFatigueSpec(
    val id: String,
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val set: CompletedSet,
)

private data class ScenarioFatigueResult(
    val cnsDrain: Int,
    val spinalDrain: Int,
    val muscularDrainByMuscle: Map<String, Int>,
) {
    val energyRemaining: Int
        get() = (100 - cnsDrain).coerceIn(0, 100)

    val spineRemaining: Int
        get() = (100 - spinalDrain).coerceIn(0, 100)

    val averageMuscleRemaining: Int
        get() = if (muscularDrainByMuscle.isEmpty()) 100 else {
            val avgDrain = muscularDrainByMuscle.values.average()
            (100.0 - avgDrain).roundToInt().coerceIn(0, 100)
        }

    fun overallRecoveryNeedLabel(): String {
        val elements = buildList {
            add(cnsDrain)
            add(spinalDrain)
            addAll(muscularDrainByMuscle.values)
        }
        val avgDrain = if (elements.isEmpty()) 0 else elements.average().roundToInt()
        return when {
            avgDrain >= 55 -> "Necesidad de recuperacion: muy alta"
            avgDrain >= 40 -> "Necesidad de recuperacion: alta"
            avgDrain >= 25 -> "Necesidad de recuperacion: media"
            avgDrain >= 12 -> "Necesidad de recuperacion: baja"
            else -> "Necesidad de recuperacion: muy baja"
        }
    }
}

private data class ScenarioMuscleGroup(val label: String, val muscleKeys: List<String>)

private val SCENARIO_MUSCLE_GROUPS = listOf(
    ScenarioMuscleGroup("Pecho", listOf("Pectorales")),
    ScenarioMuscleGroup("Espalda", listOf("Dorsales", "Trapecio", "Erectores Espinales")),
    ScenarioMuscleGroup("Hombros", listOf("Deltoides")),
    ScenarioMuscleGroup("Brazos", listOf("Biceps", "Triceps", "Antebrazo")),
    ScenarioMuscleGroup("Core", listOf("Abdomen", "Core")),
    ScenarioMuscleGroup("Piernas", listOf("Cuadriceps", "Isquiosurales", "Gluteos", "Aductores", "Pantorrillas")),
)

private fun buildScenarioSpecs(): List<ScenarioFatigueSpec> {
    val rpeSeries = (6..10).map { rpe ->
        val reps = when (rpe) {
            6 -> 10
            7 -> 8
            8 -> 7
            9 -> 6
            else -> 5
        }
        val weightValue = when (rpe) {
            6 -> 65.0
            7 -> 70.0
            8 -> 74.0
            9 -> 77.0
            else -> 80.0
        }
        ScenarioFatigueSpec(
            id = "rpe-$rpe",
            shortLabel = rpe.toString(),
            title = "RPE $rpe",
            subtitle = when (rpe) {
                6 -> "Controlada"
                7 -> "Media"
                8 -> "Alta"
                9 -> "Muy alta"
                else -> "Casi al limite"
            },
            set = CompletedSet(
                id = "scenario-rpe-$rpe",
                weight = weightValue,
                reps = reps,
                rpe = rpe.toDouble(),
                actualIntensityMode = IntensityMode.RPE,
                actualIntensityValue = rpe.toDouble(),
            ),
        )
    }

    val failure = ScenarioFatigueSpec(
        id = "failure",
        shortLabel = "F",
        title = "Failure",
        subtitle = "Serie al fallo",
        set = CompletedSet(
            id = "scenario-failure",
            weight = 80.0,
            reps = 5,
            rpe = 10.0,
            isFailure = true,
            isFailedSet = true,
            actualIntensityMode = IntensityMode.FAILURE,
            actualIntensityValue = 10.0,
        ),
    )

    return rpeSeries + failure
}

private fun computeScenarioFatigueResult(
    exercise: ExerciseMuscleInfo,
    scenario: ScenarioFatigueSpec,
): ScenarioFatigueResult {
    val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(Settings())
    val metrics = AugeFatigueEngine.getDynamicAugeMetrics(exercise.name, exercise.equipment) ?: AugeMetrics()
    val drain = AugeFatigueEngine.calculateSetBatteryDrain(
        set = scenario.set,
        metrics = metrics,
        tanks = tanks,
        accumulatedSets = 1,
        restTime = exercise.averageRestSeconds ?: 90,
        densityMultiplier = 1.0,
    )

    val roleWeightByMuscle = mutableMapOf<String, Double>()
    exercise.involvedMuscles.forEach { involvement ->
        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
        val roleWeight = when (involvement.role) {
            MuscleRole.PRIMARY -> 1.0
            MuscleRole.SECONDARY -> 0.6
            MuscleRole.STABILIZER -> 0.3
            MuscleRole.NEUTRALIZER -> 0.2
        }
        roleWeightByMuscle[canonical] = (roleWeightByMuscle[canonical] ?: 0.0) + roleWeight
    }

    val totalWeightValue = roleWeightByMuscle.values.sum().takeIf { it > 0.0 } ?: 1.0
    val byMuscle = roleWeightByMuscle.mapValues { (_, w) ->
        (drain.muscularDrainPct * (w / totalWeightValue)).roundToInt().coerceIn(0, 100)
    }

    return ScenarioFatigueResult(
        cnsDrain = drain.cnsDrainPct.roundToInt().coerceIn(0, 100),
        spinalDrain = drain.spinalDrainPct.roundToInt().coerceIn(0, 100),
        muscularDrainByMuscle = byMuscle,
    )
}

@Composable
fun ExerciseFatigueScenarios(
    exercise: ExerciseMuscleInfo,
    modifier: Modifier = Modifier,
) {
    if (exercise.involvedMuscles.isEmpty()) return

    val scenarios = remember(exercise.id) { buildScenarioSpecs() }
    var sliderIndex by rememberSaveable(exercise.id) { mutableStateOf(2f) } // Default to RPE 8 (index 2)
    val selectedScenario = scenarios[sliderIndex.roundToInt().coerceIn(0, scenarios.size - 1)]
    val selectedResult = remember(exercise.id, selectedScenario.id) {
        computeScenarioFatigueResult(exercise, selectedScenario)
    }
    val rpeMultiplier = remember(selectedScenario.id) {
        AugeFatigueEngine.calculateRpeMultiplier(AugeFatigueEngine.getEffectiveRPE(selectedScenario.set))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Drenaje por intensidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                "Impacto x${"%.2f".format(rpeMultiplier)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Intensity Slider
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedScenario.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (selectedScenario.id == "failure") "Fallo muscular" else "RPE ${selectedScenario.shortLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = sliderIndex,
                onValueChange = { sliderIndex = it },
                valueRange = 0f..5f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                ),
            )
        }

        // Horizontal Bars Summary
        ScenarioBarsSummary(selectedResult = selectedResult)

        // Muscle Drain LazyRow
        ScenarioMuscleDrainRow(selectedResult.muscularDrainByMuscle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedResult.overallRecoveryNeedLabel(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Estimación por serie",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScenarioBarsSummary(selectedResult: ScenarioFatigueResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScenarioBarRow(
            label = "Fatiga Muscular",
            drain = (100 - selectedResult.averageMuscleRemaining).coerceIn(0, 100),
            color = RingMuscular,
        )
        ScenarioBarRow(
            label = "Estrés Sistémico (Energía)",
            drain = selectedResult.cnsDrain,
            color = RingSystem,
        )
        ScenarioBarRow(
            label = "Estrés Espinal (Columna)",
            drain = selectedResult.spinalDrain,
            color = RingSpinal,
        )
    }
}

@Composable
private fun ScenarioBarRow(
    label: String,
    drain: Int,
    color: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Drenaje -$drain%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Black,
                color = color,
            )
        }
        LinearProgressIndicator(
            progress = { drain / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun ScenarioMuscleDrainRow(drainByMuscle: Map<String, Int>) {
    val items = remember(drainByMuscle) {
        drainByMuscle.entries
            .sortedWith(
                compareBy<Map.Entry<String, Int>>(
                    { entry -> scenarioMuscleGroupIndex(entry.key) },
                    { entry -> -entry.value },
                    { entry -> entry.key },
                ),
            )
    }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Drenaje por músculo",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 16.dp),
            ) {
                items(items, key = { it.key }) { (muscle, drain) ->
                    ScenarioMuscleChip(muscle = muscle, drain = drain)
                }
            }
        }
    }
}

@Composable
private fun ScenarioMuscleChip(muscle: String, drain: Int) {
    val remaining = (100 - drain).coerceIn(0, 100)
    val color = when {
        remaining >= 80 -> Color(0xFF22C55E)
        remaining >= 50 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    Surface(
        modifier = Modifier.width(130.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                muscle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "-$drain% drenaje",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            LinearProgressIndicator(
                progress = { remaining / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.1f),
            )
        }
    }
}

private fun scenarioMuscleGroupIndex(muscle: String): Int {
    val index = SCENARIO_MUSCLE_GROUPS.indexOfFirst { group ->
        group.muscleKeys.any { muscle.contains(it, ignoreCase = true) }
    }
    return if (index >= 0) index else Int.MAX_VALUE
}
