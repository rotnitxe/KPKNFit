package com.example.kpkn.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest

// ─── Routes ─────────────────────────────────────────────────────────────────

sealed class KpknRoute(val route: String) {
    // Bottom nav destinations
    object Home : KpknRoute("home")
    object Training : KpknRoute("training")
    object Nutrition : KpknRoute("nutrition")
    object BodyProgress : KpknRoute("nutrition/body-progress")
    object WikiLab : KpknRoute("wikilab")

    // ─── Detail screens ─────────────────────────────────────────────────

    object ProgramDetail : KpknRoute("program/{programId}?tab={tab}") {
        fun create(programId: String, tab: String? = null): String =
            if (tab == null) "program/$programId" else "program/$programId?tab=$tab"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_TAB = "tab"
        const val TAB_ANALYTICS = "analytics"
    }

    object SessionEditor : KpknRoute("session-editor/{programId}/{sessionId}?weekId={weekId}&macroIndex={macroIndex}&mesoIndex={mesoIndex}&dayOfWeek={dayOfWeek}&configureCompetition={configureCompetition}") {
        fun create(
            programId: String,
            sessionId: String,
            weekId: String? = null,
            macroIndex: Int? = null,
            mesoIndex: Int? = null,
            dayOfWeek: Int? = null,
            configureCompetition: Boolean = false,
        ): String {
            val query = buildList {
                weekId?.let { add("weekId=$it") }
                macroIndex?.let { add("macroIndex=$it") }
                mesoIndex?.let { add("mesoIndex=$it") }
                dayOfWeek?.let { add("dayOfWeek=$it") }
                if (configureCompetition) add("configureCompetition=true")
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
        const val ARG_CONFIGURE_COMPETITION = "configureCompetition"
    }

    object Workout : KpknRoute("workout/{programId}/{sessionId}") {
        fun create(programId: String, sessionId: String) = "workout/$programId/$sessionId"
        const val ARG_PROGRAM_ID = "programId"
        const val ARG_SESSION_ID = "sessionId"
    }

    /** Full-screen catalog. The request itself lives in the previous entry's SavedStateHandle. */
    object ExerciseCatalog : KpknRoute(
        "exerciseCatalog?requestId={requestId}&origin={origin}&selectionMode={selectionMode}&targetExerciseId={targetExerciseId}&initialQuery={initialQuery}",
    ) {
        fun create(request: CatalogLaunchRequest): String = buildString {
            append("exerciseCatalog?requestId=")
            append(Uri.encode(request.requestId))
            append("&origin=")
            append(Uri.encode(request.origin.name))
            append("&selectionMode=")
            append(Uri.encode(request.selectionMode.name))
            request.targetExerciseId?.let {
                append("&targetExerciseId=")
                append(Uri.encode(it))
            }
            if (request.initialQuery.isNotBlank()) {
                append("&initialQuery=")
                append(Uri.encode(request.initialQuery))
            }
        }

        const val ARG_REQUEST_ID = "requestId"
        const val ARG_ORIGIN = "origin"
        const val ARG_SELECTION_MODE = "selectionMode"
        const val ARG_TARGET_EXERCISE_ID = "targetExerciseId"
        const val ARG_INITIAL_QUERY = "initialQuery"
    }

    object Competitions : KpknRoute("competitions")
    object CompetitionDetail : KpknRoute("competition/{competitionId}") {
        fun create(competitionId: String) = "competition/$competitionId"
        const val ARG_COMPETITION_ID = "competitionId"
    }

    object Settings : KpknRoute("settings")
    object SettingsGeneral : KpknRoute("settings/general")
    object SettingsProfile : KpknRoute("settings/profile")
    object SettingsNutrition : KpknRoute("settings/nutrition")
    object SettingsTraining : KpknRoute("settings/training")
    object SettingsAuge : KpknRoute("settings/auge")
    object SettingsNotifications : KpknRoute("settings/notifications")
    object SettingsData : KpknRoute("settings/data")
    object SettingsDiagnostics : KpknRoute("settings/diagnostics")
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
    object MealHistory : KpknRoute("nutrition/meal-history")
    object NutritionAction : KpknRoute("nutrition/action/{action}") {
        fun create(action: String) = "nutrition/action/$action"
        const val ARG_ACTION = "action"
    }

    // ─── WikiLab Concepts ──────────────────────────────────────────────

    object WikiLabConcepts : KpknRoute("wikilab/concepts")
    object WikiLabConceptDetail : KpknRoute("wikilab/concept/{conceptId}") {
        fun create(conceptId: String) = "wikilab/concept/$conceptId"
        const val ARG_CONCEPT_ID = "conceptId"
    }

    // ─── Learn (Cursos) ───────────────────────────────────────────────

    object Learn : KpknRoute("learn")
    object LearnCourse : KpknRoute("learn/course/{courseId}") {
        fun create(courseId: String) = "learn/course/$courseId"
        const val ARG_COURSE_ID = "courseId"
    }
    object LearnReader : KpknRoute("learn/reader/{courseId}/{submoduleIndex}") {
        fun create(courseId: String, submoduleIndex: Int) = "learn/reader/$courseId/$submoduleIndex"
        const val ARG_COURSE_ID = "courseId"
        const val ARG_SUBMODULE_INDEX = "submoduleIndex"
    }
    object LearnQuiz : KpknRoute("learn/quiz/{courseId}/{submoduleIndex}") {
        fun create(courseId: String, submoduleIndex: Int = -1) = "learn/quiz/$courseId/$submoduleIndex"
        const val ARG_COURSE_ID = "courseId"
        const val ARG_SUBMODULE_INDEX = "submoduleIndex"
    }
    object LearnBadge : KpknRoute("learn/badge/{courseId}") {
        fun create(courseId: String) = "learn/badge/$courseId"
        const val ARG_COURSE_ID = "courseId"
    }
    
    // ─── Health Connect ───────────────────────────────────────────────
    object HealthConnect : KpknRoute("settings/health-connect")

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
    BottomNavItem(KpknRoute.BodyProgress.route, "Cuerpo", Icons.Default.Person),
    BottomNavItem(KpknRoute.WikiLab.route, "Aprende", Icons.Default.Info),
)
