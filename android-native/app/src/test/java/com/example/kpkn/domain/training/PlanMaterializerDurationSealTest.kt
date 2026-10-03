package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rirSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * §12.2 + §14.4/§14.5: los planes propios sellan en cada sesión
 * `targetDurationMinutes` con el estimador común. Reconstruir una semana de su
 * receta propia no pierde ese sello: lo vuelve a medir con el MISMO estimador
 * sobre el contenido reconstruido (receta sin cambios → el mismo minuto que el
 * generador), sin inventar un límite donde el plan nunca lo tuvo y sin tocar lo
 * que el usuario editó (sesión congelada).
 */
class PlanMaterializerDurationSealTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "seal_${++n}"
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    /** Día de receta con id declarado (identidad estable §14.4) y slots KPKN_DEFAULT (plan propio). */
    private fun ownDay(dayId: String, weekday: Int, label: String): DayRecipe = DayRecipe(
        id = dayId,
        label = label,
        weekday = weekday,
        slots = listOf(
            slot(
                "main",
                SlotRole.T1_MAIN,
                CatalogIds.SQ_LOW,
                rirSets(3, 5, 2, rest = 180),
                restSeconds = 180,
                source = SlotSource.KPKN_DEFAULT,
            ),
            slot(
                "supplemental",
                SlotRole.T2_SUPPLEMENTAL,
                CatalogIds.BP,
                rirSets(3, 8, 2, rest = 120),
                restSeconds = 120,
                source = SlotSource.KPKN_DEFAULT,
            ),
        ),
    )

    private fun ownRecipe(): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "seal-own-recipe",
        weeks = (1..2).map { weekNumber ->
            weekRecipe(
                weekNumber, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    ownDay("w$weekNumber-d1", 1, "Día 1"),
                    ownDay("w$weekNumber-d2", 3, "Día 2"),
                ),
            )
        },
        claimedDaysPerWeek = 2,
    )

    /** Programa con bloque curado por el motor nativo, como lo deja `SimpleCyclePersonalizer`. */
    private fun materializedOwn(recipe: TrainingPlanRecipe): Program = PlanMaterializer.materialize(
        Program(
            id = "seal-program",
            name = "Seal",
            sourceRecipe = recipe,
            macrocycles = listOf(
                Macrocycle(
                    id = "seal-macro",
                    name = "Seal",
                    blocks = listOf(
                        Block(
                            id = "seal-block",
                            name = "Base",
                            prescriptionOrigin = KPKN_NATIVE_CURATED_ORIGIN,
                            mesocycles = listOf(Mesocycle(id = "seal-meso", name = "Base", weeks = emptyList())),
                        ),
                    ),
                ),
            ),
        ),
        recipe,
        metadata,
        SeqIds(),
        strict = false,
    )

    private fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    private fun mapSessions(program: Program, transform: (Session) -> Session): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week -> week.copy(sessions = week.sessions.map(transform)) },
                            )
                        },
                    )
                },
            )
        },
    )

    /** Lo mismo que hace el generador de planes propios al publicar: sello = minutos del estimador común. */
    private fun sealedLikeTheGenerator(program: Program): Program =
        mapSessions(program) { it.copy(targetDurationMinutes = SessionDurationEstimator.estimate(it).totalMinutes) }

    @Test
    fun rebuilding_a_sealed_own_week_with_its_own_recipe_keeps_the_exact_shared_estimate() {
        val recipe = ownRecipe()
        val program = sealedLikeTheGenerator(materializedOwn(recipe))
        val week2 = weeksOf(program)[1]
        val before = week2.sessions.map { it.targetDurationMinutes }
        assertTrue("el generador sella cada sesión: $before", before.all { it != null && it > 0 })

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program, week2.id, recipe, metadata, SeqIds(), weekOccurrence = 2,
        )
        val after = weeksOf(rebuilt)[1].sessions

        assertEquals("mismas sesiones", week2.sessions.map { it.id }, after.map { it.id })
        assertEquals("el sello sobrevive a la reconstrucción", before, after.map { it.targetDurationMinutes })
        after.forEach { session ->
            assertEquals(
                "el sello es la medición del estimador común (§12.2)",
                SessionDurationEstimator.estimate(session).totalMinutes,
                session.targetDurationMinutes,
            )
        }
    }

    @Test
    fun rebuilding_an_unsealed_own_week_does_not_invent_a_time_limit() {
        val recipe = ownRecipe()
        val program = materializedOwn(recipe)
        val week2 = weeksOf(program)[1]
        assertTrue(week2.sessions.all { it.targetDurationMinutes == null })

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program, week2.id, recipe, metadata, SeqIds(), weekOccurrence = 2,
        )

        assertTrue(weeksOf(rebuilt)[1].sessions.all { it.targetDurationMinutes == null })
    }

    @Test
    fun an_accepted_volume_proposal_reseals_with_the_estimate_of_the_reduced_week() {
        val recipe = ownRecipe()
        val program = sealedLikeTheGenerator(materializedOwn(recipe))
        val week2 = weeksOf(program)[1]

        val rebuilt = PlanMaterializer.rematerializeWeek(
            program, week2.id, recipe, metadata, SeqIds(), volumeFactor = 0.5, weekOccurrence = 2,
        )
        val after = weeksOf(rebuilt)[1].sessions

        assertEquals(week2.sessions.size, after.size)
        week2.sessions.zip(after).forEach { (old, new) ->
            assertTrue("menos series, menos minutos", new.targetDurationMinutes!! < old.targetDurationMinutes!!)
            assertEquals(SessionDurationEstimator.estimate(new).totalMinutes, new.targetDurationMinutes)
        }
    }

    @Test
    fun a_frozen_session_keeps_its_own_limit_and_only_pending_ones_are_resealed() {
        val recipe = ownRecipe()
        val program = sealedLikeTheGenerator(materializedOwn(recipe))
        val week2 = weeksOf(program)[1]
        val custom = week2.sessions.first().copy(targetDurationMinutes = 99)
        val edited = mapSessions(program) { if (it.id == custom.id) custom else it }
        val marked = PlanMaterializer.withManualSessionOverride(
            program = edited,
            sessionId = custom.id,
            weekId = week2.id,
            weekOccurrence = 2,
            recipeDayId = null,
        )

        val rebuilt = PlanMaterializer.rematerializeWeek(
            marked, week2.id, recipe, metadata, SeqIds(), weekOccurrence = 2,
        )
        val after = weeksOf(rebuilt)[1].sessions

        assertEquals("la sesión congelada queda íntegra", custom, after.single { it.id == custom.id })
        val pending = after.single { it.id != custom.id }
        val pendingBefore = week2.sessions.single { it.id != custom.id }
        assertEquals(pendingBefore.targetDurationMinutes, pending.targetDurationMinutes)
        assertNotEquals(99, pending.targetDurationMinutes)
    }

    @Test
    fun a_foreign_recipe_gets_no_native_seal_and_drops_the_pending_residue_of_the_previous_recipe() {
        val program = sealedLikeTheGenerator(materializedOwn(ownRecipe()))
        val week1 = weeksOf(program)[0]
        val author = TrainingPlanRecipe(
            id = "seal-foreign-author",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(
                        day(
                            "Día autor",
                            listOf(slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, rirSets(4, 5, 2, rest = 180), restSeconds = 180)),
                        ),
                    ),
                ),
            ),
        )

        val rebuilt = PlanMaterializer.rematerializeWeek(program, week1.id, author, metadata, SeqIds())
        val after = weeksOf(rebuilt)[0].sessions

        assertEquals("solo la base de la receta ajena", 1, after.size)
        assertEquals(listOf(CatalogIds.SQ_LOW), after.single().allExercises().map { it.catalogConfigurationId })
        assertNull("una receta ajena no hereda el sello del plan propio", after.single().targetDurationMinutes)
    }
}
