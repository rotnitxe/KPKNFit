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
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.domain.auge.SessionMuscleFilter
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
    var showIndirectVolume by rememberSaveable(program.id) { mutableStateOf(false) }
    var adjustVolumeByIntensity by rememberSaveable(program.id) { mutableStateOf(false) }
    LaunchedEffect(volumeScopeOptions) {
        if (volumeScopeOptions.none { it.id == selectedVolumeScopeId }) {
            selectedVolumeScopeId = volumeScopeOptions.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedVolumeScope = remember(volumeScopeOptions, selectedVolumeScopeId) {
        volumeScopeOptions.firstOrNull { it.id == selectedVolumeScopeId } ?: volumeScopeOptions.firstOrNull()
    }
    val canonicalVolumes = remember(program, selectedVolumeScope, showIndirectVolume, adjustVolumeByIntensity) {
        mergeCanonicalVolumeCatalog(
            calculateDisplayWeeklyMuscleVolume(
                weeks = selectedVolumeScope?.weeks ?: program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks },
                averageByWeek = selectedVolumeScope?.averageByWeek ?: false,
                countIndirect = showIndirectVolume,
                adjustByIntensity = adjustVolumeByIntensity
            )
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volumen indirecto",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Switch(
                    checked = showIndirectVolume,
                    onCheckedChange = { showIndirectVolume = it },
                    modifier = Modifier.scale(0.8f)
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ajustar por RPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Switch(
                    checked = adjustVolumeByIntensity,
                    onCheckedChange = { adjustVolumeByIntensity = it },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        CanonicalVolumeBarsCard(
            canonicalVolumes = canonicalVolumes,
            personalizedTargets = personalizedTargets,
            isVolumeCalibrated = isVolumeCalibrated,
            selectedVolumeScope = selectedVolumeScope,
            showIndirectVolume = showIndirectVolume,
            adjustByIntensity = adjustVolumeByIntensity,
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
    selectedVolumeScope: VolumeScopeOption?,
    showIndirectVolume: Boolean,
    adjustByIntensity: Boolean,
) {
    val maxWeeklySets = remember(canonicalVolumes) {
        max(canonicalVolumes.maxOfOrNull { it.weeklySets } ?: 0.0, 1.0)
    }

    val anatomicalRegions = remember {
        listOf(
            "Espalda" to listOf("Dorsales", "Trapecio", "Romboides", "Erectores Espinales"),
            "Pecho" to listOf("Pectorales"),
            "Hombros" to listOf("Deltoides", "Cuello"),
            "Brazos" to listOf("Bíceps", "Tríceps", "Antebrazo"),
            "Piernas" to listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"),
            "Core" to listOf("Abdomen", "Core")
        )
    }

    var expandedRegions by remember { mutableStateOf(setOf("Espalda", "Pecho", "Hombros", "Brazos", "Piernas", "Core")) }
    var expandedMuscleName by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

            anatomicalRegions.forEach { (regionName, musclesInRegion) ->
                val entriesInRegion = canonicalVolumes.filter { it.muscleName in musclesInRegion }
                val regionTotalSets = entriesInRegion.sumOf { it.weeklySets }
                val isExpanded = expandedRegions.contains(regionName)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Region Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .clickable {
                                expandedRegions = if (isExpanded) {
                                    expandedRegions - regionName
                                } else {
                                    expandedRegions + regionName
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = regionName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${formatOneDecimal(regionTotalSets)} series",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Colapsar" else "Desplegar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                        )
                    }

                    // Region Muscles List
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            entriesInRegion
                                .sortedByDescending { it.weeklySets }
                                .forEach { entry ->
                                    val isMuscleExpanded = expandedMuscleName == entry.muscleName
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                expandedMuscleName = if (isMuscleExpanded) null else entry.muscleName
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        CanonicalMuscleBarRow(
                                            entry = entry,
                                            target = personalizedTargets[entry.muscleName],
                                            fallbackMaxWeeklySets = maxWeeklySets,
                                            isVolumeCalibrated = isVolumeCalibrated,
                                        )

                                        if (isMuscleExpanded) {
                                            ExerciseBreakdownList(
                                                muscleName = entry.muscleName,
                                                weeks = selectedVolumeScope?.weeks ?: emptyList(),
                                                averageByWeek = selectedVolumeScope?.averageByWeek ?: false,
                                                countIndirect = showIndirectVolume,
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
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

    val progressColor = when {
        !isVolumeCalibrated || target == null -> MaterialTheme.colorScheme.primary
        entry.weeklySets <= 0.0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        entry.weeklySets < target.minEffective -> Color(0xFFEAB308) // Amber / Subentrenado
        entry.weeklySets <= target.maxAdaptive -> Color(0xFF10B981) // Green / Rango ideal
        entry.weeklySets <= target.maxRecoverable -> Color(0xFFF97316) // Orange / Alto tolerable
        else -> Color(0xFFEF4444) // Red / Sobreentreno
    }

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
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        ) {
            if (isVolumeCalibrated && target != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.weight(minFraction.coerceAtLeast(0.001f)))
                    Box(
                        modifier = Modifier
                            .weight((idealFraction - minFraction).coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(Color(0xFF10B981).copy(alpha = 0.08f))
                    )
                    Spacer(modifier = Modifier.weight((1f - idealFraction).coerceAtLeast(0.001f)))
                }
            }
            if (currentFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(progressColor)
                )
            }
        }
    }
}

@Composable
private fun ExerciseBreakdownList(
    muscleName: String,
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    countIndirect: Boolean = false,
) {
    val breakdown = remember(muscleName, weeks, averageByWeek, countIndirect) {
        calculateExerciseBreakdownForMuscle(muscleName, weeks, averageByWeek, countIndirect)
    }

    if (breakdown.isEmpty()) {
        Text(
            text = "Sin ejercicios directos registrados para este músculo.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Ejercicios que aportan volumen:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            breakdown.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.exerciseName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${formatOneDecimal(item.weeklySetsContribution)} series/sem",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    // Bug fix #2: el breakdown de ejercicios ahora respeta el mismo flag que las barras
    // principales, mostrando solo directos o directos+indirectos según corresponda.
    countIndirect: Boolean = false,
): List<ExerciseVolumeBreakdown> {
    val exerciseIndex = EXERCISE_DATABASE.associateBy { it.id.lowercase() }
    val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
    val breakdownMap = mutableMapOf<String, Pair<Double, Int>>()

    weeks.flatMap { it.sessions }.flatMap { it.allExercises() }.forEach { exercise ->
        val countedSets = countEffectiveSets(exercise.sets)
        if (countedSets > 0) {
            val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] }
            if (dbInfo != null) {
                val musclesToCount = SessionMuscleFilter.relevantMusclesFor(dbInfo)
                    .filter { involvement ->
                        if (countIndirect) true
                        else involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
                    }
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
    }

    return breakdownMap.entries
        .map { (key, pair) ->
            // Recuperamos el nombre del ejercicio para mostrar en UI
            val displayName = weeks.flatMap { it.sessions }.flatMap { it.allExercises() }
                .firstOrNull { (it.exerciseDbId?.takeIf { id -> id.isNotBlank() } ?: it.name) == key }
                ?.name ?: key
            ExerciseVolumeBreakdown(
                exerciseName = displayName,
                weeklySetsContribution = (pair.first / divisor * 10.0).toInt() / 10.0,
                totalSets = pair.second
            )
        }
        .filter { it.weeklySetsContribution > 0.0 }
        .sortedByDescending { it.weeklySetsContribution }
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
private data class SubMuscleContribution(
    val subMuscleName: String,
    val weeklySets: Double,
    val exercises: List<Pair<String, Double>> // exerciseName to sets
)

private fun resolveSpecificSubMuscle(muscle: String, emphasis: String?): String {
    val lower = muscle.lowercase().replace("-", " ").replace("_", " ").trim()
    if (lower.contains("deltoides") || lower.contains("hombro")) {
        return when {
            lower.contains("posterior") || lower.contains("trasero") -> "Deltoides Posterior"
            lower.contains("lateral") || lower.contains("medio") -> "Deltoides Lateral"
            else -> "Deltoides Anterior"
        }
    }
    if (lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata") || lower.contains("tensor fascia")) {
        return when {
            lower.contains("medio") || lower.contains("medius") || lower.contains("mínimo") || lower.contains("minimus") || lower.contains("tensor") -> "Glúteo Medio"
            else -> "Glúteo Mayor"
        }
    }
    return muscle
}

private fun com.example.kpkn.data.models.ExerciseSet.effectiveTargetRpe(): Double {
    if (isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

private fun countDisplaySets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>, adjustByIntensity: Boolean): Double {
    var total = 0.0
    val activeSets = exerciseSets.filterNot { it.isIneffective }
    val counted = activeSets.filter { set ->
        ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    val targetList = if (counted.isEmpty()) activeSets else counted
    targetList.forEach { set ->
        val mult = if (adjustByIntensity) {
            com.example.kpkn.domain.auge.AugeClassifiers.getEffectiveVolumeMultiplier(set.effectiveTargetRpe())
        } else {
            1.0
        }
        total += mult
    }
    return total
}

private fun calculateDisplayWeeklyMuscleVolume(
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    countIndirect: Boolean,
    adjustByIntensity: Boolean
): List<CanonicalMuscleVolumeEntry> {
    val sessions = weeks.flatMap { it.sessions }
    val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
    val exerciseIndex = EXERCISE_DATABASE.associateBy { it.id.lowercase() }
    val volumeMap = mutableMapOf<String, Double>()
    
    for (session in sessions) {
        for (exercise in session.allExercises()) {
            val effectiveSets = countDisplaySets(exercise.sets, adjustByIntensity)
            if (effectiveSets <= 0.0) continue
            val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: continue
            
            val contributions = buildDisplayContributions(dbInfo.involvedMuscles, countIndirect)
            contributions.forEach { (canonical, multiplier) ->
                volumeMap[canonical] = (volumeMap[canonical] ?: 0.0) + effectiveSets * multiplier
            }
        }
    }
    
    return volumeMap.entries.map { (muscleName, totalSets) ->
        CanonicalMuscleVolumeEntry(
            muscleId = muscleName.lowercase().replace(" ", "-"),
            muscleName = muscleName,
            weeklySets = ((totalSets / divisor) * 10.0).toInt() / 10.0
        )
    }.sortedByDescending { it.weeklySets }
}

private fun buildDisplayContributions(
    involvedMuscles: List<com.example.kpkn.data.models.InvolvedMuscle>,
    countIndirect: Boolean
): Map<String, Double> {
    val grouped = linkedMapOf<String, Double>()
    involvedMuscles.forEach { involvement ->
        // Bug fix #1: cuando countIndirect=true incluimos TODOS los roles (primary + secondary +
        // stabilizer). Antes se excluía erróneamente el PRIMARY, lo que hacía que las barras
        // mostrasen menos series al activar el switch en lugar de más.
        val isMatch = if (countIndirect) {
            true
        } else {
            involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
        }
        if (isMatch) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonical] ?: 0.0
            if (contribution > current) {
                grouped[canonical] = contribution
            }
        }
    }
    return grouped.filterValues { it > 0.0 }
}

private fun calculateSubMuscleBreakdown(
    canonicalMuscle: String,
    sessions: List<com.example.kpkn.data.models.Session>,
    exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    divisor: Double,
    countIndirect: Boolean,
    adjustByIntensity: Boolean
): List<SubMuscleContribution> {
    val targetSubMuscles = when (canonicalMuscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio")
        else -> return emptyList()
    }
    
    val subMuscleVolumes = targetSubMuscles.associateWith { mutableMapOf<String, Double>() }.toMutableMap()
    
    for (session in sessions) {
        for (exercise in session.allExercises()) {
            val effectiveSets = countDisplaySets(exercise.sets, adjustByIntensity)
            if (effectiveSets <= 0.0) continue
            val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: continue
            
            dbInfo.involvedMuscles.forEach { involvement ->
                val isMatch = if (countIndirect) {
                    involvement.role == com.example.kpkn.data.models.MuscleRole.SECONDARY || involvement.role == com.example.kpkn.data.models.MuscleRole.STABILIZER
                } else {
                    involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
                }
                if (isMatch) {
                    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
                    if (canonical == canonicalMuscle) {
                        val subMuscle = resolveSpecificSubMuscle(involvement.muscle, involvement.emphasis)
                        val map = subMuscleVolumes[subMuscle]
                        if (map != null) {
                            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
                            val current = map[exercise.name] ?: 0.0
                            if (effectiveSets * contribution > current) {
                                map[exercise.name] = effectiveSets * contribution
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

@Composable
private fun SubMuscleBreakdownList(
    muscleName: String,
    weeks: List<ProgramWeek>,
    averageByWeek: Boolean,
    countIndirect: Boolean,
    adjustByIntensity: Boolean,
) {
    val exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } }
    val divisor = if (averageByWeek) weeks.size.coerceAtLeast(1).toDouble() else 1.0
    val subMuscleBreakdown = remember(muscleName, weeks, averageByWeek, countIndirect, adjustByIntensity) {
        calculateSubMuscleBreakdown(muscleName, weeks.flatMap { it.sessions }, exerciseIndex, divisor, countIndirect, adjustByIntensity)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subMuscleBreakdown.forEach { sub ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sub.subMuscleName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${formatOneDecimal(sub.weeklySets)} series/sem",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                
                if (sub.exercises.isEmpty()) {
                    Text(
                        text = "  Sin ejercicios registrados para esta cabeza.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                } else {
                    sub.exercises.forEach { (exName, exSets) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $exName",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${formatOneDecimal(exSets)} series/sem",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
