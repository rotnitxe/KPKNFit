package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
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
 * LV5, auditoría: reemplazos encadenados y su persistencia en el programa.
 *
 * El programa guarda en el hueco el ejercicio del plan original (A). Si en la sesión se reemplaza A por B «solo esta vez» y luego B por
 * C «en todo el programa», la persistencia buscaba a B en el programa, no lo encontraba y no guardaba nada: la persona elegía «todo el
 * programa» y el programa seguía con A.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutReplaceChainedPersistenceTest {

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
        name = "Dominadas",
        exerciseDbId = "pull_up__pronated__medium",
        // La identidad de catálogo va completa (revisión, definición y configuración), como en un ejercicio elegido del catálogo.
        catalogRevision = "rev-a",
        catalogDefinitionId = "pull_up",
        catalogConfigurationId = "pull_up__pronated__medium",
        sets = listOf(ExerciseSet(id = "s0", targetReps = 8), ExerciseSet(id = "s1", targetReps = 8)),
        restTime = 90,
    )

    private val other = Exercise(
        id = "other-1",
        name = "Remo",
        exerciseDbId = "row__barbell",
        catalogDefinitionId = "row",
        catalogConfigurationId = "row__barbell",
        sets = listOf(ExerciseSet(id = "o0", targetReps = 10)),
    )

    private fun plannedSession() = Session(id = "sess-1", name = "Tirón", exercises = listOf(exerciseA(), other))

    private fun programWith(session: Session) = Program(
        id = "prog-1",
        name = "Programa",
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(ProgramWeek(id = "week-1", name = "Semana 1", sessions = listOf(session))),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Before
    fun setUp() {
        val planned = plannedSession()
        fixture = StructuralControllerFixture.open(
            WorkoutUiState(
                session = planned,
                programId = "prog-1",
                weekId = "week-1",
                activeMode = WeekVariant.A,
                // La línea base del plan se toma al arrancar la sesión: es lo que hay en el programa.
                plannedSessionBaseline = planned,
            ),
            programId = "prog-1",
        )
        runBlocking { fixture.repository.addProgramNow(programWith(planned)).getOrThrow() }
    }

    @After
    fun tearDown() = StructuralControllerFixture.close()

    /** Un ejercicio del programa tal como lo guarda (el programa normaliza las identidades al guardarse: no se compara entero). */
    private fun programExercise(id: String): Exercise = fixture.repository.getProgramById("prog-1")!!
        .macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single()
        .exercises.first { it.id == id }

    /** El ejercicio del hueco tal como lo guarda el programa. */
    private fun programSlot(): Exercise = programExercise("slot-1")

    /** El otro ejercicio de la sesión sigue igual en el programa: mismo ejercicio y mismas series. */
    private fun assertOtherExerciseUntouched() {
        val untouched = programExercise("other-1")
        assertEquals("Remo", untouched.name)
        assertEquals("row__barbell", untouched.catalogConfigurationId)
        assertEquals(other.sets, untouched.sets)
    }

    private fun replaceAndAnswer(replacement: ExerciseMuscleInfo, scope: ReplacementPersistenceScopeV2) {
        fixture.controller.replaceExercise("slot-1", replacement, deferPersistencePrompt = true)
        fixture.controller.showDeferredReplacementPromptIfNeeded("slot-1")
        assertTrue("la pregunta de persistencia debe aparecer", fixture.state.pendingReplacementPersistencePrompt != null)
        fixture.controller.commitPendingReplacementPersistence(scope)
        assertNull(fixture.state.pendingReplacementPersistencePrompt)
    }

    @Test
    fun unReemplazoPermanenteSustituyeAlEjercicioDelHuecoEnElPrograma() {
        replaceAndAnswer(infoB, ReplacementPersistenceScopeV2.PERMANENT)

        val saved = programSlot()
        assertEquals("bench_press__barbell", saved.catalogConfigurationId)
        // Con lo que define el producto: las series planificadas y el descanso del hueco; nada de otro ejercicio.
        assertEquals(2, saved.sets.size)
        assertEquals(90, saved.restTime)
        assertOtherExerciseUntouched()
    }

    @Test
    fun soloEstaVezNoTocaElPrograma() {
        replaceAndAnswer(infoB, ReplacementPersistenceScopeV2.SESSION_ONLY)

        assertEquals("pull_up__pronated__medium", programSlot().catalogConfigurationId)
        assertEquals("bench_press__barbell", fixture.exercise("slot-1").catalogConfigurationId)
    }

    @Test
    fun reemplazarAPorBSoloEstaVezYLuegoBPorCEnTodoElProgramaDejaAEnElProgramaConC() {
        replaceAndAnswer(infoB, ReplacementPersistenceScopeV2.SESSION_ONLY)
        assertEquals("el programa sigue con el ejercicio del plan", "pull_up__pronated__medium", programSlot().catalogConfigurationId)

        replaceAndAnswer(infoC, ReplacementPersistenceScopeV2.PERMANENT)

        val saved = programSlot()
        assertEquals("push_up__bodyweight", saved.catalogConfigurationId)
        assertEquals("Flexiones", saved.name)
        assertEquals(2, saved.sets.size)
        assertEquals(90, saved.restTime)
        // La sesión en vivo también tiene a C, y el resto del programa no se tocó.
        assertEquals("push_up__bodyweight", fixture.exercise("slot-1").catalogConfigurationId)
        assertOtherExerciseUntouched()
    }

    @Test
    fun siElProgramaYaTieneAlEjercicioActualNoSeAplicaDosVeces() {
        // A por B en todo el programa, y luego B por C en todo el programa: cada paso sustituye al que había, sin saltarse ni repetirse.
        replaceAndAnswer(infoB, ReplacementPersistenceScopeV2.PERMANENT)
        assertEquals("bench_press__barbell", programSlot().catalogConfigurationId)

        replaceAndAnswer(infoC, ReplacementPersistenceScopeV2.PERMANENT)

        assertEquals("push_up__bodyweight", programSlot().catalogConfigurationId)
    }
}
