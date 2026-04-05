package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale.Companion.Fit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.domain.training.CanonicalMuscleVolumeEntry
import com.example.kpkn.domain.training.DiscomfortEntry
import com.example.kpkn.domain.training.VolumeCalculator
import kotlin.math.max

private enum class VolumeVisualizationMode(val label: String) {
    CAUPOLICAN("Caupolicán"),
    BARS("Gráficos"),
}

private enum class CaupolicanSide(val label: String) {
    FRONT("Frontal"),
    BACK("Posterior"),
}

internal data class CanonicalMuscleVolumeUi(
    val muscleId: String,
    val muscleName: String,
    val weeklySets: Double,
)

private data class HeatBlob(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
)

private data class HeatZoneSpec(
    val muscleName: String,
    val blobs: List<HeatBlob>,
)

private data class BodyFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

internal data class PersonalizedVolumeTarget(
    val muscleName: String,
    val minEffective: Int,
    val maxAdaptive: Int,
    val maxRecoverable: Int,
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
)

private val frontBodyFrame = BodyFrame(left = 0.16f, top = 0.03f, width = 0.68f, height = 0.95f)
private val backBodyFrame = BodyFrame(left = 0.18f, top = 0.03f, width = 0.64f, height = 0.95f)

private val frontHeatZones = listOf(
    HeatZoneSpec("Cuello", listOf(HeatBlob(0.50f, 0.11f, 0.10f, 0.07f))),
    HeatZoneSpec(
        "Deltoides",
        listOf(
            HeatBlob(0.18f, 0.19f, 0.13f, 0.08f, -30f),
            HeatBlob(0.82f, 0.19f, 0.13f, 0.08f, 30f),
        )
    ),
    HeatZoneSpec(
        "Pectorales",
        listOf(
            HeatBlob(0.37f, 0.24f, 0.18f, 0.10f, -12f),
            HeatBlob(0.63f, 0.24f, 0.18f, 0.10f, 12f),
        )
    ),
    HeatZoneSpec(
        "Bíceps",
        listOf(
            HeatBlob(0.12f, 0.32f, 0.085f, 0.12f, -8f),
            HeatBlob(0.88f, 0.32f, 0.085f, 0.12f, 8f),
        )
    ),
    HeatZoneSpec(
        "Antebrazo",
        listOf(
            HeatBlob(0.08f, 0.44f, 0.075f, 0.16f, -4f),
            HeatBlob(0.92f, 0.44f, 0.075f, 0.16f, 4f),
        )
    ),
    HeatZoneSpec("Abdomen", listOf(HeatBlob(0.50f, 0.40f, 0.17f, 0.17f))),
    HeatZoneSpec(
        "Core",
        listOf(
            HeatBlob(0.33f, 0.44f, 0.07f, 0.15f, -16f),
            HeatBlob(0.67f, 0.44f, 0.07f, 0.15f, 16f),
        )
    ),
    HeatZoneSpec(
        "Aductores",
        listOf(
            HeatBlob(0.45f, 0.66f, 0.06f, 0.13f, 10f),
            HeatBlob(0.55f, 0.66f, 0.06f, 0.13f, -10f),
        )
    ),
    HeatZoneSpec(
        "Cuádriceps",
        listOf(
            HeatBlob(0.37f, 0.70f, 0.12f, 0.22f, 4f),
            HeatBlob(0.63f, 0.70f, 0.12f, 0.22f, -4f),
        )
    ),
    HeatZoneSpec(
        "Pantorrillas",
        listOf(
            HeatBlob(0.39f, 0.89f, 0.07f, 0.14f, 2f),
            HeatBlob(0.61f, 0.89f, 0.07f, 0.14f, -2f),
        )
    ),
)

