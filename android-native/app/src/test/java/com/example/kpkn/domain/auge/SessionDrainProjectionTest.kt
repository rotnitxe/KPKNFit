package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MesocycleStressEMA
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.StressTrend
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.testsupport.MotCatalog
import com.example.kpkn.testsupport.MotRecipeLine
import com.example.kpkn.testsupport.MotSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · I1/I2/I4: la proyección de coste compartida (AUD1 §1). Fija el contrato entero de siempre, los decimales, la diferencia entre
 * cero, «<1» y «sin estimación», el cardio dentro del total y el ajuste por historial sin truncar antes de ajustar.
 */
class SessionDrainProjectionTest {
    private val index = MotCatalog.index
    private val enthusiast = Settings(athleteType = AthleteType.ENTHUSIAST)
    private val powerlifter = Settings(athleteType = AthleteType.POWERLIFTER)

    private fun project(session: Session, settings: Settings = enthusiast) =
        AugeFatigueEngine.projectPlannedSession(session, index, settings)

    @Test
    fun caseA_smallSessionKeepsItsSpinalCostAsLessThanOne() {
        // AUD1 §1.2 caso A: una serie de cada uno, perfil POWERLIFTER. El motor general lo truncaba a 0: ahora se lee «<1».
        val session = MotSessions.squatAndDeadlift(setsEach = 1)
        val projection = project(session, powerlifter)
        assertTrue("spinal=${projection.precise.spinal}", projection.precise.spinal > 0.0 && projection.precise.spinal < 1.0)
        assertEquals(0, projection.global.spinal)
        assertTrue(projection.spinalReading is DrainReading.UnderOne)
        assertEquals("<1", projection.spinalReading.label)
        // Músculos y Energía siguen siendo valores normales (AUD1: 11 y 4).
        assertTrue("muscular=${projection.precise.muscular}", projection.precise.muscular in 10.0..12.0)
        assertTrue("cns=${projection.precise.cns}", projection.precise.cns in 3.0..5.0)
    }

    @Test
    fun measurement_projectionValues_forTheReport() {
        fun line(label: String, p: com.example.kpkn.domain.auge.SessionDrainProjection) = println(
            "MOT-I medición $label · M/E/C = %.2f / %.2f / %.2f · enteros ${p.global.muscular}/${p.global.cns}/${p.global.spinal} · Columna «${p.spinalReading.label}»"
                .format(p.precise.muscular, p.precise.cns, p.precise.spinal),
        )
        line("caso A (POWERLIFTER, 1 serie de cada)", project(MotSessions.squatAndDeadlift(setsEach = 1), powerlifter))
        line("caso B (ENTHUSIAST, 3 series de cada)", project(MotSessions.squatAndDeadlift(setsEach = 3)))
        line("S12 cardio Z2", project(Session(id = "z2", name = "Z2", exercises = listOf(MotSessions.cardioZ2()))))
        val strength = MotSessions.squatAndDeadlift(setsEach = 3)
        line("sentadilla + peso muerto + Z2", project(strength.copy(exercises = strength.exercises + MotSessions.cardioZ2())))
    }

