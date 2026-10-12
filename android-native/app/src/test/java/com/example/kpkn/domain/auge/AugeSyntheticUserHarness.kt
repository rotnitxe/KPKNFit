package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.channelScore
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Usuario sintético del arnés de Rings v4 (paquete 0.3).
 *
 * `intensity` desplaza el RPE medio de sus series (p. ej. ±1). Con la misma `seed`, el historial y la
 * trayectoria son idénticos bit a bit.
 */
data class SyntheticUser(
    val id: String,
    val athlete: AthleteType,
    val sessionsPerWeek: Int,
    val intensity: Double,
    val weeks: Int,
    val seed: Long,
)

/** Plantillas de sesión del arnés. */
enum class SyntheticTemplate(val label: String) {
    LEGS("Piernas"),
    PUSH("Empuje"),
    PULL("Tirón"),
    FULL("Cuerpo completo"),
}

/** Una sesión ya completada, con su log (incluye `muscularImpactV2` calculado como producción). */
data class SyntheticSession(
    val index: Int,
    val template: SyntheticTemplate,
    val startMs: Long,
    val completionMs: Long,
    val log: WorkoutLog,
)

class SyntheticHistory(
    val user: SyntheticUser,
    val startMs: Long,
    val sessions: List<SyntheticSession>,
) {
    val logs: List<WorkoutLog> = sessions.map { it.log }

    /** Historial causal: solo lo completado hasta `tMs` (inclusive), igual que vería Home en ese instante. */
    fun logsUpTo(tMs: Long): List<WorkoutLog> = sessions.filter { it.completionMs <= tMs }.map { it.log }
}

/**
 * Números que devuelve la ruta de Home en un instante (sin texto).
 *
 * `energy`/`spinal`/`muscular` son `GlobalBatteries` (tras desaceleración y suelo); `energyRaw`/`spinalRaw`
 * son `calculateSystemicFatigue(...).first` y `calculateSpinalBattery(...)`; `dash*` son los puntajes del
 * dashboard; `acwr` se calcula con el instante EXPLÍCITO (el anillo muscular global de producción lo aplica
 * con el reloj real y hoy da nulo con estas fechas, ver KDoc de [AugeSyntheticUserHarness]).
 */
data class SimSample(
    val tMs: Long,
    val perMuscle: Map<String, Int>,
    val muscular: Int,
    val energy: Int,
    val spinal: Int,
    val energyRaw: Int,
    val spinalRaw: Int,
    val dashMuscular: Int,
    val dashEnergy: Int,
    val dashStructure: Int,
    val dashOverall: Int,
    val acwr: Double?,
    val recentSessions: Int,
    val logCount: Int,
)

class SyntheticTrajectory(
    val user: SyntheticUser,
    val startMs: Long,
    val history: SyntheticHistory,
    val samples: List<SimSample>,
)

/** Sesión que se cierra: la real del historial o una hipotética más pesada/ligera sobre el mismo historial previo. */
enum class ParityVariant(val label: String) {
    ACTUAL("real"),
    HEAVY("pesada"),
    LIGHT("ligera"),
}

/**
 * Divergencia vista previa del cierre ↔ Home para una sesión (todas con signo: vista previa − Home).
 * Hallazgo F-23: hoy NO es ≤ 1; el paquete 2.3 la baja.
 */
data class PreviewHomeDivergence(
    val userId: String,
    val sessionIndex: Int,
    val template: SyntheticTemplate,
    val variant: ParityVariant,
    /** Sesiones del propio usuario en los 10 días previos (la fatiga previa es lo que separa ambas rutas). */
    val sessionsIn10Days: Int,
    val muscleDiffs: Map<String, Int>,
    val muscular: Int,
    val energy: Int,
    val spinalRaw: Int,
    val structure: Int,
) {
    val worstMuscle: String? get() = muscleDiffs.maxByOrNull { abs(it.value) }?.key
    val maxMuscleAbs: Int get() = muscleDiffs.values.maxOfOrNull { abs(it) } ?: 0
    val maxGlobalAbs: Int get() = maxOf(abs(muscular), abs(energy), abs(spinalRaw), abs(structure))
    val maxAbs: Int get() = maxOf(maxMuscleAbs, maxGlobalAbs)
}

