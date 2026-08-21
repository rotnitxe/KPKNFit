package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.SplitPublicationStatus
import com.example.kpkn.domain.templates.SessionTemplateSuggestionEngine
import com.example.kpkn.domain.templates.SuggestionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitApplicationEngineTest {

    private val upperLower = SplitTemplate(
        id = "test_ul",
        name = "Upper Lower",
        pattern = listOf("Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso"),
        description = "Test",
        publicationStatus = SplitPublicationStatus.KPKN_NATIVE,
    )

    private fun programWithWeeks(weeks: List<ProgramWeek>) = Program(
        id = "program",
        name = "Program",
        startDay = 1,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Block",
                        mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = weeks)),
                    )
                ),
            )
        ),
    )

    @Test
    fun migrate_without_muscle_data_distributes_sessions_in_split_order() {
        val sessions = listOf(
            Session(id = "s1", name = "A"),
            Session(id = "s2", name = "B"),
            Session(id = "s3", name = "C"),
        )

        val migrated = SplitApplicationEngine.buildSessionsForSplit(
            pattern = upperLower.pattern,
            startDay = 1,
            existingSessions = sessions,
            migrationMode = SessionMigrationMode.MIGRATE,
        )

        assertEquals(listOf(1, 2, 4), migrated.take(3).map { it.dayOfWeek })
        assertTrue(migrated.any { it.dayOfWeek == 5 })
    }

    @Test
    fun migrate_x6_to_x4_preserves_all_sessions_and_reports_impact_contract() {
        val sixSessions = (1..6).map { index ->
            Session(id = "s$index", name = "Día $index", dayOfWeek = index)
        }
        val target = SPLIT_TEMPLATES.first { it.id == "ul_x4" }

        val migrated = SplitApplicationEngine.buildSessionsForSplit(
            splitId = target.id,
            pattern = target.pattern,
            startDay = 1,
            existingSessions = sixSessions,
            migrationMode = SessionMigrationMode.MIGRATE,
        )

        // MIGRATE is a lossless operation: four target days may host six
        // retained sessions. It is not advertised as a one-to-one match.
        assertEquals(6, migrated.size)
        assertTrue(migrated.map { it.id }.containsAll(sixSessions.map { it.id }))
        assertTrue(migrated.mapNotNull { it.dayOfWeek }.toSet().size <= 4)
        val impact = SplitApplicationEngine.impactSummary(
            SplitApplicationRequest(
                program = programWithWeeks(listOf(ProgramWeek("w1", "W1", sessions = sixSessions))),
                selectedSplit = target,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                migrationMode = SessionMigrationMode.MIGRATE,
            ),
        )
        assertTrue(impact.migrationNote?.contains("6") == true)
    }

    @Test
    fun clean_mode_reports_destructive_impact_before_replacing_sessions() {
        val program = programWithWeeks(
            listOf(
                ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A"), Session("s2", "B"))),
                ProgramWeek("w2", "W2", sessions = listOf(Session("s3", "C"))),
            )
        )

        val impact = SplitApplicationEngine.impactSummary(
            SplitApplicationRequest(
                program = program,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_BLOCK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(2, impact.affectedWeeks)
        assertEquals(3, impact.affectedSessions)
        assertTrue(impact.willReplaceSessions)
    }

    @Test
    fun copied_sessions_receive_new_ids_and_keep_day_assignment() {
        val copied = SplitApplicationEngine.copySessionsWithNewIds(
            listOf(Session(id = "s1", name = "Pierna", dayOfWeek = 1, assignedDays = listOf(1)))
        )

        assertEquals(1, copied.size)
        assertNotEquals("s1", copied.first().id)
        assertEquals(1, copied.first().dayOfWeek)
        assertEquals(listOf(1), copied.first().assignedDays)
    }

    @Test
    fun start_day_change_can_shift_sessions_only_from_selected_week() {
        val program = programWithWeeks(
            listOf(
                ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A", dayOfWeek = 1))),
                ProgramWeek("w2", "W2", sessions = listOf(Session("s2", "B", dayOfWeek = 1))),
            )
        )

        val updated = SplitApplicationEngine.applyStartDayChange(
            program = program,
            selectedWeekId = "w2",
            newStartDay = 6,
            temporalScope = StartDayTemporalScope.FROM_SELECTED_WEEK,
            sessionMode = StartDaySessionMode.KEEP_SPLIT_ORDER,
        )

        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(6, updated.startDay)
        assertEquals(1, weeks[0].sessions.first().dayOfWeek)
        assertEquals(6, weeks[1].sessions.first().dayOfWeek)
    }

    @Test
    fun start_day_change_can_keep_session_days() {
        val program = programWithWeeks(listOf(ProgramWeek("w1", "W1", sessions = listOf(Session("s1", "A", dayOfWeek = 1)))))

        val updated = SplitApplicationEngine.applyStartDayChange(
            program = program,
            selectedWeekId = "w1",
            newStartDay = 6,
            temporalScope = StartDayTemporalScope.ALL_WEEKS,
            sessionMode = StartDaySessionMode.KEEP_DAYS,
        )

        assertEquals(6, updated.startDay)
        assertEquals(1, updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.first().dayOfWeek)
    }

    @Test
    fun start_day_change_reconciles_schedule_plan_and_calendar_dates_for_keep_modes() {
        val week = ProgramWeek(
            id = "dated-week",
            name = "Semana fechada",
            sessions = listOf(
                Session("dated-s1", "A", dayOfWeek = 1, assignedDays = listOf(1)),
                Session("dated-s2", "B", dayOfWeek = 3, assignedDays = listOf(3)),
            ),
        )
        val base = programWithWeeks(listOf(week)).copy(
            startDay = 1,
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            timelineStartDate = "2026-08-03",
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-08-03",
                weekStartDay = 1,
                trainingDays = setOf(1, 3),
                mode = ScheduleMode.DATED,
            ),
        )

        val keepOrder = SplitApplicationEngine.applyStartDayChange(
            program = base,
            selectedWeekId = week.id,
            newStartDay = 5,
            temporalScope = StartDayTemporalScope.ALL_WEEKS,
            sessionMode = StartDaySessionMode.KEEP_SPLIT_ORDER,
        )
        assertEquals(5, keepOrder.resolvedSchedulePlan().weekStartDay)
        assertEquals(setOf(5, 7), keepOrder.resolvedSchedulePlan().trainingDays)
        val orderWeek = keepOrder.macrocycles[0].blocks[0].mesocycles[0].weeks.single()
        assertEquals(listOf(5, 7), orderWeek.sessions.mapNotNull { it.dayOfWeek })
        assertEquals(setOf(5, 7), orderWeek.trainingDayDates.keys)
        assertTrue(orderWeek.startDate != null && orderWeek.endDate != null)

        val keepDays = SplitApplicationEngine.applyStartDayChange(
            program = base,
            selectedWeekId = week.id,
            newStartDay = 5,
            temporalScope = StartDayTemporalScope.ALL_WEEKS,
            sessionMode = StartDaySessionMode.KEEP_DAYS,
        )
        assertEquals(5, keepDays.resolvedSchedulePlan().weekStartDay)
        assertEquals(setOf(1, 3), keepDays.resolvedSchedulePlan().trainingDays)
        val daysWeek = keepDays.macrocycles[0].blocks[0].mesocycles[0].weeks.single()
        assertEquals(listOf(1, 3), daysWeek.sessions.mapNotNull { it.dayOfWeek })
        assertEquals(setOf(1, 3), daysWeek.trainingDayDates.keys)
        assertTrue(daysWeek.startDate != null && daysWeek.endDate != null)
    }

    @Test
    fun partial_start_day_change_materializes_exact_days_per_week_not_global_union() {
        fun executable(id: String, day: Int) = Session(
            id = id,
            name = id,
            dayOfWeek = day,
            assignedDays = listOf(day),
            exercises = listOf(
                Exercise(
                    id = "$id-exercise",
                    name = "Trabajo $id",
                    sets = listOf(ExerciseSet("$id-set", targetReps = 5)),
                ),
            ),
        )
        val base = programWithWeeks(
            listOf(
                ProgramWeek("w1", "W1", sessions = listOf(executable("w1-a", 1), executable("w1-b", 3))),
                ProgramWeek("w2", "W2", sessions = listOf(executable("w2-a", 1), executable("w2-b", 3))),
            ),
        ).copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            timelineStartDate = "2026-08-03",
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-08-03",
                weekStartDay = 1,
                trainingDays = setOf(1, 3),
                mode = ScheduleMode.DATED,
            ),
        )

        val updated = SplitApplicationEngine.applyStartDayChange(
            program = base,
            selectedWeekId = "w2",
            newStartDay = 5,
            temporalScope = StartDayTemporalScope.FROM_SELECTED_WEEK,
            sessionMode = StartDaySessionMode.KEEP_SPLIT_ORDER,
        )
        val weeks = updated.macrocycles.single().blocks.single().mesocycles.single().weeks

        assertEquals(listOf(1, 3), weeks[0].sessions.mapNotNull { it.dayOfWeek })
        assertEquals(setOf(1, 3), weeks[0].trainingDayDates.keys)
        assertEquals(listOf(5, 7), weeks[1].sessions.mapNotNull { it.dayOfWeek })
        assertEquals(setOf(5, 7), weeks[1].trainingDayDates.keys)
        // The global plan remains a truthful union for selectors, while each
        // calendar projection is scoped to the sessions in its own week.
        assertEquals(setOf(1, 3, 5, 7), updated.resolvedSchedulePlan().trainingDays)
    }

    @Test
    fun prebuilt_mode_uses_matching_templates_and_clones_repeated_days() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }

        val sessions = SplitApplicationEngine.buildSessionsForSplit(
            splitId = split.id,
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            exerciseIndex = emptyMap(),
        )

        assertEquals(4, sessions.size)
        assertTrue(sessions.all { session ->
            session.exercises.isNotEmpty() || session.parts.any { it.exercises.isNotEmpty() }
        })
        val torsoSessions = sessions.filter { it.scheduleLabel == "Torso" }
        val firstIds = torsoSessions[0].exercises.map { it.id } + torsoSessions[0].parts.flatMap { part -> part.exercises.map { it.id } }
        val secondIds = torsoSessions[1].exercises.map { it.id } + torsoSessions[1].parts.flatMap { part -> part.exercises.map { it.id } }
        assertTrue(firstIds.intersect(secondIds.toSet()).isEmpty())
    }

    @Test
    fun prebuilt_ul_x4_uses_suggestion_not_firstOrNull() {
        val split = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            exerciseIndex = emptyMap(),
            prefs = SuggestionPrefs(preferredDifficulty = split.difficulty),
        )
        val legPlans = plan.days.filter { it.dayLabel.equals("Pierna", ignoreCase = true) }
        assertEquals(2, legPlans.size)
        assertNotEquals(legPlans[0].template?.id, legPlans[1].template?.id)

        val sessions = SplitApplicationEngine.buildSessionsForSplit(
            splitId = split.id,
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            exerciseIndex = emptyMap(),
            prefs = SuggestionPrefs(preferredDifficulty = split.difficulty),
        )
        val legSessions = sessions.filter { it.scheduleLabel.equals("Pierna", ignoreCase = true) }
        assertEquals(2, legSessions.size)
        fun firstExerciseName(session: Session): String? {
            session.exercises.firstOrNull()?.name?.let { return it }
            return session.parts.firstOrNull()?.exercises?.firstOrNull()?.name
        }
        val names = legSessions.map { firstExerciseName(it) }
        val fingerprints = legSessions.map { session ->
            (session.exercises.map { it.exerciseDbId ?: it.name } +
                session.parts.flatMap { part -> part.exercises.map { it.exerciseDbId ?: it.name } })
                .joinToString("|")
        }
        assertTrue(
            "Los dos días Pierna deberían diferir en contenido o en plantilla sugerida; names=$names fingerprints=$fingerprints",
            fingerprints[0] != fingerprints[1] || names[0] != names[1] ||
                legPlans[0].template?.id != legPlans[1].template?.id,
        )
    }

    @Test
    fun fullbody_x3_diversifies_same_archetype_days_with_real_catalog() {
        val split = SPLIT_TEMPLATES.first { it.id == "fullbody_x3" }
        val plan = SessionTemplateSuggestionEngine.suggestWeek(
            split = split,
            templates = SESSION_TEMPLATES_SYSTEM,
        )
        val selected = plan.days.mapNotNull { it.template?.id }
        assertEquals(3, selected.size)
        assertTrue("Full Body x3 no debe repetir una única receta: $selected", selected.distinct().size >= 2)
    }

    @Test
    fun prebuilt_missing_day_label_returns_blank_session_and_visible_warning() {
        val split = SplitTemplate(
            id = "missing-day-test",
            name = "Split con día sin plantilla",
            description = "Test",
            pattern = listOf("Día sin catálogo", "Descanso", "Descanso", "Descanso", "Descanso", "Descanso", "Descanso"),
        )

        val preview = SplitApplicationEngine.prebuiltWeekPreview(
            split = split,
            templates = emptyList(),
            exerciseIndex = emptyMap(),
        )
        val sessions = SplitApplicationEngine.buildSessionsForSplit(
            splitId = split.id,
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            templates = emptyList(),
            exerciseIndex = emptyMap(),
        )

        assertEquals(1, preview.days.size)
        assertTrue(preview.days.single().templateId == null)
        assertTrue(preview.warnings.any { it.contains("Día sin catálogo") })
        assertEquals(1, sessions.size)
        assertTrue(sessions.single().exercises.isEmpty())
        assertTrue(sessions.single().parts.isEmpty())
    }

    @Test
    fun custom_preview_and_application_use_the_same_day_recipe() {
        val split = SplitTemplate(
            id = "custom",
            name = "Mi split",
            description = "",
            pattern = listOf("Pecho", "Descanso", "Pierna", "Descanso", "Descanso", "Descanso", "Descanso"),
        )
        val chest = template("chest", "Pecho", SessionTemplateFocusCategory.PECHO)
        val leg = template("leg", "Pierna", SessionTemplateFocusCategory.PIERNAS)
        val templates = listOf(chest, leg)
        val preview = SplitApplicationEngine.prebuiltWeekPreview(
            split = split,
            templates = templates,
            exerciseIndex = emptyMap(),
        )
        val applied = SplitApplicationEngine.buildSessionsForSplit(
            splitId = "custom",
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            templates = templates,
            exerciseIndex = emptyMap(),
        )

        assertEquals(listOf("chest", "leg"), preview.days.mapNotNull { it.templateId })
        assertEquals(listOf("Pecho", "Pierna"), applied.map { it.scheduleLabel })
        assertEquals(
            listOf("Pecho fixture", "Pierna fixture"),
            applied.map { it.allExercises().single().name },
        )
    }

    @Test
    fun optInUserGenerationCatalog_isUsedByPreviewAndApply_identically() {
        val split = SplitTemplate(
            id = "custom",
            name = "Mi split",
            description = "",
            pattern = listOf("Pecho", "Descanso", "Descanso", "Descanso", "Descanso", "Descanso", "Descanso"),
        )
        val eligible = template("user-opt-in", "Pecho", SessionTemplateFocusCategory.PECHO).copy(
            sourceType = SessionTemplateSourceType.USER,
            splitIds = listOf("custom"),
            autoGenerationEligible = true,
        )
        val excluded = eligible.copy(id = "user-no-opt-in", autoGenerationEligible = false)
        val generationCatalog = listOf(eligible)
        val preview = SplitApplicationEngine.prebuiltWeekPreview(
            split = split,
            templates = generationCatalog,
            exerciseIndex = emptyMap(),
        )
        val applied = SplitApplicationEngine.buildSessionsForSplit(
            splitId = "custom",
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            templates = generationCatalog,
            exerciseIndex = emptyMap(),
        )
        assertEquals(listOf("user-opt-in"), preview.days.mapNotNull { it.templateId })
        assertEquals("Pecho fixture", applied.single().allExercises().single().name)
        assertFalse(listOf(excluded).any { it.id in preview.days.mapNotNull { day -> day.templateId } })
    }

    @Test
    fun custom_all_rest_pattern_is_rejected_instead_of_succeeding_empty() {
        val error = runCatching {
            SplitApplicationEngine.buildSessionsForSplit(
                splitId = "custom",
                pattern = List(7) { "Descanso" },
                startDay = 1,
                existingSessions = emptyList(),
                migrationMode = SessionMigrationMode.PREBUILT,
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("al menos un día") == true)
    }

    @Test
    fun prebuilt_specialized_without_exact_recipe_is_rejected_before_mutation() {
        val texas = SPLIT_TEMPLATES.first { it.id == "texas_method" }
        val program = programWithWeeks(listOf(ProgramWeek("w1", "Semana 1")))
        val request = SplitApplicationRequest(
            program = program,
            selectedSplit = texas,
            selectedBlockId = "block",
            selectedWeekId = "w1",
            startDay = 1,
            temporalScope = SplitTemporalScope.CURRENT_WEEK,
            migrationMode = SessionMigrationMode.PREBUILT,
        )

        val reasons = SplitApplicationEngine.prebuiltUnavailabilityReasons(request)
        assertTrue("Texas sin receta exacta debe quedar bloqueado", reasons.isNotEmpty())
        val error = runCatching { SplitApplicationEngine.apply(request) }.exceptionOrNull()
        assertTrue("El gate PREBUILT debe rechazar la publicación", error is IllegalArgumentException)
        assertTrue(error?.message?.contains("No se puede generar el split") == true)
    }

    @Test
    fun current_week_records_override_without_destroying_global_or_block_selection() {
        val base = programWithWeeks(listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))).copy(
            selectedSplitId = "global_split",
            blockSplitSelections = mapOf("block" to "block_split"),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = base,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals("global_split", updated.selectedSplitId)
        assertEquals("block_split", updated.blockSplitSelections["block"])
        assertEquals(upperLower.id, updated.weekSplitSelections["w1"])
        assertFalse(updated.weekSplitSelections.containsKey("w2"))
    }

    @Test
    fun whole_program_replaces_global_selection_and_clears_narrower_overrides() {
        val base = programWithWeeks(listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))).copy(
            selectedSplitId = "old",
            blockSplitSelections = mapOf("block" to "old_block"),
            weekSplitSelections = mapOf("w1" to "old_week"),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = base,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.WHOLE_PROGRAM,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(upperLower.id, updated.selectedSplitId)
        assertTrue(updated.blockSplitSelections.isEmpty())
        assertTrue(updated.weekSplitSelections.isEmpty())
    }

    @Test
    fun per_block_mode_applies_every_week_in_each_configured_block() {
        val ul = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
        val fullBody = SPLIT_TEMPLATES.first { it.id == "fullbody_x3" }
        val program = Program(
            id = "advanced",
            name = "Advanced",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block("b1", "B1", mesocycles = listOf(Mesocycle("m1", "M1", weeks = listOf(ProgramWeek("w1", "W1"), ProgramWeek("w2", "W2"))))),
                        Block("b2", "B2", mesocycles = listOf(Mesocycle("m2", "M2", weeks = listOf(ProgramWeek("w3", "W3"))))),
                    ),
                )
            ),
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = program,
                selectedSplit = ul,
                selectedBlockId = "b1",
                selectedWeekId = "w1",
                startDay = 1,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                advancedMode = AdvancedSplitMode.PER_BLOCK,
                migrationMode = SessionMigrationMode.CLEAN,
                perBlockSelections = mapOf("b1" to ul.id, "b2" to fullBody.id),
            )
        )

        val weeks = SplitApplicationEngine.buildWeekOptions(updated).associateBy { it.id }
        assertEquals(4, weeks.getValue("w1").sessions.size)
        assertEquals(4, weeks.getValue("w2").sessions.size)
        assertEquals(3, weeks.getValue("w3").sessions.size)
        assertEquals(ul.id, updated.blockSplitSelections["b1"])
        assertEquals(fullBody.id, updated.blockSplitSelections["b2"])
    }

    @Test
    fun calendarized_program_keeps_its_existing_week_start_when_applying_split() {
        val calendarized = programWithWeeks(listOf(ProgramWeek("w1", "W1"))).copy(
            startDay = 3,
            timelineStartDate = "2026-07-01",
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
        )

        val updated = SplitApplicationEngine.apply(
            SplitApplicationRequest(
                program = calendarized,
                selectedSplit = upperLower,
                selectedBlockId = "block",
                selectedWeekId = "w1",
                startDay = 6,
                temporalScope = SplitTemporalScope.CURRENT_WEEK,
                migrationMode = SessionMigrationMode.CLEAN,
            )
        )

        assertEquals(3, updated.startDay)
        val days = SplitApplicationEngine.buildWeekOptions(updated).first().sessions.mapNotNull { it.dayOfWeek }
        assertEquals(listOf(3, 4, 6, 7), days)
    }

    private fun template(
        id: String,
        dayLabel: String,
        focus: SessionTemplateFocusCategory,
    ): SessionTemplate = SessionTemplate(
        id = id,
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = id,
        description = "fixture",
        focusCategory = focus,
        publicationStatus = SessionTemplatePublicationStatus.KPKN_NATIVE,
        splitDayLabels = listOf(dayLabel),
        session = Session(
            id = "session-$id",
            name = dayLabel,
            exercises = listOf(Exercise(id = "exercise-$id", name = "$dayLabel fixture")),
        ),
    )
}
