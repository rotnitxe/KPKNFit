package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.screens.sessioneditor.computeDragSlotRect
import com.example.kpkn.screens.sessioneditor.frame.FrameAddEntry
import com.example.kpkn.screens.sessioneditor.frame.EdPopoverSide
import com.example.kpkn.screens.sessioneditor.frame.edPopoverPlacement
import com.example.kpkn.screens.sessioneditor.frame.frameAddEntries
import com.example.kpkn.screens.sessioneditor.frame.frameDeltaLabel
import com.example.kpkn.screens.sessioneditor.withoutExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pulido v1 · EDITOR · A2: la lógica de los gestos (borrar deslizando y ordenar arrastrando), de la burbuja de minutos y del menú «Añadir».
 * Todo en dp, con los valores de `scenes-ed.js` (ed-J13 … ed-J41).
 */
class EditorSwipeDeleteLogicTest {

    // ───────────────────────────── borrar deslizando ─────────────────────────────

    @Test
    fun hastaElUmbralLaFilaSigueAlDedoYDespuesOfreceResistencia() {
        assertEquals(0f, edRubber(0f), 0.0001f)
        assertEquals(60f, edRubber(60f), 0.0001f)
        assertEquals(104f, edRubber(104f), 0.0001f)
        // Más allá del umbral avanza el 42 % de lo que se desliza el dedo: 104 + 46 · 0,42.
        assertEquals(123.32f, edRubber(150f), 0.001f)
        assertEquals(104f + 96f * 0.42f, edRubber(200f), 0.001f)
    }

    @Test
    fun deslizarAlaDerechaNoMueveLaFila() {
        assertEquals(0f, edReveal(40f), 0f)
        assertEquals(60f, edReveal(-60f), 0.0001f)
        assertEquals(edRubber(150f), edReveal(-150f), 0.0001f)
    }

    @Test
    fun laPildoraSeEnciendeEnElUmbralDe104() {
        assertFalse(edArmed(103.9f))
        assertTrue(edArmed(104f))
        assertTrue(edArmed(edReveal(-150f)))
    }

    @Test
    fun laPildoraMideLoDeslizadoMenos10YApareceDespuesDe4() {
        assertEquals(0f, edPillWidth(6f), 0f)
        assertEquals(50f, edPillWidth(60f), 0.0001f)
        assertFalse(edPillVisible(4f))
        assertTrue(edPillVisible(4.1f))
        // Las capturas del lienzo: con 150 de recorrido la píldora enseña ≈ 113 dp.
        assertEquals(113.32f, edPillWidth(edReveal(-150f)), 0.001f)
    }

    @Test
    fun elGestoSeBloqueaHaciaLaIzquierdaOSeEntregaAlScroll() {
        // Poco movimiento: sin decidir.
        assertEquals(EdSwipeDecision.Undecided, edSwipeDecision(-5f, 1f))
        // Más de 8 a la izquierda y claramente horizontal: es del borrado.
        assertEquals(EdSwipeDecision.Locked, edSwipeDecision(-12f, 4f))
        // Horizontal pero no 1,3 veces la vertical: sigue sin decidir.
        assertEquals(EdSwipeDecision.Undecided, edSwipeDecision(-12f, 10f))
        // Hacia la derecha nunca se bloquea.
        assertEquals(EdSwipeDecision.Undecided, edSwipeDecision(30f, 2f))
        // Más de 10 en vertical y más que en horizontal: es del scroll.
        assertEquals(EdSwipeDecision.Scroll, edSwipeDecision(-4f, 14f))
        assertEquals(EdSwipeDecision.Scroll, edSwipeDecision(4f, -11f))
    }

    @Test
    fun soltarBorraPasadoElUmbralOConVelocidad() {
        assertTrue(edShouldCommit(104f, 0f))
        assertTrue(edShouldCommit(130f, 0.1f))
        assertFalse(edShouldCommit(90f, 0.2f))
        // Con velocidad (más de 0,9 dp/ms) basta pasar de 60.
        assertTrue(edShouldCommit(61f, 1.2f))
        assertFalse(edShouldCommit(59f, 1.2f))
        assertFalse(edShouldCommit(80f, 0.9f))
    }

    @Test
    fun elAutoscrollVaMasRapidoCuantoMasCercaDelBorde() {
        // Lista de 0 a 880 con densidad 1: zona de arriba hasta 150, zona de abajo desde 710.
        assertEquals(0f, edAutoScrollDelta(400f, 0f, 880f, 1f), 0f)
        assertEquals(-8f, edAutoScrollDelta(75f, 0f, 880f, 1f), 0.001f)
        assertEquals(-16f, edAutoScrollDelta(0f, 0f, 880f, 1f), 0.001f)
        assertEquals(-16f, edAutoScrollDelta(-40f, 0f, 880f, 1f), 0.001f)
        assertEquals(8f, edAutoScrollDelta(795f, 0f, 880f, 1f), 0.001f)
        assertEquals(16f, edAutoScrollDelta(880f, 0f, 880f, 1f), 0.001f)
        // Con densidad 3 los px se escalan: 16 dp = 48 px.
        assertEquals(-48f, edAutoScrollDelta(0f, 0f, 2640f, 3f), 0.01f)
    }

    // ───────────────────────────── la burbuja de minutos y el menú «Añadir» ─────────────────────────────

    @Test
    fun laBurbujaDiceElSignoYLosMinutos() {
        assertEquals("+9 min", frameDeltaLabel(9))
        assertEquals("−10 min", frameDeltaLabel(-10))
        assertEquals("+1 min", frameDeltaLabel(1))
    }