/**
 * Arnés de usuarios sintéticos con los motores REALES de AUGE (nada reimplementado).
 *
 * - Historiales generados con `kotlin.random.Random(seed)` sobre una base de ejercicios heterogénea propia;
 *   cada log lleva `muscularImpactV2` calculado con la receta de producción
 *   (`WorkoutFinishController`: `fromCompletedExercises` → `evaluate` → `capacitiesFor` → `evaluate`).
 * - [sample] recorre la ruta de Home en un instante dado (`getPerMuscleBatteries` → `calculateGlobalBatteries`
 *   → `calculateSystemicFatigue`/`calculateSpinalBattery` → `calculateRecoveryDashboard` + `muscularAcwrFor`).
 * - Todo el reloj es explícito: `nowOverrideMs`/`nowOverride`/`nowMs` y `ZoneId.of("UTC")`.
 *
 * Fecha base LEJANA ([BASE_START_MS], lunes 2025-03-03 10:00 UTC) a propósito: `applyMuscularAcwr`
 * (`AugeRecoveryEngine.kt:370`) y `AugeTtcEngine.calculateArticularBatteries` usan el reloj REAL y no admiten
 * inyección (candidatas al paquete 1.7). Con fechas lejanas las ventanas del reloj real quedan vacías y el
 * resultado es estable: el anillo muscular global y el dashboard reflejan hoy «ACWR no disponible» y el
 * articular no se llama (`articularBatteries = emptyMap()`, suelo 100) hasta que 1.4/1.7 inyecten el reloj.
 *
 * Modo ligero por defecto (3 usuarios × 28 días); completo con la variable de entorno `AUGE_SIM_FULL=1`
 * (10 usuarios × 90 días).
 */
object AugeSyntheticUserHarness {
    val ZONE: ZoneId = ZoneId.of("UTC")

    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24L * HOUR_MS

    /** Desplazamiento del test de invariancia al tiempo absoluto: 52 semanas exactas (mismo día de la semana). */
    const val SHIFT_MS = 364L * DAY_MS

    const val SAMPLE_STEP_HOURS = 6

    /** Origen 10:00 UTC → las sesiones empiezan a las 18:00 UTC. */
    private const val SESSION_START_OFFSET_MS = 8L * HOUR_MS

    /** Lunes 2025-03-03 10:00 UTC. */
    val BASE_START_MS: Long = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()

    fun isFullMode(): Boolean = System.getenv("AUGE_SIM_FULL") == "1"

    // ─── Base de ejercicios heterogénea ───────────────────────────────────────

    private fun primary(muscle: String, contribution: Double? = null) =
        InvolvedMuscle(muscle, MuscleRole.PRIMARY, contribution)

    private fun secondary(muscle: String, contribution: Double? = null) =
        InvolvedMuscle(muscle, MuscleRole.SECONDARY, contribution)

    private fun stabilizer(muscle: String, contribution: Double? = null) =
        InvolvedMuscle(muscle, MuscleRole.STABILIZER, contribution)

    private fun exercise(
        id: String,
        name: String,
        equipment: String,
        efc: Double,
        cnc: Double,
        ssc: Double,
        axial: Double,
        vararg muscles: InvolvedMuscle,
    ) = ExerciseMuscleInfo(
        id = id,
        name = name,
        equipment = equipment,
        efc = efc,
        cnc = cnc,
        ssc = ssc,
        axialLoadFactor = axial,
        involvedMuscles = muscles.toList(),
    )

