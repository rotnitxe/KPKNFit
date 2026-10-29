package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los filtros de la biblioteca de planes ([libraryVisibleEntries]), que siguen vivos en la página del catálogo. La hoja vieja
 * `CreateProgramTemplateSheet` se retiró (CR-S1): Inicio abre la hoja «Nuevo programa».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LibraryVisibleEntriesTest {

    private val listados by lazy {
        PersonalizedPlanCatalog.listedEntries()
            .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) }
    }

    /** El filtro de duración deja solo los planes de esa duración, comparando el valor del enum, sobre el catálogo real. */
    @Test
    fun elegirUnaDuracionDejaSoloLosPlanesDeEsaDuracion() {
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

    /** CR-F7: los días van de 1 a 7; cada cifra deja justo los planes que la admiten. */
    @Test
    fun cadaCifraDeDiasDejaLosPlanesQueLaAdmiten() {
        for (dias in 1..7) {
            assertEquals(
                "$dias días",
                listados.filter { dias in it.supportedFrequencies }.map { it.id },
                libraryVisibleEntries(listados, dias, null, LibraryProfileFilter.ALL, LibraryOriginFilter.ALL).map { it.id },
            )
        }
    }
}
