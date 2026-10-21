package com.example.kpkn.screens.programs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La entrada «Importar» de la hoja de crear programa: su título y su subtítulo nuevos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CreateProgramTemplateSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laEntradaDeImportarTieneSuTituloYSubtituloNuevos() {
        rule.setContent {
            CreateProgramTemplateSheet(
                onDismiss = {},
                onCreateBlank = {},
                onImport = {},
                onCreateFromTemplate = {},
            )
        }

        rule.onNodeWithText("Importar un programa").assertExists()
        rule.onNodeWithText("Desde Excel, Word, PDF, texto o una foto de tu rutina.").assertExists()
        rule.onNodeWithText("Importar (Excel, CSV o texto)").assertDoesNotExist()
    }

    /**
     * El filtro de duración deja solo los planes de esa duración, comparando el valor del enum. Es la regla que la hoja aplica
     * con sus chips; se prueba sobre el catálogo real, sin depender de un clic.
     */
    @Test
    fun elegirUnaDuracionDejaSoloLosPlanesDeEsaDuracion() {
        val listados = PersonalizedPlanCatalog.listedEntries()
            .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) }
        assertTrue("el catálogo tiene planes listados", listados.isNotEmpty())
        for (duracion in CatalogDuration.entries) {
            val visibles = libraryVisibleEntries(
                listados,
                days = null,
                duration = duracion,
                profile = LibraryProfileFilter.ALL,
                origin = LibraryOriginFilter.ALL,
            )
            assertEquals(
                "${duracion.name}: solo los planes de esa duración",
                listados.filter { it.duration == duracion }.map { it.id },
                visibles.map { it.id },
            )
        }
        // «Todas» (sin duración) deja el catálogo entero, en su orden.
        assertEquals(
            listados.map { it.id },
            libraryVisibleEntries(listados, null, null, LibraryProfileFilter.ALL, LibraryOriginFilter.ALL).map { it.id },
        )
    }
}
