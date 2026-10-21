package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.RecoveryLearningObservation
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
// Simulador de aprendizaje de τ (paquete 0.3 de Rings v4)
//
// Bucle cerrado «usuario con τ verdadero ⇄ aprendiz». Réplica fiel del flujo de producción
// `AugeViewModel.saveWellbeing/applyManualBatteries` → `learnFromManualAdjustment`
// (`AugeViewModel.kt:410-460`, `:596-702`, `:712-835`, `:1098-1130`), con las funciones REALES
// `LegacyEmaReference.updatePersonalizedRecoveryHours` y `updateSystemRecoveryHours`.
//
// CÓMO ENCHUFA EL PAQUETE 3.2 (aprendiz bayesiano / Kalman): implementar [SimLearner] y pasarlo como
// `learnerFactory` a [AugeLearnerSimulator.runUser] / [AugeLearnerSimulator.runPopulation]. Cada [SimGesture]
// trae el historial causal, los ajustes, la base de ejercicios, la zona y el instante del gesto, de modo que
// el aprendiz nuevo puede REEVALUAR el motor real (`AugeRecoveryEngine.*` con `nowOverrideMs = tiempoMs`) con
// el caché candidato para construir h(θ); el [CurrentEmaLearner] actual no lo usa. Las métricas (error de τ
// tras 8/16/30 gestos, sesgo firmado, % de actualizaciones que alejan τ del verdadero) salen solo del
// `cache()` del aprendiz, así que no hay que tocar el simulador.
//
// Pasos para el paquete 3.2:
//  1. `class MiAprendiz : SimLearner { cache() = estado actual; onGesture(g) = actualizar con g.observaciones }`.
//  2. h(θ): `AugeLearnerSimulator.muscleBattery(g.ajustes, g.exerciseDb, músculo, g.historial, g.tiempoMs, cacheCandidato,
//     capacidad)` con `muscleCapacity(...)` (no depende de τ), y `systemBatteries(...)` para Energía (`.cnc`) y Columna
//     (`.spinal`). Son las mismas llamadas con las que el simulador pinta lo «predicho» y lo «sentido».
//  3. `AugeLearnerSimulator.runPopulation(users, learnerFactory = { MiAprendiz() })` y comparar `metrics(...)`,
//     `metricsByOwnObservations(...)` y `awayStats(...)` con las cifras fijadas en `AugeLearnerBiasCharacterizationTest`
//     (el aprendiz actual y la referencia «congelado» ya están ahí). Escenarios: `SCENARIOS` (se pueden añadir otros).
// El simulador genera un gesto por día de entrenamiento (17:00 UTC, una hora antes de la sesión); el aprendiz decide qué
// observaciones usar (el actual descarta `sentido == predicho`, los gestos fuera de 8 h–14 d desde la última sesión y
// Columna sin estímulo axial).
// ═══════════════════════════════════════════════════════════════════════════════

enum class SimChannel { MUSCLE, ENERGY, SPINAL }

/**
 * Lo que el usuario hace sobre un canal en un gesto de readiness.
 *
 * `sentido` es el valor ENTERO que puso en el deslizador (lo que el motor real calcula con su τ verdadero más
 * ruido gaussiano, redondeado y acotado a 0..100). `predicho` es el valor entero que la pantalla mostraba antes de
 * corregir (el motor real con el caché que el aprendiz tiene en ese momento). `muscle` es la clave tal como el
 * ViewModel se la pasa al aprendiz: `"cns"`, `"spinal"` o el pilar muscular (p. ej. `"Pectorales"`).
 */
data class SimObservation(
    val channel: SimChannel,
    val muscle: String,
    val sentido: Int,
    val predicho: Int,
)

/**
 * Un gesto de readiness previo a la sesión. Además de las observaciones trae todo lo necesario para que un
 * aprendiz futuro reevalúe el motor: historial causal (solo logs con fecha ≤ `tiempoMs`), ajustes, base de
 * ejercicios, zona y el instante.
 */
data class SimGesture(
    val tiempoMs: Long,
    val observaciones: List<SimObservation>,
    val historial: List<WorkoutLog>,
    val ajustes: Settings,
    val exerciseDb: Map<String, ExerciseMuscleInfo>,
    val zona: ZoneId = ZoneId.of("UTC"),
)

