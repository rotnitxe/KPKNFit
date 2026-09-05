package com.example.kpkn.navigation

import android.net.Uri
import com.example.kpkn.domain.concepts.findConceptoClave

/**
 * Deep-link compatibility boundary. Only former conceptual WikiLab links
 * survive; every other WikiLab/Learn link redirects to Home.
 */
object DeepLinkRouter {

    data class ResolvedRoute(val route: String)

    private fun conceptRoute(id: String?): ResolvedRoute {
        val cleanId = id?.trim().orEmpty()
        return if (cleanId.isNotEmpty() && findConceptoClave(cleanId) != null) {
            ResolvedRoute(KpknRoute.Concepts.create(cleanId))
        } else {
            ResolvedRoute(KpknRoute.Concepts.create())
        }
    }

    fun resolve(uri: Uri?): ResolvedRoute? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "kpkn" && scheme != "https") return null

        val segments = buildList {
            if (scheme == "https") {
                val host = uri.host?.lowercase().orEmpty()
                if (host != "kpkn.fit" && host != "www.kpkn.fit") return null
            } else {
                uri.host?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            addAll(uri.pathSegments)
        }.map(String::trim).filter(String::isNotEmpty)

        val first = segments.getOrNull(0)?.lowercase().orEmpty()
        val second = segments.getOrNull(1)?.lowercase().orEmpty()
        val third = segments.getOrNull(2)

        if (first.isEmpty()) return ResolvedRoute(KpknRoute.Home.route)

        return when (first) {
            "home", "inicio", "rings", "mis-rings", "my-rings" -> ResolvedRoute(KpknRoute.Home.route)
            "training", "entreno" -> ResolvedRoute(KpknRoute.Training.route)
            "competitions", "competencias", "competicion", "competición" -> ResolvedRoute(KpknRoute.Profile.route)
            "competition" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { ResolvedRoute(KpknRoute.CompetitionDetail.create(it)) }
                ?: ResolvedRoute(KpknRoute.Profile.route)
            "nutrition", "nutricion", "nutrición" -> resolveNutrition(second, third)
            "settings", "ajustes" -> resolveSettings(second)
            "profile", "perfil" -> ResolvedRoute(KpknRoute.Profile.route)
            "program" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { ResolvedRoute(KpknRoute.ProgramDetail.create(it)) }
            "workout", "entreno-vivo", "sesion-viva" -> {
                val programId = segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                val sessionId = segments.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (programId != null && sessionId != null) {
                    ResolvedRoute(KpknRoute.Workout.create(programId, sessionId))
                } else {
                    ResolvedRoute(KpknRoute.Training.route)
                }
            }
            "concepts", "conceptos" -> ResolvedRoute(KpknRoute.Concepts.create())
            "concept", "concepto" -> conceptRoute(segments.getOrNull(1))
            "wikilab" -> when (second) {
                "concepts" -> ResolvedRoute(KpknRoute.Concepts.create())
                "concept" -> conceptRoute(third)
                else -> ResolvedRoute(KpknRoute.Home.route)
            }
            // Courses and quizzes are retired. Keep old links harmless.
            "learn", "cursos", "course", "curso" -> ResolvedRoute(KpknRoute.Home.route)
            // Former atlas aliases are also retired.
            "exercise", "ejercicio", "muscle", "musculo", "músculo",
            "joint", "articulacion", "articulación", "pattern", "patron", "patrón",
            "chain", "cadena", "action" -> ResolvedRoute(KpknRoute.Home.route)
            else -> null
        }
    }

    private fun resolveNutrition(second: String, third: String?): ResolvedRoute = when (second) {
        "wizard" -> ResolvedRoute(KpknRoute.NutritionWizard.create())
        "calibration" -> ResolvedRoute(KpknRoute.NutritionCalibration.route)
        "body-progress", "bodyprogress", "progress" -> ResolvedRoute(KpknRoute.BodyProgress.route)
        "meal-history", "history", "historial" -> ResolvedRoute(KpknRoute.MealHistory.route)
        "action" -> third?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ResolvedRoute(KpknRoute.NutritionAction.create(it)) }
            ?: ResolvedRoute(KpknRoute.Nutrition.route)
        else -> ResolvedRoute(KpknRoute.Nutrition.route)
    }

    private fun resolveSettings(second: String): ResolvedRoute = when (second) {
        "profile", "perfil" -> ResolvedRoute(KpknRoute.Profile.route)
        "health-connect", "healthconnect", "salud" -> ResolvedRoute(KpknRoute.HealthConnect.route)
        else -> ResolvedRoute(KpknRoute.Settings.route)
    }
}
