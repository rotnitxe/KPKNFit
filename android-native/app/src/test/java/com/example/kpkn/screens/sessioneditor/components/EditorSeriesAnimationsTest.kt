package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pulido v1 · EDITOR · pasada B/C de las series: los `@keyframes` y las `animation` de `ed.css` (ed-A06, A08, A10, A11, A12, A13, A14, A15)
 * en el 0, 25, 50, 75 y 100 % de su duración, con la duración, la curva, el relleno y el estado final del CSS. Es la parte pura (sin Compose):
 * el cableado en pantalla lo mira `EditorSeriesFramesRenderTest`. Con `KPKN_RENDER_DIR` escribe la tabla `ed-series-animaciones.tsv`.
 */
class EditorSeriesAnimationsTest {
    private fun pose(animation: KpknAnimation, timeMs: Float, reduced: Boolean = false) =
        KpknPose().also { animation.poseAt(timeMs, it, reduced) }

    private fun at(animation: KpknAnimation, fraction: Float) = pose(animation, animation.duration.ms * fraction)

    private fun assertNear(message: String, expected: Float, actual: Float, tolerance: Float = 0.002f) =
        assertEquals(message, expected, actual, tolerance)

    companion object {
        private val table = StringBuilder("animación\t%\tms\talfa\tdesplazamiento_dp\tdesplazamiento_alto\tescala\tmezcla_color\n")

        @JvmStatic
        @org.junit.AfterClass
        fun escribeLaTablaDeFotogramas() {
            System.getenv("KPKN_RENDER_DIR")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "ed-series-animaciones.tsv").writeText(table.toString())
            }
        }
    }

    private fun record(name: String, animation: KpknAnimation) {
        for (percent in listOf(0, 25, 50, 75, 100)) {
            val ms = animation.duration.ms * percent / 100f
            val p = pose(animation, ms)
            table.append("$name\t$percent\t${ms.toInt()}\t${"%.4f".format(p.alpha)}\t${"%.3f".format(p.translateYDp)}\t${"%.4f".format(p.translateYFraction)}\t${"%.4f".format(p.scaleX)}\t${"%.4f".format(p[KpknChannel.ColorMix])}\n")
        }
    }

    @Test fun edInSubeSeisDpConFundidoEn260msSinRelleno() {
        val a = EdAnimations.In
        assertEquals(260, a.duration.ms)
        assertEquals(0, a.delay.ms)
        assertEquals(1f, a.iterations, 0f)
        assertNear("0 %", 0f, at(a, 0f).alpha)
        assertNear("0 % desplazamiento", 6f, at(a, 0f).translateYDp)
        val e = KpknMotion.Estandar.transform(0.5f)
        assertNear("50 % opacidad con --ed-ease", e, at(a, 0.5f).alpha)
        assertNear("50 % desplazamiento", 6f * (1f - e), at(a, 0.5f).translateYDp)
        // sin `fill-mode`: acabada la animación el elemento vuelve a su estado (opacidad 1, sin desplazamiento)
        assertNear("100 %", 1f, at(a, 1f).alpha)
        assertNear("100 % desplazamiento", 0f, at(a, 1f).translateYDp)
        assertFalse(at(a, 1f).active)
        record("ed-in", a)
    }

    @Test fun edFlashParteDeFondoDeTintaYEscala104YVuelveEn600ms() {
        val a = EdAnimations.Flash
        assertEquals(600, a.duration.ms)
        assertNear("0 % escala", 1.04f, at(a, 0f).scaleX)
        assertNear("0 % mezcla", 0f, at(a, 0f)[KpknChannel.ColorMix])
        val e = KpknMotion.Estandar.transform(0.5f)
        assertNear("50 % escala", 1f + 0.04f * (1f - e), at(a, 0.5f).scaleX)
        assertNear("50 % mezcla", e, at(a, 0.5f)[KpknChannel.ColorMix])
        assertNear("100 % escala", 1f, at(a, 1f).scaleX)
        assertNear("100 % mezcla", 1f, at(a, 1f)[KpknChannel.ColorMix])
        assertFalse(at(a, 1f).active)
        record("ed-flash", a)
    }

    @Test fun edBumpCrecedeDe86ConResorteEn240ms() {
        val a = EdAnimations.Bump
        assertEquals(240, a.duration.ms)
        assertNear("0 %", 0.86f, at(a, 0f).scaleX)
        val e = KpknMotion.Resorte.transform(0.5f)
        assertNear("50 %", 0.86f + 0.14f * e, at(a, 0.5f).scaleX)
        assertTrue("el resorte rebasa 1", KpknMotion.Resorte.transform(0.7f) > 1f)
        assertNear("100 %", 1f, at(a, 1f).scaleX)
        record("ed-bump", a)
    }

    @Test fun edPopinCreceDesdeSuAnclaConSeisDpDeDesplazamiento() {
        for ((name, anim, dy) in listOf(Triple("ed-popin-debajo", EdAnimations.PopInDown, -6f), Triple("ed-popin-encima", EdAnimations.PopInUp, 6f))) {
            assertEquals(200, anim.duration.ms)
            val start = at(anim, 0f)
            assertNear("$name alfa", 0f, start.alpha)
            assertNear("$name escala", 0.92f, start.scaleX)
            assertNear("$name desplazamiento", dy, start.translateYDp)
            val e = KpknMotion.Estandar.transform(0.5f)
            assertNear("$name 50 % escala", 0.92f + 0.08f * e, at(anim, 0.5f).scaleX)
            assertNear("$name 100 %", 1f, at(anim, 1f).alpha)
            record(name, anim)
        }
    }

    @Test fun elVeloEntraEn220msYSaleEn200msYSeQuedaTransparente() {
        assertEquals(220, EdAnimations.ScrimIn.duration.ms)
        assertNear("velo 0 %", 0f, at(EdAnimations.ScrimIn, 0f).alpha)
        assertNear("velo 100 %", 1f, at(EdAnimations.ScrimIn, 1f).alpha)
        val out = EdAnimations.ScrimOut
        assertEquals(200, out.duration.ms)
        assertNear("salida 0 %", 1f, at(out, 0f).alpha)
        assertNear("salida 100 %", 0f, at(out, 1f).alpha)
        // `forwards`: pasados los 200 ms sigue transparente
        assertNear("salida +300 ms", 0f, pose(out, 500f).alpha)
        // curva `ease` (la suave), no la estándar
        assertNear("salida 50 %", 1f - KpknMotion.Suave.transform(0.5f), at(out, 0.5f).alpha)
        record("ed-fadein", EdAnimations.ScrimIn)
        record("ed-fadeout", out)
    }

    @Test fun laHojaSubeEn340msYBajaEn240msConLaCurvaCae() {
        val up = EdAnimations.SheetUp
        assertEquals(340, up.duration.ms)
        assertNear("subida 0 %", 1f, at(up, 0f).translateYFraction)
        assertNear("subida 100 %", 0f, at(up, 1f).translateYFraction)
        assertNear("subida 50 %", 1f - KpknMotion.Estandar.transform(0.5f), at(up, 0.5f).translateYFraction)
        val down = EdAnimations.SheetDown
        assertEquals(240, down.duration.ms)
        assertNear("bajada 0 %", 0f, at(down, 0f).translateYFraction)
        assertNear("bajada 100 %", 1f, at(down, 1f).translateYFraction)
        assertNear("bajada 50 % con `cae`", KpknMotion.Cae.transform(0.5f), at(down, 0.5f).translateYFraction)
        // `forwards`: se queda abajo
        assertNear("bajada +100 ms", 1f, pose(down, 340f).translateYFraction)
        // `EdSheetSpec` (lo que lee KpknSheet) lleva los mismos tiempos y curvas
        assertEquals(220, EdSheetSpec.scrimInMs)
        assertEquals(200, EdSheetSpec.scrimOutMs)
        assertEquals(340, EdSheetSpec.sheetInMs)
        assertEquals(240, EdSheetSpec.outMs)
        assertEquals(1f, EdSheetSpec.outTravel, 0f)
        assertEquals(KpknMotion.Cae, EdSheetSpec.outEasing)
        assertEquals(KpknMotion.Estandar, EdSheetSpec.sheetEasing)
        assertEquals(KpknMotion.Suave, EdSheetSpec.scrimOutEasing)
        assertEquals(com.example.kpkn.ui.design.depth.KpknShadow.Hoja.spec, EdSheetSpec.shadow)
        record("ed-up", up)
        record("ed-down", down)
    }

    @Test fun conMovimientoReducidoElEstadoEsElFinal() {
        assertNear("hoja", 0f, pose(EdAnimations.SheetUp, 0f, reduced = true).translateYFraction)
        assertNear("velo", 1f, pose(EdAnimations.ScrimIn, 0f, reduced = true).alpha)
        assertNear("entrada", 1f, pose(EdAnimations.In, 0f, reduced = true).alpha)
        assertNear("popover", 1f, pose(EdAnimations.PopInDown, 0f, reduced = true).alpha)
        assertNear("rebote", 1f, pose(EdAnimations.Bump, 0f, reduced = true).scaleX)
    }
}
