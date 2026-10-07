package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.onboarding.AuthoredPlanFixtures
import com.example.kpkn.domain.onboarding.AuthoredPlanMaterializer
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.missingFixedRecipeEquipment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integración de la aproximación y movilidad obligatorias con planes REALES: dos nativos propios, el nativo histórico,
 * dos plantillas con receta, 5/3/1 y PHUL (autor). Se materializan con la política por defecto (`warmup == null`,
 * automática) y se comprueban las reglas del usuario sobre las sesiones resultantes: movilidad obligatoria en el primer
 * ejercicio, aproximación solo en ejercicios con carga, nada del cuarto ejercicio en adelante (salvo lo que declaró el
 * autor), lo del autor intacto, idempotencia y que el estimador de tiempo lo cuenta.
 */
class ApproachMaterializationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private val infoOf get() = ApproachInfoProvider.fromMetadata(metadata)

    private class SeqIds(private val prefix: String = "id") : IdProvider {
        private var n = 0
        override fun newId(): String = "${prefix}_${++n}"
    }

    private val noWarmups = TrainingOptions(warmup = emptyList())

    /**
     * Un plan real ya materializado y, si lo trae un autor o una receta fija, la misma materialización con
     * `warmup = emptyList()` (sin aproximaciones automáticas: solo queda lo que la receta declara).
     */
    private class Sample(val label: String, val program: Program, val authorOnly: Program?)

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } },
    )

    private fun native(
        entryId: String,
        days: Int,
        level: CatalogLevel,
        equipment: Set<String>,
        minutes: Int = 100,
    ): Sample {
        val result = personalizer().personalize(
            "approach-${entryId.removePrefix("native:")}-$days",
            PersonalizerInput(
                catalogEntryId = entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                equipment = equipment,
                level = level,
                availableMinutes = minutes,
            ),
        )
        val program = requireNotNull(result.program) { "$entryId: ${result.report.limitations} (${result.report.reasonCode})" }
        return Sample("$entryId/$days días/${level.name}", program, authorOnly = null)
    }

    private fun template(templateId: String): Sample {
        val template = PROGRAM_TEMPLATES.first { it.id == templateId }
        fun build(options: TrainingOptions) = ProgramTemplateEngine.applyTemplate(
            Program(
                id = "approach-$templateId",
                name = templateId,
                structure = ProgramStructure.SIMPLE,
                goals = ProgramGoals(squat1RM = 180.0, bench1RM = 120.0, deadlift1RM = 210.0),
            ),
            template,
            defaultOptions = options,
        ).program
        return Sample("plantilla $templateId", build(TrainingOptions()), build(noWarmups))
    }

    private fun protocol(protocolId: String): Sample {
        val protocol = PROTOCOL_LIBRARY.first { it.id == protocolId }
        fun build(options: TrainingOptions) = ProgramProtocolEngine.applyProtocol(
            Program(id = "approach-$protocolId", name = protocolId),
            protocol,
            SeqIds(protocolId),
            defaultOptions = options,
        )
        return Sample("protocolo $protocolId", build(TrainingOptions()), build(noWarmups))
    }

    private fun authored(planId: String): Sample {
        val request = AuthoredPlanFixtures.request(AuthoredPlanFixtures.entry(planId), AuthoredPlanFixtures.fullGym, SeqIds(planId))
        val automatic = AuthoredPlanMaterializer.prepare(request)
        val authorOnly = AuthoredPlanMaterializer.prepare(
            request.copy(options = request.options.copy(warmup = emptyList()), idProvider = SeqIds(planId)),
        )
        return Sample("autor $planId", automatic, authorOnly)
    }

    private val samples: List<Sample> by lazy {
        listOf(
            native("native:strength-foundation-v2", days = 3, level = CatalogLevel.INTERMEDIATE, equipment = setOf("bodyweight", "barbell", "rack", "bench")),
            native("native:powerbuilding-foundation-v2", days = 4, level = CatalogLevel.INTERMEDIATE, equipment = setOf("bodyweight", "barbell", "rack", "bench")),
            native("native:complete-athlete-v2", days = 4, level = CatalogLevel.INTERMEDIATE, equipment = setOf("bodyweight", "barbell", "rack", "bench")),
            native("native:muscle-foundation-v2", days = 4, level = CatalogLevel.BEGINNER, equipment = setOf("bodyweight", "dumbbells")),
            native("native:machine-muscle", days = 3, level = CatalogLevel.INTERMEDIATE, equipment = setOf("machine")),
            template("power-16-4"),
            template("body-16-4"),
            protocol("wendler-531-bbb"),
            authored(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID),
        )
    }

    // ─── Lectura ────────────────────────────────────────────────────────────

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    /** Ejercicios de fuerza de la sesión, en el orden en que se hacen. */
    private fun strengthOf(session: Session): List<Exercise> =
        session.allExercises().distinctBy { it.id }.filter { it.cardioDetails == null && it.sets.any { set -> !set.isEmptySlot } }

    private fun Exercise.mobilitySeconds() = mobilitySeries.sumOf { it.durationSeconds ?: 0 }

    private fun optionsOf(program: Program) = ApproachOptions(level = approachLevelOf(program.sourceRecipe?.claimedLevel))

    /** La aproximación que la receta del autor declara en (sesión, ejercicio), según la materialización sin automática. */
    private fun authorWarmupsAt(sample: Sample, sessionIndex: Int, exerciseIndex: Int) =
        sample.authorOnly?.let(::sessionsOf)?.getOrNull(sessionIndex)?.let(::strengthOf)?.getOrNull(exerciseIndex)?.warmupSets.orEmpty()

    // ─── Las reglas, sobre cada plan ────────────────────────────────────────

    @Test
    fun every_session_has_the_mandatory_mobility_on_its_first_exercise() {
        samples.forEach { sample ->
            val sessions = sessionsOf(sample.program).filter { strengthOf(it).isNotEmpty() }
            assertTrue("${sample.label}: sin sesiones de fuerza", sessions.isNotEmpty())
            sessions.forEach { session ->
                val first = strengthOf(session).first()
                if (infoOf(first) == null) return@forEach
                assertTrue(
                    "${sample.label} / ${session.name}: el primer ejercicio '${first.name}' no tiene movilidad previa",
                    first.mobilitySeries.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun only_the_first_three_exercises_are_prepared_beyond_what_the_author_declared() {
        samples.forEach { sample ->
            sessionsOf(sample.program).forEachIndexed { sessionIndex, session ->
                strengthOf(session).forEachIndexed { index, exercise ->
                    if (index < 3) return@forEachIndexed
                    assertEquals(
                        "${sample.label} / ${session.name}: el ejercicio ${index + 1} '${exercise.name}' lleva aproximación que no es del autor",
                        authorWarmupsAt(sample, sessionIndex, index).map { it.percentageOfWorkingWeight },
                        exercise.warmupSets.map { it.percentageOfWorkingWeight },
                    )
                    assertTrue(
                        "${sample.label} / ${session.name}: el ejercicio ${index + 1} '${exercise.name}' lleva movilidad",
                        exercise.mobilitySeries.isEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun ramps_are_only_on_loaded_exercises_and_are_well_formed() {
        samples.forEach { sample ->
            sessionsOf(sample.program).forEachIndexed { sessionIndex, session ->
                strengthOf(session).forEachIndexed { index, exercise ->
                    if (exercise.warmupSets.isEmpty()) return@forEachIndexed
                    val percents = exercise.warmupSets.map { it.percentageOfWorkingWeight }
                    assertEquals("${sample.label}: porcentajes ascendentes", percents.sorted(), percents)
                    assertEquals("ids únicos", exercise.warmupSets.size, exercise.warmupSets.map { it.id }.toSet().size)
                    if (authorWarmupsAt(sample, sessionIndex, index).isNotEmpty()) return@forEachIndexed
                    val info = infoOf(exercise)
                    assertNotNull("${sample.label}: '${exercise.name}' con rampa y sin información de catálogo", info)
                    assertTrue(
                        "${sample.label} / ${session.name}: '${exercise.name}' es de aislamiento o ligero y lleva rampa",
                        info!!.canBeHeavy,
                    )
                    assertTrue("${sample.label}: 1–4 pasos entre 10 % y 90 % ($percents)", percents.size in 1..4 && percents.all { it in 10.0..90.0 })
                    assertTrue(
                        "${sample.label}: descansos de 30 a 60 s",
                        exercise.warmupSets.all { (it.restBetween ?: 0) in 30..60 },
                    )
                }
            }
        }
    }

    @Test
    fun mobility_is_executable_catalog_backed_and_within_budget() {
        samples.forEach { sample ->
            sessionsOf(sample.program).forEach { session ->
                session.allExercises().filter { it.mobilitySeries.isNotEmpty() }.forEach { exercise ->
                    assertTrue("${sample.label}: '${exercise.name}' ≤ 4 min de movilidad (${exercise.mobilitySeconds()} s)", exercise.mobilitySeconds() <= 240)
                    assertEquals(
                        "${sample.label}: sin movimientos repetidos en '${exercise.name}'",
                        exercise.mobilitySeries.size,
                        exercise.mobilitySeries.map { it.id }.toSet().size,
                    )
                    exercise.mobilitySeries.forEach { series ->
                        assertNotNull("${sample.label}: ${series.id} no está en el catálogo de movilidad", JointMobility.movement(series.id))
                        assertTrue("ejecutable: ${series.sets} × ${series.durationSeconds}", series.sets > 0 && (series.durationSeconds ?: 0) in 30..45)
                    }
                }
            }
        }
    }

    @Test
    fun what_the_author_declared_is_never_replaced_or_duplicated() {
        samples.filter { it.authorOnly != null }.forEach { sample ->
            val automatic = sessionsOf(sample.program)
            val authorOnly = sessionsOf(requireNotNull(sample.authorOnly))
            assertEquals("${sample.label}: mismas sesiones", authorOnly.size, automatic.size)
            automatic.zip(authorOnly).forEach { (generated, author) ->
                val completedExercises = strengthOf(generated)
                val originalExercises = strengthOf(author)
                assertEquals(originalExercises.size, completedExercises.size)
                completedExercises.zip(originalExercises).forEach { (completed, original) ->
                    // La prescripción de trabajo es idéntica; lo que el autor trajo se conserva tal cual.
                    assertEquals("${sample.label}: series de '${original.name}' intactas", original.sets.size, completed.sets.size)
                    if (original.warmupSets.isNotEmpty()) {
                        assertEquals(
                            "${sample.label}: aproximación del autor de '${original.name}'",
                            original.warmupSets.map { it.percentageOfWorkingWeight to it.targetReps },
                            completed.warmupSets.map { it.percentageOfWorkingWeight to it.targetReps },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun applying_the_planner_again_to_a_materialized_session_changes_nothing() {
        samples.forEach { sample ->
            val options = optionsOf(sample.program)
            sessionsOf(sample.program).forEach { session ->
                assertEquals(
                    "${sample.label} / ${session.name}: no es idempotente",
                    session,
                    ApproachPlanner.apply(session, options, infoOf),
                )
            }
        }
    }

    @Test
    fun the_default_policy_prepares_sessions_while_explicit_and_empty_policies_do_not_run_the_planner() {
        val template = PROGRAM_TEMPLATES.first { it.id == "power-16-4" }
        fun build(options: TrainingOptions) = ProgramTemplateEngine.applyTemplate(
            Program(
                id = "policy",
                name = "policy",
                structure = ProgramStructure.SIMPLE,
                goals = ProgramGoals(squat1RM = 180.0, bench1RM = 120.0, deadlift1RM = 210.0),
            ),
            template,
            defaultOptions = options,
        ).program

        fun steps(exercise: Exercise) = exercise.warmupSets.map { it.percentageOfWorkingWeight to it.targetReps }

        // «Sin calentamientos»: queda solo lo que la receta del autor declara (la plantilla de potencia trae el suyo).
        val baselineSessions = sessionsOf(build(TrainingOptions(warmup = emptyList())))
        val baseline = baselineSessions.flatMap { it.allExercises() }
        assertTrue("vacía = sin movilidad automática", baseline.all { it.mobilitySeries.isEmpty() })

        val automatic = sessionsOf(build(TrainingOptions()))
        assertTrue(
            "automática: movilidad en el primer ejercicio de cada sesión de fuerza",
            automatic.filter { strengthOf(it).isNotEmpty() }.all { strengthOf(it).first().mobilitySeries.isNotEmpty() },
        )
        val automaticExercises = automatic.flatMap { it.allExercises() }
        assertEquals(baseline.size, automaticExercises.size)
        assertTrue(
            "la automática solo añade: lo declarado por la receta se conserva",
            automaticExercises.zip(baseline).all { (completed, original) ->
                original.warmupSets.isEmpty() || steps(completed) == steps(original)
            },
        )
        assertTrue(
            "y añade aproximación donde la receta no traía",
            automaticExercises.zip(baseline).any { (completed, original) -> original.warmupSets.isEmpty() && completed.warmupSets.isNotEmpty() },
        )

        // Pasos propios: mandan en el primer compuesto de cada patrón y el planificador no corre (sin movilidad).
        val explicit = sessionsOf(
            build(TrainingOptions(warmup = listOf(SetRecipe(reps = 6, percent = 35.0), SetRecipe(reps = 2, percent = 70.0)))),
        ).flatMap { it.allExercises() }
        assertEquals(baseline.size, explicit.size)
        val ownSteps = explicit.zip(baseline).filter { (chosen, original) -> steps(chosen) != steps(original) }
        assertTrue("lista explícita: pasos propios en algún primer compuesto sin aproximación de autor", ownSteps.isNotEmpty())
        ownSteps.forEach { (chosen, _) -> assertEquals(listOf(35.0 to 6, 70.0 to 2), steps(chosen)) }
        assertTrue("lista explícita: sin movilidad automática", explicit.all { it.mobilitySeries.isEmpty() })
    }

    @Test
    fun a_native_program_rematerialized_keeps_the_mandatory_mobility_and_is_deterministic() {
        val sample = samples.first { it.label.startsWith("native:strength-foundation-v2") }
        val program = sample.program
        val weekId = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().id
        val rematerialized = PlanMaterializer.rematerializeWeek(program, weekId, metadata = metadata)
        val firstWeek = rematerialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        firstWeek.sessions.filter { strengthOf(it).isNotEmpty() }.forEach { session ->
            assertTrue("${session.name}: movilidad tras rematerializar", strengthOf(session).first().mobilitySeries.isNotEmpty())
        }
        // Misma entrada → mismo programa (ids deterministas): la rampa y la movilidad no introducen azar.
        val again = native("native:strength-foundation-v2", days = 3, level = CatalogLevel.INTERMEDIATE, equipment = setOf("bodyweight", "barbell", "rack", "bench"))
        assertEquals(program.macrocycles, again.program.macrocycles)
    }

    // ─── Guardia de material ────────────────────────────────────────────────

    private val equipmentProfiles = listOf(
        setOf("dumbbells"),
        setOf("barbell"),
        setOf("barbell", "bench"),
        setOf("machine"),
        setOf("bodyweight"),
    )

    /**
     * La movilidad cuelga de `Exercise.mobilitySeries` y `missingFixedRecipeEquipment` se saltaba los ejercicios con
     * `mobilitySeries`/`mobilityConfig` (los trataba como tarjetas de movilidad): en `smolov`, `texas-method-3d` y
     * `body-16-4` el primer ejercicio (la sentadilla con barra) dejaba de pedir el rack. La guardia ahora solo se salta la
     * tarjeta de movilidad pura (sin series de fuerza); esta prueba y `FixedRecipeEquipmentCompatibilityTest` lo fijan.
     */
    @Test
    fun the_material_guard_gives_the_same_answer_with_and_without_automatic_approach_on_real_plans() {
        samples.filter { it.authorOnly != null }.forEach { sample ->
            equipmentProfiles.forEach { equipment ->
                assertEquals(
                    "${sample.label} con $equipment: la guardia de material cambia por la movilidad del primer ejercicio",
                    missingFixedRecipeEquipment(sample.authorOnly!!, equipment, catalog),
                    missingFixedRecipeEquipment(sample.program, equipment, catalog),
                )
            }
        }
    }

    /** El mismo hueco en su forma mínima: el único ejercicio con barra de la receta es el primero y lleva movilidad. */
    @Test
    fun the_material_guard_still_checks_a_lone_first_exercise_that_carries_mobility() {
        val recipe = TrainingPlanRecipe(
            id = "guard-recipe",
            weeks = listOf(
                weekRecipe(
                    1, 0, "Base", BlockGoal.ACCUMULATION,
                    listOf(day("Día", listOf(slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, percentSets(180, 5 to 75.0), restSeconds = 180)))),
                ),
            ),
        )
        val program = PlanMaterializer.materialize(Program(id = "guard", name = "guard"), recipe, metadata, SeqIds(), strict = false)
        val bench = sessionsOf(program).first().allExercises().first()
        assertTrue("el press banca lleva movilidad previa", bench.mobilitySeries.isNotEmpty())
        val missing = missingFixedRecipeEquipment(program, setOf("dumbbells"), catalog)
        assertTrue("con solo mancuernas faltan la barra y el banco, y la guardia lo ve: $missing", missing.isNotEmpty())
    }

    /**
     * Lo que garantiza el planificador y sostiene la guardia de material: un ejercicio con movilidad prevista sigue
     * siendo un ejercicio de fuerza con series y con identidad de catálogo (nunca una tarjeta de movilidad pura).
     */
    @Test
    fun exercises_that_get_planned_mobility_stay_catalog_backed_strength_exercises() {
        samples.forEach { sample ->
            sessionsOf(sample.program).flatMap { it.allExercises() }.filter { it.mobilitySeries.isNotEmpty() }.forEach { exercise ->
                assertFalse("${sample.label}: '${exercise.name}' lleva movilidad y perdió su identidad de catálogo", exercise.catalogConfigurationId.isNullOrBlank())
                assertTrue("${sample.label}: '${exercise.name}' lleva movilidad y no tiene series de fuerza", exercise.sets.isNotEmpty())
            }
        }
    }

    // ─── Tiempo ─────────────────────────────────────────────────────────────

    private fun stripped(session: Session): Session = session.copy(
        exercises = session.exercises.map { it.copy(warmupSets = emptyList(), mobilitySeries = emptyList()) },
        parts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map { it.copy(warmupSets = emptyList(), mobilitySeries = emptyList()) })
        },
    )

    private fun rampSecondsOf(exercises: List<Exercise>): Int =
        exercises.sumOf { exercise -> exercise.warmupSets.sumOf { 30 + (it.restBetween ?: 45) } }

    private class Overhead(val seconds: Int, val rampSeconds: Int, val mobilitySeconds: Int)

    /**
     * Lo que suma la aproximación NUEVA a cada sesión de fuerza: contra la misma materialización sin aproximaciones
     * automáticas (conserva lo que declara el autor) o, en los planes propios, contra la sesión sin rampa ni movilidad.
     */
    private fun overheadsOf(sample: Sample): List<Overhead> {
        val baseline = sample.authorOnly?.let(::sessionsOf)
        return sessionsOf(sample.program).mapIndexedNotNull { index, session ->
            if (strengthOf(session).isEmpty()) return@mapIndexedNotNull null
            val reference = baseline?.getOrNull(index)
            val seconds = SessionDurationEstimator.estimate(session).totalSeconds -
                SessionDurationEstimator.estimate(reference ?: stripped(session)).totalSeconds
            val ramp = rampSecondsOf(strengthOf(session)) - (reference?.let { rampSecondsOf(strengthOf(it)) } ?: 0)
            Overhead(seconds, ramp, strengthOf(session).sumOf { it.mobilitySeconds() })
        }
    }

    @Test
    fun the_duration_estimator_counts_ramp_and_mobility_and_the_overhead_is_reasonable() {
        val report = StringBuilder("\n[D3] minutos que suma la aproximación nueva por sesión de fuerza (estimador común §12.2)\n")
        samples.forEach { sample ->
            val overheads = overheadsOf(sample)
            overheads.forEach { overhead ->
                assertEquals(
                    "${sample.label}: el estimador suma exactamente la rampa nueva + la movilidad",
                    overhead.rampSeconds + overhead.mobilitySeconds,
                    overhead.seconds,
                )
                assertTrue("${sample.label}: ${overhead.seconds / 60.0} min de aproximación nueva", overhead.seconds in 0..(16 * 60))
            }
            val avg = overheads.map { it.seconds }.average() / 60.0
            report.append(
                "  %-46s sesiones=%3d  total=%.1f min  (rampa %.1f + movilidad %.1f)  máx=%.1f min\n".format(
                    sample.label,
                    overheads.size,
                    avg,
                    overheads.map { it.rampSeconds }.average() / 60.0,
                    overheads.map { it.mobilitySeconds }.average() / 60.0,
                    overheads.maxOf { it.seconds } / 60.0,
                ),
            )
            assertTrue("${sample.label}: promedio razonable ($avg min)", avg in 0.3..10.0)
        }
        println(report)
        assertFalse(samples.isEmpty())
    }
}