    @Test
    fun caseB_keepsTheGeneralEngineContract_32_17_8() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        val projection = project(session)
        assertEquals(PredictedDrain(cns = 17, muscular = 32, spinal = 8), projection.global)
        assertEquals(projection.global, AugeFatigueEngine.calculateAdjustedPredictedDrain(session, index, enthusiast))
        // El mínimo de sesión dura (≥ 6 series a RPE ≥ 8) sube Columna a 8 aunque lo acumulado sea ≈ 4.
        assertTrue("accumulated=${projection.accumulated.spinal}", projection.accumulated.spinal < projection.precise.spinal)
        assertEquals(8.0, projection.precise.spinal, 1e-9)
    }

    @Test
    fun benchDeclaresZeroAxial_readsAsZero_notUnknown() {
        val session = MotSessions.session(
            "bench-only", "Banca",
            listOf(MotRecipeLine("bench_press__barbell", 4, 5, 8.0, 180, 100.0)),
        )
        val projection = project(session)
        assertEquals(SpinalEvidence.DECLARED, projection.spinalEvidence)
        assertEquals(DrainReading.Zero, projection.spinalReading)
        assertEquals("0", projection.spinalReading.label)
        assertTrue(projection.muscularReading is DrainReading.Value)
    }

    @Test
    fun customExerciseWithoutAxial_isUnknown_andKeepsTheHeuristicNumberInternally() {
        val custom = ExerciseMuscleInfo(
            id = "custom:abc",
            name = "Bisagra propia",
            equipment = "Barra",
            isCustom = true,
            involvedMuscles = listOf(InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, 1.0)),
        )
        val exercise = Exercise(
            id = "e1",
            name = custom.name,
            exerciseDbId = custom.id,
            restTime = 150,
            sets = List(4) { ExerciseSet(id = "s$it", targetReps = 5, targetRPE = 8.0, weight = 100.0) },
        )
        val session = Session(id = "c", name = "Propio", exercises = listOf(exercise))
        val projection = AugeFatigueEngine.projectPlannedSession(session, mapOf(custom.id to custom), enthusiast)
        assertEquals(SpinalEvidence.NONE, projection.spinalEvidence)
        assertEquals(listOf("e1"), projection.axialUnknownExerciseIds)
        assertEquals(DrainReading.Unknown, projection.spinalReading)
        assertEquals("Sin estimación", projection.spinalReading.label)
        // El número heurístico sigue existiendo para los consumidores internos: null y 0 no significan lo mismo.
        assertTrue("spinal=${projection.precise.spinal}", projection.precise.spinal > 0.0)
        assertTrue(projection.muscularReading !is DrainReading.Unknown)
    }

    @Test
    fun knownAndUnknownAxialMixed_isPartial() {
        val custom = ExerciseMuscleInfo(
            id = "custom:abc", name = "Propio", equipment = "Barra", isCustom = true,
            involvedMuscles = listOf(InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, 1.0)),
        )
        val squat = MotSessions.exercise(MotRecipeLine("high_bar_back_squat__barbell", 3, 5, 8.0, 180, 100.0), 1)
        val own = Exercise(id = "own", name = "Propio", exerciseDbId = custom.id, sets = List(2) { ExerciseSet(id = "o$it", targetReps = 8, targetRPE = 8.0) })
        val projection = AugeFatigueEngine.projectPlannedSession(
            Session(id = "m", name = "Mixta", exercises = listOf(squat, own)),
            index + (custom.id to custom),
            enthusiast,
        )
        assertEquals(SpinalEvidence.PARTIAL, projection.spinalEvidence)
        assertTrue(projection.spinalReading !is DrainReading.Unknown)
    }

    @Test
    fun cardioZ2_isInsideTheTotals_notTheFlatFallback() {
        val session = Session(id = "z2", name = "Z2", exercises = listOf(MotSessions.cardioZ2()))
        val projection = project(session)
        assertTrue(projection.hasWork)
        assertTrue("muscular=${projection.precise.muscular}", projection.precise.muscular in 9.0..11.5)
        assertTrue("cns=${projection.precise.cns}", projection.precise.cns in 0.9..1.8)
        assertTrue("spinal=${projection.precise.spinal}", projection.precise.spinal in 0.5..1.6)
        assertEquals(listOf(true), projection.perExercise.map { it.isCardio })
        assertTrue(projection.perExercise.single().cardioMuscleDrains.isNotEmpty())
        assertFalse(projection.global == AugeFatigueEngine.DEFAULT_FALLBACK_PREDICTED_DRAIN)
    }

    @Test
    fun emptySession_readsZeroEverywhere() {
        val projection = project(Session(id = "e", name = "Vacía"))
        assertFalse(projection.hasWork)
        assertEquals(DrainReading.Zero, projection.muscularReading)
        assertEquals(DrainReading.Zero, projection.cnsReading)
        assertEquals(DrainReading.Zero, projection.spinalReading)
    }

    @Test
    fun readingFormatting_distinguishesZeroUnderOneUnknownAndValues() {
        assertEquals("Sin estimación", DrainReading.of(null).label)
        assertEquals("0", DrainReading.of(0.0).label)
        assertEquals("<1", DrainReading.of(0.0001).label)
        assertEquals("<1", DrainReading.of(0.99).label)
        assertEquals("1", DrainReading.of(1.0).label)
        assertEquals("13", DrainReading.of(12.6).label)
        assertEquals("100", DrainReading.of(140.0).label)
        assertEquals(null, DrainReading.of(null).ringValue)
        assertEquals(0, DrainReading.of(0.0).ringValue)
        assertEquals(1, DrainReading.of(0.3).ringValue)
    }

    @Test
    fun emaDoesNotTurnAPositiveEstimateIntoZero() {
        val ema = MesocycleStressEMA(
            programId = "p", mesoIndex = 0, emaValue = 70.0, sessionCount = 5,
            latestStressScore = 70.0, stressTrend = StressTrend.STABLE, computedAtMs = 0L,
        )
        // El ajuste por historial baja el coste ≈ 3 %: con enteros, 1 pasaba a 0; con decimales, sigue siendo positivo.
        val asIntegers = AugeFatigueEngine.adjustPredictedDrainWithEMA(PredictedDrain(cns = 1, muscular = 1, spinal = 1), ema)
        assertEquals(PredictedDrain(0, 0, 0), asIntegers)
        val precise = AugeFatigueEngine.adjustPreciseDrainWithEMA(PreciseDrain(cns = 1.0, muscular = 1.0, spinal = 1.0), ema)
        assertTrue(precise.cns > 0.9 && precise.cns < 1.0)
        assertTrue(precise.spinal > 0.9 && precise.spinal < 1.0)
        assertTrue(DrainReading.of(precise.spinal) is DrainReading.UnderOne)
    }

    @Test
    fun projectionWithEma_isTheEmaOfThePreciseValue_once() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        val ema = MesocycleStressEMA(
            programId = "p", mesoIndex = 0, emaValue = 20.0, sessionCount = 3,
            latestStressScore = 20.0, stressTrend = StressTrend.STABLE, computedAtMs = 0L,
        )
        val plain = project(session)
        val adjusted = AugeFatigueEngine.projectPlannedSession(session, index, enthusiast, mesocycleEma = ema)
        val expected = AugeFatigueEngine.adjustPreciseDrainWithEMA(plain.precise, ema)
        assertEquals(expected.muscular, adjusted.precise.muscular, 1e-9)
        assertEquals(expected.spinal, adjusted.precise.spinal, 1e-9)
        assertEquals(expected.truncated(), adjusted.global)
    }

    @Test
    fun perExerciseContributions_addUpToTheAccumulatedTotals() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        val projection = project(session)
        // Por debajo del tope blando, lo que entró en los acumuladores es la suma de los aportes por ejercicio.
        assertEquals(projection.accumulated.muscular, projection.perExercise.sumOf { it.cappedMuscular }, 1e-6)
        assertEquals(projection.accumulated.cns, projection.perExercise.sumOf { it.cappedCns }, 1e-6)
        assertEquals(2, projection.perExercise.size)
        assertTrue(projection.perExercise.all { it.setCount == 3 })
    }

    @Test
    fun emptySlotsAndIneffectiveSetsAreNotWork() {
        val base = MotSessions.squatAndDeadlift(setsEach = 3)
        val noisy = base.copy(
            exercises = base.exercises.map { ex ->
                ex.copy(sets = ex.sets + ex.sets.first().copy(id = "${ex.id}-hole", isEmptySlot = true) + ex.sets.first().copy(id = "${ex.id}-x", isIneffective = true))
            },
        )
        assertEquals(project(base).precise, project(noisy).precise)
    }

    @Test
    fun percentRmSetsUseTheSuggestedLoadLikeTheEditorDid() {
        val repsMode = Session(
            id = "reps", name = "Reps",
            exercises = listOf(
                Exercise(id = "sq", name = "Sentadilla", catalogConfigurationId = "high_bar_back_squat__barbell", restTime = 180,
                    sets = List(3) { ExerciseSet(id = "s$it", targetReps = 5, targetRPE = 8.0, weight = 80.0) }),
            ),
        )
        val rmMode = Session(
            id = "rm", name = "RM",
            exercises = listOf(
                Exercise(id = "sq", name = "Sentadilla", catalogConfigurationId = "high_bar_back_squat__barbell", restTime = 180,
                    trainingMode = TrainingMode.RM, reference1RM = 100.0,
                    sets = List(3) { ExerciseSet(id = "s$it", targetReps = 5, targetRPE = 8.0, targetPercentageRM = 80.0) }),
            ),
        )
        assertEquals(project(repsMode).precise.muscular, project(rmMode).precise.muscular, 0.5)
    }
}
