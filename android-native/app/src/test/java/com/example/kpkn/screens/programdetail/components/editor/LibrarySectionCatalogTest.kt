package com.example.kpkn.screens.programdetail.components.editor

import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.screens.programs.isLibraryFamilyEntry
import com.example.kpkn.screens.programs.visibleProtocolOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La hoja «Aplicar plantilla o método» solo puede mostrar lo que el llamador sabe aplicar. Las entradas nativas sin
 * plantilla ni protocolo visible (`native:machine-muscle`, `native:bodyweight`…) no se anuncian como si fueran a
 * crear una copia: no entran en la biblioteca ni en el conjunto de entradas aplicables.
 */
class LibrarySectionCatalogTest {

    @Test
    fun todaFilaDeLaBibliotecaTieneUnaAplicacionRealYLasNativasNoAplicablesQuedanFuera() {
        val catalog = PersonalizedPlanCatalog.listedEntries()
            .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) }
        val applicable = catalog.filter { entry ->
            entry.template != null ||
                (entry.source == CatalogSource.PROTOCOL && visibleProtocolOf(entry) != null)
        }.map { it.id }.toSet()
        val notApplicable = catalog.filter { it.id !in applicable }

        val library = programLibrary()
        val keys = library.templates.keys + library.protocols.keys

        assertEquals("la biblioteca ofrece exactamente las entradas aplicables", applicable, keys)
        assertTrue("el catálogo debe tener entradas no aplicables para que el filtro importe", notApplicable.isNotEmpty())
        assertTrue("ninguna entrada nativa sin aplicación se cuela en la hoja", notApplicable.none { it.id in keys })
        assertEquals(
            "cada fila visible tiene su plantilla o protocolo",
            keys,
            (library.templateItems + library.methodItems).map { it.key }.toSet(),
        )
        assertEquals(library.templates.size, library.templateItems.size)
        assertEquals(library.protocols.size, library.methodItems.size)
    }
}
