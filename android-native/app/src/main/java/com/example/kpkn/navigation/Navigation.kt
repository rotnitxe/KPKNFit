package com.example.kpkn.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

// ─── Routes ─────────────────────────────────────────────────────────────────

sealed class KpknRoute(val route: String) {
    // Bottom nav destinations
    object Home : KpknRoute("home")
    object Training : KpknRoute("training")
    object Nutrition : KpknRoute("nutrition")
    object WikiLab : KpknRoute("wikilab")

    // Detail screens
    object ProgramDetail : KpknRoute("program/{programId}") {
        fun create(programId: String) = "program/$programId"
        const val ARG_PROGRAM_ID = "programId"
    }

    object Settings : KpknRoute("settings")
}

// ─── Bottom Nav Items ────────────────────────────────────────────────────────

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(KpknRoute.Home.route, "Inicio", Icons.Default.Home),
    BottomNavItem(KpknRoute.Training.route, "Entreno", Icons.Default.Home),     // custom icon in screen
    BottomNavItem(KpknRoute.Nutrition.route, "Nutrición", Icons.Default.ShoppingCart),
    BottomNavItem(KpknRoute.WikiLab.route, "WikiLab", Icons.Default.Info),
)
