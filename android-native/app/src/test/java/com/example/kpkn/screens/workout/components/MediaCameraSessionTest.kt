package com.example.kpkn.screens.workout.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cámara de «Modo visual» al expandir y plegar (LV8): la cámara (los casos de uso de CameraX) y la vista donde se pinta su vista previa son
 * cosas distintas. Expandir y plegar solo cambian la vista; la cámara no se suelta ni se vuelve a unir, y una grabación no se corta.
 */
class MediaCameraSessionTest {

    private val pintadoEn = mutableListOf<Any?>()
    private val tarjeta = Any()
    private val overlay = Any()

    private fun sesion() = MediaCameraSession(showPreviewOn = { pintadoEn += it })

    @Test
    fun unProveedorQueLlegaDespuesDeSalirNoVuelveAAbrirLaCamara() {
        val lease = MediaCameraBindingLease()
        var bindings = 0
        lease.bindIfActive { bindings += 1 }
        lease.close()
        lease.bindIfActive { bindings += 1 }
        assertEquals(1, bindings)
    }

    @Test
    fun alExpandirYPlegarLaCamaraSeUneUnaSolaVezYNoSeSuelta() {
        val sesion = sesion()
        sesion.onBound()
        sesion.attach(tarjeta)

        // Expandir: el overlay trae su vista y la de la tarjeta se va (en cualquier orden).
        sesion.attach(overlay)
        sesion.detach(tarjeta)
        assertSame(overlay, sesion.surface)

        // Plegar: vuelve la vista de la tarjeta y se va la del overlay.
        sesion.attach(tarjeta)
        sesion.detach(overlay)
        assertSame(tarjeta, sesion.surface)

        assertEquals("la cámara se unió una sola vez", 1, sesion.bindCount)
        assertEquals("y no se soltó al expandir ni al plegar", 0, sesion.unbindCount)
        assertTrue(sesion.bound)
    }

    @Test
    fun conLaNuevaVistaYaPuestaLaViejaAlIrseNoLaQuita() {
        val sesion = sesion()
        sesion.attach(tarjeta)
        sesion.attach(overlay)

        sesion.detach(tarjeta)

        assertSame("la vista previa sigue en el overlay", overlay, sesion.surface)
        assertSame(overlay, pintadoEn.last())
    }

    @Test
    fun siSeVaLaUltimaVistaLaVistaPreviaSeApagaSinSoltarLaCamara() {
        val sesion = sesion()
        sesion.onBound()
        sesion.attach(tarjeta)

        sesion.detach(tarjeta)

        assertNull(sesion.surface)
        assertNull(pintadoEn.last())
        assertTrue("la cámara sigue unida", sesion.bound)
    }

    @Test
    fun mientrasSeGrabaNuncaSeSueltaLaCamara() {
        val sesion = sesion()
        sesion.onBound()

        val sueltaGrabando = sesion.release(isRecording = true)

        assertFalse("no se suelta grabando", sueltaGrabando)
        assertTrue(sesion.bound)
        assertEquals(0, sesion.unbindCount)
    }

    @Test
    fun sinGrabarSalirDeLaCaraSueltaLaCamara() {
        val sesion = sesion()
        sesion.onBound()

        val suelta = sesion.release(isRecording = false)

        assertTrue(suelta)
        assertFalse(sesion.bound)
        assertEquals(1, sesion.unbindCount)
    }

    @Test
    fun alDejarDeSerLaPaginaActivaSeCortaLaGrabacionYSePliegaElExpandido() {
        val llamadas = mutableListOf<String>()

        onMediaPageSettledChanged(
            isSettledPage = false,
            stopIfRecording = { llamadas += "cortar" },
            foldExpanded = { llamadas += "plegar" },
        )

        assertEquals(listOf("cortar", "plegar"), llamadas)
    }

    @Test
    fun conLaPaginaAsentadaNoSeCortaNiSePliegaNada() {
        val llamadas = mutableListOf<String>()

        onMediaPageSettledChanged(
            isSettledPage = true,
            stopIfRecording = { llamadas += "cortar" },
            foldExpanded = { llamadas += "plegar" },
        )

        assertTrue(llamadas.isEmpty())
    }

    @Test
    fun laCamaraSoloEstaActivaConPermisoPaginaAsentadaYControlador() {
        assertTrue(isMediaCameraActive(permissionGranted = true, isSettledPage = true, hasCapture = true))
        assertFalse("sin permiso", isMediaCameraActive(permissionGranted = false, isSettledPage = true, hasCapture = true))
        assertFalse("en una página que no es la activa", isMediaCameraActive(permissionGranted = true, isSettledPage = false, hasCapture = true))
        assertFalse("sin controlador de captura", isMediaCameraActive(permissionGranted = true, isSettledPage = true, hasCapture = false))
    }

    @Test
    fun grabandoDuranteUnaExpansionYUnPlegadoLaGrabacionNoSeCorta() {
        val sesion = sesion()
        sesion.onBound()
        sesion.attach(tarjeta)
        var grabando = true

        // Expandir y plegar con la grabación en marcha: la cámara no se toca y la cara no la suelta.
        sesion.attach(overlay); sesion.detach(tarjeta)
        sesion.attach(tarjeta); sesion.detach(overlay)
        val sueltaAlSalir = sesion.release(isRecording = grabando)

        assertFalse(sueltaAlSalir)
        assertEquals(1, sesion.bindCount)
        assertEquals(0, sesion.unbindCount)
        grabando = false
        assertTrue("al acabar de grabar ya se puede soltar", sesion.release(isRecording = grabando))
    }

    @Test fun cederAlGrabadorLiberaSoloLaCamaraAnteriorSinDetenerLaCapturaNueva() {
        val session = sesion()
        session.onBound()
        var stops = 0
        session.stopOwnedRecording(stopOnDispose = false) { stops++ }
        assertTrue(session.release(isRecording = false))
        assertEquals(0, stops)
        assertFalse(session.bound)
        assertEquals(1, session.unbindCount)
    }

    @Test fun cerrarNormalmenteConservaLaOrdenDeDetenerLaGrabacionPropia() {
        val session = sesion()
        session.onBound()
        var stops = 0
        session.stopOwnedRecording(stopOnDispose = true) { stops++ }
        assertEquals(1, stops)
        assertFalse(session.release(isRecording = true))
        assertTrue(session.release(isRecording = false))
    }
}
