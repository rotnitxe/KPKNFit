package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.AmrapEffectTiming
import com.example.kpkn.domain.training.AmrapHit
import com.example.kpkn.domain.training.AmrapProgramPreview
import com.example.kpkn.domain.training.ProgramAutoregulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorPlanAwareTest {
    /** Todas las líneas de la observación del plan, juntas, para buscar un texto en ellas. */
    private fun planLines(context: RelatorContext): String =
        PlanObserver.observe(context).first().lines.joinToString("\n")

    @Test
    fun amrap_line_reads_the_real_table_of_the_recipe() {
        val ctx = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 95.0,
        )
        val lines = planLines(ctx)
        assertTrue(
            lines,
            lines.contains("AMRAP de {ex}: 1 limpia en reserva salvo PR. TM +5\u00A0kg con 4-5 reps; +7,5\u00A0kg con 6+."),
        )
        assertFalse("ya no promete la subida fija de 2,5 kg con 5 reps", lines.contains("≥5 reps"))

        // Otra tabla en la receta, otro texto: lo dice la receta, no el relator.
        val custom = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(fourToFiveKg = 3.0, sixPlusKg = 6.0),
            targetPercentageRm = 95.0,
        )
        assertTrue(planLines(custom).contains("TM +3\u00A0kg con 4-5 reps; +6\u00A0kg con 6+."))

        // Si la tabla solo sube con 6 o más, solo se promete eso.
        val onlySix = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(fourToFiveKg = 0.0, sixPlusKg = 7.5),
            targetPercentageRm = 90.0,
        )
        assertTrue(planLines(onlySix).contains("TM +7,5\u00A0kg con 6+ reps."))
    }

    @Test
    fun amrap_line_does_not_promise_a_tm_change_when_the_amrap_is_too_light_to_move_it() {
        val generic = "AMRAP de {ex}: deja 1 limpia; no caces el fallo si no es el test."
        // Solo un AMRAP desde el 85 % del TM mueve el TM (ProgramAutoregulationEngine).
        val light = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 65.0,
        )
        val lightLines = planLines(light)
        assertTrue(lightLines, lightLines.contains(generic))
        assertFalse(lightLines, lightLines.contains("TM +"))

        val threshold = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 85.0,
        )
        assertTrue(planLines(threshold).contains("TM +5\u00A0kg"))
        val justBelow = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 84.9,
        )
        assertFalse(planLines(justBelow).contains("TM +"))
    }

    @Test
    fun amrap_line_of_a_rep_target_rule_reads_its_percent_per_rep() {
        val ctx = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.RepTargetDrivenTm(),
            targetPercentageRm = 75.0,
        )
        assertTrue(
            planLines(ctx).contains("AMRAP de {ex}: cada rep extra sube el TM 0,5\u00A0%. Si faltan 2 o más, baja 1\u00A0% por rep."),
        )
    }

    @Test
    fun amrap_line_of_any_other_rule_is_the_generic_one() {
        val generic = "AMRAP de {ex}: deja 1 limpia; no caces el fallo si no es el test."
        listOf(
            ProgressionRule.CycleIncrement(2.5, 5.0),
            ProgressionRule.TopSetPr(),
            ProgressionRule.None,
            null,
        ).forEach { rule ->
            val ctx = relatorContext(
                protocolId = null,
                protocolName = null,
                isAmrap = true,
                progression = rule,
                targetPercentageRm = 95.0,
            )
            assertTrue("${rule?.let { it::class.simpleName }}", planLines(ctx).contains(generic))
        }
    }

    @Test
    fun juggernaut_line_matches_preview_for_the_just_logged_hit_and_defers_the_tm_change() {
        val recipe = TrainingPlanRecipe(
            id = "juggernaut-2",
            weeks = emptyList(),
            progression = ProgressionRule.RepsOverStandardTm(),
        )
        val profile = PowerliftingProfile(squat1RM = 167.0, squatTM = 150.0)
        val hit = AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = 13, weightKg = 127.5)
        val preview = AmrapProgramPreview.preview(recipe, AutoregulationMode.OFF, profile, hit)
        val context = relatorContext(protocolId = null, protocolName = null, isAmrap = false).copy(
            plan = RelatorPlanSlice(
                progression = recipe.progression,
                autoregulationMode = AutoregulationMode.OFF,
                progressionContext = RelatorProgressionContext(
                    recipe = recipe,
                    profile = profile,
                    hit = hit,
                    liftSlot = LiftSlot.SQUAT,
                    slotRole = SlotRole.T1_MAIN,
                    purpose = SetPurpose.AMRAP,
                ),
            ),
        )

        val lines = planLines(context)
        assertTrue(lines, lines.contains(preview.observerNote!!.text))
        assertTrue(lines, lines.contains("TM 170 kg"))
        assertTrue(lines, lines.contains("cerrar la ola"))
        assertFalse(lines, lines.contains("ya cambió"))
    }

    @Test
    fun gzclp_t2_gets_a_conditional_stage_note_even_without_amrap_or_close_evidence() {
        val recipe = TrainingPlanRecipe(
            id = "gzclp",
            weeks = emptyList(),
            progression = ProgressionRule.StagedVolume(),
        )
        val context = relatorContext(protocolId = null, protocolName = null, isAmrap = false).copy(
            slotRole = SlotRole.T2_SUPPLEMENTAL,
            plan = RelatorPlanSlice(
                progression = recipe.progression,
                autoregulationMode = AutoregulationMode.AUTO,
                progressionContext = RelatorProgressionContext(
                    recipe = recipe,
                    slotRole = SlotRole.T2_SUPPLEMENTAL,
                    liftSlot = LiftSlot.SQUAT,
                    plannedVolumeReps = 30,
                    purpose = SetPurpose.NONE,
                    tierCheck = null,
                ),
            ),
        )

        val lines = planLines(context)
        val note = lines.lineSequence().single { it.startsWith("GZCLP T2") }
        assertTrue(lines, lines.contains("GZCLP T2 E1"))
        assertTrue(lines, lines.contains("30 reps"))
        assertTrue(lines, lines.contains("3×8"))
        assertTrue(note, note.contains("pasa: sigue 3×10"))
        assertTrue(note, note.contains("falla: 3×8 misma carga al cierre del ciclo"))
        assertTrue(note, note.length <= 90)
        assertFalse(lines, lines.contains("completó"))
    }

    @Test
    fun gzclp_uses_aggregate_tier_check_instead_of_the_last_amrap_to_claim_a_stage_change() {
        val recipe = TrainingPlanRecipe(
            id = "gzclp",
            weeks = emptyList(),
            progression = ProgressionRule.StagedVolume(),
        )
        val check = ProgramAutoregulationEngine.GzclpTierCheck(
            liftSlot = LiftSlot.SQUAT,
            role = SlotRole.T1_MAIN,
            plannedReps = 15,
            doneReps = 12,
            lastSetReps = 7,
        )
        val context = relatorContext(protocolId = null, protocolName = null, isAmrap = true).copy(
            slotRole = SlotRole.T1_MAIN,
            plan = RelatorPlanSlice(
                progression = recipe.progression,
                progressionContext = RelatorProgressionContext(
                    recipe = recipe,
                    liftSlot = LiftSlot.SQUAT,
                    slotRole = SlotRole.T1_MAIN,
                    plannedVolumeReps = 15,
                    purpose = SetPurpose.AMRAP,
                    tierCheck = check,
                ),
            ),
        )

        val lines = planLines(context)
        assertTrue(lines, lines.contains("12/15 reps"))
        assertTrue(lines, lines.contains("6×2"))
        assertTrue(lines, lines.contains("igual carga al cerrar"))
        assertFalse(lines, lines.contains("TM +5 kg"))
    }

    @Test
    fun apre_line_matches_preview_and_ordinary_amrap_is_not_labeled_as_apre() {
        val recipe = TrainingPlanRecipe(
            id = "apre-mann",
            weeks = emptyList(),
            progression = ProgressionRule.ApreXrm(),
        )
        val profile = PowerliftingProfile(squat1RM = 200.0, squatTM = 180.0)
        val hit = AmrapHit(
            LiftSlot.SQUAT,
            81.52,
            prescribedReps = 10,
            actualReps = 17,
            weightKg = 155.0,
            purpose = SetPurpose.AMRAP,
            isLastAmrapOfExercise = true,
        )
        val preview = AmrapProgramPreview.preview(recipe, AutoregulationMode.PROPOSE, profile, hit)
        val inBlock = relatorContext(protocolId = null, protocolName = null, isAmrap = false).copy(
            plan = RelatorPlanSlice(
                progression = recipe.progression,
                autoregulationMode = AutoregulationMode.PROPOSE,
                progressionContext = RelatorProgressionContext(
                    recipe = recipe,
                    profile = profile,
                    hit = hit,
                    liftSlot = LiftSlot.SQUAT,
                    slotRole = SlotRole.T1_MAIN,
                    purpose = SetPurpose.AMRAP,
                    plannedMinimumReps = 10,
                    isApreBlock = true,
                    isLastAmrapOfExercise = true,
                ),
            ),
        )
        val ordinaryAmrap = inBlock.copy(
            isAmrap = true,
            plan = inBlock.plan.copy(
                progressionContext = inBlock.plan.progressionContext!!.copy(isApreBlock = false),
            ),
        )

        val lines = planLines(inBlock)
        assertTrue(lines, lines.contains(preview.observerNote!!.text))
        assertTrue(lines, lines.contains("162,5 kg"))
        assertTrue(lines, lines.contains("propone"))
        val genericLines = planLines(ordinaryAmrap)
        assertTrue(genericLines, genericLines.contains("AMRAP de {ex}: deja 1 limpia"))
        assertFalse(genericLines, genericLines.contains("APRE 10"))

        val plannedS4 = inBlock.copy(
            plan = inBlock.plan.copy(
                progressionContext = inBlock.plan.progressionContext!!.copy(hit = null),
            ),
        )
        val plannedLines = planLines(plannedS4)
        assertTrue(plannedLines, plannedLines.contains("Al completar S4"))
        assertFalse(plannedLines, plannedLines.contains("ya cambió"))
    }

    @Test
    fun cycle_increment_waits_for_close_and_unknown_frozen_streak_does_not_invent_a_reset() {
        val recipe = TrainingPlanRecipe(
            id = "wendler-531-bbb",
            weeks = emptyList(),
            progression = ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 5.0),
        )
        val short = AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = 3, weightKg = 100.0)
        val effect = AmrapProgramPreview.preview(recipe, AutoregulationMode.AUTO, null, short, frozenCyclesBefore = null)
        assertTrue(effect.observerNote!!.text, effect.observerNote!!.text.contains("TM congelado al cerrar el ciclo"))
        assertFalse(effect.warning.orEmpty(), effect.warning.orEmpty().contains("10 %"))

        val context = relatorContext(protocolId = null, protocolName = null, isAmrap = true).copy(
            plan = RelatorPlanSlice(
                progression = recipe.progression,
                progressionContext = RelatorProgressionContext(
                    recipe = recipe,
                    hit = short,
                    liftSlot = LiftSlot.SQUAT,
                    purpose = SetPurpose.AMRAP,
                    frozenCyclesBefore = null,
                ),
            ),
        )
        val lines = planLines(context)
        assertTrue(lines, lines.contains("TM congelado al cerrar el ciclo"))
        assertFalse(lines, lines.contains("se reinicia"))

        val upper = AmrapProgramPreview.preview(
            recipe,
            AutoregulationMode.PROPOSE,
            null,
            AmrapHit(LiftSlot.BENCH, 85.0, prescribedReps = 5, actualReps = 8, weightKg = 100.0),
        )
        assertTrue(upper.observerNote!!.text, upper.observerNote!!.text.contains("TM +2,5 kg al cerrar el ciclo"))
        assertEquals(AmrapEffectTiming.END_OF_CYCLE, upper.timing)

        val blockRecipe = recipe.copy(
            id = "custom-cycle-block",
            progression = ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK),
        )
        val block = AmrapProgramPreview.preview(
            blockRecipe,
            AutoregulationMode.PROPOSE,
            null,
            AmrapHit(LiftSlot.SQUAT, 85.0, prescribedReps = 5, actualReps = 8, weightKg = 100.0),
        )
        assertEquals(AmrapEffectTiming.END_OF_BLOCK, block.timing)
        assertTrue(block.headline, block.headline.contains("cerrar el bloque"))
        assertTrue(block.observerNote!!.text, block.observerNote!!.text.contains("al cerrar el bloque"))
    }

    @Test
    fun wendler_531_amrap_mentions_amrap_and_tm() {
        val ctx = relatorContext(
            protocolId = "wendler-531-bbb",
            protocolName = "5/3/1 BBB",
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            slotRole = SlotRole.T1_MAIN,
            targetPercentageRm = 85.0,
            prescribedWeightKg = 102.5,
        )
        val lines = PlanObserver.observe(ctx).first().lines.joinToString(" ").lowercase()
        assertTrue(lines.contains("amrap"))
        assertTrue(lines.contains("tm") || lines.contains("5/3/1"))
    }

    @Test
    fun smolov_speaks_percent_1rm_volume() {
        val ctx = relatorContext(
            protocolId = "smolov-jr",
            protocolName = "Smolov Jr",
            isAmrap = false,
            isTopSet = false,
            loadBasis = LoadBasis.PERCENT_1RM,
            targetPercentageRm = 70.0,
            progression = ProgressionRule.WeeklyPercent(5.0),
            blockGoal = BlockGoal.ACCUMULATION,
        )
        val lines = PlanObserver.observe(ctx).first().lines.joinToString(" ").lowercase()
        assertTrue(lines.contains("smolov"))
        assertTrue(lines.contains("1rm") || lines.contains("%"))
    }

    @Test
    fun westside_me_speaks_max_effort() {
        val ctx = relatorContext(
            protocolId = "westside-conjugate",
            protocolName = "Westside Conjugate",
            isTopSet = true,
            loadBasis = LoadBasis.REP_MAX,
            isAmrap = false,
            progression = ProgressionRule.RepMaxAutoregulated,
            slotRole = SlotRole.T1_MAIN,
        )
        val lines = PlanObserver.observe(ctx).first().lines.joinToString(" ").lowercase()
        assertTrue(lines.contains("westside") || lines.contains("top set") || lines.contains("rm"))
    }

    @Test
    fun kpkn_native_without_protocol_still_uses_slot_role() {
        val ctx = relatorContext(
            protocolId = null,
            protocolName = null,
            slotRole = SlotRole.T1_MAIN,
            isAmrap = false,
            progression = null,
        )
        val lines = PlanObserver.observe(ctx).first().lines.joinToString(" ").lowercase()
        assertTrue(lines.contains("t1"))
        assertTrue(!lines.contains("5/3/1") && !lines.contains("smolov") && !lines.contains("westside"))
    }
}

class RelatorRestIndependenceTest {
    @Test
    fun rest_observer_ignores_overlay_minimized() {
        val expanded = relatorContext(phase = RelatorSessionPhase.REST, restActive = true)
            .let { it.copy(rest = it.rest.copy(overlayMinimized = false)) }
        val minimized = expanded.copy(rest = expanded.rest.copy(overlayMinimized = true))
        val a = RestObserver.observe(expanded).first()
        val b = RestObserver.observe(minimized).first()
        assertTrue(a.lines == b.lines)
        assertTrue(a.actions.map { it.kind } == b.actions.map { it.kind })
    }
}
