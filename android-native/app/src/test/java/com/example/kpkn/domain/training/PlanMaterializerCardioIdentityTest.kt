package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.DEFAULT_CARDIO_PART_COLOR
import com.example.kpkn.data.models.ExerciseLoadReference
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayMinimumDose
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.warmupPercentSets
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.stableRecipeElementId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §14.1/§14.4: materialización completa de resistencia Y cardio desde la misma
 * receta, con identidad estable por (programa, receta, versión, ocurrencia,
 * semana, día[, slot]) y referencias de carga que nunca contaminan
 * `Exercise.reference1RM`.
 *
 * Reglas afirmadas aquí:
 * - Un día solo cardio (`RecipeSessionKind.CARDIO`) es una sesión real del
 *   calendario con su `SessionPart` de cardio (minutos reales, nunca un
 *   placeholder vacío).
 * - La posición del bloque (BEFORE/AFTER/ONLY) se proyecta al orden REAL de
 *   `Session.parts` y `Session.allExercises()` vía `cardioFirst`.
 * - Reconstruir la MISMA ocurrencia devuelve los mismos ids (una sesión
 *   entrenada nunca se remapea); otra ocurrencia/otro programa/otro slot nunca
 *   comparte id, y `allExercises()` no tiene duplicados.
 * - Una referencia OBSERVED_WORKING_SET (3–5 reps) jamás se almacena en
 *   `reference1RM`; una referencia 1RM capturada sí alimenta `reference1RM` y
 *   el peso del set. La bolsa del programa solo aplica a MISMA configuración.
 */
class PlanMaterializerCardioIdentityTest {
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

    // ---------------------------------------------------------------- builders

