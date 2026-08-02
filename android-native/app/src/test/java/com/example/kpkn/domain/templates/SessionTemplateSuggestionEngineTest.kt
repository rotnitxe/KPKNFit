package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateEquipmentBias
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SessionTemplateSuggestionEngineTest {

    companion object {
        private lateinit var exerciseIndexWithAliases: Map<String, ExerciseMuscleInfo>

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            exerciseIndexWithAliases = CatalogV2TestFixture.configurationLookup()
        }
    }

    @Test
    fun ulX4SuggestsDiverseLegFocus() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            templates = SESSION_TEMPLATES_SYSTEM,
            exerciseIndex = exerciseIndexWithAliases,
            prefs = SuggestionPrefs(preferredDifficulty = split.difficulty),
        )
        val legDays = plan.days.filter { it.dayLabel.equals("Pierna", ignoreCase = true) }
        assertTrue(
            "ul_x4 debe tener 2 días Pierna; days=${plan.days.map { it.dayLabel to it.template?.id }}",
            legDays.size == 2,
        )

        val ids = legDays.map { it.template?.id }
        val focuses = legDays.map { it.template?.primaryFocusMuscle }
        assertTrue("Ambos días Pierna deben resolver plantilla; ids=$ids focuses=$focuses", ids.all { it != null })
        assertNotEquals("Los dos días Pierna no deben repetir el mismo template id; ids=$ids", ids[0], ids[1])
        assertFalse(
            "Los dos días Pierna no deben ser ambos Cuádriceps; focuses=$focuses",
            focuses[0]?.equals("Cuádriceps", ignoreCase = true) == true &&
                focuses[1]?.equals("Cuádriceps", ignoreCase = true) == true,
        )
        val focusValues = focuses.filterNotNull()
        val diverse = focusValues.any { it.equals("Isquiosurales", ignoreCase = true) } ||
            focusValues.any { it.equals("Glúteos", ignoreCase = true) } ||
            (focusValues.size == 2 && !focusValues[0].equals(focusValues[1], ignoreCase = true))
        assertTrue("Se espera diversidad cuáds + isquios/glúteo; got $focuses ids=$ids", diverse)
    }

    @Test
    fun beginnerSplitPrefersMachineBias() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            templates = SESSION_TEMPLATES_SYSTEM,
            exerciseIndex = exerciseIndexWithAliases,
            prefs = SuggestionPrefs(preferredDifficulty = Difficulty.PRINCIPIANTE),
        )
        val chosen = plan.days.mapNotNull { it.template }
        assertTrue(chosen.isNotEmpty())
        val beginnerFriendly = chosen.count { template ->
            template.difficulty == Difficulty.PRINCIPIANTE ||
                template.equipmentBias == SessionTemplateEquipmentBias.MACHINE ||
                template.weeklyVolumePolicyId == "beginner_machine"
        }
        assertTrue(
            "Con preferencia PRINCIPIANTE la mayoría debería sesgar a máquina/principiante; got ${chosen.map { it.id }}",
            beginnerFriendly >= (chosen.size + 1) / 2,
        )
    }

    @Test
    fun advancedAvoidsMaxCompoundIntensity() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            templates = SESSION_TEMPLATES_SYSTEM,
            exerciseIndex = exerciseIndexWithAliases,
            prefs = SuggestionPrefs(preferredDifficulty = Difficulty.AVANZADO),
        )
        val legTemplates = plan.days
            .filter { it.dayLabel.equals("Pierna", ignoreCase = true) }
            .mapNotNull { it.template }
        assertTrue(legTemplates.isNotEmpty())
        legTemplates.forEach { template ->
            val report = SessionTemplateQualityRules.audit(template, exerciseIndexWithAliases)
            val advCompound = report.p0.any { it.code == "ADV_COMPOUND_MAX_INTENSITY" }
            assertFalse(
                "Plantilla avanzada sugerida ${template.id} no debería tener P0 ADV_COMPOUND_MAX_INTENSITY",
                advCompound,
            )
        }
    }

    @Test
    fun weeklyDrainWithinSoftBudgetOrWarns() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            templates = SESSION_TEMPLATES_SYSTEM,
            exerciseIndex = exerciseIndexWithAliases,
            prefs = SuggestionPrefs(preferredDifficulty = split.difficulty),
        )
        val caps = RingBudgetPolicy.weeklyWarningCaps()
        val within = plan.weeklyDrain.cns <= caps.cns &&
            plan.weeklyDrain.muscular <= caps.muscular &&
            plan.weeklyDrain.spinal <= caps.spinal
        if (plan.exceedsWeeklyBudget || !within) {
            assertTrue(
                "Si excede presupuesto semanal debe haber warnings o preferir low/SHORT",
                plan.warnings.isNotEmpty() ||
                    plan.days.any { day ->
                        val t = day.template ?: return@any false
                        t.durationClass == SessionTemplateDurationClass.SHORT ||
                            t.id.endsWith("-low") ||
                            t.weeklyVolumePolicyId == "high_freq_low"
                    },
            )
        } else {
            assertFalse(plan.exceedsWeeklyBudget)
        }
    }
}
