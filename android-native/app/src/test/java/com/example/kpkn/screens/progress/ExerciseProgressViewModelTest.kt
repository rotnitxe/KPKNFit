package com.example.kpkn.screens.progress

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.kpkn.domain.training.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ExerciseProgressViewModelTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val models=mutableListOf<ExerciseProgressViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(name: String) = ExerciseProgressViewModel("configuration:sample",MutableStateFlow(ExerciseProgressSamples.logs(name)),
        today={LocalDate.of(2026,10,24)},compute=dispatcher).also { models+=it }
    @Test fun cambiarMetricaEIntervaloRecalculaSinModificarHistorial() {
        val vm=model("strength");assertEquals(ExerciseProgressMetric.E1RM,vm.state.value.report!!.metric)
        vm.selectMetric(ExerciseProgressMetric.SETS);assertEquals(2.0,vm.state.value.report!!.points.last().value,0.0)
        vm.selectWindow(ExerciseProgressWindow.MONTH);assertEquals(ExerciseProgressWindow.MONTH,vm.state.value.window)
        assertEquals(5,vm.state.value.report!!.points.size)
    }
    @Test fun unaSesionMuestraDatosInsuficientesYNingunaNoRompe() {
        assertTrue(model("sparse").state.value.report!!.insufficient)
        assertTrue(model("empty").state.value.report!!.points.isEmpty())
    }
    @Test fun seleccionDePuntoValidaElIndice() {
        val vm=model("strength");vm.selectPoint(1);assertEquals(1,vm.state.value.selectedPoint)
        vm.selectPoint(1000);assertNull(vm.state.value.selectedPoint)
    }
}
