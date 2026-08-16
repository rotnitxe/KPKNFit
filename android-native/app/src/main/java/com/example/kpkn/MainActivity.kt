package com.example.kpkn

import com.example.kpkn.BuildConfig

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.services.diagnostics.KpknReportManager
import com.example.kpkn.services.diagnostics.ReportEnrichmentScheduler
import com.example.kpkn.services.diagnostics.ReportGestureDetector
import com.example.kpkn.screens.reports.ReportDialog
import com.example.kpkn.screens.reports.ReportRequestBus
import com.example.kpkn.navigation.DeepLinkRouter
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.navigation.addHealthConnectRoute
import com.example.kpkn.navigation.healthConnectRouteAvailable
import com.example.kpkn.navigation.NavigationBus
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSavedStateKeys
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogScreen
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.home.HomeGlassOverlay
import com.example.kpkn.screens.home.HomeGlassOverlayChange
import com.example.kpkn.screens.competitions.CompetitionScreen
import com.example.kpkn.screens.nutrition.BodyProgressScreen
import com.example.kpkn.screens.nutrition.MealHistoryScreen
import com.example.kpkn.screens.nutrition.NutritionScreen
import com.example.kpkn.screens.nutrition.NutritionCalibrationScreen
import com.example.kpkn.screens.nutrition.NutritionWizardScreen
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.screens.profile.ProfileScreen
import com.example.kpkn.screens.programdetail.ProgramDetailScreen
import com.example.kpkn.screens.programdetail.MainTab
import com.example.kpkn.screens.programs.ProgramsScreen
import com.example.kpkn.screens.programs.ProgramsViewModel
import com.example.kpkn.screens.sessioneditor.SessionEditorScreen
import com.example.kpkn.screens.settings.SettingsAugeScreen
import com.example.kpkn.screens.settings.SettingsDataScreen
import com.example.kpkn.screens.settings.SettingsDiagnosticsScreen
import com.example.kpkn.screens.settings.SettingsGeneralScreen
import com.example.kpkn.screens.settings.SettingsNotificationsScreen
import com.example.kpkn.screens.settings.SettingsNutritionScreen
import com.example.kpkn.screens.settings.SettingsProfileScreen
import com.example.kpkn.screens.settings.SettingsScreen
import com.example.kpkn.screens.settings.SettingsTrainingScreen
import com.example.kpkn.screens.wikilab.*
import com.example.kpkn.screens.workout.WorkoutScreen

import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.telemetry.TelemetryHelper
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import com.example.kpkn.ui.components.icons.BodyIcon
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import java.util.UUID
import com.example.kpkn.ui.locale.LocaleManager
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.kpkn.ui.components.KpknAlertDialog

class MainActivity : ComponentActivity() {
    private val pendingDeepLinkRoute = mutableStateOf<String?>(null)
    private val pendingSharedNutritionText = mutableStateOf<String?>(null)
    private val reportGestureProgress = mutableStateOf(0f)
    private lateinit var telemetryHelper: TelemetryHelper
    private var lastReportGestureHapticProgress = 0f

    private val reportGestureDetector: ReportGestureDetector by lazy {
        ReportGestureDetector.from(
            view = window.decorView,
            onCancel = ::dispatchSyntheticCancel,
            onConfirmed = {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                KpknDiagnosticLogger.event("app", "report_gesture_confirmed")
            },
            onReleased = {
                ReportRequestBus.requestGesture(KpknDiagnosticLogger.currentScreen())
                KpknDiagnosticLogger.event("reports", "report_gesture_released")
            },
            onProgress = { progress ->
                reportGestureProgress.value = progress
                if (progress >= 0.5f && lastReportGestureHapticProgress < 0.5f) {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                lastReportGestureHapticProgress = progress
                if (progress <= 0f) lastReportGestureHapticProgress = 0f
            },
        )
    }
    private var dispatchingSyntheticCancel = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (dispatchingSyntheticCancel) return super.dispatchTouchEvent(event)
        val consumed = reportGestureDetector.onTouchEvent(event)
        return if (consumed) true else super.dispatchTouchEvent(event)
    }

    private fun dispatchSyntheticCancel(event: MotionEvent) {
        dispatchingSyntheticCancel = true
        try {
            super.dispatchTouchEvent(event)
        } finally {
            dispatchingSyntheticCancel = false
        }
    }

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

        // Initialize repositories synchronously before setContent
        runCatching {
            ProgramRepository.init(this@MainActivity)
            com.example.kpkn.data.repository.CompetitionRepository.init(this@MainActivity)
            com.example.kpkn.data.repository.AugeRepository.getInstance(this@MainActivity)
            com.example.kpkn.data.repository.NutritionRepository.init(this@MainActivity)
            com.example.kpkn.data.repository.CustomExerciseRepository.initialize(this@MainActivity)
            com.example.kpkn.data.repository.LearnRepository.initialize(this@MainActivity)
        }.onFailure { logKpknError("MainActivity", "Error initializing repositories", it) }

