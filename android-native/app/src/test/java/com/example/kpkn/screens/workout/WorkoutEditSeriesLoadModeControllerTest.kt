package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.workout.WorkoutStructuralEditor
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 4, de punta a punta: lo que la persona edita en «Editar series» (el tipo de carga de una serie) se aplica entero por el camino
 * real de la sesión en vivo ([WorkoutStructuralPersistenceController.applySessionMutation]) y sobrevive a pasar de serie, a registrar y a
 * salir y volver. Antes, desde la primera edición el plan se copiaba al recuerdo de la sesión y las ediciones siguientes perdían contra esa
 * copia: el tipo «volvía al que había».
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutEditSeriesLoadModeControllerTest {
    @Test fun changingLateralityOnlyKeepsThePendingDose() {
        val root = state.session!!
        val ex = root.exercises.first()
        fixture.controller.applySessionMutation(root.copy(exercises = listOf(ex.copy(isUnilateral = true), other)))
        assertEquals(listOf("b", "c"), state.session!!.exercises.first().sets.map { it.id })
        assertTrue(state.completedSets.isEmpty())
        assertEquals("c0", state.archivedCompletedExercises.single().sets.single().id)
    }
    @Test fun reorderKeepsTheSelectedSetIdentityAndItsRecordedNeighbour() {
        fixture.state = state.copy(currentSetIdx = 2, activeStepKey = "ex-1_2")
        val root = state.session!!
        val ex = root.exercises.first()
        fixture.controller.applySessionMutation(root.copy(exercises = listOf(ex.copy(sets = ex.sets.reversed()), other)))
        assertEquals(0, state.currentSetIdx)
        assertEquals("c0", state.completedSets["ex-1_2"]?.id)
    }

    @get:Rule
    val roomRule = RoomTestRule()

    private lateinit var fixture: StructuralControllerFixture

    private val state get() = fixture.state

    private fun exercisePlan() = Exercise(
        id = "ex-1",
        name = "Dominadas",
        sets = listOf(
            ExerciseSet(id = "a", targetReps = 8, loadModeV2 = LoadModeV2.LOAD),
            ExerciseSet(id = "b", targetReps = 8, loadModeV2 = LoadModeV2.LOAD),
            ExerciseSet(id = "c", targetReps = 8, loadModeV2 = LoadModeV2.LOAD),
        ),
    )

    private val other = Exercise(
        id = "ex-2",
        name = "Remo",
        sets = listOf(ExerciseSet(id = "o0", targetReps = 10), ExerciseSet(id = "o1", targetReps = 10)),
    )

    @Before
    fun setUp() {
        val session = Session(id = "sess-1", name = "Tirón", exercises = listOf(exercisePlan(), other))
        fixture = StructuralControllerFixture.open(
            WorkoutUiState(
                session = session,
                programId = "",
                currentExerciseIdx = 0,
                currentSetIdx = 1,
                // La serie 1 de las dominadas está hecha con carga externa; el remo se registró con lastre.
                completedSets = mapOf("ex-1_0" to CompletedSet(id = "c0", weight = 10.0, reps = 8)),
                persistedLoadModeBySet = mapOf("ex-1_0" to LoadModeV2.LOAD, "ex-2_0" to LoadModeV2.LASTRE),
                persistedLoadModeByExercise = mapOf("ex-1" to LoadModeV2.LOAD, "ex-2" to LoadModeV2.LASTRE),
            ),
        )
    }

    @After
    fun tearDown() = StructuralControllerFixture.close()

    /** Lo que hace «Confirmar cambios» de «Editar series»: sustituir las series del ejercicio y aplicar la sesión. */
    private fun editSeries(vararg modes: LoadModeV2) {
        val session = state.session!!
        val updated = session.copy(
            exercises = session.exercises.map { exercise ->
                if (exercise.id != "ex-1") {
                    exercise
                } else {
                    exercise.copy(sets = exercise.sets.mapIndexed { index, set -> set.copy(loadModeV2 = modes[index]) })
                }
            },
        )
        fixture.controller.applySessionMutation(updated, preferredExerciseId = "ex-1")
    }

    /** Lo que muestra la tarjeta de la serie [setIdx]: borrador, recuerdo y plan, por ese orden. */
    private fun shown(setIdx: Int, from: WorkoutUiState = state): LoadModeV2 = resolveEffectiveLoadMode(
        draftLoadMode = from.setDrafts["ex-1_$setIdx"]?.loadMode,
        persistedLoadMode = resolvePersistedLoadModeForSet(
            exerciseId = "ex-1",
            setIdx = setIdx,
            tagId = null,
            persistedLoadModeBySet = from.persistedLoadModeBySet,
            persistedLoadModeByExercise = from.persistedLoadModeByExercise,
        ),
        plannedLoadMode = from.session!!.allExercises().first { it.id == "ex-1" }.sets[setIdx].loadModeV2,
        defaultCatalogMode = null,
    )

    @Test
    fun elTipoEditadoSeAplicaYLasEdicionesSiguientesTambien() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT)
        assertEquals(LoadModeV2.BODYWEIGHT, shown(2))
        assertEquals("la serie sin tocar sigue igual", LoadModeV2.LOAD, shown(1))

        // Una segunda y una tercera edición: antes perdían contra la copia del plan que dejó la primera.
        editSeries(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LASTRE)
        assertEquals(LoadModeV2.LASTRE, shown(2))

        editSeries(LoadModeV2.LOAD, LoadModeV2.ASSISTED, LoadModeV2.LASTRE)
        assertEquals(LoadModeV2.ASSISTED, shown(1))
        assertEquals(LoadModeV2.LASTRE, shown(2))
        assertEquals("la serie hecha conserva su tipo", LoadModeV2.LOAD, shown(0))
    }

    @Test
    fun cambiarElTipoDeLaSerieQueSigueNoAlteraLaSerieActual() {
        // Pasar de serie: estamos en la serie 2 (índice 1) y se edita la 3.
        editSeries(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.ASSISTED)

        assertEquals(1, state.currentSetIdx)
        assertEquals(LoadModeV2.LOAD, shown(1))
        assertEquals(LoadModeV2.ASSISTED, shown(2))
    }

    @Test
    fun registrarDespuesDeEditarConservaElTipoEditado() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT, LoadModeV2.LOAD)
        assertEquals(LoadModeV2.BODYWEIGHT, shown(1))

        // El registrador escribe el modo con el que se hizo la serie (por serie y por ejercicio).
        fixture.state = state.copy(
            persistedLoadModeBySet = state.persistedLoadModeBySet + ("ex-1_1" to LoadModeV2.BODYWEIGHT),
            persistedLoadModeByExercise = state.persistedLoadModeByExercise + ("ex-1" to LoadModeV2.BODYWEIGHT),
        )

        assertEquals(LoadModeV2.BODYWEIGHT, shown(1))
        assertEquals("la serie siguiente no hereda el cambio de otra", LoadModeV2.LOAD, shown(2))
    }

    @Test
    fun elTipoEditadoSobreviveASalirYVolverALaSesion() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.LASTRE, LoadModeV2.BODYWEIGHT)

        // «Salir y volver»: la sesión en vivo se guarda como OngoingWorkoutState y la rehidratación lee de ahí el plan y el recuerdo.
        val persistence = WorkoutPersistenceController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            programId = "",
            sessionId = "sess-1",
            getState = { state },
            visibleExercises = { it.session!!.allExercises() },
            writeOngoing = { WorkoutPersistResult.Ok },
        )
        val write = checkNotNull(persistence.buildOngoingUpdate(state))
        val saved = write(OngoingWorkoutState(programId = "", session = state.session!!, startTime = state.startTimeMs))

        val resumed = WorkoutUiState(
            session = saved.session,
            persistedLoadModeBySet = saved.persistedLoadModeBySet,
            persistedLoadModeByExercise = saved.persistedLoadModeByExercise,
            setDrafts = saved.setDrafts,
        )
        assertEquals(LoadModeV2.LASTRE, shown(1, from = resumed))
        assertEquals(LoadModeV2.BODYWEIGHT, shown(2, from = resumed))
    }

    @Test
    fun editarUnEjercicioNoBorraElRecuerdoDeLosDemas() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT)

        assertEquals(LoadModeV2.LASTRE, state.persistedLoadModeBySet["ex-2_0"])
        assertEquals(LoadModeV2.LASTRE, state.persistedLoadModeByExercise["ex-2"])
    }

    @Test
    fun unaEdicionQueNoCambiaElTipoNoCopiaElPlanAlRecuerdo() {
        val before = state.persistedLoadModeBySet
        val beforeByExercise = state.persistedLoadModeByExercise

        // Solo cambian las repeticiones objetivo de la serie 3.
        val session = state.session!!
        val updated = session.copy(
            exercises = session.exercises.map { exercise ->
                if (exercise.id != "ex-1") exercise else exercise.copy(sets = exercise.sets.map { it.copy(targetReps = 12) })
            },
        )
        fixture.controller.applySessionMutation(updated, preferredExerciseId = "ex-1")

        assertEquals("el recuerdo es solo lo que la sesión vio hacer", before, state.persistedLoadModeBySet)
        assertEquals(beforeByExercise, state.persistedLoadModeByExercise)
    }

    @Test
    fun quitarUnaSerieReasignaElRecuerdoDeLasQueQuedan() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.LASTRE, LoadModeV2.BODYWEIGHT)

        // Se quita la serie del medio: la que era la tercera pasa a ser la segunda y conserva su tipo.
        val removed = WorkoutStructuralEditor.removeSetFromExercise(state.session!!, "ex-1", 1)
        fixture.controller.applySessionMutation(removed, preferredExerciseId = "ex-1")

        assertEquals(listOf("a", "c"), fixture.exercise("ex-1").sets.map { it.id })
        assertEquals(LoadModeV2.LOAD, shown(0))
        assertEquals(LoadModeV2.BODYWEIGHT, shown(1))
        assertEquals("nada queda colgado de la serie que se quitó", false, state.persistedLoadModeBySet.containsKey("ex-1_2"))
    }

    @Test
    fun anadirUnaSerieNoCambiaElTipoDeLasDemas() {
        editSeries(LoadModeV2.LOAD, LoadModeV2.LASTRE, LoadModeV2.BODYWEIGHT)

        val plan = fixture.exercise("ex-1")
        val withNewSet = state.session!!.copy(
            exercises = state.session!!.exercises.map { exercise ->
                if (exercise.id != "ex-1") exercise else exercise.copy(sets = exercise.sets + exercise.sets.last().copy(id = "d"))
            },
        )
        fixture.controller.applySessionMutation(withNewSet, preferredExerciseId = "ex-1")

        assertEquals(4, fixture.exercise("ex-1").sets.size)
        assertEquals("la nueva sale como la última", LoadModeV2.BODYWEIGHT, shown(3))
        assertEquals(LoadModeV2.LASTRE, shown(1))
        assertEquals(LoadModeV2.BODYWEIGHT, shown(2))
        assertEquals(plan.sets.map { it.id }, fixture.exercise("ex-1").sets.take(3).map { it.id })
    }

    @Test
    fun laCargaInteligenteConfirmadaSeConservaEnLaSesionYEnLoGuardado() {
        val session = state.session!!
        val withReference = session.copy(
            exercises = session.exercises.map { exercise ->
                if (exercise.id != "ex-1") exercise else exercise.copy(reference1RM = 120.0, prFor1RM = PrReference(weight = 100.0, reps = 5))
            },
        )
        fixture.controller.applySessionMutation(withReference, preferredExerciseId = "ex-1")

        val saved = fixture.exercise("ex-1")
        assertEquals(120.0, saved.reference1RM ?: 0.0, 0.0)
        assertEquals(PrReference(weight = 100.0, reps = 5), saved.prFor1RM)
        // Lo calculado desde la referencia la usa: con una referencia más baja, la carga sugerida baja.
        val withLowerReference = saved.copy(reference1RM = 60.0)
        val high = calculateSuggestedLoad(saved, saved.sets[0]) ?: 0.0
        val low = calculateSuggestedLoad(withLowerReference, withLowerReference.sets[0]) ?: 0.0
        assertTrue("con 120 de referencia se sugiere más carga que con 60 ($high vs $low)", high > low && low > 0.0)

        // Y sobrevive a salir y volver: va en lo que se guarda de la sesión.
        val persistence = WorkoutPersistenceController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            programId = "",
            sessionId = "sess-1",
            getState = { state },
            visibleExercises = { it.session!!.allExercises() },
            writeOngoing = { WorkoutPersistResult.Ok },
        )
        val savedOngoing = checkNotNull(persistence.buildOngoingUpdate(state))(
            OngoingWorkoutState(programId = "", session = state.session!!, startTime = state.startTimeMs),
        )
        val resumedExercise = savedOngoing.session.allExercises().first { it.id == "ex-1" }
        assertEquals(120.0, resumedExercise.reference1RM ?: 0.0, 0.0)
        assertEquals(PrReference(weight = 100.0, reps = 5), resumedExercise.prFor1RM)
    }

    @Test
    fun elBorradorConElTipoViejoNoPisaElTipoEditado() {
        fixture.state = state.copy(
            setDrafts = mapOf(
                "ex-1_2" to WorkoutSetDraft(weightText = "60", valueText = "6", loadMode = LoadModeV2.LOAD, isDirty = true),
            ),
        )

        editSeries(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT)

        val draft = state.setDrafts.getValue("ex-1_2")
        assertNull(draft.loadMode)
        assertNull("los 60 kg se escribieron con carga externa", draft.weightText)
        assertEquals("6", draft.valueText)
        assertEquals(LoadModeV2.BODYWEIGHT, shown(2))
    }
}