/** Interfaz que el paquete 3.2 implementa para enchufar su actualizador al mismo simulador. */
interface SimLearner {
    /** Caché que el usuario «vería» en pantalla: el que el motor usa para predecir. */
    fun cache(): AugeAdaptiveCache

    fun onGesture(gesto: SimGesture)
}

/**
 * El aprendiz ACTUAL de producción, réplica de `AugeViewModel.learnFromManualAdjustment` para el gesto de
 * readiness (`saveWellbeing`: `sessionCnsDrain = sessionSpinalDrain = 0`, así que el estrés de Energía/Columna sale de
 * `calculateCompletedSessionDrain(últimaSesión)` y el muscular de `muscleStressFromLog`):
 *
 * - solo viaja lo que difiere de la predicción en pantalla (`sentido != predicho`: la «guarda de planta»);
 * - última sesión = el log más reciente con fecha ≤ ahora + 1 h; solo aprende con 8 h ≤ Δt ≤ 14 d;
 * - Columna solo con estímulo axial (`PerformanceTauLearner.sessionHasAxialStimulus`);
 * - Energía/Columna con `updateSystemRecoveryHours`, músculos con `updatePersonalizedRecoveryHours`;
 * - `totalObservations` (contador GLOBAL que fija α) sube 1 por gesto en que «algo se aprendió», y todos los canales
 *   del gesto usan el contador del inicio del gesto.
 */
class CurrentEmaLearner(initial: AugeAdaptiveCache = AugeAdaptiveCache()) : SimLearner {
    private var current: AugeAdaptiveCache = initial

    /** Observaciones que se enviaron al motor en el último gesto (diagnóstico). */
    var lastBuilt: List<RecoveryLearningObservation> = emptyList()
        private set

    override fun cache(): AugeAdaptiveCache = current