    private fun cardioBlock(
        id: String,
        position: RecipeCardioPosition,
        minutes: Int,
    ): RecipeCardioBlock = RecipeCardioBlock(
        id = id,
        details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = minutes * 60),
        position = position,
    )

    /** Día mixto: 2 slots de fuerza + 1 bloque de cardio, con ids de día/slot declarados. */
    private fun mixedRecipe(
        recipeId: String = "mixed-recipe",
        position: RecipeCardioPosition = RecipeCardioPosition.AFTER_STRENGTH,
        dayId: String = "day_mixed",
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = recipeId,
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Fuerza y cardio",
                        slots = listOf(
                            slot(
                                "s_squat",
                                SlotRole.T1_MAIN,
                                CatalogIds.SQ_LOW,
                                warmupPercentSets() + percentSets(180, 5 to 75.0),
                                180,
                                LiftSlot.SQUAT,
                                isCompetitionLift = true,
                            ),
                            slot(
                                "s_bench",
                                SlotRole.T2_SUPPLEMENTAL,
                                CatalogIds.BP,
                                percentSets(150, 5 to 70.0),
                                150,
                                LiftSlot.BENCH,
                            ),
                        ),
                        weekday = 1,
                        id = dayId,
                        cardioBlocks = listOf(cardioBlock("cardio_a", position, 25)),
                        sessionKind = RecipeSessionKind.STRENGTH_CARDIO,
                    ),
                ),
            ),
        ),
        liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP),
        claimedDaysPerWeek = 1,
    )

    /** Día solo cardio: cero slots de fuerza, dos bloques reales (25 + 4 min). */
    private fun cardioOnlyRecipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "cardio-only-recipe",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Cardio",
                        slots = emptyList(),
                        weekday = 3,
                        id = "day_cardio",
                        cardioBlocks = listOf(
                            cardioBlock("cardio_run", RecipeCardioPosition.ONLY, 25),
                            cardioBlock("cardio_bike", RecipeCardioPosition.ONLY, 4),
                        ),
                        sessionKind = RecipeSessionKind.CARDIO,
                        minimumDose = DayMinimumDose(minDistinctConfigurations = 0, minResistanceSets = 0),
                    ),
                ),
            ),
        ),
        claimedDaysPerWeek = 1,
    )

    /** Receta de 1 slot de press de banca cuyo set declara una referencia de carga. */
    private fun referenceRecipe(set: SetRecipe): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "reference-recipe",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Bloque",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        label = "Banca",
                        slots = listOf(
                            slot(
                                "s_bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                listOf(set),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                        weekday = 1,
                        id = "day_ref",
                    ),
                ),
            ),
        ),
        liftSlots = mapOf(LiftSlot.BENCH to CatalogIds.BP),
        claimedDaysPerWeek = 1,
    )

    // ---------------------------------------------------------------- helpers

    private fun materialize(
        recipe: TrainingPlanRecipe,
        programId: String = "p",
        weekOccurrence: Int = 1,
        program: Program = Program(id = programId, name = "T"),
    ): Program = PlanMaterializer.materialize(
        program,
        recipe,
        CatalogCompositionTestSupport.metadata,
        SeqIds(),
        profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
        strict = false,
        weekOccurrence = weekOccurrence,
    )

    private fun firstWeek(program: Program): ProgramWeek =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()

    private fun stableSessionId(
        recipe: TrainingPlanRecipe,
        dayId: String,
        programId: String = "p",
        weekOccurrence: Int = 1,
    ): String = stableRecipeElementId(
        programId = programId,
        recipeId = recipe.id,
        contentVersion = recipe.contentVersion,
        weekOccurrence = weekOccurrence,
        weekNumber = 1,
        dayId = dayId,
    )

    private fun stableExerciseId(
        recipe: TrainingPlanRecipe,
        dayId: String,
        slotId: String,
        programId: String = "p",
        weekOccurrence: Int = 1,
    ): String = stableRecipeElementId(
        programId = programId,
        recipeId = recipe.id,
        contentVersion = recipe.contentVersion,
        weekOccurrence = weekOccurrence,
        weekNumber = 1,
        dayId = dayId,
        slotId = slotId,
    )

    /** Tupla de identidad materializada: sesión, partes, ejercicios, sets y calentamientos. */
    private fun identitySnapshot(program: Program): List<String> =
        firstWeek(program).sessions.flatMap { session ->
            listOf(session.id) + session.parts.flatMap { part ->
                listOf(part.id) + part.exercises.flatMap { exercise ->
                    listOfNotNull(exercise.id, exercise.occurrenceId) +
                        exercise.sets.map { it.id } +
                        exercise.warmupSets.map { it.id }
                }
            }
        }

    private fun workingSetIds(week: ProgramWeek): List<String> =
        week.sessions.flatMap { session -> session.allExercises().flatMap { ex -> ex.sets.map { it.id } } }

    // ------------------------------------------------------- cardio projection

    @Test
    fun cardio_only_day_is_a_real_calendar_session_with_cardio_part() {
        val recipe = cardioOnlyRecipe()
        val program = materialize(recipe)
        val week = firstWeek(program)
        assertEquals(1, week.sessions.size)

        val session = week.sessions.first()
        assertEquals("Cardio", session.name)
        assertEquals(3, session.dayOfWeek ?: -1)
        assertEquals(listOf(3), session.assignedDays)
        assertFalse("Un día solo cardio no es sesión principal de fuerza", session.isMainSession)
        assertTrue("Cardio ONLY va primero", session.cardioFirst)
        assertTrue("La lista suelta nunca espeja parts", session.exercises.isEmpty())

        // Identidad estable §14.4: la sesión deriva de SU tupla, no de un UUID.
        assertTrue(session.id.startsWith("rs_"))
        assertEquals(stableSessionId(recipe, "day_cardio"), session.id)

        assertEquals(1, session.parts.size)
        val part = session.parts.first()
        assertEquals("${session.id}#part:cardio", part.id)
        assertEquals("Cardio", part.name)
        assertTrue(part.isCardioGroup)
        assertEquals(DEFAULT_CARDIO_PART_COLOR, part.color)
        // 25 min + 4 min = 1740 s → 29 minutos reales (redondeo hacia arriba).
        assertEquals(29, part.targetDurationMinutes ?: -1)

        assertEquals(2, part.exercises.size)
        val run = part.exercises[0]
        assertEquals("treadmill", run.name)
        assertEquals("day_cardio", run.recipeDayId)
        assertEquals("cardio_run", run.recipeSlotId)
        assertEquals(recipe.weeks.first().days.first().cardioBlocks[0].details, run.cardioDetails)
        assertTrue(run.id.startsWith("re_"))
        assertEquals(stableExerciseId(recipe, "day_cardio", "cardio:cardio_run"), run.id)
        val bike = part.exercises[1]
        assertEquals("cardio_bike", bike.recipeSlotId)
        assertNotEquals(run.id, bike.id)

        val all = session.allExercises()
        assertEquals(2, all.size)
        assertTrue(all.all { it.cardioDetails != null })
        assertEquals(all.map { it.id }.distinct().size, all.size)
    }

    @Test
    fun cardio_before_strength_projects_first_in_parts_and_exercises() {
        val recipe = mixedRecipe(position = RecipeCardioPosition.BEFORE_STRENGTH)
        val session = firstWeek(materialize(recipe)).sessions.first()

        assertTrue(session.cardioFirst)
        assertEquals(3, session.parts.size)
        assertTrue(session.parts.first().isCardioGroup)
        assertFalse(session.parts.last().isCardioGroup)

        val exercises = session.allExercises()
        assertEquals(3, exercises.size)
        assertTrue(exercises.first().cardioDetails != null)
        assertEquals(CatalogIds.SQ_LOW, exercises[1].catalogConfigurationId)
        assertEquals(CatalogIds.BP, exercises[2].catalogConfigurationId)
    }

    @Test
    fun cardio_after_strength_projects_last_in_parts_and_exercises() {
        val recipe = mixedRecipe(position = RecipeCardioPosition.AFTER_STRENGTH)
        val session = firstWeek(materialize(recipe)).sessions.first()

        assertFalse(session.cardioFirst)
        assertEquals(3, session.parts.size)
        assertFalse(session.parts.first().isCardioGroup)
        assertTrue(session.parts.last().isCardioGroup)

        val exercises = session.allExercises()
        assertEquals(3, exercises.size)
        assertEquals(CatalogIds.SQ_LOW, exercises[0].catalogConfigurationId)
        assertEquals(CatalogIds.BP, exercises[1].catalogConfigurationId)
        assertTrue(exercises.last().cardioDetails != null)
    }

    @Test
    fun mixed_session_all_exercises_has_no_duplicates() {
        val session = firstWeek(materialize(mixedRecipe())).sessions.first()
        val partIds = session.parts.flatMap { it.exercises }.map { it.id }
        val all = session.allExercises()

        assertEquals(3, all.size)
        assertEquals(session.exercises.size + partIds.size, all.size)
        assertEquals(partIds.size, all.size)
        assertEquals(all.map { it.id }.distinct().size, all.size)
    }

    // ---------------------------------------------------- stable identity §14.4

    @Test
    fun rebuilding_the_same_occurrence_keeps_every_id_stable() {
        val recipe = mixedRecipe()
        val first = identitySnapshot(materialize(recipe))
        val second = identitySnapshot(materialize(recipe))

        assertTrue("La snapshot debe tener ids", first.isNotEmpty())
        assertTrue(first.all { it.isNotBlank() })
        assertEquals(first.size, first.distinct().size)
        assertEquals(
            "Reconstruir la misma ocurrencia remapearía sesiones entrenadas",
            first,
            second,
        )
    }

    @Test
    fun identity_differs_across_week_occurrence_program_and_slot() {
        val recipe = mixedRecipe()
        val base = firstWeek(materialize(recipe, programId = "p1", weekOccurrence = 1))
        val otherOccurrence = firstWeek(materialize(recipe, programId = "p1", weekOccurrence = 2))
        val otherProgram = firstWeek(materialize(recipe, programId = "p2", weekOccurrence = 1))

        val baseSessionIds = base.sessions.map { it.id }
        assertNotEquals(baseSessionIds, otherOccurrence.sessions.map { it.id })
        assertNotEquals(baseSessionIds, otherProgram.sessions.map { it.id })

        val baseExerciseIds = base.sessions.flatMap { s -> s.allExercises().map { it.id } }
        assertNotEquals(baseExerciseIds, otherOccurrence.sessions.flatMap { s -> s.allExercises().map { it.id } })
        assertNotEquals(baseExerciseIds, otherProgram.sessions.flatMap { s -> s.allExercises().map { it.id } })

        // Dos slots con la MISMA configuración nunca comparten identidad.
        val recipeDup = TrainingPlanRecipe(
            id = "dup-config",
            weeks = listOf(
                weekRecipe(
                    1,
                    0,
                    "Bloque",
                    BlockGoal.ACCUMULATION,
                    listOf(
                        DayRecipe(
                            label = "Banca doble",
                            weekday = 1,
                            id = "day_dup",
                            slots = listOf(
                                slot(
                                    "s_bench_a",
                                    SlotRole.T1_MAIN,
                                    CatalogIds.BP,
                                    percentSets(150, 5 to 75.0),
                                    150,
                                    LiftSlot.BENCH,
                                ),
                                slot(
                                    "s_bench_b",
                                    SlotRole.T2_SUPPLEMENTAL,
                                    CatalogIds.BP,
                                    percentSets(150, 5 to 70.0),
                                    150,
                                    LiftSlot.BENCH,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val duplicated = firstWeek(materialize(recipeDup)).sessions.first().allExercises()
        assertEquals(2, duplicated.size)
        assertNotEquals(duplicated[0].id, duplicated[1].id)
        assertNotEquals(duplicated[0].occurrenceId, duplicated[1].occurrenceId)
        assertEquals("s_bench_a", duplicated[0].recipeSlotId)
        assertEquals("s_bench_b", duplicated[1].recipeSlotId)
        assertEquals(
            stableExerciseId(recipeDup, "day_dup", "s_bench_a"),
            duplicated[0].id,
        )
        assertEquals(
            stableExerciseId(recipeDup, "day_dup", "s_bench_b"),
            duplicated[1].id,
        )
    }

    @Test
    fun rematerialize_week_preserves_session_exercise_and_set_ids() {
        val recipe = mixedRecipe()
        val program = materialize(recipe)
        val week = firstWeek(program)

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = firstWeek(rebuilt)

        assertEquals(week.id, rebuiltWeek.id)
        assertEquals(
            "Reconstruir la semana remapearía las sesiones entrenadas",
            week.sessions.map { it.id },
            rebuiltWeek.sessions.map { it.id },
        )
        assertEquals(
            week.sessions.flatMap { s -> s.allExercises().map { it.id } },
            rebuiltWeek.sessions.flatMap { s -> s.allExercises().map { it.id } },
        )
        assertEquals(workingSetIds(week), workingSetIds(rebuiltWeek))
        // Los calentamientos declarados por la receta conservan su id estable.
        assertEquals(
            week.sessions.flatMap { s -> s.allExercises().flatMap { e -> e.warmupSets.map { it.id } } },
            rebuiltWeek.sessions.flatMap { s -> s.allExercises().flatMap { e -> e.warmupSets.map { it.id } } },
        )
    }

    // ------------------------------------------------ load references §14.1/§14.2

    @Test
    fun observed_working_set_is_never_stored_as_reference1rm() {
        val reference = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = CatalogIds.BP,
            quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
            sourceSlotId = "s_bench_heavy",
            repMin = 3,
            repMax = 5,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 65.0,
        )
        // percent 100 = usar la carga de trabajo observada tal cual.
        val program = materialize(
            referenceRecipe(SetRecipe(reps = 5, percent = 100.0, loadBasis = LoadBasis.PERCENT_TM, reference = reference)),
        )
        val exercise = firstWeek(program).sessions.first().allExercises().first()

        assertNull(
            "Una carga de trabajo de 3-5 reps JAMÁS se guarda en reference1RM",
            exercise.reference1RM,
        )
        assertEquals(reference, exercise.loadReference)
        assertEquals(LoadQuantityConvention.PER_IMPLEMENT, exercise.loadQuantityConvention)
        assertEquals("La carga observable sí fluye al peso del set", 65.0, exercise.sets.first().weight ?: -1.0, 0.001)
    }

    @Test
    fun captured_1rm_reference_feeds_reference1rm_and_percent_weight() {
        val reference = PlanLoadReference(
            kind = PlanLoadReferenceKind.EXERCISE_1RM,
            configurationId = CatalogIds.BP,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 100.0,
        )
        val program = materialize(
            referenceRecipe(SetRecipe(reps = 5, percent = 80.0, loadBasis = LoadBasis.PERCENT_TM, reference = reference)),
        )
        val exercise = firstWeek(program).sessions.first().allExercises().first()

        assertEquals(reference, exercise.loadReference)
        assertEquals(100.0, exercise.reference1RM ?: -1.0, 0.001)
        assertEquals(80.0, exercise.sets.first().weight ?: -1.0, 0.001)
    }

    @Test
    fun pending_reference_keeps_weight_null_never_zero_kilos() {
        val reference = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = CatalogIds.BP,
            repMin = 3,
            repMax = 5,
            state = PlanLoadReferenceState.PENDING,
        )
        val program = materialize(
            referenceRecipe(SetRecipe(reps = 5, percent = 100.0, loadBasis = LoadBasis.PERCENT_TM, reference = reference)),
        )
        val exercise = firstWeek(program).sessions.first().allExercises().first()

        assertEquals(reference, exercise.loadReference)
        assertNull("Sin referencia capturada no hay kg: null ≠ 0 kg", exercise.sets.first().weight)
        assertNull("Tampoco se inventa un 1RM de respaldo", exercise.reference1RM)
    }

    @Test
    fun program_reference_pool_applies_only_to_same_configuration_and_stable_id() {
        val recipe = mixedRecipe()
        val program = Program(
            id = "p",
            name = "T",
            exerciseLoadReferences = listOf(
                ExerciseLoadReference(
                    exerciseId = stableExerciseId(recipe, "day_mixed", "s_bench"),
                    references = listOf(
                        // Otra configuración: jamás se transfiere a la banca.
                        PlanLoadReference(
                            kind = PlanLoadReferenceKind.EXERCISE_1RM,
                            configurationId = CatalogIds.DL,
                            state = PlanLoadReferenceState.CAPTURED,
                            capturedLoadKg = 40.0,
                        ),
                        PlanLoadReference(
                            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
                            configurationId = CatalogIds.BP,
                            repMin = 3,
                            repMax = 5,
                            state = PlanLoadReferenceState.CAPTURED,
                            capturedLoadKg = 60.0,
                        ),
                    ),
                ),
            ),
        )
        val materialized = materialize(recipe, program = program)
        val exercises = firstWeek(materialized).sessions.first().allExercises()

        val bench = exercises.first { it.recipeSlotId == "s_bench" }
        assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, bench.loadReference?.kind)
        assertNull("Trabajo observado 3-5 reps nunca en reference1RM", bench.reference1RM)
        assertEquals(70.0 / 100.0 * 60.0, bench.sets.first().weight ?: -1.0, 0.001)

        // El squat no tiene entrada en la bolsa: resolución legacy intacta.
        val squat = exercises.first { it.recipeSlotId == "s_squat" }
        assertNull(squat.loadReference)
        assertEquals(180.0, squat.reference1RM ?: -1.0, 0.001)
        assertEquals(75.0 / 100.0 * 180.0, squat.sets.first().weight ?: -1.0, 0.001)

        assertNotNull(squat.warmupSets.firstOrNull())
    }
}