    /**
     * Compuestos pesados con axial 1,0 (sentadilla, rumano), bisagras, presses sin axial (banca, inclinado) y
     * aislamientos suaves; efc/cnc/ssc distintos por ejercicio; los 13 pilares aparecen con músculos reales.
     */
    val exerciseDb: Map<String, ExerciseMuscleInfo> = listOf(
        exercise(
            "sim-sentadilla", "Sentadilla", "barra", 4.2, 4.5, 1.0, 1.0,
            primary("Cuádriceps"), secondary("Glúteos", 0.5), stabilizer("Erectores Espinales", 0.4), stabilizer("Core", 0.3),
        ),
        exercise(
            "sim-rdl", "Peso muerto rumano", "barra", 3.8, 4.0, 1.4, 1.0,
            primary("Isquiosurales"), secondary("Glúteos", 0.6), secondary("Erectores Espinales", 0.5), stabilizer("Trapecio", 0.3),
        ),
        exercise(
            "sim-prensa", "Prensa de piernas", "máquina", 3.0, 2.5, 0.5, 0.2,
            primary("Cuádriceps"), secondary("Glúteos", 0.5),
        ),
        exercise("sim-curl-femoral", "Curl femoral", "máquina", 2.0, 1.8, 0.2, 0.0, primary("Isquiosurales")),
        exercise("sim-talones", "Elevación de talones", "máquina", 1.5, 1.2, 0.1, 0.3, primary("Pantorrillas")),
        exercise(
            "sim-hip-thrust", "Hip thrust", "barra", 3.4, 3.0, 0.7, 0.3,
            primary("Glúteos"), secondary("Isquiosurales", 0.4),
        ),
        exercise(
            "sim-banca", "Press banca", "barra", 3.2, 3.5, 0.35, 0.0,
            primary("Pectorales"), secondary("Tríceps", 0.45), secondary("Deltoides Anterior", 0.35),
        ),
        exercise(
            "sim-inclinado", "Press inclinado con mancuernas", "mancuernas", 3.0, 3.0, 0.3, 0.0,
            primary("Pectorales"), secondary("Deltoides Anterior", 0.45), secondary("Tríceps", 0.35),
        ),
        exercise("sim-aperturas", "Aperturas en polea", "polea", 2.0, 1.8, 0.2, 0.0, primary("Pectorales")),
        exercise(
            "sim-militar", "Press militar", "barra", 3.4, 3.6, 0.9, 0.7,
            primary("Deltoides"), secondary("Tríceps", 0.5), stabilizer("Trapecio", 0.3), stabilizer("Core", 0.3),
        ),
        exercise("sim-laterales", "Elevaciones laterales", "mancuernas", 1.6, 1.2, 0.1, 0.0, primary("Deltoides Lateral")),
        exercise("sim-triceps-polea", "Extensión de tríceps en polea", "polea", 1.5, 1.2, 0.1, 0.0, primary("Tríceps")),
        exercise(
            "sim-remo", "Remo con barra", "barra", 3.4, 3.4, 0.9, 0.8,
            primary("Dorsales"), secondary("Bíceps", 0.4), secondary("Trapecio", 0.5), stabilizer("Erectores Espinales", 0.4),
        ),
        exercise(
            "sim-dominadas", "Dominadas", "peso corporal", 3.2, 3.0, 0.3, 0.0,
            primary("Dorsales"), secondary("Bíceps", 0.5), stabilizer("Trapecio", 0.3),
        ),
        exercise(
            "sim-face-pull", "Face pull", "polea", 1.6, 1.3, 0.1, 0.0,
            primary("Deltoides Posterior"), secondary("Trapecio", 0.4),
        ),
        exercise("sim-curl-biceps", "Curl de bíceps", "barra", 1.5, 1.2, 0.1, 0.0, primary("Bíceps")),
        exercise("sim-encogimientos", "Encogimientos", "mancuernas", 2.0, 1.8, 0.4, 0.5, primary("Trapecio")),
        exercise(
            "sim-hiperextension", "Hiperextensión", "banco", 2.0, 1.5, 0.8, 0.6,
            primary("Erectores Espinales"), secondary("Glúteos", 0.4),
        ),
        exercise("sim-abdomen", "Rueda abdominal", "rueda", 1.8, 1.5, 0.3, 0.0, primary("Abdomen"), secondary("Core", 0.5)),
    ).associateBy { it.id }

    // ─── Plantillas ───────────────────────────────────────────────────────────

    private class Slot(
        val id: String,
        val minSets: Int,
        val maxSets: Int,
        val minReps: Int,
        val maxReps: Int,
        val baseKg: Double,
        val restSeconds: Int,
    )

    private fun slot(id: String, sets: IntRange, reps: IntRange, kg: Double, rest: Int) =
        Slot(id, sets.first, sets.last, reps.first, reps.last, kg, rest)

    /** Cada plantilla es una lista de grupos; de cada grupo se elige una alternativa con el Random del usuario. */
    private val templates: Map<SyntheticTemplate, List<List<Slot>>> = mapOf(
        SyntheticTemplate.LEGS to listOf(
            listOf(slot("sim-sentadilla", 3..5, 5..8, 95.0, 180)),
            listOf(slot("sim-rdl", 3..4, 6..10, 80.0, 150), slot("sim-hip-thrust", 3..4, 8..12, 90.0, 120)),
            listOf(slot("sim-prensa", 3..4, 8..12, 160.0, 120)),
            listOf(slot("sim-curl-femoral", 3..3, 10..12, 40.0, 90)),
            listOf(slot("sim-talones", 3..4, 10..15, 70.0, 75)),
        ),
        SyntheticTemplate.PUSH to listOf(
            listOf(slot("sim-banca", 3..5, 5..9, 70.0, 150)),
            listOf(slot("sim-inclinado", 3..4, 8..12, 26.0, 120), slot("sim-aperturas", 3..3, 12..15, 18.0, 90)),
            listOf(slot("sim-militar", 3..3, 6..10, 40.0, 120)),
            listOf(slot("sim-laterales", 3..4, 12..15, 10.0, 60)),
            listOf(slot("sim-triceps-polea", 3..3, 10..15, 30.0, 75)),
        ),
        SyntheticTemplate.PULL to listOf(
            listOf(slot("sim-remo", 3..5, 6..10, 65.0, 150)),
            listOf(slot("sim-dominadas", 3..4, 6..10, 10.0, 150)),
            listOf(slot("sim-face-pull", 3..3, 12..15, 25.0, 60)),
            listOf(slot("sim-curl-biceps", 3..3, 8..12, 15.0, 75)),
            listOf(slot("sim-encogimientos", 3..3, 10..12, 70.0, 90), slot("sim-hiperextension", 3..3, 10..15, 20.0, 90)),
        ),
        SyntheticTemplate.FULL to listOf(
            listOf(slot("sim-sentadilla", 3..3, 5..8, 90.0, 180)),
            listOf(slot("sim-banca", 3..3, 6..10, 65.0, 150)),
            listOf(slot("sim-remo", 3..3, 6..10, 60.0, 150), slot("sim-dominadas", 3..3, 6..10, 10.0, 150)),
            listOf(slot("sim-militar", 2..3, 8..10, 37.5, 120)),
            listOf(slot("sim-abdomen", 3..3, 12..15, 0.0, 60)),
            listOf(slot("sim-curl-biceps", 2..2, 10..12, 14.0, 75), slot("sim-triceps-polea", 2..2, 10..12, 27.5, 75)),
        ),
    )

