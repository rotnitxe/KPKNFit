package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.MacroGoals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/**
 * Reloj explícito y evaluaciones causales (WP 1.7, hallazgo F-27).
 *
 * Dos épocas a propósito: [pasado] (2025, muy anterior a hoy) y [futuro] (2085, posterior a cualquier reloj real
 * que corra esta prueba). Un motor que lea el reloj real por debajo ve la primera como «fuera de toda ventana» y la
 * segunda como «justo ahora»; con el instante explícito las dos épocas deben dar exactamente lo mismo. La guarda
 * estructural que complementa estas pruebas es [AugeNoHiddenClockTest].
 *
 * Ninguna prueba lee el reloj ni la zona del equipo: la zona por defecto se fija en UTC (los motores de nutrición y
 * de ancla usan la zona local de la persona) y todo instante sale de [pasado] o de [futuro].
 */
class AugeClockDeterminismTest {
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

    private val hora = 3_600_000L
    private val dia = 24L * hora

    /** Lunes 2025-03-03 10:00 UTC (la base del máster dorado). */
    private val pasado = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()

    /** La misma fecha 60 años después: el reloj real está siempre antes que ella. */
    private val futuro = Instant.parse("2085-03-03T10:00:00Z").toEpochMilli()

    private val settings = Settings()

