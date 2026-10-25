package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramStructureMutationResult
import com.example.kpkn.domain.training.ProgramStructureMutator

internal object HybridCalendarFixtures {
    fun program(dated: Boolean = false): Program {
        val strength = Session(
            "strength-session", "Sesión redactada", description = "Conservar íntegra",
            exercises = listOf(Exercise("squat", "Squat", sets = listOf(ExerciseSet("set", targetReps = 5)))),
            dayOfWeek = 2, assignedDays = listOf(2), targetDurationMinutes = 45, placeId = "GYM",
            sessionB = Session("strength-b", "Alternativa B", targetDurationMinutes = 30),
            scheduleLabel = "Preferencia personal", lastModifiedAtMs = 123,
        )
        val running = Session(
            "run-session", "Sesión de carrera", dayOfWeek = 4, assignedDays = listOf(4),
            targetDurationMinutes = 30, placeId = "PUBLIC",
            exercises = listOf(Exercise("run", "Run", sets = listOf(ExerciseSet("run-set", targetReps = 1)))),
        )
        val otherSession = Session(
            "other-session", "Intacta", dayOfWeek = 5, assignedDays = listOf(5),
            exercises = listOf(Exercise("other-ex", "Other", sets = listOf(ExerciseSet("other-set", targetReps = 8)))),
        )
        val meso = Mesocycle("meso", "Meso", weeks = listOf(
            ProgramWeek("week", "Semana elegida", sessions = listOf(strength, running)),
            ProgramWeek("other-week", "Semana intacta", sessions = listOf(otherSession)),
        ))
        val block = Block("block", "Bloque", mesocycles = listOf(meso))
        val raw = Program(
            id = "hybrid-calendar", name = "Programa existente", author = "Persona",
            trainingIntent = TrainingIntent(
                goals = listOf(
                    SportGoal("strength", SportId.STRENGTH, "Progresar", GoalPriority.PROGRESS, SportExperience.ADVANCED),
                    SportGoal("running", SportId.RUNNING, "Mantener", GoalPriority.MAINTAIN, SportExperience.BEGINNER),
                ), primaryGoalId = "strength",
            ),
            hybridPlanning = HybridPlanningConfiguration(availableWindows = listOf(
                AvailableTrainingWindow("gym-mon", 1, 480, 600, "GYM", setOf("barbell")),
                AvailableTrainingWindow("park-mon", 1, 720, 780, "PUBLIC", emptySet()),
            )),
            simpleProgramKind = if (dated) SimpleProgramKind.CALENDARIZED else SimpleProgramKind.CYCLIC,
            calendarization = if (dated) ProgramCalendarEngine.defaultSimpleDatedCalendarization() else null,
            timelineStartDate = if (dated) "2026-10-19" else null,
            schedulePlan = ProgramSchedulePlan(
                anchorDate = if (dated) "2026-10-19" else null,
                trainingDays = setOf(2, 4, 5), mode = if (dated) ScheduleMode.DATED else ScheduleMode.FLOATING,
            ),
            macrocycles = listOf(Macrocycle("macro", "Macro", blocks = listOf(block))),
        )
        return if (dated) ProgramCalendarEngine.materializeWeekDates(raw) else raw
    }

    fun prepared(program: Program = program()): Program {
        val proposal = (HybridProgramPlanner.prepare(program, "week", listOf(
            HybridSessionGoalBinding("strength-session", "strength", requiredEquipment = setOf("barbell")),
            HybridSessionGoalBinding("run-session", "running"),
        )) as HybridProgramPlanResult.Ready).proposal
        return replaceWeek(program) { it.copy(hybridProposal = proposal) }
    }

    fun week(program: Program, id: String = "week") = ProgramHierarchyIndex(program).locateWeek(id)!!.week
    fun replaceWeek(program: Program, edit: (ProgramWeek) -> ProgramWeek): Program =
        (ProgramStructureMutator.updateWeek(program, "week", edit) as ProgramStructureMutationResult.Success).program
    fun request(program: Program) = HybridCalendarAcceptanceRequest(
        program.id, "week", week(program).hybridProposal!!, HybridCalendarApplicationScope.BEFORE_FIRST_RUN_TEMPLATE,
    )
    fun active(program: Program = prepared()): Program = program.copy(runState = ProgramRunState(
        "run", cycleNumber = 3, weekId = "week", weekInstanceId = "inst_c3_week",
    ))
    fun activeRequest(program: Program) = request(program).copy(
        scope = HybridCalendarApplicationScope.ACTIVE_WEEK_OCCURRENCE,
        expectedRunId = "run", expectedCycleNumber = 3, expectedWeekInstanceId = "inst_c3_week",
        occurrenceWeekStartDate = "2026-10-19",
    )
    fun applyActive(program: Program = active()): Program =
        (HybridCalendarApplicator.apply(HybridCalendarSourceSnapshot(program), activeRequest(program),
            java.time.LocalDate.parse("2026-10-18"), 123456L) as HybridCalendarApplicationResult.Applied).program
}