        lifecycleScope.launch(Dispatchers.IO) {
            // 2. Initialize Exercise Database
            runCatching {
                com.example.kpkn.data.exercises.initializeExerciseDatabase(this@MainActivity)
            }.onFailure { logKpknError("MainActivity", "Error initializing exercise database", it) }

            // 3. Initialize WikiLab
            runCatching {
                val db = com.example.kpkn.data.db.KpknDatabase.getInstance(this@MainActivity)
                com.example.kpkn.data.repository.WikiLabRepository.initialize(this@MainActivity, db)
            }.onFailure { logKpknError("MainActivity", "Error initializing WikiLab", it) }

            // 4. Sync Room appLanguage -> SharedPreferences (retrocompat)
            runCatching {
                val savedLang = ProgramRepository.getInstance().settings.value.appLanguage
                LocaleManager.persist(this@MainActivity, savedLang)
            }.onFailure { logKpknError("MainActivity", "Error syncing app language", it) }

            // 5. Load Custom Exercises
            runCatching {
                com.example.kpkn.data.exercises.loadCustomExercisesAsync(this@MainActivity)
            }.onFailure { logKpknError("MainActivity", "Error loading custom exercises", it) }

            // 6. Ensure channels and alert managers
            runCatching {
                WorkoutRestAlertManager(this@MainActivity).ensureChannels()
            }.onFailure { logKpknError("MainActivity", "Error setting up WorkoutRestAlertManager channels", it) }

            // 7. Setup nutrition notification channels + reminders
            runCatching {
                val nutritionNotifManager = com.example.kpkn.services.nutrition.NutritionNotificationManager(this@MainActivity)
                nutritionNotifManager.createChannels()
                val settings = ProgramRepository.getInstance().settings.value
                if (settings.mealReminderEnabled) {
                    nutritionNotifManager.scheduleMealReminders(
                        breakfastTime = settings.mealReminderBreakfast,
                        lunchTime = settings.mealReminderLunch,
                        dinnerTime = settings.mealReminderDinner,
                    )
                    nutritionNotifManager.scheduleDailyMacroCheck()
                }
            }.onFailure { logKpknError("MainActivity", "Error setting up nutrition notifications", it) }

            // 8. Setup workout reminders
            runCatching {
                val workoutReminderManager = com.example.kpkn.services.workout.WorkoutReminderManager(this@MainActivity)
                workoutReminderManager.createChannels()
                com.example.kpkn.services.workout.LoopNotificationManager(this@MainActivity).createChannels()
                com.example.kpkn.services.competition.CompetitionReminderManager(this@MainActivity).createChannels()
                val settings = ProgramRepository.getInstance().settings.value
                if (settings.workoutReminderEnabled) {
                    workoutReminderManager.scheduleWorkoutReminder(settings.workoutReminderTime)
                }
                if (settings.sleepReminderEnabled) {
                    workoutReminderManager.scheduleSleepReminder(settings.sleepReminderTime)
                }
            }.onFailure { logKpknError("MainActivity", "Error setting up workout reminders", it) }

            // 9. Preload Catalog V2 picker cache (decodes the ~3 MB asset once per process)
            runCatching {
                com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache.getOrLoad(this@MainActivity)
            }.onFailure { logKpknError("MainActivity", "Error preloading Catalog V2 cache", it) }
        }

        // API ≤ 32: observe locale change events emitted by SettingsViewModel
        lifecycleScope.launch {
            LocaleManager.recreateEvent.collect { recreate() }
        }

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
                    reportGestureProgress = reportGestureProgress.value,
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

