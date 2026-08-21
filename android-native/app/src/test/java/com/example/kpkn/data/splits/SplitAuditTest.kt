package com.example.kpkn.data.splits

import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SessionTemplateSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Invariantes de [SPLIT_TEMPLATES] + mapeo día→plantilla (Fase A).
 */
class SplitAuditTest {

    companion object {
        private lateinit var index: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            index = CatalogV2TestFixture.configurationLookup()
        }

        /** Agrupa etiquetas de día en buckets musculares gruesos para frecuencia semanal. */
        fun muscleBucket(dayLabel: String): String {
            val n = dayLabel.lowercase()
            return when {
                n.contains("descanso") -> "rest"
                n.contains("pecho") || n.contains("bench") || n.contains("banca") ||
                    n.contains("empuje") || n.contains("push") || n.contains("me upper") ||
                    n.contains("de upper") -> "push"
                n.contains("espalda") || n.contains("tirón") || n.contains("tiron") ||
                    n.contains("pull") || n.contains("remo") -> "pull"
                n.contains("pierna") || n.contains("lower") || n.contains("sentadilla") ||
                    n.contains("squat") || n.contains("glúteo") || n.contains("gluteo") ||
                    n.contains("cuádriceps") || n.contains("cuadriceps") ||
                    n.contains("isquio") || n.contains("me lower") || n.contains("de lower") ||
                    n.contains("sbd") -> "legs"
                n.contains("hombro") || n.contains("brazo") || n.contains("militar") ||
                    n.contains("torso") || n == "upper" -> "upper"
                n.contains("full") || n.contains("cuerpo completo") || n.contains("sbd") -> "full"
                else -> "other"
            }
        }
    }

    @Test
    fun everySplitHasValidSevenDayPattern() {
        val failures = mutableListOf<String>()
        SPLIT_TEMPLATES.filterNot { it.id == "custom" }.forEach { split ->
            if (split.pattern.size != 7) {
                failures += "${split.id}: pattern size=${split.pattern.size} (esperado 7)"
            }
            if (split.pattern.all { it.equals("Descanso", ignoreCase = true) }) {
                failures += "${split.id}: todos los días son Descanso"
            }
            val trainingDays = split.pattern.count { !it.equals("Descanso", ignoreCase = true) }
            when (split.difficulty) {
                Difficulty.PRINCIPIANTE -> if (trainingDays > 5) {
                    failures += "${split.id}: PRINCIPIANTE con $trainingDays días de entrenamiento"
                }
                Difficulty.INTERMEDIO -> if (trainingDays > 6) {
                    failures += "${split.id}: INTERMEDIO con $trainingDays días"
                }
                Difficulty.AVANZADO -> Unit
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun uniqueSplitIds() {
        val ids = SPLIT_TEMPLATES.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun weeklyFrequencyAndNoTripleSameBucket() {
        val failures = mutableListOf<String>()
        // Splits que intencionalmente repiten el mismo estímulo (alta frecuencia / especialización).
        val consecutiveWhitelist = setOf(
            "fullbody_x5",
            "bulgarian_lite",
            "ant_post_x6",
            "ul_x6",
            "smolov_base",
        )
        SPLIT_TEMPLATES.filterNot { it.id == "custom" }.forEach { split ->
            var streak = 1
            for (i in 1 until split.pattern.size) {
                val prev = muscleBucket(split.pattern[i - 1])
                val cur = muscleBucket(split.pattern[i])
                if (prev != "rest" && cur != "rest" && prev == cur) {
                    streak++
                    if (streak >= 3 && split.id !in consecutiveWhitelist) {
                        failures += "${split.id}: ≥3 días consecutivos de '$cur' (${split.pattern})"
                        break
                    }
                } else {
                    streak = 1
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun suggestWeekMapsNormalDaysAndFailsClosedForUnverifiedSpecializedRecipes() {
        val failures = mutableListOf<String>()
        SPLIT_TEMPLATES.filterNot { it.id == "custom" }.forEach { split ->
            // Sin índice: no filtra por P0 de calidad (el gate P0 vive en SessionTemplateAuditTest /
            // SessionTemplateQualityRulesTest). Aquí validamos cobertura de mapeo día→candidatas.
            val plan = SessionTemplateSuggestionEngine.suggestWeek(
                split = split,
                templates = SESSION_TEMPLATES_SYSTEM,
                exerciseIndex = emptyMap(),
            )
            val trainingDayCount = split.pattern.count { !it.equals("Descanso", ignoreCase = true) }
            assertEquals(
                "Días sugeridos != training days en ${split.id}",
                trainingDayCount,
                plan.days.size,
            )
            plan.days.forEach { day ->
                if (day.template == null) {
                    if (SplitTag.POWERLIFTING !in split.tags) {
                        failures += "${split.id} day '${day.dayLabel}': sin plantilla (${day.unavailabilityReason})"
                    } else {
                        val candidates = SessionTemplateCatalogPolicy.templatesForSplitDay(
                            split.id,
                            day.dayLabel,
                            SESSION_TEMPLATES_SYSTEM,
                        )
                        assertTrue(
                            "${split.id}/${day.dayLabel} debe explicar por qué no se publica",
                            !day.unavailabilityReason.isNullOrBlank(),
                        )
                        assertTrue(
                            "${split.id}/${day.dayLabel} no puede caer a una receta genérica",
                            candidates.isEmpty(),
                        )
                    }
                } else {
                    val candidates = SessionTemplateCatalogPolicy.templatesForSplitDay(
                        split.id,
                        day.dayLabel,
                        SESSION_TEMPLATES_SYSTEM,
                    )
                    if (candidates.none { it.id == day.template.id }) {
                        failures += "${split.id} day '${day.dayLabel}': plantilla ${day.template.id} no está en candidates del día"
                    }
                    if (SplitTag.POWERLIFTING in split.tags &&
                        candidates.any { !SessionTemplateCatalogPolicy.isPowerliftingTemplate(it) }
                    ) {
                        failures += "${split.id} day '${day.dayLabel}': receta especializada no-powerlifting"
                    }
                }
            }
        }
        assertTrue(
            "Mapeo día→plantilla fallido:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun highToleranceSplitsCarryWarningCons() {
        val risky = listOf("smolov_base", "bulgarian_lite", "fullbody_x5", "ppl_x6")
        risky.forEach { id ->
            val split = SPLIT_TEMPLATES.firstOrNull { it.id == id } ?: return@forEach
            assertTrue(
                "$id debe llevar ALTA_TOLERANCIA",
                split.tags.contains(SplitTag.ALTA_TOLERANCIA) || split.tags.contains(SplitTag.POWERLIFTING),
            )
            assertTrue("$id debe tener cons disuasorios", split.cons.isNotEmpty())
        }
    }

    @Test
    fun unverified_branded_splits_are_not_publicly_selectable() {
        val hiddenIds = setOf(
            "texas_method", "smolov_base", "sheiko_3day", "bulgarian_lite",
            "westside_conjugate", "korte_3x3", "deathbench",
        )
        assertTrue(hiddenIds.all { id -> SPLIT_TEMPLATES.none { it.id == id && it.isVisibleForApplication } })
        assertTrue(SPLIT_TEMPLATES.any { it.id == "pl_sbd_x3" && it.isVisibleForApplication })
        assertTrue(SPLIT_TEMPLATES.any { it.id == "pl_classic_4" && it.isVisibleForApplication })
    }

    @Test
    fun visible_powerlifting_splits_have_exact_day_recipes_and_competition_lifts() {
        listOf("pl_sbd_x3", "pl_classic_4").forEach { splitId ->
            val split = SPLIT_TEMPLATES.first { it.id == splitId }
            split.pattern.filterNot { it.equals("Descanso", true) }.forEach { dayLabel ->
                val recipes = SessionTemplateCatalogPolicy.templatesForSplitDay(
                    splitId,
                    dayLabel,
                    SESSION_TEMPLATES_SYSTEM,
                )
                assertTrue("$splitId/$dayLabel debe tener receta exacta", recipes.isNotEmpty())
                val recipe = recipes.first()
                val main = recipe.session.allExercises().first()
                assertTrue("$splitId/$dayLabel requiere lift de competición", main.isCompetitionLift)
                assertTrue("$splitId/$dayLabel no puede exponer Smith", recipe.session.allExercises().none { it.exerciseDbId?.contains("smith", true) == true })
                assertTrue(
                    "$splitId/$dayLabel principal requiere descanso >=180s",
                    (main.restTime ?: 0) >= 180,
                )
            }
        }
    }

    @Test
    fun powerlifting_template_difficulty_matches_published_split_contract() {
        val classic = SESSION_TEMPLATES_SYSTEM.filter { "pl_classic_4" in it.splitIds }
        val sbd = SESSION_TEMPLATES_SYSTEM.filter { "pl_sbd_x3" in it.splitIds }
        assertEquals("Clásico 4 debe exponer sus cuatro recetas", 4, classic.size)
        assertTrue("Clásico 4 es un contrato intermedio", classic.all { it.difficulty == Difficulty.INTERMEDIO })
        assertEquals("SBD x3 debe conservar el contrato avanzado", 3, sbd.size)
        assertTrue("SBD x3 es avanzado", sbd.all { it.difficulty == Difficulty.AVANZADO })
    }
}
