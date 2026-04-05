package com.example.kpkn.screens.programdetail.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.AtlasSide
import com.example.kpkn.domain.training.CaupolicanAtlas
import com.example.kpkn.domain.training.CaupolicanAtlasBundle
import com.example.kpkn.domain.training.CaupolicanAtlasRepository
import com.example.kpkn.domain.training.NormalizedPoint
import com.example.kpkn.domain.training.calculateBoundingBoxOverlapWarnings
import com.example.kpkn.domain.training.findRegionsForMuscle
import com.example.kpkn.domain.training.withAddedPoint
import com.example.kpkn.domain.training.withAddedRegion
import com.example.kpkn.domain.training.withInsertedPoint
import com.example.kpkn.domain.training.withUpdatedLandmark
import com.example.kpkn.domain.training.withRemovedPoint
import com.example.kpkn.domain.training.withUpdatedPoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

private enum class HeatPreviewMode(
    val label: String,
    val intensity: Float,
) {
    LOW("Baja", 0.24f),
    IDEAL("Ideal", 0.62f),
    HIGH("Alta", 0.92f),
}

private data class EditableSelection(
    val regionId: String,
    val subzoneId: String,
    val pointIndex: Int,
)

private data class PendingInsertTarget(
    val regionId: String,
    val subzoneId: String,
    val insertAfterIndex: Int,
    val point: NormalizedPoint,
)

private val atlasJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private val frontCalibratorMuscles = listOf(
    "Cuello",
    "Deltoides",
    "Pectorales",
    "Bíceps",
    "Antebrazo",
    "Trapecio",
    "Dorsales",
    "Tríceps",
    "Erectores Espinales",
    "Core",
    "Abdomen",
    "Glúteos",
    "Aductores",
    "Cuádriceps",
    "Isquiosurales",
    "Pantorrillas",
)

private val backCalibratorMuscles = listOf(
    "Cuello",
    "Trapecio",
    "Deltoides",
    "Dorsales",
    "Tríceps",
    "Antebrazo",
    "Erectores Espinales",
    "Core",
    "Abdomen",
    "Glúteos",
    "Aductores",
    "Cuádriceps",
    "Isquiosurales",
    "Pantorrillas",
    "Pectorales",
    "Bíceps",
)

@Composable
internal fun PreciseCaupolicanHeatmapCard(
    volumeByMuscle: Map<String, Double>,
    personalizedTargets: Map<String, PersonalizedVolumeTarget>,
    isVolumeCalibrated: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedSideName by rememberSaveable { mutableStateOf(AtlasSide.FRONT.name) }
    var showCalibrationSheet by rememberSaveable { mutableStateOf(false) }
    val selectedSide = AtlasSide.valueOf(selectedSideName)
    val rotation by animateFloatAsState(
        targetValue = if (selectedSide == AtlasSide.FRONT) 0f else 180f,
        animationSpec = tween(durationMillis = 650),
        label = "precise-caupolican-flip",
    )

    val frontAtlas = remember { CaupolicanAtlasRepository.frontAtlas }
    val backAtlas = remember { CaupolicanAtlasRepository.backAtlas }
    val frontStructureReport = remember { CaupolicanAtlasRepository.validateStructure(frontAtlas) }
    val backStructureReport = remember { CaupolicanAtlasRepository.validateStructure(backAtlas) }
    val frontAssetReport = remember(context) { CaupolicanAtlasRepository.validateAssetFingerprint(context, frontAtlas) }
    val backAssetReport = remember(context) { CaupolicanAtlasRepository.validateAssetFingerprint(context, backAtlas) }
    val atlasIssues = remember(frontStructureReport, backStructureReport, frontAssetReport, backAssetReport) {
        frontStructureReport.issues + backStructureReport.issues + frontAssetReport.issues + backAssetReport.issues
    }

    val visibleMuscles = remember(selectedSide, volumeByMuscle) {
        CaupolicanAtlasRepository.atlasFor(selectedSide)
            .bindings
            .mapNotNull { binding ->
                val weeklySets = volumeByMuscle[binding.muscleName] ?: 0.0
                binding.muscleName.takeIf { weeklySets > 0.0 }?.let { it to weeklySets }
            }
            .sortedByDescending { it.second }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    Text("Caupolicán", fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (isVolumeCalibrated) {
                            "Mapa anatómico preciso con volumen comparado contra tu calibración personalizada."
                        } else {
                            "Mapa anatómico preciso con tus series efectivas promedio por semana."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AtlasSide.entries.forEach { side ->
                            FilterChip(
                                selected = selectedSide == side,
                                onClick = { selectedSideName = side.name },
                                label = { Text(if (side == AtlasSide.FRONT) "Frontal" else "Posterior", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            )
                        }
                    }
                    TextButton(onClick = { showCalibrationSheet = true }) {
                        Text("Calibrar atlas")
                    }
                }
            }

            HeatLegendRowPrecise(isVolumeCalibrated = isVolumeCalibrated)

            if (atlasIssues.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.18f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Atlas requiere revisión", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            atlasIssues.first().message,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val figureWidth = minOf(maxWidth * 0.70f, 260.dp)
                val cameraDistance = with(LocalDensity.current) { 48.dp.toPx() }
                Box(
                    modifier = Modifier
                        .width(figureWidth)
                        .aspectRatio(0.44f),
                ) {
                    PreciseHeatmapFace(
                        atlas = frontAtlas,
                        rotationY = rotation,
                        cameraDistance = cameraDistance,
                        isVisible = rotation <= 90f,
                        volumeByMuscle = volumeByMuscle,
                        personalizedTargets = personalizedTargets,
                        isVolumeCalibrated = isVolumeCalibrated,
                    )
                    PreciseHeatmapFace(
                        atlas = backAtlas,
                        rotationY = rotation - 180f,
                        cameraDistance = cameraDistance,
                        isVisible = rotation >= 90f,
                        volumeByMuscle = volumeByMuscle,
                        personalizedTargets = personalizedTargets,
                        isVolumeCalibrated = isVolumeCalibrated,
                    )
                }
            }

            if (visibleMuscles.isEmpty()) {
                Text(
                    "Aún no hay ejercicios suficientes en tu programa para pintar el atlas.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Más cargados en esta vista", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    visibleMuscles.take(4).forEach { (muscleName, weeklySets) ->
                        val target = personalizedTargets[muscleName]
                        VolumeTagRowPrecise(
                            label = muscleName,
                            value = buildMuscleStatusTextPrecise(weeklySets, target, isVolumeCalibrated),
                            intensity = heatIntensityForMusclePrecise(weeklySets, target, isVolumeCalibrated),
                        )
                    }
                }
            }
        }
    }

    if (showCalibrationSheet) {
        CaupolicanAtlasCalibrationSheet(onDismiss = { showCalibrationSheet = false })
    }
}

