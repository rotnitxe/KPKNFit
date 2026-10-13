package com.example.kpkn.ui.components.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Set KPKN Line: cada icono existe, mide 24 × 24 dp con viewport de 24 y tiene al menos un trazado. */
class KpknLineIconsTest {

    private val iconos: List<ImageVector> = listOf(
        KpknLineIcons.Home,
        KpknLineIcons.Training,
        KpknLineIcons.Nutrition,
        KpknLineIcons.Body,
        KpknLineIcons.Play,
        KpknLineIcons.PlayFilled,
        KpknLineIcons.Plus,
        KpknLineIcons.Edit,
        KpknLineIcons.Info,
        KpknLineIcons.ChevronRight,
        KpknLineIcons.ChevronLeft,
        KpknLineIcons.ChevronDown,
        KpknLineIcons.Close,
        KpknLineIcons.Check,
        KpknLineIcons.Clock,
        KpknLineIcons.Calendar,
        KpknLineIcons.Bell,
        KpknLineIcons.Book,
        KpknLineIcons.TrendUp,
        KpknLineIcons.TrendDown,
        KpknLineIcons.Repeat,
        KpknLineIcons.More,
        KpknLineIcons.Sliders,
        KpknLineIcons.Person,
        KpknLineIcons.Sunrise,
        KpknLineIcons.Sun,
        KpknLineIcons.Apple,
        KpknLineIcons.Moon,
        KpknLineIcons.Tower,
    )

    @Test
    fun cadaIconoMide24PorVeinticuatroDp() {
        assertEquals(29, iconos.size)
        for (icono in iconos) {
            assertEquals("${icono.name}: ancho", 24.dp, icono.defaultWidth)
            assertEquals("${icono.name}: alto", 24.dp, icono.defaultHeight)
            assertEquals("${icono.name}: viewport ancho", 24f, icono.viewportWidth, 0f)
            assertEquals("${icono.name}: viewport alto", 24f, icono.viewportHeight, 0f)
        }
    }

    @Test
    fun cadaIconoTieneAlMenosUnTrazado() {
        for (icono in iconos) {
            assertTrue("${icono.name} no tiene trazados", nodosDelGrupoRaiz(icono) >= 1)
        }
    }

    @Test
    fun losNombresDeLosIconosSonUnicos() {
        assertEquals(iconos.size, iconos.map { it.name }.toSet().size)
    }

    /**
     * Número de nodos del grupo raíz. La API de grupos cambia entre versiones de Compose (`size` o `numChildren`), así que
     * se lee por reflexión. Devuelve -1 si ninguno de los dos métodos existe.
     */
    private fun nodosDelGrupoRaiz(icono: ImageVector): Int {
        val raiz = icono.root
        val contar = raiz.javaClass.methods.firstOrNull { it.name == "getSize" || it.name == "getNumChildren" }
            ?: return -1
        return contar.invoke(raiz) as Int
    }
}