    /** Patrón semanal fijo: pares (día desde el lunes, plantilla). 4 → lun/mar/jue/vie a las 18:00 UTC. */
    private fun weeklyPattern(perWeek: Int): List<Pair<Int, SyntheticTemplate>> = when (perWeek.coerceIn(1, 6)) {
        1 -> listOf(0 to SyntheticTemplate.FULL)
        2 -> listOf(0 to SyntheticTemplate.FULL, 3 to SyntheticTemplate.FULL)
        3 -> listOf(0 to SyntheticTemplate.PUSH, 2 to SyntheticTemplate.LEGS, 4 to SyntheticTemplate.PULL)
        4 -> listOf(
            0 to SyntheticTemplate.LEGS, 1 to SyntheticTemplate.PUSH,
            3 to SyntheticTemplate.PULL, 4 to SyntheticTemplate.FULL,
        )
        5 -> listOf(
            0 to SyntheticTemplate.LEGS, 1 to SyntheticTemplate.PUSH, 2 to SyntheticTemplate.PULL,
            4 to SyntheticTemplate.FULL, 5 to SyntheticTemplate.LEGS,
        )
        else -> listOf(
            0 to SyntheticTemplate.PUSH, 1 to SyntheticTemplate.PULL, 2 to SyntheticTemplate.LEGS,
            3 to SyntheticTemplate.PUSH, 4 to SyntheticTemplate.PULL, 5 to SyntheticTemplate.LEGS,
        )
    }

    private val athleteCycle = listOf(
        AthleteType.ENTHUSIAST, AthleteType.POWERLIFTER, AthleteType.BODYBUILDER, AthleteType.HYBRID,
        AthleteType.CALISTHENICS, AthleteType.POWERBUILDER,
    )

    // ─── Usuarios ─────────────────────────────────────────────────────────────

    /** 3 usuarios × 28 días (modo ligero): tres atletas, tres frecuencias, tres intensidades. */
    fun lightUsers(): List<SyntheticUser> = listOf(
        SyntheticUser("L1", AthleteType.ENTHUSIAST, 4, 0.0, 4, 1101L),
        SyntheticUser("L2", AthleteType.POWERLIFTER, 3, 1.0, 4, 1102L),
        SyntheticUser("L3", AthleteType.BODYBUILDER, 5, -1.0, 4, 1103L),
    )

    /** 10 usuarios × 90 días (13 semanas, modo completo). */
    fun fullUsers(): List<SyntheticUser> {
        val perWeek = listOf(2, 3, 4, 4, 5, 6, 3, 4, 5, 4)
        val intensity = listOf(-1.0, -0.5, 0.0, 0.5, 1.0)
        return (0 until 10).map { i ->
            SyntheticUser(
                id = "F${i + 1}",
                athlete = athleteCycle[i % athleteCycle.size],
                sessionsPerWeek = perWeek[i],
                intensity = intensity[i % intensity.size],
                weeks = 13,
                seed = 2000L + i,
            )
        }
    }

    fun invariantUsers(): List<SyntheticUser> = if (isFullMode()) fullUsers() else lightUsers()

    /**
     * Usuarios del bucle cerrado: 4 sesiones/semana; por defecto 8 semanas (32 sesiones ⇒ hasta 30 gestos) y 60 (ligero)
     * o 300 (completo) usuarios. Con `weeks = 26` hay 104 sesiones ⇒ hasta 100 gestos (corrida larga).
     */
    fun learnerUsers(count: Int = if (isFullMode()) 300 else 60, weeks: Int = 8): List<SyntheticUser> {
        val intensity = listOf(-0.5, 0.0, 0.5)
        return (0 until count).map { i ->
            SyntheticUser(
                id = "C${i + 1}",
                athlete = athleteCycle[i % athleteCycle.size],
                sessionsPerWeek = 4,
                intensity = intensity[i % intensity.size],
                weeks = weeks,
                seed = 5000L + i,
            )
        }
    }

    fun settingsFor(user: SyntheticUser): Settings = Settings(athleteType = user.athlete)

