package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.rirSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección de origen nativo (`KPKN_NATIVE_CURATED`) por receta, no por
 * programa: aplicar una receta de autor encima de un plan nativo —o
 * rematerializar con una receta ajena— NO cuela el preset de aproximaciones
 * nativo dentro de la base del autor, no mezcla con los calentamientos que el
 * autor ya trae y no altera su atribución de origen ni su orden.
 */
class NativeOriginAuthorPreservationTest {
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "origin_${++n}"
    }

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private val nativeInput = PersonalizerInput(
        catalogEntryId = "native:machine-muscle",
        focus = TrainingFocus.FULL_BODY,
        frequency = 3,
        weekdays = listOf(1, 3, 5),
        equipment = setOf("machine"),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = "custom",
        splitPattern = listOf("Pecho", "Descanso", "Brazos", "Descanso", "Piernas", "Descanso", "Descanso"),
        splitName = "Origen",
    )

    private fun nativeProgram(): Program = requireNotNull(
        personalizer().personalize("native-origin", nativeInput).program,
    ) { "La generación nativa debe producir programa" }

    /** Receta de autor: slots AUTHOR (por defecto) y series RIR sin porcentaje. */
    private fun authorRirRecipe(id: String, withAuthoredWarmup: Boolean = false): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    day(
                        "Día autor",
                        listOf(
                            slot(
                                "t1",
                                SlotRole.T1_MAIN,
                                CatalogIds.SQ_LOW,
                                (if (withAuthoredWarmup) listOf(SetRecipe(reps = 5, percent = 40.0, isWarmup = true)) else emptyList()) +
                                    rirSets(4, 5, 2, rest = 180),
                                restSeconds = 180,
                            ),
                            slot(
                                "t2",
                                SlotRole.T2_SUPPLEMENTAL,
                                CatalogIds.BP,
                                rirSets(3, 8, 2, rest = 120),
                                restSeconds = 120,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun exercisesOf(program: Program) = sessionsOf(program).flatMap { it.allExercises() }

    // ─── La receta propia del plan nativo sí recibe la política nativa ────────

    /**
     * Entreno v2: el preset 40/60/80 por patrón dejó de ser la política nativa. La receta propia sigue recibiendo
     * aproximación, pero la automática: movilidad obligatoria en el primer ejercicio y, si es un compuesto con carga a
     * 8–12 repeticiones (no «cercano al 1RM»), una rampa corta de 2 pasos.
     */
    @Test
    fun native_program_materialized_with_its_own_recipe_keeps_the_automatic_approach() {
        val program = nativeProgram()
        val nativeRecipe = requireNotNull(program.sourceRecipe)
        val materialized = PlanMaterializer.materialize(program, nativeRecipe, metadata, SeqIds(), strict = false)

        sessionsOf(materialized).forEach { session ->
            assertTrue("${session.name}: movilidad previa en el primer ejercicio", session.allExercises().first().mobilitySeries.isNotEmpty())
        }
        val withWarmups = exercisesOf(materialized).filter { it.warmupSets.isNotEmpty() }
        assertTrue("La receta nativa RIR sí se aproxima en el primer compuesto", withWarmups.isNotEmpty())
        withWarmups.forEach { exercise ->
            assertEquals(listOf(50.0, 75.0), exercise.warmupSets.map { it.percentageOfWorkingWeight })
        }
        // La atribución de origen sigue intacta: la receta fuente es la nativa y
        // los bloques siguen curados por el motor nativo.
        assertEquals(nativeRecipe.id, materialized.sourceRecipe?.id)
        assertTrue(
            "El bloque materializado conserva el origen nativo curado",
            materialized.macrocycles.flatMap { it.blocks }.all { it.prescriptionOrigin == "KPKN_NATIVE_CURATED" },
        )
        // Y esa condición sobrevive a una rematerialización posterior (no se pierde
        // el reconocimiento del origen nativo tras volver a materializar).
        val weekId = materialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id
        val rematerialized = PlanMaterializer.rematerializeWeek(materialized, weekId, metadata = metadata)
        assertTrue(
            exercisesOf(rematerialized).any { it.warmupSets.isNotEmpty() },
        )
    }

    // ─── Receta de autor sobre programa nativo: base intacta ──────────────────

    /**
     * Cambió de sentido (Entreno v2): ya no hay una política de aproximación solo nativa que la receta de autor «no
     * herede»; la política (automática por defecto) vale para todos los orígenes y COMPLETA lo que el autor no
     * declaró. Lo que sigue intacto es la base: ejercicios, orden, series, RIR y atribución. La rampa que se ve aquí
     * sale del planificador (sentadilla pesada primera: larga; press banca pesado y con articulaciones nuevas: corta),
     * no del preset nativo ni de la receta fuente del programa.
     */
    @Test
    fun author_recipe_over_a_native_program_gets_the_automatic_approach_without_changing_its_base() {
        val native = nativeProgram()
        val author = authorRirRecipe("author-over-native")
        val materialized = PlanMaterializer.materialize(native, author, metadata, SeqIds(), strict = false)

        val exercises = exercisesOf(materialized)
        assertTrue(exercises.isNotEmpty())
        assertEquals(listOf(40.0, 60.0, 80.0), exercises[0].warmupSets.map { it.percentageOfWorkingWeight })
        assertEquals(listOf(50.0, 75.0), exercises[1].warmupSets.map { it.percentageOfWorkingWeight })
        assertTrue("movilidad obligatoria en el primer ejercicio", exercises[0].mobilitySeries.isNotEmpty())
        // Orden, ejercicios y prescripción: los de la receta de autor.
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP), exercises.map { it.catalogConfigurationId })
        assertEquals(listOf(4, 3), exercises.map { it.sets.size })
        assertEquals(listOf(2, 2), exercises.map { it.sets.first().targetRIR })
        // Atribución: el materializado pertenece a la receta de autor, sin rastro nativo.
        assertEquals(author.id, materialized.sourceRecipe?.id)
        assertTrue(
            "Los bloques materializados se atribuyen a la receta de autor",
            materialized.macrocycles.flatMap { it.blocks }.all { it.prescriptionOrigin == author.id },
        )
    }

    @Test
    fun authored_warmups_are_preserved_without_mixing_in_plan_steps() {
        val native = nativeProgram()
        val author = authorRirRecipe("author-with-warmups", withAuthoredWarmup = true)
        val materialized = PlanMaterializer.materialize(native, author, metadata, SeqIds(), strict = false)

        val exercises = exercisesOf(materialized)
        assertEquals(2, exercises.size)
        val first = exercises.first()
        assertEquals(
            "Solo el calentamiento del autor, sin mezclar la rampa del plan",
            listOf(40.0),
            first.warmupSets.map { it.percentageOfWorkingWeight },
        )
        assertEquals(listOf(5), first.warmupSets.map { it.targetReps })
        // Cambió de sentido: el segundo ejercicio (press banca pesado con articulaciones nuevas) ya no queda sin
        // aproximación; la completa el planificador con su rampa corta, sin tocar la del autor del primero.
        assertEquals(listOf(50.0, 75.0), exercises[1].warmupSets.map { it.percentageOfWorkingWeight })
        // La movilidad obligatoria del primer ejercicio se completa aunque el autor declarase su aproximación.
        assertTrue(first.mobilitySeries.isNotEmpty())
    }

    @Test
    fun rematerializing_a_native_week_with_a_foreign_recipe_keeps_the_author_base() {
        val native = nativeProgram()
        val author = authorRirRecipe("foreign-recipe")
        val weekId = native.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id

        val rematerialized = PlanMaterializer.rematerializeWeek(native, weekId, recipe = author, metadata = metadata)
        val exercises = exercisesOf(rematerialized)
        assertTrue(exercises.isNotEmpty())
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP), exercises.map { it.catalogConfigurationId })
        // Cambió de sentido: la base es la del autor y la aproximación la pone el planificador (no el preset nativo).
        assertEquals(listOf(40.0, 60.0, 80.0), exercises[0].warmupSets.map { it.percentageOfWorkingWeight })
        assertEquals(listOf(50.0, 75.0), exercises[1].warmupSets.map { it.percentageOfWorkingWeight })
        // La receta fuente del programa no se sustituye por la ajena.
        assertEquals(native.sourceRecipe?.id, rematerialized.sourceRecipe?.id)
    }

    private fun withSessionAppended(program: Program, weekId: String, session: Session): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { candidate ->
                                    if (candidate.id == weekId) candidate.copy(sessions = candidate.sessions + session) else candidate
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun weekSessions(program: Program, weekId: String): List<Session> =
        sessionsOfWeeks(program).first { it.first == weekId }.second

    private fun sessionsOfWeeks(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.map { it.id to it.sessions }

    private val userCreatedSession = Session(
        id = "user-created-session",
        name = "Sesión propia",
        dayOfWeek = 6,
        assignedDays = listOf(6),
        exercises = listOf(Exercise(id = "user-created-exercise", name = "Remo propio")),
    )

    /**
     * El descarte de las sesiones nativas pendientes sin contraparte en la receta
     * ajena NO alcanza a lo protegido (§14.5): la sesión congelada por edición
     * manual y la creada por el usuario sobreviven intactas. Igual que con el
     * editor real, la sesión creada por el usuario queda marcada con su
     * `ManualSessionOverride` en el mismo guardado (§14.5: «freeze de sesión
     * editada»), y esa marca —no una heurística sobre su contenido— es lo que la
     * distingue del residuo pendiente de la receta anterior. El plan nativo de
     * esta prueba es el histórico (`native:machine-muscle`): su receta no declara
     * ids de día, así que sus sesiones no se reconocen por `rs_…`/`recipeDayId`.
     */
    @Test
    fun rematerializing_a_native_week_with_a_foreign_recipe_keeps_frozen_and_user_created_sessions() {
        val native = nativeProgram()
        val author = authorRirRecipe("foreign-recipe-protected")
        val week = native.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val frozen = week.sessions.last()
        val userSession = userCreatedSession
        val withUserSession = withSessionAppended(native, week.id, userSession)
        val frozenMarked = PlanMaterializer.withManualSessionOverride(
            program = withUserSession,
            sessionId = frozen.id,
            weekId = week.id,
            weekOccurrence = 1,
            recipeDayId = frozen.allExercises().firstNotNullOfOrNull { it.recipeDayId },
        )
        val prepared = PlanMaterializer.withManualSessionOverride(
            program = frozenMarked,
            sessionId = userSession.id,
            weekId = week.id,
            weekOccurrence = 1,
            recipeDayId = null,
            reason = "Sesión guardada desde el editor",
        )

        val rebuilt = PlanMaterializer.rematerializeWeek(prepared, week.id, recipe = author, metadata = metadata)
        val rebuiltSessions = weekSessions(rebuilt, week.id)

        assertEquals("La sesión congelada se conserva íntegra", frozen, rebuiltSessions.single { it.id == frozen.id })
        assertEquals("La sesión del usuario se conserva íntegra", userSession, rebuiltSessions.single { it.id == userSession.id })
        // Lo demás es la base del autor: ninguna sesión nativa pendiente se mezcla.
        val rest = rebuiltSessions.filter { it.id != frozen.id && it.id != userSession.id }
        assertEquals(listOf(CatalogIds.SQ_LOW, CatalogIds.BP), rest.flatMap { it.allExercises() }.map { it.catalogConfigurationId })
    }

    /**
     * Con la receta PROPIA del programa no hay residuo posible: una sesión sin
     * contraparte y sin marca (p. ej. creada antes de que existiera la marca) se
     * conserva intacta y las sesiones del plan mantienen su identidad.
     */
    @Test
    fun rematerializing_with_the_programs_own_recipe_keeps_unmarked_extra_sessions() {
        val native = nativeProgram()
        val week = native.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val withUserSession = withSessionAppended(native, week.id, userCreatedSession)

        val rebuilt = PlanMaterializer.rematerializeWeek(withUserSession, week.id, metadata = metadata)
        val rebuiltSessions = weekSessions(rebuilt, week.id)

        assertEquals("La sesión sin contraparte se conserva íntegra", userCreatedSession, rebuiltSessions.single { it.id == userCreatedSession.id })
        assertEquals(
            "Las sesiones del plan conservan su identidad",
            week.sessions.map { it.id },
            rebuiltSessions.filter { it.id != userCreatedSession.id }.map { it.id },
        )
    }
}
