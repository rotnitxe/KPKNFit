package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.testsupport.RoomTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5 (auditoría, «reemplazar dentro de una superserie»): el ejercicio nuevo ocupa el hueco del miembro y la superserie sigue entera
 * (mismo grupo, mismo orden, mismos descansos y rondas), con las series del hueco y sin lo registrado ni recordado del ejercicio anterior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutReplaceInSupersetControllerTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private lateinit var fixture: StructuralControllerFixture

    private fun member(id: String, name: String, mode: LoadModeV2 = LoadModeV2.LOAD) = Exercise(
        id = id,
        name = name,
        supersetId = "g1",
        supersetGroupRef = "g1",
        supersetRestBetween = 45,
        supersetRestAfter = 90,
        restTime = 45,
        sets = (0 until 3).map { ExerciseSet(id = "$id-s$it", targetReps = 10, targetRPE = 8.0, weight = 30.0, loadModeV2 = mode) },
    )

    private val infoC = ExerciseMuscleInfo(
        id = "push_up__bodyweight",
        name = "Flexiones",
        equipment = "Peso corporal",
        catalogRevision = "rev-c",
        catalogDefinitionId = "push_up",
        catalogConfigurationId = "push_up__bodyweight",
    )

    @Before
    fun setUp() {
        val session = Session(
            id = "sess-1",
            name = "Empuje y tirón",
            exercises = listOf(member("ex-a", "Press"), member("ex-b", "Remo", LoadModeV2.LASTRE)),
            supersetGroups = listOf(
                SupersetGroup(id = "g1", exerciseOrder = listOf("ex-a", "ex-b"), restBetweenExercises = 45, restAfterSuperset = 90, rounds = 3),
            ),
        )
        fixture = StructuralControllerFixture.open(
            WorkoutUiState(
                session = session,
                programId = "",
                // La primera ronda del remo ya está registrada con lastre, y el recuerdo de la sesión lo sabe.
                completedSets = mapOf("ex-b_0" to CompletedSet(id = "c0", weight = 30.0, reps = 10)),
                persistedLoadModeBySet = mapOf("ex-b_0" to LoadModeV2.LASTRE),
                persistedLoadModeByExercise = mapOf("ex-b" to LoadModeV2.LASTRE),
            ),
        )
    }

    @After
    fun tearDown() = StructuralControllerFixture.close()

    @Test
    fun reemplazarUnMiembroDejaLaSuperserieEnteraConElEjercicioNuevo() {
        fixture.controller.replaceExercise("ex-b", infoC)

        val session = fixture.state.session!!
        val replaced = fixture.exercise("ex-b")
        // El hueco: mismo grupo, mismos descansos y mismas tres rondas con su objetivo.
        assertEquals("Flexiones", replaced.name)
        assertEquals("g1", replaced.supersetGroupRef)
        assertEquals(45, replaced.supersetRestBetween)
        assertEquals(90, replaced.supersetRestAfter)
        assertEquals(3, replaced.sets.size)
        assertEquals(listOf(8.0, 8.0, 8.0), replaced.sets.map { it.targetRPE })
        // Lo del ejercicio anterior no pasa: ni la carga ni el lastre con el que se registró.
        assertTrue(replaced.sets.all { it.weight == null && it.loadModeV2 == LoadModeV2.BODYWEIGHT })
        assertTrue(fixture.state.persistedLoadModeBySet.isEmpty())
        assertTrue(fixture.state.persistedLoadModeByExercise.isEmpty())
        assertTrue("la ronda registrada era del ejercicio anterior", fixture.state.completedSets.isEmpty())

        // La superserie sigue entera, con el miembro nuevo en su sitio (lo que la sesión normaliza al aplicar el cambio).
        val normalized = SupersetRules.normalizeSession(session)
        val group = normalized.allSupersetGroups().single()
        assertEquals(listOf("ex-a", "ex-b"), group.exerciseOrder)
        assertEquals(listOf("Press", "Flexiones"), SupersetRules.orderedMembers(normalized, "g1").map { it.name })
        assertEquals(3, group.rounds)
        // El otro miembro no se toca.
        assertEquals(member("ex-a", "Press"), fixture.exercise("ex-a"))
    }
}
