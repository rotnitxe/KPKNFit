package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.TrainingMaxMerge

data class MeetResultProposal(val profile: PowerliftingProfile,val summary: String)

object MeetResultToProfile {
    fun propose(record: CompetitionRecord,current: PowerliftingProfile?,trainingMaxPercent: Double): MeetResultProposal? {
        if (record.status != CompetitionRecordStatus.COMPLETED || record.meetResultAppliedAtMs != null ||
            !trainingMaxPercent.isFinite() || trainingMaxPercent <= 0 || trainingMaxPercent > 1) return null
        fun best(movement: CompetitionMovementType,old: Double?,estimate: Double?): Double? {
            if (!AttemptPlanner.matchesCompetitionVariant(current,movement)) return null
            val weights = record.technicalBlocks.filter { it.movementType == movement }.flatMap { block ->
                listOfNotNull(block.bestValidWeightKg) + block.attempts.filter { it.resultType == CompetitionAttemptResult.GOOD_LIFT }.mapNotNull { it.weightKg }
            }.filter { it.isFinite() && it > 0 }
            val result = weights.maxOrNull() ?: return null
            val previous = old?.takeIf { it.isFinite() && it > 0 }
                ?: estimate?.takeIf { it.isFinite() && it > 0 } ?: 0.0
            return result.takeIf { it > previous }
        }
        val squat = best(CompetitionMovementType.SQUAT,current?.squat1RM,current?.squatE1RM)
        val bench = best(CompetitionMovementType.BENCH,current?.bench1RM,current?.benchE1RM)
        val deadlift = best(CompetitionMovementType.DEADLIFT,current?.deadlift1RM,current?.deadliftE1RM)
        if (squat == null && bench == null && deadlift == null) return null
        val updated = TrainingMaxMerge.merge(current,PowerliftingProfile(squat1RM = squat,bench1RM = bench,deadlift1RM = deadlift),trainingMaxPercent)
        val summary = listOfNotNull(squat?.let { "Sentadilla: $it kg" },bench?.let { "Banca: $it kg" },deadlift?.let { "Peso muerto: $it kg" }).joinToString(" · ")
        return MeetResultProposal(updated,"Marcas válidas registradas: $summary. Confirma antes de actualizar el perfil; los resultados originales se conservan.")
    }
}