    override fun onGesture(gesto: SimGesture) {
        lastBuilt = emptyList()
        val energy = gesto.observaciones.firstOrNull { it.channel == SimChannel.ENERGY }
        val spinal = gesto.observaciones.firstOrNull { it.channel == SimChannel.SPINAL }
        val muscles = gesto.observaciones.filter { it.channel == SimChannel.MUSCLE }

        val manualNeural = energy?.let { e -> e.sentido.takeIf { it != e.predicho } }
        val manualSpinal = spinal?.let { s -> s.sentido.takeIf { it != s.predicho } }
        val manualMuscles = muscles.filter { it.sentido != it.predicho }
        val predictedNeural = energy?.predicho
        val predictedSpinal = spinal?.predicho

        val hasSystemSignal = (manualNeural != null && predictedNeural != null && manualNeural != predictedNeural) ||
            (manualSpinal != null && predictedSpinal != null && manualSpinal != predictedSpinal)
        val hasMuscleSignal = manualMuscles.isNotEmpty()
        if (!hasSystemSignal && !hasMuscleSignal) return

        val lastSession = gesto.historial
            .filter { val ms = AugeUtils.logDateMs(it); ms > 0L && ms <= gesto.tiempoMs + 3_600_000L }
            .maxByOrNull { AugeUtils.logDateMs(it) }
            ?: return
        val derivedHoursSince = (gesto.tiempoMs - AugeUtils.logDateMs(lastSession)) / 3_600_000.0
        if (derivedHoursSince < LegacyEmaReference.MIN_HOURS_FOR_TAU_LEARNING) return
        if (derivedHoursSince > MAX_HOURS_FOR_TAU_LEARNING) return

        val base = current
        var updated = base
        var learned = false

        val lastDrain = runCatching {
            AugeFatigueEngine.calculateCompletedSessionDrain(
                completedExercises = lastSession.completedExercises,
                exerciseDb = gesto.exerciseDb,
                settings = gesto.ajustes,
                durationMinutes = lastSession.durationMinutes,
            )
        }.getOrNull()
        val cnsStress = lastDrain?.cns?.toDouble()
        val spinalStress = lastDrain?.spinal?.toDouble()

        val cnsObs = if (manualNeural != null && predictedNeural != null && cnsStress != null && cnsStress > 0.0) {
            RecoveryLearningObservation(
                muscle = "cns",
                predictedBattery = predictedNeural,
                actualBattery = manualNeural,
                sessionStress = cnsStress,
                hoursSinceSession = derivedHoursSince,
            )
        } else {
            null
        }
        val spinalObs = if (
            manualSpinal != null &&
            predictedSpinal != null &&
            spinalStress != null &&
            spinalStress > 0.0 &&
            PerformanceTauLearner.sessionHasAxialStimulus(lastSession.completedExercises, gesto.exerciseDb)
        ) {
            RecoveryLearningObservation(
                muscle = "spinal",
                predictedBattery = predictedSpinal,
                actualBattery = manualSpinal,
                sessionStress = spinalStress,
                hoursSinceSession = derivedHoursSince,
            )
        } else {
            null
        }

        val built = ArrayList<RecoveryLearningObservation>()
        if (cnsObs != null || spinalObs != null) {
            val (newCnsTau, newSpinalTau) = LegacyEmaReference.updateSystemRecoveryHours(
                currentCnsTau = updated.cnsRecoveryHours,
                currentSpinalTau = updated.spinalRecoveryHours,
                cnsObservation = cnsObs,
                spinalObservation = spinalObs,
                totalObservations = base.totalObservations,
            )
            updated = updated.copy(cnsRecoveryHours = newCnsTau, spinalRecoveryHours = newSpinalTau)
            learned = true
            if (cnsObs != null) built += cnsObs
            if (spinalObs != null) built += spinalObs
        }

        for (m in manualMuscles) {
            val muscleStress = muscleStressFromLog(lastSession, m.muscle, gesto) ?: continue
            val obs = RecoveryLearningObservation(
                muscle = m.muscle,
                predictedBattery = m.predicho,
                actualBattery = m.sentido.coerceIn(0, 100),
                sessionStress = muscleStress,
                hoursSinceSession = derivedHoursSince,
            )
            val nextHours = LegacyEmaReference.updatePersonalizedRecoveryHours(
                current = updated.personalizedRecoveryHours,
                observation = obs,
                totalObservations = base.totalObservations,
            )
            built += obs
            if (nextHours != updated.personalizedRecoveryHours) {
                updated = updated.copy(personalizedRecoveryHours = nextHours)
                learned = true
            }
        }

        lastBuilt = built
        if (!learned) return
        current = updated.copy(
            totalObservations = base.totalObservations + 1,
            lastUpdatedMs = gesto.tiempoMs,
        )
    }

    /** Réplica de `AugeViewModel.muscleStressFromLog` (`:1108-1124`). */
    private fun muscleStressFromLog(log: WorkoutLog, muscle: String, gesto: SimGesture): Double? {
        val key = toAugeAdaptiveMuscleKey(muscle)
        val impact = log.muscularImpactV2?.perMuscle?.entries
            ?.firstOrNull { toAugeAdaptiveMuscleKey(it.key) == key }
            ?.value
        val fromImpact = impact?.immediateDrainPct?.takeIf { it > 0.0 }
            ?: impact?.stressUnits?.takeIf { it > 0.0 }
        if (fromImpact != null) return fromImpact
        val computed = AugeRecoveryEngine.calculateMuscleSessionStress(
            muscleName = muscle,
            log = log,
            settings = gesto.ajustes,
            exerciseDb = gesto.exerciseDb,
            adaptiveCache = AugeAdaptiveCache(),
        )
        return computed.takeIf { it > 0.0 }
    }

    private companion object {
        /** `AugeViewModel.MAX_HOURS_FOR_TAU_LEARNING`. */
        const val MAX_HOURS_FOR_TAU_LEARNING = 14.0 * 24.0
    }
}

/** Escenario del bucle cerrado: τ verdadero = defecto × `tauFactor` en TODOS los parámetros y ruido σ del deslizador. */
data class SimScenario(
    val name: String,
    val tauFactor: Double,
    val noiseSigma: Double,
    /** Semilla del ruido: dos escenarios con la misma sal comparten los sorteos (solo cambia σ). */
    val noiseSalt: Long,
)

