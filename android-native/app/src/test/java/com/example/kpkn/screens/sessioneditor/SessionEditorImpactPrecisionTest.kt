package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.DrainReading
import com.example.kpkn.domain.auge.SpinalEvidence
import com.example.kpkn.screens.sessioneditor.components.sheets.columnAxialNote
import com.example.kpkn.testsupport.MotCatalog
import com.example.kpkn.testsupport.MotSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · I2/I3/I4 en el editor: Impacto lee la proyección compartida (decimales, «<1», «sin estimación»), el reparto por músculo suma
 * el total final y las claves de la caché notan un cambio de catálogo o de historial.
 */
class SessionEditorImpactPrecisionTest {
    private val index = MotCatalog.index
    private val enthusiast = Settings(athleteType = AthleteType.ENTHUSIAST)

    private fun compute(session: Session, settings: Settings = enthusiast, index: Map<String, ExerciseMuscleInfo> = this.index) =
        computeSessionAugeComputation(session, index, settings, emptyList(), "p", 0)

    @Test
    fun smallSession_showsSpinalAsUnderOne_inTheEditor() {
        val computation = compute(MotSessions.squatAndDeadlift(setsEach = 1), Settings(athleteType = AthleteType.POWERLIFTER))
        assertEquals(0, computation.drain.spinal)
        assertTrue(computation.projection.spinalReading is DrainReading.UnderOne)
        // El reparto por músculo conserva el decimal: ningún músculo queda en «0» cuando la sesión aporta Columna.
        assertTrue(computation.muscleDrainPrecise.values.any { it.spinal > 0.0 && it.spinal < 1.0 })
    }

    @Test
    fun muscleDistributionAddsUpToTheFinalTotals() {
        val computation = compute(MotSessions.squatAndDeadlift(setsEach = 3))
        val precise = computation.projection.precise
        assertEquals(precise.muscular, computation.muscleDrainPrecise.values.sumOf { it.muscular }, 1e-6)
        assertEquals(precise.cns, computation.muscleDrainPrecise.values.sumOf { it.cns }, 1e-6)
        // Con el mínimo de sesión dura (Columna 8), el reparto se ajusta al total final sobre los músculos con aporte conocido.
        assertEquals(precise.spinal, computation.muscleDrainPrecise.values.sumOf { it.spinal }, 1e-6)
        assertTrue(computation.muscleDrainPrecise.keys.isNotEmpty())
    }

    @Test
    fun distributeToTotal_neverInventsMusclesToFitATotal() {
        assertEquals(emptyMap<String, Double>(), distributeToTotal(emptyMap(), 8.0))
        assertEquals(emptyMap<String, Double>(), distributeToTotal(mapOf("A" to 0.0), 8.0))
        assertEquals(emptyMap<String, Double>(), distributeToTotal(mapOf("A" to 2.0), 0.0))
        val spread = distributeToTotal(mapOf("A" to 1.0, "B" to 3.0), 8.0)
        assertEquals(2.0, spread.getValue("A"), 1e-9)
        assertEquals(6.0, spread.getValue("B"), 1e-9)
    }

    @Test
    fun customExerciseWithoutAxial_showsNoSpinalEstimate_andExplainsWhich() {
        val custom = ExerciseMuscleInfo(
            id = "custom:abc", name = "Bisagra propia", equipment = "Barra", isCustom = true,
            involvedMuscles = listOf(InvolvedMuscle("Isquiosurales", MuscleRole.PRIMARY, 1.0)),
        )
        val exercise = Exercise(
            id = "e1", name = custom.name, exerciseDbId = custom.id, restTime = 150,
            sets = List(4) { ExerciseSet(id = "s$it", targetReps = 5, targetRPE = 8.0, weight = 100.0) },
        )
        val session = Session(id = "c", name = "Propio", exercises = listOf(exercise))
        val computation = compute(session, index = mapOf(custom.id to custom))
        assertEquals(SpinalEvidence.NONE, computation.projection.spinalEvidence)
        assertEquals(DrainReading.Unknown, computation.projection.spinalReading)
        val note = columnAxialNote(computation.projection, session)
        assertNotNull(note)
        assertTrue(note!!, note.contains("Bisagra propia"))
        assertTrue(note, note.contains("sin estimación", ignoreCase = true))
    }

    @Test
    fun declaredAxial_hasNoNote() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        assertNull(columnAxialNote(compute(session).projection, session))
    }

    @Test
    fun cardioOnlySession_hasItsOwnCostInTheEditor() {
        val session = Session(id = "cardio", name = "Cardio", exercises = listOf(MotSessions.cardioZ2()))
        val computation = compute(session)
        assertTrue(computation.projection.hasWork)
        assertNotEquals(DrainReading.Unknown, computation.projection.muscularReading)
        assertTrue(computation.muscleDrainPrecise.isNotEmpty())
        assertEquals(1, computation.setCount)
    }

    @Test
    fun cacheKeys_changeWithTheCatalogInstanceAndTheLogs() {
        val a = mapOf("x" to ExerciseMuscleInfo(id = "x", name = "X"))
        val b = mapOf("x" to ExerciseMuscleInfo(id = "x", name = "X", alias = "otro metadato"))
        assertEquals("mismo tamaño", a.size, b.size)
        assertNotEquals("un índice nuevo, aunque conserve el tamaño, invalida", AugeCacheKeys.catalogVersion(a), AugeCacheKeys.catalogVersion(b))
        assertEquals(AugeCacheKeys.catalogVersion(a), AugeCacheKeys.catalogVersion(a))

        val log = WorkoutLog(id = "l1", programId = "p", sessionId = "s", sessionName = "S", date = "2026-10-01T10:00:00Z", durationMinutes = 60, sessionStressScore = 40.0)
        val none = AugeCacheKeys.logsFingerprint(emptyList())
        val one = AugeCacheKeys.logsFingerprint(listOf(log))
        val changed = AugeCacheKeys.logsFingerprint(listOf(log.copy(sessionStressScore = 55.0)))
        assertNotEquals(none, one)
        assertNotEquals("cambiar una puntuación de estrés invalida", one, changed)
        assertEquals(one, AugeCacheKeys.logsFingerprint(listOf(log)))
    }
}
