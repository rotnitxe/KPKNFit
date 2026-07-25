package com.example.kpkn.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.calculations.getCurrentDayOfWeek
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.RingBlue
import com.example.kpkn.ui.theme.RingRed
import com.example.kpkn.ui.theme.RingYellow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate

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
    viewModel: HomeViewModel = rememberHomeViewModel(),
    @Suppress("UNUSED_PARAMETER") nutritionViewModel: NutritionViewModel? = null,
) {
    // Theme toggle lives in Settings; params kept for API compatibility with MainActivity.
    @Suppress("UNUSED_VARIABLE")
    val unusedTheme = themeMode to onThemeChange

    val augeViewModel = rememberAugeViewModel()
    val augePerMuscle by augeViewModel.perMuscle.collectAsState()
    val augeSnapshot by augeViewModel.snapshot.collectAsState()

    val muscularProgress = augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f
    val sncProgress = augeSnapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f
    val columnaProgress = augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f
    val augeLoading = augeSnapshot.isLoading
    val pendingQuestionnaire by augeViewModel.pendingQuestionnaire.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val nutritionRepo = remember { NutritionRepository.getInstance() }
    var showFoodLogger by remember { mutableStateOf(false) }
    var selectedMealForLogger by remember {
        mutableStateOf(
            when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                in 5..10 -> MealType.BREAKFAST
                in 11..15 -> MealType.LUNCH
                in 16..20 -> MealType.DINNER
                else -> MealType.SNACK
            }
        )
    }
    val context = LocalContext.current
    LaunchedEffect(uiState.activeProgramId) {
        viewModel.loadFeedbacks(context)
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val hazeState = remember { HazeState() }
    val hazeStyle = remember {
        HazeStyle(
            blurRadius = 8.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.35f)),
            backgroundColor = Color.Black.copy(alpha = 0.55f),
            noiseFactor = 0f,
        )
    }

    // Stable indices after reorder: 0 header, 1 session, 2 rings, >=3 nutrition+
    val item0TopMarginPx = with(density) { 16.dp.roundToPx() }
    val item1TopMarginPx = with(density) { 4.dp.roundToPx() }
    val item2TopMarginPx = with(density) { 4.dp.roundToPx() }

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

    val sessionProgress by remember(item1TopMarginPx) {
        derivedStateOf {
            val item1 = listState.layoutInfo.visibleItemsInfo.find { it.index == 1 }
            when {
                item1 == null -> 1f
                item1.offset > -item1TopMarginPx -> 0f
                else -> {
                    val cut = (-item1.offset - item1TopMarginPx).toFloat()
                    val visibleHeight = (item1.size - item1TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight * 2.0f).coerceIn(0f, 1f)
                }
            }
        }
    }

    val ringsProgress by remember(item2TopMarginPx) {
        derivedStateOf {
            val item2 = listState.layoutInfo.visibleItemsInfo.find { it.index == 2 }
            when {
                item2 == null -> 1f
                item2.offset > -item2TopMarginPx -> 0f
                else -> {
                    val cut = (-item2.offset - item2TopMarginPx).toFloat()
                    val visibleHeight = (item2.size - item2TopMarginPx).toFloat().coerceAtLeast(1f)
                    (cut / visibleHeight).coerceIn(0f, 1f)
                }
            }
        }
    }

    val nutritionProgress by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            when {
                first < 3 -> 0f
                else -> 1f
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            HomeWithProgram(
                viewModel = viewModel,
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                augeLoading = augeLoading,
                perMuscle = augePerMuscle,
                todaySessions = uiState.todaySessions,
                competitionCountdown = uiState.competitionCountdown,
                hasActiveProgram = uiState.hasActiveProgram,
                activeProgramId = uiState.activeProgramId,
                programs = uiState.programs,
                listState = listState,
                userName = uiState.userName,
                greeting = uiState.greeting,
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onNavigateToProgram = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
                onNavigateToCard = onNavigateToCard,
                onNavigate = onNavigate,
                autoDeloadMessage = augeSnapshot.autoDeloadMessage,
                overtrainedMuscles = uiState.overtrainedMuscles,
                onAddMeal = { showFoodLogger = true },
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            )
        }

        HomeTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            greeting = uiState.greeting,
            userName = uiState.userName,
            greetingProgress = greetingProgress,
            ringsProgress = ringsProgress,
            sessionProgress = sessionProgress,
            nutritionProgress = nutritionProgress,
            hasPrograms = uiState.hasActiveProgram,
            muscularProgress = muscularProgress,
            sncProgress = sncProgress,
            columnaProgress = columnaProgress,
            primarySession = uiState.primarySession,
            isRestDay = uiState.isRestDay || (uiState.hasActiveProgram && uiState.todaySessions.isEmpty()),
            dailyCalorieGoal = uiState.dailyCalorieGoal,
            consumedCalories = uiState.todayNutritionTotals.calories.toInt(),
            onSettingsClick = onNavigateToSettings,
            onStartWorkout = onStartWorkout,
            onCreateProgram = onCreateProgram,
            onAddMeal = { showFoodLogger = true },
            onNavigateToProfile = onNavigateToProfile,
            hazeState = hazeState,
            glassStyle = hazeStyle,
        )

        if (showFoodLogger) {
            HomeFoodLoggerHost(
                nutritionRepo = nutritionRepo,
                selectedMealForLogger = selectedMealForLogger,
                onDismiss = { showFoodLogger = false },
            )
        }

        pendingQuestionnaire?.let { questionnaire ->
            com.example.kpkn.screens.auge.PostSessionSheet(
                questionnaire = questionnaire,
                onDismiss = { augeViewModel.dismissPendingQuestionnaire() },
                onSave = { feedback ->
                    augeViewModel.savePostSessionFeedback(feedback)
                    viewModel.loadFeedbacks(context)
                },
            )
        }
    }
}

