package com.example.kpkn.screens.sessioneditor.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExerciseCatalogV2LabelsTest {
    @Test
    fun controlledAxesAndOptionsAreLocalizedAtTheUiBoundary() {
        assertEquals("Implemento", exerciseCatalogAxisLabel("implement"))
        assertEquals("Estación", exerciseCatalogAxisLabel("station"))
        assertEquals("Ángulo y soporte", exerciseCatalogAxisLabel("support_angle"))
        assertEquals("Mancuerna", exerciseCatalogOptionLabel("dumbbells"))
        assertEquals("Máquina", exerciseCatalogOptionLabel("machine"))
        assertEquals("Barra a la espalda", exerciseCatalogOptionLabel("barbell_back"))
        assertFalse(exerciseCatalogOptionLabel("dumbbells").equals("dumbbells", ignoreCase = true))
    }
}