    @Test
    fun elMenuAnadirOfreceEjercicioCardioYGrupoYLosEspaciosQueFaltan() {
        assertEquals(
            listOf(FrameAddEntry.EXERCISE, FrameAddEntry.CARDIO, FrameAddEntry.GROUP),
            frameAddEntries(hasStrengthSpace = true, hasCardioSpace = true),
        )
        assertEquals(
            listOf(FrameAddEntry.EXERCISE, FrameAddEntry.CARDIO_SPACE, FrameAddEntry.GROUP),
            frameAddEntries(hasStrengthSpace = true, hasCardioSpace = false),
        )
        assertEquals(
            listOf(FrameAddEntry.EXERCISE, FrameAddEntry.CARDIO, FrameAddEntry.STRENGTH_SPACE),
            frameAddEntries(hasStrengthSpace = false, hasCardioSpace = true),
        )
    }

    @Test
    fun elPopoverSeColocaComoElLienzo() {
        // «⋯»: debajo de la isla (a 6 dp), con el borde derecho alineado y 12 dp de margen.
        val island = Rect(12f, 36f, 348f, 92f)
        val below = edPopoverPlacement(EdPopoverSide.BelowEnd, island, 208, 116, 360, 880, 1f)
        assertEquals(140, below.x)
        assertEquals(98, below.y)
        assertEquals(0f, below.originYFraction, 0f)
        // «Añadir»: encima de la barra, a 8 dp, alineado a la izquierda del botón y sin pasarse del borde.
        val bar = Rect(12f, 804f, 348f, 868f)
        val add = Rect(120f, 804f, 280f, 868f)
        val above = edPopoverPlacement(EdPopoverSide.AboveStart, Rect(add.left, bar.top, add.right, bar.bottom), 232, 156, 360, 880, 1f)
        assertEquals(116, above.x)
        assertEquals(804 - 8 - 156, above.y)
        assertEquals(1f, above.originYFraction, 0f)
        val clamped = edPopoverPlacement(EdPopoverSide.AboveStart, Rect(300f, 804f, 340f, 868f), 232, 156, 360, 880, 1f)
        assertEquals(360 - 12 - 232, clamped.x)
    }

    // ───────────────────────────── ordenar arrastrando: el hueco ─────────────────────────────

    private fun row(top: Float, h: Float = 68f) = Rect(0f, top, 360f, top + h)

    /** Cuatro filas de 68 dp (0, 68, 136, 204); la arrastrada es la de índice 1. */
    private val others = listOf(0 to row(0f), 2 to row(136f), 3 to row(204f))

    @Test
    fun elHuecoSeQuedaEnSuSitioSiNoHayMovimiento() {
        val dragged = row(68f)
        assertEquals(dragged, computeDragSlotRect(dragged, 68f, true, 1, 1, others))
        assertEquals(dragged, computeDragSlotRect(dragged, 68f, true, 1, 2, others))
    }

    @Test
    fun elHuecoSubeAlSitioDeLaFilaDeDestino() {
        // Se arrastra la fila 3 hacia el destino 1: el hueco ocupa el sitio de la fila 1 (arriba, a 68).
        val entries = listOf(0 to row(0f), 1 to row(68f), 2 to row(136f))
        val slot = computeDragSlotRect(row(204f), 68f, true, 3, 1, entries)
        assertNotNull(slot)
        assertEquals(68f, slot!!.top, 0.0001f)
        assertEquals(136f, slot.bottom, 0.0001f)
    }

    @Test
    fun elHuecoBajaPegadoDebajoDeLaFilaAnteriorAlDestino() {
        // Se arrastra la fila 0 al destino 3 (antes de la fila 3): queda debajo de la fila 2 (que sube y ocupa el sitio de la 0).
        val entries = listOf(1 to row(68f), 2 to row(136f), 3 to row(204f))
        val slot = computeDragSlotRect(row(0f), 68f, true, 0, 3, entries)
        assertEquals(136f, slot!!.top, 0.0001f)
        assertEquals(204f, slot.bottom, 0.0001f)
    }

    @Test
    fun enOtraListaElHuecoOcupaElSitioDeLaFilaDeDestinoOQuedaAlFinal() {
        val target = listOf(0 to row(400f), 1 to row(468f))
        val inside = computeDragSlotRect(row(0f), 68f, false, 0, 1, target)
        assertEquals(468f, inside!!.top, 0.0001f)
        val atEnd = computeDragSlotRect(row(0f), 68f, false, 0, 2, target)
        assertEquals(536f, atEnd!!.top, 0.0001f)
        // Sin filas en el destino, el hueco se queda en su sitio.
        assertEquals(row(0f), computeDragSlotRect(row(0f), 68f, false, 0, 0, emptyList()))
    }

    // ───────────────────────────── los minutos y las fichas bajan al borrar ─────────────────────────────

    @Test
    fun laSesionSinUnEjercicioPierdeSoloEseEjercicio() {
        val a = Exercise(id = "a", name = "Sentadilla")
        val b = Exercise(id = "b", name = "Press")
        val c = Exercise(id = "c", name = "Remo")
        val session = Session(
            id = "s",
            name = "Legs",
            exercises = listOf(a, b),
            parts = listOf(SessionPart(id = "p", name = "Grupo", exercises = listOf(c))),
        )
        val withoutB = session.withoutExercise("b")
        assertEquals(listOf("a"), withoutB.exercises.map { it.id })
        assertEquals(listOf("c"), withoutB.parts.single().exercises.map { it.id })
        val withoutC = session.withoutExercise("c")
        assertEquals(listOf("a", "b"), withoutC.exercises.map { it.id })
        assertTrue(withoutC.parts.single().exercises.isEmpty())
        assertNull(session.withoutExercise("zzz").exercises.firstOrNull { it.id == "zzz" })
        assertEquals(session.exercises, session.withoutExercise("zzz").exercises)
    }
}
