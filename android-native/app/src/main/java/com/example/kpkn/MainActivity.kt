package com.example.kpkn

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.nutrition.BodyProgressScreen
import com.example.kpkn.screens.nutrition.MealHistoryScreen
import com.example.kpkn.screens.nutrition.NutritionScreen
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
import com.example.kpkn.screens.workout.ReadinessGateScreen
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            com.example.kpkn.data.exercises.initializeExerciseDatabase(this)
        }.onFailure { it.printStackTrace() }

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

        // Register app context for lazy Local AI usage.
        com.example.kpkn.data.localai.LocalAiManager.primeContext(this)

        requestRequiredPermissions()

        setContent {
            var themeMode by remember { mutableStateOf(AppThemeMode.HIGH_CONTRAST) }

            KPKNTheme(themeMode = themeMode) {
                KPKNApp(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        com.example.kpkn.data.localai.LocalAiManager.onAppBackgrounded()
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
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.onFailure { it.printStackTrace() }
            }
        }
    }
}

// ─── App root with Navigation Compose ───────────────────────────────────────

@Composable
fun KPKNApp(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val programsViewModel: ProgramsViewModel = viewModel()
    val activeProgram by programsViewModel.activeProgram.collectAsState()
    val allPrograms by programsViewModel.programs.collectAsState()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
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
        currentRoute?.startsWith(KpknRoute.Nutrition.route) == true -> KpknRoute.Nutrition.route
        currentRoute?.startsWith(KpknRoute.WikiLab.route) == true   -> KpknRoute.WikiLab.route
        else -> KpknRoute.Home.route
    }

    val ongoingWorkout by ProgramRepository.getInstance().ongoingWorkout.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isFullscreenWizard) {
            KPKNNavGraph(
                navController = navController,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                primaryProgramId = primaryProgramId,
            )
        } else {
            NavigationSuiteScaffold(
                navigationSuiteItems = {

                    item(
                        icon = { Icon(Icons.Default.Home, null, tint = navIconTint(currentTab == KpknRoute.Home.route)) },
                        label = { Text("Inicio") },
                        selected = currentTab == KpknRoute.Home.route,
                        onClick = { navController.navigate(KpknRoute.Home.route) { launchSingleTop = true } },
                    )
                    item(
                        icon = { DumbbellIcon(tint = navIconTint(currentTab == KpknRoute.Training.route)) },
                        label = { Text("Entreno") },
                        selected = currentTab == KpknRoute.Training.route,
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
                    )
                    item(
                        icon = { NutritionIcon(tint = navIconTint(currentTab == KpknRoute.Nutrition.route)) },
                        label = { Text("Nutrición") },
                        selected = currentTab == KpknRoute.Nutrition.route,
                        onClick = { navController.navigate(KpknRoute.Nutrition.route) { launchSingleTop = true } },
                    )
                    item(
                        icon = { WikiIcon(tint = navIconTint(currentTab == KpknRoute.WikiLab.route)) },
                        label = { Text("WikiLab") },
                        selected = currentTab == KpknRoute.WikiLab.route,
                        onClick = { navController.navigate(KpknRoute.WikiLab.route) { launchSingleTop = true } },
                    )
                },
            ) {
                KPKNNavGraph(
                    navController = navController,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    primaryProgramId = primaryProgramId,
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
) {
    NavHost(navController = navController, startDestination = KpknRoute.Home.route) {
        composable(KpknRoute.Home.route) {
            HomeScreen(
                themeMode = themeMode,
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
                    navController.navigate(KpknRoute.ReadinessGate.create(program.id, session.id))
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
        composable(KpknRoute.NutritionWizard.route) {
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
                currentSettings = com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.value,
            )
        }
        composable(KpknRoute.BodyProgress.route) {
            BodyProgressScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.MealHistory.route) {
            MealHistoryScreen(onBack = { navController.popBackStack() })
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
                val augeViewModel: AugeViewModel = viewModel()
                val augeBatteries by augeViewModel.batteries.collectAsState()
                ExerciseDetailScreen(
                    exercise = exercise,
                    batteries = augeBatteries,
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
        composable(KpknRoute.Profile.route) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(KpknRoute.ProgramDetail.route) { backStack ->
            val id = backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_PROGRAM_ID) ?: ""
            ProgramDetailScreen(
                programId = id,
                onBack = { navController.popBackStack() },
                onStartWorkout = { session, program ->
                    navController.navigate(KpknRoute.ReadinessGate.create(program.id, session.id))
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
                draftWeekId = weekId,
                draftMacroIndex = macroIndex,
                draftMesoIndex = mesoIndex,
                draftDayOfWeek = dayOfWeek,
            )
        }
        composable(KpknRoute.ReadinessGate.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.ReadinessGate.ARG_PROGRAM_ID) ?: ""
            val sessionId = backStack.arguments?.getString(KpknRoute.ReadinessGate.ARG_SESSION_ID) ?: ""
            ReadinessGateScreen(
                programId = programId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onReady = {
                    navController.navigate(KpknRoute.Workout.create(programId, sessionId)) {
                        popUpTo(KpknRoute.ReadinessGate.route) { inclusive = true }
                    }
                },
            )
        }
        composable(KpknRoute.Workout.route) { backStack ->
            val programId = backStack.arguments?.getString(KpknRoute.Workout.ARG_PROGRAM_ID) ?: ""
            val sessionId = backStack.arguments?.getString(KpknRoute.Workout.ARG_SESSION_ID) ?: ""
            WorkoutScreen(
                programId = programId,
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
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