/** Una actualización de τ de un parámetro dentro de un gesto. */
data class SimUpdate(
    val gesture: Int,
    val param: String,
    val before: Double,
    val after: Double,
    val implied: Double?,
    /** Horas entre la última sesión y el gesto (lo que el ViewModel pasa como `hoursSinceSession`). */
    val hours: Double,
)

/** Resultado de un usuario en un escenario. */
class SimUserRun(
    val userId: String,
    val scenario: SimScenario,
    val gestures: Int,
    /** n gestos → (parámetro → τ del aprendiz). */
    val tauAtMilestone: Map<Int, Map<String, Double>>,
    /** n gestos → (parámetro → observaciones creadas hasta entonces). */
    val obsAtMilestone: Map<Int, Map<String, Int>>,
    /** parámetro → (n observaciones PROPIAS del parámetro → τ del aprendiz justo tras la n-ésima). */
    val tauAtOwnObs: Map<String, Map<Int, Double>>,
    val updates: List<SimUpdate>,
    /** Observaciones creadas por canal. */
    val observations: Map<SimChannel, Int>,
    /** De ellas, las que cayeron en la guarda de planta (`sentido == predicho` ⇒ no se aprende). */
    val plantaGuard: Map<SimChannel, Int>,
)

data class SimMetric(
    val scenario: String,
    val param: String,
    val n: Int,
    val trueTau: Double,
    val users: Int,
    val mae: Double,
    val bias: Double,
    val meanObs: Double,
)

/** Cociente medio τ_implícito / τ_verdadero de las actualizaciones de un tramo horario. */
data class SimBucket(val label: String, val updates: Int, val meanRatio: Double)

data class SimAway(val updates: Int, val counted: Int, val away: Int) {
    /** % de actualizaciones (con |τ − τ_verdadero| > 2 h antes o después) que alejan τ del verdadero. */
    val pct: Double get() = if (counted == 0) Double.NaN else 100.0 * away / counted
}

object AugeLearnerSimulator {
    const val CNS = "cns"
    const val SPINAL = "spinal"
    const val DEFAULT_CNS_TAU = 36.0
    const val DEFAULT_SPINAL_TAU = 52.0

    /** Umbral de «|τ aprendido − τ verdadero| > 2 h» de la métrica de actualizaciones que alejan. */
    const val AWAY_DEAD_BAND_HOURS = 2.0

    private const val HOUR_MS = AugeSyntheticUserHarness.HOUR_MS
    private const val DAY_MS = AugeSyntheticUserHarness.DAY_MS

    val PILLARS = listOf(
        "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps", "Isquiosurales",
        "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Erectores Espinales", "Core",
    )

    /** Hitos en gestos globales (la tabla del plan usa 8, 16 y 30; la corrida larga añade 60 y 100). */
    val MILESTONES = listOf(8, 16, 30, 60, 100)

    /** Hitos en observaciones PROPIAS del parámetro (comparables con «tras 30 obs» de la simulación del plan). */
    val OWN_OBS_MILESTONES = listOf(8, 16, 30)

    /** Tabla del plan 6.6. */
    val SCENARIOS = listOf(
        SimScenario("tau verdadero = defecto", 1.0, 4.0, 1L),
        SimScenario("tau verdadero = defecto x 1,25", 1.25, 4.0, 2L),
        SimScenario("tau verdadero = defecto x 0,75", 0.75, 4.0, 3L),
        SimScenario("usuario ruidoso sigma=7", 1.0, 7.0, 1L),
    )

    /** Clave de parámetro del músculo seguido (`toAugeAdaptiveMuscleKey`). */
    fun muscleParam(pillar: String): String = toAugeAdaptiveMuscleKey(pillar)

    fun defaultTau(param: String): Double = when (param) {
        CNS -> DEFAULT_CNS_TAU
        SPINAL -> DEFAULT_SPINAL_TAU
        else -> LegacyEmaReference.defaultRecoveryHours(param)
    }

    fun tauOf(cache: AugeAdaptiveCache, param: String): Double = when (param) {
        CNS -> cache.cnsRecoveryHours ?: DEFAULT_CNS_TAU
        SPINAL -> cache.spinalRecoveryHours ?: DEFAULT_SPINAL_TAU
        else -> cache.personalizedRecoveryHours[param] ?: LegacyEmaReference.defaultRecoveryHours(param)
    }

