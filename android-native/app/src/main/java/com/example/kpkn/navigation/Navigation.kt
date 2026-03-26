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

    // ─── Detail screens ─────────────────────────────────────────────────

    object ProgramDetail : KpknRoute("program/{programId}") {
        fun create(programId: String) = "program/$programId"
        const val ARG_PROGRAM_ID = "programId"
    }

    object SessionEditor : KpknRoute("session-editor/{programId}/{sessionId}") {
        fun create(programId: String, sessionId: String) = "session-editor/$programId/$sessionId"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
    }

    object Workout : KpknRoute("workout/{programId}/{sessionId}") {
        fun create(programId: String, sessionId: String) = "workout/$programId/$sessionId"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
    }

    object ProgramEditor : KpknRoute("program-editor/{programId}") {
        fun create(programId: String) = "program-editor/$programId"
        const val ARG_PROGRAM_ID = "programId"
    }

    object Settings : KpknRoute("settings")
    object Profile : KpknRoute("profile")

    // ─── WikiLab Sub-screens ────────────────────────────────────────────

    object WikiLabExercises : KpknRoute("wikilab/exercises")
    object WikiLabMuscleAnatomy : KpknRoute("wikilab/muscles")
    object WikiLabMuscleDetail : KpknRoute("wikilab/muscle/{muscleId}") {
        fun create(muscleId: String) = "wikilab/muscle/$muscleId"
        const val ARG_MUSCLE_ID = "muscleId"
    }
    object WikiLabJoints : KpknRoute("wikilab/joints")
    object WikiLabJointDetail : KpknRoute("wikilab/joint/{jointId}") {
        fun create(jointId: String) = "wikilab/joint/$jointId"
        const val ARG_JOINT_ID = "jointId"
    }
    object WikiLabTendonDetail : KpknRoute("wikilab/tendon/{tendonId}") {
        fun create(tendonId: String) = "wikilab/tendon/$tendonId"
        const val ARG_TENDON_ID = "tendonId"
    }
    object WikiLabMovementPatterns : KpknRoute("wikilab/patterns")
    object WikiLabPatternDetail : KpknRoute("wikilab/pattern/{patternId}") {
        fun create(patternId: String) = "wikilab/pattern/$patternId"
        const val ARG_PATTERN_ID = "patternId"
    }
    object WikiLabChainDetail : KpknRoute("wikilab/chain/{chainId}") {
        fun create(chainId: String) = "wikilab/chain/$chainId"
        const val ARG_CHAIN_ID = "chainId"
    }
    object WikiLabExerciseDetail : KpknRoute("wikilab/exercise/{exerciseId}") {
        fun create(exerciseId: String) = "wikilab/exercise/$exerciseId"
        const val ARG_EXERCISE_ID = "exerciseId"
    }
    object WikiLabBiomechanics : KpknRoute("wikilab/biomechanics")
}

// ─── Bottom Nav Items ────────────────────────────────────────────────────────

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(KpknRoute.Home.route, "Inicio", Icons.Default.Home),
    BottomNavItem(KpknRoute.Training.route, "Entreno", Icons.Default.Home),
    BottomNavItem(KpknRoute.Nutrition.route, "Nutrición", Icons.Default.ShoppingCart),
    BottomNavItem(KpknRoute.WikiLab.route, "WikiLab", Icons.Default.Info),
)
