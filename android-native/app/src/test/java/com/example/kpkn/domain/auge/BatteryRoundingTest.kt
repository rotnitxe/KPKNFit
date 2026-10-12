package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.screens.home.ringPercent
import com.example.kpkn.screens.home.sessionRecoveryPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * WP 1.5 (F-25): las baterías se redondean, no se truncan. Antes `toInt()` trunca hacia 0: con τ = 48 h el músculo marcaba 99
 * de las ≈ 60 h a las ≈ 130 h (la batería real es 99,3 → 99,9 y nunca llegaba a 100) y toda cifra salía sesgada −0,5.
 * Reloj explícito (`nowOverride`/`nowOverrideMs`) y capacidad fija: ninguna prueba toca el reloj real.
 */
class BatteryRoundingTest {
    private val origin = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
    private val hourMs = 3_600_000L
    private val capacity = 2_600.0
    private val settings = Settings()

    // ─── Fixtures ───────────────────────────────────────────────────────────

    /** Sesión con impacto V2 congelado: el músculo recibe `stress` y la capacidad al cierre es la de la prueba. */
    private fun v2Log(stressByMuscle: Map<String, Double>): WorkoutLog {
        val iso = Instant.ofEpochMilli(origin).toString()
        return WorkoutLog(
            id = "v2-${stressByMuscle.size}", programId = "p", sessionId = "s", sessionName = "Referencia",
            date = iso, durationMinutes = 60,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 30,
                perMuscle = stressByMuscle.mapValues { (_, stress) ->
                    MuscleSessionImpactV2(
                        stressUnits = stress, capacityAtCompletion = capacity, immediateDrainPct = stress / 10.0,
                        directStressUnits = stress, indirectStressUnits = 0.0,
                    )
                },
                involvedVolumeMuscles = stressByMuscle.keys,
                setInputHash = "h", contextHash = "c",
            ),
        )
    }

    /** Batería mostrada de un músculo un tiempo después de la sesión V2, con su τ explícito. */
    private fun muscleScore(stress: Double, hours: Int, tau: Double, muscle: String = "Pectorales"): Int =
        AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = muscle,
            history = listOf(v2Log(mapOf(muscle to stress))),
            wellbeing = null,
            settings = settings,
            adaptiveCache = AugeAdaptiveCache(personalizedRecoveryHours = mapOf(muscle.lowercase() to tau)),
            precomputedCapacity = capacity,
            nowOverride = origin + hours * hourMs,
        ).recoveryScore

    /** La batería REAL (Double) del músculo con la fórmula del motor: 100·exp(−R/90), R = % de fatiga restante. */
    private fun realBattery(stress: Double, hours: Double, tau: Double): Double {
        val k = AugeUtils.TAU_K_95 / tau
        val remaining = stress * exp(-k * AugeUtils.getSigmoidalHours(hours))
        // Con ≤ 0,1 unidades de carga residual el motor fija 100 exacto.
        if (remaining <= 0.1) return 100.0
        val rawPct = remaining / capacity * 100.0
        return 100.0 * exp(-rawPct / 90.0)
    }

    private fun fmt(v: Double): String = "%.4f".format(Locale.ROOT, v)

    private val exerciseDb = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barra",
            efc = 3.2, cnc = 3.5, ssc = 0.35, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
    )

    /** La sesión dura de AugeRingCharacterizationTest (sentadilla + banca), a la hora de origen. */
    private fun hardSquatAndBench(): WorkoutLog {
        val iso = Instant.ofEpochMilli(origin).toString()
        return WorkoutLog(
            id = "hard", programId = "p", sessionId = "s", sessionName = "Dura",
            date = iso, durationMinutes = 70,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso,
                globalMuscularDrain = 28,
                perMuscle = mapOf(
                    "Pectorales" to MuscleSessionImpactV2(
                        stressUnits = 750.0, capacityAtCompletion = 2600.0, immediateDrainPct = 28.0,
                        directStressUnits = 750.0, indirectStressUnits = 0.0,
                    ),
                ),
                involvedVolumeMuscles = setOf("Pectorales"),
                setInputHash = "hard", contextHash = "hard",
            ),
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 180,
                    sets = List(5) { i -> CompletedSet(id = "sq-$i", weight = 140.0, reps = 5, rpe = 8.5) },
                ),
                CompletedExercise(
                    exerciseId = "bench", exerciseName = "Press Banca", exerciseDbId = "bench", restTime = 120,
                    sets = List(4) { i -> CompletedSet(id = "b-$i", weight = 90.0, reps = 6, rpe = 8.5) },
                ),
            ),
        )
    }

    private fun freshPillars(): Map<String, MuscleRecoveryStatus> =
        AugeRecoveryEngine.pillarMuscles.associateWith { muscle ->
            MuscleRecoveryStatus(
                muscleName = muscle, recoveryScore = 100, hoursToRecovery = 0, hoursSinceLastSession = -1,
                effectiveSets = 0, status = RecoveryStatus.FRESH,
            )
        }

    // ─── (a) Meseta del 99 % ───────────────────────────────────────────────

    @Test
    fun mesetaDel99_conTau48_leeCienDesdeLasSetentaYDosHoras() {
        for (hours in 72..168) {
            val real = realBattery(750.0, hours.toDouble(), 48.0)
            assertTrue("La batería real a las $hours h es ${fmt(real)}: debería ser ≥ 99,5", real >= 99.5)
            assertEquals(
                "τ = 48 h a las $hours h (real ${fmt(real)}): debe leer 100, no 99",
                100,
                muscleScore(750.0, hours, tau = 48.0),
            )
        }
    }

    @Test
    fun elMusculoMuestraElEnteroMasCercanoDeSuBateriaReal() {
        val piso = AugeUtils.physiologicalFloor(settings).muscular
        val fallos = mutableListOf<String>()
        for (tau in listOf(24.0, 48.0, 72.0, 96.0)) {
            for (hours in 0..230) {
                val real = realBattery(750.0, hours.toDouble(), tau)
                // Bajo el suelo manda el suelo; en el empate exacto x,5 la réplica y el motor pueden diferir en el último bit.
                if (real < piso + 1 || abs(real - floor(real) - 0.5) < 1e-6) continue
                val mostrado = muscleScore(750.0, hours, tau)
                if (mostrado != real.roundToInt()) {
                    fallos += "τ=$tau h=$hours real=${fmt(real)} mostrado=$mostrado esperado=${real.roundToInt()}"
                }
            }
        }
        assertTrue("El motor trunca en lugar de redondear (${fallos.size}): ${fallos.take(8)}", fallos.isEmpty())
    }

    // ─── (b) Malla de baterías reales ──────────────────────────────────────

    @Test
    fun roundToInt_enMallaDeBateriasReales_dalElEnteroEsperadoYEsMonotono() {
        var previo = -1
        for (n in 0..1000) {
            val real = n / 10.0
            val esperado = (n + 5) / 10 // mitad hacia arriba, con aritmética entera exacta
            val obtenido = real.roundToInt()
            assertEquals("batería real $real", esperado, obtenido)
            assertTrue("no monótono en $real: $obtenido tras $previo", obtenido >= previo)
            previo = obtenido
        }
        assertEquals(100, 99.5.roundToInt())
        assertEquals(99, 99.49.roundToInt())
        assertEquals(1, 0.5.roundToInt())
        assertEquals(0, 0.49.roundToInt())
    }

    @Test
    fun elMusculoRedondeaYEsMonotonoEnUnaMallaDeBateriasReales() {
        val piso = AugeUtils.physiologicalFloor(settings).muscular
        var previo = 101
        var evaluadas = 0
        for (stress in 5..3300 step 5) {
            val real = realBattery(stress.toDouble(), 0.0, 48.0)
            if (real < piso + 1) break
            val mostrado = muscleScore(stress.toDouble(), 0, 48.0)
            if (abs(real - floor(real) - 0.5) >= 1e-6) {
                assertEquals("estrés $stress (real ${fmt(real)})", real.roundToInt(), mostrado)
            }
            assertTrue("no monótono con estrés $stress: $mostrado tras $previo", mostrado <= previo)
            previo = mostrado
            evaluadas++
        }
        assertTrue("La malla evaluó pocas baterías: $evaluadas", evaluadas > 300)
    }

    // ─── (c) Energía y Columna ─────────────────────────────────────────────

    @Test
    fun energiaYColumna_llegan100_cuandoLaCargaResidualEsDespreciable() {
        val dura = hardSquatAndBench()
        fun energia(h: Int) = AugeRecoveryEngine.calculateSystemicFatigue(
            history = listOf(dura), wellbeing = null, settings = settings, exerciseDb = exerciseDb,
            nowOverride = origin + h * hourMs,
        ).first
        fun columna(h: Int) = AugeRecoveryEngine.calculateSpinalBattery(
            history = listOf(dura), wellbeing = null, settings = settings, exerciseDb = exerciseDb,
            nowOverride = origin + h * hourMs,
        )
        assertTrue("Control: recién terminada, Energía debe estar por debajo de 100", energia(0) < 100)
        assertTrue("Control: recién terminada, Columna debe estar por debajo de 100", columna(0) < 100)
        for (hours in listOf(96, 120, 168, 216)) {
            assertEquals("Energía a las $hours h", 100, energia(hours))
            assertEquals("Columna a las $hours h", 100, columna(hours))
        }
    }

    // ─── (d) Anillo muscular global ────────────────────────────────────────

    @Test
    fun elAnilloMuscularGlobalNoBajaDe100ConTodosLosPilaresEn99_6() {
        val pilares = AugeRecoveryEngine.pillarMuscles
        val tau = 48.0
        val hours = 72
        // Estrés que deja a cada pilar en 99,6 % a las 72 h (se despeja de 100·exp(−R/90) = 99,6).
        val rawObjetivoPct = -90.0 * ln(99.6 / 100.0)
        val k = AugeUtils.TAU_K_95 / tau
        val estres = rawObjetivoPct / 100.0 * capacity / exp(-k * AugeUtils.getSigmoidalHours(hours.toDouble()))
        assertEquals("La prueba debe fijar el pilar en 99,6", 99.6, realBattery(estres, hours.toDouble(), tau), 1e-9)

        val historial = listOf(v2Log(pilares.associateWith { estres }))
        val cache = AugeAdaptiveCache(personalizedRecoveryHours = pilares.associate { it.lowercase().trim() to tau })
        val now = origin + hours * hourMs
        val porMusculo = AugeRecoveryEngine.getPerMuscleBatteries(
            history = historial, wellbeing = null, settings = settings, adaptiveCache = cache,
            nowOverrideMs = now, zone = ZoneId.of("UTC"),
        )
        for (pilar in pilares) {
            assertEquals("Pilar $pilar con la batería real en 99,6", 100, porMusculo.getValue(pilar).recoveryScore)
        }
        val globales = AugeRecoveryEngine.calculateGlobalBatteries(
            history = historial, wellbeing = null, settings = settings, adaptiveCache = cache,
            precomputedMuscles = porMusculo, nowOverrideMs = now,
        )
        assertEquals("Anillo muscular con todos los pilares en 99,6", 100, globales.muscular)
        val panel = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = globales, perMuscle = porMusculo, articularBatteries = emptyMap(), wellbeing = null,
        )
        assertEquals(100, panel.channels.first { it.id == RecoveryChannelId.MUSCULAR }.score)
    }

    @Test
    fun losTresAnillosGlobalesRedondeanLaCorreccionDelAprendizaje() {
        // Todo recuperado y una corrección de −0,4: el valor final es 99,6 y debe leerse 100 en los tres anillos.
        val cache = AugeAdaptiveCache(
            muscleDeltas = mapOf("pectorales" to -0.4),
            cnsLearningDelta = -0.4,
            spinalLearningDelta = -0.4,
        )
        val globales = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(), wellbeing = null, settings = settings, adaptiveCache = cache,
            precomputedMuscles = freshPillars(), nowOverrideMs = origin,
        )
        assertEquals("Músculos", 100, globales.muscular)
        assertEquals("Energía", 100, globales.cnc)
        assertEquals("Columna", 100, globales.spinal)
    }

    // ─── Piso articular ────────────────────────────────────────────────────

    @Test
    fun elPisoArticularRedondeaElPromedioDeLasDosMasBajas() {
        // Las dos más bajas son 80 y 81: promedio 80,5 ⇒ 81 (antes 80).
        val articulares = mapOf(
            ArticularBattery.KNEE to ArticularBatteryState(recoveryScore = 80),
            ArticularBattery.LUMBAR to ArticularBatteryState(recoveryScore = 81),
            ArticularBattery.SHOULDER to ArticularBatteryState(recoveryScore = 95),
        )
        assertEquals(81, AugeRecoveryEngine.structureInputs(emptyMap(), articulares).first)

        // Columna = min(espinal, 0,5·espinal + 0,25·piso + 0,25·guarda): 45 + 20,25 + 24,25 = 89,5 ⇒ 90 (con 80 daba 89,25 ⇒ 89).
        val guardas = mapOf(
            "Erectores Espinales" to 97, "Dorsales" to 100, "Trapecio" to 100,
        ).mapValues { (nombre, puntos) ->
            MuscleRecoveryStatus(
                muscleName = nombre, recoveryScore = puntos, hoursToRecovery = 0, hoursSinceLastSession = -1,
                effectiveSets = 0, status = RecoveryStatus.FRESH,
            )
        }
        val panel = AugeRecoveryEngine.calculateRecoveryDashboard(
            batteries = GlobalBatteries(muscular = 100, cnc = 100, spinal = 90),
            perMuscle = guardas, articularBatteries = articulares, wellbeing = null,
        )
        assertEquals(90, panel.channels.first { it.id == RecoveryChannelId.STRUCTURE }.score)
    }

    // ─── (e) Paridad en pantalla ───────────────────────────────────────────

    @Test
    fun elPorcentajeDelAnilloRedondeaElFloatYNoLoTrunca() {
        assertEquals("0,53f · 100 = 52,999996", 53, ringPercent(batteryInt = null, progress = 0.53f))
        assertEquals("0,59f · 100 = 58,999996", 59, ringPercent(batteryInt = null, progress = 0.59f))
        for (n in 0..100) {
            assertEquals("Float de $n/100", n, ringPercent(batteryInt = null, progress = n / 100f))
        }
    }

    @Test
    fun elPorcentajeDelAnilloUsaLaBateriaEnteraSiExiste() {
        // Con el entero a mano no se pasa por el Float, aunque el progreso quede un pelo por debajo.
        assertEquals(53, ringPercent(batteryInt = 53, progress = 0.5299f))
        assertEquals(100, ringPercent(batteryInt = 140, progress = 0f))
        assertEquals(0, ringPercent(batteryInt = -3, progress = 1f))
        assertEquals(0, ringPercent(batteryInt = null, progress = -0.2f))
        assertEquals(100, ringPercent(batteryInt = null, progress = 1.7f))
    }

    @Test
    fun elPromedioDeRecuperacionDeLaSesionRedondea() {
        assertEquals("99,5 ⇒ 100", 100, sessionRecoveryPercent(listOf(99, 100)))
        assertEquals("98,5 ⇒ 99", 99, sessionRecoveryPercent(listOf(98, 99)))
        assertEquals(53, sessionRecoveryPercent(listOf(53)))
        assertNull("Sin músculos no hay promedio", sessionRecoveryPercent(emptyList()))
    }
}
