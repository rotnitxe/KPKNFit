package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotPriority
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.WeekRecipe

/** Adapta una sesión materializada a [DayRecipe] para evaluar H1–H10 aislada. */
object SessionToDayRecipe {
    fun fromSession(session: Session, label: String = session.name.ifBlank { "sesión" }): DayRecipe {
        val exercises = session.allExercises()
        val speed = looksLikeSpeed(session.name, label)
        val metadata = CompositionMetadataHolder.resolve()
        data class Classified(
            val index: Int,
            val configurationId: String,
            val isolation: Boolean,
            val finisher: Boolean,
            val hasPercent: Boolean,
        )
        val classified = exercises.mapIndexed { index, exercise ->
            val configurationId = (
                exercise.catalogConfigurationId
                    ?: exercise.exerciseDbId
                    ?: exercise.exerciseId
                    ?: exercise.id
                ).lowercase()
            val meta = metadata.metadata(configurationId)
            val family = CompositionTaxonomy.familyOf(meta?.movementPatternId)
            Classified(
                index = index,
                configurationId = configurationId,
                isolation = CompositionTaxonomy.isIsolation(family, meta?.articulationType, configurationId),
                finisher = CompositionTaxonomy.isFinisherFamily(family),
                hasPercent = exercise.sets.any { it.targetPercentageRM != null },
            )
        }
        val t1Index = classified.indexOfFirst { !it.isolation && !it.finisher }
        val t2Index = classified.indexOfFirst { item ->
            t1Index >= 0 && item.index == t1Index + 1 && !item.isolation && !item.finisher
        }
        val slots = exercises.mapIndexed { index, exercise ->
            val item = classified[index]
            val working = exercise.sets.filter { !it.isEmptySlot }
            val sets = working.map { set ->
                SetRecipe(
                    reps = set.targetReps ?: set.targetRepsRange?.min,
                    repsMin = set.targetRepsRange?.min,
                    repsMax = set.targetRepsRange?.max,
                    percent = set.targetPercentageRM,
                    rpe = set.targetRPE,
                    rir = set.targetRIR,
                    amrap = set.isAmrap,
                    isTopSet = false,
                    loadBasis = when {
                        set.targetPercentageRM != null -> LoadBasis.PERCENT_TM
                        set.targetRIR != null || set.targetRPE != null -> LoadBasis.RPE
                        else -> LoadBasis.RPE
                    },
                )
            }
            val firstIsolationOrFinisher = classified.indexOfFirst { it.isolation || it.finisher }
            val role = when {
                speed && index == 0 -> SlotRole.SPEED
                t1Index >= 0 && index == t1Index -> SlotRole.T1_MAIN
                !item.isolation && !item.finisher && (
                    (t2Index >= 0 && index == t2Index) ||
                        (item.hasPercent && (firstIsolationOrFinisher < 0 || index < firstIsolationOrFinisher))
                    ) -> SlotRole.T2_SUPPLEMENTAL
                else -> SlotRole.T3_ACCESSORY
            }
            val t1Id = exercises.getOrNull(t1Index)?.id
            SlotRecipe(
                id = exercise.id,
                role = role,
                lift = LiftRef(item.configurationId),
                sets = sets.ifEmpty { listOf(SetRecipe(reps = 8, rpe = 8.0, loadBasis = LoadBasis.RPE)) },
                restSeconds = exercise.restTime ?: 90,
                isCompetitionLift = exercise.isCompetitionLift,
                supplementalOf = if (role == SlotRole.T2_SUPPLEMENTAL) t1Id else null,
                priority = if (speed && index == 0) SlotPriority.SPEED else SlotPriority.NORMAL,
            )
        }
        return DayRecipe(
            label = label,
            slots = slots,
            priority = if (speed) SlotPriority.SPEED else SlotPriority.NORMAL,
        )
    }

    fun isolatedWeek(session: Session, goal: BlockGoal = BlockGoal.ACCUMULATION): WeekRecipe {
        val day = fromSession(session)
        return WeekRecipe(
            weekNumber = 1,
            blockIndex = 0,
            blockName = "Catálogo",
            blockGoal = goal,
            kind = WeekExecutionKind.TRAINING,
            days = listOf(day),
        )
    }

    private fun looksLikeSpeed(vararg texts: String): Boolean {
        val blob = texts.joinToString(" ").lowercase()
        return blob.contains("velocidad") || blob.contains("dinámic") || blob.contains("dinamico") ||
            blob.contains("de lower") || blob.contains("explosiv")
    }
}
