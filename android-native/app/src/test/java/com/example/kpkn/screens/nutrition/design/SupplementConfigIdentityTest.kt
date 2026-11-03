package com.example.kpkn.screens.nutrition.design

import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.domain.nutrition.supplements.SupplementCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editar un suplemento no cambia quién es: un propio conserva id, nombre, unidad y notas; uno del catálogo conserva
 * su id y no se convierte en propio. Solo crear uno nuevo genera identidad.
 */
class SupplementConfigIdentityTest {

    private val caffeine = SupplementCatalog.byId("cafeina")!!

    @Test
    fun editarUnPropioConservaSuIdentidadYSusCampos() {
        val before = SupplementPilaItem(
            id = "propio-abc12345",
            dose = 2.5,
            moment = 1,
            doses = 2,
            reminder = "09:00",
            customName = "Mi mezcla",
            customUnit = "g",
            notes = "Con el desayuno",
        )

        val after = supplementConfigItem(
            initialItem = before,
            def = null,
            isCustom = true,
            dose = 3.0,
            moment = 1,
            doses = 2,
            reminderEnabled = false,
            reminderTime = "09:00",
            customName = "  Mi mezcla  ",
            customUnit = "g",
            notes = "  Con el desayuno y agua  ",
        )

        assertEquals("propio-abc12345", after.id)
        assertEquals("Mi mezcla", after.customName)
        assertEquals("g", after.customUnit)
        assertEquals("Con el desayuno y agua", after.notes)
        assertEquals(3.0, after.dose, 1e-9)
        assertEquals(2, after.doses)
        assertNull("desactivar el recordatorio lo quita", after.reminder)
    }

    @Test
    fun editarUnSuplementoDelCatalogoConservaSuIdYNoSeVuelvePropio() {
        val before = SupplementPilaItem("cafeina", 100.0, moment = 3, notes = "Antes de entrenar")

        val after = supplementConfigItem(
            initialItem = before,
            def = caffeine,
            isCustom = false,
            dose = 125.0,
            moment = 3,
            doses = 1,
            reminderEnabled = true,
            reminderTime = "07:30",
            customName = "texto que no debe usarse",
            customUnit = "mg",
            notes = "Antes de entrenar",
        )

        assertEquals("cafeina", after.id)
        assertNull("un elemento del catálogo no se convierte en propio", after.customName)
        assertNull(after.customUnit)
        assertEquals("07:30", after.reminder)
        assertEquals("Antes de entrenar", after.notes)
    }

    @Test
    fun crearUnPropioGeneraUnIdPropioYConservaElNombre() {
        val created = supplementConfigItem(
            initialItem = null,
            def = null,
            isCustom = true,
            dose = 1.0,
            moment = 2,
            doses = 1,
            reminderEnabled = false,
            reminderTime = "09:00",
            customName = "Mi cápsula",
            customUnit = "porción",
            notes = "",
        )

        assertTrue(created.id.startsWith("propio-"))
        assertEquals("Mi cápsula", created.customName)
        assertEquals("porción", created.customUnit)
    }

    @Test
    fun anadirAlgoDelCatalogoUsaElIdDelCatalogo() {
        val created = supplementConfigItem(
            initialItem = null,
            def = caffeine,
            isCustom = false,
            dose = 100.0,
            moment = 3,
            doses = 1,
            reminderEnabled = false,
            reminderTime = "09:00",
            customName = "",
            customUnit = "",
            notes = "",
        )

        assertEquals("cafeina", created.id)
        assertNull(created.customName)
    }
}
