package com.example.kpkn.data.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.onboarding.SetupCommitCoordinator
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanCatalogSnapshot
import com.example.kpkn.domain.onboarding.PlanGoalProfile
import com.example.kpkn.domain.onboarding.PlanMaterializationOutcome
import com.example.kpkn.domain.onboarding.PlanMaterializationPort
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.ObservedWorkingSet
import com.example.kpkn.domain.training.OnboardingPlanGenerator
import com.example.kpkn.domain.training.PlanLoadResolution
import com.example.kpkn.domain.training.PlanLoadResolver
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.data.models.CardioType as ModelCardioType
import com.example.kpkn.data.protocols.definitions.NativeProfileKind as NativeKind
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.screens.sessioneditor.SessionEditorViewModel
import com.example.kpkn.screens.sessioneditor.saveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * T-006 Q5 composition witnesses: prepared preview → real Room commit/receipt →
 * close/reopen → repository workout/progress/rebuild. Separate cases keep the
 * authored originals and rollback oracle readable instead of forming a product
 * matrix. No materialization override or fake repository is used here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class T006PersistenceAndUseIntegrationTest {

    companion object {
        private const val EXERCISE_REVISION = "q5-approved-fixture"

        @BeforeClass
        @JvmStatic
        fun installCatalog() {
            CatalogCompositionTestSupport.install()
        }
    }

    @get:Rule
    val testName = TestName()

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application
    private var workoutOrdinal = 0

    /** Archivo Room propio de cada test: ninguna corrida hereda ni comparte estado con otra. */
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Nombre corto: Robolectric ya incluye el nombre completo del método en el directorio de datos y
        // Windows rechaza rutas > 260 caracteres (SQLiteCantOpenDatabaseException).
        databaseName = "t006-${testName.methodName.hashCode().toUInt().toString(36)}-${UUID.randomUUID().toString().take(8)}.db"
        closePersistentRepository()
        app.deleteDatabase(databaseName)
        workoutOrdinal = 0
    }

    @After
    fun tearDown() {
        closePersistentRepository()
        app.deleteDatabase(databaseName)
        // Los repositorios singleton que tocan los commits no sobreviven al test.
        runCatching { CompetitionRepository.closeInstance() }
        runCatching { NutritionRepository.closeInstance() }
        Dispatchers.resetMain()
    }

    private fun closePersistentRepository() {
        // El repositorio es dueño de su BD en archivo: closeInstance() la cierra.
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    private suspend fun openPersistentRepository(): ProgramRepository {
        val repository = ProgramRepository.initForTestsWithDatabaseFile(app, databaseName)
        withTimeout(10_000) { repository.isReady.first { it } }
        return repository
    }

    private suspend fun reopenPersistentRepository(): ProgramRepository {
        closePersistentRepository()
        return openPersistentRepository()
    }

    private suspend fun <T> room(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private data class NativeCase(
        val label: String,
        val entryId: String,
        val kind: NativeKind,
        val goal: PlanGoalProfile,
        val reference: TrainingReference?,
        val equipment: Set<String>,
        val level: CatalogLevel = CatalogLevel.INTERMEDIATE,
        val daysPerWeek: Int = 3,
        val weekdays: List<Int> = listOf(1, 3, 5),
        val minutesPerSession: Int = 100,
        val cardio: CardioPreference? = null,
        val expectsDumbbells: Boolean = false,
        val bodyweightOnly: Boolean = false,
    )

    private fun nativeCases() = listOf(
        NativeCase(
            label = "strength-barbell",
            entryId = NativeKind.STRENGTH.entryId,
            kind = NativeKind.STRENGTH,
            goal = PlanGoalProfile.STRENGTH,
            reference = TrainingReference.POWERLIFTING,
            equipment = setOf("bodyweight", "barbell", "rack", "bench"),
        ),
        NativeCase(
            label = "muscle-dumbbells",
            entryId = NativeKind.MUSCLE.entryId,
            kind = NativeKind.MUSCLE,
            goal = PlanGoalProfile.MUSCLE,
            reference = TrainingReference.HYPERTROPHY,
            equipment = setOf("bodyweight", "dumbbells"),
            expectsDumbbells = true,
        ),
        NativeCase(
            label = "powerbuilding-barbell",
            entryId = NativeKind.POWERBUILDING.entryId,
            kind = NativeKind.POWERBUILDING,
            goal = PlanGoalProfile.STRENGTH_MUSCLE,
            reference = TrainingReference.POWERBUILDING,
            equipment = setOf("bodyweight", "barbell", "rack", "bench"),
        ),
        NativeCase(
            label = "athlete-bodyweight",
            entryId = NativeKind.COMPLETE_ATHLETE.entryId,
            kind = NativeKind.COMPLETE_ATHLETE,
            goal = PlanGoalProfile.COMPLETE_ATHLETE,
            reference = null,
            equipment = setOf("bodyweight"),
            cardio = CardioPreference(ModelCardioType.WALK, 10),
            bodyweightOnly = true,
        ),
        NativeCase(
            label = "muscle-bodyweight",
            entryId = NativeKind.MUSCLE.entryId,
            kind = NativeKind.MUSCLE,
            goal = PlanGoalProfile.MUSCLE,
            reference = TrainingReference.HYPERTROPHY,
            equipment = setOf("bodyweight"),
            bodyweightOnly = true,
        ),
    )

    private fun muscleE0SixDayCase(): NativeCase = nativeCases()
        .single { it.label == "muscle-bodyweight" }
        .copy(
            label = "muscle-e0-6d-30m-beginner",
            level = CatalogLevel.BEGINNER,
            daysPerWeek = 6,
            weekdays = (1..6).toList(),
            minutesPerSession = 30,
        )

    private fun athleteE0SixDayCase(): NativeCase = nativeCases()
        .single { it.label == "athlete-bodyweight" }
        .copy(
            label = "athlete-e0-6d-60m-beginner",
            level = CatalogLevel.BEGINNER,
            daysPerWeek = 6,
            weekdays = (1..6).toList(),
            minutesPerSession = 60,
            cardio = null,
        )

    private fun personalizer() = SimpleCyclePersonalizer(
        InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog).also {
            runBlocking { it.load() }
        },
    )

    /** Uses the real revision-gated evaluator and real onboarding personalizer as preview engine. */
    private suspend fun nativePreview(case: NativeCase, programId: String): PlanCandidateEvaluation.Ready {
        val entry = requireNotNull(PersonalizedPlanCatalog.find(case.entryId))
        val request = PlanCandidateRequest(
            inputKey = "$programId|${case.entryId}|${case.equipment.sorted().joinToString()}|" +
                "${case.level}|${case.daysPerWeek}|${case.weekdays.joinToString()}|${case.minutesPerSession}|" +
                (case.cardio?.let { "${it.type}:${it.minutes}" } ?: "default-cardio"),
            goalProfile = case.goal,
            level = case.level,
            focus = TrainingFocus.FULL_BODY,
            reference = case.reference,
            daysPerWeek = case.daysPerWeek,
            weekdays = case.weekdays.toSet(),
            minutesPerSession = case.minutesPerSession,
            effectiveEquipment = case.equipment,
            cardioMinutes = case.cardio?.minutes,
            requiresCardio = case.goal == PlanGoalProfile.COMPLETE_ATHLETE,
            planCatalogRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = EXERCISE_REVISION,
        )
        val snapshot = PlanCatalogSnapshot(
            entries = PersonalizedPlanCatalog.entries(),
            planRevision = PersonalizedPlanCatalog.REVISION,
            exerciseCatalogRevision = EXERCISE_REVISION,
        )
        val generator = OnboardingPlanGenerator(personalizer())
        val evaluation = PlanCandidateEvaluator.evaluate(
            request = request,
            snapshot = snapshot,
            entryId = entry.id,
            engine = PlanMaterializationPort { selected, _ ->
                val generated = generator.generate(
                    programId,
                    PersonalizerInput(
                        catalogEntryId = selected.id,
                        focus = TrainingFocus.FULL_BODY,
                        frequency = case.daysPerWeek,
                        weekdays = case.weekdays,
                        equipment = case.equipment,
                        level = case.level,
                        availableMinutes = case.minutesPerSession,
                        cardio = case.cardio,
                    ),
                )
                val program = generated.program ?: error(
                    "${case.label} no produjo preview: ${generated.report.reasonCode} ${generated.report.limitations}",
                )
                PlanMaterializationOutcome(
                    program = program,
                    recipe = selected.recipe ?: selected.template?.recipe,
                    report = generated.report,
                )
            },
        )
        assertTrue("${case.label}: se esperaba preview Ready, llegó $evaluation", evaluation is PlanCandidateEvaluation.Ready)
        return evaluation as PlanCandidateEvaluation.Ready
    }

    private fun weeksOf(program: Program): List<ProgramWeek> = program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }

    private fun weekOf(program: Program, weekId: String): ProgramWeek =
        weeksOf(program).first { it.id == weekId }

    private fun recipeWeek(recipe: TrainingPlanRecipe, week: ProgramWeek) =
        recipe.weeks.first { it.weekNumber == week.progressionIndex }

    private fun prescriptionSetCount(week: ProgramWeek): Int =
        week.sessions.sumOf { session -> session.allExercises().sumOf { it.sets.size } }

    /** Identidades persistidas de una sesión: sesión, partes, ejercicios, ocurrencias, series y calentamientos. */
    private fun identityOf(session: Session): List<String?> = buildList {
        add(session.id)
        session.parts.forEach { add(it.id) }
        session.allExercises().forEach { exercise ->
            add(exercise.id)
            add(exercise.occurrenceId)
            exercise.sets.forEach { add(it.id) }
            exercise.warmupSets.forEach { add(it.id) }
        }
    }

    private fun identityOf(week: ProgramWeek): List<String?> = week.sessions.flatMap { identityOf(it) }

    /** Bloques de cardio materializados (partes de cardio con sus ejercicios y detalles). */
    private fun cardioOf(week: ProgramWeek): List<Any?> = week.sessions.flatMap { session ->
        session.parts.filter { it.isCardioGroup }.map { part -> part to part.exercises.map { it.cardioDetails } }
    }

    /** Ejercicios SPEED con su prescripción completa (series, referencia y convención de carga). */
    private fun speedOf(week: ProgramWeek): List<Any?> = week.sessions.flatMap { session ->
        session.allExercises().filter { it.slotRole == SlotRole.SPEED }
            .map { Triple(it.sets, it.loadReference, it.loadQuantityConvention) }
    }

    /** Contenido de la prescripción de una sesión sin ids: lo que el atleta ve y ejecuta. */
    private fun contentOf(session: Session): List<Any?> = listOf(session.name, session.dayOfWeek) +
        session.allExercises().map { exercise ->
            listOf(
                exercise.catalogConfigurationId,
                exercise.slotRole,
                exercise.recipeDayId,
                exercise.recipeSlotId,
                exercise.sets.map { Triple(it.targetReps, it.targetPercentageRM, it.weight) },
                exercise.restTime,
                exercise.warmupSets.size,
                exercise.cardioDetails,
            )
        }

    /** Cambia la prescripción (descanso) del primer ejercicio de fuerza; el nombre solo no basta como edición. */
    private fun withLongerRestOnFirstStrengthExercise(session: Session): Session {
        var changed = false
        fun edit(exercise: com.example.kpkn.data.models.Exercise): com.example.kpkn.data.models.Exercise {
            if (changed || exercise.cardioDetails != null || exercise.sets.isEmpty()) return exercise
            changed = true
            return exercise.copy(restTime = (exercise.restTime ?: 90) + 30)
        }
        val next = session.copy(
            exercises = session.exercises.map(::edit),
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::edit)) },
        )
        check(changed) { "la sesión de prueba necesita un ejercicio de fuerza editable" }
        return next
    }

    /**
     * Edita una sesión con el EDITOR REAL (`SessionEditorViewModel.saveSession`): la marca
     * «Sesión personalizada» y el contenido viajan en la misma mutación de Room. Devuelve la
     * sesión tal como quedó persistida en el repositorio.
     */
    private suspend fun editThroughSessionEditor(
        programId: String,
        weekId: String,
        sessionId: String,
        edit: (Session) -> Session,
    ): Session {
        val repository = ProgramRepository.getInstance()
        val program = requireNotNull(repository.getProgramById(programId))
        val location = requireNotNull(ProgramHierarchyIndex(program).locateWeek(weekId))
        val editor = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = weekId,
            draftMacroIndex = location.macroIndex,
            draftMesoIndex = location.globalMesoIndex,
            draftDayOfWeek = null,
        )
        withTimeout(10_000) {
            while (editor.uiState.value.session == null) {
                editor.retryLoadSession()
                delay(50)
            }
        }
        editor.updateSession(transform = edit)
        val result = editor.saveSession()
        assertTrue("El editor real guarda la edición de $sessionId: ${result.message}", result.success)
        return weekOf(requireNotNull(repository.getProgramById(programId)), weekId).sessions.first { it.id == sessionId }
    }

    private fun configIds(week: ProgramWeek): List<String> = week.sessions
        .flatMap { it.allExercises() }
        .mapNotNull { it.catalogConfigurationId }

    private fun assertNativePreview(case: NativeCase, ready: PlanCandidateEvaluation.Ready) {
        val program = ready.preparedPlan
        val recipe = requireNotNull(program.sourceRecipe)
        assertEquals(case.kind.entryId, case.entryId)
        assertEquals(6, weeksOf(program).size)
        assertEquals(case.daysPerWeek, recipe.claimedDaysPerWeek)
        when (case.kind) {
            NativeKind.STRENGTH -> assertTrue(
                recipe.liftSlots.keys.containsAll(
                    setOf(
                        com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                        com.example.kpkn.data.protocols.LiftSlot.BENCH,
                        com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
                    ),
                ),
            )
            NativeKind.POWERBUILDING -> assertTrue(
                recipe.liftSlots.keys.containsAll(
                    setOf(
                        com.example.kpkn.data.protocols.LiftSlot.SQUAT,
                        com.example.kpkn.data.protocols.LiftSlot.BENCH,
                        com.example.kpkn.data.protocols.LiftSlot.DEADLIFT,
                    ),
                ),
            )
            NativeKind.MUSCLE -> assertTrue(recipe.weeks.first().days.flatMap { it.slots }.isNotEmpty())
            NativeKind.COMPLETE_ATHLETE -> {
                assertTrue("Atleta conserva P en la receta", recipe.weeks.first().days.flatMap { it.slots }
                    .any { it.role == SlotRole.SPEED })
                assertTrue("Atleta materializa cardio estructurado", weeksOf(program).flatMap { it.sessions }
                    .flatMap { it.parts }.any { it.isCardioGroup && it.exercises.any { exercise -> exercise.cardioDetails != null } })
                assertTrue("el evaluador ve las cuatro capacidades", ready.coverage.completeAthlete)
            }
        }
        if (case.expectsDumbbells) {
            val equipment = CatalogCompositionTestSupport.catalog.families
                .flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
            assertTrue(
                "El plan nativo con mancuernas usa al menos una configuración de mancuernas: ${configIds(weeksOf(program).first())}",
                configIds(weeksOf(program).first()).any { equipment[it]?.profile?.equipmentId == "dumbbells" },
            )
        }
        if (case.bodyweightOnly && case.kind == NativeKind.MUSCLE) {
            val equipment = CatalogCompositionTestSupport.catalog.families
                .flatMap { it.definitions }.flatMap { it.configurations }.associateBy { it.id }
            assertTrue("El plan nativo solo cuerpo no inventa material", configIds(weeksOf(program).first()).isNotEmpty())
            assertTrue(
                "Toda configuración del plan nativo solo cuerpo es corporal",
                configIds(weeksOf(program).first()).all { equipment[it]?.profile?.equipmentId == "bodyweight" },
            )
        }
        val occurrenceIds = weeksOf(program).flatMap { it.sessions }
            .flatMap { it.allExercises() }.mapNotNull { it.occurrenceId }
        assertEquals("Las ocurrencias del preview son únicas", occurrenceIds.size, occurrenceIds.distinct().size)
    }

    private fun assertSixDayE0Plan(case: NativeCase, ready: PlanCandidateEvaluation.Ready) {
        assertEquals(CatalogLevel.BEGINNER, case.level)
        assertEquals(6, case.daysPerWeek)
        assertEquals((1..6).toList(), case.weekdays)
        assertEquals("principiante", ready.preparedPlan.sourceRecipe?.claimedLevel)
        val weeks = weeksOf(ready.preparedPlan)
        assertEquals("E0 materializa seis semanas", 6, weeks.size)
        weeks.forEach { week ->
            assertEquals("${week.name}: seis sesiones reales", 6, week.sessions.size)
            assertEquals(
                "${week.name}: conserva los seis días elegidos",
                case.weekdays.toSet(),
                week.sessions.mapNotNull { it.dayOfWeek }.toSet(),
            )
            assertTrue(
                "${week.name}: todas las sesiones se pueden completar",
                week.sessions.all { it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED },
            )
            assertEquals(6, week.sessions.map { it.id }.distinct().size)
        }
        assertOnlyBodyweightConfigurations(ready.preparedPlan, case.label)
    }

    private fun assertOnlyBodyweightConfigurations(program: Program, label: String) {
        val approvedConfigurations = CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .filter {
                it.evidence.reviewStatus ==
                    com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2.APPROVED
            }
            .associateBy { it.id }
        val allExercises = weeksOf(program).flatMap { week ->
            week.sessions.flatMap { session -> session.allExercises() }
        }
        val resistanceExercises = allExercises.filter { it.cardioDetails == null }
        assertTrue("$label materializa ejercicios de resistencia", resistanceExercises.isNotEmpty())
        resistanceExercises.forEach { exercise ->
            val id = exercise.catalogConfigurationId
            assertTrue("$label exige configuración para ${exercise.name}", !id.isNullOrBlank())
            val configuration = approvedConfigurations[requireNotNull(id)]
            assertTrue("$label exige una configuración aprobada para $id", configuration != null)
            assertEquals(
                "$label no inventa equipo para E0 ($id)",
                "bodyweight",
                requireNotNull(configuration).profile.equipmentId,
            )
        }

        val cardioExercises = allExercises.filter { it.cardioDetails != null }
        val cardioPartExercises = weeksOf(program).flatMap { week ->
            week.sessions.flatMap { session -> session.parts.filter { it.isCardioGroup }.flatMap { it.exercises } }
        }
        assertTrue("$label no usa una parte cardio sin detalles reales", cardioPartExercises.all { it.cardioDetails != null })
        val structuredCardioExercises = cardioPartExercises.filter { it.cardioDetails != null }
        assertEquals(
            "$label representa cada ejercicio cardio en una parte estructurada",
            cardioExercises.map { it.id }.toSet(),
            structuredCardioExercises.map { it.id }.toSet(),
        )
        if (label.contains("athlete", ignoreCase = true)) {
            assertTrue("$label materializa cardio estructurado", cardioExercises.isNotEmpty())
        }
        cardioExercises.forEach { exercise ->
            val cardio = requireNotNull(exercise.cardioDetails)
            assertTrue(
                "$label usa una modalidad exterior compatible con E0 (${cardio.type})",
                cardio.type in setOf(ModelCardioType.WALK, ModelCardioType.RUN_OUTDOOR, ModelCardioType.BIKE_OUTDOOR),
            )
            assertTrue(
                "$label cardio usa una duración ofertada (${cardio.targetDurationSeconds})",
                cardio.targetDurationSeconds?.let { it in setOf(10, 15, 20, 30).map { minutes -> minutes * 60 } } == true,
            )
            assertNull("$label cardio no declara aparato de catálogo", exercise.catalogConfigurationId)
            assertNull("$label cardio no declara una definición de aparato", exercise.catalogDefinitionId)
        }
    }

    private fun assertBeginnerAthleteSixDayComponents(ready: PlanCandidateEvaluation.Ready) {
        assertTrue("Atleta E0 conserva fuerza", ready.coverage.hasStrength)
        assertTrue("Atleta E0 conserva hipertrofia", ready.coverage.hasHypertrophy)
        assertTrue("Atleta E0 conserva potencia", ready.coverage.hasPower)
        assertTrue("Atleta E0 conserva cardio", ready.coverage.hasCardio)

        val recipe = requireNotNull(ready.preparedPlan.sourceRecipe)
        val week1Slots = recipe.weeks.first().days.flatMap { it.slots }
        assertTrue("Atleta E0 tiene slot P operativo", week1Slots.any { it.role == SlotRole.SPEED })
        val resistanceRirs = week1Slots.filter { it.role != SlotRole.SPEED }
            .flatMap { it.sets }.filterNot { it.isWarmup }.map { it.rir }
        assertTrue("Atleta E0 prescribe series de resistencia", resistanceRirs.isNotEmpty())
        assertTrue("Atleta principiante conserva RIR 3", resistanceRirs.all { it == 3 })
        val speedRirs = week1Slots.filter { it.role == SlotRole.SPEED }
            .flatMap { it.sets }.filterNot { it.isWarmup }.map { it.rir }
        assertTrue("P conserva series efectivas con RIR 5", speedRirs.isNotEmpty() && speedRirs.all { it == 5 })

        val week1Exercises = weeksOf(ready.preparedPlan).first().sessions.flatMap { it.allExercises() }
        val materializedResistanceRirs = week1Exercises.filter {
            it.cardioDetails == null && it.slotRole != SlotRole.SPEED
        }.flatMap { it.sets }.map { it.targetRIR }
        assertTrue("El RIR de resistencia se materializa en sesiones", materializedResistanceRirs.isNotEmpty())
        assertTrue("Las sesiones de Atleta principiante materializan RIR 3", materializedResistanceRirs.all { it == 3 })
        val materializedSpeedRirs = week1Exercises.filter { it.slotRole == SlotRole.SPEED }
            .flatMap { it.sets }.map { it.targetRIR }
        assertTrue("Las sesiones materializan P con RIR 5", materializedSpeedRirs.isNotEmpty() && materializedSpeedRirs.all { it == 5 })

        val cardio = weeksOf(ready.preparedPlan).flatMap { week ->
            week.sessions.flatMap { it.parts }
        }.filter { it.isCardioGroup }.flatMap { it.exercises }.mapNotNull { it.cardioDetails }
        assertTrue("Atleta E0 materializa cardio estructurado", cardio.isNotEmpty())
        assertTrue(
            "Atleta E0 de 60 min materializa el bloque WALK dedicado de 20 min",
            cardio.any { it.type == ModelCardioType.WALK && it.targetDurationSeconds == 20 * 60 },
        )
    }

    private fun requestFor(program: Program, draftId: String, settings: Settings, activate: Boolean = false) =
        SetupCommitRequest(
            commitId = program.id,
            draftId = draftId,
            settings = settings,
            program = program,
            nutritionPlan = null,
            activateProgram = activate,
            activateNutrition = false,
        )

    private suspend fun saveDraft(db: KpknDatabase, draftId: String) {
        SetupDraftRepository(db).save(draftId, "{\"q5\":true}", 1, PersonalizedPlanCatalog.REVISION)
    }

    private suspend fun commitReopenAndCompleteWeekOne(program: Program, label: String, draftId: String) {
        var repository = openPersistentRepository()
        var db = repository.databaseForTests()
        saveDraft(db, draftId)
        val request = requestFor(
            program,
            draftId,
            Settings(username = "Q5 $label", onboardingCompleted = true),
            activate = true,
        )
        assertEquals(SetupCommitResult(program.id, program.id, null, emptyList()),
            SetupCommitCoordinator(db, repository).commit(request))
        assertEquals("$label se activa al confirmar", program.id, repository.activeProgramState.value?.programId)

        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        useCommittedProgramWeekOne(repository, db, program, label, draftId)
    }

    /** Exercise an already committed fixture after a real repository/Room reopen. */
    private suspend fun useCommittedProgramWeekOne(
        repository: ProgramRepository,
        db: KpknDatabase,
        preview: Program,
        label: String,
        draftId: String? = null,
    ) {
        val saved = room { db.programDao().getById(preview.id)?.toProgram() }
        assertEquals("$label: preview == programa persistido tras Room reopen", preview, saved)
        assertEquals(preview.id, room { db.setupCommitReceiptDao().get(preview.id)?.programId })
        draftId?.let { id ->
            assertEquals(id, room { db.setupCommitReceiptDao().get(preview.id)?.draftId })
            assertNull(room { db.setupDraftDao().getDraft(id) })
        }

        repository.startProgram(preview.id)
        assertEquals(preview.id, repository.activeProgramState.value?.programId)
        val activeProgram = requireNotNull(repository.getProgramById(preview.id))
        val orderedWeeks = weeksOf(activeProgram)
        assertTrue("$label necesita W1 y W2 ejecutables", orderedWeeks.size >= 2)
        val week1 = orderedWeeks[0]
        val week2 = orderedWeeks[1]
        val week1Before = weekOf(activeProgram, week1.id)
        val requiredSessions = week1Before.sessions.filter {
            it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED
        }
        assertTrue("$label W1 tiene sesiones requeridas", requiredSessions.isNotEmpty())
        val cycleNumber = 1
        val expectedInstanceId = weekInstanceIdFor(activeProgram, cycleNumber, week1.id)
        assertProgramCursor(repository, activeProgram, cycleNumber, week1.id)

        val logs = recordWeek(repository, preview.id, week1.id, cycleNumber, requiredOnly = true)
        assertEquals("$label registra cada sesión requerida de W1", requiredSessions.size, logs.size)
        assertEquals(requiredSessions.map { it.id }.toSet(), logs.map { it.sessionId }.toSet())
        assertTrue(logs.all {
            it.cycleNumber == cycleNumber && it.weekId == week1.id && it.weekInstanceId == expectedInstanceId
        })
        val runId = requireNotNull(repository.getProgramById(preview.id)?.runState?.runId)
        assertTrue(runId.isNotBlank())
        assertTrue(logs.all { it.programRunId == runId })

        assertProgramCursor(repository, activeProgram, cycleNumber, week2.id)
        val advanced = requireNotNull(repository.getProgramById(preview.id))
        assertEquals(week2.id, advanced.runState?.weekId)
        assertEquals(week1Before.sessions, weekOf(advanced, week1.id).sessions)
        assertEquals(week1Before.sessions.map { it.id }, weekOf(advanced, week1.id).sessions.map { it.id })
        assertEquals(logs.map { it.id }.size, logs.map { it.id }.distinct().size)

        val persistedLogs = room {
            db.workoutLogDao().getByProgram(preview.id).mapNotNull { it.toWorkoutLog() }
        }.filter { it.weekInstanceId == expectedInstanceId }
        assertEquals("$label Room conserva los logs de la instancia entrenada", requiredSessions.size, persistedLogs.size)
        assertEquals(logs.map { it.id }.toSet(), persistedLogs.map { it.id }.toSet())
        assertTrue(persistedLogs.all {
            it.cycleNumber == cycleNumber && it.weekId == week1.id &&
                it.weekInstanceId == expectedInstanceId && it.programRunId == runId
        })

        val week2BeforeRebuild = weekOf(advanced, week2.id)
        val evidence = repository.executedTrainingEvidence(advanced)
        val rebuilt = PlanMaterializer.rematerializeWeek(
            program = advanced,
            weekId = week2.id,
            recipe = requireNotNull(advanced.sourceRecipe),
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SequenceIds("q5-${preview.id}-w2"),
            weekOccurrence = 2,
            executedSessionIds = evidence.sessionIds,
        )
        repository.updateProgramNow(rebuilt)
        val afterRebuild = requireNotNull(repository.getProgramById(preview.id))
        assertEquals("$label rebuild selectivo de W2 no altera la semana entrenada", week1Before.sessions,
            weekOf(afterRebuild, week1.id).sessions)
        // Reconstruir W2 sin cambios de receta la deja IDÉNTICA, no solo con los mismos ids de
        // sesión: ids de partes/ejercicios/ocurrencias/series/calentamientos, cardio y SPEED.
        val week2After = weekOf(afterRebuild, week2.id)
        assertEquals("$label: ids de sesión de W2", week2BeforeRebuild.sessions.map { it.id }, week2After.sessions.map { it.id })
        assertEquals("$label: identidad completa (partes, ejercicios, ocurrencias, series, calentamientos) de W2",
            identityOf(week2BeforeRebuild), identityOf(week2After))
        val occurrencesAfter = week2After.sessions.flatMap { it.allExercises() }.mapNotNull { it.occurrenceId }
        assertEquals("$label: ids de ocurrencia únicos tras reconstruir", occurrencesAfter.distinct().size, occurrencesAfter.size)
        assertEquals("$label: ids de ocurrencia intactos",
            week2BeforeRebuild.sessions.flatMap { it.allExercises() }.mapNotNull { it.occurrenceId }, occurrencesAfter)
        assertEquals("$label: el cardio de W2 no cambia", cardioOf(week2BeforeRebuild), cardioOf(week2After))
        assertEquals("$label: el SPEED de W2 no cambia", speedOf(week2BeforeRebuild), speedOf(week2After))
        assertEquals("$label: igualdad completa de W2 antes/después de reconstruir", week2BeforeRebuild, week2After)
        assertProgramCursor(repository, afterRebuild, cycleNumber, week2.id)
        assertEquals(logs.map { it.id }.toSet(), repository.history.value.filter { it.programId == preview.id }.map { it.id }.toSet())
        val persistedAfterRebuild = room {
            db.workoutLogDao().getByProgram(preview.id).mapNotNull { it.toWorkoutLog() }
        }
        assertEquals(persistedLogs.map { it.id }.toSet(), persistedAfterRebuild.map { it.id }.toSet())
        assertEquals(afterRebuild, room { db.programDao().getById(preview.id)?.toProgram() })
    }

    /**
     * Los cuatro perfiles propios KPKN, también con material distinto (mancuernas / solo cuerpo).
     * OJO: «mancuernas» y «cuerpo» son GENERACIÓN nativa con otro equipamiento
     * (`OnboardingPlanGenerator`), NO la adaptación de los planes de autor PHUL/PHAT
     * (`PlanAdaptationResolver`): esa cobertura end-to-end vive en las pruebas de planes de
     * autor adaptados, no aquí.
     */
    @Test
    fun fourNativeProfilesIncludingDumbbellAndBodyweightEquipmentCommitRoundTripAndUse() = runBlocking {
        val repository = openPersistentRepository()
        val db = repository.databaseForTests()
        val coordinator = SetupCommitCoordinator(db, repository)
        val cases = nativeCases()
        assertEquals(
            "Los cuatro perfiles propios aparecen como casos con preview ejecutable",
            NativeKind.entries.toSet(),
            cases.map { it.kind }.toSet(),
        )
        val previews = cases.map { case ->
            case to nativePreview(case, "q5-native-${case.label}").also { ready ->
                assertNativePreview(case, ready)
                if (case.bodyweightOnly) assertOnlyBodyweightConfigurations(ready.preparedPlan, case.label)
            }.preparedPlan
        }
        val commits = previews.map { (case, preview) ->
            val draftId = "q5-draft-${case.label}"
            saveDraft(db, draftId)
            val request = requestFor(
                preview,
                draftId,
                Settings(username = "Q5 ${case.label}", onboardingCompleted = true),
                activate = true,
            )
            val result = coordinator.commit(request)
            assertEquals(SetupCommitResult(preview.id, preview.id, null, emptyList()), result)
            assertEquals("${case.label} se activa al confirmar", preview.id, repository.activeProgramState.value?.programId)
            case to (preview to request)
        }

        val reopened = reopenPersistentRepository()
        val reopenedDb = reopened.databaseForTests()
        val replayCoordinator = SetupCommitCoordinator(reopenedDb, reopened)
        for ((case, pair) in commits) {
            val (preview, request) = pair
            val saved = room { reopenedDb.programDao().getById(preview.id)?.toProgram() }
            assertEquals("${case.label}: preview == programa JSON de Room tras cierre real", preview, saved)
            assertEquals(
                SetupCommitResult(preview.id, preview.id, null, emptyList()),
                room { replayCoordinator.commit(request) },
            )
            assertEquals(preview.id, room { reopenedDb.setupCommitReceiptDao().get(preview.id)?.programId })
            assertEquals(request.draftId, room { reopenedDb.setupCommitReceiptDao().get(preview.id)?.draftId })
            assertNull("${case.label}: el borrador se consume con el receipt", room {
                reopenedDb.setupDraftDao().getDraft(requireNotNull(request.draftId))
            })
        }
        assertEquals(previews.size, room { reopenedDb.programDao().getAll().size })
        assertEquals(previews.size, room { reopenedDb.setupCommitReceiptDao().getAll().size })
        assertEquals(
            "El replay antiguo no pisa la configuración más nueva",
            "Q5 ${cases.last().label}",
            room { reopenedDb.settingsDao().get()?.toSettings()?.username },
        )
        commits.forEach { (case, pair) ->
            val (preview, request) = pair
            useCommittedProgramWeekOne(reopened, reopenedDb, preview, case.label, request.draftId)
        }
    }

    @Test
    fun muscleE0BeginnerSixDaysThirtyMinutesCommitsReopensAndCompletesWeekOne() = runBlocking {
        val case = muscleE0SixDayCase()
        assertEquals(30, case.minutesPerSession)
        val ready = nativePreview(case, "q5-muscle-e0-6d-30m")
        assertNativePreview(case, ready)
        assertSixDayE0Plan(case, ready)
        commitReopenAndCompleteWeekOne(ready.preparedPlan, case.label, "q5-draft-${case.label}")
    }

    @Test
    fun athleteE0BeginnerSixDaysSixtyMinutesCommitsReopensAndCompletesWeekOne() = runBlocking {
        val case = athleteE0SixDayCase()
        assertEquals(60, case.minutesPerSession)
        val ready = nativePreview(case, "q5-athlete-e0-6d-60m")
        assertNativePreview(case, ready)
        assertSixDayE0Plan(case, ready)
        assertBeginnerAthleteSixDayComponents(ready)
        commitReopenAndCompleteWeekOne(ready.preparedPlan, case.label, "q5-draft-${case.label}")
    }

    private class SequenceIds(private val prefix: String) : IdProvider {
        private var next = 0
        override fun newId(): String = "${prefix}_${++next}"
    }

    private fun materializeAuthored(id: String, name: String, recipe: TrainingPlanRecipe): Program =
        PlanMaterializer.materialize(
            program = Program(id = id, name = name),
            recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SequenceIds(id),
            strict = false,
        )

    private fun assertAuthoredOracle(program: Program, recipe: TrainingPlanRecipe) {
        val weeks = weeksOf(program)
        assertEquals(recipe.id, program.sourceRecipe?.id)
        assertEquals(recipe.weeks.size, weeks.size)
        if (recipe.id == AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID) {
            val first = weeks.first()
            assertEquals(setOf(1, 2, 4, 5), first.sessions.mapNotNull { it.dayOfWeek }.toSet())
            assertEquals(listOf(18, 16, 21, 18), first.sessions.map { s -> s.allExercises().sumOf { it.sets.size } })
            assertTrue("PHUL original sin porcentajes ni SPEED inventado", first.sessions.flatMap { it.allExercises() }
                .all { exercise -> exercise.sets.all { it.targetPercentageRM == null } && exercise.slotRole != SlotRole.SPEED })
        } else {
            assertEquals(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID, recipe.id)
            val first = weeks.first()
            assertEquals(setOf(1, 2, 4, 5, 6), first.sessions.mapNotNull { it.dayOfWeek }.toSet())
            assertEquals(listOf(21, 17, 24, 28, 28), first.sessions.map { s -> s.allExercises().sumOf { it.sets.size } })
            val speed = first.sessions.flatMap { it.allExercises() }.filter { it.slotRole == SlotRole.SPEED }
            assertEquals(3, speed.size)
            speed.forEach { exercise ->
                assertEquals(6, exercise.sets.size)
                exercise.sets.forEach { set ->
                    assertEquals(3, set.targetReps)
                    assertEquals(65.0, set.targetPercentageRM ?: Double.NaN, 0.0001)
                    assertNull(set.weight)
                }
            }
        }
    }

    @Test
    fun authoredPhulAndPhatOriginalsKeepExactRecipesAndPrescriptionsThroughRoomReopenAndUse() = runBlocking {
        val authored = listOf(
            Triple("q5-phul-original", "PHUL original", AuthoredPhulPhatRecipes.phulOriginal),
            Triple("q5-phat-original", "PHAT original", AuthoredPhulPhatRecipes.phatOriginal),
        ).map { (id, name, recipe) -> materializeAuthored(id, name, recipe).also { assertAuthoredOracle(it, recipe) } }
        val repository = openPersistentRepository()
        val db = repository.databaseForTests()
        val coordinator = SetupCommitCoordinator(db, repository)
        val requests = authored.map { program ->
            val draftId = "draft-${program.id}"
            saveDraft(db, draftId)
            requestFor(program, draftId, Settings(username = program.name, onboardingCompleted = true), activate = true)
        }
        requests.forEach { request ->
            assertEquals(
                SetupCommitResult(request.commitId, request.program?.id, null, emptyList()),
                coordinator.commit(request),
            )
            assertEquals(request.program?.id, repository.activeProgramState.value?.programId)
        }

        val reopened = reopenPersistentRepository()
        val reopenedDb = reopened.databaseForTests()
        val replayCoordinator = SetupCommitCoordinator(reopenedDb, reopened)
        authored.zip(requests).forEach { (preview, request) ->
            val saved = room { reopenedDb.programDao().getById(preview.id)?.toProgram() }
            assertEquals("${preview.name}: identidad, partes, cargas y cada ID sobreviven al reopen", preview, saved)
            assertAuthoredOracle(requireNotNull(saved), requireNotNull(preview.sourceRecipe))
            assertEquals(preview.id, room { reopenedDb.setupCommitReceiptDao().get(preview.id)?.programId })
            assertEquals(
                SetupCommitResult(preview.id, preview.id, null, emptyList()),
                room { replayCoordinator.commit(request) },
            )
            assertNull(room { reopenedDb.setupDraftDao().getDraft(requireNotNull(request.draftId)) })
        }
        assertEquals(2, room { reopenedDb.setupCommitReceiptDao().getAll().size })
        authored.zip(requests).forEach { (preview, request) ->
            useCommittedProgramWeekOne(
                reopened,
                reopenedDb,
                preview,
                preview.name,
                request.draftId,
            )
            val afterUse = room { reopenedDb.programDao().getById(preview.id)?.toProgram() }
            assertAuthoredOracle(requireNotNull(afterUse), requireNotNull(preview.sourceRecipe))
        }
    }

    private fun replaceSession(program: Program, weekId: String, replacement: Session): Program =
        program.copy(macrocycles = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        if (week.id != weekId) week else week.copy(
                            sessions = week.sessions.map { if (it.id == replacement.id) replacement else it },
                        )
                    })
                })
            })
        })

    private suspend fun recordWeek(
        repository: ProgramRepository,
        programId: String,
        templateWeekId: String,
        cycleNumber: Int,
        sessionLimit: Int? = null,
        requiredOnly: Boolean = false,
    ): List<WorkoutLog> {
        val initialProgram = requireNotNull(repository.getProgramById(programId))
        val week = weekOf(initialProgram, templateWeekId)
        val weekInstanceId = weekInstanceIdFor(initialProgram, cycleNumber, templateWeekId)
        val sessions = if (requiredOnly) {
            week.sessions.filter { it.requirement == com.example.kpkn.data.models.SessionRequirement.REQUIRED }
        } else {
            week.sessions
        }
        return sessions.take(sessionLimit ?: sessions.size).map { session ->
            val latest = requireNotNull(repository.getProgramById(programId))
            val currentSession = weekOf(latest, templateWeekId).sessions.first { it.id == session.id }
            workoutOrdinal += 1
            val started = repository.startWorkout(
                OngoingWorkoutState(
                    programId = programId,
                    session = currentSession,
                    startTime = 1_800_000_000_000L + workoutOrdinal,
                    weekId = weekInstanceId,
                ),
            )
            assertTrue("${currentSession.id} debe iniciar como sesión real", started is StartWorkoutResult.Started)
            val exercise = currentSession.allExercises().firstOrNull { it.cardioDetails == null && it.sets.isNotEmpty() }
            val completed = exercise?.let { item ->
                val set = item.sets.first()
                listOf(
                    CompletedExercise(
                        exerciseId = item.id,
                        exerciseName = item.name,
                        sets = listOf(
                            CompletedSet(
                                id = set.id,
                                weight = set.weight ?: 0.0,
                                reps = set.targetReps ?: 8,
                                rir = set.targetRIR,
                            ),
                        ),
                    ),
                )
            }.orEmpty()
            val runId = latest.runState?.runId ?: repository.activeProgramState.value?.programRunId
            val log = WorkoutLog(
                id = "q5-log-${programId}-c${cycleNumber}-${templateWeekId}-${currentSession.id}",
                programId = programId,
                sessionId = currentSession.id,
                sessionName = currentSession.name,
                date = "2026-09-29T10:${(workoutOrdinal % 60).toString().padStart(2, '0')}:00Z",
                durationMinutes = currentSession.targetDurationMinutes ?: 45,
                completedExercises = completed,
                weekId = templateWeekId,
                weekInstanceId = weekInstanceId,
                cycleNumber = cycleNumber,
                programRunId = runId,
            )
            repository.finalizeWorkout(log)
            log
        }
    }

    private fun assertCursor(repository: ProgramRepository, cycle: Int, weekId: String) {
        val expected = ProgramProgressEngine.instanceIdFor(cycle, weekId)
        val active = requireNotNull(repository.activeProgramState.value)
        assertEquals(expected, active.currentWeekInstanceId ?: active.currentWeekId)
        assertEquals(cycle, active.currentCycleNumber)
    }

    private fun usesCyclicWeekInstances(program: Program): Boolean =
        program.structure == ProgramStructure.SIMPLE && program.simpleProgramKind == SimpleProgramKind.CYCLIC

    private fun usesNativeWeekInstances(program: Program): Boolean =
        program.structure == ProgramStructure.COMPLEX && program.sourceRecipe?.nativeProgression != null

    private fun weekInstanceIdFor(program: Program, cycle: Int, weekId: String): String =
        when {
            usesCyclicWeekInstances(program) || usesNativeWeekInstances(program) ->
                ProgramProgressEngine.instanceIdFor(cycle, weekId)
            else -> weekId
        }

    private fun assertProgramCursor(repository: ProgramRepository, program: Program, cycle: Int, weekId: String) {
        val active = requireNotNull(repository.activeProgramState.value)
        assertEquals(weekInstanceIdFor(program, cycle, weekId), active.currentWeekInstanceId ?: active.currentWeekId)
        if (usesCyclicWeekInstances(program)) {
            assertEquals(cycle, active.currentCycleNumber)
        } else {
            assertEquals(cycle, repository.getProgramById(program.id)?.runState?.cycleNumber)
        }
    }

    @Test
    fun athleteReceiptSurvivesReopenThenWorkoutAcceptanceManualRestoreAndCycleAreDurable() = runBlocking {
        val athleteCase = nativeCases().single { it.kind == NativeKind.COMPLETE_ATHLETE }
        val ready = nativePreview(athleteCase, "q5-athlete-workout")
        val preview = ready.preparedPlan
        val recipe = requireNotNull(preview.sourceRecipe)
        assertTrue("Atleta requiere F/H/P/cardio", ready.coverage.completeAthlete)
        val recipeW1 = recipe.weeks.first()
        assertTrue("P es slot operativo", recipeW1.days.flatMap { it.slots }.any { it.role == SlotRole.SPEED })
        val cardioDetails = weeksOf(preview).flatMap { it.sessions }
            .flatMap { it.parts }.filter { it.isCardioGroup }
            .flatMap { it.exercises }.mapNotNull { it.cardioDetails }
        assertTrue("cardio real en parts", cardioDetails.isNotEmpty())
        assertTrue(
            "Cardio conserva WALK y los 10 minutos pedidos",
            cardioDetails.any { it.type == ModelCardioType.WALK && it.targetDurationSeconds == 10 * 60 },
        )
        val week1WorkRirs = recipeW1.days.flatMap { it.slots }
            .filter { it.role != SlotRole.SPEED }.flatMap { it.sets }
            .filterNot { it.isWarmup }.map { it.rir }
        assertTrue("Atleta tiene series efectivas en semana 1", week1WorkRirs.isNotEmpty())
        assertTrue("RIR propio del atleta en semana 1", week1WorkRirs.all { it == 3 })
        val speedWorkRirs = recipeW1.days.flatMap { it.slots }
            .filter { it.role == SlotRole.SPEED }.flatMap { it.sets }
            .filterNot { it.isWarmup }.map { it.rir }
        assertTrue("P tiene series efectivas", speedWorkRirs.isNotEmpty())
        assertTrue("P conserva RIR 5", speedWorkRirs.all { it == 5 })
        val week2WorkRirs = recipe.weeks[1].days.flatMap { it.slots }
            .filter { it.role != SlotRole.SPEED }.flatMap { it.sets }
            .filterNot { it.isWarmup }.map { it.rir }
        assertTrue("Atleta tiene series efectivas en semana 2", week2WorkRirs.isNotEmpty())
        assertTrue("RIR propio del atleta en semana 2", week2WorkRirs.all { it == 2 })

        var repository = openPersistentRepository()
        var db = repository.databaseForTests()
        val coordinator = SetupCommitCoordinator(db, repository)
        val draftId = "q5-draft-athlete"
        saveDraft(db, draftId)
        val request = requestFor(
            preview,
            draftId,
            Settings(username = "Atleta Q5", onboardingCompleted = true),
            activate = true,
        )
        assertEquals(SetupCommitResult(preview.id, preview.id, null, emptyList()), coordinator.commit(request))

        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        val saved = room { db.programDao().getById(preview.id)?.toProgram() }
        assertEquals("Preview Atleta semánticamente idéntico tras proceso/Room reopen", preview, saved)
        assertEquals(preview.id, room { db.setupCommitReceiptDao().get(preview.id)?.programId })
        assertNull(room { db.setupDraftDao().getDraft(draftId) })

        repository.startProgram(preview.id)
        val orderedWeeks = weeksOf(requireNotNull(repository.getProgramById(preview.id)))
        val week1 = orderedWeeks.first()
        val week2 = orderedWeeks[1]
        val week3 = orderedWeeks[2]
        val week4 = orderedWeeks[3]
        assertCursor(repository, 1, week1.id)
        val trainedWeekBefore = weekOf(requireNotNull(repository.getProgramById(preview.id)), week1.id)
        val week1Logs = recordWeek(repository, preview.id, week1.id, cycleNumber = 1)
        assertCursor(repository, 1, week2.id)
        assertEquals("avance real tras completar todas las sesiones de W1", week2.id,
            repository.getProgramById(preview.id)?.runState?.weekId)
        assertEquals(trainedWeekBefore.sessions, weekOf(requireNotNull(repository.getProgramById(preview.id)), week1.id).sessions)
        assertTrue(week1Logs.all { it.completedExercises.isNotEmpty() })
        assertTrue(week1Logs.all { log -> repository.history.value.any { it.id == log.id } })

        val beforeAcceptance = requireNotNull(repository.getProgramById(preview.id))
        val futureBefore = weekOf(beforeAcceptance, week2.id)
        val proposal = AutoregulationProposal(
            kind = AutoregulationProposalKind.SCALE_WEEK_VOLUME,
            volumeFactor = 0.5,
            explanation = "Q5 acceptance: reducir volumen de la semana futura",
        )
        assertTrue(repository.mutateProgramNow(preview.id) { current ->
            current.copy(
                autoregulationMode = AutoregulationMode.PROPOSE,
                runState = requireNotNull(current.runState).copy(
                    pendingAction = PendingProgramAction(
                        type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                        message = "Reducir volumen futuro",
                        proposals = listOf(proposal),
                        targetWeekId = week2.id,
                    ),
                ),
            )
        })
        assertTrue("la propuesta real se acepta por ProgramRepository", repository.resolvePendingAutoregulationNow(preview.id, accept = true))
        val accepted = requireNotNull(repository.getProgramById(preview.id))
        val futureAfter = weekOf(accepted, week2.id)
        assertNotEquals("la receta futura cambia después de aceptar", futureBefore.sessions, futureAfter.sessions)
        assertTrue("la prescripción futura pierde series, no solo cambia el mensaje",
            prescriptionSetCount(futureAfter) < prescriptionSetCount(futureBefore))
        assertEquals("la semana ya entrenada queda intacta", trainedWeekBefore.sessions,
            weekOf(accepted, week1.id).sessions)
        assertNull(accepted.runState?.pendingAction)
        assertTrue(accepted.runState?.autoregulationAudit.orEmpty().any {
            it.resolution == PendingActionResolutionStatus.APPLIED && it.resolutionReason.isNotBlank()
        })
        assertTrue(accepted.effectiveWeekRecipes.any { it.weekOccurrence == 2 && it.cycleNumber == 1 })

        val acceptedW2 = weekOf(accepted, week2.id)
        val target = acceptedW2.sessions.first()
        val recipeDay = requireNotNull(target.allExercises().firstOrNull()?.recipeDayId)
        val w3Target = weekOf(accepted, week3.id).sessions.first()
        // Ediciones REALES con el editor de sesión (SessionEditorViewModel.saveSession): la marca
        // «Sesión personalizada» y el contenido viajan en la misma mutación (§14.5/AC-G2); el
        // override no se construye a mano.
        val editedTarget = editThroughSessionEditor(preview.id, week2.id, target.id) { session ->
            withLongerRestOnFirstStrengthExercise(session)
                .copy(name = "Q5 edición manual W2", description = "Contenido editado")
        }
        val editedOther = editThroughSessionEditor(preview.id, week3.id, w3Target.id) { session ->
            session.copy(name = "Q5 edición manual W3", description = "No restaurar esta")
        }
        assertNotEquals("la edición cambia la prescripción, no solo el nombre", contentOf(target), contentOf(editedTarget))
        val marked = requireNotNull(repository.getProgramById(preview.id))
        val targetOverride = marked.manualSessionOverrides.single { it.sessionId == target.id }
        assertEquals(ManualOverrideScope.SESSION, targetOverride.scope)
        assertEquals(week2.id, targetOverride.weekId)
        assertEquals(2, targetOverride.weekOccurrence)
        assertEquals(recipeDay, targetOverride.recipeDayId)
        assertEquals(setOf(target.id, w3Target.id), marked.manualSessionOverrides.map { it.sessionId }.toSet())
        val trainingEvidence = repository.executedTrainingEvidence(marked)
        val rebuilt = PlanMaterializer.rematerializeWeek(
            program = marked,
            weekId = week2.id,
            recipe = requireNotNull(marked.sourceRecipe),
            metadata = CatalogCompositionTestSupport.metadata,
            idProvider = SequenceIds("q5-rebuild-w2"),
            weekOccurrence = 2,
            executedSessionIds = trainingEvidence.sessionIds,
        )
        repository.updateProgramNow(rebuilt)
        val afterRebuild = requireNotNull(repository.getProgramById(preview.id))
        assertEquals("la sesión congelada conserva todo el contenido", editedTarget,
            weekOf(afterRebuild, week2.id).sessions.first { it.id == editedTarget.id })
        assertEquals(acceptedW2.sessions.map { it.id }, weekOf(afterRebuild, week2.id).sessions.map { it.id })
        assertTrue(afterRebuild.manualSessionOverrides.any { it.sessionId == editedOther.id })
        assertEquals(editedOther, weekOf(afterRebuild, week3.id).sessions.first { it.id == editedOther.id })
        assertEquals(trainedWeekBefore.sessions, weekOf(afterRebuild, week1.id).sessions)

        val persistedManual = afterRebuild
        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        val reopenedManual = room { db.programDao().getById(preview.id)?.toProgram() }
        assertEquals("marca, contenido y receta efectiva manual sobreviven al reopen", persistedManual, reopenedManual)
        assertEquals(week1Logs.map { it.id }.toSet(), repository.history.value.map { it.id }.toSet())

        val source = requireNotNull(repository.getProgramById(preview.id))
        val expectedRestoredName = recipeWeek(requireNotNull(source.sourceRecipe), week2)
            .days.first { it.id == recipeDay }.label
        // Restaurar con la ViewModel REAL de Detalle de programa, tras reabrir (no una lambda
        // paralela): es el código que ejecuta el botón «Restaurar esta sesión desde el plan».
        val detail = ProgramDetailViewModel(preview.id)
        detail.restoreManualSessionFromPlan(editedTarget.id)
        withTimeout(15_000) {
            detail.uiState.first { it.snackbarMessage?.startsWith("Sesión restaurada desde el plan.") == true }
        }
        val restored = requireNotNull(repository.getProgramById(preview.id))
        val restoredTarget = weekOf(restored, week2.id).sessions.first { it.id == editedTarget.id }
        assertEquals("Restaurar devuelve esa sesión a su receta", expectedRestoredName, restoredTarget.name)
        assertNotEquals(editedTarget.name, restoredTarget.name)
        // Contenido completo de la receta EFECTIVA aceptada (SCALE_WEEK_VOLUME), no solo el nombre.
        assertEquals("la sesión restaurada recupera la prescripción efectiva aceptada",
            contentOf(target), contentOf(restoredTarget))
        assertNotEquals(contentOf(editedTarget), contentOf(restoredTarget))
        assertEquals("ids de sesión, partes, ejercicios, ocurrencias y series se conservan",
            identityOf(editedTarget), identityOf(restoredTarget))
        assertTrue("restaurar no toca la receta efectiva aceptada",
            restored.effectiveWeekRecipes == accepted.effectiveWeekRecipes)
        assertEquals("solo se restaura la elegida", listOf(editedOther.id), restored.manualSessionOverrides.map { it.sessionId })
        assertEquals("la otra sesión congelada no se toca", editedOther,
            weekOf(restored, week3.id).sessions.first { it.id == editedOther.id })
        assertEquals(trainedWeekBefore.sessions, weekOf(restored, week1.id).sessions)

        val restoredSnapshot = restored
        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        assertEquals("restauración durable sin borrar otra marca", restoredSnapshot,
            room { db.programDao().getById(preview.id)?.toProgram() })
        assertEquals(week1Logs.map { it.id }.toSet(), repository.history.value.map { it.id }.toSet())
        assertCursor(repository, 1, week2.id)

        val week2Logs = recordWeek(repository, preview.id, week2.id, cycleNumber = 1)
        assertCursor(repository, 1, week3.id)
        val beforeReject = requireNotNull(repository.getProgramById(preview.id))
        val week4BeforeReject = weekOf(beforeReject, week4.id)
        val rejectedProposal = AutoregulationProposal(
            kind = AutoregulationProposalKind.SCALE_WEEK_VOLUME,
            volumeFactor = 0.5,
            explanation = "Q5 rejection: no cambiar la siguiente semana",
        )
        assertTrue(repository.mutateProgramNow(preview.id) { current ->
            current.copy(runState = requireNotNull(current.runState).copy(
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "Propuesta a rechazar",
                    proposals = listOf(rejectedProposal),
                    targetWeekId = week4.id,
                ),
            ))
        })
        assertTrue(repository.resolvePendingAutoregulationNow(preview.id, accept = false))
        val rejected = requireNotNull(repository.getProgramById(preview.id))
        assertEquals("rechazar no reconstruye la semana futura", week4BeforeReject.sessions,
            weekOf(rejected, week4.id).sessions)
        assertTrue(rejected.runState?.autoregulationAudit.orEmpty().any {
            it.resolution == PendingActionResolutionStatus.REJECTED && it.resolutionReason.isNotBlank()
        })
        assertNull(rejected.runState?.pendingAction)

        val cycleOneLogs = mutableListOf<WorkoutLog>()
        cycleOneLogs += week1Logs
        cycleOneLogs += week2Logs
        var lastCycleOneLog: WorkoutLog? = null
        for (index in 2 until orderedWeeks.size) {
            val live = requireNotNull(repository.getProgramById(preview.id))
            val targetWeek = weeksOf(live)[index]
            assertCursor(repository, 1, targetWeek.id)
            val logs = recordWeek(repository, preview.id, targetWeek.id, cycleNumber = 1)
            cycleOneLogs += logs
            lastCycleOneLog = logs.last()
        }

        val cycleClosed = requireNotNull(repository.getProgramById(preview.id))
        assertEquals(ProgramRunStatus.ACTIVE, cycleClosed.runState?.status)
        assertEquals("ciclo realmente avanzado", 2, cycleClosed.runState?.cycleNumber)
        val cycle2Week1Instance = ProgramProgressEngine.instanceIdFor(2, week1.id)
        assertEquals(cycle2Week1Instance, cycleClosed.runState?.weekInstanceId)
        assertNotEquals(ProgramProgressEngine.instanceIdFor(1, week1.id), cycle2Week1Instance)
        assertEquals(trainedWeekBefore.sessions, weekOf(cycleClosed, week1.id).sessions)
        assertEquals(cycleOneLogs.size, cycleOneLogs.map { it.id }.distinct().size)
        assertEquals(
            "Una sesión por ocurrencia de ciclo; la plantilla repetida no colisiona",
            cycleOneLogs.size,
            cycleOneLogs.map { Triple(it.cycleNumber, it.weekInstanceId, it.sessionId) }.distinct().size,
        )
        val effectiveKeys = cycleClosed.effectiveWeekRecipes.map { it.weekOccurrence to it.cycleNumber }
        assertEquals(effectiveKeys.size, effectiveKeys.distinct().size)
        val proposalIds = cycleClosed.effectiveWeekRecipes.flatMap { it.appliedProposals }.map { it.proposalId }
        assertEquals(proposalIds.size, proposalIds.distinct().size)
        assertEquals(1, proposalIds.count { it == "native-progression-c2" })

        // Replaying the final workout receipt must not re-close the cycle or duplicate its proposal.
        val effectiveBeforeRetry = cycleClosed.effectiveWeekRecipes
        repository.finalizeWorkout(requireNotNull(lastCycleOneLog))
        val afterWorkoutRetry = requireNotNull(repository.getProgramById(preview.id))
        assertEquals(2, afterWorkoutRetry.runState?.cycleNumber)
        assertEquals(effectiveBeforeRetry, afterWorkoutRetry.effectiveWeekRecipes)
        assertEquals(cycleOneLogs.size, repository.history.value.count { it.programId == preview.id })

        val cycle2Logs = recordWeek(
            repository,
            preview.id,
            week1.id,
            cycleNumber = 2,
            sessionLimit = 1,
        )
        assertEquals(cycle2Week1Instance, repository.activeProgramState.value?.currentWeekInstanceId)
        assertEquals(2, cycle2Logs.single().cycleNumber)
        assertEquals(cycle2Week1Instance, cycle2Logs.single().weekInstanceId)
        val allLogs = repository.history.value.filter { it.programId == preview.id }
        assertEquals(allLogs.size, allLogs.map { it.id }.distinct().size)
        assertTrue(allLogs.any { it.cycleNumber == 1 && it.sessionId == cycle2Logs.single().sessionId })
        assertTrue(allLogs.any { it.cycleNumber == 2 && it.sessionId == cycle2Logs.single().sessionId })
        assertEquals("El progreso del segundo ciclo no toca el contenido histórico", trainedWeekBefore.sessions,
            weekOf(requireNotNull(repository.getProgramById(preview.id)), week1.id).sessions)
    }

    @Test
    fun failedRoomTransactionRollsBackProgramPlanSettingsReceiptAndDraftThenRetriesIdempotently() = runBlocking {
        val preview = nativePreview(nativeCases().first(), "q5-failed-transaction").preparedPlan
        // Repositorio REAL sobre Room en memoria (el tearDown lo cierra): además de las filas, la
        // caché (programas, cursor activo, settings) debe seguir intacta tras el fallo.
        val repository = ProgramRepository.initForTests(app)
        withTimeout(10_000) { repository.isReady.first { it } }
        val db = repository.databaseForTests()
        val settingsBefore = repository.settings.value
        try {
            val draftId = "q5-failed-draft"
            SetupDraftRepository(db).save(draftId, "{\"unsaved\":true}", 7, PersonalizedPlanCatalog.REVISION)
            withContext(Dispatchers.IO) {
                db.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_q5_settings BEFORE INSERT ON settings " +
                        "BEGIN SELECT RAISE(ABORT, 'q5 forced rollback'); END",
                )
            }
            val request = SetupCommitRequest(
                commitId = preview.id,
                draftId = draftId,
                settings = Settings(username = "Debe revertirse", onboardingCompleted = true),
                program = preview,
                nutritionPlan = NutritionPlan(id = "q5-nutrition-plan", name = "Q5 plan"),
                activateProgram = true,
                activateNutrition = true,
            )
            val coordinator = SetupCommitCoordinator(db, repository)
            val failed = runCatching { coordinator.commit(request) }
            assertTrue("el fallo de escritura debe propagarse", failed.isFailure)
            // Caché intacta: nada del commit fallido se publicó en memoria.
            assertNull(repository.getProgramById(preview.id))
            assertTrue(repository.programs.value.none { it.id == preview.id })
            assertNull(repository.activeProgramState.value)
            assertEquals(settingsBefore, repository.settings.value)
            assertNull(room { db.programDao().getById(preview.id) })
            assertTrue(room { db.nutritionDao().getAllPlans() }.isEmpty())
            assertNull(room { db.stateDao().getActiveProgram() })
            assertNull(room { db.nutritionDao().getActiveState() })
            assertNull(room { db.settingsDao().get() })
            assertNull(room { db.setupCommitReceiptDao().get(preview.id) })
            assertEquals("rollback conserva el borrador recuperable", 7L,
                room { db.setupDraftDao().getDraft(draftId)?.revision })

            withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_q5_settings") }
            val committed = coordinator.commit(request)
            assertEquals(SetupCommitResult(preview.id, preview.id, "q5-nutrition-plan", emptyList()), committed)
            assertEquals(preview, room { db.programDao().getById(preview.id)?.toProgram() })
            assertEquals(1, room { db.nutritionDao().getAllPlans().count { it.id == "q5-nutrition-plan" } })
            assertEquals(preview.id, room { db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId })
            assertEquals("q5-nutrition-plan", room { db.nutritionDao().getActiveState()?.activePlanId })
            assertEquals("Debe revertirse", room { db.settingsDao().get()?.toSettings()?.username })
            assertEquals(preview.id, room { db.setupCommitReceiptDao().get(preview.id)?.programId })
            assertNull(room { db.setupDraftDao().getDraft(draftId) })

            // El reintento exitoso sí publica: la caché describe el MISMO commit que Room.
            assertEquals(preview.id, repository.activeProgramState.value?.programId)
            assertEquals(room { db.programDao().getById(preview.id)?.toProgram() }, repository.getProgramById(preview.id))
            assertEquals("Debe revertirse", repository.settings.value.username)

            assertEquals("replay no duplica programa, plan ni receipt", committed, coordinator.commit(request))
            assertEquals(1, room { db.programDao().getAll().count { it.id == preview.id } })
            assertEquals(1, room { db.nutritionDao().getAllPlans().count { it.id == "q5-nutrition-plan" } })
            assertEquals(1, room { db.setupCommitReceiptDao().getAll().count { it.commitId == preview.id } })
            assertEquals(1, repository.programs.value.count { it.id == preview.id })
        } finally {
            // La BD en memoria pertenece al repositorio: ProgramRepository.closeInstance() (tearDown) la cierra.
        }
    }

    /**
     * §14.2 / §17.2 #6 con datos REALES: se registra una serie pesada de 3–5 reps en el ejercicio
     * pesado enlazado de PHAT, se relee de Room tras reabrir, y la carga SPEED sale de ESA serie:
     * 65–70 % de la MISMA configuración. Sin serie la carga queda pendiente (null ≠ 0 kg), y una
     * serie fuera de 3–5 reps o de otra configuración nunca es base de velocidad.
     */
    @Test
    fun phatHeavyWorkingSetDefinesTheSpeedLoadOfTheSameConfigurationAfterRoomReopen() = runBlocking {
        val recipe = AuthoredPhulPhatRecipes.phatOriginal
        val program = materializeAuthored("q5-phat-speed", "PHAT SPEED", recipe)
        var repository = openPersistentRepository()
        var db = repository.databaseForTests()
        val draftId = "q5-draft-phat-speed"
        saveDraft(db, draftId)
        val request = requestFor(
            program,
            draftId,
            Settings(username = "PHAT SPEED", onboardingCompleted = true),
            activate = true,
        )
        assertEquals(SetupCommitResult(program.id, program.id, null, emptyList()),
            SetupCommitCoordinator(db, repository).commit(request))
        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        repository.startProgram(program.id)

        val committed = requireNotNull(repository.getProgramById(program.id))
        val week1 = weeksOf(committed).first()
        val weekExercises = week1.sessions.flatMap { it.allExercises() }
        val speedExercises = weekExercises.filter { it.slotRole == SlotRole.SPEED }
        assertEquals("PHAT publica tres bloques SPEED en W1", 3, speedExercises.size)
        val links = speedExercises.map { speed ->
            val sourceSlotId = requireNotNull(speed.loadReference?.sourceSlotId) {
                "${speed.name}: el SPEED declara el slot pesado enlazado"
            }
            val heavy = weekExercises.first { it.recipeSlotId == sourceSlotId }
            assertEquals("el SPEED usa la MISMA configuración que su pesado enlazado",
                speed.catalogConfigurationId, heavy.catalogConfigurationId)
            assertTrue("sin serie pesada la carga SPEED sigue pendiente (null, nunca 0 kg)",
                speed.sets.isNotEmpty() && speed.sets.all { it.weight == null })
            assertTrue(
                "sin serie pesada la referencia SPEED es pendiente",
                PlanLoadResolver.resolveSpeedLoad(
                    reference = speed.loadReference,
                    targetConfigurationId = requireNotNull(speed.catalogConfigurationId),
                    targetConvention = speed.loadQuantityConvention,
                ) is PlanLoadResolution.Pending,
            )
            speed to heavy
        }

        // Serie pesada REAL de 4 reps con 100 kg en el ejercicio enlazado del primer SPEED.
        val (speed, heavy) = links.first()
        val heavySession = week1.sessions.first { session -> session.allExercises().any { it.id == heavy.id } }
        val heavySet = heavy.sets.first()
        val log = logSession(
            repository = repository,
            programId = program.id,
            templateWeekId = week1.id,
            session = heavySession,
            completed = listOf(
                CompletedExercise(
                    exerciseId = heavy.id,
                    exerciseName = heavy.name,
                    sets = listOf(CompletedSet(id = heavySet.id, weight = 100.0, reps = 4, rir = 1)),
                ),
            ),
        )

        repository = reopenPersistentRepository()
        db = repository.databaseForTests()
        val persisted = requireNotNull(room { db.workoutLogDao().getById(log.id)?.toWorkoutLog() })
        val persistedSet = persisted.completedExercises.single { it.exerciseId == heavy.id }.sets.single()
        assertEquals(100.0, persistedSet.weight, 0.0)
        assertEquals(4, persistedSet.reps)

        val heavyConfiguration = requireNotNull(heavy.catalogConfigurationId)
        val speedConfiguration = requireNotNull(speed.catalogConfigurationId)
        val observation = ObservedWorkingSet(
            configurationId = heavyConfiguration,
            quantityConvention = heavy.loadQuantityConvention,
            loadKg = persistedSet.weight,
            reps = persistedSet.reps,
            sourceSlotId = heavy.recipeSlotId,
            sourceProgramId = program.id,
            sourceRunId = persisted.programRunId,
            sourceWeekOccurrence = 1,
        )
        val resolved = PlanLoadResolver.resolveSpeedLoad(
            reference = speed.loadReference,
            targetConfigurationId = speedConfiguration,
            targetConvention = speed.loadQuantityConvention,
            observation = observation,
        )
        assertTrue("la serie pesada real habilita SPEED: $resolved", resolved is PlanLoadResolution.Resolved)
        resolved as PlanLoadResolution.Resolved
        assertEquals(100.0, resolved.baseLoadKg, 1e-9)
        assertEquals("65 % de la carga de trabajo", 65.0, resolved.targetRange.start, 1e-9)
        assertEquals("70 % de la carga de trabajo", 70.0, resolved.targetRange.endInclusive, 1e-9)
        assertEquals(resolved.targetRange.start, resolved.loadKg, 1e-9)
        assertEquals("la base capturada es de la MISMA configuración", speedConfiguration,
            resolved.sourceReference.configurationId)
        assertEquals(heavyConfiguration, resolved.sourceReference.configurationId)

        // Nunca es base de velocidad: otra configuración, fuera de 3–5 reps o sin carga válida.
        val otherConfiguration = requireNotNull(
            weekExercises.mapNotNull { it.catalogConfigurationId }.first { it != speedConfiguration },
        )
        listOf(
            "otra configuración" to observation.copy(configurationId = otherConfiguration),
            "6 reps (hipertrofia, no trabajo pesado)" to observation.copy(reps = 6),
            "2 reps (fuera de 3–5)" to observation.copy(reps = 2),
            "sin carga" to observation.copy(loadKg = 0.0),
        ).forEach { (label, invalid) ->
            assertTrue(
                "$label no puede basear la carga SPEED",
                PlanLoadResolver.resolveSpeedLoad(
                    reference = speed.loadReference,
                    targetConfigurationId = speedConfiguration,
                    targetConvention = speed.loadQuantityConvention,
                    observation = invalid,
                ) is PlanLoadResolution.Unrepresentable,
            )
        }
    }

    /** Inicia y finaliza UNA sesión con series completadas explícitas (sin tocar el resto de la semana). */
    private suspend fun logSession(
        repository: ProgramRepository,
        programId: String,
        templateWeekId: String,
        session: Session,
        completed: List<CompletedExercise>,
        cycleNumber: Int = 1,
    ): WorkoutLog {
        val latest = requireNotNull(repository.getProgramById(programId))
        val weekInstanceId = weekInstanceIdFor(latest, cycleNumber, templateWeekId)
        workoutOrdinal += 1
        val started = repository.startWorkout(
            OngoingWorkoutState(
                programId = programId,
                session = session,
                startTime = 1_800_000_000_000L + workoutOrdinal,
                weekId = weekInstanceId,
            ),
        )
        assertTrue("${session.id} debe iniciar como sesión real", started is StartWorkoutResult.Started)
        val log = WorkoutLog(
            id = "q5-log-$programId-c$cycleNumber-$templateWeekId-${session.id}",
            programId = programId,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-09-29T11:${(workoutOrdinal % 60).toString().padStart(2, '0')}:00Z",
            durationMinutes = session.targetDurationMinutes ?: 45,
            completedExercises = completed,
            weekId = templateWeekId,
            weekInstanceId = weekInstanceId,
            cycleNumber = cycleNumber,
            programRunId = latest.runState?.runId ?: repository.activeProgramState.value?.programRunId,
        )
        repository.finalizeWorkout(log)
        return log
    }
}
