package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * LV5, bug 4: editar una serie en vivo aplica la edición entera. El tipo de carga que la persona fija en «Editar series» gana al recuerdo
 * que la sesión tenía de esa serie (el modo con el que se registró otra, o un borrador), y no se hereda hacia las series que van detrás.
 */
class WorkoutPlanEditReconcileTest {

    private fun exercise(id: String = "ex", vararg modes: LoadModeV2, trainingMode: TrainingMode = TrainingMode.REPS) = Exercise(
        id = id,
        name = "Dominadas",
        trainingMode = trainingMode,
        sets = modes.mapIndexed { index, mode -> ExerciseSet(id = "$id-s$index", targetReps = 8, loadModeV2 = mode) },
    )

    private fun session(vararg exercises: Exercise) = Session(id = "sess", name = "Tirón", exercises = exercises.toList())

    private fun Exercise.withModes(vararg modes: LoadModeV2) = copy(
        sets = sets.mapIndexed { index, set -> set.copy(loadModeV2 = modes[index]) },
    )

    private fun runtime(
        bySet: Map<String, LoadModeV2> = emptyMap(),
        byExercise: Map<String, LoadModeV2> = emptyMap(),
        drafts: Map<String, WorkoutSetDraft> = emptyMap(),
    ) = LiveEditRuntime(bySet, byExercise, drafts)

    private fun done(vararg keys: String): Map<String, CompletedSet> =
        keys.associateWith { CompletedSet(id = "c-$it", weight = 10.0, reps = 8) }

    /** Lo que la tarjeta mostraría para la serie [setIdx]: borrador, recuerdo y plan, por ese orden. */
    private fun shown(plan: Exercise, state: LiveEditRuntime, setIdx: Int): LoadModeV2 = resolveEffectiveLoadMode(
        draftLoadMode = state.setDrafts[workoutSetKey(plan.id, setIdx)]?.loadMode,
        persistedLoadMode = resolvePersistedLoadModeForSet(
            exerciseId = plan.id,
            setIdx = setIdx,
            tagId = null,
            persistedLoadModeBySet = state.persistedLoadModeBySet,
            persistedLoadModeByExercise = state.persistedLoadModeByExercise,
        ),
        plannedLoadMode = plan.sets[setIdx].loadModeV2,
        defaultCatalogMode = null,
    )

    private fun reconcile(
        before: Exercise,
        after: Exercise,
        state: LiveEditRuntime,
        completed: Map<String, CompletedSet> = emptyMap(),
    ) = reconcileRuntimeWithPlanEdit(session(before), session(after), completed, emptyMap(), state)

