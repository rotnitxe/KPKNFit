package com.example.kpkn.screens.onboarding.design

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningContext
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.WizardPacePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de [reconcileLive] y de [WizardNutritionLiveState]: ni un arrastre en curso ni una escritura sin
 * confirmar se pisan con un plan confirmado de antes, y lo confirmado manda cuando no hay nada en vuelo.
 */
class WizardNutritionLiveTest {

    private val context = PlanTuningContext(80.0, 2650.0, PlanDirection.DEFICIT, EerSex.MALE)

    private fun tuning(values: PlanValues, baseline: PlanValues = BASE): PlanTuning = PlanTuning(context, baseline, values)

    private fun planned(): PlanTuning = requireNotNull(PlanTuning.recommended(context, WizardPacePreset.MEDIUM))

    // ─── reconcileLive ───────────────────────────────────────────────────────

    @Test
    fun `con un arrastre en curso no se toca nada`() {
        val live = tuning(BASE.copy(proteinG = 190, kcal = 2400))
        val committed = tuning(BASE)
        val result = reconcileLive(live, pending = null, dragging = true, committed = committed)
        assertSame(live, result.live)
        assertNull(result.pending)
    }

    @Test
    fun `sin nada en vuelo manda lo confirmado`() {
        val live = tuning(BASE.copy(proteinG = 190, kcal = 2400))
        val committed = tuning(BASE.copy(proteinG = 150, kcal = 2300))
        val result = reconcileLive(live, pending = null, dragging = false, committed = committed)
        assertSame(committed, result.live)
        assertNull(result.pending)
    }

    @Test
    fun `lo confirmado de antes no deshace una escritura enviada`() {
        val sent = BASE.copy(proteinG = 190, kcal = 2400)
        val live = tuning(sent)
        val stale = tuning(BASE) // el confirmado previo a la escritura
        val waiting = reconcileLive(live, pending = sent, dragging = false, committed = stale)
        assertSame(live, waiting.live)
        assertEquals(sent, waiting.pending)

        // Cuando vuelve el suyo se da por recibida y se adopta (con su contexto).
        val arrived = tuning(sent)
        val done = reconcileLive(live, pending = sent, dragging = false, committed = arrived)
        assertSame(arrived, done.live)
        assertNull(done.pending)
    }

    @Test
    fun `si lo confirmado ya coincide con lo que se ve se adopta el confirmado`() {
        val live = tuning(BASE, baseline = BASE)
        val committed = tuning(BASE, baseline = BASE.copy(kcal = 2300)) // otra base, mismos números
        val result = reconcileLive(live, pending = null, dragging = false, committed = committed)
        assertSame(committed, result.live)
    }

    // ─── WizardNutritionLiveState ────────────────────────────────────────────

    @Test
    fun `un arrastre mueve lo que se ve y soltar devuelve el plan a escribir una sola vez`() {
        val state = WizardNutritionLiveState(planned())
        val start = state.live
        state.edit { it.withProtein(start.proteinG + 20) }
        state.edit { it.withProtein(start.proteinG + 30) }
        assertTrue(state.dragging)
        assertEquals(start.proteinG + 30, state.live.proteinG)

        val toWrite = state.finishDrag()
        assertEquals(start.proteinG + 30, toWrite!!.proteinG)
        assertTrue(!state.dragging)
        assertEquals(toWrite.values, state.pending)

        // Soltar otra vez sin cambios no reenvía nada.
        assertNull(state.finishDrag())
    }

    @Test
    fun `soltar sin haber movido nada no escribe`() {
        val state = WizardNutritionLiveState(planned())
        assertNull(state.finishDrag())
        assertNull(state.pending)
    }

    @Test
    fun `volver al valor original antes de soltar no escribe`() {
        val state = WizardNutritionLiveState(planned())
        val start = state.live
        state.edit { it.withFat(start.fatG + 12) }
        state.edit { it.withFat(start.fatG) }
        assertNull(state.finishDrag())
        assertEquals(start.values, state.live.values)
    }

    @Test
    fun `una muesca o restablecer se aplican y se escriben ya`() {
        val state = WizardNutritionLiveState(planned())
        val moved = state.commitNow { it.withPreset(WizardPacePreset.FAST) }
        assertEquals(state.live.values, moved!!.values)
        assertEquals(moved.values, state.pending)
        val reset = state.commitNow { it.reset() }
        assertEquals(planned().values, reset!!.values)
    }

    @Test
    fun `la confirmacion de una escritura limpia lo pendiente`() {
        val state = WizardNutritionLiveState(planned())
        state.edit { it.withCarbs(it.carbsG - 30) }
        val written = state.finishDrag()!!
        assertNotEquals(null, state.pending)

        // Llega primero el confirmado de antes: se espera.
        state.adopt(planned())
        assertEquals(written.values, state.live.values)
        assertEquals(written.values, state.pending)

        // Llega el suyo: se adopta y ya no hay nada en vuelo.
        state.adopt(written)
        assertNull(state.pending)
        assertEquals(written.values, state.live.values)
    }

    @Test
    fun `una escritura perdida caduca y vuelve lo confirmado`() {
        val state = WizardNutritionLiveState(planned())
        state.edit { it.withCarbs(it.carbsG - 30) }
        state.finishDrag()
        val committed = planned()
        state.adopt(committed) // lo confirmado de antes: se espera
        assertNotEquals(committed.values, state.live.values)

        state.expirePending(committed)
        assertNull(state.pending)
        assertEquals(committed.values, state.live.values)
    }

    @Test
    fun `no caduca nada mientras se arrastra`() {
        val state = WizardNutritionLiveState(planned())
        state.edit { it.withCarbs(it.carbsG - 30) }
        val before = state.live
        state.expirePending(planned())
        assertSame(before, state.live)
        assertTrue(state.dragging)
    }

    private companion object {
        val BASE = PlanValues(kcal = 2210, proteinG = 160, carbsG = 254, fatG = 61)
    }
}
