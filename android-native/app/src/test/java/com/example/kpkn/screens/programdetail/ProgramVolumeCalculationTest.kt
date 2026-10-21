package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.TrainingVolumeFixtures
import com.example.kpkn.screens.programdetail.components.calculateProgramVolume
import com.example.kpkn.screens.programdetail.design.programVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.programVolumeLines
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class ProgramVolumeCalculationTest {
    private val f = TrainingVolumeFixtures

    @Test
    fun `sin calibracion Real es el promedio semanal del alcance y no el total`() {
        val weeks = (0..2).map { f.week("w$it", f.today.minusWeeks(2L - it), sessions = 2) }
        val program = f.program(weeks)
        val logs = weeks.flatMap { week -> List(2) { f.log("${week.id}-$it", week.startDate!!) } }
        val result = calculateProgramVolume(program, weeks, true, logs, f.catalog, zone = f.zone, today = f.today)
        assertTrue(program.volumeRecommendations.isEmpty())
        assertEquals(4.0, result.completed.muscles.getValue("Pectorales").sets, 1e-9)
        assertEquals("4", programVolumeSeries(result.completed.muscles.getValue("Pectorales").sets))
        assertEquals(4.0, result.planned.muscles.getValue("Pectorales").sets, 1e-9)
        val calibrated = program.copy(volumeRecommendations = listOf(VolumeRecommendation("Pectorales", 4, 12, 18)))
        val afterCalibration = calculateProgramVolume(calibrated, weeks, true, logs, f.catalog, zone = f.zone, today = f.today)
        assertEquals(result.completed, afterCalibration.completed)
    }

    @Test
    fun `Real sigue la semana elegida y cuenta cero en una semana vacia`() {
        val weeks = listOf(f.week("w1", f.today.minusWeeks(1), sessions = 1), f.week("w2", f.today))
        val logs = listOf(f.log("anterior", weeks.first().startDate!!, sets = 6))
        val result = calculateProgramVolume(f.program(weeks), listOf(weeks.last()), false, logs, f.catalog, zone = f.zone, today = f.today)
        assertTrue(result.completed.muscles.isEmpty())
        assertTrue(result.planned.muscles.isEmpty())
        assertEquals(1.0, result.window.weekDivisor, 0.0)
    }

    @Test
    fun `Real y plan usan los mismos modos e intensidad con el mismo trabajo`() {
        val muscles = listOf(
            InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"),
            InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.5, "anterior"),
            InvolvedMuscle("Trapecio", MuscleRole.STABILIZER, 0.4, "superior"),
        )
        val catalog = mapOf("press" to f.catalog.getValue("press").copy(involvedMuscles = muscles))
        val weeks = listOf(f.week("w", f.today, sessions = 1))
        val logs = listOf(f.log("real"))
        for (mode in 0..2) for (intensity in listOf(false, true)) {
            val result = calculateProgramVolume(
                f.program(weeks), weeks, false, logs, catalog, mode, intensity, f.zone, f.today,
            )
            assertEquals("modo=$mode, intensidad=$intensity", result.planned.muscles, result.completed.muscles)
        }
    }

    @Test
    fun `las lineas redondeadas suman la cifra visible de la barra y de cada porcion`() {
        val muscles = listOf(InvolvedMuscle("Deltoides", MuscleRole.PRIMARY, 1.0, "anterior"))
        val sessions = (0..2).map { index ->
            Session("s$index", "Sesión", exercises = listOf(f.exercise("e$index", 1, muscles).copy(exerciseDbId = "e$index")))
        }
        val weeks = (0..2).map { f.week("w$it", f.today.minusWeeks(it.toLong())) }
            .mapIndexed { i, week -> if (i == 0) week.copy(sessions = sessions) else week }
        for (mode in 0..2) for (intensity in listOf(false, true)) {
            val result = calculateProgramVolume(f.program(weeks), weeks, true, emptyList(), emptyMap(), mode, intensity, f.zone, f.today)
            val entry = result.planned.muscles.getValue("Deltoides")
            val lines = programVolumeLines(entry.exercises)
            assertEquals((entry.sets * 10.0).roundToInt(), lines.sumOf { (it.sets * 10.0).roundToInt() })
            val breakdown = programVolumeBreakdown("Deltoides", entry)
            assertTrue(breakdown.subMuscles.any { it.name == "Deltoides Posterior" && it.sets == 0.0 })
            breakdown.subMuscles.forEach { portion ->
                assertEquals((portion.sets * 10.0).roundToInt(), portion.exercises.sumOf { (it.sets * 10.0).roundToInt() })
            }
        }
    }

    @Test
    fun `la proyeccion conserva el recuento de registros sin fecha`() {
        val weeks = listOf(f.week("w", f.today, sessions = 1))
        val result = calculateProgramVolume(
            f.program(weeks), weeks, false, listOf(f.log("ilegible", "")), f.catalog, zone = f.zone, today = f.today,
        )
        assertTrue(result.completed.muscles.isEmpty())
        assertEquals(1, result.completed.unassignedCount)
    }
}
