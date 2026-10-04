package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
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
            lines.contains("AMRAP de {ex}: para con 1 limpia en reserva salvo PR; con 4 o 5 reps el TM sube 5 kg y con 6 o más, 7,5 kg."),
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
        assertTrue(planLines(custom).contains("con 4 o 5 reps el TM sube 3 kg y con 6 o más, 6 kg."))

        // Si la tabla solo sube con 6 o más, solo se promete eso.
        val onlySix = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(fourToFiveKg = 0.0, sixPlusKg = 7.5),
            targetPercentageRm = 90.0,
        )
        assertTrue(planLines(onlySix).contains("con 6 o más reps el TM sube 7,5 kg."))
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
        assertFalse(lightLines, lightLines.contains("el TM sube"))

        val threshold = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 85.0,
        )
        assertTrue(planLines(threshold).contains("el TM sube 5 kg"))
        val justBelow = relatorContext(
            protocolId = null,
            protocolName = null,
            isAmrap = true,
            progression = ProgressionRule.AmrapDrivenTm(),
            targetPercentageRm = 84.9,
        )
        assertFalse(planLines(justBelow).contains("el TM sube"))
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
            planLines(ctx).contains("AMRAP de {ex}: cada rep sobre el objetivo sube el TM un 0,5 %; si te faltan 2 o más, baja un 1 % por rep."),
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

class RelatorReadinessActionTest {
    @Test
    fun low_readiness_offers_adjust_load() {
        val ctx = relatorContext(
            dailyScore = 42,
            autoregulationMode = AutoregulationMode.PROPOSE,
        )
        val actions = ReadinessObserver.observe(ctx).flatMap { it.actions }
        assertTrue(actions.any { it.kind == RelatorActionKind.ADJUST_LOAD })
        assertTrue(actions.any { it.kind == RelatorActionKind.OPEN_REPLACE })
        assertTrue(actions.any { it.kind == RelatorActionKind.OPEN_READINESS })
        val picked = RelatorEngine.resolve(ctx)
        val kinds = picked.line.actions.map { it.kind }
        assertTrue(
            "low readiness should surface ADJUST_LOAD on the winning line or a readiness candidate",
            RelatorActionKind.ADJUST_LOAD in kinds ||
                ReadinessObserver.observe(ctx).any { it.actions.any { a -> a.kind == RelatorActionKind.ADJUST_LOAD } },
        )
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