    /** Parámetros con valor explícito en el caché más Energía y Columna. */
    private fun paramsOf(cache: AugeAdaptiveCache): List<String> =
        cache.personalizedRecoveryHours.keys.toList() + listOf(CNS, SPINAL)

    /** Caché «verdadero»: τ de los 13 pilares, Energía y Columna = defecto × factor. */
    fun trueCache(tauFactor: Double): AugeAdaptiveCache = AugeAdaptiveCache(
        personalizedRecoveryHours = PILLARS.associate { pillar ->
            val key = muscleParam(pillar)
            key to LegacyEmaReference.defaultRecoveryHours(key) * tauFactor
        },
        cnsRecoveryHours = DEFAULT_CNS_TAU * tauFactor,
        spinalRecoveryHours = DEFAULT_SPINAL_TAU * tauFactor,
    )

    // ─── τ implícito de la referencia EMA congelada ───────────────────────────

    /** τ implícito que devuelve la inversión congelada (sin tope de ±25 %); `null` si la inversión no emite. */
    fun impliedTauOrNull(obs: RecoveryLearningObservation): Double? =
        LegacyEmaReference.deriveImpliedRecoveryTime(obs)

    // ─── Evaluadores de motor real (mismos filtros que la ruta de Home) ────────

    private fun historyForBatteries(causal: List<WorkoutLog>, tMs: Long): List<WorkoutLog> {
        val from = tMs - 30L * DAY_MS
        return causal.filter { val ms = AugeUtils.logDateMs(it); ms > 0L && ms in from..tMs }
    }

    private fun historyForCapacity(causal: List<WorkoutLog>, tMs: Long): List<WorkoutLog> {
        val from = tMs - 30L * DAY_MS
        return causal.filter { val ms = AugeUtils.logDateMs(it); ms > 0L && ms in from until tMs }
    }

    /** Capacidad de trabajo del músculo en `tMs`, como la precalcula `getPerMuscleBatteries` (no depende de τ). */
    fun muscleCapacity(
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        muscle: String,
        causal: List<WorkoutLog>,
        tMs: Long,
    ): Double = AugeMuscleCapacityEngine.calculateUserWorkCapacity(
        muscleName = muscle,
        history = historyForCapacity(causal, tMs),
        settings = settings,
        exerciseDb = exerciseDb,
        completionInstantIso = Instant.ofEpochMilli(tMs).toString(),
        adaptiveCache = AugeAdaptiveCache(),
    )

    /**
     * Batería de UN músculo con el motor real (`calculateMuscleBattery`), con el mismo recorte de 30 días y la
     * misma capacidad que usa `getPerMuscleBatteries`. Ruta rápida del simulador: una prueba de conformidad
     * (`AugeSyntheticUserInvariantsTest`) la compara con la ruta completa de Home.
     */
    fun muscleBattery(
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        muscle: String,
        causal: List<WorkoutLog>,
        tMs: Long,
        cache: AugeAdaptiveCache,
        capacity: Double,
    ): Int = AugeRecoveryEngine.calculateMuscleBattery(
        muscleName = muscle,
        history = historyForBatteries(causal, tMs),
        wellbeing = null,
        settings = settings,
        exerciseDb = exerciseDb,
        adaptiveCache = cache,
        precomputedCapacity = capacity,
        nowOverride = tMs,
    ).recoveryScore

    /**
     * Energía (`.cnc`) y Columna (`.spinal`) tal como las pinta `snapshot.batteries`. Se pasa `precomputedMuscles =
     * emptyMap()` para no recalcular los 13 pilares (y no caer en la capacidad de respaldo con reloj real): `cnc` y
     * `spinal` no dependen de ese mapa. El valor `muscular` de este resultado NO es válido.
     */
    fun systemBatteries(
        settings: Settings,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
        causal: List<WorkoutLog>,
        tMs: Long,
        cache: AugeAdaptiveCache,
    ): GlobalBatteries = AugeRecoveryEngine.calculateGlobalBatteries(
        history = causal,
        wellbeing = null,
        settings = settings,
        exerciseDb = exerciseDb,
        adaptiveCache = cache,
        precomputedMuscles = emptyMap(),
        articularBatteries = emptyMap(),
        nowOverrideMs = tMs,
    )

