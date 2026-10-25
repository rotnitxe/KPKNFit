package com.example.kpkn.screens.progress

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.MuscleVolumePanel
import com.example.kpkn.domain.training.MuscleVolumePanelBuilder
import com.example.kpkn.domain.training.MuscleVolumePanelRequest
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeShade
import com.example.kpkn.ui.components.rings.RingAdjustBodyPillars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Modelo de color del cuerpo (T5a): se prueba la decisión, no los píxeles. */
class MuscleVolumeBodyModelTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val zone = ZoneId.of("America/Santiago")
    private val catalog = mapOf(
        "press" to ExerciseMuscleInfo(
            "press", "Press de banca",
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
            ),
        ),
        "curl" to ExerciseMuscleInfo("curl", "Curl", involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 1.0))),
    )

    private fun log(id: String, daysAgo: Long, exercise: String, sets: Int) = WorkoutLog(
        id = id, programId = "program", sessionId = "s", sessionName = "Sesión",
        date = today.minusDays(daysAgo).toString(), durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "$exercise-o", exerciseName = exercise, exerciseDbId = exercise,
                sets = List(sets) { CompletedSet("set-$it", weight = 50.0, reps = 8, rpe = 7.0) },
            ),
        ),
    )

    private fun panel(logs: List<WorkoutLog>, window: MuscleVolumeWindow = MuscleVolumeWindow.WEEK_1): MuscleVolumePanel =
        MuscleVolumePanelBuilder.build(logs, catalog, MuscleVolumePanelRequest(window = window), today, zone)

    @Test
    fun `una ventana sin registros pinta todas las regiones sin datos`() {
        val paints = MuscleVolumeBodyModel.volumePaints(panel(emptyList()))
        assertEquals(RingAdjustBodyPillars.toSet(), paints.keys)
        assertTrue(paints.values.all { it.kind == RegionKind.NO_DATA })
        // Sin panel (cargando) tampoco se afirma nada.
        assertTrue(MuscleVolumeBodyModel.volumePaints(null).values.all { it.kind == RegionKind.NO_DATA })
    }

    @Test
    fun `sin datos y cero son pinturas distintas`() {
        val withData = MuscleVolumeBodyModel.volumePaints(panel(listOf(log("a", 0, "press", 4))))
        val noData = MuscleVolumeBodyModel.volumePaint(VolumeShade.NO_DATA)
        val zero = withData.getValue("Bíceps")
        assertEquals(RegionKind.ZERO, zero.kind)
        assertEquals(RegionKind.NO_DATA, noData.kind)
        assertNotEquals(zero.edge, noData.edge)
        assertNotEquals(zero.fill, noData.fill)
        // El cero real no se confunde con una región trabajada.
        assertNotEquals(RegionKind.FILLED, zero.kind)
    }

    @Test
    fun `cada region pinta el color de la intensidad de su fila`() {
        val data = panel(listOf(log("a", 0, "press", 10), log("b", 1, "curl", 2)))
        val paints = MuscleVolumeBodyModel.volumePaints(data)
        RingAdjustBodyPillars.forEach { name ->
            assertEquals(name, MuscleVolumeBodyModel.volumePaint(data.row(name)!!.shade), paints.getValue(name))
        }
        assertEquals(RegionKind.FILLED, paints.getValue("Pectorales").kind)
        // 10 series de pecho en una semana son más intensas que 2 de bíceps.
        assertTrue(paints.getValue("Pectorales").level > paints.getValue("Bíceps").level)
    }

    @Test
    fun `la opacidad sube con cada escalon y todos comparten el mismo tono`() {
        val levels = listOf(VolumeShade.L1, VolumeShade.L2, VolumeShade.L3, VolumeShade.L4, VolumeShade.L5)
        val paints = levels.map { MuscleVolumeBodyModel.volumePaint(it) }
        assertTrue(paints.all { it.kind == RegionKind.FILLED })
        assertEquals(listOf(1, 2, 3, 4, 5), paints.map { it.level })
        paints.zipWithNext().forEach { (a, b) -> assertTrue(a.fill.alpha < b.fill.alpha) }
        val tones = paints.map { it.fill.copy(alpha = 1f) }.toSet()
        assertEquals(1, tones.size)
    }

    @Test
    fun `el resumen hablado del mapa dice lo que mas pesa y lo que no tiene datos`() {
        assertTrue(MuscleVolumeBodyModel.volumeSummary(panel(emptyList())).contains("sin datos"))
        val summary = MuscleVolumeBodyModel.volumeSummary(panel(listOf(log("a", 0, "press", 6))))
        assertTrue(summary, summary.contains("Pectorales 6"))
        assertTrue(summary, summary.contains("series"))

    }

    @Test
    fun `las fechas se escriben en espanol sin depender del dispositivo`() {
        assertEquals("21 oct", MuscleVolumeBodyModel.shortDate(today))
        assertEquals("1 ene", MuscleVolumeBodyModel.shortDate(LocalDate.of(2027, 1, 1)))
        assertEquals("15 oct – 21 oct", MuscleVolumeBodyModel.rangeText(panel(emptyList())))
        assertEquals("1 semana", MuscleVolumeBodyModel.windowText(1))
        assertEquals("12 semanas", MuscleVolumeBodyModel.windowText(12))
    }
}
