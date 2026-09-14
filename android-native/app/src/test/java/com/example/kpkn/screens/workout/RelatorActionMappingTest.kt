package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.TrainingPhase
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.relator.RelatorActionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorActionMappingTest {
    @Test
    fun new_action_kinds_round_trip_through_spec() {
        val kinds = listOf(
            RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            RelatorAssistActionKind.ADJUST_LOAD,
            RelatorAssistActionKind.START_REST,
            RelatorAssistActionKind.EXTEND_REST,
            RelatorAssistActionKind.SKIP_REMAINING_WARMUPS,
            RelatorAssistActionKind.OPEN_REPLACE,
            RelatorAssistActionKind.OPEN_READINESS,
            RelatorAssistActionKind.OPEN_TECHNIQUE,
            RelatorAssistActionKind.OPEN_HISTORY,
            RelatorAssistActionKind.CAPTURE_MEDIA,
            RelatorAssistActionKind.OPEN_ALBUM,
        )
        kinds.forEach { kind ->
            val action = RelatorAssistAction(
                kind = kind,
                label = kind.name,
                exerciseId = "ex",
                setIndex = 1,
                weightKg = 90.0,
                restSeconds = 15,
                loadDeltaPercent = -5.0,
            )
            val back = action.toSpec().toAssistAction()
            assertEquals(kind, back.kind)
            assertEquals(kind.name, back.toSpec().kind)
            assertEquals(90.0, back.weightKg)
            assertEquals(15, back.restSeconds)
            assertEquals(-5.0, back.loadDeltaPercent)
        }
        assertEquals(RelatorActionKind.APPLY_SUGGESTED_LOAD, RelatorAssistActionKind.APPLY_SUGGESTED_LOAD.name)
        assertEquals(RelatorActionKind.CAPTURE_MEDIA, RelatorAssistActionKind.CAPTURE_MEDIA.name)
        assertEquals(RelatorActionKind.OPEN_ALBUM, RelatorAssistActionKind.OPEN_ALBUM.name)
    }

    @Test
    fun capture_and_album_are_documented_hooks() {
        assertTrue(RelatorAssistActionKind.CAPTURE_MEDIA.name == "CAPTURE_MEDIA")
        assertTrue(RelatorAssistActionKind.OPEN_ALBUM.name == "OPEN_ALBUM")
    }
}

class RelatorLivePlanContextTest {
    @Test
    fun hydrator_copies_protocol_block_week_and_recipe() {
        val weekA = ProgramWeek(id = "w1", name = "S1")
        val weekB = ProgramWeek(id = "w2", name = "S2")
        val block = Block(
            id = "b1",
            name = "Intensidad",
            goal = BlockGoal.INTENSIFICATION,
            progressionScheme = BlockProgressionScheme.PERCENT_RM,
            mesocycles = listOf(Mesocycle(id = "m1", name = "M1", weeks = listOf(weekA, weekB))),
        )
        val program = Program(
            id = "p",
            name = "531",
            mode = ProgramMode.POWERLIFTING,
            trainingPhase = TrainingPhase.REALIZATION,
            sourceProtocolId = "wendler-531-bbb",
            sourceRecipe = TrainingPlanRecipe(
                id = "wendler-531-bbb",
                weeks = emptyList(),
                progression = ProgressionRule.AmrapDrivenTm(),
                autoregulationHooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)),
            ),
            macrocycles = listOf(Macrocycle(id = "mac", name = "Mac", blocks = listOf(block))),
        )
        val ctx = buildLivePlanContext(program, "w2", block)
        assertEquals("wendler-531-bbb", ctx.sourceProtocolId)
        assertEquals(ProgramMode.POWERLIFTING, ctx.mode)
        assertEquals(TrainingPhase.REALIZATION, ctx.trainingPhase)
        assertEquals(BlockGoal.INTENSIFICATION, ctx.blockGoal)
        assertEquals(BlockProgressionScheme.PERCENT_RM, ctx.blockProgressionScheme)
        assertEquals(2, ctx.weekIndexInBlock)
        assertEquals(2, ctx.weeksInBlock)
        assertTrue(ctx.progression is ProgressionRule.AmrapDrivenTm)
        assertEquals(1, ctx.autoregulationHooks.size)
        assertTrue(!ctx.sourceProtocolName.isNullOrBlank())
    }

    @Test
    fun hydrator_without_protocol_leaves_name_null() {
        val week = ProgramWeek(id = "w1", name = "S1")
        val block = Block(
            id = "b1",
            name = "Libre",
            mesocycles = listOf(Mesocycle(id = "m1", name = "M1", weeks = listOf(week))),
        )
        val program = Program(
            id = "p",
            name = "Custom",
            macrocycles = listOf(Macrocycle(id = "mac", name = "Mac", blocks = listOf(block))),
        )
        val ctx = buildLivePlanContext(program, "w1", block)
        assertNull(ctx.sourceProtocolId)
        assertNull(ctx.sourceProtocolName)
        assertEquals(1, ctx.weekIndexInBlock)
    }
}