    // ─── Historial ────────────────────────────────────────────────────────────

    /**
     * `muscularImpactV2` como producción (`WorkoutFinishController.kt:236-266`): `fromCompletedExercises` → `evaluate`
     * → `AugeMuscleCapacityEngine.capacitiesFor(músculos, historialPrevio, …, completionInstantIso)` →
     * `evaluate(…, capacitiesAtCompletion = caps)`. Nunca se generan logs sin V2 (su capacidad de respaldo usa el reloj real).
     */
    fun productionImpact(
        completedExercises: List<CompletedExercise>,
        completionIso: String,
        priorHistory: List<WorkoutLog>,
        settings: Settings,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
    ): MuscularSessionImpactV2 {
        val canonicalInput = MuscularSessionImpactEngine.fromCompletedExercises(
            completedExercises = completedExercises,
            completionInstantIso = completionIso,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = cache,
        )
        val initial = MuscularSessionImpactEngine.evaluate(
            input = canonicalInput,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = cache,
        )
        val capacities = AugeMuscleCapacityEngine.capacitiesFor(
            muscles = initial.involvedVolumeMuscles,
            history = priorHistory,
            settings = settings,
            exerciseDb = exerciseDb,
            completionInstantIso = completionIso,
            adaptiveCache = cache,
        )
        return MuscularSessionImpactEngine.evaluate(
            input = canonicalInput,
            exerciseDb = exerciseDb,
            settings = settings,
            adaptiveCache = cache,
            capacitiesAtCompletion = capacities,
        )
    }

    /** Ejercicio completado a mano (series iguales) para los casos pequeños de las pruebas. */
    fun completedExercise(
        exerciseId: String,
        sets: Int,
        reps: Int,
        weightKg: Double,
        rpe: Double,
        restSeconds: Int,
        idPrefix: String,
    ): CompletedExercise {
        val info = exerciseDb.getValue(exerciseId)
        return CompletedExercise(
            exerciseId = exerciseId,
            exerciseName = info.name,
            exerciseDbId = exerciseId,
            sets = List(sets) { i -> makeSet("$idPrefix-$exerciseId-$i", weightKg, reps, rpe, isFailure = false) },
            restTime = restSeconds,
        )
    }

    private fun makeSet(id: String, weight: Double, reps: Int, rpe: Double, isFailure: Boolean) = CompletedSet(
        id = id,
        weight = weight,
        reps = reps,
        rpe = rpe,
        rir = null,
        isFailure = isFailure,
        actualIntensityMode = IntensityMode.RPE,
        actualIntensityValue = rpe,
    )

    /** Log de producción para un conjunto de ejercicios (V2 como producción, con `priorHistory` como historial previo). */
    fun logFor(
        id: String,
        sessionName: String,
        exercises: List<CompletedExercise>,
        completionMs: Long,
        durationMinutes: Int,
        priorHistory: List<WorkoutLog>,
        settings: Settings,
    ): WorkoutLog {
        val iso = Instant.ofEpochMilli(completionMs).toString()
        return WorkoutLog(
            id = id,
            programId = "sim",
            sessionId = "sim-$id",
            sessionName = sessionName,
            date = iso,
            durationMinutes = durationMinutes,
            completedExercises = exercises,
            muscularImpactV2 = productionImpact(exercises, iso, priorHistory, settings),
        )
    }

    private fun roundToPlate(kg: Double): Double = (kg / 2.5).roundToInt() * 2.5

    private fun buildSession(
        user: SyntheticUser,
        index: Int,
        template: SyntheticTemplate,
        startMs: Long,
        rng: Random,
        strength: Double,
        athleteLoad: Double,
        priorLogs: List<WorkoutLog>,
        settings: Settings,
    ): SyntheticSession {
        val logId = "${user.id}-s$index"
        val sessionJitter = rng.nextDouble(-0.5, 0.5)
        val exercises = ArrayList<CompletedExercise>()
        var totalSets = 0
        templates.getValue(template).forEachIndexed { exIdx, group ->
            val slot = group[rng.nextInt(group.size)]
            val info = exerciseDb.getValue(slot.id)
            val nSets = slot.minSets + rng.nextInt(slot.maxSets - slot.minSets + 1)
            val baseReps = slot.minReps + rng.nextInt(slot.maxReps - slot.minReps + 1)
            val weight = roundToPlate(slot.baseKg * strength * athleteLoad * (0.97 + 0.06 * rng.nextDouble()))
            val sets = List(nSets) { i ->
                val reps = (baseReps - if (i >= 2) rng.nextInt(2) else 0).coerceAtLeast(1)
                val progress = if (nSets <= 1) 0.0 else i.toDouble() / (nSets - 1)
                val rpeRaw = 7.5 + user.intensity + sessionJitter + progress
                val rpe = ((rpeRaw * 2.0).roundToInt() / 2.0).coerceIn(6.0, 10.0)
                val failure = rpe >= 10.0 && i == nSets - 1 && rng.nextDouble() < 0.3
                makeSet("$logId-$exIdx-$i", weight, reps, rpe, failure)
            }
            totalSets += nSets
            exercises += CompletedExercise(
                exerciseId = slot.id,
                exerciseName = info.name,
                exerciseDbId = slot.id,
                sets = sets,
                restTime = slot.restSeconds,
            )
        }
        val duration = (14 + totalSets * 2.2 + rng.nextInt(9) - 4).roundToInt().coerceIn(30, 110)
        val completionMs = startMs + duration * 60_000L
        val iso = Instant.ofEpochMilli(completionMs).toString()
        val impact = productionImpact(exercises, iso, priorLogs, settings)
        val log = WorkoutLog(
            id = logId,
            programId = "sim",
            sessionId = "sim-$index",
            sessionName = template.label,
            date = iso,
            durationMinutes = duration,
            completedExercises = exercises,
            muscularImpactV2 = impact,
        )
        return SyntheticSession(index, template, startMs, completionMs, log)
    }