@Composable
private fun PreciseHeatmapFace(
    atlas: CaupolicanAtlas,
    rotationY: Float,
    cameraDistance: Float,
    isVisible: Boolean,
    volumeByMuscle: Map<String, Double>,
    personalizedTargets: Map<String, PersonalizedVolumeTarget>,
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
            painter = painterResource(atlas.imageSpec.drawableResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            atlas.bindings.forEach { binding ->
                val weeklySets = volumeByMuscle[binding.muscleName] ?: 0.0
                if (weeklySets <= 0.0) return@forEach
                val target = personalizedTargets[binding.muscleName]
                val intensity = heatIntensityForMusclePrecise(weeklySets, target, isVolumeCalibrated)
                val baseColor = heatColorForIntensityPrecise(intensity)
                atlas.findRegionsForMuscle(binding.muscleName).forEach { region ->
                    region.subzones.forEach { subzone ->
                        val outerPath = buildRegionPath(subzone.points, size)
                        val innerPath = buildRegionPath(scalePolygon(subzone.points, 0.93f), size)
                        drawPath(path = outerPath, color = baseColor.copy(alpha = if (isVolumeCalibrated) 0.28f else 0.22f), style = Fill)
                        drawPath(path = innerPath, color = baseColor.copy(alpha = if (isVolumeCalibrated) 0.62f else 0.52f), style = Fill)
                        drawPath(path = outerPath, color = baseColor.copy(alpha = 0.96f), style = Stroke(width = size.minDimension * 0.0035f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegendRowPrecise(isVolumeCalibrated: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Carga",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        if (isVolumeCalibrated) {
            HeatLegendDotPrecise("Bajo", heatColorForIntensityPrecise(0.24f))
            HeatLegendDotPrecise("Ideal", heatColorForIntensityPrecise(0.62f))
            HeatLegendDotPrecise("Alto", heatColorForIntensityPrecise(0.92f))
        } else {
            HeatLegendDotPrecise("Baja", heatColorForIntensityPrecise(0.20f))
            HeatLegendDotPrecise("Media", heatColorForIntensityPrecise(0.55f))
            HeatLegendDotPrecise("Alta", heatColorForIntensityPrecise(0.90f))
        }
    }
}

@Composable
private fun HeatLegendDotPrecise(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VolumeTagRowPrecise(
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
                    .size(10.dp)
                    .background(heatColorForIntensityPrecise(intensity), CircleShape),
            )
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaupolicanAtlasCalibrationSheet(
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedSideName by rememberSaveable { mutableStateOf(AtlasSide.FRONT.name) }
    var previewModeName by rememberSaveable { mutableStateOf(HeatPreviewMode.IDEAL.name) }
    var showLandmarks by rememberSaveable { mutableStateOf(true) }
    var showSilhouette by rememberSaveable { mutableStateOf(false) }
    var showOverlaps by rememberSaveable { mutableStateOf(true) }

    var frontAtlas by remember { mutableStateOf(CaupolicanAtlasRepository.frontAtlas) }
    var backAtlas by remember { mutableStateOf(CaupolicanAtlasRepository.backAtlas) }
    var selection by remember { mutableStateOf<EditableSelection?>(null) }
    var selectedMuscleOverride by remember { mutableStateOf<String?>(null) }
    var selectedLandmarkId by remember { mutableStateOf<String?>(null) }
    var pendingInsertTarget by remember { mutableStateOf<PendingInsertTarget?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var ioStatus by remember { mutableStateOf<String?>(null) }

    val selectedSide = AtlasSide.valueOf(selectedSideName)
    val previewMode = HeatPreviewMode.valueOf(previewModeName)
    val currentAtlas = if (selectedSide == AtlasSide.FRONT) frontAtlas else backAtlas
    val overlaps = remember(currentAtlas) { calculateBoundingBoxOverlapWarnings(currentAtlas) }
    val exportBundleJson = remember(frontAtlas, backAtlas) {
        atlasJson.encodeToString(
            CaupolicanAtlasBundle(
                front = frontAtlas,
                back = backAtlas,
            )
        )
    }
    val exportSideJson = remember(currentAtlas) { atlasJson.encodeToString(currentAtlas) }
    val currentMuscles = remember(currentAtlas, selectedSide) {
        val catalog = if (selectedSide == AtlasSide.FRONT) frontCalibratorMuscles else backCalibratorMuscles
        (catalog + currentAtlas.bindings.map { it.muscleName }).distinct()
    }
    val selectedMuscleName = remember(selection, currentAtlas, selectedMuscleOverride, currentMuscles) {
        selection?.let { selected ->
            currentAtlas.regions.firstOrNull { it.id == selected.regionId }?.muscleName
        } ?: selectedMuscleOverride ?: currentMuscles.firstOrNull()
    }
    val selectedRegion = remember(selection, currentAtlas, selectedMuscleName) {
        selection?.let { currentAtlas.regions.firstOrNull { region -> region.id == it.regionId } }
            ?: selectedMuscleName?.let { currentAtlas.findRegionsForMuscle(it).firstOrNull() }
    }
    val selectedSubzone = remember(selection, selectedRegion) {
        selection?.let { activeSelection -> selectedRegion?.subzones?.firstOrNull { it.id == activeSelection.subzoneId } }
            ?: selectedRegion?.subzones?.firstOrNull()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioStatus = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: error("No se pudo abrir el archivo.")
        }.fold(
            onSuccess = { rawText ->
                val importedBundle = decodeImportedAtlasBundle(rawText, frontAtlas, backAtlas)
                val frontIssues = CaupolicanAtlasRepository.validateStructure(importedBundle.front).issues
                val backIssues = CaupolicanAtlasRepository.validateStructure(importedBundle.back).issues
                if (frontIssues.any { it.code.contains("missing") || it.code.contains("invalid") || it.code.contains("outside") } ||
                    backIssues.any { it.code.contains("missing") || it.code.contains("invalid") || it.code.contains("outside") }
                ) {
                    "Importación rechazada: el atlas cargado tiene errores estructurales."
                } else {
                    frontAtlas = importedBundle.front
                    backAtlas = importedBundle.back
                    selection = null
                    selectedMuscleOverride = null
                    selectedLandmarkId = null
                    "Atlas importado correctamente."
                }
            },
            onFailure = { "No pudimos importar el atlas: ${it.message}" },
        )
    }

    val exportBundleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioStatus = writeAtlasJsonToUri(
            context = context,
            uri = uri,
            jsonText = exportBundleJson,
            successMessage = "Bundle exportado correctamente.",
        )
    }

    val exportSideLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioStatus = writeAtlasJsonToUri(
            context = context,
            uri = uri,
            jsonText = exportSideJson,
            successMessage = "Atlas ${if (selectedSide == AtlasSide.FRONT) "frontal" else "posterior"} exportado correctamente.",
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Calibrador interno de atlas", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                "Edita landmarks y polígonos sobre el PNG real. Esto es un modo interno para corregir el atlas y exportarlo en formato estable.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
            )

            ioStatus?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtlasSide.entries.forEach { side ->
                    FilterChip(
                        selected = selectedSide == side,
                        onClick = {
                            selectedSideName = side.name
                            selection = null
                            selectedMuscleOverride = null
                            selectedLandmarkId = null
                            pendingInsertTarget = null
                        },
                        label = { Text(if (side == AtlasSide.FRONT) "Frente" else "Espalda") },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeatPreviewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = previewMode == mode,
                        onClick = { previewModeName = mode.name },
                        label = { Text(mode.label) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currentMuscles.forEach { muscle ->
                    FilterChip(
                        selected = selectedMuscleName == muscle,
                        onClick = {
                            selectedMuscleOverride = muscle
                            val firstRegion = currentAtlas.findRegionsForMuscle(muscle).firstOrNull()
                            val firstSubzone = firstRegion?.subzones?.firstOrNull()
                            if (firstRegion != null && firstSubzone != null) {
                                selection = EditableSelection(firstRegion.id, firstSubzone.id, 0)
                                selectedLandmarkId = null
                                pendingInsertTarget = null
                            } else {
                                selection = null
                                selectedLandmarkId = null
                                pendingInsertTarget = null
                            }
                        },
                        label = { Text(muscle) },
                    )
                }
            }

            selectedRegion?.let { region ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    region.subzones.forEach { subzone ->
                        FilterChip(
                            selected = selection?.subzoneId == subzone.id,
                            onClick = {
                                selection = EditableSelection(region.id, subzone.id, 0)
                                selectedMuscleOverride = region.muscleName
                                selectedLandmarkId = null
                                pendingInsertTarget = null
                            },
                            label = { Text(subzone.label) },
                        )
                    }
                }
            }

            if (selectedMuscleName != null && selectedRegion == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Sin zona en esta cara", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "Este músculo todavía no tiene región en ${if (selectedSide == AtlasSide.FRONT) "frente" else "espalda"}. Puedes crear una base y después moldearla con más puntos.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                        )
                        OutlinedButton(
                            onClick = {
                                val center = defaultRegionCenterFor(selectedMuscleName, selectedSide)
                                if (selectedSide == AtlasSide.FRONT) {
                                    val newRegion = frontAtlas.withAddedRegion(selectedMuscleName, selectedSide, center)
                                    frontAtlas = newRegion
                                    val firstRegion = newRegion.findRegionsForMuscle(selectedMuscleName).lastOrNull()
                                    val firstSubzone = firstRegion?.subzones?.firstOrNull()
                                    if (firstRegion != null && firstSubzone != null) {
                                        selection = EditableSelection(firstRegion.id, firstSubzone.id, 0)
                                    }
                                } else {
                                    val newRegion = backAtlas.withAddedRegion(selectedMuscleName, selectedSide, center)
                                    backAtlas = newRegion
                                    val firstRegion = newRegion.findRegionsForMuscle(selectedMuscleName).lastOrNull()
                                    val firstSubzone = firstRegion?.subzones?.firstOrNull()
                                    if (firstRegion != null && firstSubzone != null) {
                                        selection = EditableSelection(firstRegion.id, firstSubzone.id, 0)
                                    }
                                }
                                selectedLandmarkId = null
                                pendingInsertTarget = null
                            },
                        ) {
                            Text("Crear región base")
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val figureWidth = minOf(maxWidth * 0.72f, 280.dp)
                Box(
                    modifier = Modifier
                        .width(figureWidth)
                        .aspectRatio(0.44f),
                ) {
                    Image(
                        painter = painterResource(currentAtlas.imageSpec.drawableResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .pointerInput(currentAtlas, selection, selectedLandmarkId, canvasSize) {
                                detectTapGestures { offset ->
                                    val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                    val nearestSelection = findNearestSelection(currentAtlas, offset, size)
                                    if (nearestSelection != null) {
                                        selection = nearestSelection
                                        selectedLandmarkId = null
                                        pendingInsertTarget = null
                                    } else {
                                        val nearestLandmark = findNearestLandmarkId(currentAtlas, offset, size)
                                        if (nearestLandmark != null) {
                                            selectedLandmarkId = nearestLandmark
                                            selection = null
                                            pendingInsertTarget = null
                                        } else {
                                            val selected = selection
                                            val region = selected?.let { currentAtlas.regions.firstOrNull { region -> region.id == it.regionId } }
                                            val subzone = region?.subzones?.firstOrNull { it.id == selected.subzoneId }
                                            if (region != null && subzone != null) {
                                                pendingInsertTarget = findNearestInsertTarget(region.id, subzone, offset, size)
                                            } else {
                                                pendingInsertTarget = null
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(currentAtlas, selection, canvasSize) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val size = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                                        selection = findNearestSelection(currentAtlas, offset, size)
                                        selectedLandmarkId = if (selection == null) {
                                            findNearestLandmarkId(currentAtlas, offset, size)
                                        } else {
                                            null
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val activeSelection = selection
                                        if (activeSelection != null) {
                                            val region = currentAtlas.regions.firstOrNull { it.id == activeSelection.regionId }
                                            val subzone = region?.subzones?.firstOrNull { it.id == activeSelection.subzoneId }
                                            val currentPoint = subzone?.points?.getOrNull(activeSelection.pointIndex)
                                            if (currentPoint == null) return@detectDragGestures
                                            val newPoint = NormalizedPoint(
                                                x = (currentPoint.x + (change.positionChange().x / canvasSize.width.coerceAtLeast(1))).coerceIn(0f, 1f),
                                                y = (currentPoint.y + (change.positionChange().y / canvasSize.height.coerceAtLeast(1))).coerceIn(0f, 1f),
                                            )
                                            if (selectedSide == AtlasSide.FRONT) {
                                                frontAtlas = frontAtlas.withUpdatedPoint(activeSelection.regionId, activeSelection.subzoneId, activeSelection.pointIndex, newPoint)
                                            } else {
                                                backAtlas = backAtlas.withUpdatedPoint(activeSelection.regionId, activeSelection.subzoneId, activeSelection.pointIndex, newPoint)
                                            }
                                        } else {
                                            val landmarkId = selectedLandmarkId ?: return@detectDragGestures
                                            val landmark = currentAtlas.landmarks.firstOrNull { it.id == landmarkId } ?: return@detectDragGestures
                                            val newPoint = NormalizedPoint(
                                                x = (landmark.point.x + (change.positionChange().x / canvasSize.width.coerceAtLeast(1))).coerceIn(0f, 1f),
                                                y = (landmark.point.y + (change.positionChange().y / canvasSize.height.coerceAtLeast(1))).coerceIn(0f, 1f),
                                            )
                                            if (selectedSide == AtlasSide.FRONT) {
                                                frontAtlas = frontAtlas.withUpdatedLandmark(landmarkId, newPoint)
                                            } else {
                                                backAtlas = backAtlas.withUpdatedLandmark(landmarkId, newPoint)
                                            }
                                        }
                                    },
                                )
                            }
                            .background(Color.Transparent),
                    ) {
                        canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                        if (showSilhouette) {
                            drawPath(
                                path = buildRegionPath(currentAtlas.silhouette.points, size),
                                color = Color.White.copy(alpha = 0.08f),
                                style = Stroke(width = size.minDimension * 0.004f),
                            )
                        }

                        currentAtlas.regions.forEach { region ->
                            val isSelectedRegion = selection?.regionId == region.id
                            region.subzones.forEach { subzone ->
                                val path = buildRegionPath(subzone.points, size)
                                drawPath(
                                    path = path,
                                    color = heatColorForIntensityPrecise(previewMode.intensity).copy(alpha = if (isSelectedRegion) 0.34f else 0.18f),
                                    style = Fill,
                                )
                                drawPath(
                                    path = path,
                                    color = if (isSelectedRegion) Color.White else heatColorForIntensityPrecise(previewMode.intensity),
                                    style = Stroke(width = size.minDimension * if (isSelectedRegion) 0.0055f else 0.0035f),
                                )
                                subzone.points.forEachIndexed { index, point ->
                                    val isSelectedPoint = selection?.subzoneId == subzone.id && selection?.pointIndex == index
                                    drawCircle(
                                        color = if (isSelectedPoint) Color.White else Color(0xFF60A5FA),
                                        radius = size.minDimension * if (isSelectedPoint) 0.012f else 0.009f,
                                        center = Offset(point.x * size.width, point.y * size.height),
                                    )
                                }
                            }
                        }

                        if (showLandmarks) {
                            currentAtlas.landmarks.forEach { landmark ->
                                val offset = Offset(landmark.point.x * size.width, landmark.point.y * size.height)
                                drawCircle(
                                    color = if (selectedLandmarkId == landmark.id) Color.White else Color(0xFFF59E0B),
                                    radius = size.minDimension * if (selectedLandmarkId == landmark.id) 0.011f else 0.008f,
                                    center = offset,
                                )
                            }
                        }
                        pendingInsertTarget?.let { pending ->
                            drawCircle(
                                color = Color(0xFF06B6D4),
                                radius = size.minDimension * 0.010f,
                                center = Offset(pending.point.x * size.width, pending.point.y * size.height),
                            )
                        }
                    }
                }
            }

            selectedRegion?.let { region ->
                selectedSubzone?.let { subzone ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(region.muscleName, fontWeight = FontWeight.Black)
                            Text(
                                "Subzona: ${subzone.label} · Puntos: ${subzone.points.size}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            selection?.let { active ->
                                Text(
                                    "Punto activo: ${active.pointIndex + 1}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val pending = pendingInsertTarget
                                        if (pending != null && pending.regionId == region.id && pending.subzoneId == subzone.id) {
                                            if (selectedSide == AtlasSide.FRONT) {
                                                frontAtlas = frontAtlas.withInsertedPoint(region.id, subzone.id, pending.insertAfterIndex, pending.point)
                                            } else {
                                                backAtlas = backAtlas.withInsertedPoint(region.id, subzone.id, pending.insertAfterIndex, pending.point)
                                            }
                                            pendingInsertTarget = null
                                        } else {
                                            val point = selection?.let { subzone.points.getOrNull(it.pointIndex) }
                                                ?: subzone.points.lastOrNull()
                                                ?: return@OutlinedButton
                                            val candidate = point.copy(x = (point.x + 0.015f).coerceIn(0f, 1f))
                                            if (selectedSide == AtlasSide.FRONT) {
                                                frontAtlas = frontAtlas.withAddedPoint(region.id, subzone.id, candidate)
                                            } else {
                                                backAtlas = backAtlas.withAddedPoint(region.id, subzone.id, candidate)
                                            }
                                        }
                                    },
                                ) {
                                    Text(if (pendingInsertTarget?.subzoneId == subzone.id) "Insertar punto" else "Agregar punto")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val active = selection ?: return@OutlinedButton
                                        if (selectedSide == AtlasSide.FRONT) {
                                            frontAtlas = frontAtlas.withRemovedPoint(region.id, subzone.id, active.pointIndex)
                                        } else {
                                            backAtlas = backAtlas.withRemovedPoint(region.id, subzone.id, active.pointIndex)
                                        }
                                        selection = active.copy(pointIndex = 0)
                                        pendingInsertTarget = null
                                    },
                                ) {
                                    Text("Quitar punto")
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToggleRow("Ver landmarks", showLandmarks) { showLandmarks = it }
                    ToggleRow("Ver silueta", showSilhouette) { showSilhouette = it }
                    ToggleRow("Ver overlaps", showOverlaps) { showOverlaps = it }
                    if (showOverlaps) {
                        HorizontalDivider()
                        if (overlaps.isEmpty()) {
                            Text("Sin overlaps relevantes en el atlas actual.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            overlaps.take(6).forEach { warning ->
                                Text(warning, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Archivo de calibración", fontWeight = FontWeight.Bold)
                    Text(
                        "Puedes exportar el atlas completo o solo la cara actual. También puedes reimportar un JSON para seguir ajustándolo desde aquí.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val fileName = if (selectedSide == AtlasSide.FRONT) {
                                    "caupolican_front_atlas.json"
                                } else {
                                    "caupolican_back_atlas.json"
                                }
                                exportSideLauncher.launch(fileName)
                            },
                        ) {
                            Text("Exportar cara")
                        }
                        OutlinedButton(
                            onClick = { exportBundleLauncher.launch("caupolican_atlas_bundle.json") },
                        ) {
                            Text("Exportar bundle")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        ) {
                            Text("Importar JSON")
                        }
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(exportBundleJson)) }) {
                            Text("Copiar bundle")
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("JSON cara actual", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { clipboardManager.setText(AnnotatedString(exportSideJson)) }) { Text("Copiar") }
                    }
                    SelectionContainer {
                        Text(exportSideJson, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun buildRegionPath(points: List<NormalizedPoint>, size: Size): Path {
    return Path().apply {
        points.forEachIndexed { index, point ->
            val offset = Offset(point.x * size.width, point.y * size.height)
            if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
        }
        close()
    }
}

private fun scalePolygon(points: List<NormalizedPoint>, factor: Float): List<NormalizedPoint> {
    if (points.isEmpty()) return points
    val centroidX = points.map { it.x }.average().toFloat()
    val centroidY = points.map { it.y }.average().toFloat()
    return points.map { point ->
        NormalizedPoint(
            x = (centroidX + (point.x - centroidX) * factor).coerceIn(0f, 1f),
            y = (centroidY + (point.y - centroidY) * factor).coerceIn(0f, 1f),
        )
    }
}

private fun findNearestSelection(
    atlas: CaupolicanAtlas,
    offset: Offset,
    size: Size,
): EditableSelection? {
    var bestSelection: EditableSelection? = null
    var bestDistance = Float.MAX_VALUE
    atlas.regions.forEach { region ->
        region.subzones.forEach { subzone ->
            subzone.points.forEachIndexed { index, point ->
                val pointOffset = Offset(point.x * size.width, point.y * size.height)
                val distance = abs(pointOffset.x - offset.x) + abs(pointOffset.y - offset.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestSelection = EditableSelection(region.id, subzone.id, index)
                }
            }
        }
    }
    return bestSelection.takeIf { bestDistance <= maxOf(size.width, size.height) * 0.08f }
}

private fun findNearestLandmarkId(
    atlas: CaupolicanAtlas,
    offset: Offset,
    size: Size,
): String? {
    var bestId: String? = null
    var bestDistance = Float.MAX_VALUE
    atlas.landmarks.forEach { landmark ->
        val pointOffset = Offset(landmark.point.x * size.width, landmark.point.y * size.height)
        val distance = abs(pointOffset.x - offset.x) + abs(pointOffset.y - offset.y)
        if (distance < bestDistance) {
            bestDistance = distance
            bestId = landmark.id
        }
    }
    return bestId.takeIf { bestDistance <= maxOf(size.width, size.height) * 0.06f }
}

private fun findNearestInsertTarget(
    regionId: String,
    subzone: com.example.kpkn.domain.training.MuscleSubzone,
    offset: Offset,
    size: Size,
): PendingInsertTarget? {
    if (subzone.points.size < 2) return null
    var bestTarget: PendingInsertTarget? = null
    var bestDistance = Float.MAX_VALUE
    val scaledPoints = subzone.points.map { point -> Offset(point.x * size.width, point.y * size.height) }
    scaledPoints.forEachIndexed { index, current ->
        val next = scaledPoints[(index + 1) % scaledPoints.size]
        val projection = projectPointOntoSegment(offset, current, next)
        val distance = abs(projection.x - offset.x) + abs(projection.y - offset.y)
        if (distance < bestDistance) {
            bestDistance = distance
            bestTarget = PendingInsertTarget(
                regionId = regionId,
                subzoneId = subzone.id,
                insertAfterIndex = index,
                point = NormalizedPoint(
                    x = (projection.x / size.width).coerceIn(0f, 1f),
                    y = (projection.y / size.height).coerceIn(0f, 1f),
                ),
            )
        }
    }
    return bestTarget.takeIf { bestDistance <= maxOf(size.width, size.height) * 0.09f }
}

private fun projectPointOntoSegment(
    point: Offset,
    start: Offset,
    end: Offset,
): Offset {
    val segmentX = end.x - start.x
    val segmentY = end.y - start.y
    val segmentLengthSquared = (segmentX * segmentX) + (segmentY * segmentY)
    if (segmentLengthSquared <= 0.0001f) return start
    val t = (((point.x - start.x) * segmentX) + ((point.y - start.y) * segmentY)) / segmentLengthSquared
    val clamped = t.coerceIn(0f, 1f)
    return Offset(
        x = start.x + segmentX * clamped,
        y = start.y + segmentY * clamped,
    )
}

private fun defaultRegionCenterFor(
    muscleName: String,
    side: AtlasSide,
): NormalizedPoint {
    val map = when (side) {
        AtlasSide.FRONT -> mapOf(
            "Cuello" to NormalizedPoint(0.50f, 0.15f),
            "Deltoides" to NormalizedPoint(0.24f, 0.20f),
            "Pectorales" to NormalizedPoint(0.38f, 0.24f),
            "Bíceps" to NormalizedPoint(0.20f, 0.32f),
            "Antebrazo" to NormalizedPoint(0.16f, 0.48f),
            "Trapecio" to NormalizedPoint(0.50f, 0.18f),
            "Dorsales" to NormalizedPoint(0.34f, 0.34f),
            "Tríceps" to NormalizedPoint(0.18f, 0.32f),
            "Erectores Espinales" to NormalizedPoint(0.50f, 0.40f),
            "Core" to NormalizedPoint(0.34f, 0.40f),
            "Abdomen" to NormalizedPoint(0.50f, 0.40f),
            "Glúteos" to NormalizedPoint(0.40f, 0.60f),
            "Aductores" to NormalizedPoint(0.47f, 0.67f),
            "Cuádriceps" to NormalizedPoint(0.38f, 0.71f),
            "Isquiosurales" to NormalizedPoint(0.38f, 0.74f),
            "Pantorrillas" to NormalizedPoint(0.40f, 0.90f),
        )
        AtlasSide.BACK -> mapOf(
            "Cuello" to NormalizedPoint(0.50f, 0.13f),
            "Trapecio" to NormalizedPoint(0.40f, 0.20f),
            "Deltoides" to NormalizedPoint(0.24f, 0.22f),
            "Dorsales" to NormalizedPoint(0.34f, 0.36f),
            "Tríceps" to NormalizedPoint(0.18f, 0.34f),
            "Antebrazo" to NormalizedPoint(0.15f, 0.50f),
            "Erectores Espinales" to NormalizedPoint(0.50f, 0.42f),
            "Core" to NormalizedPoint(0.50f, 0.42f),
            "Abdomen" to NormalizedPoint(0.50f, 0.42f),
            "Glúteos" to NormalizedPoint(0.40f, 0.60f),
            "Aductores" to NormalizedPoint(0.46f, 0.67f),
            "Cuádriceps" to NormalizedPoint(0.40f, 0.72f),
            "Isquiosurales" to NormalizedPoint(0.40f, 0.78f),
            "Pantorrillas" to NormalizedPoint(0.40f, 0.92f),
            "Pectorales" to NormalizedPoint(0.38f, 0.24f),
            "Bíceps" to NormalizedPoint(0.20f, 0.32f),
        )
    }
    return map[muscleName] ?: NormalizedPoint(0.50f, 0.50f)
}

private fun heatIntensityForMusclePrecise(
    weeklySets: Double,
    target: PersonalizedVolumeTarget?,
    isVolumeCalibrated: Boolean,
): Float {
    if (!isVolumeCalibrated || target == null) {
        return (weeklySets / 20.0).toFloat().coerceIn(0f, 1f)
    }

    val min = target.minEffective.toDouble().coerceAtLeast(1.0)
    val ideal = target.maxAdaptive.toDouble().coerceAtLeast(min)
    val recoverable = target.maxRecoverable.toDouble().coerceAtLeast(ideal + 1.0)

    return when {
        weeklySets <= 0.0 -> 0f
        weeklySets < min -> (0.18f + ((weeklySets / min) * 0.18f).toFloat()).coerceIn(0f, 0.36f)
        weeklySets <= ideal -> {
            val fraction = ((weeklySets - min) / (ideal - min).coerceAtLeast(1.0)).toFloat()
            (0.48f + fraction * 0.18f).coerceIn(0.48f, 0.66f)
        }
        weeklySets <= recoverable -> {
            val fraction = ((weeklySets - ideal) / (recoverable - ideal).coerceAtLeast(1.0)).toFloat()
            (0.78f + fraction * 0.14f).coerceIn(0.78f, 0.92f)
        }
        else -> 1f
    }
}

private fun heatColorForIntensityPrecise(intensity: Float): Color {
    return when {
        intensity <= 0.36f -> Color(0xFFEAB308)
        intensity <= 0.70f -> Color(0xFF22C55E)
        intensity <= 0.92f -> Color(0xFFF97316)
        else -> Color(0xFFDC2626)
    }
}

private fun buildMuscleStatusTextPrecise(
    weeklySets: Double,
    target: PersonalizedVolumeTarget?,
    isVolumeCalibrated: Boolean,
): String {
    if (!isVolumeCalibrated || target == null) {
        return "${formatOneDecimal(weeklySets)} series/sem"
    }
    return when {
        weeklySets < target.minEffective -> "Subentrenado · ${formatOneDecimal(weeklySets)}"
        weeklySets <= target.maxAdaptive -> "Ideal · ${formatOneDecimal(weeklySets)}"
        weeklySets <= target.maxRecoverable -> "Alto · ${formatOneDecimal(weeklySets)}"
        else -> "Sobreentreno · ${formatOneDecimal(weeklySets)}"
    }
}

private fun formatOneDecimal(value: Double): String = String.format("%.1f", value)

private fun decodeImportedAtlasBundle(
    rawText: String,
    currentFront: CaupolicanAtlas,
    currentBack: CaupolicanAtlas,
): CaupolicanAtlasBundle {
    return runCatching {
        atlasJson.decodeFromString<CaupolicanAtlasBundle>(rawText)
    }.getOrElse {
        val singleAtlas = atlasJson.decodeFromString<CaupolicanAtlas>(rawText)
        when (singleAtlas.side) {
            AtlasSide.FRONT -> CaupolicanAtlasBundle(front = singleAtlas, back = currentBack)
            AtlasSide.BACK -> CaupolicanAtlasBundle(front = currentFront, back = singleAtlas)
        }
    }
}

private fun writeAtlasJsonToUri(
    context: android.content.Context,
    uri: Uri,
    jsonText: String,
    successMessage: String,
): String {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(jsonText.toByteArray())
            output.flush()
        } ?: error("No se pudo abrir el destino para escritura.")
        successMessage
    }.getOrElse { "No pudimos exportar el atlas: ${it.message}" }
}
