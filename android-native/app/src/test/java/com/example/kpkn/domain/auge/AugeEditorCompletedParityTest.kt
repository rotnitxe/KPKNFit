package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.sessioneditor.computeSessionAugeComputation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class AugeEditorCompletedParityTest {
    private lateinit var previousZone: TimeZone

    @Before fun fijarZona() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restaurarZona() { TimeZone.setDefault(previousZone) }

    @Test
    fun equivalentPlanAndCompletedExecution_matchGlobalAndLocalImpact() {
        val planned = Session(
            id = "golden-session",
            name = "golden",
            exercises = AugeRealSessionFixtures.completedExercises.map { completed ->
                Exercise(
                    id = completed.exerciseId,
                    name = completed.exerciseName,
                    exerciseDbId = completed.exerciseDbId,
                    restTime = completed.restTime,
                    effectiveMuscles = AugeRealSessionFixtures.exerciseDb[completed.exerciseDbId]?.involvedMuscles,
                    sets = completed.sets.map { set ->
                        ExerciseSet(
                            id = set.id,
                            targetReps = set.reps,
                            targetRPE = set.rpe,
                            targetRIR = set.rir,
                            weight = set.weight,
                            isFailure = set.isFailure,
                            intensityMode = set.actualIntensityMode,
                        )
                    },
                )
            },
        )
        val iso = "2026-08-23T17:25:00-04:00"
        val plannedImpact = MuscularSessionImpactEngine.evaluate(
            MuscularSessionImpactEngine.fromPlannedSession(planned, iso, AugeRealSessionFixtures.exerciseDb, Settings()),
            AugeRealSessionFixtures.exerciseDb,
            Settings(),
        )
        val completedImpact = MuscularSessionImpactEngine.evaluate(
            MuscularSessionImpactEngine.fromCompletedExercises(AugeRealSessionFixtures.completedExercises, iso, AugeRealSessionFixtures.exerciseDb, Settings()),
            AugeRealSessionFixtures.exerciseDb,
            Settings(),
        )
        assertEquals(plannedImpact.globalMuscularDrain, completedImpact.globalMuscularDrain)
        assertEquals(plannedImpact.involvedVolumeMuscles, completedImpact.involvedVolumeMuscles)
        plannedImpact.involvedVolumeMuscles.forEach { muscle ->
            assertEquals(
                plannedImpact.perMuscle.getValue(muscle).immediateDrainPct,
                completedImpact.perMuscle.getValue(muscle).immediateDrainPct,
                0.0,
            )
        }
    }

    private val f = SessionDrainCharacterizationFixtures

    private fun strength(id: String, reps: Int = 8, rpe: Double = 8.5, rest: Int = 90) = CompletedExercise(
        exerciseId = id, exerciseName = f.db[id]?.name ?: "Movimiento propio",
        exerciseDbId = id.takeIf { it in f.db }, restTime = rest,
        sets = List(3) { CompletedSet("$id-$it", weight = 80.0, reps = reps, rpe = rpe) },
    )

    private fun assertParity(
        completed: List<CompletedExercise>,
        minimumMinutes: Int? = null,
        missingPlannedRest: Boolean = false,
        materializeCardioPart: Boolean = false,
    ) {
        val planned = Session("parity", "Paridad", exercises = completed.map { ex ->
            Exercise(
                id = ex.exerciseId, name = ex.exerciseName, exerciseDbId = ex.exerciseDbId,
                effectiveMuscles = ex.effectiveMuscles, restTime = ex.restTime.takeUnless { missingPlannedRest },
                supersetGroupRef = ex.supersetId, cardioDetails = ex.cardioDetails,
                targetDurationMinutes = minimumMinutes,
                sets = ex.sets.map { set ->
                    ExerciseSet(
                        set.id, weight = set.weight, targetReps = set.reps, targetRPE = set.rpe,
                        targetRIR = set.rir, intensityMode = set.actualIntensityMode, isFailure = set.isFailure,
                    )
                },
            )
        })
        val session = if (materializeCardioPart) planned.copy(
            exercises = planned.exercises.filter { it.cardioDetails == null },
            parts = listOf(SessionPart("cardio-part", "Cardio", isCardioGroup = true,
                exercises = planned.exercises.filter { it.cardioDetails != null })),
            cardioFirst = true,
        ) else planned
        val editor = computeSessionAugeComputation(session, f.db, f.settings, emptyList(), "p", 0)
        val predicted = AugeFatigueEngine.calculateAdjustedPredictedDrain(session, f.db, f.settings)
        val finished = AugeFatigueEngine.calculateCompletedSessionDrain(completed, f.db, f.settings, durationMinutes = editor.durationMinutes)
        assertEquals("Editor y predictor", predicted, editor.drain)
        assertEquals("Predictor y cierre", finished, predicted)
        if (minimumMinutes != null) assertTrue(editor.durationMinutes >= minimumMinutes)
    }

    @Test fun fuerzaConSnapshotCompartidoTieneParidadExacta() {
        assertParity(listOf(strength("bench"), strength("row").copy(
            effectiveMuscles = listOf(InvolvedMuscle("Tríceps", MuscleRole.PRIMARY, 1.0)),
        )))
    }

    @Test fun cardioEnElGlobalTieneParidadExacta() {
        val cardio = CardioDetails(CardioType.ROW_MACHINE, targetDurationSeconds = 900, intensityLevel = 8)
        assertParity(listOf(CompletedExercise("row-cardio", "Remo cardio", cardioDetails = cardio,
            sets = listOf(CompletedSet("cardio", timeSeconds = 900, rpe = 8.0)))))
    }

    @Test fun cardioEntreEjerciciosActualizaElAcumuladoConParidadExacta() {
        val cardio = CardioDetails(CardioType.ROW_MACHINE, targetDurationSeconds = 900, intensityLevel = 8)
        assertParity(listOf(strength("bench"), CompletedExercise("cardio", "Remo cardio", cardioDetails = cardio,
            sets = listOf(CompletedSet("cardio", timeSeconds = 900, rpe = 8.0))), strength("squat")))
    }

    @Test fun personalizadoSinFichaTieneParidadExacta() = assertParity(listOf(strength("custom")))

    @Test fun superserieConDensidadEquivalenteTieneParidadExacta() {
        assertParity(listOf(strength("bench", rest = 45), strength("row", rest = 45)).map {
            it.copy(supersetId = "ss", supersetExerciseCount = 2, supersetRestBetween = 45)
        })
    }

    @Test fun singlesPesadosTienenParidadExacta() = assertParity(listOf(strength("deadlift", 1, 9.5)))

    @Test fun duracionMayorA75MinutosTieneParidadExacta() =
        assertParity(listOf(strength("bench")), minimumMinutes = 80)

    @Test fun duracionMayorA90MinutosTieneParidadExacta() =
        assertParity(listOf(strength("deadlift", 1, 9.5)), minimumMinutes = 96)

    @Test fun descansoPredeterminadoDelUsuarioTieneParidadExacta() =
        assertParity(listOf(strength("bench", rest = f.settings.restTimerDefaultSeconds)), missingPlannedRest = true)

    @Test fun minimoDeSesionDuraLeeElModoRirEfectivo() {
        val ex = strength("row").copy(sets = List(6) {
            CompletedSet("rir-$it", weight = 80.0, reps = 1, rpe = 7.0, rir = 0,
                actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 0.0)
        })
        assertParity(listOf(ex))
    }

    @Test fun cardioPrimeroEnUnaParteRespetaElMismoOrdenAlCerrar() {
        val cardio = CardioDetails(CardioType.ROW_MACHINE, targetDurationSeconds = 900, intensityLevel = 8)
        assertParity(listOf(CompletedExercise("cardio", "Remo cardio", cardioDetails = cardio,
            sets = listOf(CompletedSet("cardio", timeSeconds = 900, rpe = 8.0))), strength("bench"), strength("squat")),
            materializeCardioPart = true)
    }
}
