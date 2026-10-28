package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ExerciseSwapOptions
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.split.CatalogExerciseTraitResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las sustituciones de ejercicios del detalle (`exerciseOverrides`): sobreviven a [applyLayout] con las series, las
 * repeticiones y el RIR, no cambian la huella del borrador, se ignoran si no encajan y deshacerlas devuelve el original.
 */
class ExerciseOverridesTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val resolver by lazy { CatalogExerciseTraitResolver(catalog) }
    private val profile = TrainingGoalProfile.STRENGTH_MUSCLE

    private fun draft(days: Set<Int>) = SetupWizardDraft(
        commitId = "cambiar",
        experience = SetupExperience.INTERMEDIATE,
        trainingPlaces = setOf(TrainingPlace.GYM),
        trainingOptions = SetupWizardDraft().trainingOptions.copy(
            availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM)),
        ),
        goalProfile = profile,
        goal = GoalProfileMapping.setupGoalOf(profile),
        selectedWeekdays = days,
        daysPerWeek = days.size,
        minutesPerSession = 60,
        freshestDay = days.min(),
        selectedCatalogId = GeneratedPlans.entryIdFor(profile),
    )

    private fun programFor(source: SetupWizardDraft): Program {
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        val routine = RoutineGenerator.generate(source.routineRequest(GeneratedPlans.modeFor(profile), catalog))
        return generatedProgramOf(routine, entry, source)
    }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun exercisesOf(program: Program): List<Exercise> = sessionsOf(program).flatMap { it.allExercises() }

    /** Un ejercicio de fuerza del programa con identidad de catálogo (el que se «Cambia»). */
    private fun swappable(program: Program): Exercise =
        requireNotNull(exercisesOf(program).firstOrNull { it.catalogConfigurationId != null && it.cardioDetails == null && it.sets.isNotEmpty() })

    private fun gym() = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM))

    /** La primera alternativa que ofrece el catálogo para [exercise] con el material del gimnasio. */
    private fun firstOption(exercise: Exercise): SwapOption =
        requireNotNull(ExerciseSwapOptions.optionsFor(exercise, gym(), catalog).firstOrNull())

    private fun exerciseById(program: Program, id: String): Exercise =
        requireNotNull(exercisesOf(program).firstOrNull { it.id == id })

    @Test
    fun theSwapSurvivesApplyLayoutAndKeepsSeriesRepsAndRir() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val option = firstOption(exercise)
        val swapped = source.copy(exerciseOverrides = mapOf(exercise.id to option.configId))

        val result = applyLayout(swapped, program, resolver, placeFit = null, catalog = catalog).program
        val after = exerciseById(result, exercise.id)

        assertEquals(option.configId, after.catalogConfigurationId)
        assertEquals(option.displayName, after.name)
        assertEquals(exercise.sets.size, after.sets.size)
        assertEquals(exercise.sets.first().targetRepsRange, after.sets.first().targetRepsRange)
        assertEquals(exercise.sets.first().targetRIR, after.sets.first().targetRIR)
        assertNull("la carga en kilos no pasa al ejercicio nuevo", after.sets.first().weight)
        assertNull("la referencia de 1RM era de otro levantamiento", after.reference1RM)
        assertNull("la carga pendiente era del ejercicio anterior", after.loadReference)
    }

    @Test
    fun theSamePreviewIsTheSameActivation() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val swapped = source.copy(exerciseOverrides = mapOf(exercise.id to firstOption(exercise).configId))

        val first = applyLayout(swapped, program, resolver, placeFit = null, catalog = catalog).program
        val second = applyLayout(swapped, program, resolver, placeFit = null, catalog = catalog).program
        assertEquals(first, second)
    }

    @Test
    fun withoutOverridesTheProgramIsReturnedAsItWas() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val outcome = applyLayout(source, program, resolver, placeFit = null, catalog = catalog)
        assertSame(program, outcome.program)
    }

    @Test
    fun undoingTheSwapBringsBackTheOriginal() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val swapped = source.copy(exerciseOverrides = mapOf(exercise.id to firstOption(exercise).configId))
        val undone = swapped.copy(exerciseOverrides = swapped.exerciseOverrides - exercise.id)

        val result = applyLayout(undone, program, resolver, placeFit = null, catalog = catalog).program
        assertEquals(exercise.catalogConfigurationId, exerciseById(result, exercise.id).catalogConfigurationId)
    }

    @Test
    fun anUnknownInstanceOrConfigurationIsIgnored() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val broken = source.copy(
            exerciseOverrides = mapOf("no-existe" to firstOption(exercise).configId, exercise.id to "configuracion-inventada"),
        )

        val result = applyLayout(broken, program, resolver, placeFit = null, catalog = catalog).program
        assertEquals(exercise.catalogConfigurationId, exerciseById(result, exercise.id).catalogConfigurationId)
    }

    @Test
    fun theSwapIsNotPartOfTheInputFootprint() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val swapped = source.copy(exerciseOverrides = mapOf(exercise.id to firstOption(exercise).configId))
        assertEquals(source.inputFootprint(), swapped.inputFootprint())
    }

    @Test
    fun clearingTheSwapsKeepsEverythingElse() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val swapped = source.copy(exerciseOverrides = mapOf(exercise.id to firstOption(exercise).configId))
        val cleared = swapped.withoutExerciseSwaps()
        assertTrue(cleared.exerciseOverrides.isEmpty())
        assertEquals(source.selectedCatalogId, cleared.selectedCatalogId)
        assertEquals(source.selectedWeekdays, cleared.selectedWeekdays)
    }

    @Test
    fun theDetailShowsTheSwappedNameOnlyThroughTheReveal() {
        val source = draft(setOf(1, 3, 5))
        val program = programFor(source)
        val exercise = swappable(program)
        val option = firstOption(exercise)
        val reveal = SetupPlanReveal(
            planId = GeneratedPlans.entryIdFor(profile),
            title = "Plan",
            generated = true,
            profile = profile,
            kicker = "",
            blurb = "",
            description = "",
            daysLabel = "",
            minutesLabel = "",
            levelLabel = "",
            badge = null,
            coverSeed = 0,
            mainExercises = emptyList(),
            blocks = emptyList(),
            week = SetupPlanReveals.weekOf(program),
            reasons = emptyList(),
            notes = emptyList(),
            attribution = null,
        )
        val byDay = SetupPlanReveals.exercisesByDay(
            reveal = reveal,
            swaps = mapOf(exercise.id to option.configId),
            nameOf = { CatalogDisplayNames.configurationDisplayName(catalog, it) },
        )
        val row = byDay.values.flatten().first { it.instanceId == exercise.id }
        assertEquals(option.displayName, row.name)
        assertEquals(option.configId, row.swappedConfigId)
        assertTrue(row.swappable)
        // Los demás ejercicios siguen como estaban.
        assertTrue(byDay.values.flatten().filter { it.instanceId != exercise.id }.all { it.swappedConfigId == null })
    }

    /** Los ejercicios de fuerza de la lista principal de la primera sesión que tenga al menos dos (el orden en que los recorre el aplicador). */
    private fun sessionWithTwoStrengthExercises(program: Program): Pair<Session, List<Exercise>> {
        for (session in sessionsOf(program)) {
            val strength = session.exercises.filter { it.catalogConfigurationId != null && it.cardioDetails == null && it.sets.isNotEmpty() }
            if (strength.size >= 2) return session to strength
        }
        error("el programa generado no tiene ninguna sesión con dos ejercicios de fuerza")
    }

    private fun definitionOf(configId: String): String? = com.example.kpkn.domain.training.generator.GeneratorCatalog.of(catalog).entry(configId)?.definitionId

    @Test
    fun twoSwapsTowardTheSameDestinationInOneSessionOnlyApplyTheFirst() {
        // DUP-03 (F-13b): la colisión se mide contra las configuraciones vigentes, no contra la sesión original.
        val program = programFor(draft(setOf(1, 3, 5)))
        val (session, strength) = sessionWithTwoStrengthExercises(program)
        val a = strength[0]
        val b = strength[1]
        val others = session.allExercises().mapNotNull { it.catalogConfigurationId }.toSet() - setOfNotNull(a.catalogConfigurationId)
        val destination = requireNotNull(ExerciseSwapOptions.optionsFor(a, gym(), catalog, excludedConfigurationIds = others).firstOrNull()).configId

        val result = applyExerciseOverrides(program, mapOf(a.id to destination, b.id to destination), catalog)

        val after = sessionsOf(result).first { it.id == session.id }
        assertEquals(1, after.allExercises().count { it.catalogConfigurationId == destination })
        assertEquals(destination, exerciseById(result, a.id).catalogConfigurationId)
        assertEquals(b.catalogConfigurationId, exerciseById(result, b.id).catalogConfigurationId)
    }

    @Test
    fun aDestinationThatAnotherExerciseOfTheSessionAlreadyLeftIsAccepted() {
        // DUP-03 (F-13b): el primer ejercicio cambia a otra cosa y el segundo toma su configuración original (ya libre).
        val program = programFor(draft(setOf(1, 3, 5)))
        val (session, strength) = sessionWithTwoStrengthExercises(program)
        val first = strength[0]
        val second = strength[1]
        val firstConfig = requireNotNull(first.catalogConfigurationId)
        val others = session.allExercises().mapNotNull { it.catalogConfigurationId }.toSet() - firstConfig
        // Un destino para el primero que no choque, por definición ni por familia, con su propia configuración original.
        val away = ExerciseSwapOptions.optionsFor(first, gym(), catalog, excludedConfigurationIds = others).firstOrNull {
            definitionOf(it.configId) != definitionOf(firstConfig) &&
                com.example.kpkn.domain.training.quality.SessionSlopRules.familyOf(it.configId) !=
                com.example.kpkn.domain.training.quality.SessionSlopRules.familyOf(firstConfig)
        }
        if (away == null) return // ese catálogo no ofrece una alternativa sin choque: no hay nada que comprobar

        val result = applyExerciseOverrides(program, mapOf(first.id to away.configId, second.id to firstConfig), catalog)

        assertEquals(away.configId, exerciseById(result, first.id).catalogConfigurationId)
        assertEquals(firstConfig, exerciseById(result, second.id).catalogConfigurationId)
    }
}
