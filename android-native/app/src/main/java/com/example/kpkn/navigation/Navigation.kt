package com.example.kpkn.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
    /** Home-hosted knowledge list.  The old wikilab paths are compatibility aliases only. */
    object Concepts : KpknRoute("concepts")
    object ConceptDetail : KpknRoute("concept/{conceptId}") {
        fun create(conceptId: String) = "concept/$conceptId"
        const val ARG_CONCEPT_ID = "conceptId"
    }

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
        "exerciseCatalog?requestId={requestId}&origin={origin}&selectionMode={selectionMode}&targetExerciseId={targetExerciseId}&targetGroupName={targetGroupName}&initialQuery={initialQuery}",
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
            request.targetGroupName?.let {
                append("&targetGroupName=")
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
        const val ARG_TARGET_GROUP_NAME = "targetGroupName"
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

    // ─── Nutrition Sub-screens ────────────────────────────────────────
    object NutritionWizard : KpknRoute("nutrition/wizard?mode={mode}&planId={planId}") {
        const val ARG_MODE = "mode"
        const val ARG_PLAN_ID = "planId"

        fun create(mode: String = "create", planId: String? = null): String = buildString {
            append("nutrition/wizard?mode=")
            append(Uri.encode(mode))
            append("&planId=")
            append(Uri.encode(planId.orEmpty()))
        }

        /** Stable base used by deep links and current-route checks. */
        const val BASE_ROUTE = "nutrition/wizard"
    }
    object NutritionCalibration : KpknRoute("nutrition/calibration")
    object MealHistory : KpknRoute("nutrition/meal-history")
    object NutritionAction : KpknRoute("nutrition/action/{action}") {
        fun create(action: String) = "nutrition/action/$action"
        const val ARG_ACTION = "action"
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
)