    private val db = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barbell",
            efc = 3.2, cnc = 3.5, ssc = 0.35, axialLoadFactor = 0.0,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, volumeContribution = 0.45),
                InvolvedMuscle("Deltoides Anterior", MuscleRole.SECONDARY, volumeContribution = 0.35),
            ),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barbell",
            efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
        // Sin erectores, dorsales ni trapecio en la ficha: la guardia espinal solo se mueve por lo que diga el V2 del log.
        "deadlift" to ExerciseMuscleInfo(
            id = "deadlift", name = "Peso muerto", equipment = "barbell",
            efc = 4.5, cnc = 4.8, ssc = 2.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(
                InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, volumeContribution = 1.0),
                InvolvedMuscle("Glúteos", MuscleRole.SECONDARY, volumeContribution = 0.5),
            ),
        ),
        "snatch" to ExerciseMuscleInfo(
            id = "snatch", name = "Arrancada", equipment = "barbell",
            involvedMuscles = listOf(InvolvedMuscle("Deltoides", MuscleRole.PRIMARY)),
        ),
        "curl" to ExerciseMuscleInfo(
            id = "curl", name = "Curl de bíceps", equipment = "dumbbell",
            efc = 1.5, cnc = 1.2, ssc = 0.1, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
    )

    // ─── Constructores ───────────────────────────────────────────────────────

    private fun series(prefijo: String, n: Int, kg: Double, reps: Int, rpe: Double) =
        List(n) { i -> CompletedSet(id = "$prefijo-$i", weight = kg, reps = reps, rpe = rpe) }

    private fun ejercicio(id: String, nombre: String, descanso: Int, series: List<CompletedSet>) =
        CompletedExercise(exerciseId = id, exerciseName = nombre, exerciseDbId = id, restTime = descanso, sets = series)

    private fun log(
        id: String,
        at: Long,
        ejercicios: List<CompletedExercise>,
        impacto: MuscularSessionImpactV2? = null,
    ) = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = id,
        sessionName = id,
        date = Instant.ofEpochMilli(at).toString(),
        durationMinutes = 60,
        completedExercises = ejercicios,
        muscularImpactV2 = impacto,
    )

    /** Impacto V2 con el estrés indicado por músculo (capacidad de cierre = la base de 2600). */
    private fun impactoV2(at: Long, vararg musculos: Pair<String, Double>) = MuscularSessionImpactV2(
        completionInstantIso = Instant.ofEpochMilli(at).toString(),
        globalMuscularDrain = 20,
        perMuscle = musculos.associate { (musculo, estres) ->
            musculo to MuscleSessionImpactV2(
                stressUnits = estres,
                capacityAtCompletion = 2600.0,
                immediateDrainPct = 20.0,
                directStressUnits = estres,
                indirectStressUnits = 0.0,
            )
        },
        involvedVolumeMuscles = musculos.map { it.first }.toSet(),
        setInputHash = "h",
        contextHash = "c",
    )

    /** Sesión dura de pierna con carga axial y de SNC: sentadilla y peso muerto. */
    private fun sesionDura(id: String, at: Long) = log(
        id, at,
        listOf(
            ejercicio("squat", "Sentadilla", 180, series("sq-$id", 6, 140.0, 5, 9.0)),
            ejercicio("deadlift", "Peso muerto", 180, series("dl-$id", 4, 170.0, 4, 9.0)),
        ),
    )

    /** Ocho series de arrancada: carga articular de hombro alta (TTC 3,6 por serie). */
    private fun arrancada(id: String, at: Long) =
        log(id, at, listOf(ejercicio("snatch", "Arrancada", 120, series("sn-$id", 8, 80.0, 3, 10.0))))

    /** Peso muerto pesado: mucha carga espinal en una sola sesión. */
    private fun pesados() = listOf(ejercicio("deadlift", "Peso muerto", 180, series("dl", 10, 180.0, 3, 9.5)))

    // ─── Llamadas a los motores ──────────────────────────────────────────────

    private fun energia(
        historial: List<WorkoutLog>,
        en: Long,
        bienestar: DailyWellbeingLog? = null,
    ) = AugeRecoveryEngine.calculateSystemicFatigue(
        history = historial, wellbeing = bienestar, settings = settings, exerciseDb = db, nowOverride = en,
    ).first

    private fun columna(
        historial: List<WorkoutLog>,
        en: Long,
        bienestar: DailyWellbeingLog? = null,
    ) = AugeRecoveryEngine.calculateSpinalBattery(
        history = historial, wellbeing = bienestar, settings = settings, exerciseDb = db, nowOverride = en,
    )

    private fun musculo(
        nombre: String,
        historial: List<WorkoutLog>,
        en: Long,
        bienestar: DailyWellbeingLog? = null,
    ) = AugeRecoveryEngine.calculateMuscleBattery(
        muscleName = nombre, history = historial, wellbeing = bienestar, settings = settings, exerciseDb = db,
        nowOverride = en,
    ).recoveryScore

    private fun articular(
        historial: List<WorkoutLog>,
        nowMs: Long,
    ): Map<ArticularBattery, ArticularBatteryState> =
        AugeTtcEngine.calculateArticularBatteries(history = historial, exerciseDb = db, nowMs = nowMs)

    private fun multiplicadorNutricional(logs: List<NutritionLog>, metas: Map<LocalDate, DayGoalsResult>, nowMs: Long) =
        NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(nutritionLogs = logs, goalsByDate = metas, nowMs = nowMs)

    private fun interferencias(historial: List<WorkoutLog>, nowMs: Long) =
        InterferenceEngine.calculateHistoricalInterferences(historial, db, settings, nowMs = nowMs)

    // ─── (a) Invariancia al tiempo absoluto ──────────────────────────────────

    @Test
    fun articularBatteriesDoNotDependOnTheAbsoluteTime() {
        fun enLaEpoca(epoca: Long) = articular(listOf(arrancada("a", epoca - 6 * hora)), epoca)
        val a = enLaEpoca(pasado)
        val b = enLaEpoca(futuro)
        ArticularBattery.entries.forEach { ab ->
            assertEquals("$ab: puntaje", a.getValue(ab).recoveryScore, b.getValue(ab).recoveryScore)
            assertEquals("$ab: estrés", a.getValue(ab).accumulatedStress, b.getValue(ab).accumulatedStress, 1e-9)
        }
        // Control: el hombro sí quedó cargado (si no, la igualdad no probaría nada).
        assertTrue(
            "Hombro ${a.getValue(ArticularBattery.SHOULDER).recoveryScore}",
            a.getValue(ArticularBattery.SHOULDER).recoveryScore < 90,
        )
    }

    @Test
    fun articularShoulderIsPinnedAt0And24And48HoursWithAnExplicitClock() {
        val sesion = arrancada("a", pasado)
        fun hombro(horas: Long) = articular(listOf(sesion), pasado + horas * hora).getValue(ArticularBattery.SHOULDER)
        // Ocho series de TTC 3,6 con 3 repeticiones a RPE 10: 30,24 de estrés; la meseta avascular frena las primeras 24 h.
        assertEquals(62, hombro(0).recoveryScore)
        assertEquals(30.24, hombro(0).accumulatedStress, 0.01)
        assertEquals(64, hombro(24).recoveryScore)
        assertEquals(29.05, hombro(24).accumulatedStress, 0.01)
        assertEquals(84, hombro(48).recoveryScore)
        assertEquals(13.05, hombro(48).accumulatedStress, 0.01)
    }

    @Test
    fun fallbackCapacityForLogsWithoutV2DoesNotDependOnTheAbsoluteTime() {
        fun pectoral(epoca: Long, conCargaLarga: Boolean): Int {
            val reciente = log(
                "reciente", epoca - 12 * hora,
                listOf(
                    ejercicio("bench", "Press Banca", 90, series("r1", 3, 80.0, 8, 9.0)),
                    ejercicio("bench", "Press Banca", 90, series("r2", 3, 80.0, 8, 9.0)),
                ),
            )
            // Hace 20 días: fuera de la ventana de 10 días de la batería, dentro de la de 28 de la capacidad de respaldo.
            val larga = log(
                "larga", epoca - 20 * dia,
                List(30) { e -> ejercicio("bench", "Press Banca", 90, series("l$e", 3, 80.0, 8, 9.0)) },
            )
            return musculo("Pectorales", if (conCargaLarga) listOf(larga, reciente) else listOf(reciente), epoca)
        }
        assertEquals(pectoral(pasado, true), pectoral(futuro, true))
        assertEquals(pectoral(pasado, false), pectoral(futuro, false))
        // Control: la carga de hace 20 días sube la capacidad de respaldo, así que la misma sesión pesa menos.
        assertTrue(
            "Con carga larga ${pectoral(pasado, true)} frente a sin ella ${pectoral(pasado, false)}",
            pectoral(pasado, true) > pectoral(pasado, false),
        )
    }

    @Test
    fun nutritionMultiplierDoesNotDependOnTheAbsoluteTime() {
        // Metas para cada día de 2025 a 2090: así, si el motor lee el reloj real, encuentra metas en su ventana.
        val meta = DayGoalsResult.Present(MacroGoals(calorieGoal = 2_000, proteinGoal = 100, carbGoal = 200, fatGoal = 60))
        val metas: Map<LocalDate, DayGoalsResult> = generateSequence(LocalDate.of(2025, 1, 1)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.of(2090, 1, 1)) }
            .associateWith { meta }

        fun enLaEpoca(epoca: Long): NutritionRecoveryResult {
            val fecha = Instant.ofEpochMilli(epoca).toString().take(10)
            val comida = NutritionLog(
                id = "comida", date = "${fecha}T12:00:00.000Z", mealType = MealType.LUNCH,
                foods = listOf(LoggedFood(id = "f", foodName = "Comida", amount = 100.0, calories = 400.0, protein = 10.0)),
            )
            return multiplicadorNutricional(listOf(comida), metas, epoca)
        }
        val a = enLaEpoca(pasado)
        val b = enLaEpoca(futuro)
        assertEquals(a.status, b.status)
        assertEquals(a.recoveryTimeMultiplier, b.recoveryTimeMultiplier, 1e-9)
        // Control: 400 kcal con meta de 2000 es déficit; no es el resultado neutro de «sin comidas» ni de «sin metas».
        assertEquals(NutritionRecoveryStatus.DEFICIT, a.status)
        assertTrue(a.recoveryTimeMultiplier > 1.0)
    }

    @Test
    fun historicalInterferencesDoNotDependOnTheAbsoluteTime() {
        fun enLaEpoca(epoca: Long): List<String> {
            val a = log("A", epoca - 30 * hora, listOf(ejercicio("squat", "Sentadilla", 180, series("a", 5, 140.0, 5, 9.0))))
            val b = log("B", epoca - 6 * hora, listOf(ejercicio("squat", "Sentadilla", 180, series("b", 5, 140.0, 5, 9.0))))
            return interferencias(listOf(a, b), epoca).map {
                "${it.sessionAId}>${it.sessionBId}:${it.interferencePercent}:${it.hoursApart}"
            }
        }
        val a = enLaEpoca(pasado)
        val b = enLaEpoca(futuro)
        assertEquals(a, b)
        // Control: dos sesiones de pierna con 24 h de diferencia sí interfieren.
        assertTrue("Sin interferencias: $a", a.isNotEmpty())
    }

    // ─── (b) R-10: los registros con fecha futura no cuentan ─────────────────

    @Test
    fun futureDatedSessionDoesNotChangeEnergyNorSpine() {
        val previa = sesionDura("previa", pasado - 30 * hora)
        val futura = sesionDura("futura", pasado + 2 * dia)
        assertEquals("Energía", energia(listOf(previa), pasado), energia(listOf(previa, futura), pasado))
        assertEquals("Columna", columna(listOf(previa), pasado), columna(listOf(previa, futura), pasado))
        // Sola, una sesión futura no baja nada: el anillo sigue entero.
        assertEquals(100, energia(listOf(futura), pasado))
        assertEquals(100, columna(listOf(futura), pasado))
    }

    @Test
    fun futureDatedSessionIsAlsoIgnoredWithAManualAnchor() {
        val bienestar = DailyWellbeingLog(
            id = "hoy", date = "2025-03-03",
            manualNeuralBattery = 70, manualSpinalBattery = 70,
            manualBatteryAnchorMs = pasado - 2 * hora,
        )
        val futura = sesionDura("futura", pasado + 2 * dia)
        assertEquals("Energía", energia(emptyList(), pasado, bienestar), energia(listOf(futura), pasado, bienestar))
        assertEquals("Columna", columna(emptyList(), pasado, bienestar), columna(listOf(futura), pasado, bienestar))
    }

    @Test
    fun sessionAtTheEvaluationInstantStillCounts() {
        val ahora = sesionDura("ahora", pasado)
        assertTrue("Energía ${energia(listOf(ahora), pasado)}", energia(listOf(ahora), pasado) < 100)
        assertTrue("Columna ${columna(listOf(ahora), pasado)}", columna(listOf(ahora), pasado) < 100)
    }

    // ─── (c) R-09 a: el propio log no baja la guardia que multiplica su drenaje ─

    @Test
    fun spinalDrainOfASessionDoesNotDependOnItsOwnGuardDrain() {
        val sesionEn = pasado - hora
        // Un log lejano (34 días) alarga la ventana de capacidad a ~5 semanas: la capacidad de la guardia queda en su base.
        val lejana = log("lejana", pasado - 34 * dia, listOf(ejercicio("curl", "Curl de bíceps", 60, series("cu", 3, 12.0, 10, 8.0))))
        // La misma sesión dos veces: solo cambia lo que su V2 dice de erectores, dorsales y trapecio.
        val conGuardia = log(
            "s", sesionEn, pesados(),
            impactoV2(sesionEn, "Erectores Espinales" to 6000.0, "Dorsales" to 6000.0, "Trapecio" to 6000.0),
        )
        val sinGuardia = log("s", sesionEn, pesados(), impactoV2(sesionEn, "Isquiosurales" to 600.0))

        val sin = columna(listOf(lejana, sinGuardia), pasado)
        val con = columna(listOf(lejana, conGuardia), pasado)
        assertEquals("Con y sin erectores/dorsales/trapecio en el propio log", sin, con)
        // Control: la sesión sí drena Columna (si no, la igualdad no probaría nada).
        assertTrue("Columna $sin", sin < 90)
    }

    // ─── (d) R-09 b: el estado de hoy no reescribe el drenaje de sesiones anteriores ─

    private fun guardiasAncladas(ancla: Long) = listOf("Erectores Espinales", "Dorsales", "Trapecio").associateWith {
        ManualMuscleBatteryOverride(battery = 10, anchorEpochMs = ancla, automaticBatteryAtAnchor = 100)
    }

    @Test
    fun todayManualAnchorsDoNotRewriteThePastSpinalDrain() {
        val sesionEn = pasado - 5 * hora
        val sesion = log("temprano", sesionEn, pesados(), impactoV2(sesionEn, "Isquiosurales" to 600.0))
        val sinAnclas = DailyWellbeingLog(id = "hoy", date = "2025-03-03")
        // Anclas de hoy (1 h antes de ahora), DESPUÉS de la sesión de hace 5 h.
        val conAnclas = sinAnclas.copy(manualMuscleOverridesV2 = guardiasAncladas(pasado - hora))
        val sin = columna(listOf(sesion), pasado, sinAnclas)
        assertEquals("Columna con y sin anclas de hoy", sin, columna(listOf(sesion), pasado, conAnclas))
        // Control: la sesión sigue drenando Columna a las 5 h (si no, la igualdad no probaría nada).
        assertTrue("Columna $sin", sin < 90)
    }

    @Test
    fun anchorsOlderThanTheSessionStillWeighOnItsGuard() {
        val sesionEn = pasado - 5 * hora
        val sesion = log("temprano", sesionEn, pesados(), impactoV2(sesionEn, "Isquiosurales" to 600.0))
        val sinAnclas = DailyWellbeingLog(id = "hoy", date = "2025-03-03")
        // Anclas ANTERIORES a la sesión: sí describen el estado de la guardia cuando se entrenó.
        val conAnclas = sinAnclas.copy(manualMuscleOverridesV2 = guardiasAncladas(sesionEn - 2 * hora))
        val sin = columna(listOf(sesion), pasado, sinAnclas)
        val con = columna(listOf(sesion), pasado, conAnclas)
        assertTrue("Columna con anclas previas $con frente a sin ellas $sin", con < sin)
    }

    // ─── Anclas fechadas después del instante evaluado ───────────────────────

    @Test
    fun manualAnchorsDatedAfterTheEvaluatedInstantAreNotApplied() {
        val ancla = pasado + 2 * hora
        val bienestar = DailyWellbeingLog(
            id = "hoy", date = "2025-03-03",
            manualNeuralBattery = 40, manualSpinalBattery = 40,
            manualMuscleBatteries = mapOf("Pectorales" to 40),
            manualBatteryAnchorMs = ancla,
            manualMuscleOverridesV2 = mapOf(
                "Cuádriceps" to ManualMuscleBatteryOverride(battery = 40, anchorEpochMs = ancla, automaticBatteryAtAnchor = 100),
            ),
        )
        // Antes del ancla aún no existía: con la historia vacía todo está al 100.
        assertEquals("Energía", 100, energia(emptyList(), pasado, bienestar))
        assertEquals("Columna", 100, columna(emptyList(), pasado, bienestar))
        assertEquals("Pectorales (ajuste global)", 100, musculo("Pectorales", emptyList(), pasado, bienestar))
        assertEquals("Cuádriceps (ajuste V2)", 100, musculo("Cuádriceps", emptyList(), pasado, bienestar))
        // En el instante del ancla ya se aplica (el borde cuenta): cada valor arranca en el ajustado.
        assertTrue("Energía ${energia(emptyList(), ancla, bienestar)}", energia(emptyList(), ancla, bienestar) in 38..42)
        assertTrue("Columna ${columna(emptyList(), ancla, bienestar)}", columna(emptyList(), ancla, bienestar) in 38..42)
        assertTrue("Pectorales", musculo("Pectorales", emptyList(), ancla, bienestar) in 38..42)
        assertTrue("Cuádriceps", musculo("Cuádriceps", emptyList(), ancla, bienestar) in 38..42)
    }

    // ─── (e) La vista previa de «mañana» usa la articular de ahora + 24 h ────

    @Test
    fun previewTomorrowUsesTheArticularOfNowPlus24Hours() {
        val ahora = pasado
        val manana = ahora + 24 * hora
        val base = log("base", ahora - 20 * hora, listOf(ejercicio("bench", "Press Banca", 120, series("b", 10, 100.0, 8, 10.0))))
        val vista = log("vista", ahora, listOf(ejercicio("bench", "Press Banca", 120, series("v", 3, 60.0, 10, 7.0))))
        val articularAhora = articular(listOf(base), ahora)
        val articularManana = articular(listOf(base, vista), manana)

        val vistaPrevia = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = listOf(base),
            previewLog = vista,
            wellbeing = null,
            settings = settings,
            exerciseDb = db,
            articularBatteries = articularAhora,
            nowOverrideMs = ahora,
        )

        // La misma receta que el preview, a mano y con la articular de mañana (press banca no drena Columna: el
        // anillo crudo vale 100 y solo la mezcla con la articular y la guardia mueve el resultado).
        val historia = listOf(base, vista)
        val musculos = AugeRecoveryEngine.getPerMuscleBatteries(
            history = historia, wellbeing = null, settings = settings, exerciseDb = db, nowOverrideMs = manana,
        )
        val globales = AugeRecoveryEngine.calculateGlobalBatteries(
            history = historia, wellbeing = null, settings = settings, exerciseDb = db,
            precomputedMuscles = musculos, articularBatteries = articularManana, nowOverrideMs = manana,
        )
        fun mezcla(articulares: Map<ArticularBattery, ArticularBatteryState>): Int {
            val (suelo, guardias) = AugeRecoveryEngine.structureInputs(musculos, articulares)
            return AugeRecoveryEngine.structureRingScore(globales.spinal, suelo, guardias)
        }

        assertEquals(mezcla(articularManana), requireNotNull(vistaPrevia.tomorrowSpinal))
        // Control: con la articular de ahora saldría otra cifra, o sea que la prueba distingue las dos.
        assertNotEquals(mezcla(articularAhora), mezcla(articularManana))
    }
}
