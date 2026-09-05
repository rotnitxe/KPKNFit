package com.example.kpkn.domain.competitions

import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.domain.calculations.IpfEquipment
import com.example.kpkn.domain.calculations.calculateDotsPoints
import com.example.kpkn.domain.calculations.calculateIPFGLPoints
import com.example.kpkn.domain.calculations.calculateWilksPoints

data class CompetitionPoints(
    val formula: PowerliftingPointsFormula,
    val value: Double,
    val label: String = formula.label,
)

object CompetitionScoring {
    private val sbdTypes = setOf(
        CompetitionMovementType.SQUAT,
        CompetitionMovementType.BENCH,
        CompetitionMovementType.DEADLIFT,
    )

    fun formulaFor(record: CompetitionRecord): PowerliftingPointsFormula {
        val stored = PowerliftingPointsFormula.fromId(record.powerliftingDetails?.pointsFormula)
        if (stored != null) return stored
        val fromFed = PowerliftingFederationCatalog.byId(record.federationId)?.pointsFormula
        return PowerliftingPointsFormula.fromId(fromFed) ?: PowerliftingPointsFormula.DOTS
    }

    fun bestValidKg(block: CompetitionTechnicalBlock): Double? =
        block.attempts
            .filter { it.resultType == CompetitionAttemptResult.GOOD_LIFT }
            .mapNotNull { it.weightKg }
            .maxOrNull()
            ?: block.bestValidWeightKg

    fun withBestValids(blocks: List<CompetitionTechnicalBlock>): List<CompetitionTechnicalBlock> =
        blocks.map { it.copy(bestValidWeightKg = bestValidKg(it)) }

    fun totalKg(blocks: List<CompetitionTechnicalBlock>): Double? {
        val total = blocks
            .filter { it.movementType in sbdTypes }
            .sumOf { bestValidKg(it) ?: 0.0 }
        return total.takeIf { it > 0.0 }
    }

    fun scoringSex(record: CompetitionRecord): String {
        val raw = record.powerliftingDetails?.sexCategory.orEmpty()
        return if (raw.contains("fem", ignoreCase = true)) "female" else "male"
    }

    fun ipfEquipment(record: CompetitionRecord): IpfEquipment =
        if (record.powerliftingDetails?.equipment == CompetitionEquipment.EQUIPPED) {
            IpfEquipment.EQUIPPED
        } else {
            IpfEquipment.CLASSIC
        }

    fun calculatePoints(
        totalKg: Double?,
        bodyweightKg: Double?,
        sex: String,
        formula: PowerliftingPointsFormula,
        equipment: IpfEquipment,
    ): CompetitionPoints? {
        if (totalKg == null || totalKg <= 0.0 || bodyweightKg == null || bodyweightKg <= 0.0) return null
        val value = when (formula) {
            PowerliftingPointsFormula.IPF_GL -> calculateIPFGLPoints(
                totalLifted = totalKg,
                bodyWeight = bodyweightKg,
                gender = sex,
                equipment = equipment,
            )
            PowerliftingPointsFormula.DOTS -> calculateDotsPoints(
                totalLifted = totalKg,
                bodyWeight = bodyweightKg,
                gender = sex,
            )
            PowerliftingPointsFormula.WILKS -> calculateWilksPoints(
                totalLifted = totalKg,
                bodyWeight = bodyweightKg,
                gender = sex,
            )
        }
        return CompetitionPoints(formula = formula, value = value).takeIf { value > 0.0 }
    }

    fun displayedPoints(record: CompetitionRecord): CompetitionPoints? {
        val formula = formulaFor(record)
        val details = record.powerliftingDetails
        val stored = when (formula) {
            PowerliftingPointsFormula.IPF_GL -> details?.ipfGlPoints
            PowerliftingPointsFormula.DOTS -> details?.dotsPoints
            PowerliftingPointsFormula.WILKS -> details?.wilksPoints
        }
        if (stored != null && stored > 0.0) {
            return CompetitionPoints(formula, stored)
        }
        return calculatePoints(
            totalKg = details?.totalKg ?: totalKg(record.technicalBlocks),
            bodyweightKg = record.bodyweightKg,
            sex = scoringSex(record),
            formula = formula,
            equipment = ipfEquipment(record),
        )
    }

    fun recalculate(record: CompetitionRecord): CompetitionRecord {
        if (record.sportType != CompetitionTemplateType.POWERLIFTING) return record
        val blocks = withBestValids(record.technicalBlocks)
        val total = totalKg(blocks)
        val formula = formulaFor(record.copy(technicalBlocks = blocks))
        val points = calculatePoints(
            totalKg = total,
            bodyweightKg = record.bodyweightKg,
            sex = scoringSex(record),
            formula = formula,
            equipment = ipfEquipment(record),
        )
        val details = (record.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(
            totalKg = total,
            pointsFormula = formula.id,
            ipfGlPoints = points?.value.takeIf { formula == PowerliftingPointsFormula.IPF_GL },
            dotsPoints = points?.value.takeIf { formula == PowerliftingPointsFormula.DOTS },
            wilksPoints = points?.value.takeIf { formula == PowerliftingPointsFormula.WILKS },
        )
        val summary = buildString {
            if (total != null) append("${formatKg(total)} kg")
            if (points != null) {
                if (isNotEmpty()) append(" · ")
                append("${points.label} ${formatPoints(points.value)}")
            }
        }.ifBlank { null }
        return record.copy(
            technicalBlocks = blocks,
            powerliftingDetails = details,
            resultSummary = summary,
        )
    }

    fun formatKg(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)

    fun formatPoints(value: Double): String = String.format("%.2f", value)
}
