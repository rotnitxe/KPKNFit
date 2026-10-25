package com.example.kpkn.screens.progress

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.AthleteProfileLevel
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleVolumePanelViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val models = mutableListOf<MuscleVolumePanelViewModel>()
    private val today = LocalDate.of(2026, 10, 21)
    private val zone = ZoneId.of("America/Santiago")

    @Before fun setup() { Dispatchers.setMain(dispatcher) }

    @After fun teardown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private val catalog = mapOf(
        "press" to ExerciseMuscleInfo(
            "press", "Press de banca",
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
            ),
        ),
    )

    private fun log(id: String, daysAgo: Long, sets: Int = 2, programId: String = "program") = WorkoutLog(
        id = id,
        programId = programId,
        sessionId = "session",
        sessionName = "Sesión",
        date = today.minusDays(daysAgo).toString(),
        durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "press-occurrence",
                exerciseName = "press",
                exerciseDbId = "press",
                sets = List(sets) { CompletedSet("set-$it", weight = 60.0, reps = 8, rpe = 7.0) },
            ),
        ),
    )

    private class Calls { var indexReads = 0 }

    private fun model(
        logs: MutableStateFlow<List<WorkoutLog>>,
        programs: MutableStateFlow<List<Program>> = MutableStateFlow(emptyList()),
        programId: String? = null,
        calls: Calls = Calls(),
    ) = MuscleVolumePanelViewModel(
        programId = programId,
        history = logs,
        programs = programs,
        exerciseIndex = { calls.indexReads++; catalog },
        today = { today },
        zone = { zone },
        computeDispatcher = dispatcher,
    ).also { models += it }

    @Test
    fun `calcula al abrir y deja de cargar`() {
        val vm = model(MutableStateFlow(listOf(log("a", 1, sets = 4))))
        val state = vm.state.value
        assertFalse(state.loading)
        assertNotNull(state.panel)
        assertEquals(MuscleVolumeWindow.WEEKS_4, state.request.window)
        assertEquals(1.0, state.panel!!.row("Pectorales")!!.sets!!, 1e-9)
    }

    @Test
    fun `cambiar la ventana recalcula con el divisor de esa ventana`() {
        val vm = model(MutableStateFlow(listOf(log("a", 1, sets = 12))))
        vm.selectWindow(MuscleVolumeWindow.WEEK_1)
        assertEquals(12.0, vm.state.value.panel!!.row("Pectorales")!!.sets!!, 1e-9)
        vm.selectWindow(MuscleVolumeWindow.WEEKS_12)
        assertEquals(1.0, vm.state.value.panel!!.row("Pectorales")!!.sets!!, 1e-9)
        assertEquals(MuscleVolumeWindow.WEEKS_12, vm.state.value.request.window)
    }

    @Test
    fun `cambiar promedio y modo de roles recalcula`() {
        val vm = model(MutableStateFlow(listOf(log("a", 1, sets = 8))))
        vm.selectAverage(false)
        assertEquals(8.0, vm.state.value.panel!!.row("Pectorales")!!.sets!!, 1e-9)
        assertEquals("series", vm.state.value.panel!!.unit)
        assertEquals(0.0, vm.state.value.panel!!.row("Tríceps")!!.sets!!, 0.0)
        vm.selectMode(VolumeCountingMode.SECONDARY)
        assertEquals(4.0, vm.state.value.panel!!.row("Tríceps")!!.sets!!, 1e-9)
        vm.selectAdjustByIntensity(true)
        assertTrue(vm.state.value.request.adjustByIntensity)
    }

    @Test
    fun `cambiar de vista lado o region no recalcula`() {
        val calls = Calls()
        val vm = model(MutableStateFlow(listOf(log("a", 1))), calls = calls)
        val before = calls.indexReads
        vm.selectView(MuscleVolumeView.BODY)
        vm.selectSide(BodySide.BACK)
        vm.selectRegion("Dorsales")
        assertEquals(before, calls.indexReads)
        vm.selectWindow(MuscleVolumeWindow.WEEKS_12)
        assertTrue(calls.indexReads > before)
    }

    @Test
    fun `la region elegida y la fila desplegada son la misma seleccion`() {
        val vm = model(MutableStateFlow(listOf(log("a", 1))))
        assertNull(vm.state.value.selectedMuscle)
        vm.selectRegion("Pectorales")
        assertEquals("Pectorales", vm.state.value.selectedMuscle)
        // Tocar la misma fila de la lista la pliega; tocar otra región cambia la selección sin plegar.
        vm.toggleMuscle("Pectorales")
        assertNull(vm.state.value.selectedMuscle)
        vm.toggleMuscle("Tríceps")
        assertEquals("Tríceps", vm.state.value.selectedMuscle)
        vm.selectRegion("Pectorales")
        assertEquals("Pectorales", vm.state.value.selectedMuscle)
        vm.selectRegion("Pectorales")
        assertEquals("Pectorales", vm.state.value.selectedMuscle)
    }

    @Test
    fun `un registro nuevo del historial recalcula el panel`() {
        val logs = MutableStateFlow(listOf(log("a", 1, sets = 4)))
        val vm = model(logs)
        assertEquals(1.0, vm.state.value.panel!!.row("Pectorales")!!.sets!!, 1e-9)
        logs.value = logs.value + log("b", 2, sets = 4)
        assertEquals(2.0, vm.state.value.panel!!.row("Pectorales")!!.sets!!, 1e-9)
    }

    @Test
    fun `sin registros el panel no tiene datos`() {
        val vm = model(MutableStateFlow(emptyList()))
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.panel!!.hasData)
    }

    @Test
    fun `con un programa solo cuentan sus registros y se muestra su nombre`() {
        val program = Program(id = "program", name = "Plan A")
        val logs = MutableStateFlow(listOf(log("mio", 1, sets = 4), log("otro", 1, sets = 4, programId = "otro")))
        val vm = model(logs, MutableStateFlow(listOf(program)), programId = "program")
        assertEquals(1, vm.state.value.panel!!.logCount)
        assertEquals("Plan A", vm.state.value.scopeName)
        assertEquals("program", vm.state.value.request.programId)
        // Sin programa, cuentan todos y no hay nombre.
        val all = model(logs)
        assertEquals(2, all.state.value.panel!!.logCount)
        assertNull(all.state.value.scopeName)
    }

    @Test
    fun `las zonas solo salen con programa calibrado y promedio por semana`() {
        val score = AthleteProfileScore(5, 5, 5, 5, TrainingStyle.BODYBUILDER, 20, AthleteProfileLevel.BEGINNER)
        val calibrated = Program(
            id = "program", name = "Plan",
            volumeRecommendations = listOf(
                VolumeRecommendation("Pectorales", 6, 12, 18),
                VolumeRecommendation("Pecho", 2, 4, 6),
            ),
            athleteProfileScore = score,
        )
        val logs = MutableStateFlow(listOf(log("a", 1)))
        val vm = model(logs, MutableStateFlow(listOf(calibrated)), programId = "program")
        val zones = vm.state.value.zones.getValue("Pectorales")
        // Las recomendaciones del mismo grupo canónico se suman.
        assertEquals(8, zones.minEffective)
        assertEquals(16, zones.maxAdaptive)
        assertEquals(24, zones.maxRecoverable)
        vm.selectAverage(false)
        assertTrue(vm.state.value.zones.isEmpty())

        val uncalibrated = calibrated.copy(athleteProfileScore = null)
        assertTrue(MuscleVolumePanelViewModel.programZones(uncalibrated).isEmpty())
        assertTrue(model(logs, MutableStateFlow(listOf(calibrated))).state.value.zones.isEmpty())
    }


}
