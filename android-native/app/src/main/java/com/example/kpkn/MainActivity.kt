package com.example.kpkn

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.navigation.DeepLinkRouter
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.navigation.addHealthConnectRoute
import com.example.kpkn.navigation.NavigationBus
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.nutrition.BodyProgressScreen
import com.example.kpkn.screens.nutrition.MealHistoryScreen
import com.example.kpkn.screens.nutrition.NutritionScreen
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.screens.profile.ProfileScreen
import com.example.kpkn.screens.programdetail.ProgramDetailScreen
import com.example.kpkn.screens.programeditor.ProgramEditorScreen
import com.example.kpkn.screens.programs.ProgramsScreen
import com.example.kpkn.screens.programs.ProgramsViewModel
import com.example.kpkn.screens.sessioneditor.SessionEditorScreen
import com.example.kpkn.screens.settings.SettingsAugeScreen
import com.example.kpkn.screens.settings.SettingsDataScreen
import com.example.kpkn.screens.settings.SettingsGeneralScreen
import com.example.kpkn.screens.settings.SettingsNotificationsScreen
import com.example.kpkn.screens.settings.SettingsNutritionScreen
import com.example.kpkn.screens.settings.SettingsProfileScreen
import com.example.kpkn.screens.settings.SettingsScreen
import com.example.kpkn.screens.settings.SettingsTrainingScreen
import com.example.kpkn.screens.wikilab.*
import com.example.kpkn.screens.workout.WorkoutScreen