    /** Historial completo del usuario. No depende del tiempo absoluto: solo `startMs` desplaza las fechas. */
    fun buildHistory(user: SyntheticUser, startMs: Long = BASE_START_MS): SyntheticHistory {
        val settings = settingsFor(user)
        val rng = Random(user.seed)
        val strength = 0.8 + 0.5 * rng.nextDouble()
        val athleteLoad = when (user.athlete) {
            AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> 1.2
            AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> 1.1
            AthleteType.ENTHUSIAST -> 0.9
            else -> 1.0
        }
        val pattern = weeklyPattern(user.sessionsPerWeek)
        val sessions = ArrayList<SyntheticSession>()
        var index = 0
        for (week in 0 until user.weeks) {
            for ((day, template) in pattern) {
                val sessionStart = startMs + (week * 7L + day) * DAY_MS + SESSION_START_OFFSET_MS
                sessions += buildSession(
                    user = user,
                    index = index,
                    template = template,
                    startMs = sessionStart,
                    rng = rng,
                    strength = strength,
                    athleteLoad = athleteLoad,
                    priorLogs = sessions.map { it.log },
                    settings = settings,
                )
                index++
            }
        }
        return SyntheticHistory(user, startMs, sessions)
    }

    // ─── Ruta de Home en un instante ──────────────────────────────────────────

