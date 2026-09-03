package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.workout.WorkoutRmCalcContent
import com.example.kpkn.screens.workout.sessionCalorieStatus
import com.example.kpkn.screens.workout.toTrimmedNumberString
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CockpitPage(val title: String) {
    Overview("Resumen"),
    Photos("Fotos"),
    Tools("Herramientas"),
    Notes("Notas"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutSessionCockpit(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionProgressLabel: String,
    liveEnergySummary: SessionEnergySummary,
    sessionNotes: String,
    sessionSavedNotes: List<com.example.kpkn.data.models.SessionSavedNote> = emptyList(),
    sessionPhotos: List<String>,
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit = {},
    onAddSessionPhoto: (Uri) -> Unit,
    onRemoveSessionPhoto: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    sessionAccentColor: Color,
    bodyWeight: Double? = null,
    modifier: Modifier = Modifier,
) {
    val pages = CockpitPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            .heightIn(max = 460.dp)
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Cockpit de la sesión",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = sessionAccentColor.copy(alpha = 0.18f),
            ) {
                Text(
                    sessionProgressLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = sessionAccentColor,
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = sessionAccentColor,
                    )
                }
            },
        ) {
            pages.forEachIndexed { index, page ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    selectedContentColor = sessionAccentColor,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    text = {
                        Text(
                            page.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 380.dp),
        ) { page ->
            when (pages[page]) {
                CockpitPage.Overview -> CockpitOverviewPage(
                    exercises = exercises,
                    completedSets = completedSets,
                    milestones = milestones,
                    sessionAccentColor = sessionAccentColor,
                )
                CockpitPage.Photos -> CockpitPhotosPage(
                    sessionPhotos = sessionPhotos,
                    onAddPhoto = onAddSessionPhoto,
                    onRemovePhoto = onRemoveSessionPhoto,
                    sessionAccentColor = sessionAccentColor,
                )
                CockpitPage.Tools -> CockpitToolsPage(
                    liveEnergySummary = liveEnergySummary,
                    sessionAccentColor = sessionAccentColor,
                    bodyWeight = bodyWeight,
                )
                CockpitPage.Notes -> CockpitNotesPage(
                    sessionNotes = sessionNotes,
                    sessionSavedNotes = sessionSavedNotes,
                    sessionChecklist = sessionChecklist,
                    onSessionNotesChange = onSessionNotesChange,
                    onSaveSessionNote = onSaveSessionNote,
                    onAddChecklistItem = onAddChecklistItem,
                    onToggleChecklistItem = onToggleChecklistItem,
                    onRemoveChecklistItem = onRemoveChecklistItem,
                    sessionAccentColor = sessionAccentColor,
                )
            }
        }
    }
}

