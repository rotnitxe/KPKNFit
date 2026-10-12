package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.PostSessionPreview
import com.example.kpkn.data.models.RecoveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `finishRingEdits`: qué anillos del cierre se le aplican a AUGE (paquete 1.2b, hallazgo F-02).
 *
 * La hoja del cierre y el cierre por voz comparten esta función. La hoja trae siempre los dos valores finales y los compara
 * con el resumen (`preview`); la voz solo dicta lo que dijo la persona (un final nulo es «no tocado») y puede cerrar sin
 * que el resumen esté disponible. Ningún caso usa reloj: es una función pura.
 *
 * También cubre el puente que entrega a la pantalla el cierre por voz ya guardado (`FinishRingsBridge`), la guarda de una
 * sola aplicación por sesión (`FinishRingsOnce`) y, con guardas de texto, el cableado del ViewModel y de la pantalla.
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

    // ─── Puente entre la voz y la pantalla ────────────────────────────────────────────────────────

    private class Recorder {
        val received = mutableListOf<SessionClosingFeedback>()
        val applier: (SessionClosingFeedback) -> Unit = { received += it }
    }

    @Test
    fun bridge_deliversTheSavedSessionToTheRegisteredApplier() {
        val recorder = Recorder()
        val bridge = FinishRingsBridge()
        bridge.setApplier(recorder.applier)
        val feedback = closing(neural = 60)

        bridge.onSessionSaved(feedback)

        assertEquals(listOf(feedback), recorder.received)
    }

    @Test
    fun bridge_withoutApplier_keepsTheSessionPendingAndDeliversItOnce() {
        val first = Recorder()
        val second = Recorder()
        val bridge = FinishRingsBridge()
        val feedback = closing(neural = 60)

        // El cierre termina mientras la pantalla se recrea: no hay aplicador y el aviso no se pierde.
        bridge.onSessionSaved(feedback)
        assertEquals(emptyList<SessionClosingFeedback>(), first.received)

        bridge.setApplier(first.applier)
        assertEquals("se entrega al registrarse el siguiente aplicador", listOf(feedback), first.received)

        bridge.setApplier(null)
        bridge.setApplier(second.applier)
        assertEquals("y solo una vez", emptyList<SessionClosingFeedback>(), second.received)
    }

    @Test
    fun bridge_aRemovedApplierReceivesNothingMore() {
        val recorder = Recorder()
        val bridge = FinishRingsBridge()
        bridge.setApplier(recorder.applier)
        bridge.setApplier(null)

        bridge.onSessionSaved(closing(neural = 60))

        assertEquals(emptyList<SessionClosingFeedback>(), recorder.received)
    }

    @Test
    fun bridge_aFailingApplier_isReportedAndNeverPropagated() {
        val failures = mutableListOf<Throwable>()
        val bridge = FinishRingsBridge(onFailure = { failures += it })
        bridge.setApplier { error("fallo del aplicador") }

        // No debe lanzar: detrás va el TTS «guardada».
        bridge.onSessionSaved(closing(neural = 60))

        assertEquals(listOf("fallo del aplicador"), failures.map { it.message })
    }

    @Test
    fun bridge_aFailingApplier_onAPendingSession_doesNotBreakTheRegistration() {
        val failures = mutableListOf<Throwable>()
        val bridge = FinishRingsBridge(onFailure = { failures += it })
        bridge.onSessionSaved(closing(neural = 60))

        // Registrarse (desde un efecto de Compose) nunca debe lanzar.
        bridge.setApplier { error("fallo del aplicador") }

        assertEquals(1, failures.size)
    }

    // ─── Una sola aplicación por sesión ───────────────────────────────────────────────────────────

    @Test
    fun once_theSecondApplicationForTheSameSessionIsRejected() {
        val once = FinishRingsOnce()

        assertTrue(once.shouldApply("log-1"))
        assertFalse("la misma sesión no se aplica dos veces", once.shouldApply("log-1"))
        assertFalse(once.shouldApply("log-1"))
    }

    @Test
    fun once_anotherSessionIsApplied() {
        val once = FinishRingsOnce()
        once.shouldApply("log-1")

        assertTrue(once.shouldApply("log-2"))
    }

    @Test
    fun once_withoutAKeyThereIsNothingToCompare_soItApplies() {
        val once = FinishRingsOnce()

        assertTrue(once.shouldApply(null))
        assertTrue(once.shouldApply(null))
        assertTrue(once.shouldApply(" "))
    }

    // ─── Cableado (guardas de texto, como WorkoutFinishSheetEntryTest) ────────────────────────────
    // No hay banco que construya WorkoutViewModel ni la pantalla de Compose: el contrato de «tras el guardado real y antes
    // del TTS» y el aplicador único se fijan sobre el código fuente.

    private fun workoutSource(fileName: String): String {
        val relative = "main/java/com/example/kpkn/screens/workout/$fileName"
        val file = File("../$relative").takeIf { it.exists() } ?: File("src/$relative")
        return file.readText().replace("\r\n", "\n")
    }

    @Test
    fun voiceFinish_appliesTheRingsAfterTheRealSaveAndBeforeTheSpeech() {
        val body = workoutSource("WorkoutViewModel.kt")
            // Con la llave: el puerto de la voz también declara `override fun finalizeVoiceSession() = …`.
            .substringAfter("fun finalizeVoiceSession() {")
            .substringBefore("private fun handleEmptySessionFinishBlocked")
        val finish = body.indexOf("finishWorkout(")
        val onComplete = body.indexOf("onComplete = {")
        val apply = body.indexOf("finishRingsBridge.onSessionSaved(closingFeedback)")
        val speech = body.indexOf("voiceController.speakSessionSaved()")

        assertTrue("finalizeVoiceSession no avisa al puente de los anillos", apply >= 0)
        assertTrue("el aviso va dentro del onComplete del guardado real", finish in 0 until onComplete && onComplete < apply)
        assertTrue("el aviso va antes del TTS «guardada»", apply < speech)
    }

    @Test
    fun workoutScreen_hasASingleRingApplierSharedBySheetAndVoice() {
        val screen = workoutSource("WorkoutScreen.kt")

        assertEquals(
            "applyManualBatteries debe tener un único llamador en la pantalla",
            1,
            Regex("""applyManualBatteries\(""").findAll(screen).count(),
        )
        assertTrue("la pantalla debe registrar el aplicador del cierre por voz", screen.contains("viewModel.setFinishRingsApplier("))
        assertTrue("la hoja usa el aplicador compartido", screen.contains("applyFinishRings(closingFeedback, postSessionPreview)"))
        assertFalse("la comparación antigua no debe volver a la pantalla", screen.contains("closingFeedback.finalNeuralBattery !="))
    }

    @Test
    fun workoutScreen_rereadsTodaysWellbeingRowWhenAugeRecomputesAndTheDayChanges() {
        val screen = workoutSource("WorkoutScreen.kt")
        val keyedProducer = Regex("""produceState<DailyWellbeingLog\?>\(\s*initialValue = null,\s*augeSnapshot,\s*today\s*\)""")

        assertTrue("la fila de hoy debe releerse con el snapshot de AUGE y la fecha como claves", keyedProducer.containsMatchIn(screen))
    }
}
