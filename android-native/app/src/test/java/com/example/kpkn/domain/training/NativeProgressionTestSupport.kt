package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate

/**
 * Ayudas JVM para probar la progresión por exposiciones (§12.4) sobre planes REALES
 * (`SimpleCyclePersonalizer` + `PlanMaterializer`): navegar el programa generado y
 * fabricar logs completos a partir de lo que el plan prescribe, igual que el flujo de
 * producción (los planes COMPLEX sellan `cycleNumber = 1`; el ciclo real viaja en el id
 * de instancia de semana `inst_c<N>_<semana>`).
 */
internal object NativeProgressionTestSupport {
    fun weeksOf(program: Program): List<ProgramWeek> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    /** Sesión `day` (1-based) de la semana `week` (1-based) en el orden materializado. */
    fun sessionOf(program: Program, week: Int, day: Int = 1): Session = weeksOf(program)[week - 1].sessions[day - 1]

    fun exercisesOf(program: Program, sessionId: String): List<Exercise> =
        ProgramHierarchyIndex(program).locateSession(sessionId)?.session?.allExercises()
            ?: error("La sesión $sessionId no existe en el programa")

    /** Ejercicios F/H/I con progresión nativa gestionada de una sesión. */
    fun managedOf(program: Program, sessionId: String): List<Exercise> =
        exercisesOf(program, sessionId).filter { it.nativeProgressionManaged }

    fun logDate(dayOffset: Int): String = LocalDate.of(2026, 1, 1).plusDays(dayOffset.toLong()).toString() + "T10:00:00Z"

    /**
     * Log completo de una sesión: todas las series de trabajo de cada ejercicio gestionado, en el
     * tope del rango y con el RIR objetivo salvo que se indique otra cosa. `loadKg == null` registra
     * el ejercicio como peso corporal (sin carga externa).
     */
    fun completedLog(
        program: Program,
        sessionId: String,
        logId: String,
        dayOffset: Int,
        cycle: Int = 1,
        loadKg: (Exercise) -> Double? = { 60.0 },
        repsOf: (ExerciseSet) -> Int = { set -> set.targetRepsRange?.max ?: set.targetReps ?: 0 },
        rirOf: (ExerciseSet) -> Int? = { set -> set.targetRIR },
    ): WorkoutLog {
        val location = ProgramHierarchyIndex(program).locateSession(sessionId)
            ?: error("La sesión $sessionId no existe en el programa")
        val week = location.hierarchy.week
        val completed = location.session.allExercises().filter { it.nativeProgressionManaged }.map { exercise ->
            val load = loadKg(exercise)
            val mode = if (load == null) LoadModeV2.BODYWEIGHT else LoadModeV2.LOAD
            CompletedExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                catalogConfigurationId = exercise.catalogConfigurationId,
                sets = exercise.sets.mapIndexed { index, set ->
                    val reps = repsOf(set)
                    val rir = rirOf(set)
                    CompletedSet(
                        id = "$logId-${exercise.id}-$index",
                        weight = load ?: 0.0,
                        reps = reps,
                        rir = rir,
                        recordedPayloadV3 = RecordedSetPayload(
                            exerciseId = exercise.id,
                            side = "bilateral",
                            loadInputMode = mode,
                            unitMode = UnitModeV2.REPS,
                            externalLoad = load,
                            completedReps = reps,
                            actualIntensityMode = IntensityMode.RIR,
                            actualIntensityValue = rir?.toDouble(),
                        ),
                    )
                },
            )
        }
        return WorkoutLog(
            id = logId,
            programId = program.id,
            sessionId = sessionId,
            sessionName = location.session.name,
            date = logDate(dayOffset),
            durationMinutes = 50,
            completedExercises = completed,
            weekId = week.id,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
            // Un plan COMPLEX sella cycleNumber = 1 en todos sus logs (ProgramRepository.finalizeWorkout).
            cycleNumber = 1,
        )
    }
}