@Composable
private fun HomeFoodLoggerHost(
    nutritionRepo: NutritionRepository,
    selectedMealForLogger: MealType,
    onDismiss: () -> Unit,
) {
    val foodDatabase by nutritionRepo.foodDatabase.collectAsState()
    FoodLoggerDrawer(
        nutritionRepo = nutritionRepo,
        isOpen = true,
        onDismiss = onDismiss,
        onSave = { log ->
            nutritionRepo.addNutritionLog(log)
            onDismiss()
        },
        foodDatabase = foodDatabase,
        initialDate = LocalDate.now().toString(),
        initialMealType = selectedMealForLogger,
        initialDescription = null,
        initialTab = 0,
    )
}

@Composable
private fun HomeWithProgram(
    viewModel: HomeViewModel,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    augeLoading: Boolean = false,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    todaySessions: List<TodaySessionItem>,
    competitionCountdown: CompetitionCountdown?,
    hasActiveProgram: Boolean,
    activeProgramId: String?,
    programs: List<Program>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    userName: String,
    greeting: String,
    onStartWorkout: (Session, Program) -> Unit,
    onResumeWorkout: () -> Unit,
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onNavigateToCard: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoDeloadMessage: String? = null,
    overtrainedMuscles: List<String> = emptyList(),
    onAddMeal: () -> Unit = {},
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(100.dp))
            HomeHeaderSection(greeting = greeting, userName = userName)
        }
        item(key = "session") {
            HomeSessionSection(
                sessions = todaySessions,
                hasActiveProgram = hasActiveProgram,
                currentDayOfWeek = getCurrentDayOfWeek(),
                perMuscle = perMuscle,
                onStartWorkout = onStartWorkout,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onCreateProgram = onCreateProgram,
            )
        }
        item(key = "rings") {
            HomeRingsSection(
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                hasActiveProgram = hasActiveProgram,
                isLoading = augeLoading,
            )
        }
        if (!autoDeloadMessage.isNullOrBlank()) {
            item(key = "auto-deload") {
                AlertActionCard(
                    title = "Auto-deload sugerido",
                    body = autoDeloadMessage,
                    actionLabel = "Ver AUGE",
                    onAction = { onNavigate("settings/auge") },
                    emphasize = false,
                )
            }
        }
        if (overtrainedMuscles.isNotEmpty()) {
            item(key = "overtraining") {
                AlertActionCard(
                    title = "Sobreentrenamiento crónico detectado",
                    body = "Fatiga crítica acumulada en: ${overtrainedMuscles.joinToString(", ")}. Considera reducir series semanales o tomar un descanso activo.",
                    actionLabel = "Ver recomendaciones",
                    onAction = { onNavigate("settings/auge") },
                    emphasize = true,
                    leadingIcon = true,
                )
            }
        }
        competitionCountdown?.let { countdown ->
            item(key = "competition") {
                CompetitionCountdownCard(countdown = countdown, onClick = { onNavigateToProgram(countdown.programId) })
            }
        }
        item(key = "cards") {
            Spacer(Modifier.height(8.dp))
            HomeCardsSection(viewModel = viewModel, onNavigateToCard = onNavigateToCard, onAddMeal = onAddMeal)
        }
        item(key = "programs") {
            Spacer(Modifier.height(8.dp))
            HomeProgramsSection(
                programs = programs,
                activeProgramId = activeProgramId,
                onProgramClick = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
            )
        }
        item(key = "wikilab") {
            Spacer(Modifier.height(16.dp))
            HomeWikiLabSection(onNavigate = onNavigate)
        }
    }
}

@Composable
private fun AlertActionCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    emphasize: Boolean,
    leadingIcon: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (emphasize) 0.65f else 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            )
            TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompetitionCountdownCard(countdown: CompetitionCountdown, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
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
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF59E0B)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdown.countdownLabel.substringBefore(" "), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.Black, maxLines = 1)
                    Text("COMP", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black.copy(alpha = 0.76f))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Cuenta atrás de competición", fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(countdown.programName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${countdown.countdownLabel} · ${countdown.competitionDateLabel}", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                countdown.competitionWeekLabel?.let { week ->
                    Text("Semana reservada: $week", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Ver", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFF59E0B))
        }
    }
}