    private fun logKpknError(tag: String, message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(tag, message, throwable)
        }
    }

    override fun onStop() {
        reportGestureDetector.cancel()
        KpknDiagnosticLogger.event("app", "activity_stop")
        super.onStop()
        telemetryHelper.logAppBackground()
        // Flush pending writes without blocking the main thread during background transition.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(1500L) {
                    ProgramRepository.getInstance().flushPendingWrites()
                }
            }.onFailure { logKpknError("MainActivity", "Error flushing pending writes on stop", it) }
        }
    }

    override fun onStart() {
        super.onStart()
        KpknDiagnosticLogger.event("app", "activity_start")
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            telemetryHelper.logAppForeground()
        } else {
            hasResumedOnce = true
        }
    }

    private var hasResumedOnce = false
    private var locationPermissionRequestInFlight = false

    private fun requestRequiredPermissions() {
        val shouldRequestNotifications =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (shouldRequestNotifications) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        } else {
            requestLocationPermissionIfNeeded()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            val canScheduleExact = alarmManager?.canScheduleExactAlarms() == true
            if (!canScheduleExact) {
                logKpknPermissionIssue("SCHEDULE_EXACT_ALARM")
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            // Android only presents one runtime permission dialog at a time. Chain
            // location after the notification result instead of racing two requests.
            requestLocationPermissionIfNeeded()
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            val alreadyRequested = ProgramRepository.getInstance().settings.value.locationPermissionRequestedOnce
            if (!alreadyRequested && !locationPermissionRequestInFlight) {
                locationPermissionRequestInFlight = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1002)
                lifecycleScope.launch(Dispatchers.IO) {
                    ProgramRepository.getInstance().updateSettings { it.copy(locationPermissionRequestedOnce = true) }
                }
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

private fun resolveRouteTemplate(route: String?, arguments: android.os.Bundle?): String? {
    if (route == null || arguments == null) return route
    var resolved: String = route
    Regex("\\{([^}]+)\\}").findAll(route).forEach { match ->
        val key = match.groupValues[1]
        val value = arguments.get(key)?.toString()
        if (value != null) resolved = resolved.replace(match.value, value)
    }
    return resolved
}

@Composable
fun KPKNApp(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    pendingDeepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    pendingSharedNutritionText: String? = null,
    onSharedNutritionHandled: () -> Unit = {},
    reportGestureProgress: Float = 0f,
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
    val resolvedRoute = remember(currentBackStack) {
        resolveRouteTemplate(currentRoute, currentBackStack?.arguments)
    }
    val previousRoute = remember { mutableStateOf<String?>(null) }
    val pendingReport by ReportRequestBus.pending.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionAlert by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf(emptyList<String>()) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ProgramRepository.getInstance().reconcileTemporalState()
                val missing = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val postNotifGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!postNotifGranted) {
                        missing.add("Notificaciones (Recordatorios y Alertas de Descanso)")
                    }
                }
                missingPermissions = missing
                showPermissionAlert = missing.isNotEmpty()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Log navigation when route changes
    LaunchedEffect(currentRoute) {
        KpknDiagnosticLogger.setCurrentScreen(resolvedRoute)
        if (currentRoute == null) return@LaunchedEffect
        val traceId = UUID.randomUUID().toString()
        KpknDiagnosticLogger.event(
            "app",
            "screen_visible",
            mapOf("route" to resolvedRoute),
            traceId = traceId,
        )
        if (currentRoute != previousRoute.value) {
            telemetryHelper.logNavigation(
                from = previousRoute.value ?: "unknown",
                to = resolvedRoute ?: "unknown",
                traceId = traceId,
            )
            previousRoute.value = currentRoute
        }
    }
    
    val isFullscreenWizard =
        currentRoute?.startsWith("session-editor") == true ||
        currentRoute?.startsWith("workout") == true ||
        currentRoute?.startsWith(KpknRoute.ExerciseCatalog.route) == true ||
        currentRoute?.startsWith(KpknRoute.NutritionWizard.BASE_ROUTE) == true
    val primaryProgramId = activeProgram?.id ?: allPrograms.firstOrNull()?.id

    val currentTab = when {
        currentRoute?.startsWith(KpknRoute.Training.route) == true  -> KpknRoute.Training.route
        currentRoute?.startsWith("program/") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("program-metric-") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("program-editor") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("session-editor") == true -> KpknRoute.Training.route
        currentRoute?.startsWith("workout") == true -> KpknRoute.Training.route
        currentRoute?.startsWith(KpknRoute.Competitions.route) == true -> KpknRoute.Training.route
        currentRoute?.startsWith("competition/") == true -> KpknRoute.Training.route
        currentRoute == KpknRoute.ProgramDetail.route -> KpknRoute.Training.route
        currentRoute == "log-workout" -> KpknRoute.Training.route
        currentRoute?.startsWith(KpknRoute.BodyProgress.route) == true -> KpknRoute.BodyProgress.route
        currentRoute?.startsWith(KpknRoute.Nutrition.route) == true -> KpknRoute.Nutrition.route
        currentRoute?.startsWith(KpknRoute.Learn.route) == true -> KpknRoute.WikiLab.route
        currentRoute?.startsWith(KpknRoute.WikiLab.route) == true   -> KpknRoute.WikiLab.route
        else -> KpknRoute.Home.route
    }

    val ongoingWorkout by ProgramRepository.getInstance().ongoingWorkout.collectAsState()
    var programContextTab by remember { mutableStateOf(MainTab.TRAINING) }
    var onProgramContextTabChange by remember { mutableStateOf<(MainTab) -> Unit>({}) }
    var programContextReady by remember { mutableStateOf(false) }
    var wikiSearchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(currentRoute) {
        programContextReady = currentRoute != KpknRoute.ProgramDetail.route
    }

    LaunchedEffect(currentTab) {
        if (currentTab != KpknRoute.WikiLab.route) wikiSearchQuery = ""
    }

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
        NutritionTelemetry.event(
            "shared_text_received",
            mapOf("channel" to "intent", "descriptionLength" to normalized.length),
        )
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
            NutritionTelemetry.event(
                "shared_text_received",
                mapOf("channel" to "navigation_bus", "descriptionLength" to text.length),
            )
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

    val showTrainingSubtabbar = currentTab == KpknRoute.Training.route &&
            currentRoute == KpknRoute.ProgramDetail.route &&
            programContextReady
    val showWikiSearchSubtabbar = currentTab == KpknRoute.WikiLab.route &&
            currentRoute == KpknRoute.WikiLab.route
    val showContextualSubtabbar = showTrainingSubtabbar || showWikiSearchSubtabbar

    val hazeState = remember { HazeState() }
    val overlayHost = rememberKpknOverlayHostController()
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var homeGlassOverlay by remember { mutableStateOf<HomeGlassOverlay?>(null) }
    var homeModalOverlay by remember { mutableStateOf<HomeGlassOverlay?>(null) }
    // Stable callbacks — never key DisposableEffect on these.
    val onHomeGlassOverlayChange = remember<HomeGlassOverlayChange> {
        { overlay, expectedCurrent ->
            when {
                overlay != null -> {
                    if (homeGlassOverlay !== overlay) homeGlassOverlay = overlay
                }
                // Only clear when the caller proves it still owns the registration.
                expectedCurrent != null && homeGlassOverlay === expectedCurrent -> {
                    homeGlassOverlay = null
                }
            }
        }
    }
    val onHomeModalOverlayChange = remember<HomeGlassOverlayChange> {
        { overlay, expectedCurrent ->
            when {
                overlay != null -> {
                    if (homeModalOverlay !== overlay) homeModalOverlay = overlay
                }
                expectedCurrent != null && homeModalOverlay === expectedCurrent -> {
                    homeModalOverlay = null
                }
            }
        }
    }
    // Exact Home route (not currentTab) so Settings/Profile don't keep the pill.
    // AnimatedVisibility retains content during exit — no delayed nulling (that raced remounts).
    val showHomeGlassOverlays = currentRoute == KpknRoute.Home.route
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalKpknOverlayHost provides overlayHost,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isFullscreenWizard) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .hazeSource(state = hazeState),
            ) {
                KPKNNavGraph(
                    navController = navController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    primaryProgramId = primaryProgramId,
                    nutritionViewModel = nutritionViewModel,
                    onProgramContextTabStateChange = { activeTab, onChange ->
                        programContextTab = activeTab
                        onProgramContextTabChange = onChange
                    },
                    wikiSearchQuery = wikiSearchQuery,
                    onWikiSearchQueryChange = { wikiSearchQuery = it },
                    onHomeGlassOverlayChange = onHomeGlassOverlayChange,
                    onHomeModalOverlayChange = onHomeModalOverlayChange,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .hazeSource(state = hazeState),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                KPKNNavGraph(
                    navController = navController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    primaryProgramId = primaryProgramId,
                    nutritionViewModel = nutritionViewModel,
                    onProgramContextTabStateChange = { activeTab, onChange ->
                        programContextTab = activeTab
                        onProgramContextTabChange = onChange
                        programContextReady = true
                    },
                    wikiSearchQuery = wikiSearchQuery,
                    onWikiSearchQueryChange = { wikiSearchQuery = it },
                    onHomeGlassOverlayChange = onHomeGlassOverlayChange,
                    onHomeModalOverlayChange = onHomeModalOverlayChange,
                )
                }

            }

            // ─── Session in progress banner ─────────────────────────────────
            // Glass overlays must be siblings drawn after hazeSource.
            val isHomeOrTrainingTab = currentTab == KpknRoute.Home.route || currentTab == KpknRoute.Training.route
            val showOngoingFloatingCard = ongoingWorkout != null && isHomeOrTrainingTab && !isFullscreenWizard

            val density = LocalDensity.current
            val bottomBarHeightDp = remember(bottomBarHeightPx, density) {
                with(density) { bottomBarHeightPx.toDp() }
            }

            if (showOngoingFloatingCard) {
                val targetClearance = 12.dp + (if (bottomBarHeightDp > 0.dp) bottomBarHeightDp else (if (showContextualSubtabbar) 130.dp else 76.dp)) + 8.dp
                val floatingSessionBottomPadding by animateDpAsState(
                    targetValue = targetClearance,
                    label = "ongoingSessionDockClearance",
                )
                val bgValue = ongoingWorkout?.session?.background?.value
                val accentColor = remember(bgValue) {
                    when (bgValue) {
                        "gradient://ember" -> Color(0xFFE08E45)
                        "gradient://lagoon" -> Color(0xFF5FA8D3)
                        "gradient://velvet" -> Color(0xFFE26D5A)
                        "gradient://forest" -> Color(0xFF95D5B2)
                        "solid://obsidian" -> Color(0xFF3B82F6)
                        "solid://steel" -> Color(0xFF94A3B8)
                        "solid://ember-red" -> Color(0xFFEF4444)
                        "solid://ocean" -> Color(0xFF38BDF8)
                        "solid://moss" -> Color(0xFF4ADE80)
                        else -> Color(0xFFE08E45)
                    }
                }
                var offsetY by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .offset { IntOffset(0, offsetY.toInt()) }
                        .padding(horizontal = 16.dp)
                        .padding(bottom = floatingSessionBottomPadding)
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                offsetY = (offsetY + delta).coerceIn(-300f, 0f)
                            },
                        )
                        .kpknGlass(hazeState, RoundedCornerShape(20.dp))
                        .zIndex(10f)
                        .clickable {
                            val state = ongoingWorkout ?: return@clickable
                            navController.navigate(KpknRoute.Workout.create(state.programId, state.session.id)) { launchSingleTop = true }
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = accentColor.copy(alpha = 0.25f), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.padding(6.dp), tint = accentColor)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sesión en curso", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                            Text(
                                ongoingWorkout?.session?.name ?: "Entrenamiento activo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledIconButton(
                                onClick = {
                                    val state = ongoingWorkout ?: return@FilledIconButton
                                    navController.navigate(KpknRoute.Workout.create(state.programId, state.session.id)) { launchSingleTop = true }
                                },
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reanudar sesión",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Black,
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    ProgramRepository.getInstance().clearOngoingWorkout()
                                },
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancelar sesión",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // ─── Liquid Glass bottom bar ───────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .kpknGlass(
                        hazeState = hazeState,
                        shape = RoundedCornerShape(32.dp),
                    ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                // ─── Subtabbar contextual extension (animated) ─────────────
                AnimatedVisibility(
                    visible = showContextualSubtabbar,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp)),
                        ) {
                            if (showTrainingSubtabbar) {
                                TabRow(
                                    selectedTabIndex = MainTab.entries.indexOf(programContextTab).coerceAtLeast(0),
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    indicator = { tabPositions ->
                                        val idx = MainTab.entries.indexOf(programContextTab).coerceAtLeast(0)
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                                            height = 3.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        )
                                    },
                                    divider = {},
                                ) {
                                    MainTab.entries.forEach { tab ->
                                        val selected = programContextTab == tab
                                        Tab(
                                            selected = selected,
                                            onClick = { onProgramContextTabChange(tab) },
                                            text = {
                                                Text(
                                                    text = when (tab) {
                                                        MainTab.TRAINING -> "Estructura"
                                                        MainTab.ANALYTICS -> "Analíticas"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                                )
                                            },
                                            selectedContentColor = MaterialTheme.colorScheme.primary,
                                            unselectedContentColor = Color.White,
                                        )
                                    }
                                }
                            } else if (showWikiSearchSubtabbar) {
                                WikiSearchSubtabbar(
                                    query = wikiSearchQuery,
                                    onQueryChange = { wikiSearchQuery = it },
                                    onClear = { wikiSearchQuery = "" },
                                )
                            }
                        }
                    }
                }

                // ─── Main navigation bar ───────────────────────────────────
                val navItemColors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                )
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.height(80.dp),
                ) {
                    val homeSel = currentTab == KpknRoute.Home.route
                    NavigationBarItem(
                        selected = homeSel,
                        onClick = {
                            navController.navigate(KpknRoute.Home.route) { launchSingleTop = true }
                        },
                        icon = { Icon(Icons.Default.Home, null, tint = navIconTint(homeSel)) },
                        label = {
                            Text(
                                stringResource(R.string.nav_home),
                                color = if (homeSel) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = navItemColors,
                    )
                    val trainSel = currentTab == KpknRoute.Training.route
                    NavigationBarItem(
                        selected = trainSel,
                        onClick = {
                            val activeProgramId = activeProgram?.id
                            if (activeProgramId != null) {
                                navController.navigate(KpknRoute.ProgramDetail.create(activeProgramId)) {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(KpknRoute.Training.route) { launchSingleTop = true }
                            }
                        },
                        icon = { DumbbellIcon(tint = navIconTint(trainSel)) },
                        label = {
                            Text(
                                stringResource(R.string.nav_training),
                                color = if (trainSel) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = navItemColors,
                    )
                    val nutSel = currentTab == KpknRoute.Nutrition.route
                    NavigationBarItem(
                        selected = nutSel,
                        onClick = {
                            telemetryHelper.logNutritionOpen()
                            navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true }
                        },
                        icon = { NutritionIcon(tint = navIconTint(nutSel)) },
                        label = {
                            Text(
                                stringResource(R.string.nav_nutrition),
                                color = if (nutSel) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = navItemColors,
                    )
                    val bodySel = currentTab == KpknRoute.BodyProgress.route
                    NavigationBarItem(
                        selected = bodySel,
                        onClick = {
                            navController.navigate(KpknRoute.BodyProgress.route) { launchSingleTop = true }
                        },
                        icon = { BodyIcon(tint = navIconTint(bodySel)) },
                        label = {
                            Text(
                                stringResource(R.string.nav_body),
                                color = if (bodySel) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = navItemColors,
                    )
                    val wikiSel = currentTab == KpknRoute.WikiLab.route
                    NavigationBarItem(
                        selected = wikiSel,
                        onClick = {
                            navController.navigate(KpknRoute.WikiLab.route) { launchSingleTop = true }
                        },
                        icon = { WikiIcon(tint = navIconTint(wikiSel)) },
                        label = {
                            Text(
                                stringResource(R.string.nav_wikilab),
                                color = if (wikiSel) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = navItemColors,
                    )
                }
                }
            }
        }

        // Sibling of hazeSource (drawn after). Visible only on exact Home route.
        // zIndex keeps the pill above NavHost content after back-stack returns.
        AnimatedVisibility(
            visible = showHomeGlassOverlays,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(100f),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            homeGlassOverlay?.invoke(hazeState)
        }

        AnimatedVisibility(
            visible = showHomeGlassOverlays,
            modifier = Modifier.zIndex(101f),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            homeModalOverlay?.invoke(hazeState)
        }

        // Sheets/dialogs portaled out of NavGraph so they are siblings of hazeSource.
        // Only mount the full-screen host when there is at least one entry — an empty
        // fillMaxSize Box at zIndex 400 would swallow every touch in the app.
        val overlayEntries = overlayHost.entries
        if (overlayEntries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(400f),
            ) {
                KpknOverlayHostContent(overlayHost)
            }
        }

        if (reportGestureProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .zIndex(450f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Preparando reporte", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                            progress = reportGestureProgress,
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Mantené los dos dedos quietos",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (pendingReport != null) {
        val reportRequest = pendingReport
        val reportScope = rememberCoroutineScope()
        ReportDialog(
            request = reportRequest!!,
            onDismiss = { ReportRequestBus.consume() },
            onSave = { request ->
                reportScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { KpknReportManager.create(context, request) }
                    }
                    result.onSuccess { created ->
                        ReportEnrichmentScheduler.enqueue(context, created.reportId)
                        ReportRequestBus.consume()
                        Toast.makeText(
                            context,
                            "Reporte guardado. DeepSeek V4 Flash lo analizará en segundo plano.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            "No pude guardar el reporte localmente. Intentá de nuevo.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    if (showPermissionAlert) {
        KpknAlertDialog(
            onDismissRequest = { /* No se cierra al tocar fuera */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Permisos Recomendados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Para garantizar la mejor experiencia en KPKN, te recomendamos habilitar los siguientes permisos:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    missingPermissions.forEach { permissionName ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = permissionName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Las notificaciones nos permiten enviarte tus recordatorios de entrenamiento diarios y alarmas de descanso en segundo plano, mientras que el micrófono habilita los comandos y controles por voz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ir a Configuración", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionAlert = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Omitir por ahora", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    } // CompositionLocalProvider(LocalHazeState)
}

// ─── WikiLab Search Subtabs ────────────────────────────────────────────
@Composable
private fun WikiSearchSubtabbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                null,
                modifier = Modifier.size(16.dp),
                tint = Color.White.copy(alpha = 0.4f),
            )
            Spacer(Modifier.width(8.dp))
            val textValue = query
            BasicTextField(
                value = textValue,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White,
                    fontSize = 13.sp,
                ),
                decorationBox = { innerTextField: @Composable () -> Unit ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Buscar ejercicio, músculo, concepto...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Limpiar",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.5f),
                    )
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
    onProgramContextTabStateChange: (MainTab, (MainTab) -> Unit) -> Unit,
    wikiSearchQuery: String = "",
    onWikiSearchQueryChange: (String) -> Unit = {},
    onHomeGlassOverlayChange: HomeGlassOverlayChange = { _, _ -> },
    onHomeModalOverlayChange: HomeGlassOverlayChange = { _, _ -> },
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
                onCreateProgram = { createProgramAndOpen(navController) },
                onStartWorkout = { session, program ->
                    navigateWorkoutOrCompetition(navController, session, program)
                },
                onResumeWorkout = {
                    val ongoing = ProgramRepository.getInstance().ongoingWorkout.value
                    if (ongoing != null) {
                        navController.navigate(KpknRoute.Workout.create(ongoing.programId, ongoing.session.id))
                    }
                },
                onEditSession = { session, program ->
                    navController.navigate(KpknRoute.SessionEditor.create(program.id, session.id))
                },
                onNavigateToCard = { cardId ->
                    when (cardId) {
                        "body-progress", "ffmi", "imc", "fat", "muscle" -> {
                            navController.navigate(KpknRoute.BodyProgress.route) { launchSingleTop = true }
                        }
                        "star-targets", "relative-strength", "history", "ipf-gl" -> {
                            val programId = primaryProgramId
                            if (programId != null) {
                                navController.navigate(
                                    KpknRoute.ProgramDetail.create(programId, KpknRoute.ProgramDetail.TAB_ANALYTICS)
                                )
                            } else {
                                createProgramAndOpen(navController)
                            }
                        }
                    }
                },
                onHeaderOverlayChange = onHomeGlassOverlayChange,
                onNutritionOverlayChange = onHomeModalOverlayChange,
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
                        "settings/auge" -> navController.navigate(KpknRoute.SettingsAuge.route)
                        "learn", "cursos" -> navController.navigate(KpknRoute.Learn.route)
                        "powerlifter-corner" -> {
                            navController.navigate(KpknRoute.Competitions.route)
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
                onCreateProgram = { createProgramAndOpen(navController) }
            )
        }
        composable(KpknRoute.Competitions.route) {
            CompetitionScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.CompetitionDetail.route) { backStack ->
            val competitionId = backStack.arguments
                ?.getString(KpknRoute.CompetitionDetail.ARG_COMPETITION_ID)
                ?.takeIf { it.isNotBlank() }
            CompetitionScreen(
                onBack = { navController.popBackStack() },
                initialCompetitionId = competitionId,
            )
        }
        composable(KpknRoute.Nutrition.route) {
            NutritionScreen(
                viewModel = nutritionViewModel,
                onNavigateToBodyProgress = {
                    navController.navigate(KpknRoute.BodyProgress.route)
                },
                onNavigateToMealHistory = {
                    navController.navigate(KpknRoute.MealHistory.route)
                },
                onNavigateToWizard = { mode, planId ->
                    navController.navigate(KpknRoute.NutritionWizard.create(mode, planId))
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
        composable(
            route = KpknRoute.NutritionWizard.route,
            arguments = listOf(
                navArgument(KpknRoute.NutritionWizard.ARG_MODE) {
                    type = NavType.StringType
                    defaultValue = "create"
                },
                navArgument(KpknRoute.NutritionWizard.ARG_PLAN_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            NutritionWizardScreen(
                mode = backStackEntry.arguments?.getString(KpknRoute.NutritionWizard.ARG_MODE) ?: "create",
                planId = backStackEntry.arguments?.getString(KpknRoute.NutritionWizard.ARG_PLAN_ID)?.takeIf { it.isNotBlank() },
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(KpknRoute.BodyProgress.route) {
            BodyProgressScreen(
                onCreatePlan = { navController.navigate(KpknRoute.NutritionWizard.create("create", null)) },
            )
        }
        composable(KpknRoute.MealHistory.route) {
            MealHistoryScreen(onBack = { navController.popBackStack() })
        }

        // ─── WikiLab Routes ───────────────────────────────────────────
        composable(KpknRoute.WikiLab.route) {
            WikiLabHomeScreen(
                searchQuery = wikiSearchQuery,
                onSearchQueryChange = onWikiSearchQueryChange,
                onNavigateToExercises = { navController.navigate(KpknRoute.WikiLabExercises.route) },
                onNavigateToMuscleAnatomy = { navController.navigate(KpknRoute.WikiLabMuscleAnatomy.route) },
                onNavigateToJoints = { navController.navigate(KpknRoute.WikiLabJoints.route) },
                onNavigateToMovementPatterns = { navController.navigate(KpknRoute.WikiLabMovementPatterns.route) },
                onNavigateToBiomechanics = { navController.navigate(KpknRoute.WikiLabBiomechanics.route) },
                onNavigateToConcepts = { navController.navigate(KpknRoute.WikiLabConcepts.route) },
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
                onOpenExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onBack = { navController.popBackStack() },
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
            val customExercises by com.example.kpkn.data.repository.CustomExerciseRepository.customExercises.collectAsState()
            val exercise = com.example.kpkn.data.exercises.resolveExercise(exerciseId)
                ?: customExercises.firstOrNull { it.id.equals(exerciseId, ignoreCase = true) }
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
                onNavigateToDiagnostics = { navController.navigate(KpknRoute.SettingsDiagnostics.route) },
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
                onOpenPlanOverlay = {
                    navController.navigate(KpknRoute.NutritionWizard.create(mode = "create"))
                },
                onOpenCalibration = { navController.navigate(KpknRoute.NutritionCalibration.route) },
                showHealthConnect = healthConnectRouteAvailable(),
                onOpenHealthConnect = { navController.navigate(KpknRoute.HealthConnect.route) },
            )
        }
        composable(KpknRoute.NutritionCalibration.route) {
            NutritionCalibrationScreen(onBack = { navController.popBackStack() })
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
        composable(KpknRoute.SettingsDiagnostics.route) {
            SettingsDiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        addHealthConnectRoute(navController)
        composable(KpknRoute.Profile.route) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.ProgramDetail.route) { backStack ->
            val id = backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_PROGRAM_ID) ?: ""
            val initialTab = when (backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_TAB)) {
                KpknRoute.ProgramDetail.TAB_ANALYTICS -> MainTab.ANALYTICS
                else -> null
            }
            ProgramDetailScreen(
                programId = id,
                initialTab = initialTab,
                onBack = {
                    if (!navController.popBackStack(KpknRoute.Training.route, inclusive = false)) {
                        navController.navigate(KpknRoute.Training.route) {
                            popUpTo(KpknRoute.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
                onStartWorkout = { session, program ->
                    navigateWorkoutOrCompetition(navController, session, program)
                },
                onEditSession = { sessionId ->
                    navController.navigate(KpknRoute.SessionEditor.create(id, sessionId))
                },
                onCreateSession = { sessionId, weekId, macroIndex, mesoIndex, dayOfWeek, configureCompetition ->
                    navController.navigate(
                        KpknRoute.SessionEditor.create(
                            programId = id,
                            sessionId = sessionId,
                            weekId = weekId,
                            macroIndex = macroIndex,
                            mesoIndex = mesoIndex,
                            dayOfWeek = dayOfWeek,
                            configureCompetition = configureCompetition,
                        )
                    )
                },
                onOpenProgram = { newProgramId ->
                    navController.navigate(KpknRoute.ProgramDetail.create(newProgramId)) {
                        launchSingleTop = true
                    }
                },
                onContextTabStateChange = { activeTab, onChange ->
                    onProgramContextTabStateChange(activeTab, onChange)
                },
            )
        }
        composable(KpknRoute.ExerciseCatalog.route) { backStack ->
            val requestId = backStack.arguments
                ?.getString(KpknRoute.ExerciseCatalog.ARG_REQUEST_ID)
                .orEmpty()
            val previousEntry = navController.previousBackStackEntry
            val requestJson = previousEntry?.savedStateHandle
                ?.get<String>(CatalogSavedStateKeys.request(requestId))
            val request = requestJson?.let { encoded ->
                runCatching { Json.decodeFromString<CatalogLaunchRequest>(encoded) }.getOrNull()
            } ?: CatalogLaunchRequest(
                requestId = requestId.ifBlank { CatalogLaunchRequest().requestId },
                origin = backStack.arguments
                    ?.getString(KpknRoute.ExerciseCatalog.ARG_ORIGIN)
                    ?.let { runCatching { CatalogLaunchOrigin.valueOf(it) }.getOrNull() }
                    ?: CatalogLaunchOrigin.SESSION_EDITOR,
                selectionMode = backStack.arguments
                    ?.getString(KpknRoute.ExerciseCatalog.ARG_SELECTION_MODE)
                    ?.let { runCatching { CatalogSelectionMode.valueOf(it) }.getOrNull() }
                    ?: CatalogSelectionMode.MULTIPLE,
                targetExerciseId = backStack.arguments
                    ?.getString(KpknRoute.ExerciseCatalog.ARG_TARGET_EXERCISE_ID),
                targetGroupName = backStack.arguments
                    ?.getString(KpknRoute.ExerciseCatalog.ARG_TARGET_GROUP_NAME),
                initialQuery = backStack.arguments
                    ?.getString(KpknRoute.ExerciseCatalog.ARG_INITIAL_QUERY)
                    .orEmpty(),
            )

            ExerciseCatalogScreen(
                request = request,
                onResult = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        CatalogSavedStateKeys.RESULT,
                        Json.encodeToString(result),
                    )
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(
                        CatalogSavedStateKeys.request(request.requestId),
                    )
                    navController.popBackStack()
                },
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        CatalogSavedStateKeys.RESULT,
                        Json.encodeToString(CatalogResult.cancel(request)),
                    )
                    navController.popBackStack()
                },
                onOpenExerciseDetail = { exerciseId ->
                    navController.navigate(KpknRoute.WikiLabExerciseDetail.create(exerciseId))
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
            val configureCompetition = backStack.arguments
                ?.getString(KpknRoute.SessionEditor.ARG_CONFIGURE_COMPETITION)
                ?.toBooleanStrictOrNull() == true
            val catalogResultJson by backStack.savedStateHandle
                .getStateFlow<String?>(CatalogSavedStateKeys.RESULT, null)
                .collectAsState()
            val catalogResult = catalogResultJson?.let { encoded ->
                runCatching { Json.decodeFromString<CatalogResult>(encoded) }.getOrNull()
            }
            SessionEditorScreen(
                programId = programId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onOpenExerciseDetail = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                onOpenCatalog = { request ->
                    backStack.savedStateHandle[CatalogSavedStateKeys.request(request.requestId)] =
                        Json.encodeToString(request)
                    navController.navigate(KpknRoute.ExerciseCatalog.create(request))
                },
                catalogResult = catalogResult,
                onCatalogResultConsumed = {
                    backStack.savedStateHandle.remove<String>(CatalogSavedStateKeys.RESULT)
                },
                onSavedAndExit = {
                    navController.navigate(KpknRoute.ProgramDetail.create(programId)) {
                        popUpTo(KpknRoute.SessionEditor.route) { inclusive = true }
                    }
                },
                draftWeekId = weekId,
                draftMacroIndex = macroIndex,
                draftMesoIndex = mesoIndex,
                draftDayOfWeek = dayOfWeek,
                openCompetitionConfig = configureCompetition,
            )
        }
        composable(KpknRoute.Workout.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.Workout.ARG_PROGRAM_ID) ?: ""
            val sessionId = backStack.arguments?.getString(KpknRoute.Workout.ARG_SESSION_ID) ?: ""
            val catalogResultJson by backStack.savedStateHandle
                .getStateFlow<String?>(CatalogSavedStateKeys.RESULT, null)
                .collectAsState()
            val catalogResult = catalogResultJson?.let { encoded ->
                runCatching { Json.decodeFromString<CatalogResult>(encoded) }.getOrNull()
            }
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
                onOpenCatalog = { request ->
                    backStack.savedStateHandle[CatalogSavedStateKeys.request(request.requestId)] =
                        Json.encodeToString(request)
                    navController.navigate(KpknRoute.ExerciseCatalog.create(request))
                },
                catalogResult = catalogResult,
                onCatalogResultConsumed = {
                    backStack.savedStateHandle.remove<String>(CatalogSavedStateKeys.RESULT)
                },
            )
        }
    }
}

private fun navigateWorkoutOrCompetition(
    navController: androidx.navigation.NavHostController,
    session: Session,
    program: Program,
) {
    // Las sesiones de competición (técnica, bitácora o híbrida) nunca abren el WorkoutScreen:
    // no tienen series planificadas y quedaría una pantalla vacía. Siempre van al flujo de
    // meet day (CompetitionScreen/CompetitionDetail), donde viven el peso corporal, los
    // intentos y las luces del jurado.
    if (session.isCompetitionMeet) {
        val recordId = session.competitionRecordId
        if (!recordId.isNullOrBlank()) {
            navController.navigate(KpknRoute.CompetitionDetail.create(recordId)) { launchSingleTop = true }
        } else {
            navController.navigate(KpknRoute.Competitions.route) { launchSingleTop = true }
        }
    } else {
        navController.navigate(KpknRoute.Workout.create(program.id, session.id))
    }
}

private fun createProgramAndOpen(navController: androidx.navigation.NavHostController) {
    val repository = ProgramRepository.getInstance()
    val nextNumber = repository.programs.value.count { it.name.startsWith("Nuevo programa") } + 1
    val programId = UUID.randomUUID().toString()
    repository.addProgram(
        Program(
            id = programId,
            name = "Nuevo programa $nextNumber",
            coverImage = "gradient://ember",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = UUID.randomUUID().toString(),
                    name = "Macrociclo 1",
                    blocks = listOf(
                        Block(
                            id = UUID.randomUUID().toString(),
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = UUID.randomUUID().toString(),
                                    name = "Mesociclo 1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = UUID.randomUUID().toString(),
                                            name = "Semana 1",
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            ),
        )
    )
    navController.navigate(KpknRoute.ProgramDetail.create(programId))
}

@Composable
private fun navIconTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary
    else Color.White


@Composable fun GenericScreen(t: String) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
