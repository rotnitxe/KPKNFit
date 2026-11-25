package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant

/**
 * Normalizes stable [Session.logicalLineageId] values for programs created outside recipe
 * materialization (Setup Wizard, Program Editor, Importer, cyclic templates, and week cloning)
 * and determines whether future weeks of the same variant actually exist.
 */
object ProgramLineageNormalizer {

    fun ensureSessionLineageIds(program: Program): Program {
        var changed = false
        val macros = program.macrocycles.mapIndexed { macroIdx, macro ->
            val blocks = macro.blocks.mapIndexed { blockIdx, block ->
                val mesos = block.mesocycles.mapIndexed { mesoIdx, meso ->
                    val weeks = meso.weeks.map { week ->
                        val sameDaySlots = mutableMapOf<Int, Int>()
                        val sessions = week.sessions.mapIndexed { sIdx, session ->
                            val dayKey = session.dayOfWeek ?: (1000 + sIdx)
                            val slotInDay = sameDaySlots.getOrDefault(dayKey, 0)
                            sameDaySlots[dayKey] = slotInDay + 1
                            val existing = session.logicalLineageId?.takeIf { it.isNotBlank() }
                            val resolved = existing ?: deriveLineageId(
                                program = program,
                                session = session,
                                macroIdx = macroIdx,
                                blockIdx = blockIdx,
                                variant = week.variant,
                                dayKey = session.dayOfWeek,
                                slotInDay = slotInDay,
                                sessionIdx = sIdx,
                            )
                            if (resolved != session.logicalLineageId) {
                                changed = true
                                session.copy(logicalLineageId = resolved)
                            } else {
                                session
                            }
                        }
                        if (sessions == week.sessions) week else week.copy(sessions = sessions)
                    }
                    if (weeks == meso.weeks) meso else meso.copy(weeks = weeks)
                }
                if (mesos == block.mesocycles) block else block.copy(mesocycles = mesos)
            }
            if (blocks == macro.blocks) macro else macro.copy(blocks = blocks)
        }
        return if (changed) program.copy(macrocycles = macros) else program
    }

    fun hasFutureWeeksOfVariant(
        program: Program,
        sourceWeekOrdinal: Int,
        variant: WeekVariant?,
    ): Boolean {
        var ordinal = 0
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        val currentOrdinal = ordinal++
                        if (currentOrdinal > sourceWeekOrdinal && (week.variant ?: WeekVariant.A) == (variant ?: WeekVariant.A)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun deriveLineageId(
        program: Program,
        session: Session,
        macroIdx: Int,
        blockIdx: Int,
        variant: WeekVariant?,
        dayKey: Int?,
        slotInDay: Int,
        sessionIdx: Int,
    ): String {
        val recipe = program.sourceRecipe
        val recipeDayId = session.allExercises().firstNotNullOfOrNull { it.recipeDayId }
        if (recipe != null && !recipeDayId.isNullOrBlank()) {
            return "recipe:${recipe.id}@${recipe.contentVersion}/day:$recipeDayId"
        }
        val dayToken = dayKey?.toString() ?: "idx$sessionIdx"
        val variantName = (variant ?: WeekVariant.A).name
        return "plan:${program.id}:m$macroIdx:b$blockIdx:v$variantName:d$dayToken:s$slotInDay"
    }
}
