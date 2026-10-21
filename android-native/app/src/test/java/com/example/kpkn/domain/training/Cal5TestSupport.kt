package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe

/** Ayudas compartidas por las pruebas de CAL5 y CAL6: programas materializados, registros y recetas publicadas. */
internal object Cal5TestSupport {
    class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "id_${++n}"
    }

    fun recipeOf(id: String): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == id }.recipe!!

    fun templateRecipeOf(id: String): TrainingPlanRecipe = PROGRAM_TEMPLATES.first { it.id == id }.recipe!!

    /** 1RM 200 / 120 / 220 / 80 y TM 180 / 108 / 198 / 72 (el 90 %). */
    fun fullProfile() = PowerliftingProfile(
        squat1RM = 200.0, squatTM = 180.0,
        bench1RM = 120.0, benchTM = 108.0,
        deadlift1RM = 220.0, deadliftTM = 198.0,
        overhead1RM = 80.0, overheadTM = 72.0,
    )

    fun materialize(
        recipe: TrainingPlanRecipe,
        mode: AutoregulationMode = AutoregulationMode.PROPOSE,
        profile: PowerliftingProfile? = fullProfile(),
        id: String = "cal5",
    ): Program = PlanMaterializer.materialize(
        Program(id = id, name = "CAL5 $id"),
        recipe,
        CatalogCompositionTestSupport.metadata,
        SeqIds(),
        profile = profile,
    ).copy(autoregulationMode = mode)

    fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** El ejercicio de la sesión con ese rol y esa configuración del catálogo. */
    fun exerciseOf(session: Session, configurationId: String, role: SlotRole = SlotRole.T1_MAIN): Exercise =
        session.allExercises().first { it.slotRole == role && it.catalogConfigurationId == configurationId }

    /** Un registro vacío por sesión de [weeks]: la semana queda entrenada sin series. */
    fun emptyLogs(program: Program, weeks: List<ProgramWeek>, cycle: Int = 1, runId: String? = null): List<WorkoutLog> =
        weeks.flatMap { week ->
            week.sessions.map { session ->
                WorkoutLog(
                    id = "log_${session.id}_c$cycle",
                    programId = program.id,
                    sessionId = session.id,
                    sessionName = session.name,
                    date = "2026-01-01T10:00:00.000Z",
                    durationMinutes = 45,
                    weekId = week.id,
                    cycleNumber = cycle,
                    weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
                    programRunId = runId,
                )
            }
        }

    /**
     * Registro de [session] con [exercise] hecho: cada serie del plan con su carga ([weightOf] o la prescrita) y las repeticiones de
     * [repsOf] (por defecto la repetición objetivo). Las series AMRAP del plan se marcan como AMRAP y llevan [rir] si se da.
     */
    fun exerciseLog(
        program: Program,
        week: ProgramWeek,
        session: Session,
        exercise: Exercise,
        cycle: Int = 1,
        runId: String? = null,
        rir: Int? = null,
        weightOf: (index: Int, planned: com.example.kpkn.data.models.ExerciseSet) -> Double? = { _, planned -> planned.weight },
        repsOf: (index: Int, planned: com.example.kpkn.data.models.ExerciseSet) -> Int = { _, planned -> planned.targetReps ?: 5 },
    ): WorkoutLog = WorkoutLog(
        id = "log_${session.id}_c$cycle",
        programId = program.id,
        sessionId = session.id,
        sessionName = session.name,
        date = "2026-01-01T10:00:00.000Z",
        durationMinutes = 45,
        weekId = week.id,
        cycleNumber = cycle,
        weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
        programRunId = runId,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                catalogConfigurationId = exercise.catalogConfigurationId,
                sets = exercise.sets.mapIndexed { index, planned ->
                    CompletedSet(
                        id = "set$index",
                        weight = weightOf(index, planned) ?: 60.0,
                        reps = repsOf(index, planned),
                        amrapPerformed = planned.purpose.isAmrap,
                        rir = if (planned.purpose.isAmrap) rir else null,
                    )
                },
            ),
        ),
    )

    /** [logs] con el registro de la sesión [session] sustituido por [replacement]. */
    fun replacingLogOf(logs: List<WorkoutLog>, session: Session, replacement: WorkoutLog): List<WorkoutLog> =
        logs.map { if (it.sessionId == session.id) replacement else it }
}
