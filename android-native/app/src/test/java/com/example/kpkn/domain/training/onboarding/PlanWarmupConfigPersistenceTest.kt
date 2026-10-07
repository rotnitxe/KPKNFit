package com.example.kpkn.domain.training.onboarding

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.SetupTrainingOptions
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistencia de la elección de calentamientos en el JSON del programa
 * (`Program.planWarmupConfig`) y su uso real en la rematerialización: la
 * elección del usuario (vacía o personalizada) NO se pierde para reintroducir
 * el preset 40 % × 8 / 60 % × 5 / 80 % × 3, y los programas que no guardan
 * nada siguen recibiendo el preset de siempre.
 */
class PlanWarmupConfigPersistenceTest {
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val catalog get() = CatalogCompositionTestSupport.catalog

    private class SeqIds : IdProvider {
        private var n = 0
        override fun newId(): String = "cfg_${++n}"
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
        splitName = "Persistencia",
    )

    private fun generate(options: SetupTrainingOptions = SetupTrainingOptions()): Program =
        requireNotNull(personalizer().personalize("warmup-persist", nativeInput, options).program) {
            "La generación nativa debe producir programa"
        }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun firstWeekId(program: Program): String =
        program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id

    private fun warmupsOf(program: Program): List<List<Double>> =
        sessionsOf(program).flatMap { it.allExercises() }
            .filter { it.warmupSets.isNotEmpty() }
            .map { exercise -> exercise.warmupSets.map { it.percentageOfWorkingWeight } }

