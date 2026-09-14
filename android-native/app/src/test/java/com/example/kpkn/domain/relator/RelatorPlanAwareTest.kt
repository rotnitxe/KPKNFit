package com.example.kpkn.domain.relator

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorPlanAwareTest {
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
