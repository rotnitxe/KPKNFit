package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.PostSessionPreview
import com.example.kpkn.data.models.RecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `finishRingEdits`: qué anillos del cierre se le aplican a AUGE (paquete 1.2b, hallazgo F-02).
 *
 * La hoja del cierre y el cierre por voz comparten esta función. La hoja trae siempre los dos valores finales y los compara
 * con el resumen (`preview`); la voz solo dicta lo que dijo la persona (un final nulo es «no tocado») y puede cerrar sin
 * que el resumen esté disponible. Ningún caso usa reloj: es una función pura.
 */
class FinishRingEditsTest {

    private fun muscle(name: String, score: Int) = MuscleRecoveryStatus(
        muscleName = name,
        recoveryScore = score,
        hoursToRecovery = 24,
        hoursSinceLastSession = 0,
        effectiveSets = 3,
        status = RecoveryStatus.RECOVERING,
    )

    /** Resumen del cierre: Energía 80, Columna mostrada 70 (cruda 64), drenajes 12/9/20 y dos músculos. */
    private fun preview(neural: Int = 80, spinal: Int = 70, spinalRaw: Int = 64) = PostSessionPreview(
        neural = neural,
        spinal = spinal,
        muscular = 75,
        perMuscle = mapOf(
            "Pectorales" to muscle("Pectorales", 78),
            "Tríceps" to muscle("Tríceps", 86),
        ),
        globalCnsDrain = 12,
        globalMuscularDrain = 20,
        globalSpinalDrain = 9,
        spinalRaw = spinalRaw,
        involvedVolumeMuscles = setOf("Pectorales", "Tríceps"),
    )

    private fun closing(
        neural: Int? = null,
        spinal: Int? = null,
        muscles: Map<String, Int> = emptyMap(),
        edited: Set<String> = muscles.keys,
    ) = SessionClosingFeedback(
        overallFatigue = 3,
        systemAdjustment = 0,
        muscularAdjustment = 0,
        structureAdjustment = 0,
        discomforts = emptyList(),
        finalNeuralBattery = neural,
        finalSpinalBattery = spinal,
        finalMuscleBatteries = muscles,
        editedMuscleKeys = edited,
    )

    // ─── a. Sin cambios ───────────────────────────────────────────────────────────────────────────

    @Test
    fun noChanges_returnsNull() {
        assertNull(finishRingEdits(closing(neural = 80, spinal = 70), preview()))
    }

    // ─── b. Energía distinta ──────────────────────────────────────────────────────────────────────

    @Test
    fun neuralChanged_reportsOnlyNeural_withThePreviewPredictionsAndDrains() {
        val edits = finishRingEdits(closing(neural = 60, spinal = 70), preview())

        assertEquals(
            FinishRingEdits(
                neural = 60,
                spinal = null,
                muscleDeltas = emptyMap(),
                sessionCnsDrain = 12.0,
                sessionSpinalDrain = 9.0,
                sessionMuscleDrain = 20.0,
                predictedNeural = 80,
                predictedSpinal = 64,
                predictedMuscles = mapOf("Pectorales" to 78, "Tríceps" to 86),
            ),
            edits,
        )
    }

    // ─── c. Columna distinta ──────────────────────────────────────────────────────────────────────

    @Test
    fun spinalChanged_usesTheRawSpinalAsPrediction() {
        val edits = requireNotNull(finishRingEdits(closing(neural = 80, spinal = 55), preview()))

        assertNull(edits.neural)
        assertEquals(55, edits.spinal)
        assertEquals("la predicción de Columna es la cruda, no la mostrada", 64, edits.predictedSpinal)
        assertEquals(80, edits.predictedNeural)
    }

    @Test
    fun spinalIsComparedWithTheDisplayedValue_notTheRawOne() {
        // El anillo se siembra con la Columna mostrada (70): dejarlo igual no es editar, aunque la cruda sea 64.
        assertNull(finishRingEdits(closing(neural = 80, spinal = 70), preview()))
        assertEquals(64, finishRingEdits(closing(neural = 80, spinal = 64), preview())?.spinal)
    }

    // ─── d. Músculos editados ─────────────────────────────────────────────────────────────────────

    @Test
    fun editedMuscleKeys_limitTheMuscleDeltas() {
        val edits = requireNotNull(
            finishRingEdits(
                closing(
                    neural = 80,
                    spinal = 70,
                    muscles = mapOf("Pectorales" to 60, "Tríceps" to 86),
                    edited = setOf("Pectorales"),
                ),
                preview(),
            ),
        )

        assertEquals(mapOf("Pectorales" to 60), edits.muscleDeltas)
        assertNull("los globales sin tocar no se informan", edits.neural)
        assertNull(edits.spinal)
    }

    @Test
    fun withoutEditedMuscleKeys_listedFinalValuesAreNotEdits() {
        val closing = closing(neural = 80, spinal = 70, muscles = mapOf("Pectorales" to 60), edited = emptySet())

        assertNull(finishRingEdits(closing, preview()))
    }

