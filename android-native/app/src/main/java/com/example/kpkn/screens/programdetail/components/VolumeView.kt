package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.domain.training.CanonicalMuscleVolumeEntry
import com.example.kpkn.domain.training.DiscomfortEntry
import com.example.kpkn.domain.training.ExerciseDiscomfortAssociationEntry
import com.example.kpkn.domain.training.ProgramAnalyticsReport
import com.example.kpkn.domain.training.VolumeCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
    modifier: Modifier = Modifier,
) {
    val volumeScopeOptions = remember(program) { buildVolumeScopeOptions(program) }
    var selectedVolumeScopeId by rememberSaveable(program.id) { mutableStateOf("") }
    LaunchedEffect(volumeScopeOptions) {
        if (volumeScopeOptions.none { it.id == selectedVolumeScopeId }) {
            selectedVolumeScopeId = volumeScopeOptions.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedVolumeScope = remember(volumeScopeOptions, selectedVolumeScopeId) {
        volumeScopeOptions.firstOrNull { it.id == selectedVolumeScopeId } ?: volumeScopeOptions.firstOrNull()
    }
    val canonicalVolumes = remember(program, selectedVolumeScope) {
        mergeCanonicalVolumeCatalog(
            selectedVolumeScope?.let { scope ->
                VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForWeeks(
                    weeks = scope.weeks,
                    exerciseList = EXERCISE_DATABASE,
                    averageByWeek = scope.averageByWeek,
                )
            } ?: VolumeCalculator.calculateCanonicalWeeklyMuscleVolume(program, EXERCISE_DATABASE)
        )
    }
    val personalizedTargets = remember(program.volumeRecommendations) {
        mergePersonalizedTargets(program.volumeRecommendations)
    }
    val isVolumeCalibrated = remember(program.volumeRecommendations, program.athleteProfileScore) {
        program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
    }
    val totalWeeklySets = remember(canonicalVolumes) { canonicalVolumes.sumOf { it.weeklySets } }
    val activeMuscles = remember(canonicalVolumes) { canonicalVolumes.count { it.weeklySets > 0.0 } }
    val topMuscle = remember(canonicalVolumes) { canonicalVolumes.maxByOrNull { it.weeklySets } }

    var showCalibrationSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Volumen planificado",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
        )
        Text(
            text = selectedVolumeScope?.detail
                ?: "Visualiza tus series efectivas por semana usando los contadores de volumen de cada sesión.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )

        if (!isProgramActive) {
            VolumeStateCard(
                title = "Activa tu programa para ver volumen",
                body = "Cuando el programa esté activo, KPKN usará tu estructura para mostrar cómo se reparte el volumen por músculo.",
                buttonLabel = "Activar programa",
                onAction = onActivateProgram,
            )
            Spacer(Modifier.height(120.dp))
            return@Column
        }

        if (!hasCreatedSessions) {
            VolumeStateCard(
                title = "Todavía no hay sesiones creadas",
                body = "Primero crea al menos una sesión dentro de tu semana para que podamos representar tu volumen planificado por músculo.",
                buttonLabel = "Ir a crear sesión",
                onAction = onGoCreateSession,
            )
            Spacer(Modifier.height(120.dp))
            return@Column
        }

        if (!isVolumeCalibrated || program.athleteProfileScore == null) {
            VolumeCalibrationInvitationCard(
                onCalibrate = { showCalibrationSheet = true },
            )
        }

        if (volumeScopeOptions.size > 1 && selectedVolumeScope != null) {
            VolumeScopeSelector(
                options = volumeScopeOptions,
                selected = selectedVolumeScope,
                onSelect = { selectedVolumeScopeId = it.id },
            )
        }

        CanonicalVolumeBarsCard(
            canonicalVolumes = canonicalVolumes,
            personalizedTargets = personalizedTargets,
            isVolumeCalibrated = isVolumeCalibrated,
        )

        analyticsReport?.let { report ->
            VolumeAnalyticsCard(report = report)
        }

        VolumeSummaryStrip(
            totalWeeklySets = totalWeeklySets,
            activeMuscles = activeMuscles,
            topMuscle = topMuscle,
        )

        if (programDiscomforts.isNotEmpty()) {
            CompactDiscomfortWidget(discomforts = programDiscomforts)
        }

        if (exerciseDiscomfortAssociations.isNotEmpty()) {
            ExerciseDiscomfortAssociationWidget(associations = exerciseDiscomfortAssociations)
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

@Composable
private fun VolumeScopeSelector(
    options: List<VolumeScopeOption>,
    selected: VolumeScopeOption,
    onSelect: (VolumeScopeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(selected.label, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(
                    selected.detail,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp,
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                option.detail,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 13.sp,
                            )
                        }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExerciseDiscomfortAssociationWidget(
    associations: List<ExerciseDiscomfortAssociationEntry>,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Asociación molestias por ejercicio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            associations.take(6).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.exerciseName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = entry.discomfortLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "x${entry.count}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeStateCard(
    title: String,
    body: String,
    buttonLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Volumen",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.82f, 0.62f, 0.44f).forEachIndexed { index, widthFraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                when (index) {
                                    0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                    1 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f)
                                    else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
                                },
                            ),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VolumeCalibrationInvitationCard(
    onCalibrate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Calibra tu volumen",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Para decirte si un músculo está quedándose corto, en rango ideal o pasado de volumen, primero necesitamos tu calibración personalizada.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )
            Button(
                onClick = onCalibrate,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Calibrar volumen", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CanonicalVolumeBarsCard(
    canonicalVolumes: List<CanonicalMuscleVolumeUi>,
    personalizedTargets: Map<String, PersonalizedVolumeTarget>,
    isVolumeCalibrated: Boolean,
) {
    val maxWeeklySets = remember(canonicalVolumes) {
        max(canonicalVolumes.maxOfOrNull { it.weeklySets } ?: 0.0, 1.0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Gráficos por músculo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (isVolumeCalibrated) {
                    "Cada barra compara tus series semanales contra tu rango ideal de volumen."
                } else {
                    "Cada barra representa tus series efectivas promedio por semana. Calibra tu volumen para ver rangos personalizados."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            canonicalVolumes
                .sortedByDescending { it.weeklySets }
                .forEach { entry ->
                    CanonicalMuscleBarRow(
                        entry = entry,
                        target = personalizedTargets[entry.muscleName],
                        fallbackMaxWeeklySets = maxWeeklySets,
                        isVolumeCalibrated = isVolumeCalibrated,
                    )
                }
        }
    }
}

@Composable
private fun CanonicalMuscleBarRow(
    entry: CanonicalMuscleVolumeUi,
    target: PersonalizedVolumeTarget?,
    fallbackMaxWeeklySets: Double,
    isVolumeCalibrated: Boolean,
) {
    val scaleMax = when {
        isVolumeCalibrated && target != null -> max(entry.weeklySets, target.maxRecoverable.toDouble()).coerceAtLeast(1.0)
        else -> fallbackMaxWeeklySets.coerceAtLeast(1.0)
    }
    val currentFraction = (entry.weeklySets / scaleMax).toFloat().coerceIn(0f, 1f)
    val minFraction = if (target != null) (target.minEffective / scaleMax).toFloat().coerceIn(0f, 1f) else 0f
    val idealFraction = if (target != null) (target.maxAdaptive / scaleMax).toFloat().coerceIn(0f, 1f) else 0f
    val recoverableFraction = if (target != null) (target.maxRecoverable / scaleMax).toFloat().coerceIn(0f, 1f) else 1f
    val markerColor = heatColorForIntensity(
        heatIntensityForMuscle(
            weeklySets = entry.weeklySets,
            target = target,
            maxWeeklySets = scaleMax,
            isVolumeCalibrated = isVolumeCalibrated,
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.muscleName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildMuscleStatusText(
                    weeklySets = entry.weeklySets,
                    target = target,
                    isVolumeCalibrated = isVolumeCalibrated,
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            if (isVolumeCalibrated && target != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(minFraction.coerceAtLeast(0.001f), fill = true)
                            .fillMaxHeight()
                            .background(Color(0xFFEAB308).copy(alpha = 0.28f)),
                    )
                    Box(
                        modifier = Modifier
                            .weight((idealFraction - minFraction).coerceAtLeast(0.001f), fill = true)
                            .fillMaxHeight()
                            .background(Color(0xFF22C55E).copy(alpha = 0.26f)),
                    )
                    Box(
                        modifier = Modifier
                            .weight((recoverableFraction - idealFraction).coerceAtLeast(0.001f), fill = true)
                            .fillMaxHeight()
                            .background(Color(0xFFEF4444).copy(alpha = 0.22f)),
                    )
                    if (recoverableFraction < 1f) {
                        Box(
                            modifier = Modifier
                                .weight((1f - recoverableFraction).coerceAtLeast(0.001f), fill = true)
                                .fillMaxHeight()
                                .background(Color(0xFF7F1D1D).copy(alpha = 0.18f)),
                        )
                    }
                }
            }
            if (currentFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentFraction)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (isVolumeCalibrated) 4.dp else 999.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(markerColor.copy(alpha = if (isVolumeCalibrated) 0.96f else 0.72f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeSummaryStrip(
    totalWeeklySets: Double,
    activeMuscles: Int,
    topMuscle: CanonicalMuscleVolumeUi?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Series/sem",
            value = formatOneDecimal(totalWeeklySets),
        )
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Músculos activos",
            value = activeMuscles.toString(),
        )
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Más cargado",
            value = topMuscle?.muscleName ?: "Sin datos",
        )
    }
}

@Composable
private fun SummaryMetricCard(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun VolumeTagRow(
    label: String,
    value: String,
    intensity: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(heatColorForIntensity(intensity)),
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactDiscomfortWidget(discomforts: List<DiscomfortEntry>) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Molestias recientes",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
            discomforts.take(4).forEach { entry ->
                val intensity = (entry.count / 5f).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Canvas(
                        modifier = Modifier
                            .width(70.dp)
                            .height(10.dp)
                    ) {
                        drawLine(
                            color = trackColor,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = heatColorForIntensity(intensity),
                            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                            end = androidx.compose.ui.geometry.Offset(size.width * intensity, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
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
        label = "Macrociclo completo",
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
    val date = week.startDate?.let(::parseIsoDateOrNull)?.format(monthDayFormatter)
    return if (date != null && !week.name.contains(date)) {
        "${week.name} · $date"
    } else {
        week.name
    }
}

private fun mergePersonalizedTargets(
    recommendations: List<VolumeRecommendation>,
): Map<String, PersonalizedVolumeTarget> {
    return recommendations
        .groupBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup) }
        .mapValues { (muscleName, groupedRecommendations) ->
            PersonalizedVolumeTarget(
                muscleName = muscleName,
                minEffective = groupedRecommendations.sumOf { it.minEffectiveVolume },
                maxAdaptive = groupedRecommendations.sumOf { it.maxAdaptiveVolume },
                maxRecoverable = groupedRecommendations.sumOf { it.maxRecoverableVolume },
            )
        }
}

private fun heatIntensityForMuscle(
    weeklySets: Double,
    target: PersonalizedVolumeTarget?,
    maxWeeklySets: Double,
    isVolumeCalibrated: Boolean,
): Float {
    if (!isVolumeCalibrated || target == null) {
        return (weeklySets / maxWeeklySets).toFloat().coerceIn(0f, 1f)
    }

    val min = target.minEffective.toDouble().coerceAtLeast(1.0)
    val ideal = target.maxAdaptive.toDouble().coerceAtLeast(min)
    val recoverable = target.maxRecoverable.toDouble().coerceAtLeast(ideal + 1.0)

    return when {
        weeklySets <= 0.0 -> 0f
        weeklySets < min -> (0.16f + ((weeklySets / min) * 0.22f).toFloat()).coerceIn(0f, 0.38f)
        weeklySets <= ideal -> {
            val fraction = ((weeklySets - min) / (ideal - min).coerceAtLeast(1.0)).toFloat()
            (0.48f + fraction * 0.16f).coerceIn(0.48f, 0.68f)
        }
        weeklySets <= recoverable -> {
            val fraction = ((weeklySets - ideal) / (recoverable - ideal).coerceAtLeast(1.0)).toFloat()
            (0.78f + fraction * 0.14f).coerceIn(0.78f, 0.92f)
        }
        else -> 1f
    }
}

internal fun buildMuscleStatusText(
    weeklySets: Double,
    target: PersonalizedVolumeTarget?,
    isVolumeCalibrated: Boolean,
): String {
    if (!isVolumeCalibrated || target == null) {
        return "${formatOneDecimal(weeklySets)} series/sem"
    }

    val status = when {
        weeklySets < target.minEffective -> "Subentrenado"
        weeklySets == target.minEffective.toDouble() -> "Base mínima"
        weeklySets <= target.maxAdaptive -> "Rango ideal"
        weeklySets <= target.maxRecoverable -> "Alto tolerable"
        else -> "Sobreentreno"
    }
    return "$status · ${formatOneDecimal(weeklySets)} series/sem"
}

private fun heatColorForIntensity(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    val low = Color(0xFFFACC15)
    val mid = Color(0xFFF97316)
    val high = Color(0xFFEF4444)
    val baseColor = if (clamped < 0.5f) {
        lerp(low, mid, clamped / 0.5f)
    } else {
        lerp(mid, high, (clamped - 0.5f) / 0.5f)
    }
    return baseColor.copy(alpha = 0.28f + (clamped * 0.42f))
}

private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)

private fun parseIsoDateOrNull(raw: String): LocalDate? = try {
    LocalDate.parse(raw)
} catch (_: DateTimeParseException) {
    null
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
