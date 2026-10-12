package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * ACWR fiable (WP 1.4, hallazgo F-20). La carga crónica se divide entre las semanas reales de seguimiento
 * (mínimo 2, máximo 4) y no siempre entre 4; el factor muscular del anillo es una rampa continua; el reloj es
 * explícito y los umbrales viven en un solo mapa.
 *
 * Todo con reloj explícito y una base lejana (2025-03-03 10:00 UTC): ninguna ventana de 28 días del reloj real la
 * alcanza, así que el código que lea el reloj escondido se nota (la ventana queda vacía y el factor desaparece).
 * El anillo se mide con los 13 pilares fijos en 60 (`precomputedMuscles`): solo el factor de ACWR puede moverlo.
 */
class AcwrReliabilityTest {
    private var zonaOriginal: TimeZone? = null

    @Before
    fun fijarZona() {
        zonaOriginal = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restaurarZona() {
        zonaOriginal?.let { TimeZone.setDefault(it) }
    }

    private val hour = 3_600_000L
    private val day = 24L * hour

    /** Lunes 2025-03-03 10:00 UTC, mucho antes de hoy a propósito. */
    private val base = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()

    private val db = mapOf(
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.0, cnc = 4.0, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)),
        ),
    )

    private val pilares = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps", "Isquiosurales",
        "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

    // ─── Constructores ───────────────────────────────────────────────────────

    /**
     * Sesión de sentadilla (carga axial y de SNC para los monitores) con [stress] unidades de impacto V2
     * (ACWR muscular). [sets] escala la carga axial y del SNC; [stress] la muscular.
     */
    private fun session(id: String, at: Long, stress: Double = 100.0, sets: Int = 5): WorkoutLog {
        val iso = Instant.ofEpochMilli(at).toString()
        return WorkoutLog(
            id = id,
            programId = "p",
            sessionId = id,
            sessionName = id,
            date = iso,
            durationMinutes = 50,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 150,
                    sets = List(sets) { i -> CompletedSet(id = "$id-$i", weight = 120.0, reps = 5, rpe = 8.0) },
                ),
            ),
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 20,
                perMuscle = mapOf(
                    "Cuádriceps" to MuscleSessionImpactV2(
                        stressUnits = stress,
                        capacityAtCompletion = 260.0,
                        immediateDrainPct = 20.0,
                        directStressUnits = stress,
                        indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Cuádriceps"),
                setInputHash = id,
                contextHash = id,
            ),
        )
    }

    /**
     * Seis semanas estables y una última semana (la más reciente) de [lastWeekStress] por sesión: tres sesiones por
     * semana, en los días −1, −3 y −5 de cada una. Con 300 y 15 series es la semana de carga triple; con 100 y 5, el control.
     */
    private fun peakHistory(now: Long, lastWeekStress: Double, lastWeekSets: Int): List<WorkoutLog> =
        (0..6).flatMap { w ->
            val stress = if (w == 0) lastWeekStress else 100.0
            val sets = if (w == 0) lastWeekSets else 5
            listOf(1, 3, 5).map { o -> session("p$w-$o", now - (7L * w + o) * day, stress, sets) }
        }

    /** Anillo muscular global con los pilares fijos en 60: solo el factor de ACWR del instante [at] puede moverlo. */
    private fun muscularRing(history: List<WorkoutLog>, at: Long): Int {
        val fijos = pilares.associateWith { MuscleRecoveryStatus(it, 60, 0, 0, 0, RecoveryStatus.OPTIMAL) }
        return AugeRecoveryEngine.calculateGlobalBatteries(
            history = history,
            wellbeing = null,
            settings = Settings(),
            exerciseDb = db,
            precomputedMuscles = fijos,
            nowOverrideMs = at,
        ).muscular
    }

    /** Factor de ACWR que se aplicaría al anillo: 1,0 cuando todavía no hay ACWR. */
    private fun appliedFactor(history: List<WorkoutLog>, at: Long): Double =
        AugeRecoveryEngine.muscularAcwrFor(history, at)?.let(AugeClassifiers::muscularLoadFactorFromAcwr) ?: 1.0

    /** Nivel de aviso que sale solo del ACWR (sin extras de frecuencia ni tendencia). */
    private fun levelFromAcwr(acwr: Double?, insufficient: Boolean): LoadAdvisoryLevel = LoadAdvisoryEngine.levelFor(
        acwr = acwr,
        insufficient = insufficient,
        extraWatch = false,
        extraAdjust = false,
        extraUnload = false,
        previous = LoadAdvisoryLevel.NONE,
        allowDrop = true,
    )

    /** Avisos completos (Columna y Energía) para [history] en el instante [now]. */
    private fun advisories(history: List<WorkoutLog>, now: Long) = LoadAdvisoryEngine.evaluate(
        axial = AxialLoadMonitor.evaluate(history, db, now),
        systemic = SystemicLoadMonitor.evaluate(history, db, Settings(), nowMs = now),
        cache = AugeAdaptiveCache(),
        nowMs = now,
    ).advisories

    private fun cns(log: WorkoutLog): Double = AugeFatigueEngine.calculateCompletedSessionDrain(
        completedExercises = log.completedExercises,
        exerciseDb = db,
        settings = Settings(),
        adaptiveCache = AugeAdaptiveCache(),
    ).cns.toDouble()

    // ─── a · Recién llegado ──────────────────────────────────────────────────

    @Test
    fun a_newcomerWithFiveEqualSessionsIn15Days_isNotAlarmed() {
        val now = base
        val history = listOf(15, 12, 8, 5, 1).mapIndexed { i, d -> session("a$i", now - d * day) }

        val muscular = AugeRecoveryEngine.muscularAcwrFor(history, now)
        assertNotNull("5 sesiones en 15 días ya dan ACWR muscular", muscular)
        // Estable ≈ 0,86 con 15/7 semanas de datos; con el denominador fijo ÷4 daba 1,60.
        assertEquals("ACWR muscular del recién llegado", 1.0, muscular!!, 0.25)

        val axial = AxialLoadMonitor.evaluate(history, db, now)
        val systemic = SystemicLoadMonitor.evaluate(history, db, Settings(), nowMs = now)
        assertFalse("axial suficiente", axial.insufficientData)
        assertFalse("sistémico suficiente", systemic.insufficientData)
        assertNotNull(axial.acwr)
        assertNotNull(systemic.acwr)
        assertEquals("ACWR axial del recién llegado", 1.0, axial.acwr!!, 0.25)
        assertEquals("ACWR sistémico del recién llegado", 1.0, systemic.acwr!!, 0.25)
        assertEquals(LoadAdvisoryLevel.NONE, levelFromAcwr(axial.acwr, axial.insufficientData))
        assertEquals(LoadAdvisoryLevel.NONE, levelFromAcwr(systemic.acwr, systemic.insufficientData))
        assertTrue("no debe salir ningún aviso (antes: ADJUST «Hoy baja 10–15 %»)", advisories(history, now).isEmpty())
    }

    // ─── b · Estable ─────────────────────────────────────────────────────────

    @Test
    fun b_stableThreePerWeekForEightWeeks_isAroundOneAndNone() {
        val now = base
        val lastSession = now - 6 * hour
        // Semanas 0..7 con sesiones los días 0, 2 y 4 de cada una; la última sesión es la de la semana 7, día 4.
        val history = (0..7).flatMap { w ->
            listOf(0, 2, 4).map { off -> session("b$w-$off", lastSession - (7 - w) * 7 * day + (off - 4) * day) }
        }

        val muscular = AugeRecoveryEngine.muscularAcwrFor(history, now)
        assertNotNull(muscular)
        assertEquals(1.0, muscular!!, 0.05)
        val axial = AxialLoadMonitor.evaluate(history, db, now)
        val systemic = SystemicLoadMonitor.evaluate(history, db, Settings(), nowMs = now)
        assertNotNull(axial.acwr)
        assertNotNull(systemic.acwr)
        assertEquals(1.0, axial.acwr!!, 0.05)
        assertEquals(1.0, systemic.acwr!!, 0.05)
        assertEquals(LoadAdvisoryLevel.NONE, levelFromAcwr(axial.acwr, axial.insufficientData))
        assertEquals(LoadAdvisoryLevel.NONE, levelFromAcwr(systemic.acwr, systemic.insufficientData))
        assertTrue("estable no avisa", advisories(history, now).isEmpty())
    }

    // ─── c · Pico real ───────────────────────────────────────────────────────

    @Test
    fun c_realPeak_isDangerAndLowersTheMuscularRing() {
        val now = base
        val spike = peakHistory(now, lastWeekStress = 300.0, lastWeekSets = 15)
        val control = peakHistory(now, lastWeekStress = 100.0, lastWeekSets = 5)

        val acwr = AugeRecoveryEngine.muscularAcwrFor(spike, now)
        assertNotNull(acwr)
        assertTrue("ACWR del pico = $acwr", acwr!! > 1.5)
        assertEquals(AugeClassifiers.AcwrZone.DANGER, AugeClassifiers.classifyAcwrZone(acwr))
        assertTrue(AugeClassifiers.muscularLoadFactorFromAcwr(acwr) > 1.06)
        assertEquals("el mismo historial sin pico es estable", 1.0, AugeRecoveryEngine.muscularAcwrFor(control, now)!!, 0.05)

        // El monitor axial también ve el pico (carga axial lineal con las series): ADJUST.
        val axial = AxialLoadMonitor.evaluate(spike, db, now)
        assertNotNull(axial.acwr)
        assertEquals(LoadAdvisoryLevel.ADJUST, levelFromAcwr(axial.acwr, axial.insufficientData))

        val ringSpike = muscularRing(spike, now)
        val ringControl = muscularRing(control, now)
        assertTrue("anillo con pico ($ringSpike) debe quedar por debajo del anillo sin pico ($ringControl)", ringSpike < ringControl)
        assertEquals("sin pico: 60 sin factor", 60, ringControl)
        assertEquals("con pico: 100 − 40 · 1,12", 55, ringSpike)
    }

    // ─── d · Pausa larga con historia antigua ────────────────────────────────

    @Test
    fun d_longPauseWithOldHistory_stillDividesByFourWeeks() {
        val now = base
        // Tres meses de historia (cada 3 días, hasta hace 34 días), las tres últimas sesiones antes de parar
        // y, tras 3 semanas sin entrenar, una sesión grande (3 veces la carga).
        val old = (0 until 26).map { k -> session("d-old$k", now - (109 - 3 * k) * day) }
        val lastBlock = listOf(26, 24, 22).map { session("d-pre$it", now - it * day) }
        val big = session("d-big", now - 1 * day, stress = 300.0, sets = 15)
        val history = old + lastBlock + big

        // Muscular: aguda 300, mes 600, crónica 600 ÷ 4 = 150 ⇒ 2,0 exacto. (Con las semanas de la ventana, 26/7, daría 1,86.)
        val muscular = AugeRecoveryEngine.muscularAcwrFor(history, now)
        assertNotNull(muscular)
        assertEquals(2.0, muscular!!, 1e-9)
        assertEquals(AugeClassifiers.AcwrZone.DANGER, AugeClassifiers.classifyAcwrZone(muscular))

        val axialBig = AxialLoadMonitor.axialUnits(big, db)
        val axialMonth = lastBlock.sumOf { AxialLoadMonitor.axialUnits(it, db) } + axialBig
        assertTrue("la carga axial de la sesión grande es positiva", axialBig > 0.0)
        val axial = AxialLoadMonitor.evaluate(history, db, now)
        assertFalse(axial.insufficientData)
        assertNotNull(axial.acwr)
        assertEquals("axial: aguda ÷ (mes ÷ 4)", axialBig / (axialMonth / 4.0), axial.acwr!!, 1e-9)
        // El pico al volver SÍ se detecta.
        assertEquals(LoadAdvisoryLevel.ADJUST, levelFromAcwr(axial.acwr, axial.insufficientData))

        val cnsBig = cns(big)
        val cnsMonth = lastBlock.sumOf { cns(it) } + cnsBig
        assertTrue("el drenaje de SNC de la sesión grande es positivo", cnsBig > 0.0)
        val systemic = SystemicLoadMonitor.evaluate(history, db, Settings(), nowMs = now)
        assertFalse(systemic.insufficientData)
        assertNotNull(systemic.acwr)
        assertEquals("sistémico: aguda ÷ (mes ÷ 4)", cnsBig / (cnsMonth / 4.0), systemic.acwr!!, 1e-9)
    }

    // ─── e · Sin escalón a los 14 días ───────────────────────────────────────

    /** Recién llegado estable: la misma sesión los lunes y jueves, desde [base]. */
    private fun newcomerSessions(): List<WorkoutLog> = (0..3).flatMap { w ->
        listOf(0, 3).map { off -> session("e$w-$off", base + (7L * w + off) * day) }
    }

    /** Instante de evaluación del día [d] tras la primera sesión: una hora después de la sesión de ese día, si la hay. */
    private fun dayAfterFirst(d: Int): Long = base + d * day + hour

    private fun historyAt(all: List<WorkoutLog>, now: Long): List<WorkoutLog> =
        all.filter { AugeUtils.logDateMs(it) <= now }

    @Test
    fun e_noFactorStepWhenTheFirstAcwrAppears() {
        val all = newcomerSessions()
        assertNull("el día 13 aún no hay ACWR (14 días de margen)", AugeRecoveryEngine.muscularAcwrFor(historyAt(all, dayAfterFirst(13)), dayAfterFirst(13)))
        assertNotNull("el día 14 ya hay ACWR", AugeRecoveryEngine.muscularAcwrFor(historyAt(all, dayAfterFirst(14)), dayAfterFirst(14)))
        for (d in 12..17) {
            val now = dayAfterFirst(d)
            val factor = appliedFactor(historyAt(all, now), now)
            assertEquals("factor de ACWR el día $d (antes saltaba a 1,12 el día 14)", 1.0, factor, 0.01)
        }
    }

    @Test
    fun e_noRingStepNorFalseAdvisoryAroundDay14() {
        val all = newcomerSessions()
        val rings = (13..15).map { d -> muscularRing(historyAt(all, dayAfterFirst(d)), dayAfterFirst(d)) }
        val step = rings.max() - rings.min()
        assertTrue("el anillo muscular salta $step puntos entre los días 13 y 15 ($rings)", step < 2)
        for (d in 14..16) {
            val now = dayAfterFirst(d)
            assertTrue("aviso falso el día $d", advisories(historyAt(all, now), now).isEmpty())
        }
    }

    // ─── f · Propiedad del factor ────────────────────────────────────────────

    @Test
    fun f_factorIsContinuousMonotoneAndBounded() {
        var previous = AugeClassifiers.muscularLoadFactorFromAcwr(0.0)
        assertEquals(1.0, previous, 1e-9)
        for (i in 1..300) {
            val acwr = i / 100.0
            val factor = AugeClassifiers.muscularLoadFactorFromAcwr(acwr)
            assertTrue("no decreciente en $acwr: $previous → $factor", factor >= previous - 1e-12)
            assertTrue("paso de ${factor - previous} en $acwr (máx. 0,0035)", factor - previous <= 0.0035)
            if (acwr <= 1.3) assertEquals("hasta 1,3 vale 1,0 (acwr=$acwr)", 1.0, factor, 1e-9)
            if (acwr >= 1.7) assertEquals("desde 1,7 vale 1,12 (acwr=$acwr)", 1.12, factor, 1e-9)
            previous = factor
        }
        assertEquals("a mitad de rampa", 1.06, AugeClassifiers.muscularLoadFactorFromAcwr(1.5), 1e-9)
    }

    // ─── g · Reloj ───────────────────────────────────────────────────────────

    @Test
    fun g_globalBatteriesApplyTheAcwrFactorOfTheGivenInstant() {
        val now = base
        val history = peakHistory(now, lastWeekStress = 300.0, lastWeekSets = 15)

        val justAfterPeak = muscularRing(history, now)
        val tenDaysLater = muscularRing(history, now + 10 * day)
        val twoMonthsLater = muscularRing(history, now + 60 * day)

        assertEquals("tras el pico: ACWR 2,0 ⇒ 100 − 40 · 1,12", 55, justAfterPeak)
        assertEquals("10 días después la semana aguda está vacía (ACWR 0) ⇒ sin factor", 60, tenDaysLater)
        assertEquals("2 meses después la ventana de 28 días está vacía ⇒ sin ACWR", 60, twoMonthsLater)
        assertTrue("dos instantes tienen que dar dos resultados", justAfterPeak != tenDaysLater)
    }
}
