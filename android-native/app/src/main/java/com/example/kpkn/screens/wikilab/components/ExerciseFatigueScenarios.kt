package com.example.kpkn.screens.wikilab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
private val RingSystem = Color(0xFFFFB74D)
private val RingSpinal = Color(0xFFB39DDB)

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
    val averageMuscleRemaining: Int
        get() = if (muscularDrainByMuscle.isEmpty()) 100
        else (100 - muscularDrainByMuscle.values.average()).roundToInt().coerceIn(0, 100)

    fun overallRecoveryNeedLabel(): String {
        val maxMusDrain = muscularDrainByMuscle.values.maxOrNull() ?: 0
        val maxSystemic = maxOf(cnsDrain, spinalDrain)
        val score = maxOf(maxMusDrain, maxSystemic)
        return when {
            score >= 70 -> "Recuperación prolongada (48-72h)"
            score >= 40 -> "Recuperación estándar (24-48h)"
            else -> "Recuperación rápida (<24h)"
        }
    }
}

private fun buildScenarioSpecs(): List<ScenarioFatigueSpec> {
    val reps = 8
    val rpeSeries = (6..9).map { rpe ->
        val weightValue = when (rpe) {
            6 -> 70.0
            7 -> 72.5
            8 -> 75.0
            9 -> 77.0
            else -> 80.0
        }
        ScenarioFatigueSpec(
            id = "rpe-$rpe",
            shortLabel = rpe.toString(),
            title = "RPE $rpe",
            subtitle = when (rpe) {
                6 -> "Intensidad controlada"
                7 -> "Intensidad media"
                8 -> "Intensidad alta"
                9 -> "Intensidad muy alta"
                else -> "Casi al límite"
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
        subtitle = "Serie al fallo muscular",
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Drenaje por Intensidad",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Impacto x${"%.2f".format(rpeMultiplier)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Intensity Slider
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
                    text = selectedScenario.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = if (selectedScenario.id == "failure") "Fallo muscular" else "RPE ${selectedScenario.shortLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
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
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
        }

        // Horizontal Bars Summary (CNS, Spinal, Muscle)
        ScenarioBarsSummary(selectedResult = selectedResult)

        // Muscle Drain Flat List
        ScenarioMuscleDrainList(selectedResult.muscularDrainByMuscle)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedResult.overallRecoveryNeedLabel(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Cálculo estimado por serie de trabajo",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ScenarioBarsSummary(selectedResult: ScenarioFatigueResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScenarioBarRow(
            label = "Fatiga Muscular Local",
            drain = (100 - selectedResult.averageMuscleRemaining).coerceIn(0, 100),
            color = RingMuscular,
        )
        ScenarioBarRow(
            label = "Estrés Sistémico (CNS)",
            drain = selectedResult.cnsDrain,
            color = RingSystem,
        )
        ScenarioBarRow(
            label = "Estrés Espinal (Compresión columna)",
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
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = "-$drain%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        LinearProgressIndicator(
            progress = { drain / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.12f),
        )
    }
}

@Composable
private fun ScenarioMuscleDrainList(drainByMuscle: Map<String, Int>) {
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

    if (items.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Drenaje por Grupo Muscular",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (muscle, drain) ->
                val remaining = (100 - drain).coerceIn(0, 100)
                val color = when {
                    remaining >= 80 -> Color(0xFF22C55E)
                    remaining >= 50 -> Color(0xFFFACC15)
                    else -> Color(0xFFEF4444)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = muscle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Text(
                        text = "-$drain% drenaje",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun scenarioMuscleGroupIndex(muscle: String): Int {
    val index = SCENARIO_MUSCLE_GROUPS.indexOfFirst { group ->
        group.muscleKeys.any { muscle.contains(it, ignoreCase = true) }
    }
    return if (index >= 0) index else Int.MAX_VALUE
}

private val SCENARIO_MUSCLE_GROUPS = listOf(
    ScenarioMuscleGroup("Pecho", listOf("pecho", "pectoral")),
    ScenarioMuscleGroup("Espalda", listOf("espalda", "dorsal", "trapecio", "erector")),
    ScenarioMuscleGroup("Hombros", listOf("hombro", "deltoides")),
    ScenarioMuscleGroup("Brazos", listOf("brazo", "biceps", "triceps", "antebrazo")),
    ScenarioMuscleGroup("Piernas", listOf("pierna", "cuadriceps", "isquio", "gluteo", "pantorrilla", "aductor")),
    ScenarioMuscleGroup("Core", listOf("core", "abdomen"))
)

private data class ScenarioMuscleGroup(val label: String, val muscleKeys: List<String>)
