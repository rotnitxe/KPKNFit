package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.calculations.calculateSessionTimeBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeCoachEngineTest {

    @Test
    fun generate_restSuggestion_whenOverLimit() {
        val session = Session(
            id = "s1",
            name = "Test",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Press banca",
                    restTime = 180,
                    sets = List(4) { ExerciseSet(id = "s$it", targetReps = 8) },
                ),
                Exercise(
                    id = "e2",
                    name = "Remo",
                    restTime = 180,
                    sets = List(4) { ExerciseSet(id = "r$it", targetReps = 8) },
                ),
            ),
            targetDurationMinutes = 20,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        assertTrue(breakdown.totalMinutes > 20)
        val suggestions = TimeCoachEngine.generate(
            session = session,
            breakdown = breakdown,
            targetDurationMinutes = 20,
            exerciseIndex = emptyMap(),
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.action is TimeCoachAction.ReduceRests })
        val restSuggestion = suggestions.first { it.action is TimeCoachAction.ReduceRests }
        val reduce = restSuggestion.action as TimeCoachAction.ReduceRests
        assertTrue(reduce.perExerciseTargetRests.isNotEmpty())
        val applied = TimeCoachEngine.apply(session, restSuggestion.action)
        val after = calculateSessionTimeBreakdown(
            exercises = applied.allExercises(),
            supersetGroups = applied.allSupersetGroups(),
        )
        assertTrue(after.totalMinutes <= breakdown.totalMinutes)
        assertTrue(after.totalMinutes >= 20)
        assertTrue(reduce.perExerciseTargetRests.values.all { it <= 180 })
    }

    @Test
    fun generate_dedupes_density_suggestions_for_same_exercise() {
        val session = Session(
            id = "s2",
            name = "Density",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Curl femoral",
                    exerciseDbId = "curl_femoral",
                    restTime = 150,
                    sets = List(4) { ExerciseSet(id = "c$it", targetReps = 10) },
                ),
            ),
            targetDurationMinutes = 5,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "curl_femoral" to infoSimple(
                id = "curl_femoral",
                name = "Curl femoral",
                type = "Aislamiento",
                movementPattern = "knee flexion",
                muscles = listOf("isquiosurales"),
                equipment = "machine",
            ),
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 5, index)
        val density = suggestions.filter {
            it.action is TimeCoachAction.ConvertToDropSets || it.action is TimeCoachAction.ConvertToRestPause
        }
        assertEquals(1, density.size)
    }

    @Test
    fun generate_restSuggestion_prioritizes_isolation_and_does_not_overcut_budget() {
        val session = Session(
            id = "s2b",
            name = "Dynamic rests",
            exercises = listOf(
                Exercise(
                    id = "cmp1",
                    name = "Sentadilla",
                    exerciseDbId = "sentadilla",
                    restTime = 180,
                    sets = List(4) { ExerciseSet(id = "sq$it", targetReps = 6) },
                ),
                Exercise(
                    id = "iso1",
                    name = "Curl bíceps",
                    exerciseDbId = "curl_biceps",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "cb$it", targetReps = 12) },
                ),
            ),
            targetDurationMinutes = 13,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "sentadilla" to infoSimple(
                id = "sentadilla",
                name = "Sentadilla",
                type = "Básico",
                movementPattern = "squat",
                muscles = listOf("cuadriceps", "gluteo mayor"),
                equipment = "barbell",
                bodybuildingScore = 8.5,
            ),
            "curl_biceps" to infoSimple(
                id = "curl_biceps",
                name = "Curl bíceps",
                type = "Aislamiento",
                movementPattern = "curl",
                muscles = listOf("biceps"),
                equipment = "dumbbell",
                bodybuildingScore = 8.0,
            ),
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 13, index)
        val restSuggestion = suggestions.first { it.action is TimeCoachAction.ReduceRests }
        val action = restSuggestion.action as TimeCoachAction.ReduceRests
        assertTrue((action.perExerciseTargetRests["iso1"] ?: 120) <= (action.perExerciseTargetRests["cmp1"] ?: 180))
        val applied = TimeCoachEngine.apply(session, action)
        val after = calculateSessionTimeBreakdown(
            exercises = applied.allExercises(),
            supersetGroups = applied.allSupersetGroups(),
        )
        assertTrue(after.totalMinutes >= 13)
    }

    @Test
    fun generate_doesNotSuggest_bad_replacement_for_glute_medio() {
        val session = Session(
            id = "s3",
            name = "Gluteo medio",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Abducción en polea",
                    exerciseDbId = "abd_polea",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "a$it", targetReps = 15) },
                ),
                Exercise(
                    id = "e2",
                    name = "Abducción en máquina",
                    exerciseDbId = "abd_maquina",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "b$it", targetReps = 15) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "abd_polea" to infoWithEmphasis(
                id = "abd_polea",
                name = "Abducción en polea",
                type = "Aislamiento",
                movementPattern = "abduccion de cadera",
                muscles = listOf("gluteo" to "medio"),
                equipment = "cable",
                bodybuildingScore = 8.5,
            ),
            "abd_maquina" to infoWithEmphasis(
                id = "abd_maquina",
                name = "Abducción en máquina",
                type = "Aislamiento",
                movementPattern = "abduccion de cadera",
                muscles = listOf("gluteo" to "medio"),
                equipment = "machine",
                bodybuildingScore = 8.4,
            ),
            "good_morning" to infoWithEmphasis(
                id = "good_morning",
                name = "Buenos días",
                type = "Básico",
                movementPattern = "hip hinge",
                muscles = listOf("gluteo" to "mayor"),
                equipment = "barbell",
                bodybuildingScore = 6.0,
            ),
        )

        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertFalse(suggestions.any { it.title.contains("Buenos días", ignoreCase = true) })
    }

    @Test
    fun generate_replacement_ignores_niche_when_common_option_missing() {
        val session = Session(
            id = "s3b",
            name = "Niche replacement",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Extensión de tríceps en cuerda",
                    exerciseDbId = "triceps_cuerda",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "t1$it", targetReps = 12) },
                ),
                Exercise(
                    id = "e2",
                    name = "Extensión de tríceps unilateral",
                    exerciseDbId = "triceps_unilateral",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "t2$it", targetReps = 12) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "triceps_cuerda" to infoSimple(
                id = "triceps_cuerda",
                name = "Extensión de tríceps en cuerda",
                type = "Aislamiento",
                movementPattern = "extension de codo",
                muscles = listOf("triceps"),
                equipment = "cable",
                bodybuildingScore = 8.5,
            ),
            "triceps_unilateral" to infoSimple(
                id = "triceps_unilateral",
                name = "Extensión de tríceps unilateral",
                type = "Aislamiento",
                movementPattern = "extension de codo",
                muscles = listOf("triceps"),
                equipment = "cable",
                bodybuildingScore = 7.8,
            ),
            "zercher_triceps" to infoSimple(
                id = "zercher_triceps",
                name = "Zercher press extraño",
                type = "Básico",
                movementPattern = "extension de codo",
                muscles = listOf("triceps"),
                equipment = "barbell",
                bodybuildingScore = 9.0,
            ),
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertFalse(suggestions.any { it.title.contains("Zercher", ignoreCase = true) })
    }

    @Test
    fun generate_prefers_curated_replacement_group_and_priority() {
        val session = Session(
            id = "s5",
            name = "Curated replacement",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Abducción 1",
                    exerciseDbId = "abd_1",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "g1$it", targetReps = 15) },
                ),
                Exercise(
                    id = "e2",
                    name = "Abducción 2",
                    exerciseDbId = "abd_2",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "g2$it", targetReps = 15) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val curatedTop = infoWithEmphasis(
            id = "abd_machine",
            name = "Abducción en máquina",
            type = "Aislamiento",
            movementPattern = "abduccion de cadera",
            muscles = listOf("gluteo" to "medio"),
            equipment = "machine",
            bodybuildingScore = 8.8,
        ).copy(isCommon = true, replacementPriority = 1, replacementGroup = "glute_med_abduction")
        val curatedLower = infoWithEmphasis(
            id = "abd_band",
            name = "Abducción con banda",
            type = "Aislamiento",
            movementPattern = "abduccion de cadera",
            muscles = listOf("gluteo" to "medio"),
            equipment = "banda",
            bodybuildingScore = 8.0,
        ).copy(isCommon = true, replacementPriority = 3, replacementGroup = "glute_med_abduction")
        val index = mapOf(
            "abd_1" to infoWithEmphasis(
                id = "abd_1",
                name = "Abducción polea",
                type = "Aislamiento",
                movementPattern = "abduccion de cadera",
                muscles = listOf("gluteo" to "medio"),
                equipment = "cable",
                bodybuildingScore = 8.0,
            ).copy(isCommon = true, replacementGroup = "glute_med_abduction"),
            "abd_2" to infoWithEmphasis(
                id = "abd_2",
                name = "Abducción máquina",
                type = "Aislamiento",
                movementPattern = "abduccion de cadera",
                muscles = listOf("gluteo" to "medio"),
                equipment = "machine",
                bodybuildingScore = 8.0,
            ).copy(isCommon = true, replacementGroup = "glute_med_abduction"),
            curatedTop.id to curatedTop,
            curatedLower.id to curatedLower,
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertTrue(suggestions.any { it.title.contains("Abducción en máquina", ignoreCase = true) })
    }

    @Test
    fun generate_skips_replacement_when_curated_groups_conflict() {
        val session = Session(
            id = "s6",
            name = "Conflict replacement",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Lateral 1",
                    exerciseDbId = "lat_1",
                    restTime = 90,
                    sets = List(4) { ExerciseSet(id = "l1$it", targetReps = 15) },
                ),
                Exercise(
                    id = "e2",
                    name = "Rear 1",
                    exerciseDbId = "rear_1",
                    restTime = 90,
                    sets = List(4) { ExerciseSet(id = "r1$it", targetReps = 15) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "lat_1" to infoWithEmphasis(
                id = "lat_1",
                name = "Lateral polea",
                type = "Aislamiento",
                movementPattern = "abduccion hombro",
                muscles = listOf("deltoides" to "medio"),
                equipment = "cable",
                bodybuildingScore = 8.0,
            ).copy(isCommon = true, replacementGroup = "shoulder_lateral_raise"),
            "rear_1" to infoWithEmphasis(
                id = "rear_1",
                name = "Posterior polea",
                type = "Aislamiento",
                movementPattern = "abduccion horizontal",
                muscles = listOf("deltoides" to "posterior"),
                equipment = "cable",
                bodybuildingScore = 8.0,
            ).copy(isCommon = true, replacementGroup = "shoulder_rear_delt_raise"),
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertFalse(suggestions.any { it.action is TimeCoachAction.ReplaceWithCompound })
    }

    @Test
    fun generate_skips_replacement_when_same_pattern_but_curated_back_groups_differ() {
        val session = Session(
            id = "s7",
            name = "Back row conflict",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Remo polea baja",
                    exerciseDbId = "row_low",
                    restTime = 90,
                    sets = List(4) { ExerciseSet(id = "rb1$it", targetReps = 12) },
                ),
                Exercise(
                    id = "e2",
                    name = "Remo polea alta",
                    exerciseDbId = "row_high",
                    restTime = 90,
                    sets = List(4) { ExerciseSet(id = "rb2$it", targetReps = 12) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val low = infoSimple(
            id = "row_low",
            name = "Remo polea baja",
            type = "Accesorio",
            movementPattern = "tiron horizontal",
            muscles = listOf("dorsales", "trapecio"),
            equipment = "polea",
            bodybuildingScore = 8.0,
        ).copy(isCommon = true, replacementGroup = "back_row_cable_low")
        val high = infoSimple(
            id = "row_high",
            name = "Remo polea alta",
            type = "Accesorio",
            movementPattern = "tiron horizontal",
            muscles = listOf("dorsales", "trapecio"),
            equipment = "polea",
            bodybuildingScore = 8.0,
        ).copy(isCommon = true, replacementGroup = "back_row_cable_high")
        val index = mapOf(
            low.id to low,
            high.id to high,
        )
        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertFalse(suggestions.any { it.action is TimeCoachAction.ReplaceWithCompound })
    }

    @Test
    fun generate_suggests_removing_redundant_exercise_when_pattern_and_muscles_repeat() {
        val session = Session(
            id = "s4",
            name = "Redundancia",
            exercises = listOf(
                Exercise(
                    id = "e1",
                    name = "Curl martillo",
                    exerciseDbId = "curl_martillo",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "m$it", targetReps = 10) },
                ),
                Exercise(
                    id = "e2",
                    name = "Curl alterno",
                    exerciseDbId = "curl_alterno",
                    restTime = 120,
                    sets = List(4) { ExerciseSet(id = "n$it", targetReps = 10) },
                ),
            ),
            targetDurationMinutes = 8,
        )
        val breakdown = calculateSessionTimeBreakdown(
            exercises = session.allExercises(),
            supersetGroups = session.allSupersetGroups(),
        )
        val index = mapOf(
            "curl_martillo" to infoSimple(
                id = "curl_martillo",
                name = "Curl martillo",
                type = "Aislamiento",
                movementPattern = "curl",
                muscles = listOf("biceps"),
                equipment = "dumbbell",
                bodybuildingScore = 8.2,
            ),
            "curl_alterno" to infoSimple(
                id = "curl_alterno",
                name = "Curl alterno",
                type = "Aislamiento",
                movementPattern = "curl",
                muscles = listOf("biceps"),
                equipment = "dumbbell",
                bodybuildingScore = 7.9,
            ),
        )

        val suggestions = TimeCoachEngine.generate(session, breakdown, 8, index)
        assertTrue(suggestions.any { it.action is TimeCoachAction.RemoveExercise })
    }

    private fun infoSimple(
        id: String,
        name: String,
        type: String,
        movementPattern: String,
        muscles: List<String>,
        equipment: String,
        bodybuildingScore: Double = 0.0,
    ): ExerciseMuscleInfo {
        return ExerciseMuscleInfo(
            id = id,
            name = name,
            type = type,
            movementPattern = movementPattern,
            equipment = equipment,
            bodybuildingScore = bodybuildingScore,
            involvedMuscles = muscles.map {
                InvolvedMuscle(muscle = it, role = MuscleRole.PRIMARY)
            },
        )
    }

    private fun infoWithEmphasis(
        id: String,
        name: String,
        type: String,
        movementPattern: String,
        muscles: List<Pair<String, String>>,
        equipment: String,
        bodybuildingScore: Double = 0.0,
    ): ExerciseMuscleInfo {
        return ExerciseMuscleInfo(
            id = id,
            name = name,
            type = type,
            movementPattern = movementPattern,
            equipment = equipment,
            bodybuildingScore = bodybuildingScore,
            involvedMuscles = muscles.map { (muscle, emphasis) ->
                InvolvedMuscle(
                    muscle = muscle,
                    role = MuscleRole.PRIMARY,
                    emphasis = emphasis,
                )
            },
        )
    }
}