    // ─── e. Cierre por voz con valores nulos (solo lo dictado) ────────────────────────────────────

    @Test
    fun voiceDictatedOnlyEnergy_appliesOnlyEnergy() {
        val edits = requireNotNull(finishRingEdits(closing(neural = 60, spinal = null), preview()))

        assertEquals(60, edits.neural)
        assertNull("lo no dictado no es «distinto del resumen»", edits.spinal)
        assertEquals(80, edits.predictedNeural)
        assertEquals(64, edits.predictedSpinal)
    }

    @Test
    fun voiceDictatedOnlySpine_appliesOnlySpine() {
        val edits = requireNotNull(finishRingEdits(closing(neural = null, spinal = 40), preview()))

        assertNull("lo no dictado no es «distinto del resumen»", edits.neural)
        assertEquals(40, edits.spinal)
    }

    @Test
    fun voiceDictatedNothing_returnsNull() {
        assertNull(finishRingEdits(closing(neural = null, spinal = null), preview()))
    }

    @Test
    fun voiceDictatedTheSameAsThePreview_isNotAnEdit() {
        assertNull(finishRingEdits(closing(neural = 80, spinal = null), preview()))
    }

    // ─── f. Sin resumen (la hoja no estaba abierta) ───────────────────────────────────────────────

    @Test
    fun withoutPreview_dictatedValuesAreAppliedWithoutPredictions() {
        val edits = finishRingEdits(closing(neural = 60, spinal = 40), preview = null)

        assertEquals(
            FinishRingEdits(
                neural = 60,
                spinal = 40,
                muscleDeltas = emptyMap(),
                sessionCnsDrain = 0.0,
                sessionSpinalDrain = 0.0,
                sessionMuscleDrain = 0.0,
                predictedNeural = null,
                predictedSpinal = null,
                predictedMuscles = emptyMap(),
            ),
            edits,
        )
    }

    @Test
    fun withoutPreview_onlyWhatWasDictatedIsApplied() {
        val edits = requireNotNull(finishRingEdits(closing(neural = null, spinal = 40), preview = null))

        assertNull(edits.neural)
        assertEquals(40, edits.spinal)
        assertNull(edits.predictedSpinal)
    }

    @Test
    fun withoutPreviewAndNothingDictated_returnsNull() {
        assertNull(finishRingEdits(closing(neural = null, spinal = null), preview = null))
    }

    // ─── g. Equivalencia con la lógica antigua de WorkoutScreen ───────────────────────────────────

    @Test
    fun withInformedFinals_matchesTheLegacyComparisons() {
        val grid = (0..100 step 10).toList()
        var compared = 0
        for (finalNeural in grid) for (previewNeural in grid) for (finalSpinal in grid) for (previewSpinal in grid) {
            val preview = preview(neural = previewNeural, spinal = previewSpinal)
            val closing = closing(neural = finalNeural, spinal = finalSpinal)
            // Lo que hacía WorkoutScreen antes del paquete: comparación por valor y «algún anillo editado».
            val legacyNeuralChanged = closing.finalNeuralBattery != preview.neural
            val legacySpinalChanged = closing.finalSpinalBattery != preview.spinal
            val legacyAnyRingEdit = legacyNeuralChanged || legacySpinalChanged || closing.editedMuscleKeys.isNotEmpty()

            val edits = finishRingEdits(closing, preview)

            val case = "Energía $finalNeural/$previewNeural, Columna $finalSpinal/$previewSpinal"
            assertEquals(case, legacyAnyRingEdit, edits != null)
            assertEquals(case, if (legacyNeuralChanged) finalNeural else null, edits?.neural)
            assertEquals(case, if (legacySpinalChanged) finalSpinal else null, edits?.spinal)
            compared++
        }
        assertEquals("la malla debe recorrer 11⁴ combinaciones", 14_641, compared)
    }

    @Test
    fun withEditedMuscles_matchesTheLegacyBlock() {
        val muscles = mapOf("Pectorales" to 60, "Tríceps" to 70)
        listOf(emptySet(), setOf("Pectorales"), setOf("Pectorales", "Tríceps")).forEach { edited ->
            val closing = closing(neural = 80, spinal = 70, muscles = muscles, edited = edited)
            // perMuscleDelta de la hoja antigua: null sin músculos editados; si no, los finales de los editados.
            val legacyDelta = if (closing.editedMuscleKeys.isNotEmpty()) {
                closing.finalMuscleBatteries.filterKeys { it in closing.editedMuscleKeys }
            } else {
                null
            }

            val edits = finishRingEdits(closing, preview())

            assertEquals("editados=$edited", edited.isNotEmpty(), edits != null)
            assertEquals("editados=$edited", legacyDelta.orEmpty(), edits?.muscleDeltas.orEmpty())
        }
    }
}
