package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.analyticsExerciseKey
import com.example.kpkn.domain.exercises.displayLabel
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.training.ProgramAnalyticsReport
import java.time.Duration
import java.time.Instant

private data class ProgressPoint(
    val date: String,
    val estimated1RM: Double,
    val bestLoad: Double,
    val reps: Int,
    val tagId: String?,
)

private data class ExerciseProgressDetail(
    val key: String,
    val exerciseName: String,
    val anchorLabel: String?,
    val relationLabel: String?,
    val isStar: Boolean,
    val goal1RM: Double?,
    val bestEstimated1RM: Double,
    val bestPrVolume: Double,
    val totalSessions: Int,
    val averageWeeklyGain: Double,
    val relativeStrength: Double?,
    val points: List<ProgressPoint>,
)

private data class PlannedExerciseMeta(
    val name: String,
    val goal1RM: Double?,
    val isStar: Boolean,
    val anchorCanonicalId: String?,
    val relationLabel: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressView(
    program: Program,
    programLogs: List<WorkoutLog>,
    userBodyWeightKg: Double?,
    onUpdateProgram: (Program) -> Unit,
    analyticsReport: ProgramAnalyticsReport? = null,
    modifier: Modifier = Modifier,
) {
    val progressDetails = remember(program, programLogs, userBodyWeightKg) {
        buildExerciseProgressDetails(program, programLogs, userBodyWeightKg)
    }

    var selectedExerciseKey by rememberSaveable(progressDetails.map { it.key }) {
        mutableStateOf(progressDetails.firstOrNull()?.key)
    }
    var goalEditorKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(progressDetails) {
        if (selectedExerciseKey == null || progressDetails.none { it.key == selectedExerciseKey }) {
            selectedExerciseKey = progressDetails.firstOrNull()?.key
        }
    }

    val selectedExercise = remember(progressDetails, selectedExerciseKey) {
        progressDetails.firstOrNull { it.key == selectedExerciseKey }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Progreso", fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(
            "Aquí ves fuerza relativa, velocidad de mejora semanal y metas RM de tus ejercicios clave.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )

        if (progressDetails.isEmpty()) {
            EmptyAnalyticsCard("Registra sesiones para desbloquear tu progreso por ejercicio.")
            Spacer(Modifier.height(120.dp))
            return@Column
        }

        RelativeStrengthSection(
            exercises = progressDetails,
            selectedExercise = selectedExercise,
            selectedExerciseKey = selectedExerciseKey,
            bodyWeightKg = userBodyWeightKg,
            onSelectExercise = { selectedExerciseKey = it },
        )

        analyticsReport?.let { report ->
            ProgressAnalyticsCard(report = report)
        }

        WeeklyTrendSection(exercises = progressDetails)

        GoalRmSection(
            allExercises = progressDetails,
            onEditGoal = { goalEditorKey = it },
            onToggleStar = { key, isStar ->
                onUpdateProgram(updateStarForExercise(program, key, isStar))
            },
        )

        Spacer(Modifier.height(120.dp))
    }

    goalEditorKey?.let { exerciseKey ->
        val targetExercise = progressDetails.firstOrNull { it.key == exerciseKey }
        GoalEditorSheet(
            exerciseName = targetExercise?.exerciseName ?: "Meta RM",
            initialGoal = targetExercise?.goal1RM,
            onDismiss = { goalEditorKey = null },
            onSave = { goal ->
                onUpdateProgram(updateGoalForExercise(program, exerciseKey, goal))
                goalEditorKey = null
            },
        )
    }
}

@Composable
private fun RelativeStrengthSection(
    exercises: List<ExerciseProgressDetail>,
    selectedExercise: ExerciseProgressDetail?,
    selectedExerciseKey: String?,
    bodyWeightKg: Double?,
    onSelectExercise: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Fuerza relativa", fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Elige un ejercicio y compara tu eRM más alto con tu peso corporal actual.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                exercises.forEach { exercise ->
                    FilterChip(
                        selected = selectedExerciseKey == exercise.key,
                        onClick = { onSelectExercise(exercise.key) },
                        label = {
                            Text(
                                exercise.exerciseName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            if (selectedExercise != null) {
                val ratio = selectedExercise.relativeStrength
                selectedExercise.anchorLabel?.let { anchor ->
                    Text(
                        text = selectedExercise.relationLabel?.let { "$it de $anchor" } ?: "Vinculado a $anchor",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "eRM alto",
                        value = "${selectedExercise.bestEstimated1RM.toInt()} kg",
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Peso corporal",
                        value = bodyWeightKg?.let { "${it.toInt()} kg" } ?: "Pendiente",
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Relación",
                        value = ratio?.let { "${formatOneDecimal(it)}x" } ?: "Sin dato",
                    )
                }

                Text(
                    text = if (ratio != null) {
                        buildRelativeStrengthCopy(ratio)
                    } else {
                        "Añade tu peso corporal en ajustes o perfil para calcular la fuerza relativa de forma exacta."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )

                TagComparativeChart(exercise = selectedExercise)
            }
        }
    }
}

@Composable
private fun WeeklyTrendSection(
    exercises: List<ExerciseProgressDetail>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tendencia de mejora semanal", fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Promedio estimado de mejora por semana según la evolución del eRM en cada ejercicio.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            exercises
                .sortedByDescending { it.averageWeeklyGain }
                .forEach { exercise ->
                    val gain = exercise.averageWeeklyGain
                    val trendColor = when {
                        gain > 0.3 -> Color(0xFF22C55E)
                        gain < -0.3 -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(exercise.exerciseName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    text = "${if (gain >= 0) "+" else ""}${formatOneDecimal(gain)} kg/sem",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = trendColor,
                                )
                            }
                            Text(
                                text = "Mejor eRM: ${exercise.bestEstimated1RM.toInt()} kg · ${exercise.totalSessions} sesiones registradas",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun GoalRmSection(
    allExercises: List<ExerciseProgressDetail>,
    onEditGoal: (String) -> Unit,
    onToggleStar: (String, Boolean) -> Unit,
) {
    val (starredExercises, nonStarredExercises) = remember(allExercises) {
        allExercises.partition { it.isStar }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Metas RM", fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(
                "Los ejercicios marcados con estrella aparecen aquí con su meta RM. Puedes añadir cualquier ejercicio desde esta vista.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            if (starredExercises.isEmpty()) {
                EmptyInlineHint("Todavía no hay ejercicios estrella. Márcalos desde el editor de sesiones o añádelos aquí.")
            } else {
                starredExercises.forEach { exercise ->
                    val progressFraction = exercise.goal1RM?.takeIf { it > 0.0 }?.let {
                        (exercise.bestEstimated1RM / it).toFloat().coerceIn(0f, 1f)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("★", color = Color(0xFFF59E0B), fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            exercise.exerciseName,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        exercise.anchorLabel?.let { anchor ->
                                            Text(
                                                text = exercise.relationLabel?.let { "$it de $anchor" } ?: "Vinculado a $anchor",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                Row {
                                    TextButton(onClick = { onEditGoal(exercise.key) }) {
                                        Text(if (exercise.goal1RM != null) "Editar meta" else "Definir meta")
                                    }
                                    TextButton(onClick = { onToggleStar(exercise.key, false) }) {
                                        Text("Quitar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text(
                                text = if (exercise.goal1RM != null) {
                                    "Meta: ${exercise.goal1RM.toInt()} kg · Mejor eRM: ${exercise.bestEstimated1RM.toInt()} kg"
                                } else {
                                    "Aún no tiene meta RM asignada."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (progressFraction != null) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            }
                        }
                    }
                }
            }

            if (nonStarredExercises.isNotEmpty()) {
                Text(
                    "Añadir ejercicios",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                nonStarredExercises.forEach { exercise ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    "☆",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        exercise.exerciseName,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    exercise.anchorLabel?.let { anchor ->
                                        Text(
                                            text = exercise.relationLabel?.let { "$it de $anchor" } ?: "Vinculado a $anchor",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { onToggleStar(exercise.key, true) }) {
                                Text("★ Añadir")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorSheet(
    exerciseName: String,
    initialGoal: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var value by rememberSaveable(initialGoal) { mutableStateOf(initialGoal?.toString().orEmpty()) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Meta RM", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Define una meta de 1RM para $exerciseName. Déjalo vacío si quieres quitar la meta.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { char -> char.isDigit() || char == '.' } },
                label = { Text("Meta en kg") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(
                    onClick = { onSave(value.toDoubleOrNull()) },
                ) {
                    Text("Guardar")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EmptyAnalyticsCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyInlineHint(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
    }
}

private fun buildExerciseProgressDetails(
    program: Program,
    logs: List<WorkoutLog>,
    userBodyWeightKg: Double?,
): List<ExerciseProgressDetail> {
    val planned = collectProgramExerciseGoals(program)
    val exerciseNames = buildCanonicalExerciseNameIndex(program, logs)
    val groupedLogs = logs
        .flatMap { log ->
            log.completedExercises.mapNotNull { exercise ->
                val key = exercise.analyticsExerciseKey()
                val bestSet = exercise.sets.maxByOrNull { it.weight * it.reps } ?: return@mapNotNull null
                val estimated1RM = calculateEpley1RM(bestSet.weight, bestSet.reps)
                val exerciseDbId = exercise.exerciseDbId ?: ""
                val tagId = log.exerciseTags[exerciseDbId] ?: log.exerciseTags[exercise.exerciseId]
                Triple(
                    key,
                    exercise,
                    ProgressPoint(
                        date = log.date,
                        estimated1RM = estimated1RM,
                        bestLoad = bestSet.weight,
                        reps = bestSet.reps,
                        tagId = tagId
                    )
                )
            }
        }
        .groupBy { it.first }

    return (groupedLogs.keys + planned.keys).map { key ->
        val values = groupedLogs[key].orEmpty()
        val displayExercise = values.lastOrNull()?.second
        val planMeta = planned[key]
        val displayName = displayExercise?.exerciseName ?: planMeta?.name ?: key.removePrefix("exercise:")
        val points = values.map { it.third }.sortedBy { it.date }
        val bestEstimated1RM = points.maxOfOrNull { it.estimated1RM } ?: 0.0
        val bestPrVolume = points.maxOfOrNull { it.bestLoad * it.reps } ?: 0.0
        val averageWeeklyGain = computeWeeklyGain(points)
        val goal = planMeta?.goal1RM
        val isStar = planMeta?.isStar == true
        val anchorCanonicalId = displayExercise?.relativeToCanonicalExerciseId ?: planMeta?.anchorCanonicalId
        ExerciseProgressDetail(
            key = key,
            exerciseName = displayName,
            anchorLabel = resolveAnchorLabel(anchorCanonicalId, exerciseNames),
            relationLabel = planMeta?.relationLabel,
            isStar = isStar,
            goal1RM = goal,
            bestEstimated1RM = bestEstimated1RM,
            bestPrVolume = bestPrVolume,
            totalSessions = points.size,
            averageWeeklyGain = averageWeeklyGain,
            relativeStrength = userBodyWeightKg?.takeIf { it > 0.0 }?.let { bestEstimated1RM / it },
            points = points,
        )
    }.sortedByDescending { it.bestEstimated1RM }
}

private fun collectProgramExerciseGoals(
    program: Program,
): Map<String, PlannedExerciseMeta> {
    val map = mutableMapOf<String, PlannedExerciseMeta>()
    program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { week ->
            week.sessions.flatMap { session ->
                val exercises = if (session.parts.isNotEmpty()) {
                    session.parts.flatMap { it.exercises }
                } else {
                    session.exercises
                }
                exercises
            }
        }
        .forEach { exercise ->
            val key = exercise.analyticsExerciseKey()
            val current = map[key]
            map[key] = PlannedExerciseMeta(
                name = current?.name ?: exercise.name,
                goal1RM = exercise.goal1RM ?: current?.goal1RM,
                isStar = exercise.isStarTarget || current?.isStar == true,
                anchorCanonicalId = exercise.relativeToCanonicalExerciseId ?: current?.anchorCanonicalId,
                relationLabel = exercise.relationshipType?.displayLabel() ?: current?.relationLabel,
            )
        }
    return map
}

private fun buildCanonicalExerciseNameIndex(
    program: Program,
    logs: List<WorkoutLog>,
): Map<String, String> {
    val names = mutableMapOf<String, String>()
    program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.sessions }
        .flatMap { it.exercises + it.parts.flatMap { part -> part.exercises } }
        .forEach { exercise ->
            names.putIfAbsent(exercise.resolvedCanonicalExerciseId(), exercise.name)
        }
    logs.flatMap { it.completedExercises }.forEach { exercise ->
        names.putIfAbsent(exercise.resolvedCanonicalExerciseId(), exercise.exerciseName)
    }
    return names
}

private fun resolveAnchorLabel(
    anchorCanonicalId: String?,
    exerciseNames: Map<String, String>,
): String? {
    val id = anchorCanonicalId ?: return null
    return exerciseNames[id] ?: id
}

private fun updateGoalForExercise(
    program: Program,
    exerciseKey: String,
    goal1RM: Double?,
): Program {
    return program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    week.copy(
                                        sessions = week.sessions.map { session ->
                                            session.copy(
                                                parts = session.parts.map { part ->
                                                    part.copy(
                                                        exercises = part.exercises.map { exercise ->
                                                            if (exercise.analyticsExerciseKey() == exerciseKey) {
                                                                exercise.copy(goal1RM = goal1RM, isStarTarget = true)
                                                            } else {
                                                                exercise
                                                            }
                                                        }
                                                    )
                                                },
                                                exercises = session.exercises.map { exercise ->
                                                    if (exercise.analyticsExerciseKey() == exerciseKey) {
                                                        exercise.copy(goal1RM = goal1RM, isStarTarget = true)
                                                    } else {
                                                        exercise
                                                    }
                                                },
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

private fun updateStarForExercise(
    program: Program,
    exerciseKey: String,
    isStar: Boolean,
): Program {
    return program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    week.copy(
                                        sessions = week.sessions.map { session ->
                                            session.copy(
                                                parts = session.parts.map { part ->
                                                    part.copy(
                                                        exercises = part.exercises.map { exercise ->
                                                            if (exercise.analyticsExerciseKey() == exerciseKey) {
                                                                exercise.copy(isStarTarget = isStar)
                                                            } else {
                                                                exercise
                                                            }
                                                        }
                                                    )
                                                },
                                                exercises = session.exercises.map { exercise ->
                                                    if (exercise.analyticsExerciseKey() == exerciseKey) {
                                                        exercise.copy(isStarTarget = isStar)
                                                    } else {
                                                        exercise
                                                    }
                                                },
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

private fun computeWeeklyGain(points: List<ProgressPoint>): Double {
    if (points.size < 2) return 0.0
    val first = points.first()
    val last = points.last()
    val firstInstant = runCatching { Instant.parse(first.date) }.getOrNull()
    val lastInstant = runCatching { Instant.parse(last.date) }.getOrNull()
    val weeks = if (firstInstant != null && lastInstant != null) {
        maxOf(Duration.between(firstInstant, lastInstant).toDays() / 7.0, 1.0)
    } else {
        maxOf(points.size - 1.0, 1.0)
    }
    return (last.estimated1RM - first.estimated1RM) / weeks
}

private fun buildRelativeStrengthCopy(relativeStrength: Double): String {
    val tier = when {
        relativeStrength >= 2.0 -> "Nivel altísimo"
        relativeStrength >= 1.5 -> "Muy buen nivel"
        relativeStrength >= 1.2 -> "Buen nivel"
        relativeStrength >= 1.0 -> "Base sólida"
        else -> "Aún en construcción"
    }
    return "$tier en este ejercicio según tu mejor eRM frente a tu peso corporal."
}

private fun calculateEpley1RM(load: Double, reps: Int): Double {
    if (load <= 0.0 || reps <= 0) return 0.0
    return if (reps == 1) load else load * (1 + reps / 30.0)
}

private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagComparativeChart(
    exercise: ExerciseProgressDetail,
) {
    val pointsByTag = remember(exercise.points) {
        exercise.points.groupBy { it.tagId?.trim().orEmpty().ifBlank { "Base" } }
    }
    
    if (exercise.points.isEmpty()) return
    
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF38BDF8), // Sky Blue
        Color(0xFF34D399), // Emerald
        Color(0xFFFBBF24), // Amber
        Color(0xFFF87171), // Rose
        Color(0xFFC084FC)  // Purple
    )
    
    val tags = pointsByTag.keys.toList()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Evolución comparativa por Variante/Etiqueta (eRM)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Legends
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEachIndexed { index, tag ->
                val color = colors[index % colors.size]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(color)
                    )
                    Text(tag, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        
        // Chart Canvas
        val minVal = remember(exercise.points) { (exercise.points.minOf { it.estimated1RM } * 0.95).coerceAtLeast(0.0) }
        val maxVal = remember(exercise.points) { (exercise.points.maxOf { it.estimated1RM } * 1.05).coerceAtLeast(1.0) }
        val dateInstants = remember(exercise.points) {
            exercise.points.map { 
                runCatching { java.time.Instant.parse(it.date).toEpochMilli() }.getOrDefault(0L)
            }
        }
        val minTime = remember(dateInstants) { dateInstants.minOrNull() ?: 0L }
        val maxTime = remember(dateInstants) { dateInstants.maxOrNull() ?: 1L }
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)
        val valRange = (maxVal - minVal).coerceAtLeast(1.0)

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val width = size.width
            val height = size.height
            
            // Draw horizontal guide lines
            val numGuides = 3
            for (i in 0..numGuides) {
                val y = height * (i.toFloat() / numGuides)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(width, y),
                    strokeWidth = 1f
                )
            }
            
            // Plot lines for each tag
            tags.forEachIndexed { tagIndex, tag ->
                val tagPoints = pointsByTag[tag].orEmpty().sortedBy { it.date }
                if (tagPoints.isEmpty()) return@forEachIndexed
                
                val color = colors[tagIndex % colors.size]
                val path = androidx.compose.ui.graphics.Path()
                
                tagPoints.forEachIndexed { pIndex, pt ->
                    val time = runCatching { java.time.Instant.parse(pt.date).toEpochMilli() }.getOrDefault(0L)
                    val x = if (timeRange == 0L) width / 2f else ((time - minTime).toFloat() / timeRange.toFloat()) * width
                    val y = height - (((pt.estimated1RM - minVal) / valRange) * height).toFloat()
                    
                    val offset = androidx.compose.ui.geometry.Offset(x, y)
                    
                    if (pIndex == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    
                    drawCircle(
                        color = color,
                        radius = 4f,
                        center = offset
                    )
                }
                
                if (tagPoints.size > 1) {
                    drawPath(
                        path = path,
                        color = color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
        }
    }
}
