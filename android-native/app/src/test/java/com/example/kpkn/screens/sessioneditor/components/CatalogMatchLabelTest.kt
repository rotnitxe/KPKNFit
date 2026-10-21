package com.example.kpkn.screens.sessioneditor.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La línea de coincidencias de una fila no repite músculo ni material, y no existe sin búsqueda (SE6b). */
class CatalogMatchLabelTest {

    @Test
    fun noRepiteLoQueYaDiceLaFila() {
        assertEquals(
            "Agarre prono",
            catalogMatchLabel(
                matchChips = listOf("Peso corporal", "Agarre prono"),
                shownInSubtitle = listOf("Dorsales", "Peso corporal"),
                query = "dominadas",
            ),
        )
    }

    @Test
    fun sinBusquedaEscritaNoHayLinea() {
        assertNull(
            catalogMatchLabel(
                matchChips = listOf("Agarre prono"),
                shownInSubtitle = emptyList(),
                query = "",
            ),
        )
    }

    @Test
    fun siTodoYaEstaEnLaFilaNoHayLinea() {
        assertNull(
            catalogMatchLabel(
                matchChips = listOf("Peso corporal"),
                shownInSubtitle = listOf("Peso corporal"),
                query = "dominadas",
            ),
        )
    }

    @Test
    fun masDeTresCoincidenciasSeResumen() {
        assertEquals(
            "A · B · C · +1",
            catalogMatchLabel(
                matchChips = listOf("A", "B", "C", "D"),
                shownInSubtitle = emptyList(),
                query = "x",
            ),
        )
    }
}
