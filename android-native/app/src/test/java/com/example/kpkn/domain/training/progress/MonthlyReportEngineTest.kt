package com.example.kpkn.domain.training.progress

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyReportEngineTest {
    private val march = YearMonth.of(2026, 3)

    private fun iso(day: String) = "${day}T10:00:00.000Z"
    private fun set(weight: Double = 0.0, reps: Int = 1, rir: Int? = null, skipped: Boolean = false, isWarmup: Boolean = false) =
        CompletedSet("s", weight = weight, reps = reps, rir = rir, skipped = skipped, isWarmup = isWarmup)
    private fun exercise(name: String, vararg sets: CompletedSet, muscles: List<InvolvedMuscle>? = null) =
        CompletedExercise(exerciseId = name, exerciseName = name, effectiveMuscles = muscles, sets = sets.toList())
    private fun log(id: String, day: String, vararg exercises: CompletedExercise, programId: String = "p1") = WorkoutLog(
        id = id,
        programId = programId,
        sessionId = "s$id",
        sessionName = "Sesión $id",
        date = iso(day),
        durationMinutes = 60,
        completedExercises = exercises.toList(),
    )
    private fun primary(muscle: String) = InvolvedMuscle(muscle, role = MuscleRole.PRIMARY)
    private fun secondary(muscle: String) = InvolvedMuscle(muscle, role = MuscleRole.SECONDARY)
    private fun reportOf(logs: List<WorkoutLog>, programId: String? = null, tracked: List<String> = emptyList()) =
        MonthlyReportEngine.build(logs, march, programId, tracked)

    @Test
    fun filtraPorMesYPrograma() {
        val logs = listOf(
            log("a", "2026-03-02", exercise("Press banca", set(100.0))),
            log("b", "2026-03-10", exercise("Press banca", set(100.0))),
            log("c", "2026-03-15", exercise("Press banca", set(100.0)), programId = "p2"),
            log("d", "2026-02-20", exercise("Press banca", set(100.0))),
            log("e", "2026-04-01", exercise("Press banca", set(100.0))),
        )
        val p1 = reportOf(logs, programId = "p1")
        assertEquals(2, p1.sessions)
        assertEquals(listOf(2, 10), p1.trainedDays)
        assertEquals(120, p1.minutes)
        assertEquals(3, reportOf(logs).sessions)
        assertEquals(listOf(2, 10, 15), reportOf(logs).trainedDays)
    }

    @Test
    fun diasEntrenadosSinRepetirNiDesordenar() {
        val logs = listOf(
            log("a", "2026-03-20", exercise("Press banca", set(100.0))),
            log("b", "2026-03-05", exercise("Press banca", set(100.0))),
            log("c", "2026-03-05", exercise("Sentadilla", set(100.0))),
        )
        val report = reportOf(logs)
        assertEquals(3, report.sessions)
        assertEquals(listOf(5, 20), report.trainedDays)
    }

    @Test
    fun logConFechaIlegibleSeIgnora() {
        val logs = listOf(
            log("a", "2026-03-45", exercise("Press banca", set(100.0, reps = 5))),
            log("b", "2026-03-05", exercise("Press banca", set(100.0))),
        )
        val report = reportOf(logs)
        assertEquals(1, report.sessions)
        assertEquals(100.0, report.tonnageKg, 1e-9)
        assertEquals(listOf(5), report.trainedDays)
    }

    @Test
    fun tonelajeYComparacionConElMesAnteriorDelMismoPrograma() {
        val logs = listOf(
            log(
                "a", "2026-03-03",
                exercise(
                    "Press banca",
                    set(100.0, reps = 5),
                    set(80.0, reps = 8),
                    set(0.0, reps = 10), // peso 0: cuenta como serie y repeticiones, no como tonelaje
                    set(50.0, reps = 10, isWarmup = true), // calentamiento: fuera
                    set(100.0, reps = 5, skipped = true), // omitida: fuera
                ),
            ),
            log("b", "2026-02-10", exercise("Press banca", set(100.0, reps = 10))), // mes anterior, mismo programa: 1000 kg
            log("c", "2026-02-11", exercise("Press banca", set(900.0, reps = 10)), programId = "p2"), // otro programa: no cuenta
        )
        val report = reportOf(logs, programId = "p1")
        assertEquals(1140.0, report.tonnageKg, 1e-9) // 500 + 640
        assertEquals(3, report.hardSets)
        assertEquals(23, report.reps) // 5 + 8 + 10
        assertEquals(14, report.tonnageVsPreviousPct) // (1140 - 1000) / 1000
    }

    @Test
    fun sinCargaEnElMesAnteriorLaComparacionEsNull() {
        val logs = listOf(
            log("a", "2026-03-03", exercise("Flexiones", set(0.0, reps = 20))),
            log("b", "2026-02-03", exercise("Flexiones", set(0.0, reps = 20))),
        )
        val report = reportOf(logs)
        assertEquals(0.0, report.tonnageKg, 1e-9)
        assertNull(report.tonnageVsPreviousPct)
    }

    @Test
    fun gananciasSoloConTresSesionesDeltaPositivoOrdenadasYMaximoTres() {
        // Con una sola repetición, el e1RM de cada sesión es la carga registrada.
        val logs = listOf(
            log(
                "2", "2026-03-02",
                exercise("Press banca", set(100.0)), exercise("Sentadilla", set(150.0)),
                exercise("Peso muerto", set(200.0)), exercise("Dominadas", set(90.0)), exercise("Remo", set(60.0)),
            ),
            log(
                "5", "2026-03-05",
                exercise("Press banca", set(102.0)), exercise("Sentadilla", set(160.0)),
                exercise("Peso muerto", set(250.0)), exercise("Dominadas", set(90.0)), exercise("Remo", set(58.0)),
                exercise("Curl", set(20.0)), exercise("Fondos", set(40.0)),
            ),
            log(
                "9", "2026-03-09",
                exercise("Press banca", set(104.0)), exercise("Sentadilla", set(170.0)),
                exercise("Dominadas", set(90.0)), exercise("Remo", set(57.0)),
                exercise("Curl", set(22.0)), exercise("Fondos", set(41.0)),
            ),
            log(
                "16", "2026-03-16",
                exercise("Press banca", set(110.0)), exercise("Dominadas", set(90.0)), exercise("Remo", set(56.0)),
                exercise("Curl", set(25.0)), exercise("Fondos", set(42.0)),
            ),
            log("23", "2026-03-23", exercise("Remo", set(55.0)), exercise("Fondos", set(43.0))),
        )
        val gains = reportOf(logs).gains
        // Peso muerto: solo 2 sesiones. Dominadas: delta 0. Remo: delta negativo.
        // Fondos (+3, 4 sesiones) queda fuera por el máximo de tres.
        assertEquals(listOf("Sentadilla", "Press banca", "Curl"), gains.map { it.exerciseName })
        assertEquals(listOf(20.0, 10.0, 5.0), gains.map { it.deltaKg })
        assertEquals(listOf(150.0, 160.0, 170.0), gains.first().points)
    }

    @Test
    fun mejorMarcaEsElMayorE1rmYEnEmpateGanaLaMasReciente() {
        val logs = listOf(
            log("a", "2026-03-03", exercise("Press banca", set(100.0, reps = 5, rir = 1))), // e1RM 116,1
            log("b", "2026-03-10", exercise("Sentadilla", set(110.0), set(300.0, isWarmup = true))), // más carga, menos e1RM
            // Empate con el anterior: gana el más reciente. La serie saltada (120 kg) no cuenta.
            log("c", "2026-03-20", exercise("Remo", set(100.0, reps = 5, rir = 1), set(120.0, skipped = true))),
        )
        val best = reportOf(logs).bestMark!!
        assertEquals("Remo", best.exerciseName)
        assertEquals(116.1, best.e1rm, 1e-9)
        assertEquals(100.0, best.weight, 1e-9)
        assertEquals(5, best.reps)
        assertEquals(1, best.rir)
        assertEquals(LocalDate.of(2026, 3, 20), best.date)
    }

    @Test
    fun sinSeriesConCargaNoHayMejorMarca() {
        val logs = listOf(log("a", "2026-03-03", exercise("Flexiones", set(0.0, reps = 20))))
        assertNull(reportOf(logs).bestMark)
    }

    @Test
    fun musculosCuentanSoloElPrincipalPorSerieDeTrabajo() {
        val logs = listOf(
            log(
                "a", "2026-03-03",
                exercise(
                    "Press banca",
                    set(100.0, reps = 5), set(100.0, reps = 5), set(100.0, reps = 5),
                    set(40.0, reps = 10, isWarmup = true), set(100.0, reps = 5, skipped = true),
                    muscles = listOf(primary("Pecho"), secondary("Tríceps"), InvolvedMuscle("Deltoides", role = MuscleRole.STABILIZER)),
                ),
                exercise(
                    "Sentadilla",
                    set(120.0, reps = 5), set(120.0, reps = 5),
                    muscles = listOf(primary("Cuádriceps"), primary("Glúteo"), secondary("Lumbar")),
                ),
                exercise(
                    "Dominadas",
                    set(0.0, reps = 8),
                    muscles = listOf(primary("Dorsal"), primary("Dorsal"), secondary("Bíceps")), // principal repetido: cuenta una vez
                ),
                exercise("Remo", set(60.0, reps = 10), muscles = emptyList()),
                exercise("Curl", set(20.0, reps = 10), set(20.0, reps = 10)), // sin effectiveMuscles
                exercise("Plancha", set(0.0, reps = 30, isWarmup = true), muscles = listOf(primary("Abdominales"))), // sin serie de trabajo
            ),
        )
        assertEquals(
            listOf(
                MonthlyMuscleSets("Pecho", 3),
                MonthlyMuscleSets("Cuádriceps", 2),
                MonthlyMuscleSets("Glúteo", 2),
                MonthlyMuscleSets("Dorsal", 1),
            ),
            reportOf(logs).muscles,
        )
    }

    @Test
    fun musculosSinSeriesSeListanEnElOrdenDeSeguimiento() {
        val logs = listOf(log("a", "2026-03-03", exercise("Press banca", set(100.0), muscles = listOf(primary("Pecho")))))
        val report = reportOf(logs, tracked = listOf("Isquios", "Pecho", "Espalda", "Cuádriceps", "Espalda"))
        assertEquals(listOf("Isquios", "Espalda", "Cuádriceps"), report.untrainedMuscles)
    }

    @Test
    fun rachaToleraHuecosDeHastaTresDiasYSeCortaConCuatro() {
        val days = listOf("2026-03-02", "2026-03-04", "2026-03-06", "2026-03-11", "2026-03-13", "2026-03-16", "2026-03-17", "2026-03-21")
        val logs = days.mapIndexed { i, day -> log("l$i", day, exercise("Press banca", set(100.0))) }
        // Tiradas: 02-04-06 (huecos de 2 y luego 5 días: corta), 11-13-16-17 (hueco de 3 incluido; el de 4 corta), 21.
        assertEquals(4, reportOf(logs).longestStreak)
        assertEquals(4, reportOf(logs.reversed()).longestStreak)
    }

    @Test
    fun elegibleDesdeCuatroSesionesYUnaSolaSesionDaRachaDeUno() {
        val three = (3..5).map { log("l$it", "2026-03-0$it", exercise("Press banca", set(100.0))) }
        val four = three + log("l6", "2026-03-06", exercise("Press banca", set(100.0)))
        val threeReport = reportOf(three)
        val fourReport = reportOf(four)
        assertEquals(3, threeReport.sessions)
        assertFalse(threeReport.eligible)
        assertEquals(4, fourReport.sessions)
        assertTrue(fourReport.eligible)
        assertEquals(4, fourReport.longestStreak)
        assertEquals(1, reportOf(three.take(1)).longestStreak)
    }

    @Test
    fun rirMedioSoloDeSeriesDeTrabajoConUnDecimal() {
        val logs = listOf(
            log(
                "a", "2026-03-03",
                exercise(
                    "Press banca",
                    set(100.0, rir = 2), set(100.0, rir = 1), set(100.0), // la serie sin RIR no cuenta
                    set(60.0, rir = 4, isWarmup = true), set(100.0, rir = 0, skipped = true),
                ),
            ),
            log("b", "2026-03-05", exercise("Press banca", set(100.0, rir = 1))),
        )
        assertEquals(1.3, reportOf(logs).rirMean!!, 1e-9) // (2 + 1 + 1) / 3 = 1,33
    }

    @Test
    fun mesVacioDaTodoACeroYListasVacias() {
        val report = reportOf(emptyList(), tracked = listOf("Pecho", "Espalda"))
        assertEquals(march, report.month)
        assertEquals(0, report.sessions)
        assertTrue(report.trainedDays.isEmpty())
        assertEquals(0, report.minutes)
        assertEquals(0.0, report.tonnageKg, 1e-9)
        assertEquals(0, report.hardSets)
        assertEquals(0, report.reps)
        assertNull(report.tonnageVsPreviousPct)
        assertTrue(report.gains.isEmpty())
        assertNull(report.bestMark)
        assertTrue(report.muscles.isEmpty())
        assertEquals(listOf("Pecho", "Espalda"), report.untrainedMuscles)
        assertNull(report.rirMean)
        assertEquals(0, report.longestStreak)
        assertFalse(report.eligible)
    }
}
