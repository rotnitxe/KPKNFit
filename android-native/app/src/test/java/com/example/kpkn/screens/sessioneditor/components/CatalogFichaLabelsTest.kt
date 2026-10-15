package com.example.kpkn.screens.sessioneditor.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La línea de la ficha nombra el implemento elegido (CF1) y las cifras del módulo «Músculos» van con coma, plural y espacio duro (CF1). */
class CatalogFichaLabelsTest {

    @Test
    fun laLineaDeLaFichaNombraElImplementoElegidoNoElDePorDefecto() {
        val label = catalogFichaMaterialLabel(
            selectedImplementation = "dumbbells",
            defaultImplementation = "barbell",
            definitionId = "press-banca-plano",
        )

        assertEquals(exerciseCatalogOptionLabel("dumbbells", "press-banca-plano"), label)
        assertNotEquals(exerciseCatalogOptionLabel("barbell", "press-banca-plano"), label)
    }

    @Test
    fun sinImplementoElegidoLaLineaUsaElDePorDefecto() {
        val label = catalogFichaMaterialLabel(
            selectedImplementation = null,
            defaultImplementation = "barbell",
            definitionId = "press-banca-plano",
        )

        assertEquals(exerciseCatalogOptionLabel("barbell", "press-banca-plano"), label)
    }

    @Test
    fun unDefaultOUnValorVacioNoCuentanComoImplemento() {
        val label = catalogFichaMaterialLabel(
            selectedImplementation = "default",
            defaultImplementation = "",
            definitionId = "press-banca-plano",
        )

        assertNull(label)
    }

    @Test
    fun lasCifrasDelMusculoVanConComaPluralYEspacioDuroAntesDeLaUnidad() {
        assertEquals("1 serie · 100 %", muscleFigures(1.0))
        assertEquals("0,5 series · 50 %", muscleFigures(0.5))
        assertEquals("0,8 series · 80 %", muscleFigures(0.8))
    }
}
