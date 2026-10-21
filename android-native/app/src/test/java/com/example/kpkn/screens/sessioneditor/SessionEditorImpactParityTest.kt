package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.testsupport.MotCatalog
import com.example.kpkn.testsupport.MotSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · I1 (AUD1 §1): el editor debe mostrar el mismo coste que el motor general. Reproducciones con el catálogo real aprobado
 * (sentadilla trasera alta y peso muerto convencional, cardio Z2). Estas pruebas solo usan la API anterior a la proyección
 * compartida: en la rama sin arreglo fallan por la divergencia del cálculo del editor.
 */
class SessionEditorImpactParityTest {
    private val index = MotCatalog.index
    private val enthusiast = Settings(athleteType = AthleteType.ENTHUSIAST)

    private fun editorDrain(session: Session, settings: Settings = enthusiast): PredictedDrain =
        computeSessionAugeComputation(
            session = session,
            exerciseIndex = index,
            settings = settings,
            programLogs = emptyList(),
            programId = "p",
            mesoIndex = 0,
        ).drain

    private fun generalDrain(session: Session, settings: Settings = enthusiast): PredictedDrain =
        AugeFatigueEngine.calculateAdjustedPredictedDrain(session, index, settings)

    @Test
    fun squatAndDeadlift_editorColumnEqualsGeneralEngine() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        val general = generalDrain(session)
        val editor = editorDrain(session)
        // Contrato actual del motor general fijado por AUD1 §1.2 (caso B): 32 / 17 / 8 (Músculos / Energía / Columna).
        assertEquals("general=$general", 8, general.spinal)
        assertEquals("editor=$editor general=$general", general.spinal, editor.spinal)
        assertEquals("editor=$editor general=$general", general.muscular, editor.muscular)
        assertEquals("editor=$editor general=$general", general.cns, editor.cns)
    }

    @Test
    fun cardioOnlySession_usesCardioContribution_notTheFlatFallback() {
        val session = Session(id = "cardio-only", name = "Cardio", exercises = listOf(MotSessions.cardioZ2()))
        val general = generalDrain(session)
        val editor = editorDrain(session)
        assertNotEquals(
            "El cardio no puede acabar en el respaldo fijo 14/10/8: editor=$editor",
            AugeFatigueEngine.DEFAULT_FALLBACK_PREDICTED_DRAIN,
            editor,
        )
        assertEquals("editor=$editor general=$general", general, editor)
    }

    @Test
    fun squatPlusCardio_cardioCountsInTheTotals() {
        val strength = MotSessions.squatAndDeadlift(setsEach = 3)
        val mixed = strength.copy(exercises = strength.exercises + MotSessions.cardioZ2())
        val editorMixed = editorDrain(mixed)
        val generalMixed = generalDrain(mixed)
        assertEquals("editor=$editorMixed general=$generalMixed", generalMixed, editorMixed)
        assertTrue(
            "El cardio mezclado debe sumar a los totales: mixto=$editorMixed solo fuerza=${editorDrain(strength)}",
            editorMixed.muscular > editorDrain(strength).muscular || editorMixed.cns > editorDrain(strength).cns,
        )
    }

    @Test
    fun emptySlotsDoNotCountAsWork() {
        val session = MotSessions.squatAndDeadlift(setsEach = 3)
        val withHoles = session.copy(
            exercises = session.exercises.map { ex ->
                ex.copy(sets = ex.sets + ex.sets.first().copy(id = "${ex.id}-hole", isEmptySlot = true))
            },
        )
        assertEquals(
            "Un hueco de ronda borrada no es una serie",
            editorDrain(session),
            editorDrain(withHoles),
        )
    }
}
