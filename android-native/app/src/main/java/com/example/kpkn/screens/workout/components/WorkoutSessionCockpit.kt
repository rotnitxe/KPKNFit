package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.workout.toTrimmedNumberString
import java.io.File
import kotlin.math.max

data class UnilateralBalanceUi(
    val leftScore: Double,
    val rightScore: Double,
) {
    val total: Double get() = leftScore + rightScore
    val leftRatio: Float get() = if (total <= 0) 0.5f else (leftScore / total).toFloat()
    val rightRatio: Float get() = 1f - leftRatio
    val imbalancePercent: Int
        get() {
            if (leftScore <= 0 || rightScore <= 0) return 0
            val ratio = kotlin.math.abs(leftScore - rightScore) / max(leftScore, rightScore)
            return (ratio * 100).toInt()
        }
}

@Composable
fun WorkoutSessionCockpit(
    currentExercise: Exercise?,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    exerciseNote: String,
    exercisePhotos: List<String>,
    sessionProgressLabel: String,
    onNoteChange: (String) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    sessionAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cockpit de la Sesión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Surface(
                shape = WorkoutUiTokens.ChipShape,
                color = sessionAccentColor.copy(alpha = 0.22f),
            ) {
                Text(
                    text = sessionProgressLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = sessionAccentColor,
                )
            }
        }

        CockpitCard(title = "Hitos", icon = { Icon(Icons.Default.EmojiEvents, null, Modifier.size(16.dp), tint = Color(0xFFFFD600)) }) {
            val relevant = milestones.filter { currentExercise == null || it.exerciseId == currentExercise.id }
                .ifEmpty { milestones.takeLast(5) }
            if (relevant.isEmpty()) {
                Text(
                    "Todavía no hay hitos. Un PR o meta destacada aparecerán aquí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                relevant.takeLast(6).asReversed().forEach { milestone ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1F1F1F),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(milestone.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(
                                buildString {
                                    append(milestone.exerciseName)
                                    milestone.detail?.let { append(" · "); append(it) }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (currentExercise?.isStarTarget == true) {
            val goal = currentExercise.goal1RM?.takeIf { it > 0 }
            val bestSessionE1rm = completedSets
                .filterKeys { it.startsWith(currentExercise.id) }
                .values
                .filter { it.weight > 0 && it.reps > 0 }
                .maxOfOrNull { calculateHybrid1RM(it.weight, it.reps) }
            CockpitCard(title = "META estrella", icon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFD600)) }) {
                if (goal == null) {
                    Text(
                        "Ejercicio marcado como estrella. Define una meta 1RM en el editor para ver progreso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val progress = ((bestSessionE1rm ?: 0.0) / goal).toFloat().coerceIn(0f, 1f)
                    Text(
                        if (bestSessionE1rm != null) {
                            "${bestSessionE1rm.toTrimmedNumberString()} / ${goal.toTrimmedNumberString()} kg e1RM"
                        } else {
                            "Meta ${goal.toTrimmedNumberString()} kg · sin series aún"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = sessionAccentColor,
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                }
            }
        }

        if (currentExercise?.isEffectivelyUnilateral() == true) {
            val balance = remember(currentExercise.id, completedSets) {
                unilateralBalanceFor(currentExercise.id, completedSets)
            }
            CockpitCard(title = "Balance unilateral") {
                if (balance == null || balance.total <= 0) {
                    Text(
                        "Registra ambos lados para ver el balance visual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        if (balance.imbalancePercent <= 8) "Equilibrado (±${balance.imbalancePercent}%)"
                        else "Desbalance ~${balance.imbalancePercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (balance.imbalancePercent <= 8) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BalanceBar(
                            label = "Izq",
                            ratio = balance.leftRatio,
                            color = sessionAccentColor,
                            modifier = Modifier.weight(1f),
                        )
                        BalanceBar(
                            label = "Der",
                            ratio = balance.rightRatio,
                            color = Color(0xFF64B5F6),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        CockpitCard(title = "Notas del ejercicio", icon = { Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(16.dp)) }) {
            var draft by remember(currentExercise?.id) { mutableStateOf(exerciseNote) }
            val draftLatest = rememberUpdatedState(draft)
            LaunchedEffect(currentExercise?.id) {
                draft = exerciseNote
            }
            DisposableEffect(currentExercise?.id) {
                onDispose { onNoteChange(draftLatest.value) }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onNoteChange(it)
                },
                placeholder = { Text("Sensación, setup, tip… se verá en el historial") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
        }

        CockpitCard(title = "Fotos (máx. 2)", icon = { Icon(Icons.Default.AddAPhoto, null, Modifier.size(16.dp)) }) {
            val context = LocalContext.current
            var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
            var cameraPermissionDenied by remember { mutableStateOf(false) }
            val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) onAddPhoto(uri)
            }
            val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                val uri = pendingCameraUri
                pendingCameraUri = null
                if (ok && uri != null) onAddPhoto(uri)
            }
            fun launchCameraCapture() {
                cameraPermissionDenied = false
                val dir = File(context.cacheDir, "workout_camera").also { it.mkdirs() }
                val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    launchCameraCapture()
                } else {
                    cameraPermissionDenied = true
                }
            }
            fun requestCameraOrCapture() {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercisePhotos.forEach { path ->
                        Box {
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            IconButton(
                                onClick = { onRemovePhoto(path) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp),
                            ) {
                                Icon(Icons.Default.Close, "Quitar", Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                    }
                    if (exercisePhotos.size < 2) {
                        Surface(
                            onClick = { requestCameraOrCapture() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AddAPhoto, "Cámara", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Surface(
                            onClick = { galleryPicker.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PhotoLibrary, "Galería", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
                if (cameraPermissionDenied) {
                    Text(
                        "Permiso de cámara denegado. Puedes usar la galería o habilitarlo en Ajustes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFC107),
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitCard(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF2A2A2A),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon?.invoke()
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun BalanceBar(
    label: String,
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0.08f, 1f))
                    .height(12.dp)
                    .background(color),
            )
        }
    }
}

internal fun unilateralBalanceFor(
    exerciseId: String,
    completedSets: Map<String, CompletedSet>,
): UnilateralBalanceUi? {
    fun score(set: CompletedSet): Double {
        val metric = when {
            (set.timeSeconds ?: 0) > 0 -> set.timeSeconds?.toDouble() ?: 0.0
            set.reps > 0 -> set.reps.toDouble()
            else -> 0.0
        }
        return (set.weight.coerceAtLeast(0.0) + 1.0) * metric
    }
    val left = completedSets.filterKeys { it.startsWith(exerciseId) && it.endsWith("_L") }.values.sumOf { score(it) }
    val right = completedSets.filterKeys { it.startsWith(exerciseId) && it.endsWith("_R") }.values.sumOf { score(it) }
    if (left <= 0 && right <= 0) return null
    return UnilateralBalanceUi(left, right)
}
