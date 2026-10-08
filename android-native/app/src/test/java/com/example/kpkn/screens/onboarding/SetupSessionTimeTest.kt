package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.generator.RoutineGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · los minutos de la revisión final: UNA medida (el estimador común sobre el programa ya armado, con su
 * aproximación y su movilidad) y UNA regla de «¿cabe?» ([SessionTimeFit]) con las tolerancias del asistente (±1 min en
 * los programas «a medida», 15 % en los del catálogo). La diferencia con la fórmula propia que había antes se explica
 * con aritmética independiente: se cuenta aquí, serie a serie, sin llamar al estimador.
 */
class SetupSessionTimeTest {

    // ─── La regla de «¿cabe?» ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_catalog_plan_fits_with_the_fifteen_percent_of_the_sweep_and_a_generated_one_with_one_minute() {
        // La misma tolerancia con la que el barrido da por viable un plan del catálogo, en todo el rango del reloj (30–180).
        (EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX).forEach { requested ->
            assertEquals(timeBudgetWithTolerance(requested), SessionTimeFit.limit(requested, generated = false))
            assertEquals(requested + 1, SessionTimeFit.limit(requested, generated = true))
        }
        assertEquals(69, SessionTimeFit.limit(60, generated = false))
        assertTrue(SessionTimeFit.fits(60, 69, generated = false))
        assertFalse(SessionTimeFit.fits(60, 70, generated = false))
        assertTrue(SessionTimeFit.fits(60, 61, generated = true))
        assertFalse(SessionTimeFit.fits(60, 62, generated = true))
    }

    @Test
    fun a_catalog_plan_matches_only_without_passing_the_request_and_a_generated_one_has_its_minute_of_margin() {
        assertTrue(SessionTimeFit.matches(60, 60, generated = false))
        assertFalse("pasarse un minuto del catálogo cabe pero no es lo pedido", SessionTimeFit.matches(60, 61, generated = false))
        assertTrue(SessionTimeFit.fits(60, 61, generated = false))
        assertTrue(SessionTimeFit.matches(60, 61, generated = true))
        assertFalse(SessionTimeFit.matches(60, 62, generated = true))
        // «Cabe» nunca es más estricto que «sale lo pedido».
        (EntrenoStepValues.SESSION_MINUTES_MIN..EntrenoStepValues.SESSION_MINUTES_MAX step EntrenoStepValues.SESSION_MINUTES_STEP).forEach { requested ->
            (requested - 3..requested + 30).forEach { longest ->
                listOf(true, false).forEach { generated ->
                    if (SessionTimeFit.matches(requested, longest, generated)) {
                        assertTrue(SessionTimeFit.fits(requested, longest, generated))
                    }
                }
            }
        }
    }

    @Test
    fun the_review_note_appears_only_when_the_longest_session_passes_what_was_asked() {
        val catalogPlan = SetupWizardDraft(minutesPerSession = 60, selectedCatalogId = "native:strength-foundation-v2")
        assertNull(timeReviewNote(catalogPlan, 60))
        assertEquals("~64 min por sesión: un poco más de los 60 que pediste.", timeReviewNote(catalogPlan, 64))
        assertNull("sin minutos medidos no hay nota", timeReviewNote(catalogPlan, null))
        assertNull("sin tiempo pedido no hay con qué comparar", timeReviewNote(SetupWizardDraft(), 64))

        val generatedId = GeneratedPlans.entryIdFor(TrainingGoalProfile.STRENGTH_MUSCLE)
        val tailored = SetupWizardDraft(minutesPerSession = 60, selectedCatalogId = generatedId)
        assertNull("un programa a medida tiene su minuto de margen", timeReviewNote(tailored, 61))
        assertEquals("~63 min por sesión: un poco más de los 60 que pediste.", timeReviewNote(tailored, 63))
    }

    @Test
    fun a_fixed_recipe_differs_when_it_brings_other_days_or_runs_longer_and_a_native_plan_never_asks() {
        val authored = requireNotNull(
            PersonalizedPlanCatalog.listedEntries().firstOrNull { it.source != com.example.kpkn.data.programs.CatalogSource.NATIVE },
        ) { "el catálogo trae planes de autor" }
        assertTrue(isFixedRecipe(authored.id))
        assertFalse(isFixedRecipe("native:strength-foundation-v2"))
        assertFalse(isFixedRecipe(GeneratedPlans.entryIdFor(TrainingGoalProfile.POWERBUILDING)))
        assertFalse(isFixedRecipe(null))

        val draft = SetupWizardDraft(selectedCatalogId = authored.id, selectedWeekdays = setOf(1, 3, 5), minutesPerSession = 60)
        fun state(days: Set<Int>?, minutes: Int?) = SetupWizardState(draft = draft, fixedTrainingDays = days, programSessionMinutes = minutes)
        assertFalse("mismos días y sale lo pedido", state(setOf(1, 3, 5), 60).fixedRecipeDiffers())
        assertTrue("otros días", state(setOf(1, 2, 4), 60).fixedRecipeDiffers())
        assertTrue("pasa de lo pedido", state(setOf(1, 3, 5), 61).fixedRecipeDiffers())
        assertFalse("sin medida no hay diferencia que confirmar", state(setOf(1, 3, 5), null).fixedRecipeDiffers())

        val native = SetupWizardDraft(selectedCatalogId = "native:strength-foundation-v2", selectedWeekdays = setOf(1, 3, 5), minutesPerSession = 60)
        assertFalse(SetupWizardState(draft = native, fixedTrainingDays = null, programSessionMinutes = 70).fixedRecipeDiffers())
    }

    // ─── La medida: el estimador común sobre el programa ya armado ───────────────────────────────────────────

    /**
     * Dos ejercicios en una sesión, contados a mano con las reglas del estimador (SessionDurationEstimator + §12.2):
     *  - cada ejercicio: 60 s de montaje, 45 s por serie (un rango de ≤ 11 repeticiones no pasa de 45 s) y el descanso
     *    entre series (una menos que series);
     *  - cada serie de aproximación: 30 s más su descanso (45 s si no trae);
     *  - cada serie de movilidad: su duración por sus series;
     *  - y 180 s fijos de calentamiento general cuando hay trabajo de resistencia.
     */
    private fun approachedSession(withApproachAndMobility: Boolean): Session {
        fun sets(prefix: String, count: Int) = (1..count).map { ExerciseSet(id = "$prefix-s$it", targetReps = 5) }
        val heavy = Exercise(
            id = "heavy",
            name = "Sentadilla",
            sets = sets("heavy", 4),
            restTime = 120,
            warmupSets = if (withApproachAndMobility) {
                listOf(
                    WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.5, targetReps = 5, restBetween = 60),
                    WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.7, targetReps = 3),
                )
            } else {
                emptyList()
            },
            mobilitySeries = if (withApproachAndMobility) {
                listOf(
                    MobilitySeries(id = "m1", name = "Tobillo", sets = 1, durationSeconds = 30),
                    MobilitySeries(id = "m2", name = "Cadera", sets = 2, durationSeconds = 20),
                )
            } else {
                emptyList()
            },
        )
        val light = Exercise(id = "light", name = "Remo", sets = sets("light", 3), restTime = 90)
        return Session(id = "s", name = "Pierna", exercises = listOf(heavy, light), dayOfWeek = 1, assignedDays = listOf(1))
    }

    private fun programOf(vararg sessions: Session): Program {
        val week = ProgramWeek("w", "Semana", sessions = sessions.toList())
        val block = Block("b", "Bloque", mesocycles = listOf(Mesocycle("me", "Meso", weeks = listOf(week))))
        return Program(id = "p", name = "Programa", macrocycles = listOf(Macrocycle("m", "Macro", blocks = listOf(block))))
    }

    @Test
    fun the_review_minutes_count_the_approach_and_the_mobility_and_the_arithmetic_is_independent() {
        val with = approachedSession(withApproachAndMobility = true)
        val without = approachedSession(withApproachAndMobility = false)

        // Segundos a mano. Sentadilla: 60 + 4·45 + 3·120 = 600; remo: 60 + 3·45 + 2·90 = 375.
        val heavyWork = 60 + 4 * 45 + 3 * 120
        val lightWork = 60 + 3 * 45 + 2 * 90
        val general = 180
        // Aproximación: (30 + 60) + (30 + 45); movilidad: 30·1 + 20·2.
        val approach = (30 + 60) + (30 + 45)
        val mobility = 30 * 1 + 20 * 2
        assertEquals(600, heavyWork)
        assertEquals(375, lightWork)
        assertEquals(165, approach)
        assertEquals(70, mobility)

        val expectedWithout = heavyWork + lightWork + general
        val expectedWith = expectedWithout + approach + mobility
        assertEquals(1155, expectedWithout)
        assertEquals(1390, expectedWith)
        assertEquals(expectedWithout, SessionDurationEstimator.estimate(without).totalSeconds)
        assertEquals(expectedWith, SessionDurationEstimator.estimate(with).totalSeconds)

        // Los minutos son los segundos hacia arriba: 1155 s = 19,25 → 20 min; 1390 s = 23,17 → 24 min.
        assertEquals(20, longestSessionMinutes(programOf(without)))
        assertEquals(24, longestSessionMinutes(programOf(with)))
        // La aproximación y la movilidad suman exactamente lo que se cuenta a mano (235 s), no «lo que salga».
        assertEquals(approach + mobility, expectedWith - expectedWithout)
    }

    @Test
    fun the_old_formula_missed_the_setup_the_warmups_and_the_approach() {
        // Lo que había: series × (45 s + descanso acotado a 30–300 s) por ejercicio, redondeado hacia arriba al minuto.
        fun oldFormulaMinutes(session: Session): Int =
            (session.exercises.sumOf { exercise -> exercise.sets.size * (45 + (exercise.restTime ?: 90).coerceIn(30, 300)) } + 59) / 60

        val with = approachedSession(withApproachAndMobility = true)
        // 4·(45 + 120) + 3·(45 + 90) = 660 + 405 = 1065 s → 18 min por la fórmula antigua…
        assertEquals(18, oldFormulaMinutes(with))
        // …frente a los 24 min del programa armado: 60 s de montaje por ejercicio (120), 180 s de calentamiento general,
        // 235 s de aproximación y movilidad y, a cambio, el último descanso de cada ejercicio que la fórmula antigua sí contaba.
        val missed = 120 + 180 + 235
        val lastRests = 120 + 90
        assertEquals(1065 + missed - lastRests, SessionDurationEstimator.estimate(with).totalSeconds)
        assertEquals(24, longestSessionMinutes(programOf(with)))
    }

    @Test
    fun the_fixed_warmup_of_the_estimator_adds_exactly_three_minutes_even_when_the_program_brings_its_own() {
        // Desviación conocida (no se cambia: la usa más código): el estimador suma 180 s de calentamiento general a toda
        // sesión con resistencia, también cuando el programa ya trae aproximación y movilidad explícitas.
        val with = approachedSession(withApproachAndMobility = true)
        val breakdown = SessionDurationEstimator.estimate(with)
        val explicit = (30 + 60) + (30 + 45) + 30 + 20 * 2
        assertEquals("el bloque de calentamiento = lo explícito + el fijo", explicit + 180, breakdown.warmupSeconds)
        val withoutFixed = breakdown.totalSeconds - 180
        assertEquals(1210, withoutFixed)
        // 180 es múltiplo de 60: la desviación en minutos es SIEMPRE de 3, sea cual sea la sesión.
        assertEquals(3, breakdown.totalMinutes - minutesOfSeconds(withoutFixed))
        listOf(
            programOf(approachedSession(withApproachAndMobility = false)),
            programOf(with),
        ).forEach { program ->
            program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.forEach { session ->
                val total = SessionDurationEstimator.estimate(session).totalSeconds
                assertEquals(3, minutesOfSeconds(total) - minutesOfSeconds(total - 180))
            }
        }
    }

    private fun minutesOfSeconds(seconds: Int): Int = (seconds + 59) / 60

    @Test
    fun the_longest_session_is_the_maximum_of_the_whole_program_and_empty_programs_have_none() {
        val short = approachedSession(withApproachAndMobility = false).copy(id = "short", dayOfWeek = 2, assignedDays = listOf(2))
        val long = approachedSession(withApproachAndMobility = true)
        val program = programOf(short, long)
        assertEquals(24, longestSessionMinutes(program))
        assertNull(longestSessionMinutes(null))
        assertNull("sin sesiones con contenido no hay minutos", longestSessionMinutes(programOf(Session(id = "vacía", name = "Vacía"))))
    }

    // ─── Sobre un programa «a medida» real ───────────────────────────────────────────────────────────────

    @Test
    fun a_tailored_program_is_measured_with_the_same_estimator_the_generator_used() {
        val profile = TrainingGoalProfile.STRENGTH_MUSCLE
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        val gym = setOf(TrainingPlace.GYM)
        val draft = SetupWizardDraft(
            commitId = "revision",
            experience = SetupExperience.INTERMEDIATE,
            trainingPlaces = gym,
            trainingOptions = SetupWizardDraft().trainingOptions.copy(
                availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym),
            ),
            goalProfile = profile,
            goal = GoalProfileMapping.setupGoalOf(profile),
            selectedWeekdays = setOf(1, 3, 5),
            daysPerWeek = 3,
            minutesPerSession = 60,
            freshestDay = 3,
            selectedCatalogId = entry.id,
        )
        val routine = RoutineGenerator.generate(draft.routineRequest(GeneratedPlans.modeFor(profile), CatalogCompositionTestSupport.catalog))
        val program = generatedProgramOf(routine, entry, draft)

        val longest = checkNotNull(longestSessionMinutes(program)) { "el programa tiene sesiones" }
        val bySession = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
            .map { SessionDurationEstimator.estimate(it).totalMinutes }
        assertEquals(bySession.max(), longest)
        assertEquals("el generador midió con el mismo estimador", routine.report.sessionMinutes.max(), longest)
        // La ventana del generador es [85 %, 110 %] del tiempo pedido: 51–66 min para 60.
        assertTrue("la sesión más larga cae en la ventana del generador: $longest min de 60", longest in 51..66)
        // El renglón de la revisión usa ESA medida.
        val detail = checkNotNull(programReviewDetail(program, draft)) { "el programa tiene días" }
        assertTrue("«$detail»", "~$longest min por sesión" in detail)
    }
}
