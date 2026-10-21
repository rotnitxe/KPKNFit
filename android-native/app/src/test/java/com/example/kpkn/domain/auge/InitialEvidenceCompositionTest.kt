package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Equivalencia con historiales sintéticos reales; τ por defecto, sin deltas, nutrición ni carga articular. */
class InitialEvidenceCompositionTest {
    private val zone = ZoneId.of("UTC")
    private val t0 = Instant.parse("2026-10-01T22:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val realTime = t0 + 4 * hour

    companion object {
        internal fun evidence(
            time: Long,
            muscular: Map<String, Double>,
            energy: Double = 0.0,
            spinal: Double = 0.0,
        ) = InitialRecoveryEvidence(
            capturedAtMs = time, coveredFromMs = time, coveredToMs = time,
            expiresAtMs = time + 14L * 24 * 3_600_000L, sessions = 1,
            activityType = InitialRecoveryActivityType.STRENGTH, intensity = InitialRecoveryIntensity.HARD,
            muscularScore = 100, systemScore = 100, structureScore = 100, confidence = 50,
            estimatorVersion = 2, dayLoads = listOf(InitialRecoveryDayLoad(time, muscular, energy, spinal)),
        )
    }

    private fun log(time: Long, loads: Map<String, Double>, exercises: List<CompletedExercise> = emptyList()): WorkoutLog {
        val iso = Instant.ofEpochMilli(time).toString()
        val capacity = 1200.0
        return WorkoutLog("pseudo-" + time, "programa", "sesión", "Sesión sintética", iso, 60,
            completedExercises = exercises,
            muscularImpactV2 = MuscularSessionImpactV2(
                completionInstantIso = iso, globalMuscularDrain = 0,
                perMuscle = loads.mapValues { (_, rawPct) ->
                    val stress = capacity * rawPct / 100.0
                    MuscleSessionImpactV2(stress, capacity, 100.0 * (1.0 - exp(-stress / capacity)), stress, 0.0)
                },
                involvedVolumeMuscles = loads.filterValues { it > 0.0 }.keys,
                setInputHash = "sintético", contextHash = "sin-calibración",
            ),
        )
    }

    @Test fun initialPillarLoadsEqualAddingTheirEquivalentSyntheticSessionToHistory() {
        // Pseudo-sesión de Pectorales al 60 % en t0: R = −90·ln(0,6).
        // La sesión real ocurre cuatro horas después, al día siguiente; no suprime la evidencia de ayer.
        val raw = -AugeParameters.C_MUSCLE * ln(0.6)
        val loads = mapOf("Pectorales" to raw)
        val pseudo = log(t0, loads)
        val real = log(realTime, loads)
        val initial = evidence(t0, loads)
        for (h in listOf(4, 12, 24, 48)) {
            val now = t0 + h * hour
            fun batteries(history: List<WorkoutLog>, e: InitialRecoveryEvidence?) = AugeRecoveryEngine.getPerMuscleBatteries(
                history, null, Settings(initialRecoveryEvidence = e), nowOverrideMs = now, zone = zone,
            )
            val composed = batteries(listOf(real), initial).getValue("Pectorales")
            val withPseudo = batteries(listOf(pseudo, real), null).getValue("Pectorales")
            assertTrue("Pectorales @" + h + ": " + composed.recoveryScore + "/" + withPseudo.recoveryScore,
                abs(composed.recoveryScore - withPseudo.recoveryScore) <= 1)
            val actualOnly = batteries(listOf(real), null).getValue("Pectorales").recoveryScore
            val initialOnly = InitialRecoveryEvidencePolicy.v2PillarBatteries(
                InitialRecoveryPolicyInput(initial, now, zone = zone), listOf("Pectorales"),
            ).getValue("Pectorales")
            println("2.5 músculo @" + h + "h antes=" + (actualOnly - (100 - initialOnly)) +
                " después=" + composed.recoveryScore + " pseudo=" + withPseudo.recoveryScore)
        }
    }

    @Test fun initialEnergyAndSpinalLoadsEqualTheirEquivalentSyntheticSessionInTheEngine() {
        val exercise = CompletedExercise(
            exerciseId = "sentadilla", exerciseName = "Sentadilla", exerciseDbId = "sentadilla", restTime = 180,
            sets = List(6) { i -> CompletedSet("serie-" + i, weight = 120.0, reps = 6, rpe = 8.5) },
        )
        val db = mapOf("sentadilla" to ExerciseMuscleInfo(
            id = "sentadilla", name = "Sentadilla", equipment = "barra", efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0)),
        ))
        // V2 muscular nulo: los guardianes permanecen al 100; así se aísla la composición de cargas sistémicas.
        val noMuscleLoad = AugeParameters.PILLARS.associateWith { 0.0 }
        val pseudo = log(t0, noMuscleLoad, listOf(exercise))
        val real = log(realTime, noMuscleLoad, listOf(exercise))
        fun batteries(history: List<WorkoutLog>, e: InitialRecoveryEvidence?, now: Long) = AugeRecoveryEngine.calculateGlobalBatteries(
            history, null, Settings(initialRecoveryEvidence = e), exerciseDb = db, nowOverrideMs = now, zone = zone,
        )
        // Medimos una pseudo-sesión aislada con el motor y convertimos su batería al R equivalente.
        // La única imprecisión de entrada es el redondeo a entero; el contrato permite un punto.
        val isolated = batteries(listOf(pseudo), null, t0)
        assertTrue(isolated.cnc < 95 && isolated.spinal < 100)
        val initial = evidence(t0, emptyMap(), -AugeParameters.C_ENERGY * ln(isolated.cnc / 100.0),
            -AugeParameters.C_SPINAL * ln(isolated.spinal / 100.0))
        for (h in listOf(4, 12, 24, 48)) {
            val now = t0 + h * hour
            val composed = batteries(listOf(real), initial, now)
            val withPseudo = batteries(listOf(pseudo, real), null, now)
            assertTrue("Energía @" + h + ": " + composed.cnc + "/" + withPseudo.cnc,
                abs(composed.cnc - withPseudo.cnc) <= 1)
            assertTrue("Columna @" + h + ": " + composed.spinal + "/" + withPseudo.spinal,
                abs(composed.spinal - withPseudo.spinal) <= 1)
            val onlyReal = batteries(listOf(real), null, now)
            val contribution = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(initial, now, listOf(real), zone = zone))
            println("2.5 energía/columna @" + h + "h antes=" + (onlyReal.cnc - (100 - requireNotNull(contribution.system))) +
                "/" + (onlyReal.spinal - (100 - requireNotNull(contribution.structure))) + " después=" + composed.cnc + "/" + composed.spinal +
                " pseudo=" + withPseudo.cnc + "/" + withPseudo.spinal)
        }
    }

    @Test fun uniformInitialMuscleLoadsEqualAddingEquivalentSessionsInBothGlobalPaths() {
        // En el instante de la sesión real todos los pilares reales están al 60 %.
        // El agregado es homogéneo y permite contrastar también la composición global directa.
        val loads = AugeParameters.PILLARS.associateWith { -AugeParameters.C_MUSCLE * ln(0.6) }
        val pseudo = log(t0, loads)
        val real = log(realTime, loads)
        val initial = evidence(t0, loads)
        fun direct(history: List<WorkoutLog>, e: InitialRecoveryEvidence?) = AugeRecoveryEngine.calculateGlobalBatteries(
            history, null, Settings(initialRecoveryEvidence = e), nowOverrideMs = realTime, zone = zone,
        ).muscular
        fun snapshot(history: List<WorkoutLog>, e: InitialRecoveryEvidence?) = AugeSnapshotComputer.compute(
            AugeSnapshotInputs(history, null, Settings(initialRecoveryEvidence = e), emptyMap(), realTime, zone),
        ).batteries.muscular
        val composed = direct(listOf(real), initial)
        val withPseudo = direct(listOf(pseudo, real), null)
        assertTrue("Global directo: " + composed + "/" + withPseudo, abs(composed - withPseudo) <= 1)
        val composedSnapshot = snapshot(listOf(real), initial)
        val pseudoSnapshot = snapshot(listOf(pseudo, real), null)
        assertTrue("Global de Home: " + composedSnapshot + "/" + pseudoSnapshot, abs(composedSnapshot - pseudoSnapshot) <= 1)
        println("2.5 global muscular directo=" + composed + " pseudo=" + withPseudo +
            " Home=" + composedSnapshot + " pseudoHome=" + pseudoSnapshot)
    }

    @Test fun zeroInitialBatteryKeepsThePhysiologicalFloors() {
        val initial = evidence(t0, emptyMap()).copy(estimatorVersion = 1, muscularScore = 0, systemScore = 0, structureScore = 0)
        val result = AugeRecoveryEngine.calculateGlobalBatteries(emptyList(), null, Settings(initialRecoveryEvidence = initial),
            nowOverrideMs = t0, zone = zone)
        val floor = AugeUtils.physiologicalFloor(Settings())
        assertEquals(floor.muscular, result.muscular)
        assertEquals(floor.cns, result.cnc)
        assertEquals(floor.spinal, result.spinal)
    }
}
