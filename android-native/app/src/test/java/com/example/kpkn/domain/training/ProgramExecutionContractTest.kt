package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.models.WeekExecutionKind
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramExecutionContractTest {

    private fun program(week: ProgramWeek, block: Block = Block(
        id = "b",
        name = "Bloque",
        mesocycles = listOf(Mesocycle(id = "m", name = "Meso", weeks = listOf(week))),
    )): Program = Program(
        id = "p",
        name = "Programa",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(Macrocycle(id = "mc", name = "Macro", blocks = listOf(block))),
    )

    @Test
    fun empty_training_week_is_invalid_but_explicit_rest_is_valid() {
        val trainingIssues = ProgramExecutionContract.validate(
            program(ProgramWeek(id = "w", name = "Vacía")),
        )
        assertTrue(trainingIssues.any { it is ProgramExecutionIssue.EmptyTrainingWeek })

        val restIssues = ProgramExecutionContract.validate(
            program(ProgramWeek(id = "rest", name = "Descanso", executionKind = WeekExecutionKind.REST)),
        )
        assertTrue(restIssues.none { it is ProgramExecutionIssue.EmptyTrainingWeek })
    }

    @Test
    fun stale_cursor_and_pending_materialization_are_blocking() {
        val source = program(
            ProgramWeek(id = "w", name = "Semana", sessions = listOf(Session(id = "s", name = "Día"))),
            block = Block(
                id = "b",
                name = "Bloque",
                materializationPending = true,
                mesocycles = listOf(Mesocycle(id = "m", name = "Meso", weeks = listOf(
                    ProgramWeek(id = "w", name = "Semana", sessions = listOf(Session(id = "s", name = "Día"))),
                ))),
            ),
        ).copy(runState = ProgramRunState(runId = "run", weekId = "gone", blockId = "gone-block"))
        val issues = ProgramExecutionContract.validate(source)
        assertTrue(issues.any { it is ProgramExecutionIssue.PendingMaterialization })
        assertTrue(issues.any { it is ProgramExecutionIssue.StaleCursor })
        try {
            ProgramExecutionContract.requireExecutable(source)
            error("Se esperaba bloqueo de plan no ejecutable")
        } catch (_: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun empty_user_draft_session_is_not_executable() {
        val issues = ProgramExecutionContract.validate(
            program(
                ProgramWeek(
                    id = "draft-week",
                    name = "Borrador sin contenido",
                    sessions = listOf(
                        Session(
                            id = "draft-session",
                            name = "USER_DRAFT",
                            origin = SessionOrigin.USER_DRAFT,
                        ),
                    ),
                ),
            ),
        )
        assertTrue(issues.any { it is ProgramExecutionIssue.EmptyTrainingWeek })
    }

    @Test
    fun visible_placeholders_and_duration_targets_do_not_validate_a_training_week() {
        val invalidSessions = listOf(
            Session(id = "placeholder", name = "Ejercicio sin sets", exercises = listOf(Exercise(id = "e", name = "Press"))),
            Session(
                id = "cardio-empty",
                name = "Cardio sin receta",
                parts = listOf(SessionPart("cardio", "Cardio", isCardioGroup = true, targetDurationMinutes = 20)),
            ),
            Session(
                id = "mobility-empty",
                name = "Movilidad sin receta",
                parts = listOf(SessionPart("mobility", "Movilidad", isMobilityGroup = true, targetDurationMinutes = 20)),
            ),
            Session(id = "target-only", name = "Duración sin actividad", targetDurationMinutes = 20),
        )
        invalidSessions.forEach { session ->
            val issues = ProgramExecutionContract.validate(
                program(ProgramWeek(id = "${session.id}-week", name = session.name, sessions = listOf(session))),
            )
            assertTrue("${session.id} no debe validar", issues.any { it is ProgramExecutionIssue.EmptyTrainingWeek })
        }
    }

    @Test
    fun required_placeholder_blocks_week_even_when_another_session_is_valid() {
        val valid = Session(
            id = "valid",
            name = "Fuerza válida",
            exercises = listOf(
                Exercise(
                    id = "valid-exercise",
                    name = "Sentadilla",
                    sets = listOf(ExerciseSet("valid-set", targetReps = 5)),
                ),
            ),
        )
        val placeholder = Session(
            id = "required-placeholder",
            name = "Sesión requerida sin receta",
            requirement = SessionRequirement.REQUIRED,
            origin = SessionOrigin.GENERATED_PLACEHOLDER,
        )
        val requiredIssues = ProgramExecutionContract.validate(
            program(ProgramWeek(id = "mixed-required", name = "Mixta", sessions = listOf(valid, placeholder))),
        )
        assertTrue(requiredIssues.any { it is ProgramExecutionIssue.EmptyTrainingWeek })

        val optionalIssues = ProgramExecutionContract.validate(
            program(ProgramWeek(
                id = "mixed-optional",
                name = "Mixta opcional",
                sessions = listOf(valid, placeholder.copy(requirement = SessionRequirement.OPTIONAL)),
            )),
        )
        assertTrue(optionalIssues.none { it is ProgramExecutionIssue.EmptyTrainingWeek })
    }

    @Test
    fun real_multimodal_prescriptions_validate_a_training_week() {
        val sessions = listOf(
            Session(
                id = "strength",
                name = "Fuerza",
                exercises = listOf(Exercise("strength-e", "Sentadilla", sets = listOf(ExerciseSet("strength-set", targetReps = 5)))),
            ),
            Session(
                id = "cardio",
                name = "Cardio",
                exercises = listOf(Exercise("cardio-e", "Cinta", cardioDetails = CardioDetails(CardioType.TREADMILL, targetDurationSeconds = 600))),
            ),
            Session(
                id = "mobility",
                name = "Movilidad",
                parts = listOf(SessionPart("mobility-part", "Movilidad", isMobilityGroup = true, mobilitySeries = listOf(
                    MobilitySeries("mobility-series", name = "Cadera", sets = 1, durationSeconds = 60),
                ))),
            ),
            Session(
                id = "warmup",
                name = "Calentamiento",
                warmup = listOf(WarmupExercise("warmup-e", "Bicicleta", duration = 300)),
            ),
        )
        val issues = ProgramExecutionContract.validate(
            program(ProgramWeek(id = "multi-week", name = "Multimodal", sessions = sessions)),
        )
        assertTrue(issues.none { it is ProgramExecutionIssue.EmptyTrainingWeek })
    }

    @Test
    fun phase_order_restarts_for_each_macrocycle() {
        val restWeek = { id: String ->
            ProgramWeek(id = id, name = id, executionKind = WeekExecutionKind.REST)
        }
        val firstMacro = Macrocycle(
            id = "macro-1",
            name = "Macro 1",
            blocks = listOf(
                Block(
                    id = "m1-peak",
                    name = "Pico 1",
                    goal = BlockGoal.PEAK,
                    mesocycles = listOf(Mesocycle(id = "m1p", name = "Pico", weeks = listOf(restWeek("m1p-w")))),
                ),
                Block(
                    id = "m1-taper",
                    name = "Taper 1",
                    goal = BlockGoal.TAPER,
                    mesocycles = listOf(Mesocycle(id = "m1t", name = "Taper", weeks = listOf(restWeek("m1t-w")))),
                ),
            ),
        )
        val secondMacro = Macrocycle(
            id = "macro-2",
            name = "Macro 2",
            blocks = listOf(
                Block(
                    id = "m2-base",
                    name = "Base 2",
                    goal = BlockGoal.ACCUMULATION,
                    mesocycles = listOf(Mesocycle(id = "m2a", name = "Base", weeks = listOf(restWeek("m2a-w")))),
                ),
            ),
        )
        val program = Program(
            id = "multi-macro",
            name = "Multi macro",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(firstMacro, secondMacro),
        )
        assertTrue(
            ProgramExecutionContract.validate(program).none { it is ProgramExecutionIssue.PhaseOrder },
        )
    }
}
