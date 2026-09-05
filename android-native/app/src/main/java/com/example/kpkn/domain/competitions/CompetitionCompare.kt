package com.example.kpkn.domain.competitions

import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import java.time.LocalDate

data class CompetitionComparePoint(
    val recordId: String,
    val date: LocalDate?,
    val title: String,
    val venue: String?,
    val federationId: String?,
    val federationLabel: String?,
    val totalKg: Double?,
    val squatKg: Double?,
    val benchKg: Double?,
    val deadliftKg: Double?,
    val points: Double?,
    val pointsLabel: String,
    val place: Int?,
    val medal: String?,
    val trophyId: String?,
)

object CompetitionCompare {
    fun series(records: List<CompetitionRecord>): List<CompetitionComparePoint> =
        records
            .filter { it.status != CompetitionRecordStatus.ARCHIVED }
            .map { toPoint(it) }
            .sortedWith(compareBy<CompetitionComparePoint> { it.date ?: LocalDate.MAX }.thenBy { it.title })

    fun pair(records: List<CompetitionRecord>, firstId: String, secondId: String): Pair<CompetitionComparePoint, CompetitionComparePoint>? {
        val byId = series(records).associateBy { it.recordId }
        val a = byId[firstId] ?: return null
        val b = byId[secondId] ?: return null
        return a to b
    }

    fun toPoint(record: CompetitionRecord): CompetitionComparePoint {
        val scored = CompetitionScoring.recalculate(record)
        val blocks = scored.technicalBlocks
        val points = CompetitionScoring.displayedPoints(scored)
        val fed = PowerliftingFederationCatalog.byId(scored.federationId)
        return CompetitionComparePoint(
            recordId = scored.id,
            date = scored.eventDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            title = scored.title,
            venue = scored.location,
            federationId = scored.federationId,
            federationLabel = fed?.shortName ?: scored.federation,
            totalKg = scored.powerliftingDetails?.totalKg ?: CompetitionScoring.totalKg(blocks),
            squatKg = bestOf(blocks, CompetitionMovementType.SQUAT),
            benchKg = bestOf(blocks, CompetitionMovementType.BENCH),
            deadliftKg = bestOf(blocks, CompetitionMovementType.DEADLIFT),
            points = points?.value,
            pointsLabel = points?.label ?: CompetitionScoring.formulaFor(scored).label,
            place = CompetitionPlaceHonors.parsePlace(scored.placement),
            medal = scored.medal,
            trophyId = scored.trophyId,
        )
    }

    private fun bestOf(
        blocks: List<com.example.kpkn.data.models.CompetitionTechnicalBlock>,
        type: CompetitionMovementType,
    ): Double? = blocks.firstOrNull { it.movementType == type }?.let(CompetitionScoring::bestValidKg)
}
