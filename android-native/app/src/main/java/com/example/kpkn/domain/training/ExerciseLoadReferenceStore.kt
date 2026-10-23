package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseLoadReference
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState

/** Pure access to Program.exerciseLoadReferences. Capturing does not apply a prescription or change a profile. */
object ExerciseLoadReferenceStore {
    const val APRE_XRM = "APRE_XRM"
    const val MANUAL = "MANUAL"
    const val LEGACY_PROFILE = "LEGACY_PROFILE"
    const val MATERIALIZED_RECIPE = "MATERIALIZED_RECIPE"
    const val AUTHOR_PROGRESSION = "AUTHOR_PROGRESSION"

    /** Null side is the global exercise base, not an alias for left/right/bilateral. */
    fun sameIdentity(candidate: PlanLoadReference, request: PlanLoadReference): Boolean =
        candidate.kind == request.kind && candidate.configurationId == request.configurationId &&
            candidate.quantityConvention == request.quantityConvention && candidate.side == request.side &&
            candidate.metadata?.contextKey == request.metadata?.contextKey &&
            (request.kind != PlanLoadReferenceKind.OBSERVED_WORKING_SET ||
                (candidate.repMin == request.repMin && candidate.repMax == request.repMax &&
                    candidate.sourceSlotId == request.sourceSlotId && candidate.metadata?.origin == request.metadata?.origin))

    fun history(program: Program, exerciseId: String, request: PlanLoadReference): List<PlanLoadReference> =
        program.exerciseLoadReferences.filter { it.exerciseId == exerciseId }.flatMap { it.references }
            .filter { sameIdentity(it, request) }

    fun resolve(program: Program, exerciseId: String, request: PlanLoadReference): PlanLoadReference? =
        resolve(history(program, exerciseId, request), request)

    /** Captured wins over pending. Missing capture dates remain unknown; insertion order breaks equal-date ties. */
    fun resolve(references: List<PlanLoadReference>, request: PlanLoadReference): PlanLoadReference? {
        val compatible = references.withIndex().filter { sameIdentity(it.value, request) }
        return compatible.filter { (_, ref) -> ref.state == PlanLoadReferenceState.CAPTURED &&
            ref.capturedLoadKg?.let { it.isFinite() && it > 0.0 } == true }
            .maxWithOrNull(compareBy<IndexedValue<PlanLoadReference>> { it.value.capturedAtMs ?: Long.MIN_VALUE }
                .thenBy { it.value.metadata?.revision ?: 0 }.thenBy { it.index })?.value
            ?: compatible.lastOrNull { it.value.state == PlanLoadReferenceState.PENDING }?.value
    }

    /** Append-only and idempotent for the same event/payload. A correction of that event gets a new revision. */
    fun appendCapture(program: Program, exerciseIds: Set<String>, reference: PlanLoadReference): Program {
        require(reference.state == PlanLoadReferenceState.CAPTURED)
        require(reference.configurationId.isNotBlank())
        require(reference.capturedLoadKg?.let { it.isFinite() && it > 0.0 } == true)
        if (exerciseIds.isEmpty()) return program
        var pool = program.exerciseLoadReferences
        exerciseIds.forEach { id ->
            require(id.isNotBlank())
            val existing = pool.filter { it.exerciseId == id }.flatMap { it.references }
            val sourceId = reference.metadata?.sourceId?.takeIf { it.isNotBlank() }
            val sameEvent = existing.filter { sameIdentity(it, reference) &&
                sourceId != null && it.metadata?.sourceId == sourceId }
            val duplicate = if (sourceId == null) reference in existing else sameEvent.any { samePayload(it, reference) }
            if (!duplicate) {
                val captured = if (reference.metadata == null) reference else reference.copy(metadata = reference.metadata.copy(
                    revision = maxOf(reference.metadata.revision, (sameEvent.maxOfOrNull { it.metadata?.revision ?: 0 } ?: -1) + 1),
                ))
                val next = ExerciseLoadReference(id, existing + captured)
                pool = pool.filterNot { it.exerciseId == id } + next
            }
        }
        return if (pool == program.exerciseLoadReferences) program else program.copy(exerciseLoadReferences = pool)
    }

    private fun samePayload(first: PlanLoadReference, second: PlanLoadReference): Boolean =
        first.copy(capturedAtMs = null, metadata = first.metadata?.copy(revision = 0)) ==
            second.copy(capturedAtMs = null, metadata = second.metadata?.copy(revision = 0))
}
