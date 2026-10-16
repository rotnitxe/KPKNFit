package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LV5, bug 1: la cabecera del catálogo dice que se está reemplazando y qué ejercicio se reemplaza. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogListHeaderReplacementTest {

    @get:Rule
    val rule = createComposeRule()

    private fun header(
        isReplacement: Boolean,
        replacingName: String? = null,
        targetGroupName: String? = null,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        CatalogListHeader(
            query = "",
            onQueryChange = {},
            onBack = {},
            targetGroupName = targetGroupName,
            filterRegion = null,
            filterMuscle = null,
            muscleFilterExpanded = false,
            muscleIds = emptyList(),
            onClearFilters = {},
            onToggleUpper = {},
            onToggleLower = {},
            onToggleMuscle = {},
            onToggleMuscleId = {},
            isReplacement = isReplacement,
            replacingName = replacingName,
        )
    }

    @Test
    fun alReemplazarElTituloYElEjercicioActualSeVenArriba() {
        rule.setContent(header(isReplacement = true, replacingName = "Press de Banca Plano · Mancuernas"))

        rule.onNodeWithText("Reemplazar ejercicio").assertExists()
        rule.onNodeWithText("Añadir ejercicios").assertDoesNotExist()
        rule.onNodeWithText("Reemplazando: Press de Banca Plano · Mancuernas").assertExists()
    }

    @Test
    fun alAnadirSigueDiciendoAnadirYLaParteDeLaSesion() {
        rule.setContent(header(isReplacement = false, targetGroupName = "Bloque principal"))

        rule.onNodeWithText("Añadir ejercicios").assertExists()
        rule.onNodeWithText("Reemplazar ejercicio").assertDoesNotExist()
        rule.onNodeWithText("Agrega ejercicios para Bloque principal").assertExists()
    }

    @Test
    fun alReemplazarSinNombreSoloCambiaElTituloYNoDiceAgregar() {
        rule.setContent(header(isReplacement = true, replacingName = "  ", targetGroupName = "Bloque principal"))

        rule.onNodeWithText("Reemplazar ejercicio").assertExists()
        rule.onNodeWithTag(CatalogHeaderContextTag).assertDoesNotExist()
        rule.onNodeWithText("Agrega ejercicios para Bloque principal").assertDoesNotExist()
    }

    @Test
    fun laLineaDeContextoEsPuraYNoInventaTexto() {
        assertEquals("Reemplazando: Remo", catalogHeaderContextLine(true, " Remo ", "Parte"))
        assertNull(catalogHeaderContextLine(true, null, "Parte"))
        assertNull(catalogHeaderContextLine(false, "Remo", null))
        assertEquals("Agrega ejercicios para Parte", catalogHeaderContextLine(false, "Remo", "Parte"))
        assertEquals("Reemplazar ejercicio", catalogHeaderTitle(true))
        assertEquals("Añadir ejercicios", catalogHeaderTitle(false))
    }
}
