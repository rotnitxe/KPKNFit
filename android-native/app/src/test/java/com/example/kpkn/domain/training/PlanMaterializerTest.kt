package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.EffectiveWeekRecipe
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolution
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayArchetypes
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.repeatPercentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class PlanMaterializerTest {
    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun sampleRecipe(): TrainingPlanRecipe {
        val day = DayArchetypes.plSquat(80.0, weekday = 1)
        return TrainingPlanRecipe(
            id = "test-recipe",
            weeks = listOf(
                weekRecipe(1, 0, "Fuerza", BlockGoal.INTENSIFICATION, listOf(day)),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(
                LiftSlot.SQUAT to CatalogIds.SQ_LOW,
                LiftSlot.BENCH to CatalogIds.BP,
                LiftSlot.DEADLIFT to CatalogIds.DL,
            ),
            claimedDaysPerWeek = 4,
        )
    }

    @Test
    fun materialize_copies_sets_reps_percent_and_amrap() {
        val amrapDay = DayArchetypes.plSquat(80.0, t1Amrap = true, weekday = 1)
        val recipe = sampleRecipe().copy(
            weeks = listOf(weekRecipe(1, 0, "Fuerza", BlockGoal.INTENSIFICATION, listOf(amrapDay))),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val t1 = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().allExercises().first { it.catalogConfigurationId == CatalogIds.SQ_LOW }
        assertEquals(4, t1.sets.size)
        assertEquals(4, t1.sets.first().targetReps)
        assertEquals(80.0, t1.sets.first().targetPercentageRM)
        assertTrue(t1.sets.last().isAmrap)
        assertEquals(180.0, t1.reference1RM ?: -1.0, 0.001)
        assertEquals(180.0 * 0.80, t1.sets.first().weight ?: -1.0, 0.001)
        // La receta trae sus propios calentamientos (40/55/65) y el plan manda
        // conservarlos íntegros salvo edición explícita: el preset no se suma.
        assertEquals(listOf(40.0, 55.0, 65.0), t1.warmupSets.map { it.percentageOfWorkingWeight })
    }

    @Test
    fun materialize_does_not_duplicate_exercises_in_loose_list() {
        val recipe = sampleRecipe()
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val session = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first()
        assertTrue(session.exercises.isEmpty())
        assertTrue(session.parts.isNotEmpty())
        val grouped = session.parts.flatMap { it.exercises }
        assertEquals(grouped.size, session.allExercises().size)
        assertEquals(grouped.size, grouped.map { it.id }.distinct().size)
        assertTrue(session.parts.any { it.name == "Principal" })
        assertTrue(session.parts.any { it.name == "Suplementario" })
        assertTrue(session.parts.any { it.name == "Accesorios" })
        assertTrue(grouped.any { it.catalogConfigurationId == CatalogIds.SQ_LOW })
        assertTrue(grouped.any { it.catalogConfigurationId == CatalogIds.BP_PAUSE })
    }

    @Test
    fun materialize_never_mirrors_exercises_loose_and_grouped() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        val recipes = (
            PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.mapNotNull { it.recipe } +
                PROGRAM_TEMPLATES.mapNotNull { it.recipe }
            ).distinctBy { it.id }
        assertTrue("Debe haber recetas para el test de duplicados", recipes.size >= 4)
        recipes.forEach { recipe ->
            val program = PlanMaterializer.materialize(
                Program(id = "p", name = "T"),
                recipe,
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
                profile = profile,
            )
            program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                .flatMap { it.sessions }.forEach { session ->
                    val looseIds = session.exercises.map { it.id }
                    val partIds = session.parts.flatMap { it.exercises }.map { it.id }
                    assertTrue(
                        "${recipe.id}/${session.name}: suelto espejado en grupo: ${looseIds.intersect(partIds.toSet())}",
                        looseIds.intersect(partIds.toSet()).isEmpty(),
                    )
                    assertEquals(
                        "${recipe.id}/${session.name}: ids duplicados en parts",
                        partIds.size,
                        partIds.distinct().size,
                    )
                }
        }
    }

    @Test
    fun materialize_preserves_slot_order_across_role_changes() {
        val recipes = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.mapNotNull { it.recipe }
        recipes.forEach { recipe ->
            val program = PlanMaterializer.materialize(
                Program(id = "order", name = "Order"), recipe,
                CatalogCompositionTestSupport.metadata, SeqIds(),
                profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            )
            val sessions = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
                .flatMap { it.weeks }.flatMap { it.sessions }
            val days = recipe.weeks.sortedWith(compareBy({ it.blockIndex }, { it.weekNumber })).flatMap { it.days }
            assertEquals(recipe.id, days.size, sessions.size)
            days.zip(sessions).forEach { (day, session) ->
                assertEquals(
                    "${recipe.id}/${day.label}",
                    day.slots.map { it.lift.configurationId },
                    session.allExercises().map { it.catalogConfigurationId },
                )
            }
        }
    }

    @Test
    fun rematerializeWeek_does_not_rewrite_executed_weeks() {
        val recipe = sampleRecipe()
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val weekId = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id
        val unchanged = PlanMaterializer.rematerializeWeek(
            program,
            weekId,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            intensityScale = 0.5,
            executedWeekIds = setOf(weekId),
        )
        assertEquals(program.macrocycles, unchanged.macrocycles)
    }

    @Test
    fun materialize_is_deterministic_with_same_id_provider() {
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        val recipes = (
            PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.mapNotNull { it.recipe } +
                PROGRAM_TEMPLATES.mapNotNull { it.recipe }
            ).distinctBy { it.id }
        assertTrue("Debe haber recetas para el test de determinismo", recipes.size >= 4)
        recipes.forEach { recipe ->
            val first = PlanMaterializer.materialize(
                Program(id = "p", name = "T"),
                recipe,
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
                profile = profile,
            )
            val second = PlanMaterializer.materialize(
                Program(id = "p", name = "T"),
                recipe,
                CatalogCompositionTestSupport.metadata,
                SeqIds(),
                profile = profile,
            )
            assertEquals("${recipe.id} bloques", first.macrocycles, second.macrocycles)
            assertEquals("${recipe.id} receta fuente", first.sourceRecipe, second.sourceRecipe)
        }
    }

    @Test
    fun hydrateProfile_uses_program_goals() {
        val profile = PlanMaterializer.hydrateProfile(
            Program(id = "p", name = "T", goals = ProgramGoals(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)),
            profile = null,
            trainingMaxPercent = 0.90,
        )
        assertEquals(180.0, profile?.squatTM ?: -1.0, 0.001)
        assertEquals(108.0, profile?.benchTM ?: -1.0, 0.001)
    }

    @Test
    fun percent_sets_preserve_load_basis() {
        val sets = percentSets(180, 5 to 65.0, 5 to 75.0, 5 to 85.0, amrapLast = true, basis = LoadBasis.PERCENT_TM)
        assertEquals(3, sets.size)
        assertTrue(sets.last().amrap)
        assertEquals(LoadBasis.PERCENT_TM, sets.first().loadBasis)
        val t1 = slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, sets, 240, LiftSlot.SQUAT, isCompetitionLift = true)
        assertEquals(3, t1.sets.size)
        assertEquals(repeatPercentSets(3, 5, 80.0, 180).size, 3)
    }

    @Test
    fun texas_volume_is_90_percent_of_friday_top_and_recovery_is_80_percent_of_monday() {
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "texas-method-3d" }
        val recipe = protocol.recipe!!
        val program = PlanMaterializer.materialize(
            Program(id = "tx", name = "Texas"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks[1]
        val byName = week.sessions.associateBy { it.name }
        fun squatOf(sessionName: String) = byName.getValue(sessionName).allExercises().first {
            it.catalogConfigurationId == CatalogIds.SQ_LOW
        }
        val monday = squatOf("Volumen 5x5")
        val wednesday = squatOf("Recuperación")
        val friday = squatOf("Intensidad PR")
        // D7: el TM de Texas es el 87 % del 1RM (≈ 5RM). Con 1RM 200 kg el TM es 174 kg.
        val tm = 200.0 * 0.87
        assertEquals(174.0, tm, 0.01)
        assertEquals(174.0, friday.sets.first().weight ?: -1.0, 0.01)
        assertEquals(156.6, monday.sets.first().weight ?: -1.0, 0.01)
        assertEquals(125.28, wednesday.sets.first().weight ?: -1.0, 0.01)
        assertEquals(tm * 0.90, monday.sets.first().weight ?: -1.0, 0.01)
        assertEquals(tm * 0.72, wednesday.sets.first().weight ?: -1.0, 0.01)
    }

    @Test
    fun madcow_week4_resolves_against_the_5rm_tm_and_never_exceeds_the_friday_triple() {
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "madcow-5x5" }
        val program = PlanMaterializer.materialize(
            Program(id = "mc", name = "Madcow"),
            protocol.recipe!!,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks[3]
        val byName = week.sessions.associateBy { it.name }
        fun squatOf(sessionName: String) = byName.getValue(sessionName).allExercises().first {
            it.catalogConfigurationId == CatalogIds.SQ_LOW
        }
        val monday = squatOf("Volumen")
        val friday = squatOf("Intensidad")
        // D7: TM al 87 % del 1RM → 174 kg. El 5.º set del lunes es el 100 % del TM y el triple del viernes el 102,5 %.
        assertEquals(174.0, monday.sets[4].weight ?: -1.0, 0.01)
        assertEquals(178.35, friday.sets[4].weight ?: -1.0, 0.01)
        // La rampa del lunes sube de 87 a 174 kg sin escalar dos veces.
        assertEquals(
            listOf(87.0, 108.75, 130.5, 152.25, 174.0),
            monday.sets.map { it.weight ?: -1.0 }.map { Math.round(it * 100) / 100.0 },
        )
        // Ningún set de la semana supera el triple del viernes (174 × 1,025).
        val heaviest = week.sessions.flatMap { it.allExercises() }.flatMap { it.sets }.mapNotNull { it.weight }.maxOrNull() ?: -1.0
        assertTrue("el set más pesado de la semana es $heaviest kg", heaviest <= 174.0 * 1.025 + 0.01)
    }

    @Test
    fun materialize_persists_optional_slot_role_top_set_and_load_basis() {
        val recipe = TrainingPlanRecipe(
            id = "opt-fields",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.INTENSIFICATION,
                    listOf(
                        com.example.kpkn.data.protocols.DayRecipe(
                            label = "Dia",
                            slots = listOf(
                                slot(
                                    "t1",
                                    SlotRole.T1_MAIN,
                                    CatalogIds.SQ_LOW,
                                    listOf(
                                        com.example.kpkn.data.protocols.SetRecipe(
                                            reps = 5,
                                            percent = 75.0,
                                            isTopSet = true,
                                            loadBasis = LoadBasis.PERCENT_TM,
                                        ),
                                    ),
                                    180,
                                    LiftSlot.SQUAT,
                                    technique = com.example.kpkn.data.protocols.TechniqueModifier.PAUSE_2S,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "Opt"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )
        val exercise = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().allExercises().first()
        assertEquals(SlotRole.T1_MAIN, exercise.slotRole)
        assertEquals(com.example.kpkn.data.protocols.TechniqueModifier.PAUSE_2S, exercise.techniqueModifier)
        assertTrue(exercise.sets.first().isTopSet)
        assertEquals(LoadBasis.PERCENT_TM, exercise.sets.first().loadBasis)
    }

    @Test
    fun materialize_uses_verbatim_catalog_name_with_technique_as_chip() {
        val derivedIndex = com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
            .buildDisplayNameIndex(CatalogCompositionTestSupport.catalog)
        val recipe = TrainingPlanRecipe(
            id = "names",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.INTENSIFICATION,
                    listOf(
                        com.example.kpkn.data.protocols.DayRecipe(
                            label = "Dia",
                            slots = listOf(
                                slot(
                                    "t1",
                                    SlotRole.T1_MAIN,
                                    CatalogIds.BP,
                                    listOf(
                                        com.example.kpkn.data.protocols.SetRecipe(
                                            reps = 5,
                                            percent = 75.0,
                                            loadBasis = LoadBasis.PERCENT_TM,
                                        ),
                                    ),
                                    180,
                                    LiftSlot.BENCH,
                                ),
                                slot(
                                    "t2",
                                    SlotRole.T2_SUPPLEMENTAL,
                                    CatalogIds.BP,
                                    listOf(
                                        com.example.kpkn.data.protocols.SetRecipe(
                                            reps = 5,
                                            percent = 70.0,
                                            loadBasis = LoadBasis.PERCENT_TM,
                                        ),
                                    ),
                                    150,
                                    LiftSlot.BENCH,
                                    technique = com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
                                    supplementalOf = "t1",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "N"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )
        val exercises = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
            .sessions.first().allExercises()
        val base = derivedIndex.getValue(CatalogIds.BP)
        // El nombre almacenado es el canonical verbatim; la técnica viaja en
        // campos y se muestra como chip en exerciseDisplayParts.
        assertEquals(base, exercises[0].name)
        assertEquals(base, exercises[1].name)
        assertEquals(
            com.example.kpkn.data.protocols.TechniqueModifier.SPEED,
            exercises[1].techniqueModifier,
        )
        val parts = com.example.kpkn.domain.exercises.exerciseDisplayParts(exercises[1], null)
        assertEquals(base, parts.parentName)
        assertTrue(parts.chips.isEmpty())
        exercises.forEach { exercise ->
            assertTrue(
                "Ningún nombre materializado puede ser el id crudo: '${exercise.name}'",
                !exercise.name.contains("__"),
            )
        }
    }

    // ─── B.S3: WeeklyKg (Smolov Jr) ───────────────────────────────────────────────

    private fun smolovJrRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "smolov-jr" }.recipe!!

    private fun smolovJrProgram(recipe: TrainingPlanRecipe, profile: PowerliftingProfile?): Program =
        PlanMaterializer.materialize(
            Program(id = "sj", name = "Smolov Jr"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = profile,
            // Estas pruebas miden cargas, no la composición de la receta (la cubre ProtocolCompositionContractTest).
            strict = false,
        )

    private fun squatWork(program: Program, weekIndex: Int, dayIndex: Int) =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks[weekIndex]
            .sessions[dayIndex].allExercises().first { it.catalogConfigurationId == CatalogIds.SQ_LOW }

    @Test
    fun weekly_kg_offsets_squat_sets_by_week() {
        // 1RM 200 con TM 1,0: S1 6×6 al 70 % (140 kg), S2 7×5 al 75 %, S3 8×4 al 80 %, S4 10×3 al 85 %.
        val program = smolovJrProgram(
            smolovJrRecipe(),
            PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val baseKg = listOf(140.0, 150.0, 160.0, 170.0)
        val sets = listOf(6, 7, 8, 10)
        val basePercent = listOf(70.0, 75.0, 80.0, 85.0)
        // Semana 1 sin kilo extra; semana 2 +5 kg y semana 3 +10 kg en cada serie de sentadilla.
        listOf(0.0, 5.0, 10.0).forEachIndexed { weekIndex, offset ->
            baseKg.indices.forEach { dayIndex ->
                val squat = squatWork(program, weekIndex, dayIndex)
                val label = "semana ${weekIndex + 1} día ${dayIndex + 1}"
                assertEquals("$label series", sets[dayIndex], squat.sets.size)
                squat.sets.forEach { set ->
                    assertEquals("$label kg", baseKg[dayIndex] + offset, set.weight ?: -1.0, 0.001)
                    // El % mostrado es coherente con el kg: pct + kg ÷ 1RM × 100 (70 → 72,5 → 75).
                    assertEquals("$label %", basePercent[dayIndex] + offset / 200.0 * 100.0, set.targetPercentageRM ?: -1.0, 0.001)
                    assertEquals("$label kg ÷ 1RM", set.weight!! / 200.0 * 100.0, set.targetPercentageRM!!, 0.001)
                }
            }
        }
        // El primer día de las tres semanas: 140, 145 y 150 kg.
        assertEquals(
            listOf(140.0, 145.0, 150.0),
            (0..2).map { week -> squatWork(program, week, 0).sets.first().weight ?: -1.0 },
        )
    }

    @Test
    fun weekly_kg_never_touches_accessories_without_a_lift_slot() {
        val program = smolovJrProgram(
            smolovJrRecipe(),
            PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val week3 = program.macrocycles.first().blocks.first().mesocycles.first().weeks[2]
        week3.sessions.forEach { session ->
            session.allExercises().filter { it.catalogConfigurationId != CatalogIds.SQ_LOW }.forEach { accessory ->
                assertTrue(
                    "${session.name}/${accessory.name}: los accesorios por RPE no llevan kg de WeeklyKg",
                    accessory.sets.all { it.weight == null && it.targetPercentageRM == null },
                )
            }
        }
    }

    @Test
    fun weekly_kg_without_a_load_base_keeps_the_weight_null_and_the_recipe_percent() {
        val program = smolovJrProgram(smolovJrRecipe(), profile = null)
        val squat = squatWork(program, weekIndex = 1, dayIndex = 0)
        assertTrue("sin 1RM no hay kg", squat.sets.all { it.weight == null })
        assertTrue("el porcentaje es el de la receta", squat.sets.all { it.targetPercentageRM == 70.0 })
    }

    @Test
    fun without_the_weekly_kg_rule_every_week_keeps_the_same_load() {
        val recipe = smolovJrRecipe().copy(progression = ProgressionRule.None)
        val program = smolovJrProgram(recipe, PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0))
        (0..2).forEach { week ->
            assertEquals("semana ${week + 1}", 140.0, squatWork(program, week, 0).sets.first().weight ?: -1.0, 0.001)
        }
    }

    // ─── B.S3 · R-23: reconstruir una semana no rota los días ─────────────────────

    @Test
    fun rematerializeWeek_keeps_the_weekdays_of_a_program_with_a_start_day() {
        val recipe = sampleRecipe()
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T", startDay = 3),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        // El lunes de la receta rota al miércoles cuando la semana empieza en miércoles.
        assertEquals(listOf(3), week.sessions.map { it.dayOfWeek })

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = rebuilt.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertEquals("los días no rotan al reconstruir", listOf(3), rebuiltWeek.sessions.map { it.dayOfWeek })
    }

    @Test
    fun rematerializeWeek_keeps_the_split_training_days_when_the_recipe_declares_no_weekday() {
        fun squatDay(label: String) = day(
            label,
            slots = listOf(
                slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 70.0, 5 to 80.0), 180, LiftSlot.SQUAT),
            ),
        )
        val recipe = TrainingPlanRecipe(
            id = "split-days",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(squatDay("A"), squatDay("B"), squatDay("C"), squatDay("D")),
                ),
            ),
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW),
            repeats = true,
        )
        val program = PlanMaterializer.materialize(
            Program(id = "p", name = "T", startDay = 3, selectedSplitId = "ul_x4"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0),
            strict = false,
        )
        val week = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        // «ul_x4» es Torso, Pierna, Descanso, Torso, Pierna…; con la semana empezando en miércoles: mié, jue, sáb, dom.
        assertEquals(listOf(3, 4, 6, 7), week.sessions.map { it.dayOfWeek })

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = rebuilt.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertEquals(listOf(3, 4, 6, 7), rebuiltWeek.sessions.map { it.dayOfWeek })
    }

    // ─── B.S5 · H13: re-materializar reinicia la progresión del método ────────────

    private fun wendlerRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!

    private fun weeksOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Entrena las cuatro semanas del ciclo 1 y cierra el ciclo con el motor de progreso (sube el TM del método). */
    private fun closeFirstCycle(materialized: Program): Program {
        val weeks = weeksOf(materialized)
        val last = weeks.last()
        val atEnd = materialized.copy(
            runState = ProgramRunState(
                runId = "run_531",
                cycleNumber = 1,
                weekId = last.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, last.id),
            ),
        )
        val logs = weeks.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}",
                    programId = atEnd.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-01-01T10:00:00.000Z",
                    durationMinutes = 45,
                    weekId = week.id,
                    cycleNumber = 1,
                    weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id),
                )
            }
        }
        return ProgramProgressEngine.completeCycle(
            program = atEnd,
            activeState = null,
            cycleNumber = 1,
            logs = logs,
            compositionMetadata = CatalogCompositionTestSupport.metadata,
        ).program
    }

    private fun markersOf(program: Program, proposalId: String) =
        program.effectiveWeekRecipes.flatMap { it.appliedProposals }.filter { it.proposalId == proposalId }

    @Test
    fun rematerializing_the_same_program_lets_the_method_raise_the_tm_again_at_the_first_cycle_close() {
        val recipe = wendlerRecipe()
        val oneRms = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)
        val first = PlanMaterializer.materialize(
            Program(id = "w531", name = "5/3/1"), recipe, CatalogCompositionTestSupport.metadata, SeqIds(),
            profile = oneRms,
        )
        val closed = closeFirstCycle(first)
        assertEquals("el primer cierre sube la sentadilla de 180 a 185", 185.0, closed.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(1, markersOf(closed, "author-cycle-c2").size)
        assertEquals(1, closed.nativeProgressionAudit.count { it.proposalId == "author-cycle-c2" })

        // Se vuelve a aplicar el mismo plan al mismo programa: el run empieza de cero.
        val again = PlanMaterializer.materialize(
            closed, recipe, CatalogCompositionTestSupport.metadata, SeqIds(),
            profile = oneRms,
        )

        // El run empieza de cero: ciclo 1 y cursor en la primera semana (no el ciclo 2 del run anterior).
        assertEquals(1 to weeksOf(again).first().id, again.runState?.cycleNumber to again.runState?.weekId)
        assertTrue("sin marca del ciclo anterior", markersOf(again, "author-cycle-c2").isEmpty())
        assertTrue("la marca era lo único de esa receta efectiva", again.effectiveWeekRecipes.isEmpty())
        assertTrue("sin aviso del ciclo anterior", again.nativeProgressionAudit.none { it.proposalId == "author-cycle-c2" })
        assertEquals("el TM vuelve a empezar", 180.0, again.powerliftingProfile!!.squatTM!!, 1e-9)

        // Antes de H13 la marca vieja suprimía esta subida: el TM se quedaba en 180 y no había aviso.
        val closedAgain = closeFirstCycle(again)
        assertEquals(185.0, closedAgain.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(110.5, closedAgain.powerliftingProfile!!.benchTM!!, 1e-9)
        assertEquals(1, markersOf(closedAgain, "author-cycle-c2").size)
        assertEquals(1, closedAgain.nativeProgressionAudit.count { it.proposalId == "author-cycle-c2" && it.userFacingNotice })
    }

    @Test
    fun materialize_drops_only_the_author_markers_and_notices_of_a_previous_run() {
        val recipe = sampleRecipe()
        fun applied(id: String, kind: String) = AppliedRecipeProposal(proposalId = id, kind = kind, summary = "x", acceptedAtMs = 1L)
        fun notice(id: String) = NativeProgressionResolution(
            proposalId = id,
            status = NativeProgressionResolutionStatus.NOTICE,
            kind = NativeProgressionProposalKind.INCREASE_LOAD,
            resolvedAtMs = 1L,
            reason = "x",
            userFacingNotice = true,
        )
        val dirty = Program(
            id = "p",
            name = "T",
            effectiveWeekRecipes = listOf(
                // Solo llevaban la marca de autor: desaparecen.
                EffectiveWeekRecipe(2, 2, appliedProposals = listOf(applied("author-cycle-c2", "AUTHOR_CYCLE_INCREMENT"))),
                EffectiveWeekRecipe(3, 1, appliedProposals = listOf(applied("author-block-b1", "AUTHOR_BLOCK_INCREMENT"))),
                // Semana efectiva aprobada con una propuesta y una marca de autor: queda la semana y la propuesta.
                EffectiveWeekRecipe(
                    weekOccurrence = 1,
                    cycleNumber = 1,
                    version = 4,
                    weekRecipe = recipe.weeks.first(),
                    appliedProposals = listOf(applied("auge-1", "ADJUST_TM"), applied("author-cycle-c3", "AUTHOR_CYCLE_INCREMENT")),
                ),
                // Continuación nativa: no es del motor de autor y no se toca.
                EffectiveWeekRecipe(1, 3, appliedProposals = listOf(applied("native-progression-c3", "NATIVE_PROGRESSION"))),
            ),
            nativeProgressionAudit = listOf(
                notice("author-cycle-c2"),
                notice("author-block-b1"),
                notice("native-cycle-c2"),
                notice("p-1"),
            ),
        )

        val program = PlanMaterializer.materialize(
            dirty, recipe, CatalogCompositionTestSupport.metadata, SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        )

        assertEquals(
            listOf(1 to 1, 1 to 3),
            program.effectiveWeekRecipes.map { it.weekOccurrence to it.cycleNumber },
        )
        val kept = program.effectiveWeekRecipes.first { it.weekOccurrence == 1 && it.cycleNumber == 1 }
        assertEquals(listOf("auge-1"), kept.appliedProposals.map { it.proposalId })
        assertEquals("la semana efectiva y su versión no cambian", recipe.weeks.first(), kept.weekRecipe)
        assertEquals(4, kept.version)
        assertEquals(listOf("native-cycle-c2", "p-1"), program.nativeProgressionAudit.map { it.proposalId })
    }
}
