package com.example.kpkn.screens.home

import androidx.compose.foundation.Image
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.R
import com.example.kpkn.data.models.MuscleFeedbackEntry
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.calculations.getCurrentDayOfWeek
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.ui.theme.AppThemeMode
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

// ─── Home Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToProgram: (String) -> Unit = {},
    onCreateProgram: () -> Unit = {},
    onStartWorkout: (Session, Program) -> Unit = { _, _ -> },
    onResumeWorkout: () -> Unit = {},
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onNavigateToCard: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    augeViewModel: AugeViewModel = viewModel(),
    nutritionViewModel: NutritionViewModel? = null,
) {
    // AUGE batteries (0-100) → ring progress (0-1)
    val augePerMuscle by augeViewModel.perMuscle.collectAsState()
    val augePending by augeViewModel.pendingQuestionnaire.collectAsState()
    val augeDashboard by augeViewModel.dashboard.collectAsState()
    val augeSnapshot by augeViewModel.snapshot.collectAsState()

    // Rings are rendered straight from AUGE so they react immediately to new logs.
    val muscularProgress = augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f
    val sncProgress = augeSnapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f
    val columnaProgress = augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f
    val userName by viewModel.userName.collectAsState()
    val todaySessions by viewModel.todaySessions.collectAsState()
    val activeProgramId by viewModel.activeProgramId.collectAsState()
    val hasActiveProgram by viewModel.hasActiveProgram.collectAsState()
    val competitionCountdown by viewModel.competitionCountdown.collectAsState()
    val dailyCalorieGoal by viewModel.dailyCalorieGoal.collectAsState()
    val todayNutritionTotals by viewModel.todayNutritionTotals.collectAsState()
    val greeting = viewModel.getGreeting()
    val nutritionRepo = remember { NutritionRepository.getInstance() }
    val foodDatabase by nutritionRepo.foodDatabase.collectAsState()
    var showFoodLogger by remember { mutableStateOf(false) }
    var selectedMealForLogger by remember { mutableStateOf(MealType.LUNCH) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val item0TopMarginPx = with(density) { 16.dp.roundToPx() }
    val item1TopMarginPx = with(density) { 4.dp.roundToPx() }
    val item2TopMarginPx = with(density) { 4.dp.roundToPx() }
    val item3TopMarginPx = with(density) { 8.dp.roundToPx() }

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

    val sessionProgress by remember(item2TopMarginPx) {
        derivedStateOf {
            val item2 = listState.layoutInfo.visibleItemsInfo.find { it.index == 2 }
            when {
                item2 == null -> 1f
                item2.offset > -item2TopMarginPx -> 0f
                else -> {
                    val cut = (-item2.offset - item2TopMarginPx).toFloat()
                    val visibleHeight = (item2.size - item2TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight * 2.0f).coerceIn(0f, 1f)
                }
            }
        }
    }

    val nutritionProgress by remember(item3TopMarginPx) {
        derivedStateOf {
            val item3 = listState.layoutInfo.visibleItemsInfo.find { it.index == 3 }
            when {
                item3 == null -> 1f
                item3.offset > -item3TopMarginPx -> 0f
                else -> {
                    val cut = (-item3.offset - item3TopMarginPx).toFloat()
                    val visibleHeight = (item3.size - item3TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight * 3.0f).coerceIn(0f, 1f)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                HomeTopBar(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    greeting = greeting,
                    userName = userName,
                    greetingProgress = greetingProgress,
                    ringsProgress = ringsProgress,
                    sessionProgress = sessionProgress,
                    nutritionProgress = nutritionProgress,
                    hasPrograms = hasActiveProgram,
                    muscularProgress = muscularProgress,
                    sncProgress = sncProgress,
                    columnaProgress = columnaProgress,
                    todaySessions = todaySessions,
                    dailyCalorieGoal = dailyCalorieGoal,
                    consumedCalories = todayNutritionTotals.calories.toInt(),
                    onSettingsClick = onNavigateToSettings,
                    onStartWorkout = onStartWorkout,
                    onCreateProgram = onCreateProgram,
                    onAddMeal = { showFoodLogger = true },
                    onNavigateToProfile = onNavigateToProfile,
                )
            },
        ) { innerPadding ->
            HomeWithProgram(
                viewModel = viewModel,
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                perMuscle = augePerMuscle,
                todaySessions = todaySessions,
                competitionCountdown = competitionCountdown,
                hasActiveProgram = hasActiveProgram,
                activeProgramId = activeProgramId,
                listState = listState,
                userName = userName,
                greeting = greeting,
                onSettingsClick = onNavigateToSettings,
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onNavigateToProgram = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
                onNavigateToCard = onNavigateToCard,
                onNavigate = onNavigate,
                autoDeloadMessage = augeSnapshot.autoDeloadMessage,
                onAddMeal = { showFoodLogger = true },
                modifier = Modifier.padding(innerPadding),
            )
        }

        var showPostSessionSheet by remember { mutableStateOf(false) }
        LaunchedEffect(augePending?.logId) {
            if (augePending != null) {
                showPostSessionSheet = true
            }
        }
        if (showPostSessionSheet && augePending != null) {
            PostSessionFeedbackSheet(
                sessionName = augePending!!.sessionName,
                muscleGroups = augePending!!.muscleGroups,
                logId = augePending!!.logId,
                onSave = { fb ->
                    augeViewModel.savePostSessionFeedback(fb)
                    showPostSessionSheet = false
                },
                onDismiss = {
                    augeViewModel.dismissPendingQuestionnaire()
                    showPostSessionSheet = false
                },
            )
        }

        FoodLoggerDrawer(
            nutritionRepo = nutritionRepo,
            isOpen = showFoodLogger,
            onDismiss = {
                showFoodLogger = false
            },
            onSave = { log ->
                nutritionRepo.addNutritionLog(log)
                showFoodLogger = false
            },
            foodDatabase = foodDatabase,
            initialDate = LocalDate.now().toString(),
            initialMealType = selectedMealForLogger,
            initialDescription = null,
            initialTab = 0,
        )

    }
}

@Composable
private fun HomeWithProgram(
    viewModel: HomeViewModel,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    todaySessions: List<TodaySessionItem>,
    competitionCountdown: CompetitionCountdown?,
    hasActiveProgram: Boolean,
    activeProgramId: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    userName: String,
    greeting: String,
    onSettingsClick: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onResumeWorkout: () -> Unit,
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onNavigateToCard: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    autoDeloadMessage: String? = null,
    onAddMeal: () -> Unit = {},
) {
    val programs by viewModel.programs.collectAsState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(listModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HomeHeaderSection(
                greeting = greeting,
                userName = userName,
            )
        }

        item {
            HomeRingsSection(
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                hasActiveProgram = hasActiveProgram,
            )
        }

        if (!autoDeloadMessage.isNullOrBlank()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Auto-deload sugerido",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            autoDeloadMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        competitionCountdown?.let { countdown ->
            item {
                CompetitionCountdownCard(
                    countdown = countdown,
                    onClick = { onNavigateToProgram(countdown.programId) },
                )
            }
        }

        item {
            HomeSessionSection(
                sessions = todaySessions,
                hasActiveProgram = hasActiveProgram,
                currentDayOfWeek = getCurrentDayOfWeek(),
                perMuscle = perMuscle,
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            HomeCardsSection(
                viewModel = viewModel,
                onNavigateToCard = onNavigateToCard,
                onAddMeal = onAddMeal,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            HomeProgramsSection(
                programs = programs,
                activeProgramId = activeProgramId,
                onProgramClick = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            HomeWikiLabSection(onNavigate = onNavigate)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CompetitionCountdownCard(
    countdown: CompetitionCountdown,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.14f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF59E0B)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        countdown.countdownLabel.substringBefore(" "),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        maxLines = 1,
                    )
                    Text("COMP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black.copy(alpha = 0.76f))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Cuenta atrás de competición", fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(countdown.programName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${countdown.countdownLabel} · ${countdown.competitionDateLabel}",
                    fontSize = 11.sp,
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                )
                countdown.competitionWeekLabel?.let { week ->
                    Text(
                        "Semana reservada: $week",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("Ver", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFF59E0B))
        }
    }
}

@Composable
private fun HomeTopBar(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
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
    todaySessions: List<TodaySessionItem>,
    dailyCalorieGoal: Int,
    consumedCalories: Int,
    onSettingsClick: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onCreateProgram: () -> Unit,
    onAddMeal: () -> Unit,
    onNavigateToProfile: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.56f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    painter = painterResource(R.drawable.kpknicon),
                    contentDescription = "KPKN",
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )

                val boxHeightDp = 32.dp
                val topBarDensity = LocalDensity.current
                val boxHeightPx = with(topBarDensity) { boxHeightDp.toPx() }

                val greetingAlpha: Float
                val greetingSlide: Float
                val ringsAlpha: Float
                val ringsSlide: Float
                val sessionAlpha: Float
                val sessionSlide: Float
                val nutritionAlpha: Float
                val nutritionSlide: Float
                if (nutritionProgress > 0f) {
                    nutritionAlpha = nutritionProgress
                    nutritionSlide = (1f - nutritionProgress) * boxHeightPx
                    sessionAlpha = 1f - nutritionProgress
                    sessionSlide = -nutritionProgress * boxHeightPx
                    ringsAlpha = 0f
                    ringsSlide = 0f
                    greetingAlpha = 0f
                    greetingSlide = 0f
                } else if (sessionProgress > 0f) {
                    sessionAlpha = sessionProgress
                    sessionSlide = (1f - sessionProgress) * boxHeightPx
                    ringsAlpha = 1f - sessionProgress
                    ringsSlide = -sessionProgress * boxHeightPx
                    nutritionAlpha = 0f
                    nutritionSlide = 0f
                    greetingAlpha = 0f
                    greetingSlide = 0f
                } else if (ringsProgress > 0f) {
                    greetingAlpha = 1f - ringsProgress
                    greetingSlide = -ringsProgress * boxHeightPx
                    ringsAlpha = ringsProgress
                    ringsSlide = (1f - ringsProgress) * boxHeightPx
                    sessionAlpha = 0f
                    sessionSlide = 0f
                    nutritionAlpha = 0f
                    nutritionSlide = 0f
                } else {
                    greetingAlpha = greetingProgress
                    greetingSlide = (1f - greetingProgress) * boxHeightPx
                    ringsAlpha = 0f
                    ringsSlide = 0f
                    sessionAlpha = 0f
                    sessionSlide = 0f
                    nutritionAlpha = 0f
                    nutritionSlide = 0f
                }

                Box(
                    modifier = Modifier
                        .height(boxHeightDp)
                        .weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "$greeting, $userName!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = greetingAlpha
                            translationY = greetingSlide
                        },
                    )

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

                    MiniNutritionCard(
                        dailyCalorieGoal = dailyCalorieGoal,
                        consumedCalories = consumedCalories,
                        onAddMeal = onAddMeal,
                        modifier = Modifier.graphicsLayer {
                            alpha = nutritionAlpha
                            translationY = nutritionSlide
                        },
                    )
                }
            }

            Row {
                IconButton(onClick = onNavigateToProfile) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Perfil del atleta",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRingsWidget(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    modifier: Modifier = Modifier,
    hasActiveProgram: Boolean = true,
) {
    val ringColors = if (hasActiveProgram)
        listOf(Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFFFFD740))
    else
        listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
    val progressValues = listOf(muscularProgress, sncProgress, columnaProgress)

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(38.dp)
    ) {
        val r = size.height * 0.38f
        val strokeW = r * 0.28f
        val gap = r * 0.35f
        val diameter = r * 2f
        val spacing = diameter - gap

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

@Composable
private fun MiniSessionCard(
    hasPrograms: Boolean,
    todaySessions: List<TodaySessionItem>,
    onStartWorkout: (Session, Program) -> Unit,
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
                Button(
                    onClick = onCreateProgram,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = RoundedCornerShape(8.dp),
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

@Composable
private fun MiniNutritionCard(
    dailyCalorieGoal: Int,
    consumedCalories: Int,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = if (dailyCalorieGoal > 0) {
        (consumedCalories.toFloat() / dailyCalorieGoal.toFloat()).coerceIn(0f, 1.5f)
    } else 0f
    val progressColor = when {
        pct < 0.9f -> MaterialTheme.colorScheme.tertiary
        pct <= 1.1f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

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
            LinearProgressIndicator(
                progress = { pct.coerceAtMost(1f) },
                modifier = Modifier
                    .padding(top = 2.dp, end = 8.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
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

// ─── Post-Session Feedback Sheet ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostSessionFeedbackSheet(
    sessionName: String,
    muscleGroups: List<String>,
    logId: String,
    onSave: (PostSessionFeedback) -> Unit,
    onDismiss: () -> Unit,
) {
    var cnsRecovery by remember { mutableIntStateOf(7) }
    val muscleDoms = remember(muscleGroups) {
        mutableStateMapOf<String, Int>().also { m -> muscleGroups.forEach { m[it] = 1 } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "¿Cómo quedaste tras «$sessionName»?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Feedback post-sesión — ayuda a AUGE a calibrar tu recuperación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recuperación del sistema (1 = agotado · 10 = fresco)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = cnsRecovery.toFloat(),
                    onValueChange = { cnsRecovery = it.toInt().coerceIn(1, 10) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                Text("${cnsRecovery}/10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            if (muscleGroups.isNotEmpty()) {
                Text("Agujetas por músculo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                muscleGroups.take(6).forEach { muscle ->
                    val doms = muscleDoms[muscle] ?: 1
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(muscle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { level ->
                                val active = level <= doms
                                val color = when (level) {
                                    1, 2 -> Color(0xFF22C55E)
                                    3 -> Color(0xFFFACC15)
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (active) color else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { muscleDoms[muscle] = level },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        PostSessionFeedback(
                            logId = logId,
                            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            cnsRecovery = cnsRecovery,
                            muscleFeedback = muscleDoms.mapValues { (_, v) -> MuscleFeedbackEntry(doms = v) },
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Guardar feedback", fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
