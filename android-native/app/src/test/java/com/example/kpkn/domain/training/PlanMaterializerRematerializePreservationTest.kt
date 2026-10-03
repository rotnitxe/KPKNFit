package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §14.4/§14.5 + AC-G2/AC-G3: la reconstrucción de UNA semana conserva cardio,
 * SPEED, cargas, IDs y marcas de edición manual; las sesiones entrenadas o
 * congeladas no se reescriben y el resto se reconstruye.
 */
class PlanMaterializerRematerializePreservationTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "keep_${++n}"
    }

    private fun mixedDay(dayId: String, weekday: Int, label: String): DayRecipe = DayRecipe(
        id = dayId,
        label = label,
        weekday = weekday,
        sessionKind = RecipeSessionKind.STRENGTH_CARDIO,
        cardioBlocks = listOf(
            RecipeCardioBlock(
                id = "cardio-$dayId",
                details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 20 * 60),
                position = RecipeCardioPosition.AFTER_STRENGTH,
            ),
        ),
        slots = listOf(
            slot(
                "speed-sq",
                SlotRole.SPEED,
                CatalogIds.SQ_LOW,
                percentSets(120, 5 to 60.0, 5 to 60.0, 5 to 60.0),
                120,
                LiftSlot.SQUAT,
            ),
            slot(
                "main-bp",
                SlotRole.T1_MAIN,
                CatalogIds.BP,
                percentSets(180, 5 to 75.0, 5 to 75.0, 5 to 75.0, 5 to 75.0),
                180,
                LiftSlot.BENCH,
                isCompetitionLift = true,
            ),
        ),
    )

    private fun recipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "preserve-recipe",
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(mixedDay("day_a", 1, "Día A"), mixedDay("day_b", 3, "Día B")),
            ),
            weekRecipe(
                2, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(mixedDay("day_c", 1, "Día C"), mixedDay("day_d", 3, "Día D")),
            ),
        ),
        claimedDaysPerWeek = 2,
    )

    /** Receta legacy SIN ids de día/slot: la identidad sigue con el IdProvider. */
    private fun legacyRecipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "legacy-preserve",
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(com.example.kpkn.data.protocols.DayArchetypes.plSquat(80.0, t1Amrap = false, weekday = 1)),
            ),
        ),
    )

    private fun materialized(recipe: TrainingPlanRecipe, programId: String = "p-preserve"): Program =
        PlanMaterializer.materialize(
            Program(id = programId, name = "Preserva"),
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )

    private fun firstWeek(program: Program): ProgramWeek =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.first()

    private fun identityOf(week: ProgramWeek): List<String?> =
        week.sessions.flatMap { session ->
            listOf(session.id) +
                session.parts.flatMap { part ->
                    listOf(part.id) + part.exercises.flatMap { exercise ->
                        listOf(exercise.id, exercise.occurrenceId) +
                            exercise.sets.map { it.id } +
                            exercise.warmupSets.map { it.id }
                    }
                }
        }

    private fun replaceSession(program: Program, weekId: String, session: Session): Program =
        program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else week.copy(sessions = week.sessions.map { if (it.id == session.id) session else it })
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )

    @Test
    fun plain_rebuild_preserves_cardio_speed_loads_and_every_id() {
        val recipe = recipe()
        val program = materialized(recipe)
        val week = firstWeek(program)

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = firstWeek(rebuilt)

        assertEquals("La semana mantiene su id", week.id, rebuiltWeek.id)
        assertEquals("IDs estables §14.4", identityOf(week), identityOf(rebuiltWeek))

        val original = week.sessions.first()
        val after = rebuiltWeek.sessions.first()
        // Cardio completo: duración, intensidad, modalidad y enlace.
        val cardioBefore = original.parts.first { it.isCardioGroup }
        val cardioAfter = after.parts.first { it.isCardioGroup }
        assertEquals(cardioBefore.targetDurationMinutes, cardioAfter.targetDurationMinutes)
        assertEquals(cardioBefore.exercises.map { it.cardioDetails }, cardioAfter.exercises.map { it.cardioDetails })
        assertEquals(cardioBefore.id, cardioAfter.id)
        assertEquals(original.cardioFirst, after.cardioFirst)

        // SPEED: mismo número de series y mismos porcentajes.
        val speedBefore = original.allExercises().first { it.slotRole == SlotRole.SPEED }
        val speedAfter = after.allExercises().first { it.slotRole == SlotRole.SPEED }
        assertEquals(speedBefore.sets.size, speedAfter.sets.size)
        assertEquals(speedBefore.sets.map { it.targetPercentageRM }, speedAfter.sets.map { it.targetPercentageRM })

        // Cargas intactas (porcentaje y kg calculados).
        assertEquals(
            original.allExercises().flatMap { it.sets }.map { it.targetPercentageRM to it.weight },
            after.allExercises().flatMap { it.sets }.map { it.targetPercentageRM to it.weight },
        )
    }

    @Test
    fun volume_proposal_never_changes_cardio_duration_or_speed_set_counts() {
        val source = recipe().weeks.first()
        val scaled = PlanMaterializer.scaleWeekRecipe(source, intensityScale = 1.0, volumeFactor = 0.5)
        val originalDay = source.days.first()
        val day = scaled.days.first()

        val speed = day.slots.first { it.role == SlotRole.SPEED }
        val main = day.slots.first { it.role == SlotRole.T1_MAIN }
        assertEquals(
            "SPEED conserva sus series",
            originalDay.slots.first { it.role == SlotRole.SPEED }.sets.size,
            speed.sets.size,
        )
        assertTrue(
            "El slot ordinario sí pierde series",
            main.sets.size < originalDay.slots.first { it.role == SlotRole.T1_MAIN }.sets.size,
        )
        assertEquals("El cardio no se toca", originalDay.cardioBlocks, day.cardioBlocks)
        assertEquals(
            originalDay.cardioBlocks.first().details.targetDurationSeconds,
            day.cardioBlocks.first().details.targetDurationSeconds,
        )
    }

    @Test
    fun manual_override_session_survives_rebuild_while_the_rest_is_rematerialized() {
        val recipe = recipe()
        val program = materialized(recipe)
        val week = firstWeek(program)
        val target = week.sessions.first()
        val other = week.sessions[1]
        val edited = target.copy(name = "Personalizada a mano", description = "Edicion manual")

        val marked = PlanMaterializer.withManualSessionOverride(
            program = replaceSession(program, week.id, edited),
            sessionId = edited.id,
            weekId = week.id,
            weekOccurrence = 1,
            recipeDayId = "day_a",
            reason = "Sesión guardada desde el editor",
        )
        assertEquals(1, marked.manualSessionOverrides.size)

        val rebuilt = PlanMaterializer.rematerializeWeek(
            marked,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = firstWeek(rebuilt)

        assertEquals(2, rebuiltWeek.sessions.size)
        val preserved = rebuiltWeek.sessions.first { it.id == edited.id }
        assertEquals("La marca congela contenido completo", edited.name, preserved.name)
        assertEquals(edited.description, preserved.description)
        assertEquals(edited.allExercises().map { it.id }, preserved.allExercises().map { it.id })
        // La otra sesión de la semana sí se reconstruye con su nombre de receta.
        val rebuiltOther = rebuiltWeek.sessions.first { it.id == other.id }
        assertEquals(recipe.weeks.first().days[1].label, rebuiltOther.name)
    }

    @Test
    fun restore_from_plan_reverts_only_the_chosen_session_and_keeps_history_attached() {
        val recipe = recipe()
        val program = materialized(recipe)
        val week = firstWeek(program)
        val target = week.sessions.first()
        val edited = target.copy(name = "Personalizada a mano")
        val marked = PlanMaterializer.withManualSessionOverride(
            program = replaceSession(program, week.id, edited),
            sessionId = edited.id,
            weekId = week.id,
            weekOccurrence = 1,
            recipeDayId = "day_a",
        )

        // Otra sesión congelada en otra semana: la restauración no la toca.
        val withOtherOverride = PlanMaterializer.withManualSessionOverride(
            program = marked,
            sessionId = "session-externa",
            weekId = "otra-semana",
            weekOccurrence = 2,
            recipeDayId = "day_z",
        )

        val restored = PlanMaterializer.removeManualSessionOverride(withOtherOverride, edited.id)
        assertEquals(
            "Solo se quita la marca elegida",
            listOf("session-externa"),
            restored.manualSessionOverrides.map { it.sessionId },
        )

        val rebuilt = PlanMaterializer.rematerializeWeek(
            restored,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = firstWeek(rebuilt)
        val reverted = rebuiltWeek.sessions.first { it.id == edited.id }
        assertEquals("Vuelve a la receta", recipe.weeks.first().days.first().label, reverted.name)
        // Mismo id → los logs históricos siguen apuntando a la misma sesión.
        assertEquals(edited.id, reverted.id)
        // La otra sesión congelada no se reconstruyó: su marca sigue presente.
        assertTrue(withOtherOverride.manualSessionOverrides.any { it.sessionId == "session-externa" })
    }

    @Test
    fun legacy_rebuild_keeps_session_exercise_and_set_ids() {
        val recipe = legacyRecipe()
        val program = materialized(recipe, programId = "p-legacy")
        val week = firstWeek(program)

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
        )
        val rebuiltWeek = firstWeek(rebuilt)

        assertEquals("La receta legacy no remapea ids", identityOf(week), identityOf(rebuiltWeek))
        assertEquals(week.sessions.map { it.id }, rebuiltWeek.sessions.map { it.id })
    }

    @Test
    fun repeated_rebuild_is_byte_identical() {
        val recipe = recipe()
        val program = materialized(recipe)
        val week = firstWeek(program)

        val first = PlanMaterializer.rematerializeWeek(program, week.id, recipe, CatalogCompositionTestSupport.metadata, SeqIds())
        val second = PlanMaterializer.rematerializeWeek(first, week.id, recipe, CatalogCompositionTestSupport.metadata, SeqIds())

        assertEquals(first.macrocycles, second.macrocycles)
    }

    @Test
    fun session_level_evidence_preserves_trained_sessions_and_rebuilds_the_pending_ones() {
        val recipe = TrainingPlanRecipe(
            id = "two-days",
            weeks = listOf(
                weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, listOf(mixedDay("day_a", 1, "Día A"), mixedDay("day_c", 3, "Día C"))),
            ),
            claimedDaysPerWeek = 2,
        )
        val program = materialized(recipe)
        val week = firstWeek(program)
        val trained = week.sessions.first().copy(name = "Entrenada y registrada")
        val programWithTrained = replaceSession(program, week.id, trained)

        val rebuilt = PlanMaterializer.rematerializeWeek(
            programWithTrained,
            week.id,
            recipe,
            CatalogCompositionTestSupport.metadata,
            SeqIds(),
            executedSessionIds = setOf(trained.id),
        )
        val rebuiltWeek = firstWeek(rebuilt)

        assertEquals(2, rebuiltWeek.sessions.size)
        val preserved = rebuiltWeek.sessions.first { it.id == trained.id }
        assertEquals("La sesión entrenada queda intacta", trained.name, preserved.name)
        assertEquals(trained.allExercises().map { it.id }, preserved.allExercises().map { it.id })
        val rebuiltPending = rebuiltWeek.sessions.first { it.id != trained.id }
        assertEquals("La sesión pendiente vuelve a la receta", "Día C", rebuiltPending.name)
        assertNotEquals(trained.name, rebuiltPending.name)
    }
}
