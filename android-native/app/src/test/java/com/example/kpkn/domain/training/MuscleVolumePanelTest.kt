package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Panel de series por músculo (T5a): misma contabilidad y ventana que T2, con el desglose que reconcilia con la cifra. */
class MuscleVolumePanelTest {
    private val zone: ZoneId = ZoneId.of("America/Santiago")
    private val today: LocalDate = LocalDate.of(2026, 10, 21)

    private val catalog: Map<String, ExerciseMuscleInfo> = mapOf(
        "press" to ExerciseMuscleInfo(
            "press", "Press de banca",
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, 0.5),
                InvolvedMuscle("Core", MuscleRole.STABILIZER, 0.4),
            ),
        ),
        "fly" to ExerciseMuscleInfo(
            "fly", "Aperturas",
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio")),
        ),
        "curl" to ExerciseMuscleInfo(
            "curl", "Curl de bíceps",
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, 1.0)),
        ),
    )

    private fun log(id: String, date: String, exercise: String = "press", sets: Int = 2, rpe: Double = 7.0): WorkoutLog = WorkoutLog(
        id = id,
        programId = "program",
        sessionId = "session",
        sessionName = "Sesión",
        date = date,
        durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "$exercise-occurrence",
                exerciseName = exercise,
                exerciseDbId = exercise,
                sets = List(sets) { CompletedSet("set-$it", weight = 60.0, reps = 8, rpe = rpe) },
            ),
        ),
    )

    private fun daysAgo(days: Long): String = today.minusDays(days).toString()

    private fun build(
        logs: List<WorkoutLog>,
        window: MuscleVolumeWindow = MuscleVolumeWindow.WEEKS_4,
        average: Boolean = true,
        mode: VolumeCountingMode = VolumeCountingMode.DIRECT,
        adjust: Boolean = false,
    ) = MuscleVolumePanelBuilder.build(
        logs, catalog, MuscleVolumePanelRequest(window, average, mode, adjust), today, zone,
    )

    /** Historial de 12 semanas con dos ejercicios, series y esfuerzos distintos. */
    private fun history(): List<WorkoutLog> = (0..40).map { index ->
        val exercise = if (index % 3 == 0) "fly" else if (index % 5 == 0) "curl" else "press"
        log("log-$index", daysAgo(index * 2L), exercise, sets = 2 + index % 4, rpe = 6.0 + index % 4)
    }

    @Test
    fun `la suma del desglose por ejercicio es la cifra de la fila en cada ventana y modo`() {
        val logs = history()
        for (window in MuscleVolumeWindow.values()) for (mode in VolumeCountingMode.values())
            for (adjust in listOf(false, true)) for (average in listOf(true, false)) {
                val panel = build(logs, window, average, mode, adjust)
                assertTrue(panel.hasData)
                panel.rows.forEach { row ->
                    assertEquals(
                        "${row.muscleName} $window $mode ajuste=$adjust promedio=$average",
                        row.contributors.sumOf { it.sets }, row.sets!!, 1e-9,
                    )
                }
            }
    }

    @Test
    fun `cada fila coincide con el calculo canonico de T2 sobre la misma ventana`() {
        val logs = history()
        for (window in MuscleVolumeWindow.values()) for (mode in VolumeCountingMode.values())
            for (adjust in listOf(false, true)) for (average in listOf(true, false)) {
                val request = MuscleVolumePanelRequest(window, average, mode, adjust)
                val panel = MuscleVolumePanelBuilder.build(logs, catalog, request, today, zone)
                val canonical = VolumeCalculator.calculateCompletedVolume(
                    logs, catalog, MuscleVolumePanelBuilder.windowFor(request, today, zone), mode, adjust,
                )
                VolumeCalculator.standardVolumeMuscles.forEach { muscle ->
                    assertEquals(
                        "$muscle $window $mode ajuste=$adjust promedio=$average",
                        canonical.muscles[muscle]?.sets ?: 0.0, panel.row(muscle)!!.sets!!, 1e-9,
                    )
                }
                assertEquals(canonical.unassignedCount, panel.unassignedCount)
            }
    }

    @Test
    fun `una ventana sin registros no tiene datos y ninguna fila trae cifra`() {
        val panel = build(emptyList())
        assertFalse(panel.hasData)
        assertEquals(0, panel.logCount)
        assertEquals(VolumeCalculator.standardVolumeMuscles.size, panel.rows.size)
        panel.rows.forEach { row ->
            assertNull(row.sets)
            assertFalse(row.hasData)
            assertEquals(VolumeShade.NO_DATA, row.shade)
            assertTrue(row.contributors.isEmpty())
        }
    }

    @Test
    fun `los registros fuera de la ventana no cuentan como datos`() {
        val panel = build(listOf(log("viejo", daysAgo(40))), MuscleVolumeWindow.WEEKS_4)
        assertFalse(panel.hasData)
        assertEquals(0, panel.unassignedCount)
        // La misma carga sí aparece en la ventana de 12 semanas.
        assertTrue(build(listOf(log("viejo", daysAgo(40))), MuscleVolumeWindow.WEEKS_12).hasData)
    }

    @Test
    fun `una ventana con registros muestra cero real en el musculo sin series`() {
        val panel = build(listOf(log("hoy", daysAgo(0), "press")))
        assertTrue(panel.hasData)
        val biceps = panel.row("Bíceps")!!
        assertEquals(0.0, biceps.sets!!, 0.0)
        assertTrue(biceps.hasData)
        assertEquals(VolumeShade.ZERO, biceps.shade)
        assertNotEquals(VolumeShade.NO_DATA, biceps.shade)
        // Directo: el tríceps (secundario del press) no suma; con +Secundario sí.
        assertEquals(0.0, panel.row("Tríceps")!!.sets!!, 0.0)
        assertTrue(build(listOf(log("hoy", daysAgo(0), "press")), mode = VolumeCountingMode.SECONDARY).row("Tríceps")!!.sets!! > 0.0)
    }

    @Test
    fun `los registros sin fecha se cuentan como sin asignar y no suman`() {
        val withBadDate = listOf(log("sin-fecha", "ilegible"), log("hoy", daysAgo(0), "press", sets = 4))
        val panel = build(withBadDate)
        assertEquals(1, panel.unassignedCount)
        assertEquals(1, panel.logCount)
        // 4 series de pecho en 4 semanas son 1 por semana: el registro ilegible no añade nada.
        assertEquals(1.0, panel.row("Pectorales")!!.sets!!, 1e-9)

        val onlyBad = build(listOf(log("sin-fecha", "ilegible")))
        assertFalse(onlyBad.hasData)
        assertEquals(1, onlyBad.unassignedCount)
        assertNull(onlyBad.row("Pectorales")!!.sets)
    }

    @Test
    fun `seis sesiones de dos series en una semana son doce series por semana`() {
        val logs = (0..5).map { log("s-$it", daysAgo(it.toLong()), sets = 2) }
        val panel = build(logs, MuscleVolumeWindow.WEEK_1)
        assertEquals(12.0, panel.row("Pectorales")!!.sets!!, 1e-9)
        assertEquals(1.0, panel.window.weekDivisor, 0.0)
    }

    @Test
    fun `la misma carga repartida en tres semanas se divide entre las semanas de la ventana`() {
        val logs = (0..5).map { log("s-$it", daysAgo((it / 2 * 7).toLong()), sets = 2) }
        val average = build(logs, MuscleVolumeWindow.WEEKS_4, average = true)
        // Doce series en una ventana de cuatro semanas: tres por semana (la cuarta semana vacía cuenta en el divisor).
        assertEquals(3.0, average.row("Pectorales")!!.sets!!, 1e-9)
        assertEquals("series/sem", average.unit)
        val total = build(logs, MuscleVolumeWindow.WEEKS_4, average = false)
        assertEquals(12.0, total.row("Pectorales")!!.sets!!, 1e-9)
        assertEquals("series", total.unit)
        // Con el divisor de T2 para tres semanas prescritas (tres semanas, no cuatro) saldrían 4 por semana.
        val tres = VolumeCalculator.calculateCompletedVolume(
            logs, catalog, TrainingWindow.prescribedAverage(today.minusWeeks(2), 3, zone),
        )
        assertEquals(4.0, tres.muscles.getValue("Pectorales").sets, 1e-9)
    }

    @Test
    fun `las veintitres treinta locales cuentan en su dia local aunque UTC ya sea manana`() {
        val local = today.atTime(23, 30).atZone(zone).toInstant().toString()
        val panel = build(listOf(log("tarde", local, sets = 4)), MuscleVolumeWindow.WEEK_1)
        assertTrue(panel.hasData)
        assertEquals(today, panel.row("Pectorales")!!.contributors.single().lastDate)
        // Último día de la ventana de siete días: el primer minuto local cuenta; el día anterior a la ventana, no.
        val firstDay = today.minusDays(6).atTime(0, 30).atZone(zone).toInstant().toString()
        val outside = today.minusDays(7).atTime(23, 30).atZone(zone).toInstant().toString()
        assertTrue(build(listOf(log("borde", firstDay)), MuscleVolumeWindow.WEEK_1).hasData)
        assertFalse(build(listOf(log("fuera", outside)), MuscleVolumeWindow.WEEK_1).hasData)
    }

    @Test
    fun `cada contribuyente trae sus sesiones y su ultima fecha`() {
        val logs = listOf(
            log("a", daysAgo(10), "press", sets = 3),
            log("b", daysAgo(3), "press", sets = 2),
            log("c", daysAgo(5), "fly", sets = 4),
        )
        val chest = build(logs, average = false).row("Pectorales")!!
        // Primero el que más aporta: el press (5 series) y después las aperturas (4).
        assertEquals(listOf("press", "fly"), chest.contributors.map { it.exerciseKey })
        val press = chest.contributors.first { it.exerciseKey == "press" }
        assertEquals(5.0, press.sets, 1e-9)
        assertEquals(2, press.sessions)
        assertEquals(today.minusDays(3), press.lastDate)
        val fly = chest.contributors.first { it.exerciseKey == "fly" }
        assertEquals(1, fly.sessions)
        assertEquals(today.minusDays(5), fly.lastDate)
        assertEquals(9.0, chest.sets!!, 1e-9)
    }

    @Test
    fun `las filas salen ordenadas por series y traen todos los grupos`() {
        val panel = build(history(), average = false)
        assertEquals(VolumeCalculator.standardVolumeMuscles, panel.rows.map { it.muscleName }.toSet())
        val values = panel.rows.map { it.sets!! }
        assertEquals(values.sortedDescending(), values)
        assertEquals(values.max(), panel.maxSets, 0.0)
    }

    @Test
    fun `las porciones y los roles acompanan a la cifra del musculo`() {
        val shoulderCatalog = catalog + ("lateral" to ExerciseMuscleInfo(
            "lateral", "Elevaciones laterales",
            involvedMuscles = listOf(InvolvedMuscle("Deltoides lateral", MuscleRole.PRIMARY, 1.0, "lateral")),
        ))
        val request = MuscleVolumePanelRequest(MuscleVolumeWindow.WEEK_1, false, VolumeCountingMode.STABILIZER)
        val panel = MuscleVolumePanelBuilder.build(
            listOf(log("hoy", daysAgo(0), "lateral", sets = 3)), shoulderCatalog, request, today, zone,
        )
        val deltoids = panel.row("Deltoides")!!
        assertEquals(3.0, deltoids.sets!!, 1e-9)
        assertEquals(3.0, deltoids.roles.directSets, 1e-9)
        assertNotNull(deltoids.portions.firstOrNull { it.name.contains("Lateral") })
        val press = MuscleVolumePanelBuilder.build(listOf(log("hoy", daysAgo(0), "press", sets = 2)), catalog, request, today, zone)
        val triceps = press.row("Tríceps")!!
        assertEquals(1.0, triceps.sets!!, 1e-9)
        assertEquals(1.0, triceps.roles.secondarySets, 1e-9)
        assertEquals(0.0, triceps.roles.directSets, 0.0)
    }

    @Test
    fun `el color distingue sin datos de cero y sube con las series`() {
        val w = MuscleVolumeWindow.WEEKS_4
        assertEquals(VolumeShade.NO_DATA, MuscleVolumeScale.shade(null, w, true))
        assertEquals(VolumeShade.ZERO, MuscleVolumeScale.shade(0.0, w, true))
        assertEquals(VolumeShade.L1, MuscleVolumeScale.shade(0.5, w, true))
        assertEquals(VolumeShade.L2, MuscleVolumeScale.shade(4.0, w, true))
        assertEquals(VolumeShade.L3, MuscleVolumeScale.shade(8.0, w, true))
        assertEquals(VolumeShade.L4, MuscleVolumeScale.shade(12.0, w, true))
        assertEquals(VolumeShade.L5, MuscleVolumeScale.shade(16.0, w, true))
        assertEquals(VolumeShade.L5, MuscleVolumeScale.shade(40.0, w, true))
    }

    @Test
    fun `con el total los umbrales se multiplican por las semanas y el color no cambia`() {
        val w = MuscleVolumeWindow.WEEKS_4
        // 10 series por semana son 40 series en cuatro semanas: el mismo escalón en las dos vistas.
        assertEquals(MuscleVolumeScale.shade(10.0, w, true), MuscleVolumeScale.shade(40.0, w, false))
        assertEquals(VolumeShade.L3, MuscleVolumeScale.shade(40.0, w, false))
        val legend = MuscleVolumeScale.legend(w, averagePerWeek = false).map { it.second }
        assertEquals(listOf("0", "menos de 16", "16–32", "32–48", "48–64", "64 o más"), legend)
        assertEquals(
            listOf("0", "menos de 4", "4–8", "8–12", "12–16", "16 o más"),
            MuscleVolumeScale.legend(w, averagePerWeek = true).map { it.second },
        )
    }

    @Test
    fun `el color de cada fila sale de su cifra y de la ventana`() {
        val logs = (0..5).map { log("s-$it", daysAgo(it.toLong()), sets = 2) }
        val panel = build(logs, MuscleVolumeWindow.WEEK_1)
        assertEquals(VolumeShade.L4, panel.row("Pectorales")!!.shade)
        assertEquals(VolumeShade.ZERO, panel.row("Dorsales")!!.shade)
    }

    @Test
    fun `el redondeo a decimas reparte el resto para que las lineas sumen el total visible`() {
        val values = listOf(1.04, 1.04, 1.04)
        val tenths = MuscleVolumePanelBuilder.reconcileTenths(values)
        assertEquals((values.sum() * 10.0).let { Math.round(it).toInt() }, tenths.sum())
        val many = listOf(2.33, 3.33, 3.33, 0.97, 0.01)
        assertEquals(Math.round(many.sum() * 10.0).toInt(), MuscleVolumePanelBuilder.reconcileTenths(many).sum())
        assertTrue(MuscleVolumePanelBuilder.reconcileTenths(emptyList()).isEmpty())
    }

    @Test
    fun `solo cuentan los registros del programa pedido`() {
        val other = log("otro", daysAgo(0), "press", sets = 4).copy(programId = "otro-programa")
        val mine = log("mio", daysAgo(0), "press", sets = 4)
        val request = MuscleVolumePanelRequest(MuscleVolumeWindow.WEEK_1, false, programId = "program")
        val panel = MuscleVolumePanelBuilder.build(listOf(other, mine), catalog, request, today, zone)
        assertEquals(1, panel.logCount)
        assertEquals(4.0, panel.row("Pectorales")!!.sets!!, 1e-9)
        val all = MuscleVolumePanelBuilder.build(listOf(other, mine), catalog, request.copy(programId = null), today, zone)
        assertEquals(8.0, all.row("Pectorales")!!.sets!!, 1e-9)
    }
}
