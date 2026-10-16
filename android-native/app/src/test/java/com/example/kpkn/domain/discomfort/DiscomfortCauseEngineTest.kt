package com.example.kpkn.domain.discomfort

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscomfortCauseEngineTest {

    private val now: Instant = Instant.parse("2026-10-16T12:00:00Z")
    private val nbsp = ' '

    // ── Datos de prueba ──

    private val squatMuscles = listOf(
        InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("Glúteos", MuscleRole.SECONDARY),
        InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER),
    )
    private val curlMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY))
    private val lungeMuscles = listOf(
        InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("Glúteos", MuscleRole.PRIMARY),
    )

    private fun set(weight: Double = 100.0, reps: Int = 8, rir: Int? = 3, isFailure: Boolean = false, warmup: Boolean = false) =
        CompletedSet(
            id = "s-$weight-$reps-$rir-$isFailure",
            weight = weight,
            reps = reps,
            rir = rir,
            isFailure = isFailure,
            isWarmup = warmup,
        )

    private fun sets(count: Int, weight: Double = 100.0, reps: Int = 8, rir: Int? = 3) =
        List(count) { set(weight, reps, rir) }

    private fun todayExercise(
        id: String = "squat",
        name: String = "Sentadilla",
        muscles: List<InvolvedMuscle> = squatMuscles,
        sets: List<CompletedSet> = sets(3),
        technicalQuality: Int? = null,
        axial: Double? = 0.7,
        impact: Boolean = false,
        unilateral: Boolean = false,
    ) = DiscomfortSessionExercise(
        exerciseId = id,
        name = name,
        muscles = muscles,
        sets = sets,
        technicalQuality = technicalQuality,
        axialLoadFactor = axial,
        isImpact = impact,
        isUnilateral = unilateral,
    )

    private fun session(vararg exercises: DiscomfortSessionExercise, focus: String? = null) =
        DiscomfortSession(exercises.toList(), focus)

    private fun history(vararg logs: WorkoutLog) = DiscomfortHistory(logs.toList(), now)

    private fun daysAgo(days: Long): String = now.minus(days, ChronoUnit.DAYS).toString()

    private fun completed(
        id: String = "squat",
        name: String = "Sentadilla",
        muscles: List<InvolvedMuscle> = squatMuscles,
        sets: List<CompletedSet> = sets(4),
    ) = CompletedExercise(exerciseId = id, exerciseName = name, effectiveMuscles = muscles, sets = sets)

    private var logCounter = 0
    private fun log(
        days: Long,
        vararg exercises: CompletedExercise,
        reports: List<ExerciseDiscomfortReport> = emptyList(),
        stillPresent: List<String> = emptyList(),
    ) = WorkoutLog(
        id = "log-${logCounter++}",
        programId = "p",
        sessionId = "s",
        sessionName = "Pierna",
        date = daysAgo(days),
        durationMinutes = 60,
        completedExercises = exercises.toList(),
        postExerciseReports = reports,
        stillPresentDiscomfortIds = stillPresent,
    )

    private fun report(vararg ids: String, exerciseId: String = "squat") = ExerciseDiscomfortReport(
        exerciseId = exerciseId,
        exerciseName = "Sentadilla",
        technicalQuality = 8,
        discomfortIds = ids.toList(),
    )

    /** Cuatro semanas anteriores con [baseline] series de sentadilla cada una (a 10, 17, 24 y 31 días). */
    private fun baselineLogs(baseline: Int): List<WorkoutLog> =
        listOf(10L, 17L, 24L, 31L).map { log(it, completed(sets = sets(baseline))) }

    private fun historyOf(logs: List<WorkoutLog>) = DiscomfortHistory(logs, now)

    private fun explain(id: String, s: DiscomfortSession, h: DiscomfortHistory) =
        DiscomfortCauseEngine.explain(id, s, h)

    private fun DiscomfortInsight.todayOf(kind: DiscomfortEvidenceKind) = today.firstOrNull { it.kind == kind }
    private fun DiscomfortInsight.weekOf(kind: DiscomfortEvidenceKind) = week.firstOrNull { it.kind == kind }

    // ── Sin evidencia ──

    @Test
    fun `sin datos no hay aviso ni listas ni consejo`() {
        val insight = explain("knee_patellar", session(todayExercise(sets = emptyList())), history())
        assertNull(insight.headline)
        assertTrue(insight.today.isEmpty())
        assertTrue(insight.week.isEmpty())
        assertTrue(insight.advice.isEmpty())
        assertFalse(insight.persistent)
        assertFalse(insight.hasEvidence)
        assertNull(DiscomfortCauseEngine.sessionNotice(session(todayExercise()), history()))
    }

    @Test
    fun `una molestia desconocida o none no explican nada`() {
        val s = session(todayExercise(sets = sets(4, rir = 0)))
        assertFalse(explain("none", s, history()).hasEvidence)
        assertFalse(explain("no_existe", s, history()).hasEvidence)
    }

    @Test
    fun `un ejercicio que no carga la zona no genera evidencia de hoy`() {
        val s = session(
            todayExercise(id = "curl", name = "Curl", muscles = curlMuscles, sets = sets(4, rir = 0), axial = 0.0),
        )
        assertTrue(explain("knee_patellar", s, history()).today.isEmpty())
    }

    // ── Hoy ──

    @Test
    fun `series pesadas por RIR 1 o menos con el umbral en 2 series`() {
        fun heavy(count: Int) = explain(
            "knee_patellar",
            session(todayExercise(sets = sets(count, rir = 1) + sets(2, rir = 3))),
            history(),
        ).todayOf(DiscomfortEvidenceKind.HEAVY_SETS)

        assertNull("1 serie pesada no basta", heavy(1))
        val two = heavy(2)
        assertNotNull(two)
        assertEquals(2.0, two!!.number, 0.0)
        assertEquals("2 series pesadas de sentadilla", two.text)
        assertEquals(4.0, heavy(4)!!.number, 0.0)
    }

    @Test
    fun `series pesadas por 80 por ciento del e1RM del historial justo en el limite`() {
        // Historial: 100 kg x 1 da e1RM 100.
        val hist = history(log(7, completed(sets = listOf(set(100.0, 1, rir = 0)))))
        fun heavy(weight: Double) = explain(
            "knee_patellar",
            session(todayExercise(sets = sets(2, weight = weight, reps = 5, rir = 3))),
            hist,
        ).todayOf(DiscomfortEvidenceKind.HEAVY_SETS)

        assertNotNull("80 kg es el 80 %", heavy(80.0))
        assertNull("79,9 kg no llega", heavy(79.9))
    }

    @Test
    fun `el calentamiento no cuenta`() {
        val s = session(
            todayExercise(sets = listOf(set(rir = 0, warmup = true), set(rir = 0, warmup = true), set(rir = 3))),
        )
        assertTrue(explain("knee_patellar", s, history()).today.isEmpty())
    }

    @Test
    fun `subida de carga desde 7,5 por ciento frente a la ultima sesion`() {
        val hist = history(log(5, completed(sets = sets(3, weight = 100.0))))
        fun jump(weight: Double) = explain(
            "knee_patellar",
            session(todayExercise(sets = sets(3, weight = weight))),
            hist,
        ).todayOf(DiscomfortEvidenceKind.LOAD_JUMP)

        val atLimit = jump(107.5)
        assertNotNull("+7,5 % exacto cuenta", atLimit)
        assertEquals(7.5, atLimit!!.number, 1e-6)
        assertEquals("+7,5${nbsp}% de carga en sentadilla frente a la última sesión", atLimit.text)
        assertNull("+7 % no", jump(107.0))
        assertEquals("+20${nbsp}% de carga en sentadilla frente a la última sesión", jump(120.0)!!.text)
    }

    @Test
    fun `sin sesion anterior no hay subida de carga`() {
        val s = session(todayExercise(sets = sets(3, weight = 300.0)))
        assertNull(explain("knee_patellar", s, history()).todayOf(DiscomfortEvidenceKind.LOAD_JUMP))
    }

    @Test
    fun `tecnica de 6 sobre 10 avisa y de 7 no`() {
        fun low(q: Int) = explain(
            "knee_patellar",
            session(todayExercise(technicalQuality = q)),
            history(),
        ).todayOf(DiscomfortEvidenceKind.LOW_TECHNIQUE)

        assertEquals("Técnica de 6/10 en sentadilla", low(6)!!.text)
        assertNull(low(7))
        assertEquals(4.0, low(4)!!.number, 0.0)
        assertTrue("más baja, más fuerte", low(4)!!.strength > low(6)!!.strength)
    }

    @Test
    fun `series al fallo`() {
        val s = session(
            todayExercise(sets = listOf(set(rir = 3), set(rir = null, isFailure = true), set(rir = 0))),
        )
        val e = explain("knee_patellar", s, history()).todayOf(DiscomfortEvidenceKind.FAILURE_SETS)!!
        assertEquals(2.0, e.number, 0.0)
        assertEquals("2 series al fallo en sentadilla", e.text)
    }

    @Test
    fun `ejercicios de impacto o a una pierna seguidos`() {
        val lunge = todayExercise(id = "lunge", name = "Zancada", muscles = lungeMuscles, unilateral = true, sets = sets(3))
        val jump = todayExercise(id = "jump", name = "Salto al cajón", muscles = lungeMuscles, impact = true, sets = sets(3))
        val press = todayExercise(id = "press", name = "Prensa", muscles = lungeMuscles, sets = sets(3))

        val chain = explain("knee_patellar", session(lunge, jump), history())
            .todayOf(DiscomfortEvidenceKind.IMPACT_OR_UNILATERAL_CHAIN)
        assertNotNull(chain)
        assertEquals(2.0, chain!!.number, 0.0)
        assertTrue(chain.text, chain.text.contains("zancada, salto al cajón"))

        assertNull(
            explain("knee_patellar", session(lunge, press, jump), history())
                .todayOf(DiscomfortEvidenceKind.IMPACT_OR_UNILATERAL_CHAIN),
        )
        assertNull(
            explain("knee_patellar", session(lunge), history())
                .todayOf(DiscomfortEvidenceKind.IMPACT_OR_UNILATERAL_CHAIN),
        )
    }

    @Test
    fun `isImpactExercise reconoce saltos y carrera`() {
        assertTrue(DiscomfortCauseEngine.isImpactExercise("Salto al cajón"))
        assertTrue(DiscomfortCauseEngine.isImpactExercise("Jump squat"))
        assertTrue(DiscomfortCauseEngine.isImpactExercise("Carrera continua"))
        assertFalse(DiscomfortCauseEngine.isImpactExercise("Sentadilla trasera"))
    }

    // ── Esta semana ──

    @Test
    fun `semana con 35 por ciento mas de cuadriceps da la linea de F2`() {
        // Media de 20 series; esta semana 20 hace tres días y 7 hoy suman 27 (+35 %).
        val hist = historyOf(baselineLogs(20) + log(3, completed(sets = sets(20))))
        val s = session(todayExercise(sets = sets(7, rir = 3)))
        val insight = explain("knee_patellar", s, hist)
        val rise = insight.weekOf(DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE)!!
        assertEquals(35.0, rise.number, 1e-6)
        assertEquals("+35${nbsp}% de series de cuádriceps sobre tu media", rise.text)
        assertEquals(
            "Esta semana llevas un 35${nbsp}% más de series de cuádriceps que tu media. " +
                "Si la rodilla avisa, empieza por ahí.",
            insight.headline,
        )
        assertEquals(insight.headline, rise.headline)
    }

    @Test
    fun `el umbral semanal esta en mas 25 por ciento`() {
        fun rise(thisWeek: Int): DiscomfortEvidence? =
            explain(
                "knee_patellar",
                session(todayExercise(sets = sets(thisWeek, rir = 3))),
                historyOf(baselineLogs(20)),
            ).weekOf(DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE)

        assertNull("+20 %", rise(24))
        assertNotNull("+25 % exacto", rise(25))
        assertEquals(25.0, rise(25)!!.number, 1e-6)
    }

    @Test
    fun `la media necesita al menos dos semanas con datos y volumen suficiente`() {
        val s = session(todayExercise(sets = sets(30, rir = 3)))
        val oneWeek = historyOf(listOf(log(10, completed(sets = sets(20)))))
        assertNull(explain("knee_patellar", s, oneWeek).weekOf(DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE))
        // Dos semanas pero media de 2 series (menos de 3): ruido, no evidencia.
        val tiny = historyOf(listOf(10L, 17L).map { log(it, completed(sets = sets(4))) })
        assertNull(explain("knee_patellar", s, tiny).weekOf(DiscomfortEvidenceKind.WEEKLY_VOLUME_RISE))
    }

    @Test
    fun `frecuencia desde 3 sesiones que cargan la zona en 7 dias`() {
        val today = session(todayExercise(sets = sets(4)))
        val twoBefore = historyOf(listOf(log(2, completed(sets = sets(4))), log(5, completed(sets = sets(4)))))
        val freq = explain("knee_patellar", today, twoBefore).weekOf(DiscomfortEvidenceKind.FREQUENCY)!!
        assertEquals(3.0, freq.number, 0.0)
        assertEquals("3 sesiones que cargan la rodilla en 7 días", freq.text)

        val oneBefore = historyOf(listOf(log(2, completed(sets = sets(4)))))
        assertNull(explain("knee_patellar", today, oneBefore).weekOf(DiscomfortEvidenceKind.FREQUENCY))
        // Una sesión de hace 9 días queda fuera de los 7.
        val old = historyOf(listOf(log(2, completed(sets = sets(4))), log(9, completed(sets = sets(4)))))
        assertNull(explain("knee_patellar", today, old).weekOf(DiscomfortEvidenceKind.FREQUENCY))
    }

    @Test
    fun `sesiones de brazo no cuentan como sesiones que cargan la rodilla`() {
        val arms = historyOf(
            listOf(
                log(1, completed("curl", "Curl", curlMuscles, sets(6))),
                log(3, completed("curl", "Curl", curlMuscles, sets(6))),
            ),
        )
        val today = session(todayExercise(sets = sets(4)))
        assertNull(explain("knee_patellar", today, arms).weekOf(DiscomfortEvidenceKind.FREQUENCY))
    }

    @Test
    fun `la misma molestia en tres sesiones seguidas es persistente`() {
        val s = session(todayExercise(), focus = "squat")
        val logs = listOf(
            log(2, completed(), reports = listOf(report("knee_patellar"))),
            log(9, completed(), reports = listOf(report("knee_patellar"))),
            log(16, completed(), reports = listOf(report("knee_patellar"))),
        )
        val insight = explain("knee_patellar", s, historyOf(logs))
        assertTrue(insight.persistent)
        val e = insight.weekOf(DiscomfortEvidenceKind.REPEATED_REPORT)!!
        assertEquals(3.0, e.number, 0.0)
        assertEquals("Has reportado esta molestia en 3 sesiones seguidas con este ejercicio.", insight.headline)
        assertEquals(3, DiscomfortCauseEngine.consecutiveReportCount("knee_patellar", s, historyOf(logs)))

        val two = explain("knee_patellar", s, historyOf(logs.take(2)))
        assertFalse("2 sesiones aún no es persistente", two.persistent)
        assertEquals(2.0, two.weekOf(DiscomfortEvidenceKind.REPEATED_REPORT)!!.number, 0.0)
        val one = explain("knee_patellar", s, historyOf(logs.take(1)))
        assertNull(one.weekOf(DiscomfortEvidenceKind.REPEATED_REPORT))
    }

    @Test
    fun `la regla persistente es la del ViewModel`() {
        val s = session(todayExercise(), focus = "squat")
        // De la más reciente a la más antigua: A, A, B, A. Para A cuenta 2 y se rompe en la sesión con solo B.
        val logs = historyOf(
            listOf(
                log(2, completed(), reports = listOf(report("knee_patellar"))),
                log(9, completed(), reports = listOf(report("knee_patellar"))),
                log(16, completed(), reports = listOf(report("lumbar"))),
                log(23, completed(), reports = listOf(report("knee_patellar"))),
            ),
        )
        assertEquals(2, DiscomfortCauseEngine.consecutiveReportCount("knee_patellar", s, logs))
        // La molestia no está en la sesión más reciente: 0 (el ViewModel solo arranca desde la más reciente).
        assertEquals(0, DiscomfortCauseEngine.consecutiveReportCount("lumbar", s, logs))
        // Solo cuentan los reportes de ESE ejercicio y «none» no es una molestia.
        val other = historyOf(
            listOf(
                log(
                    2,
                    completed("curl", "Curl", curlMuscles),
                    reports = listOf(report("knee_patellar", exerciseId = "curl")),
                ),
            ),
        )
        assertEquals(0, DiscomfortCauseEngine.consecutiveReportCount("knee_patellar", s, other))
        val none = historyOf(listOf(log(2, completed(), reports = listOf(report("none")))))
        assertEquals(0, DiscomfortCauseEngine.consecutiveReportCount("knee_patellar", s, none))
    }

    @Test
    fun `aun presente tiene prioridad como en el ViewModel`() {
        val s = session(todayExercise(), focus = "squat")
        // Cada sesión reportó dos molestias, pero solo «lumbar» quedó aún presente al cerrar: cuenta esa.
        val logs = historyOf(
            listOf(
                log(2, completed(), reports = listOf(report("knee_patellar", "lumbar")), stillPresent = listOf("lumbar")),
                log(9, completed(), reports = listOf(report("knee_patellar", "lumbar")), stillPresent = listOf("lumbar")),
            ),
        )
        assertEquals(2, DiscomfortCauseEngine.consecutiveReportCount("lumbar", s, logs))
        assertEquals(0, DiscomfortCauseEngine.consecutiveReportCount("knee_patellar", s, logs))
    }

    @Test
    fun `cinco semanas subiendo sin descarga`() {
        // Series de sentadilla por semana, de la más antigua a la actual: 8, 10, 12, 14, 16 y hoy 20.
        fun weeklyLogs(perWeek: List<Int>) =
            perWeek.mapIndexed { i, n -> log((perWeek.size - i) * 7L + 2, completed(sets = sets(n))) }
        val s = session(todayExercise(sets = sets(20, rir = 3)))

        val e = explain("knee_patellar", s, historyOf(weeklyLogs(listOf(8, 10, 12, 14, 16))))
            .weekOf(DiscomfortEvidenceKind.NO_DELOAD)!!
        assertEquals(5.0, e.number, 0.0)
        assertEquals("5 semanas seguidas subiendo el volumen sin descarga", e.text)

        // Con una semana de descarga a mitad ya no hay aviso.
        assertNull(
            explain("knee_patellar", s, historyOf(weeklyLogs(listOf(8, 10, 5, 14, 16))))
                .weekOf(DiscomfortEvidenceKind.NO_DELOAD),
        )
        // Con solo cuatro semanas de datos tampoco.
        assertNull(
            explain("knee_patellar", s, historyOf(weeklyLogs(listOf(10, 12, 14, 16))))
                .weekOf(DiscomfortEvidenceKind.NO_DELOAD),
        )
    }

    // ── Consejo ──

    @Test
    fun `el consejo sale de la evidencia mas fuerte y termina con el cierre profesional`() {
        val hist = historyOf(baselineLogs(20) + log(3, completed(sets = sets(20))))
        val s = session(todayExercise(sets = sets(7, rir = 0)))
        val insight = explain("knee_patellar", s, hist)
        assertTrue(insight.advice.size in 2..3)
        assertEquals(PROFESSIONAL_ADVICE, insight.advice.last())
        assertEquals(insight.advice.distinct(), insight.advice)
        insight.advice.forEach {
            assertFalse(it.contains('!'))
            assertFalse(it.lowercase().contains("debes"))
        }
        // Misma entrada, misma salida.
        assertEquals(insight, explain("knee_patellar", s, hist))
    }

    @Test
    fun `los textos de evidencia no llevan exclamaciones ni la palabra debes`() {
        val hist = historyOf(baselineLogs(20) + log(3, completed(sets = sets(20))))
        val s = session(todayExercise(sets = sets(7, rir = 0), technicalQuality = 4))
        val insight = explain("knee_patellar", s, hist)
        assertTrue(insight.today.isNotEmpty() && insight.week.isNotEmpty())
        (insight.today + insight.week).forEach { e ->
            listOf(e.text, e.headline, e.advice).forEach {
                assertFalse(it, it.contains('!'))
                assertFalse(it, it.lowercase().contains("debes"))
            }
        }
    }

    // ── Ranking ──

    @Test
    fun `rankLikely pone primero la rodilla para la sentadilla y respeta el limite`() {
        val s = session(todayExercise(), focus = "squat")
        val top = DiscomfortCauseEngine.rankLikely(s, history())
        assertEquals("knee_patellar", top.first())
        assertEquals(3, top.size)
        assertFalse(top.contains("none"))
        assertEquals(top, DiscomfortCauseEngine.rankLikely(s, history())) // determinista
        assertEquals(1, DiscomfortCauseEngine.rankLikely(s, history(), limit = 1).size)
        assertTrue(DiscomfortCauseEngine.rankLikely(s, history(), limit = 0).isEmpty())
    }

    @Test
    fun `rankLikely para un curl habla de codo y no de rodilla`() {
        val s = session(todayExercise(id = "curl", name = "Curl", muscles = curlMuscles, axial = 0.0), focus = "curl")
        val top = DiscomfortCauseEngine.rankLikely(s, history(), limit = 5)
        assertTrue(top.any { it.startsWith("elbow") })
        assertFalse(top.contains("knee_patellar"))
    }

    @Test
    fun `rankLikely sube una molestia ya reportada con el ejercicio`() {
        val s = session(todayExercise(), focus = "squat")
        val hist = historyOf(listOf(log(3, completed(), reports = listOf(report("ankle")))))
        assertTrue("el tobillo reportado entra", DiscomfortCauseEngine.rankLikely(s, hist, limit = 3).contains("ankle"))
        assertFalse(DiscomfortCauseEngine.rankLikely(s, history(), limit = 3).contains("ankle"))
    }

    @Test
    fun `rankLikely sin ejercicios no inventa nada`() {
        assertTrue(DiscomfortCauseEngine.rankLikely(session(), history()).isEmpty())
    }

    @Test
    fun `rankLikely sin foco mira toda la sesion`() {
        val s = session(todayExercise(), todayExercise(id = "curl", name = "Curl", muscles = curlMuscles, axial = 0.0))
        val top = DiscomfortCauseEngine.rankLikely(s, history(), limit = 17)
        assertTrue(top.contains("knee_patellar"))
        assertTrue(top.contains("elbow_medial"))
        assertEquals(top.size, top.toSet().size)
        assertTrue(top.all { id -> DISCOMFORT_CATALOG.any { it.id == id } })
    }

    @Test
    fun `previousReports devuelve las reportadas con el ejercicio la mas reciente primero`() {
        val s = session(todayExercise(), focus = "squat")
        val hist = historyOf(
            listOf(
                log(9, completed(), reports = listOf(report("lumbar"))),
                log(2, completed(), reports = listOf(report("knee_patellar"))),
                log(16, completed(), reports = listOf(report("lumbar"))),
                log(
                    1,
                    completed("curl", "Curl", curlMuscles),
                    reports = listOf(report("elbow_medial", exerciseId = "curl")),
                ),
            ),
        )
        val prev = DiscomfortCauseEngine.previousReports(s, hist)
        assertEquals(listOf("knee_patellar", "lumbar"), prev.map { it.discomfortId })
        assertEquals(2, prev.last().sessionsCount)
        assertTrue(prev.first().lastReportedAtMs > prev.last().lastReportedAtMs)
    }

    // ── Aviso de la sesión ──

    @Test
    fun `sessionNotice devuelve la linea de F2 cuando la semana lo demuestra`() {
        val hist = historyOf(baselineLogs(20) + log(3, completed(sets = sets(20))))
        val s = session(todayExercise(sets = sets(7, rir = 3)), focus = "squat")
        val notice = DiscomfortCauseEngine.sessionNotice(s, hist)
        assertNotNull(notice)
        assertEquals("knee_patellar", notice!!.discomfortId)
        assertEquals(
            "Esta semana llevas un 35${nbsp}% más de series de cuádriceps que tu media. " +
                "Si la rodilla avisa, empieza por ahí.",
            notice.headline,
        )
    }

    @Test
    fun `formatNumber usa coma decimal`() {
        assertEquals("35", DiscomfortCauseEngine.formatNumber(35.0))
        assertEquals("7,5", DiscomfortCauseEngine.formatNumber(7.5))
        assertEquals("33,3", DiscomfortCauseEngine.formatNumber(33.333))
    }
}
