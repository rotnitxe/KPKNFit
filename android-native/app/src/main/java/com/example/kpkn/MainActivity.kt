package com.example.kpkn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.home.HomeScreen
import com.example.kpkn.screens.programs.ProgramsScreen
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.NutritionIcon
import com.example.kpkn.ui.components.icons.WikiIcon
import com.example.kpkn.ui.theme.KPKNTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KPKNTheme {
                KPKNApp()
            }
        }
    }
}

// ─── App root with Navigation Compose ───────────────────────────────────────

@Composable
fun KPKNApp() {
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
                    HomeScreen()
                }
                composable(KpknRoute.Training.route) {
                    ProgramsScreen(
                        onNavigateToProgram = { programId ->
                            navController.navigate(KpknRoute.ProgramDetail.create(programId))
                        },
                        onCreateProgram = { /* TODO */ }
                    )
                }
                composable(KpknRoute.Nutrition.route) { GenericScreen("Nutrición") }
                composable(KpknRoute.WikiLab.route)   { GenericScreen("WikiLab") }
                composable(KpknRoute.ProgramDetail.route) { backStack ->
                    val id = backStack.arguments?.getString(KpknRoute.ProgramDetail.ARG_PROGRAM_ID)
                    GenericScreen("Programa: $id")
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