@Composable
private fun HomeTopBar(
    modifier: Modifier = Modifier,
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
    primarySession: TodaySessionItem?,
    isRestDay: Boolean,
    dailyCalorieGoal: Int,
    consumedCalories: Int,
    onSettingsClick: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onCreateProgram: () -> Unit,
    onAddMeal: () -> Unit,
    onNavigateToProfile: () -> Unit,
    hazeState: HazeState,
    glassStyle: HazeStyle,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .hazeEffect(state = hazeState, style = glassStyle),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = Color.Black.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 4.dp),
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
                        modifier = Modifier.size(43.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    )
                    val boxHeightDp = 24.dp
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

                    // Order after F2: greeting → session → rings → nutrition
                    if (nutritionProgress > 0f) {
                        nutritionAlpha = nutritionProgress
                        nutritionSlide = (1f - nutritionProgress) * boxHeightPx
                        ringsAlpha = 1f - nutritionProgress
                        ringsSlide = -nutritionProgress * boxHeightPx
                        sessionAlpha = 0f
                        sessionSlide = 0f
                        greetingAlpha = 0f
                        greetingSlide = 0f
                    } else if (ringsProgress > 0f) {
                        ringsAlpha = ringsProgress
                        ringsSlide = (1f - ringsProgress) * boxHeightPx
                        sessionAlpha = 1f - ringsProgress
                        sessionSlide = -ringsProgress * boxHeightPx
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                        greetingAlpha = 0f
                        greetingSlide = 0f
                    } else if (sessionProgress > 0f) {
                        sessionAlpha = sessionProgress
                        sessionSlide = (1f - sessionProgress) * boxHeightPx
                        greetingAlpha = 1f - sessionProgress
                        greetingSlide = -sessionProgress * boxHeightPx
                        ringsAlpha = 0f
                        ringsSlide = 0f
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                    } else {
                        greetingAlpha = 1f - greetingProgress
                        greetingSlide = greetingProgress * boxHeightPx
                        sessionAlpha = greetingProgress
                        sessionSlide = (greetingProgress - 1f) * boxHeightPx
                        ringsAlpha = 0f
                        ringsSlide = 0f
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                    }

                    Box(modifier = Modifier.height(48.dp).weight(1f), contentAlignment = Alignment.TopStart) {
                        Text(
                            "$greeting, $userName!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = greetingAlpha; translationY = greetingSlide },
                        )
                        MiniSessionCard(
                            hasPrograms = hasPrograms,
                            primarySession = primarySession,
                            isRestDay = isRestDay,
                            onStartWorkout = onStartWorkout,
                            onCreateProgram = onCreateProgram,
                            modifier = Modifier.graphicsLayer { alpha = sessionAlpha; translationY = sessionSlide },
                        )
                        MiniRingsWidget(
                            muscularProgress = muscularProgress,
                            sncProgress = sncProgress,
                            columnaProgress = columnaProgress,
                            hasActiveProgram = hasPrograms,
                            modifier = Modifier.graphicsLayer { alpha = ringsAlpha; translationY = ringsSlide },
                        )
                        MiniNutritionCard(
                            dailyCalorieGoal = dailyCalorieGoal,
                            consumedCalories = consumedCalories,
                            onAddMeal = onAddMeal,
                            modifier = Modifier.graphicsLayer { alpha = nutritionAlpha; translationY = nutritionSlide },
                        )
                    }
                }
                Row {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Perfil", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
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
    val ringColors = if (hasActiveProgram) {
        listOf(RingRed, RingBlue, RingYellow)
    } else {
        listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
    }
    val progressValues = listOf(muscularProgress, sncProgress, columnaProgress)
    Canvas(modifier = modifier.fillMaxWidth().height(38.dp)) {
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
            drawCircle(color.copy(alpha = 0.15f), r, c, style = Stroke(strokeW))
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
    primarySession: TodaySessionItem?,
    isRestDay: Boolean,
    onStartWorkout: (Session, Program) -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!hasPrograms) {
            Button(
                onClick = onCreateProgram,
                modifier = Modifier.weight(1f).height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Crear programa", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        } else if (isRestDay || primarySession == null) {
            Text(
                "Día de descanso",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
            )
        } else {
            Text(
                primarySession.session.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onStartWorkout(primarySession.session, primarySession.program) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, "Iniciar", modifier = Modifier.size(18.dp))
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
    } else {
        0f
    }
    val progressColor = when {
        pct < 0.9f -> MaterialTheme.colorScheme.tertiary
        pct <= 1.1f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
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
                Text(consumedCalories.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, fontSize = 15.sp)
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
                modifier = Modifier.padding(top = 2.dp, end = 8.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
        IconButton(onClick = onAddMeal, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, "Agregar", modifier = Modifier.size(18.dp))
        }
    }
}