    // ─── Bucle cerrado ────────────────────────────────────────────────────────

    private class GestureCtx(
        val tMs: Long,
        val known: List<WorkoutLog>,
        val topMuscles: List<String>,
        val axial: Boolean,
        val hoursSince: Double,
    )

    private class TrueValues(val energy: Int, val spinal: Int, val muscles: Map<String, Int>)

    private fun gaussian(rng: Random): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        val u2 = rng.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /**
     * Gesto de readiness en CADA día de entrenamiento, una hora antes de la sesión (17:00 UTC), tras la sesión
     * anterior. Se puede repetir para varios escenarios con el mismo historial y las mismas evaluaciones del «usuario
     * verdadero» (memoizadas), que no dependen del aprendiz.
     */
    fun runUser(
        user: SyntheticUser,
        history: SyntheticHistory,
        scenarios: List<SimScenario> = SCENARIOS,
        gestures: Int = 30,
        musclesPerGesture: Int = 3,
        learnerFactory: () -> SimLearner = { CurrentEmaLearner() },
        trackedMuscle: String = "Pectorales",
    ): List<SimUserRun> {
        val settings = AugeSyntheticUserHarness.settingsFor(user)
        val db = AugeSyntheticUserHarness.exerciseDb
        val trackedParams = listOf(muscleParam(trackedMuscle), CNS, SPINAL)

        // Contextos de gesto: lo que no depende del aprendiz.
        val contexts = ArrayList<GestureCtx>()
        val lastIndex = minOf(gestures, history.sessions.size - 1)
        for (g in 1..lastIndex) {
            val tMs = history.sessions[g].startMs - HOUR_MS
            val known = history.logsUpTo(tMs)
            val last = known.maxByOrNull { AugeUtils.logDateMs(it) } ?: continue
            val top = last.muscularImpactV2?.perMuscle?.entries
                ?.sortedWith(compareByDescending<Map.Entry<String, MuscleSessionImpactV2>> { it.value.stressUnits }.thenBy { it.key })
                ?.take(musclesPerGesture)
                ?.map { it.key }
                .orEmpty()
            contexts += GestureCtx(
                tMs = tMs,
                known = known,
                topMuscles = top,
                axial = PerformanceTauLearner.sessionHasAxialStimulus(last.completedExercises, db),
                hoursSince = (tMs - AugeUtils.logDateMs(last)) / 3_600_000.0,
            )
        }

        val capacityMemo = HashMap<String, Double>()
        fun capacity(index: Int, ctx: GestureCtx, muscle: String): Double =
            capacityMemo.getOrPut("$index|$muscle") { muscleCapacity(settings, db, muscle, ctx.known, ctx.tMs) }

        val trueCaches = HashMap<Double, AugeAdaptiveCache>()
        val trueMemo = HashMap<String, TrueValues>()
        fun trueValues(index: Int, ctx: GestureCtx, factor: Double): TrueValues =
            trueMemo.getOrPut("$factor|$index") {
                val cache = trueCaches.getOrPut(factor) { trueCache(factor) }
                val system = systemBatteries(settings, db, ctx.known, ctx.tMs, cache)
                TrueValues(
                    energy = system.cnc,
                    spinal = system.spinal,
                    muscles = ctx.topMuscles.associateWith { muscle ->
                        muscleBattery(settings, db, muscle, ctx.known, ctx.tMs, cache, capacity(index, ctx, muscle))
                    },
                )
            }

        return scenarios.map { scenario ->
            val rng = Random(user.seed * 7919L + scenario.noiseSalt * 104_729L + 17L)
            val learner = learnerFactory()
            val tauAtMilestone = HashMap<Int, Map<String, Double>>()
            val obsAtMilestone = HashMap<Int, Map<String, Int>>()
            val tauAtOwnObs = HashMap<String, HashMap<Int, Double>>()
            val obsByParam = HashMap<String, Int>()
            val observationsByChannel = HashMap<SimChannel, Int>()
            val plantaByChannel = HashMap<SimChannel, Int>()
            val updates = ArrayList<SimUpdate>()

            contexts.forEachIndexed { index, ctx ->
                val gestureNo = index + 1
                val before = learner.cache()
                val truth = trueValues(index, ctx, scenario.tauFactor)
                val system = systemBatteries(settings, db, ctx.known, ctx.tMs, before)
                // Sorteos fijos por gesto (energía, columna, 3 músculos) para que el flujo no dependa de qué canales haya.
                val noise = DoubleArray(5) { gaussian(rng) }
                fun felt(truthValue: Int, slot: Int): Int =
                    (truthValue + scenario.noiseSigma * noise[slot]).roundToInt().coerceIn(0, 100)

                val observations = ArrayList<SimObservation>()
                observations += SimObservation(SimChannel.ENERGY, CNS, felt(truth.energy, 0), system.cnc)
                if (ctx.axial) {
                    observations += SimObservation(SimChannel.SPINAL, SPINAL, felt(truth.spinal, 1), system.spinal)
                }
                ctx.topMuscles.forEachIndexed { k, muscle ->
                    val predicted = muscleBattery(settings, db, muscle, ctx.known, ctx.tMs, before, capacity(index, ctx, muscle))
                    val truthMuscle = truth.muscles.getValue(muscle)
                    observations += SimObservation(SimChannel.MUSCLE, muscle, felt(truthMuscle, 2 + k), predicted)
                }
                observations.forEach { o ->
                    observationsByChannel[o.channel] = (observationsByChannel[o.channel] ?: 0) + 1
                    if (o.sentido == o.predicho) plantaByChannel[o.channel] = (plantaByChannel[o.channel] ?: 0) + 1
                    val param = when (o.channel) {
                        SimChannel.ENERGY -> CNS
                        SimChannel.SPINAL -> SPINAL
                        SimChannel.MUSCLE -> toAugeAdaptiveMuscleKey(o.muscle)
                    }
                    obsByParam[param] = (obsByParam[param] ?: 0) + 1
                }

                learner.onGesture(
                    SimGesture(
                        tiempoMs = ctx.tMs,
                        observaciones = observations,
                        historial = ctx.known,
                        ajustes = settings,
                        exerciseDb = db,
                    ),
                )

                val after = learner.cache()
                val implied: Map<String, Double?> = (learner as? CurrentEmaLearner)?.lastBuilt
                    ?.associate { it.muscle.lowercase().trim() to impliedTauOrNull(it) }
                    .orEmpty()
                for (param in paramsOf(after)) {
                    val b = tauOf(before, param)
                    val a = tauOf(after, param)
                    if (a != b) updates += SimUpdate(gestureNo, param, b, a, implied[param], ctx.hoursSince)
                }
                trackedParams.forEach { p ->
                    val n = obsByParam[p] ?: 0
                    if (n in OWN_OBS_MILESTONES) {
                        val recorded = tauAtOwnObs.getOrPut(p) { HashMap() }
                        if (!recorded.containsKey(n)) recorded[n] = tauOf(after, p)
                    }
                }
                if (gestureNo in MILESTONES) {
                    tauAtMilestone[gestureNo] = trackedParams.associateWith { tauOf(after, it) }
                    obsAtMilestone[gestureNo] = trackedParams.associateWith { obsByParam[it] ?: 0 }
                }
            }
            SimUserRun(
                userId = user.id,
                scenario = scenario,
                gestures = contexts.size,
                tauAtMilestone = tauAtMilestone,
                obsAtMilestone = obsAtMilestone,
                tauAtOwnObs = tauAtOwnObs,
                updates = updates,
                observations = observationsByChannel,
                plantaGuard = plantaByChannel,
            )
        }
    }

