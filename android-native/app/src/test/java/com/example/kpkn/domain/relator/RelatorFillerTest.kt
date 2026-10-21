package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sin relleno (LR1h): la técnica va tal cual y el plan sin receta dice la serie que toca. */
class RelatorFillerTest {

    @Test
    fun la_tecnica_va_tal_cual_y_no_termina_con_el_nombre_del_ejercicio() {
        val ctx = relatorContext(
            conceptId = "carga-axial",
            conceptLines = listOf("La carga axial comprime en vertical. No dejes que el tronco se rompa."),
            executionCues = listOf("Pecho alto."),
        )
        val candidate = TechniqueObserver.observe(ctx).single()
        assertTrue(candidate.lines.contains("La carga axial comprime en vertical. No dejes que el tronco se rompa."))
        assertTrue(candidate.lines.contains("Pecho alto."))
        assertTrue(candidate.lines.none { it.contains("{ex}") })

        // Lo que sale al relator tampoco lleva el nombre pegado al final.
        repeat(3) { cycle ->
            val (line, _) = RelatorComposer.compose(
                candidate,
                ctx.copy(setKey = "ex_$cycle|0"),
                RelatorSelectorState(),
                RelatorLongTermMemory(),
            )
            assertFalse("«${line.text}» lleva el nombre", line.text.orEmpty().contains("Sentadilla"))
        }
    }

    @Test
    fun el_plan_sin_receta_dice_la_serie_que_toca_y_no_rellena() {
        val ctx = relatorContext(
            protocolId = null,
            protocolName = null,
            slotRole = null,
            isAmrap = false,
            progression = null,
            targetPercentageRm = null,
            autoregulationMode = com.example.kpkn.data.models.AutoregulationMode.OFF,
            weekIndex = null,
            weeksInBlock = null,
            blockGoal = null,
            prescribedWeightKg = 95.0,
            suggestedWeightKg = 95.0,
            setIndex = 1,
            setCount = 4,
        ).copy(targetReps = 6, targetRir = 2)
        val plan = PlanObserver.observe(ctx).single()
        assertEquals(listOf("Serie 2 de 4: 95 kg × 6 a RIR 2."), plan.lines)
    }

    @Test
    fun el_plan_sin_receta_ni_carga_sigue_diciendo_la_serie() {
        val ctx = relatorContext(
            protocolId = null,
            protocolName = null,
            slotRole = null,
            isAmrap = false,
            progression = null,
            targetPercentageRm = null,
            autoregulationMode = com.example.kpkn.data.models.AutoregulationMode.OFF,
            weekIndex = null,
            weeksInBlock = null,
            blockGoal = null,
            prescribedWeightKg = null,
            suggestedWeightKg = null,
            setIndex = 0,
            setCount = 1,
        ).copy(targetReps = null, targetRir = null)
        val plan = PlanObserver.observe(ctx).single()
        assertEquals(listOf("Serie 1."), plan.lines)
    }
}
