package com.example.kpkn.screens.onboarding.design.entreno.plan

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.screens.onboarding.SetupPlanReveals
import com.example.kpkn.screens.onboarding.entreno.toPhaseModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Entreno v2 (D2c) · la franja de fases del detalle: un programa de varias semanas enseña sus fases con sus semanas, y uno de
 * una sola semana no enseña ninguna franja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class PlanPhasesStripTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aSixWeekProgramShowsItsThreePhasesWithTheirWeeks() {
        val phases = SetupPlanReveals.phasesOf(
            programOf(
                block("Acumulación", BlockGoal.ACCUMULATION, weeks = 3),
                block("Intensificación", BlockGoal.INTENSIFICATION, weeks = 2),
                block("Descarga", BlockGoal.DELOAD, weeks = 1),
            ),
        )
        assertEquals(listOf(3, 2, 1), phases.map { it.weeks })
        rule.setContent { PlanPhasesStrip(phases.map { it.toPhaseModel() }) }
        rule.onNodeWithTag(PLAN_PHASES_TAG).assertExists()
        rule.onNodeWithContentDescription("Fase 1 de 3: Acumulación, 3 semanas").assertExists()
        rule.onNodeWithContentDescription("Fase 2 de 3: Intensificación, 2 semanas").assertExists()
        rule.onNodeWithContentDescription("Fase 3 de 3: Descarga, 1 semana").assertExists()
    }

    @Test
    fun aSingleWeekProgramShowsNoStrip() {
        val phases = SetupPlanReveals.phasesOf(programOf(block("Semana", BlockGoal.ACCUMULATION, weeks = 1)))
        assertEquals(emptyList<Int>(), phases.map { it.weeks })
        rule.setContent { PlanPhasesStrip(phases.map { it.toPhaseModel() }) }
        rule.onNodeWithTag(PLAN_PHASES_TAG).assertDoesNotExist()
    }

    /** Un bloque con una sola mesociclo de [weeks] semanas (sin sesiones: la franja solo mira las semanas). */
    private fun block(name: String, goal: BlockGoal, weeks: Int): Block = Block(
        id = "block-$name",
        name = name,
        goal = goal,
        mesocycles = listOf(
            Mesocycle(
                id = "meso-$name",
                name = name,
                weeks = (1..weeks).map { number -> ProgramWeek(id = "week-$name-$number", name = "Semana $number") },
            ),
        ),
    )

    private fun programOf(vararg blocks: Block): Program = Program(
        id = "phases-test",
        name = "Programa de prueba",
        startDay = 1,
        macrocycles = listOf(Macrocycle(id = "macro", name = "Mi plan", blocks = blocks.toList())),
        schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(1, 3, 5)),
    )
}