    @Test
    fun elTipoQueSeFijaEnElPlanGanaAlRecuerdoDeLaSerieQueSeRegistro() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LOAD)
        // La serie 1 está hecha con carga externa: la sesión recuerda ese modo para ella y para el ejercicio.
        val state = runtime(bySet = mapOf("ex_0" to LoadModeV2.LOAD), byExercise = mapOf("ex" to LoadModeV2.LOAD))
        val after = before.withModes(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT, LoadModeV2.LOAD)

        val reconciled = reconcile(before, after, state, completed = done("ex_0"))

        assertEquals("sin la conciliación manda el recuerdo viejo", LoadModeV2.LOAD, shown(after, state, 1))
        assertEquals("la serie editada enseña su tipo nuevo", LoadModeV2.BODYWEIGHT, shown(after, reconciled, 1))
        assertEquals("la que no se tocó sigue como salía", LoadModeV2.LOAD, shown(after, reconciled, 2))
        assertEquals("la serie hecha conserva el suyo", LoadModeV2.LOAD, shown(after, reconciled, 0))
    }

    @Test
    fun elTipoNuevoDeUnaSerieNoSeHeredaHaciaLasSiguientes() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LOAD)
        val after = before.withModes(LoadModeV2.LASTRE, LoadModeV2.LOAD, LoadModeV2.LOAD)

        val reconciled = reconcile(before, after, runtime())

        assertEquals(LoadModeV2.LASTRE, shown(after, reconciled, 0))
        assertEquals(LoadModeV2.LOAD, shown(after, reconciled, 1))
        assertEquals(LoadModeV2.LOAD, shown(after, reconciled, 2))
    }

    @Test
    fun lasSeriesPendientesConservanElModoQueYaArrastrabanDeUnaSerieRegistrada() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LOAD)
        // La serie 1 se registró con lastre: arrastra a las siguientes mientras el plan no diga otra cosa.
        val state = runtime(bySet = mapOf("ex_0" to LoadModeV2.LASTRE), byExercise = mapOf("ex" to LoadModeV2.LASTRE))
        val after = before.withModes(LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.ASSISTED, LoadModeV2.LOAD)

        val reconciled = reconcile(before, after, state, completed = done("ex_0"))

        assertEquals("lo que arrastraba sigue igual", LoadModeV2.LASTRE, shown(after, reconciled, 1))
        assertEquals("la editada manda", LoadModeV2.ASSISTED, shown(after, reconciled, 2))
        assertEquals("la de detrás no hereda el asistido y sigue como salía", LoadModeV2.LASTRE, shown(after, reconciled, 3))
    }

    @Test
    fun cambiarElTipoVariasVecesSeguidasAplicaSiempreLaUltimaEdicion() {
        var plan = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD)
        var state = runtime()
        val edits = listOf(
            arrayOf(LoadModeV2.BODYWEIGHT, LoadModeV2.LOAD),
            arrayOf(LoadModeV2.LASTRE, LoadModeV2.LOAD),
            arrayOf(LoadModeV2.ASSISTED, LoadModeV2.BODYWEIGHT),
            arrayOf(LoadModeV2.LOAD, LoadModeV2.LASTRE),
        )

        edits.forEach { modes ->
            val next = plan.withModes(*modes)
            state = reconcile(plan, next, state)
            plan = next
            assertEquals(modes[0], shown(plan, state, 0))
            assertEquals(modes[1], shown(plan, state, 1))
        }
    }

    @Test
    fun elBorradorConElTipoViejoSeDescartaYUnoConElTipoNuevoSeConserva() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD, LoadModeV2.LOAD)
        val state = runtime(
            drafts = mapOf(
                // Escribió 80 kg con carga externa y luego cambió el tipo de la serie en el plan.
                "ex_0" to WorkoutSetDraft(weightText = "80", valueText = "6", loadMode = LoadModeV2.LOAD, isDirty = true),
                // Borrador sin tipo propio con una carga escrita bajo el tipo anterior.
                "ex_1" to WorkoutSetDraft(weightText = "70", valueText = "5", isDirty = true),
                // Borrador que ya estaba en el tipo al que se cambia: se conserva entero.
                "ex_2" to WorkoutSetDraft(weightText = "40", loadMode = LoadModeV2.LASTRE, isDirty = true),
            ),
        )
        val after = before.withModes(LoadModeV2.BODYWEIGHT, LoadModeV2.LASTRE, LoadModeV2.LASTRE)

        val reconciled = reconcile(before, after, state)

        val d0 = reconciled.setDrafts.getValue("ex_0")
        assertNull(d0.loadMode)
        assertNull("los kg escritos con otro tipo no valen para el nuevo", d0.weightText)
        assertEquals("el resto del borrador sigue", "6", d0.valueText)
        val d1 = reconciled.setDrafts.getValue("ex_1")
        assertNull(d1.weightText)
        assertEquals("5", d1.valueText)
        assertEquals(state.setDrafts.getValue("ex_2"), reconciled.setDrafts.getValue("ex_2"))
        assertEquals(LoadModeV2.BODYWEIGHT, shown(after, reconciled, 0))
    }

    @Test
    fun cambiarLaUnidadDeLaSerieDescartaElValorEscrito() {
        val before = Exercise(
            id = "ex",
            name = "Plancha",
            sets = listOf(ExerciseSet(id = "ex-s0", targetReps = 10, unitModeV2 = UnitModeV2.REPS)),
        )
        val after = before.copy(
            trainingMode = TrainingMode.TIME,
            sets = before.sets.map { it.copy(unitModeV2 = UnitModeV2.TIME, targetReps = null, targetDuration = 45) },
        )
        val state = runtime(drafts = mapOf("ex_0" to WorkoutSetDraft(weightText = "20", valueText = "9", isDirty = true)))

        val reconciled = reconcile(before, after, state)

        assertNull("«9» eran repeticiones, no segundos", reconciled.setDrafts.getValue("ex_0").valueText)
        assertEquals("la carga no depende de la unidad", "20", reconciled.setDrafts.getValue("ex_0").weightText)
    }

    @Test
    fun unaEdicionQueNoTocaElTipoNiLaUnidadNoCambiaNada() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LASTRE)
        val state = runtime(
            bySet = mapOf("ex_0" to LoadModeV2.LASTRE),
            byExercise = mapOf("ex" to LoadModeV2.LASTRE),
            drafts = mapOf("ex_1" to WorkoutSetDraft(weightText = "5", loadMode = LoadModeV2.LASTRE, isDirty = true)),
        )
        val after = before.copy(sets = before.sets.map { it.copy(targetReps = 12, targetRPE = 8.0) })

        assertSame(state, reconcile(before, after, state))
    }

    @Test
    fun editarUnEjercicioNoTocaElRecuerdoDeLosDemas() {
        val a = exercise("a", LoadModeV2.LOAD, LoadModeV2.LOAD)
        val b = exercise("b", LoadModeV2.LOAD, LoadModeV2.LOAD)
        val state = runtime(
            bySet = mapOf("a_0" to LoadModeV2.LASTRE, "b_0" to LoadModeV2.ASSISTED),
            byExercise = mapOf("a" to LoadModeV2.LASTRE, "b" to LoadModeV2.ASSISTED),
        )
        val editedB = b.withModes(LoadModeV2.LOAD, LoadModeV2.BODYWEIGHT)

        val reconciled = reconcileRuntimeWithPlanEdit(
            session(a, b), session(a, editedB), emptyMap<String, CompletedSet>(), emptyMap(), state,
        )

        assertEquals(LoadModeV2.LASTRE, reconciled.persistedLoadModeBySet["a_0"])
        assertEquals(LoadModeV2.LASTRE, reconciled.persistedLoadModeByExercise["a"])
        assertEquals(LoadModeV2.BODYWEIGHT, shown(editedB, reconciled, 1))
    }

    @Test
    fun unaSerieConRegistroDeUnLadoCuentaComoHechaYNoSeTocaAunqueElPlanCambie() {
        val before = exercise("ex", LoadModeV2.LOAD, LoadModeV2.LOAD)
        val state = runtime(bySet = mapOf("ex_0" to LoadModeV2.LOAD))
        // La serie 1 tiene registrado el lado izquierdo (unilateral): conserva el tipo con el que se registró.
        val after = before.withModes(LoadModeV2.LASTRE, LoadModeV2.LOAD)

        assertSame(state, reconcile(before, after, state, completed = done("ex_0_L")))
    }

    @Test
    fun unaSerieNuevaOUnEjercicioNuevoNoCuentanComoCambioDeTipo() {
        val before = exercise("ex", LoadModeV2.LOAD)
        val after = before.copy(sets = before.sets + ExerciseSet(id = "ex-new", targetReps = 8, loadModeV2 = LoadModeV2.LASTRE))
        val state = runtime(bySet = mapOf("ex_0" to LoadModeV2.LOAD))

        assertSame(state, reconcile(before, after, state))
        assertSame(
            state,
            reconcileRuntimeWithPlanEdit(session(before), session(before, exercise("nuevo", LoadModeV2.ASSISTED)), emptyMap<String, CompletedSet>(), emptyMap(), state),
        )
    }
}
