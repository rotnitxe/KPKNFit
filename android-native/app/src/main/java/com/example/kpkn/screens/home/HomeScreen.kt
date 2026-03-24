package com.example.kpkn.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.R

// ─── Home Screen ────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
) {
    val muscularProgress by viewModel.muscularProgress.collectAsState()
    val sncProgress by viewModel.sncProgress.collectAsState()
    val columnaProgress by viewModel.columnaProgress.collectAsState()
    val selectedRingIndex by viewModel.selectedRingIndex.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val ringsViewMode by viewModel.ringsViewMode.collectAsState()
    val programs by viewModel.programs.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val dailyCalorieGoal by viewModel.dailyCalorieGoal.collectAsState()
    val greeting = viewModel.getGreeting()

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Margen interno de cada item (padding antes del contenido visible)
    val item0TopMarginPx = with(density) { 16.dp.roundToPx() }  // HomeHeaderSection padding(top=16.dp)
    val item1TopMarginPx = 0  // HomeRingsSection no tiene top padding

    // Progreso 1:1 con el scroll: cut_px / contenido_visible_total
    // Si el 50% del item está cortado → progress = 0.5 → 50% de visibilidad en topbar
    val greetingProgress by remember(item0TopMarginPx) {
        derivedStateOf {
            val item0 = listState.layoutInfo.visibleItemsInfo.find { it.index == 0 }
            when {
                item0 == null -> 1f
                item0.offset > -item0TopMarginPx -> 0f
                else -> {
                    val cut = (-item0.offset - item0TopMarginPx).toFloat()
                    val visibleHeight = (item0.size - item0TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight).coerceIn(0f, 1f)
                }
            }
        }
    }

    val ringsProgress by remember(item1TopMarginPx) {
        derivedStateOf {
            val item1 = listState.layoutInfo.visibleItemsInfo.find { it.index == 1 }
            when {
                item1 == null -> 1f
                item1.offset > -item1TopMarginPx -> 0f
                else -> {
                    val cut = (-item1.offset - item1TopMarginPx).toFloat()
                    val visibleHeight = (item1.size - item1TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Sesión de hoy: item 2 en LazyColumn
    val sessionProgress by remember {
        derivedStateOf {
            val item2 = listState.layoutInfo.visibleItemsInfo.find { it.index == 2 }
            when {
                item2 == null -> 1f
                item2.offset > 0 -> 0f
                else -> {
                    val cut = (-item2.offset).toFloat()
                    val visibleHeight = item2.size.toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Progreso de nutrición: item 3 en LazyColumn
    val nutritionProgress by remember {
        derivedStateOf {
            val item3 = listState.layoutInfo.visibleItemsInfo.find { it.index == 3 }
            when {
                item3 == null -> 1f
                item3.offset > 0 -> 0f
                else -> {
                    val cut = (-item3.offset).toFloat()
                    val visibleHeight = item3.size.toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight).coerceIn(0f, 1f)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    greeting = greeting,
                    userName = userName,
                    greetingProgress = greetingProgress,
                    ringsProgress = ringsProgress,
                    sessionProgress = sessionProgress,
                    nutritionProgress = nutritionProgress,
                    hasPrograms = programs.isNotEmpty(),
                    muscularProgress = muscularProgress,
                    sncProgress = sncProgress,
                    columnaProgress = columnaProgress,
                    todaySessions = todaySessions,
                    dailyCalorieGoal = dailyCalorieGoal,
                    onThemeToggle = { /* TODO */ },
                    onSettingsClick = { /* TODO */ },
                    onStartWorkout = { session, program -> /* TODO: navegar a sesión */ },
                    onCreateProgram = { /* TODO: navegar a creador */ },
                    onAddMeal = { /* TODO: navegar a registro de comidas */ },
                )
            },
        ) { innerPadding ->
            HomeWithProgram(
                viewModel = viewModel,
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                ringsViewMode = ringsViewMode,
                todaySessions = todaySessions,
                hasActiveProgram = programs.isNotEmpty(),
                listState = listState,
                userName = userName,
                greeting = greeting,
                onRingSelect = { viewModel.selectRing(it) },
                onRingsViewChange = { viewModel.setRingsViewMode(it) },
                onThemeToggle = { /* TODO */ },
                onSettingsClick = { /* TODO */ },
                onStartWorkout = { _, _ -> /* TODO */ },
                onResumeWorkout = { /* TODO */ },
                onNavigateToCard = { /* TODO */ },
                onNavigate = { /* TODO */ },
                modifier = Modifier.padding(innerPadding),
            )
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

// ─── Home With Program ───────────────────────────────────────────────────────

@Composable
private fun HomeWithProgram(
    viewModel: HomeViewModel,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    ringsViewMode: HomeViewModel.RingsViewMode,
    todaySessions: List<com.example.kpkn.data.models.TodaySessionItem>,
    hasActiveProgram: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
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

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Item 0 — Saludo (se corta por el header al hacer scroll)
        item {
            HomeHeaderSection(
                greeting = greeting,
                userName = userName,
                ringsViewMode = ringsViewMode,
                onThemeToggle = onThemeToggle,
                onSettingsClick = onSettingsClick,
                onRingsViewChange = onRingsViewChange,
            )
        }

        // Item 1 — Rings (se corta después del saludo)
        item {
            HomeRingsSection(
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                ringsViewMode = ringsViewMode,
                hasActiveProgram = hasActiveProgram,
                onRingSelect = onRingSelect,
            )
        }

        item {
            HomeSessionSection(
                sessions = todaySessions,
                hasActiveProgram = hasActiveProgram,
                currentDayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK),
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
            )
        }

        item {
            val dailyCalorieGoal by viewModel.dailyCalorieGoal.collectAsState()
            HomeNutritionProgressSection(
                dailyCalorieGoal = dailyCalorieGoal,
                consumedCalories = 0, // Será actualizado cuando se implemente nutrición
                onAddMeal = { /* TODO: navegar a registro de comidas */ },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            HomeCardsSection(
                viewModel = viewModel,
                onNavigateToCard = onNavigateToCard,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            HomeProgramsSection(
                programs = programs,
                onProgramClick = { },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            HomeCornersSection(onNavigate = onNavigate)
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Top Bar permanente ──────────────────────────────────────────────────────
// Siempre visible. Entre el logo y los botones hay una zona dinámica:
// conforme el saludo se "corta" arriba, el mini saludo sube desde abajo.
// Conforme los rings se "cortan", los mini rings suben y el saludo baja.

@Composable
private fun HomeTopBar(
    greeting: String,
    userName: String,
    greetingProgress: Float,
    ringsProgress: Float,
    sessionProgress: Float,
    nutritionProgress: Float,
    hasPrograms: Boolean,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    todaySessions: List<com.example.kpkn.data.models.TodaySessionItem>,
    dailyCalorieGoal: Int,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onStartWorkout: (com.example.kpkn.data.models.Session, com.example.kpkn.data.models.Program) -> Unit,
    onCreateProgram: () -> Unit,
    onAddMeal: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()       // respeta la status bar del sistema
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Izquierda: logo + zona dinámica animada
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                // KPKN Logo
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.kpknicon),
                    contentDescription = "KPKN",
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )

                // Zona dinámica — clip para que el slide no se desborde
                // Altura generosa para que el slide desde abajo sea visible
                val boxHeightDp = 44.dp
                val topBarDensity = LocalDensity.current
                val boxHeightPx = with(topBarDensity) { boxHeightDp.toPx() }

                // CUATRO FASES sin mezcla:
                // Fase 1 — item 0 cortándose: saludo sube desde abajo (0→1)
                // Fase 2 — item 1 cortándose: saludo sale por arriba (1→0)
                //                              rings suben desde abajo (0→1)
                // Fase 3 — item 2 cortándose: rings salen por arriba (1→0)
                //                              sesión sube desde abajo (0→1)
                // Fase 4 — item 3 cortándose: sesión sale por arriba (1→0)
                //                              nutrición sube desde abajo (0→1)
                // El crossfade es estrictamente 1:1 con el scroll en cada fase.
                val greetingAlpha: Float
                val greetingSlide: Float
                val ringsAlpha: Float
                val ringsSlide: Float
                val sessionAlpha: Float
                val sessionSlide: Float
                if (nutritionProgress > 0f) {
                    // Fase 4: sesión sale hacia arriba, nutrición sube
                    sessionAlpha = 1f - nutritionProgress
                    sessionSlide = -nutritionProgress * boxHeightPx
                    ringsAlpha = 0f
                    ringsSlide = 0f
                    greetingAlpha = 0f
                    greetingSlide = 0f
                } else if (sessionProgress > 0f) {
                    // Fase 3: rings salen hacia arriba, sesión sube
                    sessionAlpha = sessionProgress
                    sessionSlide = (1f - sessionProgress) * boxHeightPx
                    ringsAlpha = 1f - sessionProgress
                    ringsSlide = -sessionProgress * boxHeightPx
                    greetingAlpha = 0f
                    greetingSlide = 0f
                } else if (ringsProgress > 0f) {
                    // Fase 2: saludo sale hacia arriba mientras rings suben
                    greetingAlpha = 1f - ringsProgress
                    greetingSlide = -ringsProgress * boxHeightPx
                    ringsAlpha = ringsProgress
                    ringsSlide = (1f - ringsProgress) * boxHeightPx
                    sessionAlpha = 0f
                    sessionSlide = 0f
                } else {
                    // Fase 1: saludo entra desde abajo
                    greetingAlpha = greetingProgress
                    greetingSlide = (1f - greetingProgress) * boxHeightPx
                    ringsAlpha = 0f
                    ringsSlide = 0f
                    sessionAlpha = 0f
                    sessionSlide = 0f
                }

                Box(
                    modifier = Modifier
                        .height(boxHeightDp)
                        .weight(1f)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Saludo mini
                    Text(
                        "$greeting $userName!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = greetingAlpha
                            translationY = greetingSlide
                        },
                    )

                    // Mini rings — entran desde abajo 1:1 con el corte de item 1
                    MiniRingsWidget(
                        muscularProgress = muscularProgress,
                        sncProgress = sncProgress,
                        columnaProgress = columnaProgress,
                        hasActiveProgram = hasPrograms,
                        modifier = Modifier.graphicsLayer {
                            alpha = ringsAlpha
                            translationY = ringsSlide
                        },
                    )

                    // Mini session card — entra desde abajo 1:1 con el corte de item 2
                    MiniSessionCard(
                        hasPrograms = hasPrograms,
                        todaySessions = todaySessions,
                        onStartWorkout = onStartWorkout,
                        onCreateProgram = onCreateProgram,
                        modifier = Modifier.graphicsLayer {
                            alpha = sessionAlpha
                            translationY = sessionSlide
                        },
                    )

                    // Mini nutrition card — entra desde abajo 1:1 con el corte de item 3
                    MiniNutritionCard(
                        dailyCalorieGoal = dailyCalorieGoal,
                        consumedCalories = 0, // Será actualizado cuando se implemente nutrición
                        onAddMeal = onAddMeal,
                        modifier = Modifier.graphicsLayer {
                            alpha = nutritionProgress
                            translationY = (1f - nutritionProgress) * boxHeightPx
                        },
                    )
                }
            }

            // Derecha: botones (siempre visibles)
            Row {
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        painterResource(R.drawable.ic_sun),
                        contentDescription = "Tema",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notificaciones",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        modifier = Modifier.size(22.dp),
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

// ─── Mini Rings Widget ───────────────────────────────────────────────────────

@Composable
private fun MiniRingsWidget(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    hasActiveProgram: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ringColors = if (hasActiveProgram)
        listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
    else
        listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
    val progressValues = listOf(muscularProgress, sncProgress, columnaProgress)

    // Llena el ancho disponible (weight(1f) del padre) con altura fija
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(38.dp)
    ) {
        // Radio basado en la altura disponible para que los rings sean grandes
        val r = size.height * 0.38f
        val strokeW = r * 0.28f
        // Espaciado entre centros: diámetro + pequeño gap para que se vean entrelazados
        val gap = r * 0.35f
        val diameter = r * 2f
        val spacing = diameter - gap  // se solapan un poco como en la vista grande

        // Posicionamos los 3 rings centrados horizontalmente
        val totalWidth = diameter + spacing * 2
        val startX = (size.width - totalWidth) / 2f + r
        val cy = size.height / 2f

        val centers = listOf(
            Offset(startX, cy),
            Offset(startX + spacing, cy),
            Offset(startX + spacing * 2, cy),
        )

        for (i in centers.indices) {
            val c = centers[i]
            val color = ringColors[i]
            val progress = progressValues[i]

            drawCircle(
                color.copy(alpha = 0.15f),
                r,
                c,
                style = Stroke(strokeW),
            )
            drawArc(
                color,
                -90f,
                360f * progress,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2f, r * 2f),
                style = Stroke(strokeW),
            )
        }
    }
}

// ─── Mini Session Card ───────────────────────────────────────────────────

@Composable
private fun MiniSessionCard(
    hasPrograms: Boolean,
    todaySessions: List<com.example.kpkn.data.models.TodaySessionItem>,
    onStartWorkout: (com.example.kpkn.data.models.Session, com.example.kpkn.data.models.Program) -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when {
            !hasPrograms -> {
                // No program: create program button
                Button(
                    onClick = onCreateProgram,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "Crear programa",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            todaySessions.isEmpty() -> {
                // Has program but no session today: rest day
                Text(
                    "Día de descanso",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            else -> {
                // Has session: show name + play icon
                val session = todaySessions.firstOrNull() ?: return
                Text(
                    session.session.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onStartWorkout(session.session, session.program) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Iniciar sesión",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ─── Mini Nutrition Card ────────────────────────────────────────────────

@Composable
private fun MiniNutritionCard(
    dailyCalorieGoal: Int,
    consumedCalories: Int,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Calorías",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    consumedCalories.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "/ $dailyCalorieGoal",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        IconButton(
            onClick = onAddMeal,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Agregar comida",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
