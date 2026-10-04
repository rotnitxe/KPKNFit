package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B.S3: reglas puras del motor de progresión de autor: kilos semanales de `WeeklyKg`, subida de TM
 * de `CycleIncrement` por levantamiento, alcance (ciclo o bloque), redondeo por inventario y texto
 * del aviso. La aplicación al cerrar ciclo o bloque la cubren `ProgramProgressCycleCloseTest` y
 * `ProgramProgressEngineTest`.
 */
class AuthoredProgressionEngineTest {

    private val weekly = ProgressionRule.WeeklyKg(mapOf(2 to 5.0, 3 to 10.0))
    private val increment = ProgressionRule.CycleIncrement(upperKg = 2.5, lowerKg = 5.0)

    // ─── WeeklyKg ──────────────────────────────────────────────────────────────────

    @Test
    fun weekly_offset_follows_the_week_map_for_the_main_work_of_a_declared_lift() {
        assertNull("semana 1 no está en el mapa", AuthoredProgressionEngine.weeklyOffsetKg(weekly, 1, LiftSlot.SQUAT, SlotRole.T1_MAIN))
        assertEquals(5.0, AuthoredProgressionEngine.weeklyOffsetKg(weekly, 2, LiftSlot.SQUAT, SlotRole.T1_MAIN)!!, 0.0)
        assertEquals(10.0, AuthoredProgressionEngine.weeklyOffsetKg(weekly, 3, LiftSlot.SQUAT, SlotRole.T1_MAIN)!!, 0.0)
        assertNull("semana 4 no está en el mapa", AuthoredProgressionEngine.weeklyOffsetKg(weekly, 4, LiftSlot.SQUAT, SlotRole.T1_MAIN))
        // El mapa no nombra el levantamiento: aplica al que declare el slot principal.
        assertEquals(5.0, AuthoredProgressionEngine.weeklyOffsetKg(weekly, 2, LiftSlot.BENCH, SlotRole.T1_MAIN)!!, 0.0)
    }

    @Test
    fun weekly_offset_is_null_without_a_lift_slot_or_outside_the_main_role() {
        assertNull("sin liftSlot", AuthoredProgressionEngine.weeklyOffsetKg(weekly, 2, null, SlotRole.T1_MAIN))
        listOf(SlotRole.T2_SUPPLEMENTAL, SlotRole.T3_ACCESSORY, SlotRole.SPEED, SlotRole.TECHNIQUE).forEach { role ->
            assertNull("$role", AuthoredProgressionEngine.weeklyOffsetKg(weekly, 2, LiftSlot.SQUAT, role))
        }
    }

    @Test
    fun weekly_offset_only_exists_for_the_weekly_kg_rule() {
        listOf(
            ProgressionRule.None,
            increment,
            ProgressionRule.WeeklyPercent(2.5),
            ProgressionRule.TopSetPr(),
            ProgressionRule.RepMaxAutoregulated,
            ProgressionRule.AmrapDrivenTm(),
            ProgressionRule.RepTargetDrivenTm(),
        ).forEach { rule ->
            assertNull("${rule::class.simpleName}", AuthoredProgressionEngine.weeklyOffsetKg(rule, 2, LiftSlot.SQUAT, SlotRole.T1_MAIN))
        }
    }

    // ─── CycleIncrement ────────────────────────────────────────────────────────────

    @Test
    fun cycle_increment_is_the_upper_value_for_bench_and_overhead_and_the_lower_for_squat_and_deadlift() {
        assertEquals(2.5, AuthoredProgressionEngine.cycleIncrementKg(increment, LiftSlot.BENCH)!!, 0.0)
        assertEquals(2.5, AuthoredProgressionEngine.cycleIncrementKg(increment, LiftSlot.OVERHEAD)!!, 0.0)
        assertEquals(5.0, AuthoredProgressionEngine.cycleIncrementKg(increment, LiftSlot.SQUAT)!!, 0.0)
        assertEquals(5.0, AuthoredProgressionEngine.cycleIncrementKg(increment, LiftSlot.DEADLIFT)!!, 0.0)
    }

