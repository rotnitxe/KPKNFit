package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/** C4c: con competición futura o de esta semana, ni el pico ni el taper proponen el test de 1RM. */
class BlockTransitionEngineCompetitionTest {

    /** Lunes 5 a viernes 9 de octubre de 2026: la semana de hoy. */
    private val today = LocalDate.of(2026, 10, 9)

    private fun prescribedSession(id: String): Session = Session(
        id = id,
        name = "Sesión",
        isMainSession = true,
        exercises = listOf(
            Exercise(
                id = "ex_$id",
                name = "Sentadilla",
                isCompetitionLift = true,
                sets = listOf(
                    ExerciseSet(
                        id = "set_$id",
                        targetReps = 5,
                        targetPercentageRM = 75.0,
                        targetRPE = 7.0,
                        intensityMode = IntensityMode.SOLO_RM,
                    ),
                ),
            ),
        ),
    )

    private fun complexProgram(blockGoals: List<BlockGoal>): Program {
        val blocks = blockGoals.mapIndexed { index, goal ->
            Block(
                id = "b$index",
                name = "Bloque $index",
                goal = goal,
                progressionScheme = BlockProgressionScheme.PERCENT_RM,
                mesocycles = listOf(
                    Mesocycle(
                        id = "m$index",
                        name = "Meso $index",
                        goal = MesocycleGoal.ACCUMULATION,
                        weeks = listOf(
                            ProgramWeek(
                                id = "w${index}_1",
                                name = "Semana 1",
                                progressionIndex = 1,
                                sessions = listOf(prescribedSession("s${index}_1")),
                            ),
                        ),
                    ),
                ),
            )
        }
        return Program(
            id = "prog-complex",
            name = "Complex",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle(id = "mac1", name = "Macro", blocks = blocks)),
        )
    }

    private fun Program.withCompetition(meetIso: String): Program = copy(
        keyDates = listOf(
            ProgramKeyDate(
                id = "kd-meet",
                title = "Competición",
                type = KeyDateType.COMPETITION,
                startDate = meetIso,
                eventDate = meetIso,
            ),
        ),
    )

    private fun completeLogs(program: Program, blockId: String): List<WorkoutLog> {
        val block = program.macrocycles.first().blocks.first { it.id == blockId }
        return block.mesocycles.flatMap { it.weeks }.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}",
                    programId = program.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-08-01T10:00:00.000Z",
                    durationMinutes = 60,
                    weekId = week.id,
                )
            }
        }
    }

    private fun evaluate(program: Program, blockId: String, on: LocalDate = today) =
        BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = blockId,
            logs = completeLogs(program, blockId),
            context = BlockTransitionEngine.TransitionContext(today = on),
        )

    @Test
    fun competicion_futura_no_propone_1rm_tras_el_pico() {
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
            .withCompetition("2026-11-20")
        val decision = evaluate(program, "b0")
        assertFalse(decision.kind == BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST)
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b1", decision.nextBlockId)
    }

    @Test
    fun competicion_futura_no_propone_1rm_tras_el_taper() {
        val program = complexProgram(listOf(BlockGoal.PEAK, BlockGoal.TAPER, BlockGoal.ACCUMULATION))
            .withCompetition("2026-11-20")
        val decision = evaluate(program, "b1")
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b2", decision.nextBlockId)
    }

    @Test
    fun competicion_de_esta_semana_no_propone_1rm_tras_el_pico() {
        // Hoy es viernes 9: la semana empieza el lunes 5. El miércoles 7 sigue siendo «esta semana».
        val program = complexProgram(listOf(BlockGoal.PEAK, BlockGoal.TAPER)).withCompetition("2026-10-07")
        val decision = evaluate(program, "b0")
        assertFalse(decision.kind == BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST)
    }

    @Test
    fun competicion_pasada_de_la_semana_anterior_mantiene_el_1rm_tras_el_pico() {
        // El domingo 4 (semana anterior) ya no cuenta: el test vuelve a proponerse como hasta ahora.
        val program = complexProgram(listOf(BlockGoal.REALIZATION, BlockGoal.ACCUMULATION))
            .withCompetition("2026-10-04")
        val decision = evaluate(program, "b0")
        assertEquals(BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST, decision.kind)
    }

    @Test
    fun sin_competicion_el_test_tras_el_taper_sigue_proponiendose() {
        val program = complexProgram(listOf(BlockGoal.PEAK, BlockGoal.TAPER, BlockGoal.ACCUMULATION))
        val decision = evaluate(program, "b1")
        assertEquals(BlockTransitionEngine.DecisionKind.PROPOSE_1RM_TEST, decision.kind)
    }
}
