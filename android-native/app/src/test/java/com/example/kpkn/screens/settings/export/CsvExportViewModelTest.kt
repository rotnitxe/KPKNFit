package com.example.kpkn.screens.settings.export

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.export.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CsvExportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<CsvExportViewModel>()
    private class Port : CsvExportPort {
        var preparations = 0; var writes = 0; var prepareError = false; var writeError = false
        val bundle = CsvExportBundle(listOf(CsvExportFile("kpkn-comidas-2026-10-24.csv", byteArrayOf(1))), emptyList(), LocalDate.of(2026,10,24))
        override suspend fun prepare(domains: Set<CsvExportDomain>): CsvExportBundle { preparations++; check(!prepareError) { "fallo de preparación" }; return bundle }
        override suspend fun write(destination: Uri, bundle: CsvExportBundle) { check(!writeError) { "fallo de escritura" }; writes++ }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(port: Port) = CsvExportViewModel(port, dispatcher).also { models += it }
    @Test fun noEscribeHastaRecibirDestinoYLaSolicitudSeConsumeUnaVez() {
        val port = Port(); val vm = model(port)
        vm.prepare(setOf(CsvExportDomain.MEALS)); assertEquals(0, port.writes)
        val request = requireNotNull(vm.state.value.request); assertEquals("text/csv", request.mimeType)
        vm.acknowledge(request.token); assertNull(vm.state.value.request); assertTrue(vm.state.value.busy)
        vm.destinationChosen(Uri.parse("content://csv/result")); vm.destinationChosen(Uri.parse("content://csv/result"))
        assertEquals(1, port.writes); assertFalse(vm.state.value.busy); assertNotNull(vm.state.value.notice)
    }
    @Test fun cancelarSelectorLiberaLaPreparacionSinEscribirYPermiteReintentar() {
        val port = Port(); val vm = model(port)
        vm.prepare(setOf(CsvExportDomain.MEALS)); vm.destinationChosen(null)
        assertEquals(0, port.writes); assertFalse(vm.state.value.busy)
        vm.prepare(setOf(CsvExportDomain.BODY)); assertEquals(2, port.preparations); assertNotNull(vm.state.value.request)
    }
    @Test fun dobleToqueNoCreaDosSolicitudes() {
        val port = Port(); val vm = model(port)
        vm.prepare(setOf(CsvExportDomain.MEALS)); val request = vm.state.value.request
        vm.prepare(setOf(CsvExportDomain.BODY))
        assertEquals(1, port.preparations); assertEquals(request, vm.state.value.request)
    }
    @Test fun erroresDePreparacionYEscrituraSeMuestran() {
        val port = Port().apply { prepareError = true }; val vm = model(port)
        vm.prepare(setOf(CsvExportDomain.MEALS)); assertNotNull(vm.state.value.error); assertFalse(vm.state.value.busy)
        port.prepareError = false; port.writeError = true
        vm.prepare(setOf(CsvExportDomain.MEALS)); vm.destinationChosen(Uri.parse("content://csv/result"))
        assertTrue(vm.state.value.error!!.contains("fallo de escritura")); assertFalse(vm.state.value.busy); assertEquals(0, port.writes)
    }
}