    /**
     * La ruta de Home en `tMs` con el historial CAUSAL (solo logs con fecha ≤ `tMs`):
     * `getPerMuscleBatteries` → `calculateGlobalBatteries(precomputedMuscles)` → `calculateSystemicFatigue` y
     * `calculateSpinalBattery` (valores crudos) → `calculateRecoveryDashboard` y `muscularAcwrFor(history, tMs)`.
     *
     * Sin bienestar, sin sueño, sin nutrición (el seguimiento nutricional está apagado por defecto: no toca el reloj)
     * y sin articulares (`AugeTtcEngine.calculateArticularBatteries` usa el reloj real).
     */
    fun sample(
        user: SyntheticUser,
        history: List<WorkoutLog>,
        tMs: Long,
        cache: AugeAdaptiveCache = AugeAdaptiveCache(),
        includeRaw: Boolean = true,
    ): SimSample {
        val settings = settingsFor(user)
        val causal = history.filter { val ms = AugeUtils.logDateMs(it); ms > 0L && ms <= tMs }
        val muscles = AugeRecoveryEngine.getPerMuscleBatteries(
            history = causal,
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            adaptiveCache = cache,
            nowOverrideMs = tMs,
            zone = ZONE,
        )
        val batteries = AugeRecoveryEngine.calculateGlobalBatteries(
            history = causal,
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            adaptiveCache = cache,
            precomputedMuscles = muscles,
            articularBatteries = emptyMap(),
            nowOverrideMs = tMs,
        )
        val energyRaw: Int
        val spinalRaw: Int
        if (includeRaw) {
            energyRaw = AugeRecoveryEngine.calculateSystemicFatigue(
                history = causal,
                wellbeing = null,
                settings = settings,
                exerciseDb = exerciseDb,
                adaptiveCache = cache,
                nowOverride = tMs,
            ).first
            spinalRaw = AugeRecoveryEngine.calculateSpinalBattery(
                history = causal,
                wellbeing = null,
                settings = settings,
                exerciseDb = exerciseDb,
                adaptiveCache = cache,
                nowOverride = tMs,
            )
        } else {
            energyRaw = -1
            spinalRaw = -1
        }
        val recent = AugeRecoveryEngine.recentSessionCount(causal, tMs)
        val dashboard = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = batteries,
            perMuscle = muscles,
            articularBatteries = emptyMap(),
            wellbeing = null,
            sleepLogs = emptyList(),
            recentSessionCount = recent,
        )
        return SimSample(
            tMs = tMs,
            perMuscle = muscles.mapValues { it.value.recoveryScore },
            muscular = batteries.muscular,
            energy = batteries.cnc,
            spinal = batteries.spinal,
            energyRaw = energyRaw,
            spinalRaw = spinalRaw,
            dashMuscular = dashboard.channelScore(RecoveryChannelId.MUSCULAR),
            dashEnergy = dashboard.channelScore(RecoveryChannelId.SYSTEM),
            dashStructure = dashboard.channelScore(RecoveryChannelId.STRUCTURE),
            dashOverall = dashboard.overallScore,
            acwr = AugeRecoveryEngine.muscularAcwrFor(causal, tMs),
            recentSessions = recent,
            logCount = causal.size,
        )
    }

    /** Historial + muestreo cada [stepHours] horas desde `startMs` hasta el final de las semanas del usuario. */
    fun trajectory(
        user: SyntheticUser,
        startMs: Long = BASE_START_MS,
        stepHours: Int = SAMPLE_STEP_HOURS,
        includeRaw: Boolean = true,
    ): SyntheticTrajectory {
        val history = buildHistory(user, startMs)
        val endMs = startMs + user.weeks * 7L * DAY_MS
        val samples = ArrayList<SimSample>()
        var t = startMs
        while (t <= endMs) {
            samples += sample(user, history.logs, t, includeRaw = includeRaw)
            t += stepHours * HOUR_MS
        }
        return SyntheticTrajectory(user, startMs, history, samples)
    }

    // ─── Hash canónico ────────────────────────────────────────────────────────

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun fmtOrNull(value: Double?): String = if (value == null) "null" else fmt(value)

    /**
     * Volcado canónico: claves ordenadas, `Locale.ROOT`, 3 decimales y TIEMPOS RELATIVOS al origen (así el mismo
     * recorrido desplazado en el calendario produce el mismo volcado).
     */
    fun canonicalDump(trajectory: SyntheticTrajectory): String {
        val sb = StringBuilder()
        val u = trajectory.user
        val origin = trajectory.startMs
        sb.append("user|").append(u.id).append('|').append(u.athlete).append('|').append(u.sessionsPerWeek)
            .append('|').append(fmt(u.intensity)).append('|').append(u.weeks).append('|').append(u.seed).append('\n')
        trajectory.history.sessions.forEach { s ->
            sb.append("log|").append(s.index).append('|').append(s.template).append('|')
                .append(fmt((s.startMs - origin) / HOUR_MS.toDouble())).append('|')
                .append(fmt((s.completionMs - origin) / HOUR_MS.toDouble())).append('|')
                .append(s.log.durationMinutes).append('|').append(s.log.completedExercises.sumOf { it.sets.size }).append('\n')
            val v2 = s.log.muscularImpactV2
            if (v2 != null) {
                sb.append("v2|").append(v2.globalMuscularDrain).append('|').append(v2.setInputHash).append('\n')
                v2.perMuscle.toSortedMap().forEach { (muscle, impact) ->
                    sb.append("v2m|").append(muscle).append('|').append(fmt(impact.stressUnits)).append('|')
                        .append(fmt(impact.capacityAtCompletion)).append('|').append(fmt(impact.immediateDrainPct)).append('|')
                        .append(fmt(impact.directStressUnits)).append('|').append(fmt(impact.indirectStressUnits)).append('\n')
                }
            }
        }
        trajectory.samples.forEach { s ->
            sb.append("t|").append(fmt((s.tMs - origin) / HOUR_MS.toDouble()))
                .append("|mus=").append(s.muscular).append("|en=").append(s.energy).append("|sp=").append(s.spinal)
                .append("|enR=").append(s.energyRaw).append("|spR=").append(s.spinalRaw)
                .append("|dM=").append(s.dashMuscular).append("|dE=").append(s.dashEnergy)
                .append("|dS=").append(s.dashStructure).append("|dO=").append(s.dashOverall)
                .append("|acwr=").append(fmtOrNull(s.acwr)).append("|rec=").append(s.recentSessions)
                .append("|logs=").append(s.logCount).append('\n')
            s.perMuscle.toSortedMap().forEach { (muscle, score) ->
                sb.append("m|").append(muscle).append('=').append(score).append('\n')
            }
        }
        return sb.toString()
    }

    fun hashOf(trajectory: SyntheticTrajectory): String = sha256(canonicalDump(trajectory))

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ─── Vista previa del cierre ↔ Home ───────────────────────────────────────

    /**
     * Para la sesión `sessionIndex`: vista previa del cierre (`previewPostSessionBatteries`, con la misma entrada que
     * `AugeViewModel.computePostSessionPreview`: historial SIN la sesión, log previo SIN V2 y el V2 automático aparte)
     * frente a Home tras añadir esa sesión (con su V2) y evaluar en su instante de cierre.
     */
    fun previewVsHome(
        history: SyntheticHistory,
        sessionIndex: Int,
        variant: ParityVariant = ParityVariant.ACTUAL,
    ): PreviewHomeDivergence {
        val user = history.user
        val settings = settingsFor(user)
        val session = history.sessions[sessionIndex]
        val prior = history.sessions.take(sessionIndex).map { it.log }
        // La sesión que se cierra: la real o una hipotética (más pesada / más ligera) sobre el mismo historial previo.
        val closed: WorkoutLog = when (variant) {
            ParityVariant.ACTUAL -> session.log
            ParityVariant.HEAVY -> logFor(
                id = "${session.log.id}-pesada",
                sessionName = session.template.label,
                exercises = scaleSession(session.log.completedExercises, 1.5, 1.0),
                completionMs = session.completionMs,
                durationMinutes = session.log.durationMinutes,
                priorHistory = prior,
                settings = settings,
            )
            ParityVariant.LIGHT -> logFor(
                id = "${session.log.id}-ligera",
                sessionName = session.template.label,
                exercises = scaleSession(session.log.completedExercises, 0.6, -1.0),
                completionMs = session.completionMs,
                durationMinutes = session.log.durationMinutes,
                priorHistory = prior,
                settings = settings,
            )
        }
        val impact = closed.muscularImpactV2
        val previewLog = WorkoutLog(
            id = "preview",
            programId = "",
            sessionId = "",
            sessionName = "preview",
            date = closed.date,
            completedExercises = closed.completedExercises,
            durationMinutes = closed.durationMinutes,
        )
        val preview = AugeRecoveryEngine.previewPostSessionBatteries(
            baseHistory = prior,
            previewLog = previewLog,
            wellbeing = null,
            settings = settings,
            exerciseDb = exerciseDb,
            adaptiveCache = AugeAdaptiveCache(),
            articularBatteries = emptyMap(),
            nowOverrideMs = session.completionMs,
            completionInstantIso = closed.date,
            inputHash = impact?.setInputHash,
            automaticImpact = impact,
        )
        val home = sample(user, prior + closed, session.completionMs)
        val muscleDiffs = LinkedHashMap<String, Int>()
        preview.perMuscle.toSortedMap().forEach { (muscle, status) ->
            val homeScore = home.perMuscle[muscle] ?: return@forEach
            muscleDiffs[muscle] = status.recoveryScore - homeScore
        }
        val tenDaysAgo = session.completionMs - 10L * DAY_MS
        return PreviewHomeDivergence(
            userId = user.id,
            sessionIndex = sessionIndex,
            template = session.template,
            variant = variant,
            sessionsIn10Days = prior.count { AugeUtils.logDateMs(it) > tenDaysAgo },
            muscleDiffs = muscleDiffs,
            muscular = preview.muscular - home.muscular,
            energy = preview.neural - home.energy,
            spinalRaw = preview.spinalRaw - home.spinal,
            structure = preview.spinal - home.dashStructure,
        )
    }

    /**
     * Variante hipotética de una sesión: cambia el número de series por ejercicio (mín. 2, máx. 8) y desplaza el RPE
     * (acotado a 6..10). Las series nuevas copian cíclicamente las originales con ids únicos.
     */
    fun scaleSession(
        exercises: List<CompletedExercise>,
        setsFactor: Double,
        rpeDelta: Double,
    ): List<CompletedExercise> = exercises.map { ex ->
        val n = (ex.sets.size * setsFactor).roundToInt().coerceIn(2, 8)
        ex.copy(
            sets = List(n) { i ->
                val proto = ex.sets[i % ex.sets.size]
                val rpe = ((proto.rpe ?: 7.5) + rpeDelta).coerceIn(6.0, 10.0)
                proto.copy(
                    id = "${proto.id.substringBeforeLast('-')}-v$i",
                    rpe = rpe,
                    actualIntensityValue = rpe,
                    isFailure = false,
                )
            },
        )
    }

    // ─── Paralelismo determinista ─────────────────────────────────────────────

    /**
     * `map` en paralelo por usuario: cada tarea es independiente (motores puros) y el resultado conserva el orden de
     * entrada, así que el resultado no depende del planificador.
     */
    fun <T, R> parallelMap(items: List<T>, transform: (T) -> R): List<R> {
        if (items.size <= 1) return items.map(transform)
        val threads = minOf(items.size, Runtime.getRuntime().availableProcessors().coerceAtLeast(1), 12)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = items.map { item -> pool.submit(Callable { transform(item) }) }
            return futures.map { it.get() }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            pool.shutdownNow()
        }
    }
}
