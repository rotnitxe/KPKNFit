package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.generator.DisciplinePools
import com.example.kpkn.domain.training.generator.GeneratorCatalog
import com.example.kpkn.domain.training.generator.MovementPools
import com.example.kpkn.domain.training.generator.PoolEntry
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import com.example.kpkn.screens.onboarding.marksLifts
import com.example.kpkn.screens.onboarding.routineRequest
import com.example.kpkn.screens.onboarding.withFreshestDay
import com.example.kpkn.screens.onboarding.withGoalProfile
import com.example.kpkn.screens.onboarding.withLiftMark
import com.example.kpkn.screens.onboarding.withPlaces
import com.example.kpkn.screens.onboarding.withSessionMinutes
import com.example.kpkn.screens.onboarding.withWeekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · ¿se preguntan las marcas de arranque y de dos tiempos a Halterofilia? La regla de oro del asistente es que
 * ningún control escribe un dato que el motor no lea. Con el lote OL-1 el catálogo trae los levantamientos olímpicos y
 * desde el paquete D1b las reservas del generador los programan y cuelgan su carga de esas marcas. La evidencia, medida
 * aquí con el generador real y el pedido que arma el propio asistente:
 *
 *  1. Qué ids del catálogo corresponden a cada marca (las entradas de las reservas que la citan) y que todos existen.
 *  2. Cada entrada olímpica lleva una razón estimada de 0,6 a 1,0 respecto a su marca, no es «básica» y pide nivel
 *     intermedio o avanzado: de ahí [MarksContext.OLYMPIC_MIN_LEVEL].
 *  3. El valor de [MarksContext.CATALOG_HAS_OLYMPIC_LIFTS] coincide con lo que las reservas leen.
 *  4. Lo declarado tiene efecto: cambiar la marca del arranque cambia SOLO la carga de los ejercicios del arranque, y la de los
 *     dos tiempos, SOLO la de los suyos; sin marca esas series salen sin carga.
 *  5. Se preguntan si y solo si el programa de esa persona lleva levantamientos olímpicos: quien empieza o vuelve no los
 *     recibe y no ve esos controles.
 */
class OlympicMarksTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog
    private val gym = setOf(TrainingPlace.GYM)
    private val weightlifting = GeneratedPlans.modeFor(TrainingGoalProfile.WEIGHTLIFTING)
    private val olympicMarks = setOf(LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK)

    /** Las configuraciones de OL-1 que cuelgan de cada marca, tal como las citan las reservas de D1b. */
    private val expectedConfigurations: Map<LiftMark, Set<String>> = mapOf(
        LiftMark.SNATCH to setOf(
            "power_snatch__barbell", "hang_power_snatch__barbell", "squat_snatch__barbell", "snatch_pull__barbell",
            "overhead_squat__barbell",
        ),
        LiftMark.CLEAN_AND_JERK to setOf(
            "power_clean__barbell", "hang_power_clean__barbell", "squat_clean__barbell", "clean_pull__barbell",
            "push_jerk__barbell", "split_jerk__barbell",
        ),
    )

    private fun poolEntries(): List<PoolEntry> =
        MovementPools.byPattern.values.flatten().flatMap { it.entries } +
            DisciplinePools.byMode.values.flatMap { byPattern -> byPattern.values.flatten().flatMap { it.entries } }

    private fun olympicEntries(): List<PoolEntry> = poolEntries().filter { it.mark in olympicMarks }

    // ─── 1 · A qué ids corresponde cada marca ───────────────────────────────────────────────────────────────────

    @Test
    fun eachOlympicMarkMapsToTheCatalogConfigurationsTheGeneratorPoolsHangFromIt() {
        val index = GeneratorCatalog.of(catalog)
        olympicMarks.forEach { mark ->
            val cited = olympicEntries().filter { it.mark == mark }.map { it.id }.toSet()
            assertEquals("las configuraciones que cuelgan de $mark", expectedConfigurations.getValue(mark), cited)
            cited.forEach { id -> assertTrue("«$id» ($mark) está en el catálogo aprobado", index.entry(id) != null) }
        }
        // Ninguna configuración cuelga de las dos marcas a la vez: cada levantamiento lee una sola.
        val bySnatch = expectedConfigurations.getValue(LiftMark.SNATCH)
        val byCleanAndJerk = expectedConfigurations.getValue(LiftMark.CLEAN_AND_JERK)
        assertTrue(bySnatch.intersect(byCleanAndJerk).isEmpty())
    }

    // ─── 2 · La razón con la marca y el nivel mínimo ────────────────────────────────────────────────────────────

    @Test
    fun everyOlympicEntryIsAnEstimatedRatioOfItsMarkAndNeverBasicAndNeedsTheIntermediateLevel() {
        val entries = olympicEntries()
        assertTrue("las reservas citan levantamientos olímpicos", entries.size >= 11)
        entries.forEach { entry ->
            assertTrue("${entry.id}: razón ${entry.markFactor} con ${entry.mark}", entry.markFactor in 0.6..1.0)
            assertFalse("${entry.id} es básico: la exención del novato es solo de los básicos de fuerza", entry.basic)
            assertTrue("${entry.id}: nivel ${entry.minLevel}", entry.minLevel.ordinal >= RoutineLevel.INTERMEDIATE.ordinal)
        }
        assertEquals(
            "el nivel desde el que se preguntan es el mínimo de las entradas que leen esas marcas",
            entries.minOf { it.minLevel },
            MarksContext.OLYMPIC_MIN_LEVEL,
        )
    }

    // ─── 3 · El valor de la bandera ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun theOlympicMarksFlagMatchesWhetherTheGeneratorReadsThem() {
        val marksRead = poolEntries().mapNotNull { it.mark }.toSet()
        assertEquals("las reservas leen las seis marcas", LiftMark.entries.toSet(), marksRead)
        val generatorReadsThem = poolEntries().any { it.mark in olympicMarks }
        assertEquals(
            "Las marcas olímpicas solo se preguntan si el generador las lee: " +
                (if (generatorReadsThem) "las reservas citan SNATCH/CLEAN_AND_JERK, así que MarksContext.CATALOG_HAS_OLYMPIC_LIFTS debe ser true"
                else "ninguna reserva cita SNATCH/CLEAN_AND_JERK, así que MarksContext.CATALOG_HAS_OLYMPIC_LIFTS debe ser false"),
            generatorReadsThem,
            MarksContext.CATALOG_HAS_OLYMPIC_LIFTS,
        )
    }

    // ─── 4 · Lo declarado tiene efecto ──────────────────────────────────────────────────────────────────────────

    private fun weightliftingDraft(
        experience: SetupExperience,
        marks: Map<LiftMark, Double> = emptyMap(),
        days: Set<Int> = setOf(1, 2, 4, 5, 6),
        minutes: Int = 90,
    ): SetupWizardDraft = SetupWizardDraft(includeTraining = true)
        .withPlaces(gym)
        .withGoalProfile(TrainingGoalProfile.WEIGHTLIFTING)
        .copy(experience = experience)
        .withWeekdays(days)
        .withFreshestDay(days.min())
        .withSessionMinutes(minutes)
        .let { base -> marks.entries.fold(base) { draft, (lift, kg) -> draft.withLiftMark(lift, kg) } }

    private fun programOf(draft: SetupWizardDraft): Program =
        RoutineGenerator.generate(draft.routineRequest(weightlifting, catalog)).program

    private fun exercisesOf(program: Program): List<Exercise> = program.macrocycles.flatMap { it.blocks }
        .flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.flatMap { it.allExercises() }

    /** Configuración y peso de cada serie, en el orden del programa: lo que cambia si cambia una marca. */
    private fun loadsOf(program: Program): List<Pair<String?, List<Double?>>> =
        exercisesOf(program).map { exercise -> exercise.catalogConfigurationId to exercise.sets.map { it.weight } }

    private fun idsOf(loads: List<Pair<String?, List<Double?>>>) = loads.map { it.first }

    private fun changedConfigurations(
        before: List<Pair<String?, List<Double?>>>,
        after: List<Pair<String?, List<Double?>>>,
    ): Set<String?> = before.indices.filter { before[it] != after[it] }.map { before[it].first }.toSet()

    @Test
    fun declaringTheSnatchMarkChangesOnlyTheLoadsOfTheSnatchLiftsAndTheCleanAndJerkMarkOnlyThoseOfTheirs() {
        val squat = mapOf(LiftMark.SQUAT to 140.0)
        val snatchIds = expectedConfigurations.getValue(LiftMark.SNATCH)
        val jerkIds = expectedConfigurations.getValue(LiftMark.CLEAN_AND_JERK)
        for (experience in listOf(SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED)) {
            val none = loadsOf(programOf(weightliftingDraft(experience, squat)))
            val both = loadsOf(programOf(weightliftingDraft(experience, squat + mapOf(LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 100.0))))
            val snatchUp = loadsOf(programOf(weightliftingDraft(experience, squat + mapOf(LiftMark.SNATCH to 100.0, LiftMark.CLEAN_AND_JERK to 100.0))))
            val jerkUp = loadsOf(programOf(weightliftingDraft(experience, squat + mapOf(LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 120.0))))

            // Declarar marcas no cambia qué ejercicios salen, solo con cuánta carga.
            assertEquals("$experience: los mismos ejercicios con y sin marcas", idsOf(none), idsOf(both))
            assertEquals("$experience: los mismos ejercicios al subir el arranque", idsOf(both), idsOf(snatchUp))
            assertEquals("$experience: los mismos ejercicios al subir los dos tiempos", idsOf(both), idsOf(jerkUp))

            val used = idsOf(both).toSet()
            assertTrue("$experience: el programa lleva levantamientos del arranque: $used", used.any { it in snatchIds })
            assertTrue("$experience: el programa lleva levantamientos de los dos tiempos: $used", used.any { it in jerkIds })

            // Sin marca, la serie de esos levantamientos sale sin carga («carga pendiente»); con ella, con carga.
            none.filter { it.first in snatchIds + jerkIds }.forEach { (id, weights) ->
                assertTrue("$experience: $id sin marca declarada debe salir sin carga: $weights", weights.all { it == null })
            }
            both.filter { it.first in snatchIds + jerkIds }.forEach { (id, weights) ->
                assertTrue("$experience: $id con su marca declarada debe llevar carga: $weights", weights.all { it != null && it > 0.0 })
            }

            // Subir el arranque cambia el arranque y nada más; subir los dos tiempos, los dos tiempos y nada más.
            val snatchChanged = changedConfigurations(both, snatchUp)
            assertTrue("$experience: subir el arranque no cambió ninguna carga", snatchChanged.isNotEmpty())
            assertTrue("$experience: subir el arranque cambió también ${snatchChanged - snatchIds}", snatchIds.containsAll(snatchChanged))
            val jerkChanged = changedConfigurations(both, jerkUp)
            assertTrue("$experience: subir los dos tiempos no cambió ninguna carga", jerkChanged.isNotEmpty())
            assertTrue("$experience: subir los dos tiempos cambió también ${jerkChanged - jerkIds}", jerkIds.containsAll(jerkChanged))
        }
    }

    // ─── 5 · Se preguntan si y solo si el programa los lleva ────────────────────────────────────────────────────

    @Test
    fun theWeightliftingPickerAsksSquatSnatchAndCleanAndJerkFromTheIntermediateLevelOnly() {
        val squatOnly = listOf(LiftMark.SQUAT)
        val all = listOf(LiftMark.SQUAT, LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK)
        assertEquals(emptyList<LiftMark>(), weightliftingDraft(SetupExperience.NEW).marksLifts())
        assertEquals(squatOnly, weightliftingDraft(SetupExperience.RETURNING).marksLifts())
        assertEquals(all, weightliftingDraft(SetupExperience.INTERMEDIATE).marksLifts())
        assertEquals(all, weightliftingDraft(SetupExperience.ADVANCED).marksLifts())
        // Sin declarar la experiencia se parte del nivel de quien empieza: tampoco se piden las olímpicas.
        assertEquals(squatOnly, weightliftingDraft(SetupExperience.INTERMEDIATE).copy(experience = null).marksLifts())
    }

    @Test
    fun theOlympicMarksAreAskedExactlyWhenTheGeneratedProgramCarriesOlympicLifts() {
        val olympicIds = expectedConfigurations.values.flatten().toSet()
        val marks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.SNATCH to 80.0, LiftMark.CLEAN_AND_JERK to 100.0)
        for (experience in SetupExperience.entries) {
            val draft = weightliftingDraft(experience, marks)
            val program = programOf(draft)
            val carriesOlympic = exercisesOf(program).any { it.catalogConfigurationId in olympicIds }
            val asked = draft.marksLifts().containsAll(olympicMarks)
            assertEquals(
                "$experience: las marcas del arranque y de los dos tiempos se preguntan si y solo si el programa lleva levantamientos olímpicos",
                carriesOlympic,
                asked,
            )
            // Y la pregunta coincide con el nivel que el generador ve.
            assertEquals(experience.toString(), MarksContext.readsOlympicMarks(draft.routineRequest(weightlifting, catalog).level), asked)
        }
    }

    @Test
    fun theOlympicMarksAreNotAskedToAnyOtherProfile() {
        for (profile in TrainingGoalProfile.entries.filter { it != TrainingGoalProfile.WEIGHTLIFTING }) {
            for (experience in SetupExperience.entries) {
                val lifts = SetupWizardDraft(includeTraining = true).withPlaces(gym).withGoalProfile(profile)
                    .copy(experience = experience).marksLifts()
                assertTrue("$profile $experience: ${lifts.filter { it in olympicMarks }}", lifts.none { it in olympicMarks })
            }
        }
        // El envión de empuje del strongman (su patrón de empuje vertical) también lee la marca de los dos tiempos, pero el asistente
        // no la pregunta a Strongman: ese envión sale con «carga pendiente». Se deja a propósito (ver el informe de S-B2).
        assertFalse(
            "si Strongman pasara a preguntar los dos tiempos, esta prueba y el informe deben actualizarse",
            LiftMark.CLEAN_AND_JERK in MarksContext.liftsFor(TrainingGoalProfile.STRONGMAN, novice = false),
        )
    }
}
