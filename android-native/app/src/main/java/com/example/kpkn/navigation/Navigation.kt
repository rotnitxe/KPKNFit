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

    object SessionEditor : KpknRoute("session-editor/{programId}/{sessionId}?weekId={weekId}&macroIndex={macroIndex}&mesoIndex={mesoIndex}&dayOfWeek={dayOfWeek}") {
        fun create(
            programId: String,
            sessionId: String,
            weekId: String? = null,
            macroIndex: Int? = null,
            mesoIndex: Int? = null,
            dayOfWeek: Int? = null,
        ): String {
            val query = buildList {
                weekId?.let { add("weekId=$it") }
                macroIndex?.let { add("macroIndex=$it") }
                mesoIndex?.let { add("mesoIndex=$it") }
                dayOfWeek?.let { add("dayOfWeek=$it") }
            }.joinToString("&")

            return if (query.isBlank()) {
                "session-editor/$programId/$sessionId"
            } else {
                "session-editor/$programId/$sessionId?$query"
            }
        }
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_WEEK_ID = "weekId"
        const val ARG_MACRO_INDEX = "macroIndex"
        const val ARG_MESO_INDEX = "mesoIndex"
        const val ARG_DAY_OF_WEEK = "dayOfWeek"
    }

    object Workout : KpknRoute("workout/{programId}/{sessionId}") {
        fun create(programId: String, sessionId: String) = "workout/$programId/$sessionId"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
    }

    object ReadinessGate : KpknRoute("readiness-gate/{programId}/{sessionId}") {
        fun create(programId: String, sessionId: String) = "readiness-gate/$programId/$sessionId"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
    }

    object ProgramEditor : KpknRoute("program-editor/{programId}") {
        fun create(programId: String) = "program-editor/$programId"
        const val ARG_PROGRAM_ID = "programId"
    }

    object Settings : KpknRoute("settings")
    object SettingsGeneral : KpknRoute("settings/general")
    object SettingsProfile : KpknRoute("settings/profile")
    object SettingsNutrition : KpknRoute("settings/nutrition")
    object SettingsTraining : KpknRoute("settings/training")
    object SettingsAuge : KpknRoute("settings/auge")
    object SettingsNotifications : KpknRoute("settings/notifications")
    object SettingsData : KpknRoute("settings/data")
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

    // ─── Nutrition Sub-screens ────────────────────────────────────────
    object NutritionWizard : KpknRoute("nutrition/wizard")
    object BodyProgress : KpknRoute("nutrition/body-progress")
    object MealHistory : KpknRoute("nutrition/meal-history")

    // ─── WikiLab Concepts ──────────────────────────────────────────────

    object WikiLabConcepts : KpknRoute("wikilab/concepts")
    object WikiLabConceptDetail : KpknRoute("wikilab/concept/{conceptId}") {
        fun create(conceptId: String) = "wikilab/concept/$conceptId"
        const val ARG_CONCEPT_ID = "conceptId"
    }
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
