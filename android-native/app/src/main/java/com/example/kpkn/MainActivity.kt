package com.example.kpkn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.nutrition.NutritionScreen
import com.example.kpkn.screens.profile.ProfileScreen
import com.example.kpkn.screens.programdetail.ProgramDetailScreen
import com.example.kpkn.screens.programeditor.ProgramEditorScreen
import com.example.kpkn.screens.programs.ProgramsScreen
import com.example.kpkn.screens.sessioneditor.SessionEditorScreen
import com.example.kpkn.screens.settings.SettingsScreen
import com.example.kpkn.screens.wikilab.*
import com.example.kpkn.screens.workout.WorkoutScreen
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize repositories (loads Room data → StateFlows)
        com.example.kpkn.data.repository.ProgramRepository.init(this)
        com.example.kpkn.data.repository.AugeRepository.getInstance(this)
        com.example.kpkn.data.repository.NutritionRepository.init(this)

        // Initialize WikiLab (muscle/joint/tendon/pattern/chain data)
        val db = com.example.kpkn.data.db.KpknDatabase.getInstance(this)
        com.example.kpkn.data.repository.WikiLabRepository.initialize(this, db)

        // Initialize Local AI at app startup (non-blocking)
        val context = this
        kotlinx.coroutines.GlobalScope.launch {
            com.example.kpkn.data.localai.LocalAiManager.initialize(context)
        }

        setContent {
            val systemInDarkTheme = isSystemInDarkTheme()
            var themeMode by remember { 
                mutableStateOf(if (systemInDarkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT) 
            }

            KPKNTheme(themeMode = themeMode) {
                KPKNApp(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it }
                )
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
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val currentTab = when {
        currentRoute?.startsWith(KpknRoute.Training.route) == true  -> KpknRoute.Training.route
        currentRoute?.startsWith(KpknRoute.Nutrition.route) == true -> KpknRoute.Nutrition.route
        currentRoute?.startsWith(KpknRoute.WikiLab.route) == true   -> KpknRoute.WikiLab.route
        else -> KpknRoute.Home.route
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onClick = { navController.navigate(KpknRoute.Training.route) { launchSingleTop = true } },
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
            NavHost(navController = navController, startDestination = KpknRoute.Home.route) {
                composable(KpknRoute.Home.route) {
                    HomeScreen(
                        themeMode = themeMode,
                        onThemeChange = onThemeChange,
                        onNavigateToSettings = { navController.navigate(KpknRoute.Settings.route) },
                        onNavigateToProfile = { navController.navigate(KpknRoute.Profile.route) },
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
                composable(KpknRoute.Nutrition.route) { NutritionScreen() }

                // ─── WikiLab Routes ───────────────────────────────────────────
                composable(KpknRoute.WikiLab.route) {
                    WikiLabHomeScreen(
                        onNavigateToExercises = { navController.navigate(KpknRoute.WikiLabExercises.route) },
                        onNavigateToMuscleAnatomy = { navController.navigate(KpknRoute.WikiLabMuscleAnatomy.route) },
                        onNavigateToJoints = { navController.navigate(KpknRoute.WikiLabJoints.route) },
                        onNavigateToMovementPatterns = { navController.navigate(KpknRoute.WikiLabMovementPatterns.route) },
                        onNavigateToBiomechanics = { navController.navigate(KpknRoute.WikiLabBiomechanics.route) },
                        onNavigateToExercise = { navController.navigate(KpknRoute.WikiLabExerciseDetail.create(it)) },
                        onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                        onNavigateToChain = { navController.navigate(KpknRoute.WikiLabChainDetail.create(it)) },
                    )
                }
                composable(KpknRoute.WikiLabExercises.route) { WikiLabScreen() }
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
                    val joints by com.example.kpkn.data.repository.WikiLabRepository.joints.collectAsState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text("Articulaciones", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        }
                        items(joints) { joint ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    navController.navigate(KpknRoute.WikiLabJointDetail.create(joint.id))
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(joint.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        com.example.kpkn.data.repository.WikiLabRepository.getJointTypeLabel(joint.type),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
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
                    val patterns by com.example.kpkn.data.repository.WikiLabRepository.patterns.collectAsState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text("Patrones de Movimiento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        }
                        items(patterns) { pattern ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    navController.navigate(KpknRoute.WikiLabPatternDetail.create(pattern.id))
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(pattern.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        pattern.description.take(80) + "...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
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
                    val exercise = com.example.kpkn.data.exercises.EXERCISE_DATABASE.find { it.id == exerciseId }
                    if (exercise != null) {
                        ExerciseDetailScreen(
                            exercise = exercise,
                            onNavigateToMuscle = { navController.navigate(KpknRoute.WikiLabMuscleDetail.create(it)) },
                            onNavigateToJoint = { navController.navigate(KpknRoute.WikiLabJointDetail.create(it)) },
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

                composable(KpknRoute.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        themeMode = themeMode,
                        onThemeChange = onThemeChange,
                    )
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
                            navController.navigate(KpknRoute.Workout.create(program.id, session.id))
                        },
                        onEditSession = { sessionId ->
                            navController.navigate(KpknRoute.SessionEditor.create(id, sessionId))
                        },
                        onEditProgram = {
                            navController.navigate(KpknRoute.ProgramEditor.create(id))
                        },
                    )
                }
                composable(KpknRoute.SessionEditor.route) { backStack ->
                    val programId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_PROGRAM_ID) ?: ""
                    val sessionId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_SESSION_ID) ?: ""
                    SessionEditorScreen(
                        programId = programId,
                        sessionId = sessionId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(KpknRoute.Workout.route) { backStack ->
                    val programId = backStack.arguments?.getString(KpknRoute.Workout.ARG_PROGRAM_ID) ?: ""
                    val sessionId = backStack.arguments?.getString(KpknRoute.Workout.ARG_SESSION_ID) ?: ""
                    WorkoutScreen(
                        programId = programId,
                        sessionId = sessionId,
                        onBack = { navController.popBackStack() },
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
    }
}

@Composable
private fun navIconTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

@Composable fun GenericScreen(t: String) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