private val backHeatZones = listOf(
    HeatZoneSpec("Cuello", listOf(HeatBlob(0.50f, 0.11f, 0.10f, 0.07f))),
    HeatZoneSpec(
        "Trapecio",
        listOf(
            HeatBlob(0.50f, 0.19f, 0.16f, 0.10f),
            HeatBlob(0.39f, 0.20f, 0.10f, 0.08f, -18f),
            HeatBlob(0.61f, 0.20f, 0.10f, 0.08f, 18f),
        )
    ),
    HeatZoneSpec(
        "Deltoides",
        listOf(
            HeatBlob(0.17f, 0.19f, 0.12f, 0.08f, -28f),
            HeatBlob(0.83f, 0.19f, 0.12f, 0.08f, 28f),
        )
    ),
    HeatZoneSpec(
        "Dorsales",
        listOf(
            HeatBlob(0.35f, 0.34f, 0.13f, 0.18f, -12f),
            HeatBlob(0.65f, 0.34f, 0.13f, 0.18f, 12f),
        )
    ),
    HeatZoneSpec(
        "Tríceps",
        listOf(
            HeatBlob(0.11f, 0.31f, 0.08f, 0.13f, -10f),
            HeatBlob(0.89f, 0.31f, 0.08f, 0.13f, 10f),
        )
    ),
    HeatZoneSpec(
        "Antebrazo",
        listOf(
            HeatBlob(0.07f, 0.43f, 0.07f, 0.16f, -4f),
            HeatBlob(0.93f, 0.43f, 0.07f, 0.16f, 4f),
        )
    ),
    HeatZoneSpec(
        "Erectores Espinales",
        listOf(
            HeatBlob(0.47f, 0.39f, 0.045f, 0.23f),
            HeatBlob(0.53f, 0.39f, 0.045f, 0.23f),
        )
    ),
    HeatZoneSpec(
        "Glúteos",
        listOf(
            HeatBlob(0.40f, 0.59f, 0.12f, 0.10f, 4f),
            HeatBlob(0.60f, 0.59f, 0.12f, 0.10f, -4f),
        )
    ),
    HeatZoneSpec(
        "Isquiosurales",
        listOf(
            HeatBlob(0.39f, 0.71f, 0.10f, 0.20f, 4f),
            HeatBlob(0.61f, 0.71f, 0.10f, 0.20f, -4f),
        )
    ),
    HeatZoneSpec(
        "Pantorrillas",
        listOf(
            HeatBlob(0.39f, 0.89f, 0.07f, 0.16f, 1f),
            HeatBlob(0.61f, 0.89f, 0.07f, 0.16f, -1f),
        )
    ),
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
    modifier: Modifier = Modifier,
) {
    val canonicalVolumes = remember(program) {
        mergeCanonicalVolumeCatalog(
            VolumeCalculator.calculateCanonicalWeeklyMuscleVolume(program, EXERCISE_DATABASE)
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

    var selectedModeName by rememberSaveable { mutableStateOf(VolumeVisualizationMode.CAUPOLICAN.name) }
    val selectedMode = VolumeVisualizationMode.valueOf(selectedModeName)
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
            text = "Visualiza tus series efectivas promedio por semana usando tus músculos del programa.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )

        if (!isProgramActive) {
            VolumeStateCard(
                title = "Activa tu programa para ver volumen",
                body = "Cuando el programa esté activo, KPKN usará tu estructura para pintar el Caupolicán y mostrar cómo se reparte el volumen por músculo.",
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

        ViewModeSelector(
            selectedMode = selectedMode,
            onSelect = { selectedModeName = it.name },
        )

        when (selectedMode) {
            VolumeVisualizationMode.CAUPOLICAN -> PreciseCaupolicanHeatmapCard(
                volumeByMuscle = canonicalVolumes.associate { it.muscleName to it.weeklySets },
                personalizedTargets = personalizedTargets,
                isVolumeCalibrated = isVolumeCalibrated,
            )
            VolumeVisualizationMode.BARS -> CanonicalVolumeBarsCard(
                canonicalVolumes = canonicalVolumes,
                personalizedTargets = personalizedTargets,
                isVolumeCalibrated = isVolumeCalibrated,
            )
        }

        VolumeSummaryStrip(
            totalWeeklySets = totalWeeklySets,
            activeMuscles = activeMuscles,
            topMuscle = topMuscle,
        )

        if (programDiscomforts.isNotEmpty()) {
            CompactDiscomfortWidget(discomforts = programDiscomforts)
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
                text = "Caupolicán",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val figureWidth = minOf(maxWidth * 0.62f, 220.dp)
                Box(
                    modifier = Modifier
                        .width(figureWidth)
                        .aspectRatio(0.44f),
                ) {
                    Image(
                        painter = painterResource(R.drawable.caupolican_front),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = Fit,
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
private fun ViewModeSelector(
    selectedMode: VolumeVisualizationMode,
    onSelect: (VolumeVisualizationMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VolumeVisualizationMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label, fontWeight = FontWeight.SemiBold) },
            )
        }
    }
}

@Composable
private fun CaupolicanHeatmapCard(
    canonicalVolumes: List<CanonicalMuscleVolumeUi>,
    personalizedTargets: Map<String, PersonalizedVolumeTarget>,
    isVolumeCalibrated: Boolean,
) {
    var selectedSideName by rememberSaveable { mutableStateOf(CaupolicanSide.FRONT.name) }
    val selectedSide = CaupolicanSide.valueOf(selectedSideName)
    val rotation by animateFloatAsState(
        targetValue = if (selectedSide == CaupolicanSide.FRONT) 0f else 180f,
        animationSpec = tween(durationMillis = 650),
        label = "caupolican-flip",
    )

    val maxWeeklySets = remember(canonicalVolumes, personalizedTargets, isVolumeCalibrated) {
        if (isVolumeCalibrated) {
            max(
                personalizedTargets.maxOfOrNull { it.value.maxRecoverable.toDouble() } ?: 0.0,
                canonicalVolumes.maxOfOrNull { it.weeklySets } ?: 0.0,
            ).coerceAtLeast(1.0)
        } else {
            max(canonicalVolumes.maxOfOrNull { it.weeklySets } ?: 0.0, 1.0)
        }
    }
    val volumeByMuscle = remember(canonicalVolumes) {
        canonicalVolumes.associate { it.muscleName to it.weeklySets }
    }
    val visibleMuscles = remember(selectedSide, canonicalVolumes) {
        val zones = if (selectedSide == CaupolicanSide.FRONT) frontHeatZones else backHeatZones
        zones.mapNotNull { zone ->
            val value = volumeByMuscle[zone.muscleName] ?: 0.0
            zone.muscleName.takeIf { value > 0.0 }?.let { it to value }
        }.sortedByDescending { it.second }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Caupolicán",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (isVolumeCalibrated) {
                            "Mapa de calor según tus series semanales comparadas con tu volumen personalizado."
                        } else {
                            "Mapa de calor según tus series efectivas promedio por semana."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CaupolicanSide.entries.forEach { side ->
                        FilterChip(
                            selected = selectedSide == side,
                            onClick = { selectedSideName = side.name },
                            label = { Text(side.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
            }

            HeatLegendRow(isVolumeCalibrated = isVolumeCalibrated)

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val figureWidth = minOf(maxWidth * 0.68f, 250.dp)
                val cameraDistance = with(LocalDensity.current) { 48.dp.toPx() }

                Box(
                    modifier = Modifier
                        .width(figureWidth)
                        .aspectRatio(0.44f),
                ) {
                    BodyHeatmapFace(
                        painter = painterResource(R.drawable.caupolican_front),
                        rotationY = rotation,
                        cameraDistance = cameraDistance,
                        isVisible = rotation <= 90f,
                        zones = frontHeatZones,
                        bodyFrame = frontBodyFrame,
                        volumeByMuscle = volumeByMuscle,
                        personalizedTargets = personalizedTargets,
                        maxWeeklySets = maxWeeklySets,
                        isVolumeCalibrated = isVolumeCalibrated,
                    )
                    BodyHeatmapFace(
                        painter = painterResource(R.drawable.caupolican_back),
                        rotationY = rotation - 180f,
                        cameraDistance = cameraDistance,
                        isVisible = rotation >= 90f,
                        zones = backHeatZones,
                        bodyFrame = backBodyFrame,
                        volumeByMuscle = volumeByMuscle,
                        personalizedTargets = personalizedTargets,
                        maxWeeklySets = maxWeeklySets,
                        isVolumeCalibrated = isVolumeCalibrated,
                    )
                }
            }

            if (visibleMuscles.isEmpty()) {
                Text(
                    text = "Aún no hay ejercicios suficientes en tu programa para pintar el mapa de calor.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Más cargados en esta vista",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    visibleMuscles.take(4).forEach { (muscleName, value) ->
                        VolumeTagRow(
                            label = muscleName,
                            value = buildMuscleStatusText(
                                weeklySets = value,
                                target = personalizedTargets[muscleName],
                                isVolumeCalibrated = isVolumeCalibrated,
                            ),
                            intensity = heatIntensityForMuscle(
                                weeklySets = value,
                                target = personalizedTargets[muscleName],
                                maxWeeklySets = maxWeeklySets,
                                isVolumeCalibrated = isVolumeCalibrated,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyHeatmapFace(
    painter: Painter,
    rotationY: Float,
    cameraDistance: Float,
    isVisible: Boolean,
    zones: List<HeatZoneSpec>,
    bodyFrame: BodyFrame,
    volumeByMuscle: Map<String, Double>,
    personalizedTargets: Map<String, PersonalizedVolumeTarget>,
    maxWeeklySets: Double,
    isVolumeCalibrated: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.rotationY = rotationY
                transformOrigin = TransformOrigin.Center
                this.cameraDistance = cameraDistance
                alpha = if (isVisible) 1f else 0f
            }
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = Fit,
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            zones.forEach { zone ->
                val weeklySets = volumeByMuscle[zone.muscleName] ?: 0.0
                if (weeklySets <= 0.0) return@forEach

                val target = personalizedTargets[zone.muscleName]
                val intensity = heatIntensityForMuscle(
                    weeklySets = weeklySets,
                    target = target,
                    maxWeeklySets = maxWeeklySets,
                    isVolumeCalibrated = isVolumeCalibrated,
                )
                val fillColor = heatColorForIntensity(intensity)
                val strokeColor = fillColor.copy(alpha = minOf(fillColor.alpha + 0.2f, 0.95f))

                zone.blobs.forEach { blob ->
                    val blobWidth = size.width * bodyFrame.width * blob.width
                    val blobHeight = size.height * bodyFrame.height * blob.height
                    val centerX = size.width * bodyFrame.left + size.width * bodyFrame.width * blob.centerX
                    val centerY = size.height * bodyFrame.top + size.height * bodyFrame.height * blob.centerY
                    val topLeftX = centerX - blobWidth / 2f
                    val topLeftY = centerY - blobHeight / 2f

                    withTransform({
                        rotate(
                            degrees = blob.rotationDegrees,
                            pivot = androidx.compose.ui.geometry.Offset(
                                x = topLeftX + blobWidth / 2f,
                                y = topLeftY + blobHeight / 2f,
                            ),
                        )
                    }) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                            size = androidx.compose.ui.geometry.Size(blobWidth, blobHeight),
                            cornerRadius = CornerRadius(blobWidth * 0.48f, blobHeight * 0.48f),
                            blendMode = BlendMode.SrcOver,
                        )
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                            size = androidx.compose.ui.geometry.Size(blobWidth, blobHeight),
                            cornerRadius = CornerRadius(blobWidth * 0.48f, blobHeight * 0.48f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.0045f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegendRow(isVolumeCalibrated: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Carga",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        if (isVolumeCalibrated) {
            HeatLegendDot(label = "Bajo", color = heatColorForIntensity(0.24f))
            HeatLegendDot(label = "Ideal", color = heatColorForIntensity(0.6f))
            HeatLegendDot(label = "Alto", color = heatColorForIntensity(0.94f))
        } else {
            HeatLegendDot(label = "Baja", color = heatColorForIntensity(0.25f))
            HeatLegendDot(label = "Media", color = heatColorForIntensity(0.55f))
            HeatLegendDot(label = "Alta", color = heatColorForIntensity(0.9f))
        }
    }
}

@Composable
private fun HeatLegendDot(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun buildMuscleStatusText(
    weeklySets: Double,
    target: PersonalizedVolumeTarget?,
    isVolumeCalibrated: Boolean,
): String {
    if (!isVolumeCalibrated || target == null) {
        return "${formatOneDecimal(weeklySets)} series/sem"
    }

    val status = when {
        weeklySets < target.minEffective -> "Subentrenado"
        weeklySets <= target.maxAdaptive -> "Rango ideal"
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

private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)
