package com.example.kpkn.screens.nutrition.scan

import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El modelo del escáner con la fuente guiada: sin cámara, sin red y con el reloj en la mano. */
class BarcodeScanViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val source = ScriptedBarcodeSource()
    private val lookups = mutableListOf<String>()
    private var haptics = 0
    private var clock = 0L
    private var answer: suspend (String) -> BarcodeLookup = { BarcodeLookup.NotFound }
    private val leche = FoodItem(id = "off_1", name = "Leche entera", calories = 60.0, protein = 3.0, carbs = 5.0, fats = 3.0, sourceRecordId = "4006381333931")

    @After fun tearDown() = scope.cancel()

    private fun model(access: CameraAccess = CameraAccess.GRANTED) = BarcodeScanViewModel(
        source = source,
        lookup = { code -> lookups += code; answer(code) },
        scope = scope,
        initialAccess = access,
        onAccepted = { haptics++ },
        now = { clock },
    )

    @Test fun `acepta un codigo tras dos lecturas iguales seguidas, vibra una vez y libera la camara`() {
        answer = { BarcodeLookup.Found(leche, BarcodeOrigin.CATALOGO) }
        val vm = model()
        assertTrue("con permiso la cámara arranca", source.running)
        source.emit("4006381333931")
        assertEquals("una sola lectura no basta", ScanPhase.SCANNING, vm.state.value.phase)
        assertTrue(lookups.isEmpty())
        source.emit("4006381333931")
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
        assertEquals(listOf("4006381333931"), lookups)
        assertEquals(1, haptics)
        assertFalse("tras acertar se detiene y libera la cámara", source.running)
        assertEquals("4006381333931", vm.state.value.code)
        assertEquals(BarcodeLookup.Found(leche, BarcodeOrigin.CATALOGO), vm.state.value.result)
    }

    @Test fun `el ruido de codigos distintos no se acepta mientras no se repita el mismo`() {
        val vm = model()
        listOf("4006381333931", "7801234567894", "4006381333931", "7801234567894").forEach { source.emit(it) }
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        assertTrue(lookups.isEmpty())
        source.emit("7801234567894")
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
        assertEquals(listOf("7801234567894"), lookups)
    }

    @Test fun `un codigo con el digito de control mal nunca se acepta ni repetido`() {
        val vm = model()
        repeat(5) { source.emit("4006381333932") }
        source.emit("texto cualquiera")
        source.emit("https://example.com/4006381333931")
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        assertTrue(lookups.isEmpty())
        assertEquals(0, haptics)
    }

    @Test fun `una lectura invalida entre dos buenas rompe la racha`() {
        val vm = model()
        source.emit("4006381333931")
        source.emit("40063813")
        source.emit("4006381333931")
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        source.emit("4006381333931")
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
    }

    @Test fun `un UPC-A y su EAN-13 con cero delante cuentan como el mismo codigo`() {
        val vm = model()
        source.emit("036000291452")
        source.emit("0036000291452")
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
        assertEquals(listOf("0036000291452"), lookups)
    }

    @Test fun `si pasa demasiado tiempo entre dos lecturas la racha empieza de nuevo`() {
        val vm = model()
        clock = 0
        source.emit("4006381333931")
        clock = BarcodeScanViewModel.STREAK_WINDOW_MS + 500
        source.emit("4006381333931")
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        clock += 100
        source.emit("4006381333931")
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
    }

    // ── Permiso ──────────────────────────────────────────────────────────────

    @Test fun `sin permiso decidido no se enciende la camara y se explica antes de pedir`() {
        val vm = model(CameraAccess.ASK)
        assertFalse(source.running)
        assertEquals(CameraAccess.ASK, vm.state.value.access)
        assertFalse(vm.state.value.showManual)
        source.emit("4006381333931")
        source.emit("4006381333931")
        assertEquals("sin cámara encendida no se acepta nada", ScanPhase.SCANNING, vm.state.value.phase)
    }

    @Test fun `al conceder el permiso arranca la camara`() {
        val vm = model(CameraAccess.ASK)
        vm.onPermissionResult(true)
        assertTrue(source.running)
        assertEquals(CameraAccess.GRANTED, vm.state.value.access)
    }

    @Test fun `si se deniega el permiso queda solo la entrada manual y la camara no arranca`() {
        val vm = model(CameraAccess.ASK)
        vm.onPermissionResult(false)
        assertEquals(CameraAccess.DENIED, vm.state.value.access)
        assertTrue(vm.state.value.manualOnly)
        assertTrue(vm.state.value.showManual)
        assertFalse(source.running)
        vm.showCamera()
        assertTrue("sin permiso no hay vuelta a la cámara", vm.state.value.showManual)
        assertFalse(source.running)
    }

    @Test fun `al volver con el permiso concedido en Ajustes el visor arranca`() {
        val vm = model(CameraAccess.ASK)
        vm.onPermissionResult(false)
        assertTrue(vm.state.value.manualOnly)
        vm.onPermissionRechecked(true)
        assertEquals(CameraAccess.GRANTED, vm.state.value.access)
        assertFalse(vm.state.value.manualOnly)
        assertTrue("con el permiso concedido la cámara vuelve a arrancar", source.running)
    }

    @Test fun `si el permiso se revoca con la pantalla abierta la camara se para y queda la entrada manual`() {
        val vm = model(CameraAccess.GRANTED)
        assertTrue(source.running)
        vm.onPermissionRechecked(false)
        assertEquals(CameraAccess.DENIED, vm.state.value.access)
        assertTrue(vm.state.value.showManual)
        assertFalse(source.running)
    }

    @Test fun `volver sin permiso y sin haberlo decidido no cambia nada`() {
        val vm = model(CameraAccess.ASK)
        vm.onPermissionRechecked(false)
        assertEquals(CameraAccess.ASK, vm.state.value.access)
        assertFalse(source.running)
    }

    @Test fun `con el permiso denegado el codigo tecleado se valida y se busca`() {
        answer = { BarcodeLookup.Found(leche, BarcodeOrigin.CATALOGO) }
        val vm = model(CameraAccess.ASK)
        vm.onPermissionResult(false)
        vm.onManualTextChange("4006 3813 33931")
        vm.submitManual()
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
        assertEquals(listOf("4006381333931"), lookups)
    }

    // ── Entrada manual ───────────────────────────────────────────────────────

    @Test fun `el campo manual solo admite digitos espacios y guiones`() {
        val vm = model()
        vm.showManualEntry()
        vm.onManualTextChange("40a06-38 1x33 3931!")
        assertEquals("4006-38 133 3931", vm.state.value.manualText)
        vm.onManualTextChange("1".repeat(40))
        assertEquals(20, vm.state.value.manualText.length)
    }

    @Test fun `el codigo manual rechaza lo vacio, las longitudes raras y un digito de control erroneo con su mensaje`() {
        val vm = model()
        vm.showManualEntry()
        vm.submitManual()
        assertEquals("Escribe el código de barras.", vm.state.value.manualError)
        vm.onManualTextChange("123")
        vm.submitManual()
        assertEquals("Un código de producto tiene 8, 12 o 13 dígitos.", vm.state.value.manualError)
        vm.onManualTextChange("4006381333932")
        vm.submitManual()
        assertEquals("El código no es válido: revisa los dígitos.", vm.state.value.manualError)
        assertTrue(lookups.isEmpty())
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        vm.onManualTextChange("4006381333931")
        assertNull("al seguir escribiendo el error se quita", vm.state.value.manualError)
    }

    @Test fun `el modo manual apaga la camara y se vuelve a ella con permiso`() {
        val vm = model()
        assertTrue(source.running)
        vm.showManualEntry()
        assertFalse(source.running)
        vm.showCamera()
        assertTrue(source.running)
        assertFalse(vm.state.value.showManual)
    }

    // ── Resultado ────────────────────────────────────────────────────────────

    @Test fun `un codigo desconocido da NotFound con el codigo y Probar otro vuelve a leer`() {
        val vm = model()
        source.emit("7801234567894")
        source.emit("7801234567894")
        assertEquals(BarcodeLookup.NotFound, vm.state.value.result)
        assertEquals("7801234567894", vm.state.value.code)
        assertFalse(source.running)
        vm.scanAnother()
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        assertNull(vm.state.value.result)
        assertNull(vm.state.value.code)
        assertTrue("vuelve a leer", source.running)
        assertEquals(2, source.startCount)
        // La racha anterior no se arrastra: hacen falta dos lecturas nuevas.
        source.emit("7801234567894")
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
    }

    @Test fun `si la consulta falla se avisa y se vuelve a leer`() {
        answer = { error("sin base") }
        val vm = model()
        source.emit("4006381333931")
        source.emit("4006381333931")
        assertEquals(ScanPhase.SCANNING, vm.state.value.phase)
        assertNotNull(vm.state.value.notice)
        assertTrue(source.running)
    }

    @Test fun `tras crear un alimento propio se muestra como encontrado y propio`() {
        val vm = model()
        source.emit("7801234567894")
        source.emit("7801234567894")
        val propio = leche.copy(id = "custom_1", isCustom = true)
        vm.showCreated(propio)
        assertEquals(BarcodeLookup.Found(propio, BarcodeOrigin.PROPIO), vm.state.value.result)
        assertEquals(ScanPhase.RESULT, vm.state.value.phase)
        assertFalse(source.running)
    }

    // ── Linterna, fallo de cámara y cierre ───────────────────────────────────

    @Test fun `la linterna se enciende y se apaga y se apaga al aceptar un codigo`() {
        val vm = model()
        vm.toggleTorch()
        assertTrue(vm.state.value.torchOn)
        assertTrue(source.torchOn)
        vm.toggleTorch()
        assertFalse(vm.state.value.torchOn)
        vm.toggleTorch()
        source.emit("4006381333931")
        source.emit("4006381333931")
        assertFalse(vm.state.value.torchOn)
        assertFalse(source.torchOn)
    }

    @Test fun `sin linterna disponible el interruptor no hace nada`() {
        val sinLinterna = ScriptedBarcodeSource(torch = false)
        val vm = BarcodeScanViewModel(sinLinterna, { BarcodeLookup.NotFound }, scope, CameraAccess.GRANTED)
        vm.toggleTorch()
        assertFalse(vm.state.value.torchOn)
        assertFalse(vm.torchAvailable.value)
    }

    @Test fun `si la camara no se puede abrir queda solo la entrada manual con un aviso`() {
        val vm = model()
        source.fail()
        assertTrue(vm.state.value.cameraFailed)
        assertTrue(vm.state.value.manualOnly)
        assertTrue(vm.state.value.showManual)
        assertNotNull(vm.state.value.notice)
        assertFalse(source.running)
    }

    @Test fun `cerrar libera la fuente y despues ya no se acepta nada`() {
        val vm = model()
        assertTrue(source.running)
        vm.close()
        assertFalse(source.running)
        assertEquals(1, source.stopCount)
        source.emit("4006381333931")
        source.emit("4006381333931")
        assertTrue(lookups.isEmpty())
        // Cerrar dos veces no rompe nada.
        vm.close()
    }

    @Test fun `cerrar con una consulta en curso la cancela y libera la fuente`() {
        val gate = kotlinx.coroutines.CompletableDeferred<BarcodeLookup>()
        answer = { gate.await() }
        val vm = model()
        source.emit("4006381333931")
        source.emit("4006381333931")
        assertEquals(ScanPhase.SEARCHING, vm.state.value.phase)
        vm.close()
        gate.complete(BarcodeLookup.NotFound)
        assertEquals("la respuesta tardía no cambia el estado", ScanPhase.SEARCHING, vm.state.value.phase)
        assertFalse(source.running)
    }
}
