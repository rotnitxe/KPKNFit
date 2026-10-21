package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.testsupport.RoomTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 3, de punta a punta: el controlador estructural reemplaza A por B y luego B por C sobre el MISMO hueco de la sesión (el id del
 * hueco no cambia). C queda con lo que dice la regla y el estado en vivo que A y B dejaron (modo de carga con el que se registraron,
 * borradores, cargas manuales) no llega a C.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutReplaceTwiceControllerTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private lateinit var fixture: StructuralControllerFixture

    private val infoB = ExerciseMuscleInfo(
        id = "bench_press__barbell",
        name = "Press de Banca Plano",
        equipment = "Barra",
        catalogRevision = "rev-b",
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__barbell",
    )
    private val infoC = ExerciseMuscleInfo(
        id = "push_up__bodyweight",
        name = "Flexiones",
        equipment = "Peso corporal",
        catalogRevision = "rev-c",
        catalogDefinitionId = "push_up",
        catalogConfigurationId = "push_up__bodyweight",
    )

    private fun exerciseA() = Exercise(
        id = "slot-1",
        name = "Dominadas lastradas",
        exerciseDbId = "pull_up__pronated__medium",
        catalogDefinitionId = "pull_up",
        catalogConfigurationId = "pull_up__pronated__medium",
        sets = listOf(
            ExerciseSet(id = "s0", targetReps = 6, targetRPE = 8.0, restAfterSeconds = 150, weight = 25.0, loadModeV2 = LoadModeV2.LASTRE),
            ExerciseSet(id = "s1", targetReps = 6, targetRPE = 8.0, weight = 25.0, loadModeV2 = LoadModeV2.LASTRE),
            ExerciseSet(id = "s2", targetReps = 6, targetRPE = 9.0, weight = 27.5, loadModeV2 = LoadModeV2.LASTRE),
        ),
        restTime = 120,
        reference1RM = 140.0,
        prFor1RM = PrReference(weight = 25.0, reps = 6),
        loadReference = PlanLoadReference(PlanLoadReferenceKind.EXERCISE_1RM, "pull_up__pronated__medium"),
        nativeProgressionManaged = true,
        trackRom = true,
    )

    private val other = Exercise(
        id = "other-1",
        name = "Remo",
        sets = listOf(ExerciseSet(id = "o0", targetReps = 10)),
    )

    @Before
    fun setUp() {
        val session = Session(id = "sess-1", name = "Tirón", exercises = listOf(exerciseA(), other))
        fixture = StructuralControllerFixture.open(
            WorkoutUiState(
                session = session,
                programId = "",
                activeMode = WeekVariant.A,
                // Lo que la sesión aprendió de A: lo registró con lastre, borradores, cargas manuales y sugerencias.
                persistedLoadModeBySet = mapOf("slot-1_0" to LoadModeV2.LASTRE, "other-1_0" to LoadModeV2.LOAD),
                persistedLoadModeByExercise = mapOf("slot-1" to LoadModeV2.LASTRE, "other-1" to LoadModeV2.LOAD),
                setDrafts = mapOf("slot-1_1" to WorkoutSetDraft(weightText = "27,5", loadMode = LoadModeV2.LASTRE, isDirty = true)),
                manualLoadOverrides = mapOf("slot-1_0" to 25.0),
            ),
        )
    }

    @After
    fun tearDown() = StructuralControllerFixture.close()

    private fun slot(): Exercise = fixture.exercise("slot-1")

    @Test
    fun reemplazarAPorBLeDaAlHuecoLoQueDiceLaReglaYOlvidaElEstadoDeA() {
        fixture.controller.replaceExercise("slot-1", infoB)

        val b = slot()
        assertEquals("bench_press__barbell", b.catalogConfigurationId)
        assertEquals(3, b.sets.size)
        assertEquals(120, b.restTime)
        assertEquals(listOf(8.0, 8.0, 9.0), b.sets.map { it.targetRPE })
        assertEquals(150, b.sets[0].restAfterSeconds)
        assertTrue(b.sets.all { it.loadModeV2 == LoadModeV2.LOAD && it.weight == null })
        assertNull(b.reference1RM)
        assertNull(b.prFor1RM)
        assertNull(b.loadReference)
        assertFalse(b.nativeProgressionManaged)
        assertFalse(b.trackRom)

        // El estado en vivo de A se fue con A; el de los demás ejercicios sigue.
        assertEquals(mapOf("other-1_0" to LoadModeV2.LOAD), fixture.state.persistedLoadModeBySet)
        assertEquals(mapOf("other-1" to LoadModeV2.LOAD), fixture.state.persistedLoadModeByExercise)
        assertTrue(fixture.state.setDrafts.isEmpty())
        assertTrue(fixture.state.manualLoadOverrides.isEmpty())
    }

    @Test
    fun reemplazarBPorCNoLeDejaACNiLoQueLaPersonaHizoEnB() {
        fixture.controller.replaceExercise("slot-1", infoB)
        // La persona trabaja con B: lo edita (otro modo de carga y cargas) y registra una serie con ese modo.
        fixture.state = fixture.state.copy(
            session = fixture.state.session!!.copy(
                exercises = fixture.state.session!!.exercises.map { exercise ->
                    if (exercise.id != "slot-1") {
                        exercise
                    } else {
                        exercise.copy(
                            restTime = 75,
                            reference1RM = 100.0,
                            sets = exercise.sets.map { it.copy(weight = 80.0, loadModeV2 = LoadModeV2.LASTRE) },
                        )
                    }
                },
            ),
            persistedLoadModeBySet = fixture.state.persistedLoadModeBySet + ("slot-1_0" to LoadModeV2.LASTRE),
            persistedLoadModeByExercise = fixture.state.persistedLoadModeByExercise + ("slot-1" to LoadModeV2.LASTRE),
            setDrafts = fixture.state.setDrafts +
                ("slot-1_2" to WorkoutSetDraft(weightText = "80", loadMode = LoadModeV2.LASTRE, isDirty = true)),
        )

        fixture.controller.replaceExercise("slot-1", infoC)

        val c = slot()
        assertEquals("push_up__bodyweight", c.catalogConfigurationId)
        assertEquals(3, c.sets.size)
        // Del hueco, tal como lo dejó B: su descanso. El modo de carga sale del equipo de C (peso corporal), no del lastre de B ni de A.
        assertEquals(75, c.restTime)
        assertTrue(c.sets.all { it.loadModeV2 == LoadModeV2.BODYWEIGHT })
        assertTrue("ninguna carga de B ni de A", c.sets.all { it.weight == null })
        assertNull(c.reference1RM)
        assertNull(c.prFor1RM)
        assertNull(c.loadReference)
        // Y el estado en vivo de B tampoco: el modo con el que se registró B no manda sobre el de C.
        assertFalse(fixture.state.persistedLoadModeBySet.keys.any { it.startsWith("slot-1") })
        assertFalse(fixture.state.persistedLoadModeByExercise.keys.any { it.startsWith("slot-1") })
        assertTrue(fixture.state.setDrafts.isEmpty())
        // Lo efectivo para la primera serie de C es su propio plan.
        val effective = resolveEffectiveLoadMode(
            draftLoadMode = null,
            persistedLoadMode = resolvePersistedLoadModeForSet(
                exerciseId = "slot-1",
                setIdx = 0,
                tagId = null,
                persistedLoadModeBySet = fixture.state.persistedLoadModeBySet,
                persistedLoadModeByExercise = fixture.state.persistedLoadModeByExercise,
            ),
            plannedLoadMode = c.sets[0].loadModeV2,
            defaultCatalogMode = null,
        )
        assertEquals(LoadModeV2.BODYWEIGHT, effective)
        // Los demás ejercicios no se tocan.
        assertEquals(other, fixture.exercise("other-1"))
    }
}
