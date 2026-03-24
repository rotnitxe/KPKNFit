package com.example.kpkn.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Home Screen ────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
) {
    // Collect all ViewModel state
    val muscularProgress by viewModel.muscularProgress.collectAsState()
    val sncProgress by viewModel.sncProgress.collectAsState()
    val columnaProgress by viewModel.columnaProgress.collectAsState()
    val selectedRingIndex by viewModel.selectedRingIndex.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val ringsViewMode by viewModel.ringsViewMode.collectAsState()
    val programs by viewModel.programs.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val greeting = viewModel.getGreeting()

    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first != null && first.index == 0)
                (kotlin.math.abs(first.offset) / 250f).coerceIn(0f, 1f)
            else 1f
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    scrollProgress = scrollProgress,
                    greeting = greeting,
                    userName = userName,
                    onSettingsClick = { /* TODO: navigate to settings */ },
                    hasPrograms = programs.isNotEmpty(),
                )
            },
        ) { innerPadding ->
            if (programs.isEmpty()) {
                // Empty state
                HomeEmptyState(
                    greeting = greeting,
                    userName = userName,
                    onNavigateToEditor = { /* TODO: navigate to program editor */ },
                    onNavigateToCard = { /* TODO */ },
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                // Full home with program
                HomeWithProgram(
                    viewModel = viewModel,
                    muscularProgress = muscularProgress,
                    sncProgress = sncProgress,
                    columnaProgress = columnaProgress,
                    ringsViewMode = ringsViewMode,
                    todaySessions = todaySessions,
                    listState = listState,
                    scrollProgress = scrollProgress,
                    userName = userName,
                    greeting = greeting,
                    onRingSelect = { viewModel.selectRing(it) },
                    onRingsViewChange = { viewModel.setRingsViewMode(it) },
                    onThemeToggle = { /* TODO: toggle theme */ },
                    onSettingsClick = { /* TODO: navigate to settings */ },
                    onStartWorkout = { _, _ -> /* TODO */ },
                    onResumeWorkout = { /* TODO */ },
                    onNavigateToCard = { /* TODO */ },
                    onNavigate = { /* TODO */ },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        // Calibration overlay
        AnimatedVisibility(
            visible = selectedRingIndex != -1,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CalibrationOverlay(
                index = selectedRingIndex,
                initialProgress = when (selectedRingIndex) {
                    0 -> muscularProgress
                    1 -> sncProgress
                    else -> columnaProgress
                },
                onProgressChange = { viewModel.updateProgress(selectedRingIndex, it) },
                onDismiss = { viewModel.clearSelection() },
            )
        }
    }
}

// ─── Home With Program (Full Layout) ────────────────────────────────────────

@Composable
private fun HomeWithProgram(
    viewModel: HomeViewModel,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    ringsViewMode: HomeViewModel.RingsViewMode,
    todaySessions: List<com.example.kpkn.data.models.TodaySessionItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollProgress: Float,
    userName: String,
    greeting: String,
    onRingSelect: (Int) -> Unit,
    onRingsViewChange: (HomeViewModel.RingsViewMode) -> Unit,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onStartWorkout: (com.example.kpkn.data.models.Session, com.example.kpkn.data.models.Program) -> Unit,
    onResumeWorkout: () -> Unit,
    onNavigateToCard: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val programs by viewModel.programs.collectAsState()
    val mainAlpha = (1f - scrollProgress * 2f).coerceIn(0f, 1f)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ─── Header ─────────────────────────────────────────────────────────
        item {
            Box(Modifier.graphicsLayer { alpha = mainAlpha }) {
                HomeHeaderSection(
                    greeting = greeting,
                    userName = userName,
                    ringsViewMode = ringsViewMode,
                    onThemeToggle = onThemeToggle,
                    onSettingsClick = onSettingsClick,
                    onRingsViewChange = onRingsViewChange,
                )
            }
        }

        // ─── Tus RINGS ──────────────────────────────────────────────────────
        item {
            HomeRingsSection(
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                ringsViewMode = ringsViewMode,
                onRingSelect = onRingSelect,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        // ─── Sesión de hoy ──────────────────────────────────────────────────
        item {
            HomeSessionSection(
                sessions = todaySessions,
                currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK),
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
            )
        }

        // ─── Progreso físico + Ejercicios (Cards) ──────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            HomeCardsSection(
                viewModel = viewModel,
                onNavigateToCard = onNavigateToCard,
            )
        }

        // ─── Tus Programas ─────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            HomeProgramsSection(
                programs = programs,
                onProgramClick = { /* TODO: navigate to program detail */ },
            )
        }

        // ─── Rincones ──────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            HomeCornersSection(onNavigate = onNavigate)
            Spacer(Modifier.height(80.dp)) // Bottom padding for nav bar
        }
    }
}

// ─── Empty State ────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyState(
    greeting: String,
    userName: String,
    onNavigateToEditor: () -> Unit,
    onNavigateToCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Animated background with icons
        AnimatedIconBackground()

        // Content on top
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            // CTA Card
            item {
                CreateProgramCard(
                    onClick = onNavigateToEditor,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

// ─── Top Bar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    scrollProgress: Float,
    greeting: String,
    userName: String,
    onSettingsClick: () -> Unit,
    hasPrograms: Boolean = true,
) {
    val headerHeight by animateDpAsState(
        if (scrollProgress > 0.5f) 64.dp else 0.dp,
        label = "topBarHeight",
    )

    if (headerHeight > 0.dp) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedVisibility(
                    visible = scrollProgress > 0.6f && hasPrograms,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        "$greeting, $userName!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─── Calibration Overlay ────────────────────────────────────────────────────

@Composable
private fun CalibrationOverlay(
    index: Int,
    initialProgress: Float,
    onProgressChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var localP by remember(index) { mutableFloatStateOf(initialProgress) }
    val color = when (index) {
        0 -> Color(0xFFFF5252)
        1 -> Color(0xFF448AFF)
        else -> Color(0xFFFFD740)
    }
    val name = when (index) {
        0 -> "MUSCULAR"
        1 -> "SNC"
        else -> "COLUMNA"
    }
    val msg = when (index) {
        0 -> "Lectura automática."
        1 -> "¿Cómo te sientes mentalmente?"
        else -> "¿Fatiga en espalda?"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .pointerInput(index) {
                if (index != 0) detectDragGestures { change, drag ->
                    change.consume()
                    localP = (localP - drag.y / 1000f).coerceIn(0f, 1f)
                    onProgressChange(localP)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Text(
                "RECALIBRANDO $name",
                color = color,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )

            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    drawCircle(
                        color.copy(alpha = 0.1f),
                        style = Stroke(20.dp.toPx()),
                    )
                    drawArc(
                        color,
                        -90f,
                        360f * localP,
                        false,
                        style = Stroke(20.dp.toPx()),
                    )
                }
                Text(
                    "${(localP * 100).toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
            }

            Text(
                msg,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            if (index == 0) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                ) {
                    Text("CERRAR", color = Color.Black)
                }
            }
        }
    }
}
