package com.example.kpkn.navigation

import android.net.Uri

object DeepLinkRouter {

    data class ResolvedRoute(
        val route: String,
    )

    fun resolve(uri: Uri?): ResolvedRoute? {
        if (uri == null) return null

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "kpkn" && scheme != "https") return null

        val segments = mutableListOf<String>()

        if (scheme == "https") {
            val host = uri.host?.lowercase().orEmpty()
            if (host != "kpkn.fit" && host != "www.kpkn.fit") return null
            segments += uri.pathSegments
        } else {
            val host = uri.host?.trim().orEmpty()
            if (host.isNotEmpty()) segments += host
            segments += uri.pathSegments
        }

        val cleanSegments = segments
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val first = cleanSegments.getOrNull(0)?.lowercase().orEmpty()
        val second = cleanSegments.getOrNull(1)?.lowercase().orEmpty()
        val third = cleanSegments.getOrNull(2).orEmpty()

        if (first.isEmpty()) {
            return ResolvedRoute(KpknRoute.Home.route)
        }

        return when (first) {
            "home", "inicio" -> ResolvedRoute(KpknRoute.Home.route)
            "training", "entreno" -> ResolvedRoute(KpknRoute.Training.route)
            "competitions", "competencias", "competicion", "competición" -> ResolvedRoute(KpknRoute.Competitions.route)
            "rings", "mis-rings", "my-rings" -> ResolvedRoute(KpknRoute.Home.route)
            "nutrition", "nutricion", "nutrición" -> {
                when (second) {
                    "wizard" -> ResolvedRoute(KpknRoute.NutritionWizard.create())
                    "calibration" -> ResolvedRoute(KpknRoute.NutritionCalibration.route)
                    "body-progress", "bodyprogress", "progress" -> ResolvedRoute(KpknRoute.BodyProgress.route)
                    "meal-history", "history", "historial" -> ResolvedRoute(KpknRoute.MealHistory.route)
                    "action" -> {
                        val action = third.trim()
                        if (action.isNotEmpty()) {
                            ResolvedRoute(KpknRoute.NutritionAction.create(action))
                        } else {
                            ResolvedRoute(KpknRoute.Nutrition.route)
                        }
                    }
                    else -> ResolvedRoute(KpknRoute.Nutrition.route)
                }
            }
            "wikilab" -> {
                when (second) {
                    "", "home", "inicio" -> ResolvedRoute(KpknRoute.WikiLab.route)
                    "exercises" -> ResolvedRoute(KpknRoute.WikiLabExercises.route)
                    "muscles", "muscle-anatomy" -> ResolvedRoute(KpknRoute.WikiLabMuscleAnatomy.route)
                    "joints" -> ResolvedRoute(KpknRoute.WikiLabJoints.route)
                    "patterns" -> ResolvedRoute(KpknRoute.WikiLabMovementPatterns.route)
                    "biomechanics" -> ResolvedRoute(KpknRoute.WikiLabBiomechanics.route)
                    "concepts" -> ResolvedRoute(KpknRoute.WikiLabConcepts.route)
                    "exercise" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabExerciseDetail.create(id)) else null
                    }
                    "muscle" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabMuscleDetail.create(id)) else null
                    }
                    "joint" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabJointDetail.create(id)) else null
                    }
                    "pattern" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabPatternDetail.create(id)) else null
                    }
                    "chain" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabChainDetail.create(id)) else null
                    }
                    "concept" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabConceptDetail.create(id)) else null
                    }
                    else -> ResolvedRoute(KpknRoute.WikiLab.route)
                }
            }
            "learn", "cursos" -> {
                when (second) {
                    "course", "curso" -> {
                        val id = third.trim()
                        if (id.isNotEmpty()) ResolvedRoute(KpknRoute.LearnCourse.create(id)) else ResolvedRoute(KpknRoute.Learn.route)
                    }
                    else -> ResolvedRoute(KpknRoute.Learn.route)
                }
            }
            "settings", "ajustes" -> {
                when (second) {
                    "general" -> ResolvedRoute(KpknRoute.SettingsGeneral.route)
                    "profile", "perfil" -> ResolvedRoute(KpknRoute.SettingsProfile.route)
                    "nutrition", "nutricion", "nutrición" -> ResolvedRoute(KpknRoute.SettingsNutrition.route)
                    "health-connect", "healthconnect", "salud" -> ResolvedRoute(KpknRoute.HealthConnect.route)
                    "training", "entreno" -> ResolvedRoute(KpknRoute.SettingsTraining.route)
                    "auge" -> ResolvedRoute(KpknRoute.SettingsAuge.route)
                    "notifications", "notificaciones" -> ResolvedRoute(KpknRoute.SettingsNotifications.route)
                    "data", "datos" -> ResolvedRoute(KpknRoute.SettingsData.route)
                    "diagnostics", "diagnosticos", "diagnósticos" -> ResolvedRoute(KpknRoute.SettingsDiagnostics.route)
                    else -> ResolvedRoute(KpknRoute.Settings.route)
                }
            }
            "profile", "perfil" -> ResolvedRoute(KpknRoute.Profile.route)
            "program" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.ProgramDetail.create(id)) else null
            }
            "exercise", "ejercicio" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabExerciseDetail.create(id)) else null
            }
            "muscle", "musculo", "músculo" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabMuscleDetail.create(id)) else null
            }
            "joint", "articulacion", "articulación" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabJointDetail.create(id)) else null
            }
            "pattern", "patron", "patrón" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabPatternDetail.create(id)) else null
            }
            "chain", "cadena" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabChainDetail.create(id)) else null
            }
            "concept", "concepto" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.WikiLabConceptDetail.create(id)) else null
            }
            "course", "curso" -> {
                val id = cleanSegments.getOrNull(1).orEmpty().trim()
                if (id.isNotEmpty()) ResolvedRoute(KpknRoute.LearnCourse.create(id)) else null
            }
            "action" -> {
                val action = cleanSegments.getOrNull(1).orEmpty().trim()
                if (action.isNotEmpty()) ResolvedRoute(KpknRoute.NutritionAction.create(action)) else null
            }
            else -> null
        }
    }
}