@Composable
private fun CockpitOverviewPage(
    exercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    milestones: List<SessionMilestone>,
    sessionAccentColor: Color,
) {
    val starExercises = remember(exercises) { exercises.filter { it.isStarTarget } }
    val orderedMilestones = remember(milestones) {
        milestones.sortedByDescending { it.createdAtIso }.take(12)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CockpitSectionTitle(icon = Icons.Default.EmojiEvents, title = "Hitos de la sesión")
        if (orderedMilestones.isEmpty()) {
            Text(
                "Todavía no hay hitos. Aparecen con PRs reales vs historial o meta estrella.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            orderedMilestones.forEach { milestone ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            milestone.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            milestone.exerciseName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                        milestone.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }

        if (starExercises.isNotEmpty()) {
            CockpitSectionTitle(icon = Icons.Default.Star, title = "Ejercicios estrella")
            starExercises.forEach { exercise ->
                val goal = exercise.goal1RM?.takeIf { it > 0 } ?: return@forEach
                val bestE1rm = completedSets
                    .filterKeys { key ->
                        (key.startsWith("${exercise.id}_") || key == exercise.id) &&
                            !key.contains("_warmup_")
                    }
                    .values
                    .filter { !it.isWarmup && !it.skipped && (it.weight ?: 0.0) > 0 && it.reps > 0 }
                    .maxOfOrNull { calculateHybrid1RM(it.weight ?: 0.0, it.reps) }
                    ?: 0.0
                val progress = (bestE1rm / goal).toFloat().coerceIn(0f, 1f)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = sessionAccentColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${bestE1rm.toTrimmedNumberString()} / ${goal.toTrimmedNumberString()} kg e1RM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = sessionAccentColor,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitPhotosPage(
    sessionPhotos: List<String>,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    sessionAccentColor: Color,
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAddPhoto(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) onAddPhoto(uri)
        pendingCameraUri = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createSessionCameraUri(context) ?: return@rememberLauncherForActivityResult
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val uri = createSessionCameraUri(context) ?: return@TextButton
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = sessionAccentColor)
                Spacer(Modifier.size(6.dp))
                Text("Cámara")
            }
            TextButton(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = sessionPhotos.size < 8,
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = sessionAccentColor)
                Spacer(Modifier.size(6.dp))
                Text("Galería")
            }
        }
        if (sessionPhotos.isEmpty()) {
            Text(
                "Fotos generales de la sesión (hasta 8).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            sessionPhotos.forEach { path ->
                Box {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    IconButton(
                        onClick = { onRemovePhoto(path) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar foto", tint = Color.White)
                    }
                }
            }
        }
    }
}

private enum class CockpitToolTab(val title: String) {
    Timer("Temporizador"),
    Rm("Calculadora RM"),
    Calories("Calorías"),
}

@Composable
private fun CockpitToolsPage(
    liveEnergySummary: SessionEnergySummary,
    sessionAccentColor: Color,
    bodyWeight: Double?,
) {
    var selectedTool by remember { mutableStateOf(CockpitToolTab.Timer) }
    var timerRunning by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timerRunning) {
        while (timerRunning) {
            delay(250)
            elapsedMs += 250
        }
    }

    val totalSeconds = (elapsedMs / 1000L).toInt()
    val mm = totalSeconds / 60
    val ss = totalSeconds % 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CockpitToolTab.entries.forEach { tab ->
                val selected = selectedTool == tab
                Surface(
                    onClick = { selectedTool = tab },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        sessionAccentColor.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.06f)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        tab.title,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.92f else 0.68f),
                    )
                }
            }
        }

        when (selectedTool) {
            CockpitToolTab.Timer -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "%02d:%02d".format(mm, ss),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { timerRunning = !timerRunning }) {
                                Icon(
                                    if (timerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (timerRunning) "Pausar" else "Iniciar",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = {
                                    timerRunning = false
                                    elapsedMs = 0L
                                },
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reiniciar",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                        Text(
                            "Independiente del descanso entre series",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            CockpitToolTab.Rm -> {
                WorkoutRmCalcContent(
                    bodyWeight = bodyWeight,
                    sessionAccentColor = sessionAccentColor,
                    softInputs = true,
                )
            }
            CockpitToolTab.Calories -> {
                val completedCount = liveEnergySummary.exerciseContributions.sumOf { it.completedSets }
                val status = sessionCalorieStatus(liveEnergySummary, completedCount, bodyWeight)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Calorías quemadas",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        )
                        if (status.kcal != null) {
                            Text(
                                "${status.kcal} kcal",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                status.hint.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitNotesPage(
    sessionNotes: String,
    sessionSavedNotes: List<com.example.kpkn.data.models.SessionSavedNote>,
    sessionChecklist: List<SessionChecklistItem>,
    onSessionNotesChange: (String) -> Unit,
    onSaveSessionNote: (String) -> Unit,
    onAddChecklistItem: (String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    sessionAccentColor: Color,
) {
    val lastSaved = sessionSavedNotes.lastOrNull()?.text
    var draftItem by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CockpitSectionTitle(icon = Icons.AutoMirrored.Filled.Notes, title = "Notas de sesión")
        WorkoutSoftField(
            value = sessionNotes,
            onValueChange = onSessionNotesChange,
            placeholder = "Anotaciones generales de la sesión…",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        TextButton(
            onClick = { onSaveSessionNote(sessionNotes) },
            enabled = sessionNotes.isNotBlank(),
        ) {
            Text("Guardar nota", color = MaterialTheme.colorScheme.onSurface)
        }
        if (lastSaved != null) {
            Text(
                lastSaved,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        CockpitSectionTitle(icon = Icons.Default.Add, title = "Tareas / objetivos")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkoutSoftField(
                value = draftItem,
                onValueChange = { draftItem = it },
                modifier = Modifier.weight(1f),
                placeholder = "Nueva tarea",
            )
            TextButton(
                onClick = {
                    onAddChecklistItem(draftItem)
                    draftItem = ""
                },
                enabled = draftItem.isNotBlank(),
            ) {
                Text("Añadir", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (sessionChecklist.isEmpty()) {
            Text(
                "Checklist vacío. Añade objetivos para esta sesión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            sessionChecklist.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.done,
                        onCheckedChange = { onToggleChecklistItem(item.id) },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            checkmarkColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Text(
                        item.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.done) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    IconButton(onClick = { onRemoveChecklistItem(item.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitSectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

private fun createSessionCameraUri(context: android.content.Context): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "workout_camera").also { if (!it.exists()) it.mkdirs() }
        val file = File(dir, "session_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
