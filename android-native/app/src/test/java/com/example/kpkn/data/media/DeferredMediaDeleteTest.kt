package com.example.kpkn.data.media

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El borrado aplazado del álbum (LV8): al borrar, el elemento deja de verse pero no se toca nada; el borrado definitivo ocurre al
 * caducar el aviso, al llegar otro borrado o al salir del álbum, nunca antes, y si se deshace no se toca nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeferredMediaDeleteTest {

    private fun media(id: String, kind: WorkoutMediaKind = WorkoutMediaKind.VIDEO) =
        WorkoutMedia(id = id, kind = kind, filePath = "/medios/$id", createdAtMs = 1_000L)

    private val borrados = mutableListOf<List<String>>()

    private fun conVentana(ventana: Long = 5_000L, scope: kotlinx.coroutines.CoroutineScope) = DeferredMediaDelete(
        scope = scope,
        commit = { items -> borrados += items.map { it.id } },
        undoWindowMs = ventana,
    )

    @Test
    fun alBorrarElElementoSeOcultaPeroNoSeTocaNada() = runTest {
        val borrado = conVentana(scope = backgroundScope)

        borrado.schedule(listOf(media("a")))

        assertEquals(setOf("a"), borrado.hiddenIds())
        assertEquals("el aviso lo lleva", listOf("a"), borrado.pending.value!!.items.map { it.id })
        assertTrue("antes de caducar no se borra nada", borrados.isEmpty())
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue("con el aviso aún vivo no se borra nada", borrados.isEmpty())
    }

    @Test
    fun alCaducarElAvisoSeBorraDeVerdadUnaSolaVez() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        borrado.schedule(listOf(media("a")))

        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(listOf(listOf("a")), borrados)
        assertNull("el aviso desaparece", borrado.pending.value)
        assertEquals("no reaparece mientras Room termina el borrado", setOf("a"), borrado.hiddenIds())
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals("no se borra dos veces", 1, borrados.size)
    }

    @Test
    fun siSeDeshaceNoSeTocaNadaNiAlCaducarLaVentana() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        borrado.schedule(listOf(media("a")))
        advanceTimeBy(2_000L)

        val devuelto = borrado.undo()

        assertEquals(listOf("a"), devuelto!!.map { it.id })
        assertTrue("vuelve a verse", borrado.hiddenIds().isEmpty())
        assertNull(borrado.pending.value)
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue("deshacer no borra nada, ni después", borrados.isEmpty())
    }

    @Test
    fun deshacerSinNadaEnEsperaNoHaceNada() = runTest {
        val borrado = conVentana(scope = backgroundScope)

        assertNull(borrado.undo())
        borrado.flush()

        assertTrue(borrados.isEmpty())
    }

    @Test
    fun alSalirDelAlbumSeBorraYaLoQueSeguiaEnEspera() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        borrado.schedule(listOf(media("a"), media("b", WorkoutMediaKind.PHOTO)))
        advanceTimeBy(1_000L)
        assertTrue("antes de salir, nada", borrados.isEmpty())

        borrado.flush()

        assertEquals(listOf(listOf("a", "b")), borrados)
        assertNull(borrado.pending.value)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals("el temporizador ya no borra otra vez", 1, borrados.size)
    }

    @Test
    fun unBorradoNuevoHaceCaducarElAnteriorYSoloElUltimoSePuedeDeshacer() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        borrado.schedule(listOf(media("a")))
        advanceTimeBy(1_000L)

        borrado.schedule(listOf(media("b")))

        assertEquals("el anterior caduca al llegar el nuevo", listOf(listOf("a")), borrados)
        assertEquals(setOf("a", "b"), borrado.hiddenIds())
        val devuelto = borrado.undo()
        assertEquals(listOf("b"), devuelto!!.map { it.id })
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals("lo deshecho no se borra", listOf(listOf("a")), borrados)
    }

    @Test
    fun elTemporizadorDelBorradoNuevoEmpiezaDeNuevo() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        borrado.schedule(listOf(media("a")))
        advanceTimeBy(4_000L)
        borrado.schedule(listOf(media("b")))

        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals("el segundo aviso aún vive a los 4 s", listOf(listOf("a")), borrados)
        assertNotNull(borrado.pending.value)
        advanceTimeBy(1_001L)
        runCurrent()
        assertEquals(listOf(listOf("a"), listOf("b")), borrados)
    }

    @Test
    fun unFalloAsincronoDevuelveElRegistroSinPermitirDeshacerUnBorradoCaducado() = runTest {
        val borrado = conVentana(scope = backgroundScope)
        val item = media("a")
        borrado.schedule(listOf(item))
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(setOf("a"), borrado.hiddenIds())

        borrado.restoreFailed(listOf(item))

        assertTrue(borrado.hiddenIds().isEmpty())
        assertNull(borrado.undo())
    }

    @Test
    fun unBorradoVacioNoHaceNada() = runTest {
        val borrado = conVentana(scope = backgroundScope)

        borrado.schedule(emptyList())

        assertNull(borrado.pending.value)
        assertFalse(borrado.hiddenIds().isNotEmpty())
    }

    @Test
    fun siElBorradoDefinitivoFallaSeAvisaYNoSeCuelga() = runTest {
        val fallos = mutableListOf<String>()
        val borrado = DeferredMediaDelete(
            scope = backgroundScope,
            commit = { throw IllegalStateException("sin disco") },
            onCommitFailed = { items, error -> fallos += "${items.size}:${error.message}" },
        )
        borrado.schedule(listOf(media("a")))

        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(listOf("1:sin disco"), fallos)
        assertNull(borrado.pending.value)
    }
}
