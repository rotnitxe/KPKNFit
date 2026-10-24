package com.example.kpkn.screens.nutrition.customfood

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.nutrition.label.*
import com.example.kpkn.domain.nutrition.label.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LabelReadViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<LabelReadViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun label(basis: LabelBasis = LabelBasis.PER_100G, size: Double? = 30.0) = LabelParse(basis, size, "g", mapOf(
        LabelField.ENERGY_KCAL to LabelValue(200.0, LabelConfidence.READ, "200 kcal"),
        LabelField.PROTEIN_G to LabelValue(10.0, LabelConfidence.UNSURE, "1O g")))
    private fun model(initial: CustomFoodForm = CustomFoodForm(name = "Mi alimento", brand = "Marca", calories = "99", fats = "9"),
        result: LabelReadResult = LabelReadResult.Ready(label())) = LabelReadViewModel(initial, object : LabelReaderPort {
        override suspend fun read(uri: Uri) = result
    }).also { models += it }
    @Test fun soloRellenaLoLeidoYDeshacerRestauraTodosLosTextos() {
        val vm = model(); val before = vm.state.value.form
        vm.read(Uri.EMPTY)
        assertEquals("Mi alimento", vm.state.value.form.name); assertEquals("Marca", vm.state.value.form.brand)
        assertEquals("200", vm.state.value.form.calories); assertEquals("", vm.state.value.form.fats)
        assertEquals(LabelConfidence.UNSURE, vm.state.value.markers[CustomFoodField.PROTEIN])
        assertTrue(vm.state.value.canUndo)
        vm.undo(); assertEquals(before, vm.state.value.form); assertTrue(vm.state.value.markers.isEmpty())
    }
    @Test fun convertirBaseConservaLaCorreccionManual() {
        val vm = model(); vm.read(Uri.EMPTY)
        vm.editForm(vm.state.value.form.copy(protein = "12"))
        vm.selectBasis(LabelBasis.PER_SERVING)
        assertEquals("3.6", vm.state.value.form.protein); assertEquals("60", vm.state.value.form.calories)
        assertFalse(vm.state.value.markers.containsKey(CustomFoodField.PROTEIN))
        assertEquals(LabelConfidence.DERIVED, vm.state.value.markers[CustomFoodField.CALORIES])
        vm.selectBasis(LabelBasis.PER_100G); assertEquals("12", vm.state.value.form.protein)
    }
    @Test fun conversionSinPorcionConservaCifrasYExplicaElProblema() {
        val vm = model(result = LabelReadResult.Ready(label(size = null))); vm.read(Uri.EMPTY)
        val before = vm.state.value.form; vm.selectBasis(LabelBasis.PER_SERVING)
        assertEquals(before, vm.state.value.form); assertNotNull(vm.state.value.error)
    }
    @Test fun baseDesconocidaExigeConfirmacionExplicita() {
        val vm = model(result = LabelReadResult.Ready(label(LabelBasis.UNKNOWN))); vm.read(Uri.EMPTY)
        assertTrue(vm.state.value.needsBasisConfirmation)
        vm.selectBasis(LabelBasis.PER_100ML)
        assertFalse(vm.state.value.needsBasisConfirmation)
        assertEquals("100", vm.state.value.form.servingAmount); assertEquals("ml", vm.state.value.form.servingUnit)
        assertEquals(CustomFoodBasis.PER_SERVING, vm.state.value.form.basis)
    }
    @Test fun cienMililitrosConservaReferenciaSinInventarDensidad() {
        val vm = model(result = LabelReadResult.Ready(label(LabelBasis.PER_100ML))); vm.read(Uri.EMPTY)
        assertEquals("ml", vm.state.value.form.servingUnit); assertEquals("100", vm.state.value.form.servingAmount)
        assertEquals("200", vm.state.value.form.calories)
    }
    @Test fun erroresYSinTablaConservanElFormulario() {
        listOf(LabelReadResult.NoTable(), LabelReadResult.Failed("Imagen ilegible", true)).forEach { result ->
            val vm = model(result = result); val before = vm.state.value.form; vm.read(Uri.EMPTY)
            assertEquals(before, vm.state.value.form); assertNotNull(vm.state.value.error); assertFalse(vm.state.value.canUndo)
        }
    }
    @Test fun cancelarIgnoraResultadoTardioAunqueElLectorNoSeaCancelable() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val vm = LabelReadViewModel(CustomFoodForm(name = "Antes"), object : LabelReaderPort {
            override suspend fun read(uri: Uri): LabelReadResult = withContext(NonCancellable) { release.await(); LabelReadResult.Ready(label()) }
        }).also { models += it }
        vm.read(Uri.EMPTY); assertTrue(vm.state.value.reading)
        vm.cancelReading(); release.complete(Unit); runCurrent()
        assertFalse(vm.state.value.reading); assertEquals("", vm.state.value.form.calories); assertFalse(vm.state.value.canUndo)
    }
    @Test fun cambiarLaPorcionDeReferenciaNoSiguePresentandoLosValoresComoLeidosPorCien() {
        val vm=model(result=LabelReadResult.Ready(label(LabelBasis.PER_100ML)));vm.read(Uri.EMPTY)
        vm.editForm(vm.state.value.form.copy(servingAmount="30"))
        assertEquals(LabelBasis.PER_SERVING,vm.state.value.label!!.basis)
        assertTrue(vm.state.value.markers.isEmpty());assertTrue(vm.state.value.label!!.warnings.any { "manualmente" in it })
        assertEquals("200",vm.state.value.form.calories)
    }
}