    private fun authorPercentRecipe(id: String = "author-warmup-recipe"): TrainingPlanRecipe = TrainingPlanRecipe(
        id = id,
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(
                    day(
                        "Día",
                        listOf(
                            slot("t1", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 75.0), restSeconds = 180),
                            slot("t2", SlotRole.T2_SUPPLEMENTAL, CatalogIds.BP, percentSets(180, 8 to 60.0), restSeconds = 120),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun materializedAuthor(program: Program = Program(id = "p", name = "T"), options: SetupTrainingOptions = SetupTrainingOptions()): Program =
        PlanMaterializer.materialize(program, authorPercentRecipe(), metadata, SeqIds(), strict = false, options = options)

    // ─── Ruta nativa: la elección se persiste y sobrevive ─────────────────────

    /** Entreno v2: `null` ya no significa «preset 40/60/80» sino aproximación automática (movilidad + rampa si pesa). */
    @Test
    fun native_generation_persists_the_choice_null_means_automatic_approach() {
        val automatic = generate()
        assertNull("Sin elección explícita no se guarda nada: rige la aproximación automática", automatic.planWarmupConfig)
        val sessions = sessionsOf(automatic)
        assertTrue(
            "La movilidad obligatoria del primer ejercicio está en la ruta nativa",
            sessions.isNotEmpty() && sessions.all { it.allExercises().first().mobilitySeries.isNotEmpty() },
        )
        assertTrue(
            "Ya no hay preset 40/60/80 en cada primer compuesto: solo rampas cortas en lo que abre la sesión",
            warmupsOf(automatic).all { it == listOf(50.0, 75.0) },
        )
    }

    @Test
    fun rematerializing_never_reintroduces_the_automatic_approach_after_an_empty_choice() {
        val program = generate(SetupTrainingOptions(warmup = emptyList()))
        assertEquals(emptyList<SetRecipe>(), program.planWarmupConfig)
        assertTrue("Sin calentamientos en la generación", warmupsOf(program).isEmpty())

        val rematerialized = PlanMaterializer.rematerializeWeek(program, firstWeekId(program), metadata = metadata)
        val exercises = sessionsOf(rematerialized).flatMap { it.allExercises() }
        assertTrue(
            "La rematerialización respeta la elección vacía y no reintroduce la aproximación",
            exercises.all { it.warmupSets.isEmpty() },
        )
        assertTrue("ni la movilidad automática", exercises.all { it.mobilitySeries.isEmpty() })
        assertEquals(emptyList<SetRecipe>(), rematerialized.planWarmupConfig)
    }

    @Test
    fun rematerializing_keeps_declared_custom_steps() {
        val custom = listOf(SetRecipe(reps = 6, percent = 35.0), SetRecipe(reps = 2, percent = 70.0))
        val program = generate(SetupTrainingOptions(warmup = custom))
        assertEquals(listOf(35.0, 70.0), program.planWarmupConfig?.map { it.percent })

        val rematerialized = PlanMaterializer.rematerializeWeek(program, firstWeekId(program), metadata = metadata)
        val withWarmups = sessionsOf(rematerialized).flatMap { it.allExercises() }.filter { it.warmupSets.isNotEmpty() }
        assertTrue("Los pasos propios se aplican al primer compuesto", withWarmups.isNotEmpty())
        withWarmups.forEach { exercise ->
            assertEquals(listOf(35.0, 70.0), exercise.warmupSets.map { it.percentageOfWorkingWeight })
            assertEquals(listOf(6, 2), exercise.warmupSets.map { it.targetReps })
        }
        // Con pasos propios no corre el planificador: ni la rampa automática ni la movilidad.
        warmupsOf(rematerialized).forEach { steps -> assertTrue(steps.none { it == 50.0 || it == 75.0 || it == 40.0 || it == 60.0 || it == 80.0 }) }
        assertTrue(sessionsOf(rematerialized).flatMap { it.allExercises() }.all { it.mobilitySeries.isEmpty() })
    }

    @Test
    fun rematerializing_without_persisted_choice_keeps_the_automatic_approach() {
        val program = generate()
        val rematerialized = PlanMaterializer.rematerializeWeek(program, firstWeekId(program), metadata = metadata)
        val firstWeek = rematerialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        assertTrue(firstWeek.sessions.isNotEmpty())
        assertTrue(
            "Sin elección persistida la rematerialización sigue aproximando el primer ejercicio",
            firstWeek.sessions.all { it.allExercises().first().mobilitySeries.isNotEmpty() },
        )
        warmupsOf(rematerialized).forEach { assertEquals(listOf(50.0, 75.0), it) }
    }

    // ─── Materialización: legacy intacta + elección explícita persistida ──────

    /**
     * Cambió de sentido (Entreno v2): con las opciones por defecto una receta de autor ya no recibe el preset
     * 40/60/80 en cada primer compuesto de patrón sino la aproximación automática: la sentadilla pesada que abre el
     * día lleva la rampa larga y el press (pesado, articulaciones nuevas) la corta. Sigue sin persistirse nada.
     */
    @Test
    fun author_recipe_with_default_options_gets_the_automatic_approach_and_persists_nothing() {
        val program = materializedAuthor()
        assertNull(program.planWarmupConfig)
        val exercises = sessionsOf(program).flatMap { it.allExercises() }
        assertEquals(listOf(40.0, 60.0, 80.0), exercises[0].warmupSets.map { it.percentageOfWorkingWeight })
        assertEquals(listOf(50.0, 75.0), exercises[1].warmupSets.map { it.percentageOfWorkingWeight })
        assertTrue(exercises[0].mobilitySeries.isNotEmpty())
    }

    @Test
    fun explicit_empty_choice_is_persisted_so_the_next_rematerialization_stays_empty() {
        val first = materializedAuthor(options = SetupTrainingOptions(warmup = emptyList()))
        assertEquals(emptyList<SetRecipe>(), first.planWarmupConfig)
        assertTrue(sessionsOf(first).flatMap { it.allExercises() }.all { it.warmupSets.isEmpty() })

        // options por defecto en la llamada siguiente: manda lo persistido.
        val again = PlanMaterializer.rematerializeWeek(first, firstWeekId(first), metadata = metadata)
        assertTrue(
            "Options por defecto no puede devolver la aproximación tras una elección vacía guardada",
            sessionsOf(again).flatMap { it.allExercises() }.all { it.warmupSets.isEmpty() && it.mobilitySeries.isEmpty() },
        )
    }

    @Test
    fun persisted_choice_wins_over_a_default_options_call_on_full_materialization() {
        val custom = listOf(SetRecipe(reps = 4, percent = 45.0))
        val program = materializedAuthor().copy(planWarmupConfig = custom)
        val rematerialized = PlanMaterializer.materialize(
            program,
            authorPercentRecipe(),
            metadata,
            SeqIds(),
            strict = false,
        )
        assertEquals(listOf(45.0), rematerialized.planWarmupConfig?.map { it.percent })
        val warmups = sessionsOf(rematerialized).flatMap { it.allExercises() }.filter { it.warmupSets.isNotEmpty() }
        assertTrue(warmups.isNotEmpty())
        warmups.forEach { exercise ->
            assertEquals(listOf(45.0), exercise.warmupSets.map { it.percentageOfWorkingWeight })
            assertEquals(listOf(4), exercise.warmupSets.map { it.targetReps })
        }
    }

    // ─── AUTO sin confirmación: rechazo explícito en ambas rutas ──────────────

    @Test
    fun unconfirmed_auto_is_rejected_by_materialization_and_rematerialization() {
        val unconfirmed = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO)
        val materializeFailure = runCatching {
            PlanMaterializer.materialize(Program(id = "p", name = "T"), authorPercentRecipe(), metadata, SeqIds(), strict = false, options = unconfirmed)
        }.exceptionOrNull()
        assertTrue("materialize debe rechazar AUTO sin confirmar", materializeFailure is IllegalArgumentException)
        assertTrue(materializeFailure?.message.orEmpty().contains("confirmación"))

        val program = materializedAuthor()
        val rematerializeFailure = runCatching {
            PlanMaterializer.rematerializeWeek(program, firstWeekId(program), metadata = metadata, options = unconfirmed)
        }.exceptionOrNull()
        assertTrue("rematerializeWeek debe rechazar AUTO sin confirmar", rematerializeFailure is IllegalArgumentException)
        assertTrue(rematerializeFailure?.message.orEmpty().contains("confirmación"))

        // Y la ruta nativa también lo rechaza (sin programa, con el motivo).
        val native = personalizer().personalize("auto-unconfirmed", nativeInput, unconfirmed)
        assertNull(native.program)
        assertTrue(native.report.limitations.any { it.contains("confirmación", ignoreCase = true) })
    }

    @Test
    fun confirmed_auto_passes_both_paths() {
        val confirmed = SetupTrainingOptions(autoregulationMode = AutoregulationMode.AUTO, automaticConfirmed = true)
        val materialized = materializedAuthor(options = confirmed)
        assertEquals(
            "materialize no fuerza el modo: conserva el del programa",
            AutoregulationMode.OFF,
            materialized.autoregulationMode,
        )
        val program = generate(confirmed)
        assertEquals(AutoregulationMode.AUTO, program.autoregulationMode)
        val rematerialized = PlanMaterializer.rematerializeWeek(program, firstWeekId(program), metadata = metadata)
        assertEquals(
            "La rematerialización no silencia el modo persistido",
            AutoregulationMode.AUTO,
            rematerialized.autoregulationMode,
        )
    }
}
