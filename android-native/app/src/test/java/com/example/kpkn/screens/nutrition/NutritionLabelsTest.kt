package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.screens.nutrition.components.portionPresetLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WP-U17: las etiquetas de la página de Nutrición salen en el idioma de la app, nunca como identificadores crudos. */
class NutritionLabelsTest {

    // ─── Fecha del encabezado ─────────────────────────────────────────────────

    @Test
    fun `the hero date is written in Spanish for a Spanish app`() {
        assertEquals("Viernes, 2 de octubre", nutritionHeroDateLabel("2026-10-02", Locale.forLanguageTag("es-CL")))
    }

    @Test
    fun `the hero date is written in English for an English app without the Spanish de`() {
        val label = nutritionHeroDateLabel("2026-10-02", Locale.ENGLISH)
        assertEquals("Friday, October 2", label)
        assertFalse(label.contains(" de "))
    }

    @Test
    fun `any other language gets its own long date`() {
        val label = nutritionHeroDateLabel("2026-10-02", Locale.FRENCH)
        assertTrue(label, label.contains("2026") && label.contains("octobre"))
        assertTrue(label, label.first().isUpperCase())
    }

    @Test
    fun `a date that cannot be read is shown as it came`() {
        assertEquals("no-es-fecha", nutritionHeroDateLabel("no-es-fecha", Locale.ENGLISH))
    }

    // ─── Calibración ───────────────────────────────────────────────────────────

    @Test
    fun `every calibration status the engine writes has a Spanish label`() {
        val statuses = listOf("incomplete", "needs_more_weights_or_complete_days", "waiting_after_plan_change", "ready")
        val labels = statuses.map(::calibrationStatusLabel)
        assertEquals(statuses.size, labels.toSet().size)
        statuses.zip(labels).forEach { (status, label) ->
            assertNotEquals(status, label)
            assertFalse(label, label.contains('_'))
        }
        assertEquals("Faltan días de registro", calibrationStatusLabel("incomplete"))
    }

    @Test
    fun `an unknown calibration status is neutral and never the raw word`() {
        assertEquals("En evaluación", calibrationStatusLabel("something_new"))
    }

    @Test
    fun `the weighing convention is shown in words`() {
        assertEquals("sin definir", weighingConventionLabel(null))
        assertEquals("Crudo", weighingConventionLabel("RAW"))
        assertEquals("Cocido", weighingConventionLabel("COOKED"))
        assertEquals("Depende", weighingConventionLabel("DEPENDS"))
        assertEquals("OTRA", weighingConventionLabel("OTRA"))
    }

    // ─── Porciones ──────────────────────────────────────────────────────────────

    @Test
    fun `portion presets are named in Spanish and never by their identifier`() {
        assertEquals("Pequeño", portionPresetLabel(PortionPreset.SMALL))
        assertEquals("Mediano", portionPresetLabel(PortionPreset.MEDIUM))
        assertEquals("Grande", portionPresetLabel(PortionPreset.LARGE))
        assertEquals("Extra", portionPresetLabel(PortionPreset.EXTRA))
        PortionPreset.entries.forEach { preset ->
            assertTrue(portionPresetLabel(preset).isNotBlank())
            assertNotEquals(preset.name, portionPresetLabel(preset))
        }
    }

    // ─── Guardar tras abrir una edición (WP-U17 / seguimiento de U11) ───────────

    private fun log(id: String) = NutritionLog(id = id, date = "2026-10-02T12:00:00.000Z", mealType = MealType.LUNCH, foods = emptyList())

    @Test
    fun `saving the meal that was opened for editing is an edit`() {
        assertTrue(isEditSave("log-1", log("log-1")))
    }

    @Test
    fun `a new meal is never routed as an edit even if an edit id is still remembered`() {
        assertFalse(isEditSave("log-1", log("another-draft-id")))
        assertFalse(isEditSave(null, log("log-1")))
    }
}
