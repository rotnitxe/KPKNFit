package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.data.programs.CatalogDuration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los tres nombres de la duración en la biblioteca: los mismos que el glosario y Program Detail. */
class ProgramLibraryFiltersTest {

    @Test
    fun `catalogDurationLabel da los tres nombres nuevos`() {
        assertEquals("Semana que se repite", catalogDurationLabel(CatalogDuration.REPEATING_WEEK))
        assertEquals("Ciclo que se repite", catalogDurationLabel(CatalogDuration.REPEATING_CYCLE))
        assertEquals("Ciclo que termina", catalogDurationLabel(CatalogDuration.FINITE_CYCLE))
    }
}
