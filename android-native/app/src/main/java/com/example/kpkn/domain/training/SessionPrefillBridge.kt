package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.templates.SuggestionPrefs

/**
 * Bridges program creation to executable weekly sessions.  Selection is made
 * per week, not once per program: week override > block override > global >
 * protocol/default track.  This keeps an advanced plan from receiving the same
 * split/content in every block by accident.
 */
object SessionPrefillBridge {

    fun resolveDefaultSplitId(trackLabel: String?): String = when (trackLabel) {
        "Powerlifting" -> "pl_sbd_x3"
        "Powerbuilding" -> "ppl_ul"
        "Culturismo" -> "ppl_x6"
        else -> "ul_x4"
    }

    fun resolveSplit(
        program: Program,
        protocolDefaultSplitId: String? = null,
        fallbackTrackLabel: String? = null,
    ): SplitTemplate? = resolveSplitId(
        program = program,
        rawSplitId = protocolDefaultSplitId
            ?: program.selectedSplitId
            ?: resolveDefaultSplitId(fallbackTrackLabel),
    )

    /**
     * Fills only structurally empty weeks.  A non-empty session list can be an
     * intentional editor draft (including a session that has not received its
     * first exercise yet), so treating "no executable content" as blank would
     * overwrite user work.  The advanced validator owns the incomplete-draft
     * signal; the split prefill must remain lossless.
     */
    fun prefillEmptyWeeks(
        program: Program,
        split: SplitTemplate?,
        templates: List<SessionTemplate> = SESSION_TEMPLATES_SYSTEM,
    ): Program {
        val defaultSplit = split ?: resolveSplit(program) ?: return program
        val startDay = program.resolvedSchedulePlan().weekStartDay ?: program.startDay ?: 1
        var changed = false
        val rebuilt = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (!weekNeedsPrefill(week)) return@map week
                                        val effectiveSplit = resolveSplitId(
                                            program = program,
                                            rawSplitId = program.weekSplitSelections[week.id]
                                                ?: program.blockSplitSelections[block.id]
                                                ?: defaultSplit.id,
                                        ) ?: return@map week
                                        val preview = SplitApplicationEngine.prebuiltWeekPreview(
                                            split = effectiveSplit,
                                            templates = templates,
                                            prefs = SuggestionPrefs(preferredDifficulty = effectiveSplit.difficulty),
                                        )
                                        // No published recipe means no prefill. The advanced
                                        // validator will report the incomplete block instead of
                                        // silently persisting blank "generated" sessions.
                                        if (preview.days.isEmpty() || preview.days.any { !it.isAvailable }) return@map week
                                        changed = true
                                        week.copy(
                                            sessions = SplitApplicationEngine.buildSessionsForSplit(
                                                splitId = effectiveSplit.id,
                                                pattern = effectiveSplit.pattern,
                                                sessionDescriptions = effectiveSplit.sessionDescriptions,
                                                startDay = startDay,
                                                existingSessions = emptyList(),
                                                migrationMode = SessionMigrationMode.PREBUILT,
                                                prefs = SuggestionPrefs(preferredDifficulty = effectiveSplit.difficulty),
                                                templates = templates,
                                            ),
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        if (!changed) return program
        val days = rebuilt.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }
            .mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }.toSet()
        val scheduled = rebuilt.copy(
            schedulePlan = rebuilt.resolvedSchedulePlan().copy(
                weekStartDay = startDay,
                trainingDays = days,
            ),
        )
        return if (ProgramCalendarEngine.isCalendarized(scheduled)) ProgramCalendarEngine.materializeWeekDates(scheduled) else scheduled
    }

    fun prefillIfEmpty(
        program: Program,
        split: SplitTemplate?,
        templates: List<SessionTemplate> = SESSION_TEMPLATES_SYSTEM,
    ): Program = prefillEmptyWeeks(program, split, templates)

    private fun weekNeedsPrefill(week: ProgramWeek): Boolean =
        week.sessions.isEmpty() || week.sessions.all { it.origin == SessionOrigin.GENERATED_PLACEHOLDER }

    private fun resolveSplitId(program: Program, rawSplitId: String?): SplitTemplate? {
        val id = rawSplitId?.takeIf { it.isNotBlank() } ?: return null
        if (id != "custom") {
            val resolvedId = runCatching { ProgramProtocolEngine.resolveSplitId(id) }.getOrNull() ?: return null
            return SPLIT_TEMPLATES.firstOrNull { it.id == resolvedId }
                ?.takeIf { it.isVisibleForApplication }
        }
        val pattern = program.customSplitPattern
        if (pattern.size != 7 || pattern.all { it.equals("Descanso", ignoreCase = true) }) return null
        return SplitTemplate(
            id = "custom",
            name = program.customSplitName ?: "Mi split",
            description = program.customSplitDescription.orEmpty(),
            tags = listOf(SplitTag.PERSONALIZADO),
            pattern = pattern,
            difficulty = Difficulty.INTERMEDIO,
        )
    }
}