import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.telemetry.TelemetryHelper
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.RingsTabIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.screens.myrings.MyRingsScreen
import com.example.kpkn.ui.locale.LocaleManager
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)
    private val pendingSharedNutritionText = mutableStateOf<String?>(null)
    private lateinit var telemetryHelper: TelemetryHelper

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize telemetry
        telemetryHelper = TelemetryHelper(this)
        telemetryHelper.logAppOpen()

        pendingDeepLinkRoute.value = resolveNavigationRouteFromIntent(intent)
        pendingSharedNutritionText.value = extractSharedNutritionText(intent)

        runCatching {
            com.example.kpkn.data.exercises.initializeExerciseDatabase(this)
        }.onFailure { it.printStackTrace() }

        // Sync Room appLanguage → SharedPreferences on first load (retrocompat)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val savedLang = com.example.kpkn.data.repository.ProgramRepository
                    .init(this@MainActivity).settings.value.appLanguage
                LocaleManager.persist(this@MainActivity, savedLang)
            }.onFailure { it.printStackTrace() }
        }

        // API ≤ 32: observe locale change events emitted by SettingsViewModel
        lifecycleScope.launch {
            LocaleManager.recreateEvent.collect { recreate() }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                com.example.kpkn.data.exercises.loadCustomExercisesAsync(this@MainActivity)
            }.onFailure { it.printStackTrace() }
        }

        runCatching {
            WorkoutRestAlertManager(this).ensureChannels()
        }.onFailure { it.printStackTrace() }

        // Setup nutrition notification channels + reminders based on settings
        runCatching {
            val nutritionNotifManager = com.example.kpkn.services.nutrition.NutritionNotificationManager(this)
            nutritionNotifManager.createChannels()
            val settings = com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.value
            if (settings.mealReminderEnabled) {
                nutritionNotifManager.scheduleMealReminders(
                    breakfastTime = settings.mealReminderBreakfast,
                    lunchTime = settings.mealReminderLunch,
                    dinnerTime = settings.mealReminderDinner,
                )
                nutritionNotifManager.scheduleDailyMacroCheck()
            }
        }.onFailure { it.printStackTrace() }

        // Setup workout reminder channels + reminders based on settings
        runCatching {
            val workoutReminderManager = com.example.kpkn.services.workout.WorkoutReminderManager(this)
            workoutReminderManager.createChannels()
            val settings = com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.value
            if (settings.workoutReminderEnabled) {
                workoutReminderManager.scheduleWorkoutReminder(settings.workoutReminderTime)
            }
            if (settings.sleepReminderEnabled) {
                workoutReminderManager.scheduleSleepReminder(settings.sleepReminderTime)
            }
        }.onFailure { it.printStackTrace() }

        // Initialize repositories (loads Room data → StateFlows)
        runCatching {
            com.example.kpkn.data.repository.ProgramRepository.init(this)
            com.example.kpkn.data.repository.AugeRepository.getInstance(this)
            com.example.kpkn.data.repository.NutritionRepository.init(this)
            com.example.kpkn.data.repository.CustomExerciseRepository.initialize(this)
            com.example.kpkn.data.repository.LearnRepository.initialize(this)
        }.onFailure { it.printStackTrace() }

        // Initialize WikiLab (muscle/joint/tendon/pattern/chain data)
        runCatching {
            val db = com.example.kpkn.data.db.KpknDatabase.getInstance(this)
            com.example.kpkn.data.repository.WikiLabRepository.initialize(this, db)
        }.onFailure { it.printStackTrace() }

        requestRequiredPermissions()

        setContent {
            var themeMode by remember { mutableStateOf(AppThemeMode.HIGH_CONTRAST) }

            KPKNTheme(themeMode = themeMode) {
                KPKNApp(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    pendingDeepLinkRoute = pendingDeepLinkRoute.value,
                    onDeepLinkHandled = { pendingDeepLinkRoute.value = null },
                    pendingSharedNutritionText = pendingSharedNutritionText.value,
                    onSharedNutritionHandled = { pendingSharedNutritionText.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLinkRoute = resolveNavigationRouteFromIntent(intent)
        pendingDeepLinkRoute.value = deepLinkRoute
        
        if (!deepLinkRoute.isNullOrBlank()) {
            telemetryHelper.logDeepLinkOpen(deepLinkRoute)
        }
        
        val shared = extractSharedNutritionText(intent)
        pendingSharedNutritionText.value = shared
        if (!shared.isNullOrBlank()) {
            NavigationBus.emitSharedNutritionText(shared)
            telemetryHelper.logFoodItemAdd("shared_text", "Shared nutrition text", null)
        }
    }

    override fun onStop() {
        super.onStop()
        telemetryHelper.logAppBackground()
        // Flush any in-flight Room writes to prevent session loss on background kill
        runBlocking {
            runCatching {
                com.example.kpkn.data.repository.ProgramRepository.getInstance().flushPendingWrites()
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun requestRequiredPermissions() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            val canScheduleExact = alarmManager?.canScheduleExactAlarms() == true
            if (!canScheduleExact) {
                logKpknPermissionIssue("SCHEDULE_EXACT_ALARM")
            }
        }
    }
    
    private fun logKpknPermissionIssue(permission: String) {
        runCatching {
            com.example.kpkn.telemetry.KpknTelemetry.getInstance(this).logEvent(
                "permission_issue",
                "permission" to permission,
                "blocker" to false
            )
        }
    }

    private fun extractSharedNutritionText(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND) return null
        val mime = intent.type.orEmpty()
        if (!mime.contains("text", ignoreCase = true)) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun resolveNavigationRouteFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val explicitAction = intent.getStringExtra("kpkn_nutrition_action")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (explicitAction != null) {
            return KpknRoute.NutritionAction.create(explicitAction)
        }

        val dataRoute = DeepLinkRouter.resolve(intent.data)?.route
        if (dataRoute != null) return dataRoute

        val data = intent.data
        if (data != null && data.scheme.equals("kpkn", ignoreCase = true)) {
            val action = data.getQueryParameter("action")
                ?: data.pathSegments.lastOrNull()
            if (!action.isNullOrBlank()) {
                return KpknRoute.NutritionAction.create(action)
            }
        }
        return null
    }
}

// ─── App root with Navigation Compose ───────────────────────────────────────

@Composable
fun KPKNApp(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    pendingDeepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    pendingSharedNutritionText: String? = null,
    onSharedNutritionHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val telemetryHelper = remember { TelemetryHelper(context) }
    val navController = rememberNavController()
    val programsViewModel: ProgramsViewModel = viewModel()
    val nutritionViewModel: NutritionViewModel = viewModel { NutritionViewModel() }
    val activeProgram by programsViewModel.activeProgram.collectAsState()
    val allPrograms by programsViewModel.programs.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val previousRoute = remember { mutableStateOf<String?>(null) }
    
    // Log navigation when route changes
    LaunchedEffect(currentRoute) {
        if (currentRoute != previousRoute.value) {
            telemetryHelper.logNavigation(
                from = previousRoute.value ?: "unknown",
                to = currentRoute ?: "unknown"
            )
            previousRoute.value = currentRoute
        }
    }
    
    val isFullscreenWizard = (currentRoute == KpknRoute.ProgramEditor.route &&
        currentBackStack?.arguments?.getString(KpknRoute.ProgramEditor.ARG_PROGRAM_ID) == "new") ||
        currentRoute == KpknRoute.NutritionWizard.route ||
        currentRoute?.startsWith("session-editor") == true ||
        currentRoute?.startsWith("readiness-gate") == true ||
        currentRoute?.startsWith("workout") == true
    val primaryProgramId = activeProgram?.id ?: allPrograms.firstOrNull()?.id

    val currentTab = when {
        currentRoute?.startsWith(KpknRoute.Training.route) == true  -> KpknRoute.Training.route
        currentRoute?.startsWith("program/") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("program-metric-") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("program-editor") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("session-editor") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("readiness-gate") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("workout") == true -> KpknRoute.Training.route
        currentRoute == KpknRoute.ProgramDetail.route -> KpknRoute.Training.route
        currentRoute == "log-workout" -> KpknRoute.Training.route
        currentRoute?.startsWith(KpknRoute.MyRings.route) == true -> KpknRoute.MyRings.route
        currentRoute?.startsWith(KpknRoute.Nutrition.route) == true -> KpknRoute.Nutrition.route
        currentRoute?.startsWith(KpknRoute.Learn.route) == true -> KpknRoute.WikiLab.route
        currentRoute?.startsWith(KpknRoute.WikiLab.route) == true   -> KpknRoute.WikiLab.route
        else -> KpknRoute.Home.route
    }

    val ongoingWorkout by ProgramRepository.getInstance().ongoingWorkout.collectAsState()

    LaunchedEffect(pendingDeepLinkRoute) {
        val route = pendingDeepLinkRoute ?: return@LaunchedEffect
        if (route != currentRoute) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        onDeepLinkHandled()
    }

    LaunchedEffect(pendingSharedNutritionText) {
        val shared = pendingSharedNutritionText ?: return@LaunchedEffect
        val normalized = shared.trim()
        if (normalized.isBlank()) {
            onSharedNutritionHandled()
            return@LaunchedEffect
        }
        telemetryHelper.logMealLogStart("shared")
        nutritionViewModel.enqueueSharedDescription(normalized, openTab = 0)
        if (currentRoute != KpknRoute.Nutrition.route) {
            navController.navigate(KpknRoute.Nutrition.route) {
                launchSingleTop = true
                restoreState = true
            }
        }
        onSharedNutritionHandled()
    }

    DisposableEffect(Unit) {
        val listener: (String) -> Unit = { text ->
            nutritionViewModel.enqueueSharedDescription(text, openTab = 0)
            val routeNow = navController.currentBackStackEntry?.destination?.route
            if (routeNow != KpknRoute.Nutrition.route) {
                navController.navigate(KpknRoute.Nutrition.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        NavigationBus.registerNutritionShareListener(listener)
        onDispose {
            NavigationBus.unregisterNutritionShareListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFullscreenWizard) {
            KPKNNavGraph(
                navController = navController,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                primaryProgramId = primaryProgramId,
                nutritionViewModel = nutritionViewModel,
            )
        } else {
            NavigationSuiteScaffold(
                navigationSuiteItems = {

                    item(
                        icon = { Icon(Icons.Default.Home, null, tint = navIconTint(currentTab == KpknRoute.Home.route)) },
                        label = { Text(stringResource(R.string.nav_home)) },
                        selected = currentTab == KpknRoute.Home.route,
                        onClick = { 
                            telemetryHelper.logNavigation(currentTab, KpknRoute.Home.route)
                            navController.navigate(KpknRoute.Home.route) { launchSingleTop = true } 
                        },
                    )
                    item(
                        icon = { DumbbellIcon(tint = navIconTint(currentTab == KpknRoute.Training.route)) },
                        label = { Text(stringResource(R.string.nav_training)) },
                        selected = currentTab == KpknRoute.Training.route,
                        onClick = { 
                            telemetryHelper.logNavigation(currentTab, KpknRoute.Training.route)
                            val activeProgramId = activeProgram?.id
                            if (activeProgramId != null) {
                                navController.navigate(KpknRoute.ProgramDetail.create(activeProgramId)) {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(KpknRoute.Training.route) { launchSingleTop = true }
                            }
                        },
                    )
                    item(
                        icon = { NutritionIcon(tint = navIconTint(currentTab == KpknRoute.Nutrition.route)) },
                        label = { Text(stringResource(R.string.nav_nutrition)) },
                        selected = currentTab == KpknRoute.Nutrition.route,
                        onClick = { 
                            telemetryHelper.logNutritionOpen()
                            telemetryHelper.logNavigation(currentTab, KpknRoute.Nutrition.route)
                            navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true } 
                        },
                    )
                    item(
                        icon = { RingsTabIcon(tint = navIconTint(currentTab == KpknRoute.MyRings.route)) },
                        label = { Text(stringResource(R.string.nav_my_rings)) },
                        selected = currentTab == KpknRoute.MyRings.route,
                        onClick = {
                            telemetryHelper.logNavigation(currentTab, KpknRoute.MyRings.route)
                            navController.navigate(KpknRoute.MyRings.route) { launchSingleTop = true }
                        },
                    )
                    item(
                        icon = { WikiIcon(tint = navIconTint(currentTab == KpknRoute.WikiLab.route)) },
                        label = { Text(stringResource(R.string.nav_wikilab)) },
                        selected = currentTab == KpknRoute.WikiLab.route,
                        onClick = { 
                            telemetryHelper.logNavigation(currentTab, KpknRoute.WikiLab.route)
                            navController.navigate(KpknRoute.WikiLab.route) { launchSingleTop = true } 
                        },
                    )
                },
            ) {
                KPKNNavGraph(
                    navController = navController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    primaryProgramId = primaryProgramId,
                    nutritionViewModel = nutritionViewModel,
                )
            }
        }

        // ─── Floating "session in progress" banner ─────────────────────────────
        // Appears on top of navigation when a workout is active but the user
        // has navigated away. Hidden during workout/readiness screens (isFullscreenWizard).
        if (!isFullscreenWizard) {
            androidx.compose.animation.AnimatedVisibility(
                visible = ongoingWorkout != null,
                enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp),
            ) {
                Card(
                    onClick = {
                        val state = ongoingWorkout ?: return@Card
                        navController.navigate(
                            KpknRoute.Workout.create(state.programId, state.session.id)
                        ) { launchSingleTop = true }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Sesión en curso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                ongoingWorkout?.session?.name ?: "Entrenamiento activo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val state = ongoingWorkout ?: return@FilledTonalButton
                                navController.navigate(
                                    KpknRoute.Workout.create(state.programId, state.session.id)
                                ) { launchSingleTop = true }
                            },
                        ) {
                            Text("Reanudar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KPKNNavGraph(
    navController: androidx.navigation.NavHostController,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    primaryProgramId: String?,
    nutritionViewModel: NutritionViewModel,
) {
    NavHost(navController = navController, startDestination = KpknRoute.Home.route) {
        composable(KpknRoute.Home.route) {
            HomeScreen(
                themeMode = themeMode,
                nutritionViewModel = nutritionViewModel,
                onThemeChange = onThemeChange,
                onNavigateToSettings = { navController.navigate(KpknRoute.Settings.route) },
                onNavigateToProfile = { navController.navigate(KpknRoute.Profile.route) },
                onNavigateToProgram = { programId ->
                    navController.navigate(KpknRoute.ProgramDetail.create(programId))
                },
                onCreateProgram = {
                    navController.navigate(KpknRoute.ProgramEditor.create("new"))
                },
                onStartWorkout = { session, program ->
                    navController.navigate(KpknRoute.Workout.create(program.id, session.id))
                },
                onResumeWorkout = {
                    val ongoing = com.example.kpkn.data.repository.ProgramRepository.getInstance().ongoingWorkout.value
                    if (ongoing != null) {
                        navController.navigate(KpknRoute.Workout.create(ongoing.programId, ongoing.session.id))
                    }
                },
                onNavigateToCard = { cardId ->
                    when (cardId) {
                        "body-progress", "ffmi", "imc", "fat", "muscle" -> {
                            navController.navigate(KpknRoute.Profile.route)
                        }
                        "star-targets", "relative-strength", "history", "ipf-gl" -> {
                            primaryProgramId?.let { navController.navigate(KpknRoute.ProgramDetail.create(it)) }
                        }
                    }
                },
                onNavigate = { destination ->
                    when (destination) {
                        "wiki-home" -> navController.navigate(KpknRoute.WikiLab.route)
                        "wikilab/" -> navController.navigate(KpknRoute.WikiLab.route)
                        "wikilab" -> navController.navigate(KpknRoute.WikiLab.route)
                        "wikilab/concepts" -> navController.navigate(KpknRoute.WikiLabConcepts.route)
                        "wiki-concepts" -> navController.navigate(KpknRoute.WikiLabConcepts.route)
                        "wiki-concept" -> navController.navigate(KpknRoute.WikiLabConcepts.route)
                        "wiki-concept-detail" -> navController.navigate(KpknRoute.WikiLabConcepts.route)
                        "nutrition" -> navController.navigate(KpknRoute.Nutrition.route)
                        "settings/notifications" -> navController.navigate(KpknRoute.SettingsNotifications.route)
                        "learn", "cursos" -> navController.navigate(KpknRoute.Learn.route)
                        "powerlifter-corner" -> {
                            primaryProgramId?.let { navController.navigate(KpknRoute.ProgramDetail.create(it)) }
                        }
                        else -> {
                            if (destination.startsWith("wikilab/concept/")) {
                                val conceptId = destination.removePrefix("wikilab/concept/")
                                if (conceptId.isNotBlank()) {
                                    navController.navigate(KpknRoute.WikiLabConceptDetail.create(conceptId))
                                } else {
                                    navController.navigate(KpknRoute.WikiLab.route)
                                }
                            } else if (destination.startsWith("learn/course/")) {
                                val courseId = destination.removePrefix("learn/course/")
                                if (courseId.isNotBlank()) {
                                    navController.navigate(KpknRoute.LearnCourse.create(courseId))
                                } else {
                                    navController.navigate(KpknRoute.Learn.route)
                                }
                            }
                        }
                    }
                },
            )
        }
        composable(KpknRoute.Training.route) {
            ProgramsScreen(
                onNavigateToProgram = { programId ->
                    navController.navigate(KpknRoute.ProgramDetail.create(programId))
                },
                onCreateProgram = {
                    navController.navigate(KpknRoute.ProgramEditor.create("new"))
                }
            )
        }
        composable(KpknRoute.Nutrition.route) {
            NutritionScreen(
                viewModel = nutritionViewModel,
                onNavigateToWizard = {
                    navController.navigate(KpknRoute.NutritionWizard.route)
                },
                onNavigateToBodyProgress = {
                    navController.navigate(KpknRoute.BodyProgress.route)
                },
                onNavigateToMealHistory = {
                    navController.navigate(KpknRoute.MealHistory.route)
                },
            )
        }

        // Route de acciones rápidas internas para navegación directa desde widgets/atajos
        composable(KpknRoute.NutritionAction.route) { backStack ->
            val action = backStack.arguments?.getString(KpknRoute.NutritionAction.ARG_ACTION)?.lowercase().orEmpty()
            when (action) {
                "openfoodlog", "foodlog", "log" -> {
                    nutritionViewModel.requestFoodLoggerOpen(tab = 0)
                    navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true }
                }
                "opensearch", "search" -> {
                    nutritionViewModel.requestFoodLoggerOpen(tab = 1)
                    navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true }
                }
                "openweighteditor", "weight" -> {
                    navController.navigate(KpknRoute.BodyProgress.route) { launchSingleTop = true }
                }
                "opendashboard", "dashboard" -> {
                    navController.navigate(KpknRoute.Home.route) { launchSingleTop = true }
                }
                else -> {
                    navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true }
                }
            }

            LaunchedEffect(action) {
                navController.popBackStack(KpknRoute.NutritionAction.route, inclusive = true)
            }
        }
        composable(KpknRoute.NutritionWizard.route) {
            val currentSettings by ProgramRepository.getInstance().settings.collectAsState()
            com.example.kpkn.screens.nutrition.components.NutritionWizardView(
                onComplete = { plan ->
                    com.example.kpkn.data.repository.NutritionRepository.getInstance().addNutritionPlan(plan)
                    com.example.kpkn.data.repository.NutritionRepository.getInstance().activatePlan(plan.id)
                    navController.navigate(KpknRoute.Nutrition.route) {
                        popUpTo(KpknRoute.NutritionWizard.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(KpknRoute.Nutrition.route) {
                        popUpTo(KpknRoute.NutritionWizard.route) { inclusive = true }
                    }
                },
                currentSettings = currentSettings,
            )
        }
        composable(KpknRoute.BodyProgress.route) {
            BodyProgressScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.MealHistory.route) {
            MealHistoryScreen(onBack = { navController.popBackStack() })
        }

        // ─── Mis RINGS ────────────────────────────────────────────────
        composable(KpknRoute.MyRings.route) {
            val augeVm: com.example.kpkn.screens.auge.AugeViewModel = viewModel()
            MyRingsScreen(augeViewModel = augeVm)
        }

        // ─── WikiLab Routes ───────────────────────────────────────────
        composable(KpknRoute.WikiLab.route) {
            WikiLabHomeScreen(
                onNavigateToExercises = { navController.navigate(KpknRoute.WikiLabExercises.route) },
                onNavigateToMuscleAnatomy = { navController.navigate(KpknRoute.WikiLabMuscleAnatomy.route) },
                onNavigateToJoints = { navController.navigate(KpknRoute.WikiLabJoints.route) },
                onNavigateToMovementPatterns = { navController.navigate(KpknRoute.WikiLabMovementPatterns.route) },
                onNavigateToBiomechanics = { navController.navigate(KpknRoute.WikiLabBiomechanics.route) },
                onNavigateToConcepts = { navController.navigate(KpknRoute.WikiLabConcepts.route) },
                onNavigateToLearn = { navController.navigate(KpknRoute.Learn.route) },
                onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onNavigateToChain = { navController.navigate(KpknRoute.WikiLabChainDetail.create(it)) },
                onNavigateToConcept = { navController.navigate(KpknRoute.WikiLabConceptDetail.create(it)) },
                onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                onNavigateToPattern = { navController.navigate(KpknRoute.WikiLabPatternDetail.create(it)) },
            )
        }
        composable(KpknRoute.WikiLabExercises.route) {
            WikiLabScreen(
                onCreateExercise = { navController.navigate(KpknRoute.WikiLabExerciseCreator.route) },
                onOpenExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
            )
        }
        composable(KpknRoute.WikiLabExerciseCreator.route) {
            CustomExerciseCreatorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { exerciseId ->
                    val previous = navController.previousBackStackEntry?.destination?.route.orEmpty()
                    if (previous.contains("session-editor")) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(KpknRoute.WikiLabExerciseDetail.create(exerciseId))
                    }
                },
            )
        }
        composable(KpknRoute.WikiLabMuscleAnatomy.route) {
            MuscleCategoryScreen(
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabMuscleDetail.route) { backStack ->
            val muscleId = backStack.arguments?.getString(KpknRoute.WikiLabMuscleDetail.ARG_MUSCLE_ID) ?: ""
            MuscleGroupDetailScreen(
                muscleId = muscleId,
                onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                onNavigateToTendon = { navController.navigate(KpknRoute.WikiLabTendonDetail.create(it)) },
                onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabJoints.route) {
            JointsListScreen(
                onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabJointDetail.route) { backStack ->
            val jointId = backStack.arguments?.getString(KpknRoute.WikiLabJointDetail.ARG_JOINT_ID) ?: ""
            JointDetailScreen(
                jointId = jointId,
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onNavigateToTendon = { navController.navigate(KpknRoute.WikiLabTendonDetail.create(it)) },
                onNavigateToPattern = { navController.navigate(KpknRoute.WikiLabPatternDetail.create(it)) },
                onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabTendonDetail.route) { backStack ->
            val tendonId = backStack.arguments?.getString(KpknRoute.WikiLabTendonDetail.ARG_TENDON_ID) ?: ""
            TendonDetailScreen(
                tendonId = tendonId,
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabMovementPatterns.route) {
            PatternsListScreen(
                onNavigateToPattern = { navController.navigate(KpknRoute.WikiLabPatternDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabPatternDetail.route) { backStack ->
            val patternId = backStack.arguments?.getString(KpknRoute.WikiLabPatternDetail.ARG_PATTERN_ID) ?: ""
            MovementPatternDetailScreen(
                patternId = patternId,
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabChainDetail.route) { backStack ->
            val chainId = backStack.arguments?.getString(KpknRoute.WikiLabChainDetail.ARG_CHAIN_ID) ?: ""
            KineticChainDetailScreen(
                chainId = chainId,
                onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabExerciseDetail.route) { backStack ->
            val exerciseId = backStack.arguments?.getString(KpknRoute.WikiLabExerciseDetail.ARG_EXERCISE_ID) ?: ""
            val exercise = com.example.kpkn.data.exercises.resolveExercise(exerciseId)
                ?: com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.value
                    .firstOrNull { it.id.equals(exerciseId, ignoreCase = true) }
            if (exercise != null) {
                ExerciseDetailScreen(
                    exercise = exercise,
                    onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                    onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
                    onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                    onBack = { navController.popBackStack() },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ejercicio no encontrado")
                }
            }
        }
        composable(KpknRoute.WikiLabBiomechanics.route) {
            BiomechanicsScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.WikiLabConcepts.route) {
            TrainingConceptsScreen(
                onNavigateToConcept = { navController.navigate(KpknRoute.WikiLabConceptDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.WikiLabConceptDetail.route) { backStack ->
            val conceptId = backStack.arguments?.getString(KpknRoute.WikiLabConceptDetail.ARG_CONCEPT_ID) ?: ""
            ConceptDetailScreen(
                conceptId = conceptId,
                onNavigateToConcept = { navController.navigate(KpknRoute.WikiLabConceptDetail.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        // ─── Learn (Cursos) Routes ────────────────────────────────────────
        composable(KpknRoute.Learn.route) {
            com.example.kpkn.screens.learn.LearnHomeScreen(
                onOpenCourse = { navController.navigate(KpknRoute.LearnCourse.create(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.LearnCourse.route) { backStack ->
            val courseId = backStack.arguments?.getString(KpknRoute.LearnCourse.ARG_COURSE_ID) ?: ""
            com.example.kpkn.screens.learn.LearnCourseScreen(
                courseId = courseId,
                onStartModule = { submoduleIndex ->
                    navController.navigate(KpknRoute.LearnReader.create(courseId, submoduleIndex))
                },
                onStartFinalQuiz = {
                    navController.navigate(KpknRoute.LearnQuiz.create(courseId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.LearnReader.route) { backStack ->
            val courseId = backStack.arguments?.getString(KpknRoute.LearnReader.ARG_COURSE_ID) ?: ""
            val submoduleIndex = backStack.arguments?.getString(KpknRoute.LearnReader.ARG_SUBMODULE_INDEX)?.toIntOrNull() ?: 0
            com.example.kpkn.screens.learn.LearnReaderScreen(
                courseId = courseId,
                submoduleIndex = submoduleIndex,
                onBack = { navController.popBackStack() },
                onStartQuiz = {
                    navController.navigate(KpknRoute.LearnQuiz.create(courseId, submoduleIndex)) {
                        popUpTo(KpknRoute.LearnReader.create(courseId, submoduleIndex)) { inclusive = true }
                    }
                },
            )
        }
        composable(KpknRoute.LearnQuiz.route) { backStack ->
            val courseId = backStack.arguments?.getString(KpknRoute.LearnQuiz.ARG_COURSE_ID) ?: ""
            val submoduleIndex = backStack.arguments?.getString(KpknRoute.LearnQuiz.ARG_SUBMODULE_INDEX)?.toIntOrNull() ?: -1
            com.example.kpkn.screens.learn.LearnQuizScreen(
                courseId = courseId,
                submoduleIndex = submoduleIndex,
                onComplete = { _, _ ->
                    if (submoduleIndex < 0) {
                        // Quiz final -> mostrar badge
                        navController.navigate(KpknRoute.LearnBadge.create(courseId)) {
                            popUpTo(KpknRoute.LearnQuiz.create(courseId)) { inclusive = true }
                        }
                    } else {
                        // Submodule quiz -> volver al curso
                        navController.navigate(KpknRoute.LearnCourse.create(courseId)) {
                            popUpTo(KpknRoute.LearnQuiz.create(courseId, submoduleIndex)) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.LearnBadge.route) { backStack ->
            val courseId = backStack.arguments?.getString(KpknRoute.LearnBadge.ARG_COURSE_ID) ?: ""
            com.example.kpkn.screens.learn.LearnBadgeScreen(
                courseId = courseId,
                onContinue = {
                    navController.navigate(KpknRoute.Learn.route) {
                        popUpTo(KpknRoute.LearnBadge.create(courseId)) { inclusive = true }
                    }
                },
            )
        }

        composable(KpknRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToGeneral = { navController.navigate(KpknRoute.SettingsGeneral.route) },
                onNavigateToProfile = { navController.navigate(KpknRoute.SettingsProfile.route) },
                onNavigateToNutrition = { navController.navigate(KpknRoute.SettingsNutrition.route) },
                onNavigateToTraining = { navController.navigate(KpknRoute.SettingsTraining.route) },
                onNavigateToAuge = { navController.navigate(KpknRoute.SettingsAuge.route) },
                onNavigateToNotifications = { navController.navigate(KpknRoute.SettingsNotifications.route) },
                onNavigateToData = { navController.navigate(KpknRoute.SettingsData.route) },
            )
        }
        composable(KpknRoute.SettingsGeneral.route) {
            SettingsGeneralScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.SettingsProfile.route) {
            SettingsProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.SettingsNutrition.route) {
            SettingsNutritionScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWizard = { navController.navigate(KpknRoute.NutritionWizard.route) },
            )
        }
        composable(KpknRoute.SettingsTraining.route) {
            SettingsTrainingScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.SettingsAuge.route) {
            SettingsAugeScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.SettingsNotifications.route) {
            SettingsNotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.SettingsData.route) {
            SettingsDataScreen(onBack = { navController.popBackStack() })
        }
        addHealthConnectRoute(navController)
        composable(KpknRoute.Profile.route) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.ProgramDetail.route) { backStack ->
            val id = backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_PROGRAM_ID) ?: ""
            ProgramDetailScreen(
                programId = id,
                onBack = { navController.popBackStack() },
                onStartWorkout = { session, program ->
                    navController.navigate(KpknRoute.Workout.create(program.id, session.id))
                },
                onEditSession = { sessionId ->
                    navController.navigate(KpknRoute.SessionEditor.create(id, sessionId))
                },
                onCreateSession = { sessionId, weekId, macroIndex, mesoIndex, dayOfWeek ->
                    navController.navigate(
                        KpknRoute.SessionEditor.create(
                            programId = id,
                            sessionId = sessionId,
                            weekId = weekId,
                            macroIndex = macroIndex,
                            mesoIndex = mesoIndex,
                            dayOfWeek = dayOfWeek,
                        )
                    )
                },
                onEditProgram = { targetId ->
                    val exists = ProgramRepository.getInstance().getProgramById(targetId) != null
                    if (exists) {
                        navController.navigate(KpknRoute.ProgramEditor.create(targetId))
                    }
                },
            )
        }
        composable(KpknRoute.SessionEditor.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_PROGRAM_ID) ?: ""
            val sessionId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_SESSION_ID) ?: ""
            val weekId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_WEEK_ID)
            val macroIndex = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_MACRO_INDEX)?.toIntOrNull()
            val mesoIndex = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_MESO_INDEX)?.toIntOrNull()
            val dayOfWeek = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_DAY_OF_WEEK)?.toIntOrNull()
            SessionEditorScreen(
                programId = programId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onOpenExerciseCreator = { navController.navigate(KpknRoute.WikiLabExerciseCreator.route) },
                onOpenExerciseDetail = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onSavedAndExit = {
                    navController.navigate(KpknRoute.ProgramDetail.create(programId)) {
                        popUpTo(KpknRoute.SessionEditor.route) { inclusive = true }
                    }
                },
                draftWeekId = weekId,
                draftMacroIndex = macroIndex,
                draftMesoIndex = mesoIndex,
                draftDayOfWeek = dayOfWeek,
            )
        }
        composable(KpknRoute.Workout.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.Workout.ARG_PROGRAM_ID) ?: ""
            val sessionId = backStack.arguments?.getString(KpknRoute.Workout.ARG_SESSION_ID) ?: ""
            WorkoutScreen(
                programId = programId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(KpknRoute.Home.route) {
                        popUpTo(KpknRoute.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToWikiLab = { exerciseDbId ->
                    navController.navigate(KpknRoute.WikiLabExerciseDetail.create(exerciseDbId))
                },
            )
        }
        composable(KpknRoute.ProgramEditor.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.ProgramEditor.ARG_PROGRAM_ID) ?: ""
            ProgramEditorScreen(
                programId = programId,
                onNavigateToDetail = { id ->
                    navController.navigate(KpknRoute.ProgramDetail.create(id)) {
                        popUpTo(KpknRoute.ProgramEditor.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun navIconTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

@Composable fun GenericScreen(t: String) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