    /** Población completa: un hilo por usuario (determinista: el orden de la salida es el de `users`). */
    fun runPopulation(
        users: List<SyntheticUser>,
        scenarios: List<SimScenario> = SCENARIOS,
        gestures: Int = 30,
        musclesPerGesture: Int = 3,
        learnerFactory: () -> SimLearner = { CurrentEmaLearner() },
        trackedMuscle: String = "Pectorales",
    ): Map<String, List<SimUserRun>> {
        val perUser = AugeSyntheticUserHarness.parallelMap(users) { user ->
            val history = AugeSyntheticUserHarness.buildHistory(user)
            runUser(user, history, scenarios, gestures, musclesPerGesture, learnerFactory, trackedMuscle)
        }
        return scenarios.indices.associate { s ->
            scenarios[s].name to perUser.map { it[s] }
        }
    }

    // ─── Métricas ─────────────────────────────────────────────────────────────

    fun metrics(
        runs: List<SimUserRun>,
        scenario: SimScenario,
        params: List<String>,
        milestones: List<Int> = MILESTONES,
    ): List<SimMetric> = params.flatMap { param ->
        val trueTau = defaultTau(param) * scenario.tauFactor
        milestones.mapNotNull { n ->
            val rows = runs.filter { it.tauAtMilestone.containsKey(n) }
            if (rows.isEmpty()) return@mapNotNull null
            val errors = rows.map { it.tauAtMilestone.getValue(n).getValue(param) - trueTau }
            SimMetric(
                scenario = scenario.name,
                param = param,
                n = n,
                trueTau = trueTau,
                users = rows.size,
                mae = errors.map { abs(it) }.average(),
                bias = errors.average(),
                meanObs = rows.map { it.obsAtMilestone.getValue(n).getValue(param).toDouble() }.average(),
            )
        }
    }