    @Test
    fun cycle_increment_is_null_for_every_other_rule() {
        listOf(ProgressionRule.None, weekly, ProgressionRule.TopSetPr(), ProgressionRule.WeeklyPercent(2.5)).forEach { rule ->
            LiftSlot.entries.forEach { lift ->
                assertNull("${rule::class.simpleName}/$lift", AuthoredProgressionEngine.cycleIncrementKg(rule, lift))
            }
        }
    }

    @Test
    fun scope_decides_whether_the_rule_applies_at_cycle_close_or_at_block_close() {
        val byCycle = ProgressionRule.CycleIncrement(2.5, 5.0)
        val byBlock = ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK)
        assertEquals("el alcance por defecto es el ciclo", IncrementScope.CYCLE, byCycle.scope)
        assertTrue(AuthoredProgressionEngine.appliesAtCycleClose(byCycle))
        assertFalse(AuthoredProgressionEngine.appliesAtBlockClose(byCycle))
        assertFalse(AuthoredProgressionEngine.appliesAtCycleClose(byBlock))
        assertTrue(AuthoredProgressionEngine.appliesAtBlockClose(byBlock))
        listOf(ProgressionRule.None, weekly, ProgressionRule.TopSetPr(), null).forEach { rule ->
            assertFalse("${rule?.let { it::class.simpleName }} ciclo", AuthoredProgressionEngine.appliesAtCycleClose(rule))
            assertFalse("${rule?.let { it::class.simpleName }} bloque", AuthoredProgressionEngine.appliesAtBlockClose(rule))
        }
    }

    // ─── Redondeo por inventario ───────────────────────────────────────────────────

    @Test
    fun the_rounding_step_is_twice_the_smallest_plate_and_half_a_kilo_without_inventory() {
        assertEquals(0.5, AuthoredProgressionEngine.roundingStepKg(null), 0.0)
        assertEquals(0.5, AuthoredProgressionEngine.roundingStepKg(EquipmentInventory()), 0.0)
        val common = EquipmentInventory(
            plates = listOf(PlateStock(20.0, 2), PlateStock(1.25, 2), PlateStock(5.0, 2), PlateStock(2.5, 2)),
        )
        assertEquals(2.5, AuthoredProgressionEngine.roundingStepKg(common), 0.0)
        val fine = EquipmentInventory(plates = listOf(PlateStock(0.25, null), PlateStock(5.0, 4)))
        assertEquals(0.5, AuthoredProgressionEngine.roundingStepKg(fine), 0.0)
        // Un disco sin unidades no existe: no marca el paso.
        val ghost = EquipmentInventory(plates = listOf(PlateStock(0.5, 0), PlateStock(2.5, 2)))
        assertEquals(5.0, AuthoredProgressionEngine.roundingStepKg(ghost), 0.0)
    }

    @Test
    fun the_next_tm_rounds_to_the_step_and_never_goes_below_the_current_one() {
        assertEquals(185.0, AuthoredProgressionEngine.incrementedTm(180.0, 5.0, 0.5), 0.0)
        assertEquals(110.5, AuthoredProgressionEngine.incrementedTm(108.0, 2.5, 0.5), 0.0)
        // Con discos de 1,25 kg el paso es 2,5: 110,5 se redondea al múltiplo más cercano.
        assertEquals(110.0, AuthoredProgressionEngine.incrementedTm(108.0, 2.5, 2.5), 0.0)
        assertEquals(202.5, AuthoredProgressionEngine.incrementedTm(198.0, 5.0, 2.5), 0.0)
        // Nunca baja del TM actual aunque el redondeo caiga por debajo.
        assertEquals(110.9, AuthoredProgressionEngine.incrementedTm(110.9, 0.2, 2.5), 0.0)
        // Sin paso válido no se redondea.
        assertEquals(103.3, AuthoredProgressionEngine.incrementedTm(100.8, 2.5, 0.0), 1e-9)
    }

    // ─── Cambios de TM ─────────────────────────────────────────────────────────────

    private fun recipeWith(rule: ProgressionRule, lifts: Set<LiftSlot>, trainingMaxPercent: Double = 0.90) = TrainingPlanRecipe(
        id = "engine-test",
        weeks = emptyList(),
        trainingMaxPercent = trainingMaxPercent,
        liftSlots = lifts.associateWith { CatalogIds.SQ_LOW },
        progression = rule,
    )

    @Test
    fun tm_changes_list_each_lift_of_the_recipe_that_has_a_tm_in_lift_order() {
        val profile = PowerliftingProfile(
            squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
            squatTM = 180.0, benchTM = 108.0, deadliftTM = 198.0,
        )
        val recipe = recipeWith(increment, setOf(LiftSlot.DEADLIFT, LiftSlot.OVERHEAD, LiftSlot.BENCH, LiftSlot.SQUAT))
        val changes = AuthoredProgressionEngine.tmChanges(profile, recipe)
        // El press militar no tiene TM ni 1RM: no sube. El resto, en el orden de LiftSlot.
        assertEquals(
            listOf(
                AuthoredProgressionEngine.TmChange(LiftSlot.SQUAT, 180.0, 185.0),
                AuthoredProgressionEngine.TmChange(LiftSlot.BENCH, 108.0, 110.5),
                AuthoredProgressionEngine.TmChange(LiftSlot.DEADLIFT, 198.0, 203.0),
            ),
            changes,
        )
    }

    @Test
    fun tm_changes_derive_the_tm_from_the_one_rm_when_no_tm_is_stored() {
        val profile = PowerliftingProfile(squat1RM = 200.0)
        val recipe = recipeWith(increment, setOf(LiftSlot.SQUAT), trainingMaxPercent = 0.87)
        val change = AuthoredProgressionEngine.tmChanges(profile, recipe).single()
        assertEquals(174.0, change.beforeKg, 1e-9)
        assertEquals(179.0, change.afterKg, 1e-9)
    }

    @Test
    fun tm_changes_skip_lifts_the_recipe_does_not_declare_and_rules_without_an_increment() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, squatTM = 180.0, benchTM = 108.0)
        val onlySquat = AuthoredProgressionEngine.tmChanges(profile, recipeWith(increment, setOf(LiftSlot.SQUAT)))
        assertEquals(listOf(LiftSlot.SQUAT), onlySquat.map { it.liftSlot })
        assertTrue(AuthoredProgressionEngine.tmChanges(profile, recipeWith(ProgressionRule.None, setOf(LiftSlot.SQUAT))).isEmpty())
        assertTrue(AuthoredProgressionEngine.tmChanges(profile, recipeWith(weekly, setOf(LiftSlot.SQUAT))).isEmpty())
        assertTrue(AuthoredProgressionEngine.tmChanges(PowerliftingProfile(), recipeWith(increment, setOf(LiftSlot.SQUAT))).isEmpty())
        // Un incremento cero o negativo no cambia nada.
        assertTrue(
            AuthoredProgressionEngine.tmChanges(
                profile,
                recipeWith(ProgressionRule.CycleIncrement(0.0, 0.0), setOf(LiftSlot.SQUAT, LiftSlot.BENCH)),
            ).isEmpty(),
        )
    }

    @Test
    fun the_rounding_step_never_exceeds_the_increment_of_the_method() {
        // Con discos de 2,5 kg el paso del inventario es 5 kg, pero una subida de 2,5 kg no puede saltar 5.
        val step = AuthoredProgressionEngine.roundingStepKg(
            EquipmentInventory(plates = listOf(PlateStock(2.5, 2), PlateStock(5.0, 2), PlateStock(20.0, 2))),
        )
        assertEquals(5.0, step, 0.0)
        val profile = PowerliftingProfile(
            squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
            squatTM = 180.0, benchTM = 110.0, deadliftTM = 200.0,
        )
        val recipe = recipeWith(increment, setOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT))

        assertEquals(
            listOf(
                AuthoredProgressionEngine.TmChange(LiftSlot.SQUAT, 180.0, 185.0),
                // Sin acotar el paso la banca saltaría 110 → 115.
                AuthoredProgressionEngine.TmChange(LiftSlot.BENCH, 110.0, 112.5),
                AuthoredProgressionEngine.TmChange(LiftSlot.DEADLIFT, 200.0, 205.0),
            ),
            AuthoredProgressionEngine.tmChanges(profile, recipe, step),
        )
    }

    // ─── Registro de consumidores ──────────────────────────────────────────────────

    @Test
    fun the_consumer_registry_lists_the_author_engine_and_the_autoregulation_rules() {
        assertEquals(
            setOf(ProgressionRule.CycleIncrement::class, ProgressionRule.WeeklyKg::class),
            ProgressionConsumers.authored,
        )
        assertEquals(
            setOf(ProgressionRule.AmrapDrivenTm::class, ProgressionRule.RepTargetDrivenTm::class),
            ProgressionConsumers.autoregulation,
        )
        assertEquals(ProgressionConsumers.authored + ProgressionConsumers.autoregulation, ProgressionConsumers.executable)
        assertTrue(ProgressionConsumers.isExecutable(increment))
        assertTrue(ProgressionConsumers.isExecutable(weekly))
        assertTrue(ProgressionConsumers.isExecutable(ProgressionRule.AmrapDrivenTm()))
        // Hasta B.S4 y B.S6 estas no hacen nada al ejecutar el plan.
        assertFalse(ProgressionConsumers.isExecutable(ProgressionRule.TopSetPr()))
        assertFalse(ProgressionConsumers.isExecutable(ProgressionRule.WeeklyPercent(2.5)))
        assertFalse(ProgressionConsumers.isExecutable(ProgressionRule.RepMaxAutoregulated))
        assertFalse(ProgressionConsumers.isExecutable(ProgressionRule.None))
    }

    // ─── Texto del aviso ───────────────────────────────────────────────────────────

    @Test
    fun the_notice_names_only_the_lifts_that_change_with_a_decimal_comma() {
        val text = AuthoredProgressionEngine.noticeText(
            label = "Nuevo ciclo",
            changes = listOf(
                AuthoredProgressionEngine.TmChange(LiftSlot.SQUAT, 180.0, 185.0),
                AuthoredProgressionEngine.TmChange(LiftSlot.BENCH, 120.0, 122.5),
                AuthoredProgressionEngine.TmChange(LiftSlot.DEADLIFT, 220.0, 225.0),
            ),
            preservedSessions = 0,
            pendingMaterialization = false,
        )
        assertEquals("Nuevo ciclo: TM sentadilla 180 → 185 kg, banca 120 → 122,5 kg, peso muerto 220 → 225 kg.", text)
        val overhead = AuthoredProgressionEngine.noticeText(
            "Nuevo bloque",
            listOf(AuthoredProgressionEngine.TmChange(LiftSlot.OVERHEAD, 60.0, 62.5)),
            0,
            false,
        )
        assertEquals("Nuevo bloque: TM press militar 60 → 62,5 kg.", overhead)
    }

    @Test
    fun the_notice_counts_the_manual_sessions_it_kept_and_says_how_to_apply_pending_loads() {
        val change = listOf(AuthoredProgressionEngine.TmChange(LiftSlot.SQUAT, 180.0, 185.0))
        val one = AuthoredProgressionEngine.noticeText("Nuevo ciclo", change, 1, false)
        assertEquals("Nuevo ciclo: TM sentadilla 180 → 185 kg. 1 sesión con ajustes manuales se conservó sin cambios.", one)
        val several = AuthoredProgressionEngine.noticeText("Nuevo ciclo", change, 3, false)
        assertEquals("Nuevo ciclo: TM sentadilla 180 → 185 kg. 3 sesiones con ajustes manuales se conservaron sin cambios.", several)
        val pending = AuthoredProgressionEngine.noticeText("Nuevo ciclo", change, 0, true)
        assertTrue(pending, pending.endsWith("Las cargas nuevas se aplican al pulsar RE-MATERIALIZAR."))
    }
}
