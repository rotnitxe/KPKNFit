package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import com.example.kpkn.domain.training.ProgramStructureMutator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface HybridCompositionFailure {
    data object UnsupportedSchema : HybridCompositionFailure
    data object MissingIntent : HybridCompositionFailure
    data object AlreadyStarted : HybridCompositionFailure
    data object InvalidHierarchy : HybridCompositionFailure
    data object EmptyWeeks : HybridCompositionFailure
    data class DuplicateWeek(val weekId: String) : HybridCompositionFailure
    data class WeekNotFound(val weekId: String) : HybridCompositionFailure
    data class InvalidSource(val sessionId: String) : HybridCompositionFailure
    data class IdentityConflict(val sessionId: String) : HybridCompositionFailure
    data class CannotPlan(val weekId: String, val result: HybridProgramPlanResult) : HybridCompositionFailure
}

sealed interface HybridCompositionResult {
    data class Ready(val program: Program, val proposals: List<HybridWeekProposal>) : HybridCompositionResult
    data class Rejected(val reason: HybridCompositionFailure) : HybridCompositionResult
}

/**
 * Joins the existing native generator/author recipe with independently authored sport sessions.
 * Keeps the primary recipe, native load references, progression and all session content. It never
 * scales doses to make a week fit, repeats a foreign recipe, or changes the athlete's objectives.
 * Output proposals require a separate explicit calendar acceptance.
 */
object HybridProgramAssembler {
    private val json = Json { encodeDefaults = true }

    fun assemble(program: Program, composition: HybridProgramComposition): HybridCompositionResult {
        fun reject(failure: HybridCompositionFailure) = HybridCompositionResult.Rejected(failure)
        if (composition.schemaVersion != 1) return reject(HybridCompositionFailure.UnsupportedSchema)
        if (program.trainingIntent == null) return reject(HybridCompositionFailure.MissingIntent)
        if (program.runState != null || program.hybridOccurrenceSchedules.isNotEmpty()) return reject(HybridCompositionFailure.AlreadyStarted)
        if (composition.weeks.isEmpty()) return reject(HybridCompositionFailure.EmptyWeeks)
        val duplicate = composition.weeks.groupingBy { it.weekId }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) return reject(HybridCompositionFailure.DuplicateWeek(duplicate.key))
        // Detach all content, including nested variants and payload objects, for a stable proposal.
        var candidate = json.decodeFromString<Program>(json.encodeToString(program)).copy(
            hybridPlanning = HybridProgramPlanner.snapshotConfiguration(composition.configuration),
        )
        val input = json.decodeFromString<HybridProgramComposition>(json.encodeToString(composition))
        if (!ProgramHierarchyIndex(candidate).isValid) return reject(HybridCompositionFailure.InvalidHierarchy)
        val proposals = mutableListOf<HybridWeekProposal>()
        for (weekInput in input.weeks) {
            val index = ProgramHierarchyIndex(candidate)
            val week = index.locateWeek(weekInput.weekId)?.week
                ?: return reject(HybridCompositionFailure.WeekNotFound(weekInput.weekId))
            val sources = candidate.hybridAuthoredSources.toMutableList()
            val additions = mutableListOf<Session>()
            val incomingIds = mutableSetOf<String>()
            for (contribution in weekInput.contributions) {
                val session = contribution.session
                if (session.id.isBlank() || contribution.binding.sessionId != session.id ||
                    contribution.sourceId.isBlank() || contribution.sourceVersion <= 0 || !incomingIds.add(session.id)
                ) return reject(HybridCompositionFailure.InvalidSource(session.id))
                val source = HybridAuthoredSessionSource(week.id, session.id, contribution.binding.goalId,
                    contribution.sourceId, contribution.sourceVersion)
                val existing = index.locateSession(session.id)
                if (existing == null) {
                    additions += session
                    sources += source
                } else if (existing.hierarchy.weekId != week.id || existing.session != session ||
                    candidate.hybridAuthoredSources.count { it == source } != 1
                ) return reject(HybridCompositionFailure.IdentityConflict(session.id))
            }
            val changed = ProgramStructureMutator.updateWeek(candidate, week.id) { it.copy(
                sessions = it.sessions + additions,
                hybridPlanningConfiguration = weekInput.configuration?.let(HybridProgramPlanner::snapshotConfiguration)
                    ?: it.hybridPlanningConfiguration,
            ) }
            candidate = (changed as? ProgramStructureMutationResult.Success)?.program?.copy(hybridAuthoredSources = sources)
                ?: return reject(HybridCompositionFailure.InvalidHierarchy)
            if (!hasUniqueSessionIdentities(candidate)) return reject(HybridCompositionFailure.InvalidHierarchy)
            val bindings = weekInput.baseSessionBindings + weekInput.contributions.map { it.binding }
            val prepared = HybridProgramPlanner.prepare(candidate, week.id, bindings)
            if (prepared !is HybridProgramPlanResult.Ready) return reject(HybridCompositionFailure.CannotPlan(week.id, prepared))
            val stored = ProgramStructureMutator.updateWeek(candidate, week.id) { it.copy(hybridProposal = prepared.proposal) }
            candidate = (stored as ProgramStructureMutationResult.Success).program
            proposals += prepared.proposal
        }
        return HybridCompositionResult.Ready(candidate, proposals)
    }

    private fun hasUniqueSessionIdentities(program: Program): Boolean {
        val ids = mutableSetOf<String>()
        fun visit(session: Session): Boolean = session.id.isNotBlank() && ids.add(session.id) &&
            listOfNotNull(session.sessionB, session.sessionC, session.sessionD).all(::visit)
        val templates = ProgramHierarchyIndex(program).orderedSessions().map { it.session }
        val additional = program.loops.flatMap { it.sessions } + program.events.flatMap { it.sessions } +
            program.calendarBreaks.flatMap { it.weeks }.flatMap { it.sessions }
        return (templates + additional).all(::visit)
    }
}
