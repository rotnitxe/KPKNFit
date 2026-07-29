package com.example.kpkn.domain.energy

import com.example.kpkn.data.exercises.setCustomExerciseOverlay
import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrainingEnergyEngineTest {

    @Before
    fun seedCatalog() {
        setCustomExerciseOverlay(
            listOf(
                ExerciseMuscleInfo(
                    id = "quads_sentadilla_trasera_barra_alta",
                    name = "Sentadilla Trasera Barra Alta",
                    type = "Básico",
                    force = "Sentadilla",
                    equipment = "Barra",
                    bodyPart = "lower",
                    movementPattern = "sentadilla",
                    efc = 4.2,
                    cnc = 4.0,
                    ssc = 1.5,
                    ttc = 2.5,
                    coreInvolvement = "high",
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Cuádriceps", role = MuscleRole.PRIMARY),
                        InvolvedMuscle(muscle = "Glúteo mayor", role = MuscleRole.PRIMARY),
                    ),
                ),
                ExerciseMuscleInfo(
                    id = "hamstrings_rdl_barra",
                    name = "Peso Muerto Rumano con Barra",
                    type = "Básico",
                    force = "Bisagra",
                    equipment = "Barra",
                    bodyPart = "lower",
                    movementPattern = "bisagra / peso muerto",
                    efc = 3.8,
                    cnc = 3.5,
                    ssc = 1.4,
                    ttc = 2.2,
                    coreInvolvement = "high",
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Isquiosurales", role = MuscleRole.PRIMARY),
                        InvolvedMuscle(muscle = "Glúteo mayor", role = MuscleRole.PRIMARY),
                    ),
                ),
                ExerciseMuscleInfo(
                    id = "quads_prensa_45",
                    name = "Prensa de Piernas 45°",
                    type = "Accesorio",
                    force = "Sentadilla",
                    equipment = "Máquina",
                    bodyPart = "lower",
                    movementPattern = "prensa",
                    efc = 3.2,
                    cnc = 2.5,
                    ssc = 0.6,
                    ttc = 1.5,
                    technicalDifficulty = 2.0,
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Cuádriceps", role = MuscleRole.PRIMARY),
                    ),
                ),
                ExerciseMuscleInfo(
                    id = "hamstrings_curl_maquina",
                    name = "Curl Femoral en Máquina",
                    type = "Aislamiento",
                    force = "Monoarticular",
                    equipment = "Máquina",
                    bodyPart = "lower",
                    movementPattern = "curl femoral",
                    efc = 2.4,
                    cnc = 1.8,
                    ssc = 0.2,
                    ttc = 1.0,
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Isquiosurales", role = MuscleRole.PRIMARY),
                    ),
                ),
                ExerciseMuscleInfo(
                    id = "calves_elevacion_maquina",
                    name = "Elevación de Gemelos en Máquina",
                    type = "Aislamiento",
                    force = "Monoarticular",
                    equipment = "Máquina",
                    bodyPart = "lower",
                    movementPattern = "gemelos",
                    efc = 2.0,
                    cnc = 1.5,
                    ssc = 0.2,
                    ttc = 0.8,
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Gemelos", role = MuscleRole.PRIMARY),
                    ),
                ),
                ExerciseMuscleInfo(
                    id = "biceps_curl_de_pie_supino_barra_recta",
                    name = "Curl de Bíceps de Pie con Barra Recta",
                    type = "Aislamiento",
                    force = "Monoarticular",
                    equipment = "Barra",
                    bodyPart = "upper",
                    movementPattern = "curl",
                    efc = 2.2,
                    cnc = 1.6,
                    ssc = 0.1,
                    ttc = 0.8,
                    involvedMuscles = listOf(
                        InvolvedMuscle(muscle = "Bíceps", role = MuscleRole.PRIMARY),
                    ),
                ),
            ),
        )
    }

    private fun buildCompletedSet(
        weight: Double = 60.0,
        reps: Int = 8,
        rpe: Double? = 7.0,
        isFailure: Boolean = false,
        isFailedSet: Boolean = false,
    ): CompletedSet = CompletedSet(
        id = "",
        weight = weight,
        reps = reps,
        rpe = rpe,
        isFailure = isFailure,
        isFailedSet = isFailedSet,
    )

    private fun buildCompletedExercise(
        name: String,
        sets: List<CompletedSet>,
        exerciseDbId: String,
        restTime: Int = 90,
    ): CompletedExercise = CompletedExercise(
        exerciseId = exerciseDbId,
        exerciseName = name,
        exerciseDbId = exerciseDbId,
        restTime = restTime,
        sets = sets,
    )

    private fun buildSettings(weight: Double? = 80.0): Settings =
        Settings(userVitals = UserVitals(weight = weight))

    private fun sets(count: Int, weight: Double, reps: Int, rpe: Double) =
        List(count) { buildCompletedSet(weight = weight, reps = reps, rpe = rpe) }

    private fun plannedSets(count: Int, weight: Double?, reps: Int, rpe: Double?) =
        List(count) {
            ExerciseSet(
                id = "s$it",
                weight = weight,
                targetReps = reps,
                targetRPE = rpe,
            )
        }

    private fun heavyLegSession(withWeights: Boolean, withReference1Rm: Boolean = false): Session {
        fun ex(
            id: String,
            name: String,
            dbId: String,
            weight: Double?,
            reps: Int,
            rpe: Double,
            setCount: Int,
            rest: Int,
            reference1RM: Double? = null,
        ) = Exercise(
            id = id,
            name = name,
            exerciseDbId = dbId,
            restTime = rest,
            reference1RM = reference1RM,
            sets = plannedSets(
                count = setCount,
                weight = if (withWeights) weight else null,
                reps = reps,
                rpe = rpe,
            ),
        )

        return Session(
            id = "leg-day",
            name = "Pierna pesada",
            exercises = listOf(
                ex(
                    id = "e1",
                    name = "Sentadilla Trasera Barra Alta",
                    dbId = "quads_sentadilla_trasera_barra_alta",
                    weight = 100.0,
                    reps = 8,
                    rpe = 8.0,
                    setCount = 4,
                    rest = 180,
                    reference1RM = if (withReference1Rm) 140.0 else null,
                ),
                ex(
                    id = "e2",
                    name = "Peso Muerto Rumano con Barra",
                    dbId = "hamstrings_rdl_barra",
                    weight = 100.0,
                    reps = 8,
                    rpe = 8.0,
                    setCount = 3,
                    rest = 150,
                    reference1RM = if (withReference1Rm) 140.0 else null,
                ),
                ex(
                    id = "e3",
                    name = "Prensa de Piernas 45°",
                    dbId = "quads_prensa_45",
                    weight = 180.0,
                    reps = 10,
                    rpe = 8.0,
                    setCount = 3,
                    rest = 120,
                    reference1RM = if (withReference1Rm) 240.0 else null,
                ),
                ex(
                    id = "e4",
                    name = "Curl Femoral en Máquina",
                    dbId = "hamstrings_curl_maquina",
                    weight = 50.0,
                    reps = 12,
                    rpe = 7.0,
                    setCount = 3,
                    rest = 90,
                    reference1RM = if (withReference1Rm) 70.0 else null,
                ),
                ex(
                    id = "e5",
                    name = "Elevación de Gemelos en Máquina",
                    dbId = "calves_elevacion_maquina",
                    weight = 80.0,
                    reps = 15,
                    rpe = 7.0,
                    setCount = 3,
                    rest = 60,
                    reference1RM = if (withReference1Rm) 110.0 else null,
                ),
            ),
        )
    }

    @Test
    fun `methodVersion is auge-energy-v2`() {
        val result = TrainingEnergyEngine.estimateLiveSession(
            listOf(
                buildCompletedExercise(
                    "Sentadilla Trasera Barra Alta",
                    sets(1, 100.0, 8, 8.0),
                    "quads_sentadilla_trasera_barra_alta",
                ),
            ),
            buildSettings(),
        )
        assertEquals(TrainingEnergyEngine.METHOD_VERSION, result.methodVersion)
        assertEquals("auge-energy-v2", result.methodVersion)
    }

    @Test
    fun `high RPE clearly increases kcal per set vs moderate RPE`() {
        val settings = buildSettings()
        val highRpe = TrainingEnergyEngine.estimateLiveSession(
            listOf(
                buildCompletedExercise(
                    "Sentadilla Trasera Barra Alta",
                    listOf(buildCompletedSet(weight = 80.0, reps = 8, rpe = 9.5)),
                    "quads_sentadilla_trasera_barra_alta",
                ),
            ),
            settings,
        )
        val moderateRpe = TrainingEnergyEngine.estimateLiveSession(
            listOf(
                buildCompletedExercise(
                    "Sentadilla Trasera Barra Alta",
                    listOf(buildCompletedSet(weight = 80.0, reps = 8, rpe = 7.0)),
                    "quads_sentadilla_trasera_barra_alta",
                ),
            ),
            settings,
        )

        assertTrue(
            "RPE alto debe generar mas kcal que RPE moderado (${highRpe.totalKcal.mid} vs ${moderateRpe.totalKcal.mid})",
            highRpe.totalKcal.mid > moderateRpe.totalKcal.mid,
        )
    }

    @Test
    fun `compound squat generates more kcal than isolation curl with same tonnage`() {
        val settings = buildSettings(weight = 80.0)
        val squatResult = TrainingEnergyEngine.estimateLiveSession(
            listOf(
                buildCompletedExercise(
                    "Sentadilla Trasera Barra Alta",
                    listOf(buildCompletedSet(weight = 100.0, reps = 8, rpe = 8.0)),
                    "quads_sentadilla_trasera_barra_alta",
                    restTime = 90,
                ),
            ),
            settings,
        )
        val curlResult = TrainingEnergyEngine.estimateLiveSession(
            listOf(
                buildCompletedExercise(
                    "Curl de Bíceps de Pie con Barra Recta",
                    listOf(buildCompletedSet(weight = 100.0, reps = 8, rpe = 8.0)),
                    "biceps_curl_de_pie_supino_barra_recta",
                    restTime = 90,
                ),
            ),
            settings,
        )

        assertTrue(
            "Sentadilla (${squatResult.totalKcal.mid} kcal) debe generar mas kcal que curl (${curlResult.totalKcal.mid} kcal) con mismo tonelaje",
            squatResult.totalKcal.mid > curlResult.totalKcal.mid,
        )
        // Multiarticular should be clearly higher, not a rounding tie.
        assertTrue(
            "Diferencia insuficiente: squat=${squatResult.totalKcal.mid} curl=${curlResult.totalKcal.mid}",
            squatResult.totalKcal.mid >= curlResult.totalKcal.mid + 5,
        )
    }

    @Test
    fun `missing planned weight without 1RM does not invent kcal`() {
        val settings = buildSettings(weight = 80.0)
        val session = Session(
            id = "test-session",
            name = "Test",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Curl de Bíceps de Pie con Barra Recta",
                    exerciseDbId = "biceps_curl_de_pie_supino_barra_recta",
                    sets = listOf(
                        ExerciseSet(id = "s1", weight = null, targetReps = 8, targetRPE = 8.0),
                    ),
                ),
            ),
        )

        val result = TrainingEnergyEngine.estimatePlannedSession(session, settings)

        assertEquals(0, result.totalKcal.mid)
        assertEquals(EnergyConfidence.LOW, result.confidence)
        assertTrue(
            "Debe incluir nota sobre falta de peso/1RM, notas: ${result.notes}",
            result.notes.any {
                it.contains("Sin carga ni 1RM", ignoreCase = true) ||
                    it.contains("completa pesos", ignoreCase = true) ||
                    it.contains("No se pudo estimar", ignoreCase = true)
            },
        )
    }

    @Test
    fun `heavy leg day with planned weights lands in 400 to 550 kcal`() {
        val settings = buildSettings(weight = 80.0)
        val result = TrainingEnergyEngine.estimatePlannedSession(
            heavyLegSession(withWeights = true),
            settings,
        )

        assertTrue(
            "Pierna pesada debe caer en 400–550 kcal, got ${result.totalKcal.mid}. notes=${result.notes}",
            result.totalKcal.mid in 400..550,
        )
        assertEquals(EnergyEstimateSource.PLANNED, result.source)
        assertEquals("auge-energy-v2", result.methodVersion)
    }

    @Test
    fun `heavy leg day without weights but with 1RM resolves load and stays in magnitude`() {
        val settings = buildSettings(weight = 80.0)
        val withWeights = TrainingEnergyEngine.estimatePlannedSession(
            heavyLegSession(withWeights = true),
            settings,
        )
        val from1Rm = TrainingEnergyEngine.estimatePlannedSession(
            heavyLegSession(withWeights = false, withReference1Rm = true),
            settings,
        )

        assertTrue(
            "Con 1RM debe estimar kcal > 0, got ${from1Rm.totalKcal.mid}",
            from1Rm.totalKcal.mid > 0,
        )
        assertTrue(
            "Estimación por 1RM debe estar en orden de magnitud similar " +
                "(${from1Rm.totalKcal.mid} vs ${withWeights.totalKcal.mid})",
            from1Rm.totalKcal.mid.toDouble() >= withWeights.totalKcal.mid * 0.55 &&
                from1Rm.totalKcal.mid.toDouble() <= withWeights.totalKcal.mid * 1.45,
        )
        assertTrue(
            "Debe anotar estimación desde 1RM, notes=${from1Rm.notes}",
            from1Rm.notes.any { it.contains("1RM", ignoreCase = true) },
        )
    }

    @Test
    fun `poor technique increases epoc and total kcal mid`() {
        val settings = buildSettings(weight = 80.0)
        val tenHeavySets = (1..10).map {
            buildCompletedSet(weight = 150.0, reps = 10, rpe = 9.5)
        }
        val exercises = listOf(
            buildCompletedExercise(
                "Sentadilla Trasera Barra Alta",
                tenHeavySets,
                "quads_sentadilla_trasera_barra_alta",
            ),
        )

        val postFeedback = mapOf(
            "quads_sentadilla_trasera_barra_alta" to PostExerciseFeedback(
                exerciseId = "quads_sentadilla_trasera_barra_alta",
                exerciseName = "Sentadilla Trasera Barra Alta",
                technicalQuality = 2,
            ),
        )

        val goodResult = TrainingEnergyEngine.estimateCompletedSession(
            completedExercises = exercises,
            settings = settings,
            postExerciseFeedback = emptyMap(),
        )
        val poorResult = TrainingEnergyEngine.estimateCompletedSession(
            completedExercises = exercises,
            settings = settings,
            postExerciseFeedback = postFeedback,
        )

        assertTrue(
            "Tecnica pobre debe aumentar EPOC kcal mid (${poorResult.epocKcal.mid} vs ${goodResult.epocKcal.mid})",
            poorResult.epocKcal.mid > goodResult.epocKcal.mid,
        )
        assertTrue(
            "Tecnica pobre debe aumentar total kcal mid (${poorResult.totalKcal.mid} vs ${goodResult.totalKcal.mid})",
            poorResult.totalKcal.mid > goodResult.totalKcal.mid,
        )
    }

    @Test
    fun `planned and completed with same inputs are in same magnitude`() {
        val settings = buildSettings(weight = 80.0)
        val planned = TrainingEnergyEngine.estimatePlannedSession(
            heavyLegSession(withWeights = true),
            settings,
        )
        val completed = TrainingEnergyEngine.estimateCompletedSession(
            completedExercises = listOf(
                buildCompletedExercise(
                    "Sentadilla Trasera Barra Alta",
                    sets(4, 100.0, 8, 8.0),
                    "quads_sentadilla_trasera_barra_alta",
                    restTime = 180,
                ),
                buildCompletedExercise(
                    "Peso Muerto Rumano con Barra",
                    sets(3, 100.0, 8, 8.0),
                    "hamstrings_rdl_barra",
                    restTime = 150,
                ),
                buildCompletedExercise(
                    "Prensa de Piernas 45°",
                    sets(3, 180.0, 10, 8.0),
                    "quads_prensa_45",
                    restTime = 120,
                ),
                buildCompletedExercise(
                    "Curl Femoral en Máquina",
                    sets(3, 50.0, 12, 7.0),
                    "hamstrings_curl_maquina",
                    restTime = 90,
                ),
                buildCompletedExercise(
                    "Elevación de Gemelos en Máquina",
                    sets(3, 80.0, 15, 7.0),
                    "calves_elevacion_maquina",
                    restTime = 60,
                ),
            ),
            settings = settings,
        )

        val ratio = completed.totalKcal.mid.toDouble() / planned.totalKcal.mid.coerceAtLeast(1)
        assertTrue(
            "Planned (${planned.totalKcal.mid}) y completed (${completed.totalKcal.mid}) deben ser coherentes",
            ratio in 0.75..1.35,
        )
    }

    @Test
    fun `recruitment factor is higher for compound than isolation`() {
        val squat = ExerciseMuscleInfo(
            id = "x_squat",
            name = "Squat",
            type = "Básico",
            involvedMuscles = listOf(
                InvolvedMuscle("Quads", MuscleRole.PRIMARY),
                InvolvedMuscle("Glutes", MuscleRole.PRIMARY),
            ),
            coreInvolvement = "high",
        )
        val curl = ExerciseMuscleInfo(
            id = "x_curl",
            name = "Curl",
            type = "Aislamiento",
            involvedMuscles = listOf(InvolvedMuscle("Biceps", MuscleRole.PRIMARY)),
        )
        assertTrue(
            TrainingEnergyEngine.recruitmentFactor(squat) >
                TrainingEnergyEngine.recruitmentFactor(curl),
        )
        assertTrue(TrainingEnergyEngine.bodyweightParticipation(squat.copy(
            movementPattern = "sentadilla",
            bodyPart = "lower",
            force = "Sentadilla",
            equipment = "Barra",
        )) > TrainingEnergyEngine.bodyweightParticipation(curl.copy(
            movementPattern = "curl",
            bodyPart = "upper",
            force = "Monoarticular",
            equipment = "Barra",
        )))
    }
}
