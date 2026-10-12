package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AlgorithmSettings
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Paquete 1.12a: la descarga automática solo ocurre si la persona activó `augeAutoDeload`.
 * Con el ajuste apagado, ni la estrés EMA ni los músculos sobrecargados insertan un bloque «Descarga (auto)».
 */
class BlockTransitionAutoDeloadFlagTest {

    /** Fecha fija: el valor por defecto de `TransitionContext.today` lee el reloj real. */
    private val today = LocalDate.of(2026, 10, 12)

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

    /** Programa COMPLEX de dos bloques (acumulación e intensificación), cada uno con una sesión ejecutable. */
    private fun complexProgram(): Program {
        val blocks = listOf(BlockGoal.ACCUMULATION, BlockGoal.INTENSIFICATION).mapIndexed { index, goal ->
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

    /** Cierra el bloque b0 (ya completo) con el ajuste indicado y solo las señales pedidas. */
    private fun closeBlock0(
        augeAutoDeload: Boolean,
        mesocycleStressEma: Double = 0.0,
        overtrainedMuscles: List<String> = emptyList(),
    ): BlockTransitionEngine.TransitionDecision {
        val program = complexProgram()
        return BlockTransitionEngine.evaluate(
            program = program,
            completedBlockId = "b0",
            logs = completeLogs(program, "b0"),
            context = BlockTransitionEngine.TransitionContext(
                settings = Settings(algorithmSettings = AlgorithmSettings(augeAutoDeload = augeAutoDeload)),
                mesocycleStressEma = mesocycleStressEma,
                overtrainedMuscles = overtrainedMuscles,
                today = today,
            ),
        )
    }

    /** Sin bloque de descarga: el programa conserva solo sus dos bloques originales. */
    private fun assertNoDeloadBlock(decision: BlockTransitionEngine.TransitionDecision) {
        val blocks = decision.updatedProgram?.macrocycles?.flatMap { it.blocks }.orEmpty()
        assertEquals(listOf("b0", "b1"), blocks.map { it.id })
    }

    /** Bloque «Descarga (auto)» insertado entre el b0 y el b1, y apuntado como siguiente bloque. */
    private fun assertDeloadInserted(decision: BlockTransitionEngine.TransitionDecision) {
        val blocks = decision.updatedProgram!!.macrocycles.flatMap { it.blocks }
        val deload = blocks.first { it.name == "Descarga (auto)" }
        assertEquals(BlockGoal.DELOAD, deload.goal)
        assertEquals(deload.id, decision.nextBlockId)
        assertEquals(listOf("b0", deload.id, "b1"), blocks.map { it.id })
    }

    @Test
    fun autoDeloadOffStressAloneDoesNotInsertDeload() {
        val decision = closeBlock0(augeAutoDeload = false, mesocycleStressEma = 90.0)
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b1", decision.nextBlockId)
        assertNoDeloadBlock(decision)
    }

    @Test
    fun autoDeloadOffOvertrainingAloneDoesNotInsertDeload() {
        val decision = closeBlock0(augeAutoDeload = false, overtrainedMuscles = listOf("Cuádriceps"))
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b1", decision.nextBlockId)
        assertNoDeloadBlock(decision)
    }

    @Test
    fun autoDeloadOnStressInsertsDeloadBlock() {
        val decision = closeBlock0(augeAutoDeload = true, mesocycleStressEma = 90.0)
        assertEquals(BlockTransitionEngine.DecisionKind.INSERT_DELOAD, decision.kind)
        assertDeloadInserted(decision)
    }

    @Test
    fun autoDeloadOnOvertrainingInsertsDeloadBlock() {
        val decision = closeBlock0(augeAutoDeload = true, overtrainedMuscles = listOf("Cuádriceps"))
        assertEquals(BlockTransitionEngine.DecisionKind.INSERT_DELOAD, decision.kind)
        assertDeloadInserted(decision)
    }

    @Test
    fun autoDeloadOnWithoutSignalsAdvancesToNextBlock() {
        val decision = closeBlock0(augeAutoDeload = true)
        assertEquals(BlockTransitionEngine.DecisionKind.ADVANCE_NEXT_BLOCK, decision.kind)
        assertEquals("b1", decision.nextBlockId)
        assertNoDeloadBlock(decision)
    }
}
