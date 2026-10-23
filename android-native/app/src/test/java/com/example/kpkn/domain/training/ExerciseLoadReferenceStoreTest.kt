package com.example.kpkn.domain.training

import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceMetadata
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ExerciseLoadReferenceStoreTest {
    private fun ref(kind: PlanLoadReferenceKind = PlanLoadReferenceKind.EXERCISE_TM, kg: Double = 100.0,
        at: Long? = 10L, source: String = "event") = PlanLoadReference(kind, "press__barbell",
        LoadQuantityConvention.TOTAL_EXTERNAL, state = PlanLoadReferenceState.CAPTURED,
        capturedLoadKg = kg, capturedAtMs = at, metadata = PlanLoadReferenceMetadata(sourceId = source))
    private fun add(program: Program, reference: PlanLoadReference) = ExerciseLoadReferenceStore.appendCapture(program, setOf("press"), reference)
    private fun current(program: Program, request: PlanLoadReference) = ExerciseLoadReferenceStore.resolve(program, "press", request)

    @Test fun oneRmAndTmCoexistAndAnObservedCaptureDoesNotEraseThem() {
        val oneRm = ref(PlanLoadReferenceKind.EXERCISE_1RM, 125.0)
        val tm = ref()
        val observed = ref(PlanLoadReferenceKind.OBSERVED_WORKING_SET, 80.0).copy(repMin = 3, repMax = 5)
        val program = add(add(add(Program("p", "Plan"), oneRm), tm), observed)
        assertEquals(125.0, current(program, oneRm)!!.capturedLoadKg!!, 0.0)
        assertEquals(100.0, current(program, tm)!!.capturedLoadKg!!, 0.0)
        assertEquals(3, program.exerciseLoadReferences.single().references.size)
    }

    @Test fun pendingDoesNotHideTheLatestCompatibleCapture() {
        val old = ref(kg = 95.0, at = 10, source = "old")
        val newest = ref(kg = 110.0, at = 30, source = "new")
        val pending = old.copy(state = PlanLoadReferenceState.PENDING, capturedLoadKg = null)
        assertEquals(newest, ExerciseLoadReferenceStore.resolve(listOf(newest, old, pending), pending))
    }

    @Test fun allExactIdentityBoundariesAreEnforced() {
        val base = ref()
        val program = add(Program("p", "Plan"), base)
        listOf(base.copy(configurationId = "press__dumbbells"), base.copy(quantityConvention = LoadQuantityConvention.PER_IMPLEMENT),
            base.copy(side = "left"), base.copy(side = "right"), base.copy(side = "bilateral"),
            base.copy(metadata = base.metadata!!.copy(contextKey = "tag-brand-setup")),
            base.copy(kind = PlanLoadReferenceKind.EXERCISE_1RM)).forEach { assertNull(current(program, it)) }
        assertEquals(base, current(program, base))
    }

    @Test fun apreXrmDoesNotMatchOrdinaryWorkingSetsOrAnotherVariantOrRecipeSlot() {
        val xrm = ref(PlanLoadReferenceKind.OBSERVED_WORKING_SET).copy(repMin = 3, repMax = 3, sourceSlotId = "real-slot",
            metadata = PlanLoadReferenceMetadata(origin = ExerciseLoadReferenceStore.APRE_XRM, sourceId = "s4", sourceSetId = "set-4"))
        val program = add(Program("p", "Plan"), xrm)
        listOf(xrm.copy(repMax = 5), xrm.copy(repMin = 6, repMax = 6), xrm.copy(sourceSlotId = "other-slot"),
            xrm.copy(metadata = xrm.metadata!!.copy(origin = null))).forEach { assertNull(current(program, it)) }
        assertEquals(xrm, current(program, xrm))
    }

    @Test fun repeatingEventWithSamePayloadIsIdempotentButCorrectionHasHistory() {
        val reference = ref()
        val initial = add(Program("p", "Plan"), reference)
        assertEquals(initial, add(initial, reference.copy(capturedAtMs = 20)))
        val corrected = add(initial, reference.copy(capturedLoadKg = 105.0, capturedAtMs = 21))
        val history = ExerciseLoadReferenceStore.history(corrected, "press", reference)
        assertEquals(2, history.size)
        assertEquals(0, history[0].metadata!!.revision)
        assertEquals(1, history[1].metadata!!.revision)
        assertEquals(105.0, current(corrected, reference)!!.capturedLoadKg!!, 0.0)
        assertEquals(corrected, add(corrected, reference.copy(capturedLoadKg = 105.0, capturedAtMs = 50)))
        val reverted = add(corrected, reference.copy(capturedAtMs = 60))
        assertEquals(3, ExerciseLoadReferenceStore.history(reverted, "press", reference).size)
        assertEquals(100.0, current(reverted, reference)!!.capturedLoadKg!!, 0.0)
    }

    @Test fun unknownDateIsNotInventedAndEqualDatesUseTheLastRevision() {
        val old = ref(at = null)
        val corrected = old.copy(capturedLoadKg = 105.0)
        val program = add(add(Program("p", "Plan"), old), corrected)
        assertTrue(program.exerciseLoadReferences.single().references.all { it.capturedAtMs == null })
        assertEquals(105.0, current(program, old)!!.capturedLoadKg!!, 0.0)
    }

    @Test fun onlyRequestedExerciseIdsAreCapturedAndNothingIsApplied() {
        val before = Program("p", "Plan")
        val after = ExerciseLoadReferenceStore.appendCapture(before, setOf("press", "press-next"), ref())
        assertEquals(setOf("press", "press-next"), after.exerciseLoadReferences.map { it.exerciseId }.toSet())
        assertNull(current(after, ref().copy(configurationId = "other")))
        assertEquals(before.macrocycles, after.macrocycles)
        assertEquals(before.powerliftingProfile, after.powerliftingProfile)
        assertEquals(before.runState, after.runState)
    }

    @Test fun explicitClearDoesNotResurrectAnOlderCaptureButNewCaptureCanReplaceIt() {
        val reference = ref()
        val captured = add(Program("p", "Plan"), reference)
        val cleared = ExerciseLoadReferenceStore.invalidate(captured, setOf("press"), reference, 20, "clear", "Retirado")
        assertEquals(PlanLoadReferenceState.PENDING, current(cleared, reference)!!.state)
        assertNull(current(cleared, reference)!!.capturedLoadKg)
        val updated = add(cleared, reference.copy(capturedLoadKg = 110.0, capturedAtMs = 30,
            metadata = reference.metadata!!.copy(sourceId = "new")))
        assertEquals(110.0, current(updated, reference)!!.capturedLoadKg!!, 0.0)
        assertEquals(3, ExerciseLoadReferenceStore.history(updated, "press", reference).size)
    }

    @Test fun oldJsonRemainsReadableAndNewProvenanceRoundTrips() {
        val legacy = Json.decodeFromString<PlanLoadReference>("""{"kind":"EXERCISE_TM","configurationId":"press"}""")
        assertNull(legacy.metadata)
        assertNull(legacy.capturedAtMs)
        val reference = ref().copy(metadata = PlanLoadReferenceMetadata(origin = "MANUAL", reason = "Confirmado", tmFraction = 0.9))
        assertEquals(reference, Json.decodeFromString<PlanLoadReference>(Json.encodeToString(reference)))
    }
}
