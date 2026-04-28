package com.example.kpkn.screens.wikilab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.kpkn.screens.home.SingleRingCanvas
import com.example.kpkn.ui.components.SectionHeader
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        if (exercise.involvedMuscles.isEmpty()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeader("Fatiga AUGE por RPE")
                Text(
                    "Todavia no hay suficientes musculos asociados para estimar el drenaje por escenario.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Card
        }

        val scenarios = remember(exercise.id) { buildScenarioSpecs() }
        var selectedScenarioId by rememberSaveable(exercise.id) { mutableStateOf("rpe-8") }
        val selectedScenario = scenarios.firstOrNull { it.id == selectedScenarioId } ?: scenarios[2]
        val selectedResult = remember(exercise.id, selectedScenario.id) {
            computeScenarioFatigueResult(exercise, selectedScenario)
        }
        val rpeMultiplier = remember(selectedScenario.id) {
            AugeFatigueEngine.calculateRpeMultiplier(AugeFatigueEngine.getEffectiveRPE(selectedScenario.set))
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("Fatiga AUGE por RPE")
            Text(
                "Simula 1 serie efectiva y ve como cambia la demanda muscular, neural y espinal segun el RPE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ScenarioSelectorRow(
                scenarios = scenarios,
                selectedScenarioId = selectedScenario.id,
                onScenarioSelected = { selectedScenarioId = it },
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                selectedScenario.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                selectedScenario.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    "AUGE x${"%.2f".format(rpeMultiplier)}",
                                    fontWeight = FontWeight.Black,
                                )
                            },
                            leadingIcon = {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.Insights,
                                    contentDescription = null,
                                )
                            },
                        )
                    }

                    ScenarioRingsSummary(selectedResult = selectedResult)

                    Text(
                        "Descuento estimado por serie: Musculos -${(100 - selectedResult.averageMuscleRemaining).coerceIn(0, 100)}% · Energia -${selectedResult.cnsDrain}% · Columna -${selectedResult.spinalDrain}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ScenarioMuscleDrainRow(selectedResult.muscularDrainByMuscle)

                    Text(
                        selectedResult.overallRecoveryNeedLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenarioSelectorRow(
    scenarios: List<ScenarioFatigueSpec>,
    selectedScenarioId: String,
    onScenarioSelected: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        if (compact) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    ScenarioSelectorButton(
                        scenario = scenario,
                        isSelected = scenario.id == selectedScenarioId,
                        onClick = { onScenarioSelected(scenario.id) },
                        modifier = Modifier.width(92.dp),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scenarios.forEach { scenario ->
                    ScenarioSelectorButton(
                        scenario = scenario,
                        isSelected = scenario.id == selectedScenarioId,
                        onClick = { onScenarioSelected(scenario.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenarioSelectorButton(
    scenario: ScenarioFatigueSpec,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (scenario.id) {
        "failure" -> MaterialTheme.colorScheme.error
        "rpe-6", "rpe-7" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accent.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = scenario.shortLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = scenario.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScenarioRingsSummary(selectedResult: ScenarioFatigueResult) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScenarioRingCard(
                    title = "Muscular",
                    value = selectedResult.averageMuscleRemaining,
                    drain = (100 - selectedResult.averageMuscleRemaining).coerceIn(0, 100),
                    color = RingMuscular,
                    modifier = Modifier.fillMaxWidth()
                )
                ScenarioRingCard(
                    title = "Energia",
                    value = selectedResult.energyRemaining,
                    drain = selectedResult.cnsDrain,
                    color = RingSystem,
                    modifier = Modifier.fillMaxWidth()
                )
                ScenarioRingCard(
                    title = "Columna",
                    value = selectedResult.spineRemaining,
                    drain = selectedResult.spinalDrain,
                    color = RingSpinal,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScenarioRingCard(
                    title = "Muscular",
                    value = selectedResult.averageMuscleRemaining,
                    drain = (100 - selectedResult.averageMuscleRemaining).coerceIn(0, 100),
                    color = RingMuscular,
                    modifier = Modifier.weight(1f)
                )
                ScenarioRingCard(
                    title = "Energia",
                    value = selectedResult.energyRemaining,
                    drain = selectedResult.cnsDrain,
                    color = RingSystem,
                    modifier = Modifier.weight(1f)
                )
                ScenarioRingCard(
                    title = "Columna",
                    value = selectedResult.spineRemaining,
                    drain = selectedResult.spinalDrain,
                    color = RingSpinal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScenarioRingCard(
    title: String,
    value: Int,
    drain: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleRingCanvas(
                value = (value.coerceIn(0, 100) / 100f),
                color = color,
                ringDiameter = 68f,
                strokeWidth = 6f,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = color,
                )
                Text(
                    "$value% restante",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Drenaje -$drain%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Drenaje por musculo")
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 28.dp),
            ) {
                items(items, key = { it.key }) { (muscle, drain) ->
                    ScenarioMuscleCard(muscle = muscle, drain = drain)
                }
            }
            if (listState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun ScenarioMuscleCard(muscle: String, drain: Int) {
    val remaining = (100 - drain).coerceIn(0, 100)
    val color = when {
        remaining >= 80 -> Color(0xFF22C55E)
        remaining >= 50 -> Color(0xFFFACC15)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.width(168.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                muscle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$remaining% restante",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Black,
            )
            LinearProgressIndicator(
                progress = { remaining / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
            )
            Text(
                "Drenaje estimado -$drain%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
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