    /** Como [metrics] pero con el hito en observaciones PROPIAS del parámetro (n = 8, 16, 30 observaciones de ese parámetro). */
    fun metricsByOwnObservations(
        runs: List<SimUserRun>,
        scenario: SimScenario,
        params: List<String>,
        milestones: List<Int> = OWN_OBS_MILESTONES,
    ): List<SimMetric> = params.flatMap { param ->
        val trueTau = defaultTau(param) * scenario.tauFactor
        milestones.mapNotNull { n ->
            val taus = runs.mapNotNull { it.tauAtOwnObs[param]?.get(n) }
            if (taus.isEmpty()) return@mapNotNull null
            SimMetric(
                scenario = scenario.name,
                param = param,
                n = n,
                trueTau = trueTau,
                users = taus.size,
                mae = taus.map { abs(it - trueTau) }.average(),
                bias = taus.map { it - trueTau }.average(),
                meanObs = n.toDouble(),
            )
        }
    }

    /** Cociente medio τ_implícito / τ_verdadero por tramo de horas desde la sesión (con el ruido del escenario). */
    fun impliedRatioByHours(runs: List<SimUserRun>, scenario: SimScenario, include: (String) -> Boolean): List<SimBucket> {
        val buckets = listOf(Triple("8-30 h", 8.0, 30.0), Triple("30-60 h", 30.0, 60.0), Triple(">60 h", 60.0, 10_000.0))
        return buckets.map { (label, lo, hi) ->
            val ratios = runs.flatMap { it.updates }
                .filter { include(it.param) && it.implied != null && it.hours >= lo && it.hours < hi }
                .map { it.implied!! / (defaultTau(it.param) * scenario.tauFactor) }
            SimBucket(label, ratios.size, if (ratios.isEmpty()) Double.NaN else ratios.average())
        }
    }

    /**
     * % de actualizaciones que ALEJAN τ del verdadero. Se cuentan las actualizaciones con |τ − τ_verdadero| > 2 h antes
     * o después de actualizar (la zona de ±2 h alrededor de la verdad no se juzga); «aleja» = el error absoluto crece.
     */
    fun awayStats(runs: List<SimUserRun>, scenario: SimScenario, include: (String) -> Boolean): SimAway {
        var updates = 0
        var counted = 0
        var away = 0
        runs.forEach { run ->
            run.updates.forEach { u ->
                if (!include(u.param)) return@forEach
                updates++
                val trueTau = defaultTau(u.param) * scenario.tauFactor
                val err0 = abs(u.before - trueTau)
                val err1 = abs(u.after - trueTau)
                if (max(err0, err1) > AWAY_DEAD_BAND_HOURS) {
                    counted++
                    if (err1 > err0) away++
                }
            }
        }
        return SimAway(updates, counted, away)
    }
}
