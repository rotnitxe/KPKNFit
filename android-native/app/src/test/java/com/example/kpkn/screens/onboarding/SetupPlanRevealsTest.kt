package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.generator.DisciplineWeeks
import com.example.kpkn.domain.training.generator.RoutineGenerator
import com.example.kpkn.domain.training.generator.RoutineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Entreno v2 · lo que el revelado del paso PLAN cuenta de cada programa sale del programa ya preparado. */
class SetupPlanRevealsTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private fun draft(profile: TrainingGoalProfile, days: Set<Int> = setOf(1, 3, 5), fresh: Int = 3) = SetupWizardDraft(
        commitId = "revelado",
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
        freshestDay = fresh,
    )

    @Test
    fun the_tailored_program_is_revealed_with_its_real_week_reasons_and_the_freshest_day_as_main() {
        val profile = TrainingGoalProfile.STRENGTH_MUSCLE
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        val source = draft(profile).copy(selectedCatalogId = entry.id)
        val routine = RoutineGenerator.generate(source.routineRequest(GeneratedPlans.modeFor(profile), catalog))
        val program = generatedProgramOf(routine, entry, source)

        val reveal = SetupPlanReveals.forGenerated(entry, program, routine, profile, CatalogLevel.INTERMEDIATE, 60, coverSeed = 0)

        assertEquals(entry.id, reveal.planId)
        assertEquals(entry.displayName, reveal.title)
        assertTrue(reveal.generated)
        assertEquals(SetupPlanReveals.KICKER_TAILORED, reveal.kicker)
        assertEquals("3 días", reveal.daysLabel)
        assertEquals("Intermedio", reveal.levelLabel)
        assertTrue("blurb de 110 caracteres como mucho: «${reveal.blurb}»", reveal.blurb.length <= SetupPlanReveals.BLURB_MAX)
        assertEquals("la semana tipo son los días elegidos", listOf(1, 3, 5), reveal.week.map { it.day })
        assertEquals("la sesión principal cae el día con más energía", listOf(3), reveal.week.filter { it.isMain }.map { it.day })
        assertTrue("cada día dice dónde se entrena", reveal.week.all { it.place == TrainingPlace.GYM })
        assertTrue("3 a 5 razones: ${reveal.reasons}", reveal.reasons.size in 3..5)
        assertEquals(routine.summary.reasons, reveal.reasons)
        assertTrue("ejercicios principales", reveal.mainExercises.isNotEmpty() && reveal.mainExercises.size <= SetupPlanReveals.MAX_MAIN_EXERCISES)
        assertEquals(listOf(SetupPlanReveals.REPEATING_WEEK_LABEL), reveal.blocks.map { it.label })
        assertTrue("minutos de la portada: ${reveal.minutesLabel}", reveal.minutesLabel.matches(Regex("""~\d+ min""")))
        assertNull(reveal.attribution)
    }

    @Test
    fun a_discipline_without_authors_is_labelled_initial_version_with_its_honest_note() {
        val profile = TrainingGoalProfile.CALISTHENICS
        val entry = requireNotNull(PersonalizedPlanCatalog.find(GeneratedPlans.entryIdFor(profile)))
        val source = draft(profile).copy(selectedCatalogId = entry.id)
        val routine = RoutineGenerator.generate(source.routineRequest(GeneratedPlans.modeFor(profile), catalog))
        val reveal = SetupPlanReveals.forGenerated(
            entry, generatedProgramOf(routine, entry, source), routine, profile, CatalogLevel.INTERMEDIATE, 60, coverSeed = 1,
        )
        assertTrue(routine.summary.isInitialVersion)
        assertEquals(SetupPlanReveals.KICKER_INITIAL, reveal.kicker)
        assertTrue(reveal.isInitialVersion)
        assertTrue("la nota de versión inicial viaja al detalle: ${reveal.notes}", reveal.notes.any { "muscle-up" in it })
    }

    @Test
    fun a_catalog_plan_shows_its_blocks_deloads_kicker_and_a_time_note_when_it_runs_long() {
        val entry = requireNotNull(PersonalizedPlanCatalog.find("native:muscle-foundation-v2"))
        val reveal = SetupPlanReveals.forCatalog(entry, sixWeekProgram(), listOf("Encaja con tus 3 días por semana"), TrainingGoalProfile.BODYBUILDING, 45, coverSeed = 2)
        assertEquals(SetupPlanReveals.KICKER_KPKN, reveal.kicker)
        assertEquals("los planes propios se adaptan", SetupPlanReveals.BADGE_ADAPTIVE, reveal.badge)
        assertEquals(
            listOf("Acumulación" to "Semanas 1–5", "Descarga" to "Semana 6"),
            reveal.blocks.map { it.label to it.weeksLabel },
        )
        assertEquals(listOf("Encaja con tus 3 días por semana"), reveal.reasons)
        assertEquals("Press de banca", reveal.mainExercises.single())
        val longest = reveal.week.maxOf { it.minutes }
        assertTrue("con más minutos de los pedidos hay nota: ${reveal.notes}", longest <= 45 || reveal.notes.any { "un poco más de los 45" in it })
    }

    @Test
    fun the_blurb_keeps_whole_sentences_or_cuts_on_a_word() {
        assertEquals("Corto y claro.", SetupPlanReveals.blurbOf("  Corto   y claro. "))
        val two = "Una frase de cuarenta caracteres más o menos. Otra frase que también entra en el límite. " +
            "Y una tercera que ya no cabe en los ciento diez caracteres del límite."
        val blurb = SetupPlanReveals.blurbOf(two)
        assertTrue(blurb.length <= SetupPlanReveals.BLURB_MAX)
        assertTrue("termina en una frase entera: «$blurb»", blurb.endsWith("."))
        val endless = "palabra ".repeat(40).trim()
        val cut = SetupPlanReveals.blurbOf(endless)
        assertTrue(cut.length <= SetupPlanReveals.BLURB_MAX)
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun a_long_single_sentence_is_cut_on_its_last_comma_that_fits_and_closed_with_a_period() {
        // Halterofilia con 4 días (QP, teléfono real): antes salía «…con movilidad, unos…»; ahora pierde el inciso final.
        val weightlifting = "4 días de sentadilla, empuje sobre la cabeza y potencia, sentadilla frontal y tirones, con movilidad, unos 90 min por sesión."
        assertEquals(
            "4 días de sentadilla, empuje sobre la cabeza y potencia, sentadilla frontal y tirones, con movilidad.",
            SetupPlanReveals.blurbOf(weightlifting),
        )
        // «Músculo KPKN» (carrusel de Culturismo): su primera frase de autor pasa de 110 y antes salía «…siempre dejas…».
        val muscle = requireNotNull(PersonalizedPlanCatalog.find("native:muscle-foundation-v2")).summary
        assertEquals(
            "Entrenamiento para ganar músculo con rangos de repeticiones y esfuerzo controlado.",
            SetupPlanReveals.blurbOf(muscle),
        )
        // Sin una pausa que deje un trozo con sentido, se sigue cortando en una palabra con «…».
        val shortClause = "Frase, " + "palabra ".repeat(30).trim()
        val cut = SetupPlanReveals.blurbOf(shortClause)
        assertTrue("«$cut»", cut.endsWith("…") && cut.length <= SetupPlanReveals.BLURB_MAX)
    }

    @Test
    fun no_discipline_one_liner_is_ever_cut_in_the_middle_of_its_description() {
        // La frase de cada disciplina con cualquier número de días, con la plantilla de `RoutineNarrative.oneLiner`.
        val disciplines = listOf(
            RoutineMode.DISCIPLINE_CALISTHENICS,
            RoutineMode.DISCIPLINE_ARMWRESTLING,
            RoutineMode.DISCIPLINE_STRONGMAN,
            RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE,
            RoutineMode.CUSTOM_POWERLIFTING,
        )
        for (mode in disciplines) {
            for (days in 1..7) {
                val line = "${SetupPlanReveals.daysLabel(days)} de ${DisciplineWeeks.describe(mode, days)}, unos 90 min por sesión."
                val blurb = SetupPlanReveals.blurbOf(line)
                assertTrue("$mode $days días: «$blurb» ($line)", blurb.length <= SetupPlanReveals.BLURB_MAX)
                assertTrue("$mode $days días: «$blurb» queda cortada con «…»", !blurb.endsWith("…"))
            }
        }
    }

    @Test
    fun the_author_of_a_method_is_the_kicker() {
        val phul = requireNotNull(PersonalizedPlanCatalog.find("original:phul-ms-2021-r1"))
        assertTrue("«${SetupPlanReveals.kickerOf(phul)}»", SetupPlanReveals.kickerOf(phul).contains("Campbell"))
        assertEquals(SetupPlanReveals.KICKER_KPKN, SetupPlanReveals.kickerOf(requireNotNull(PersonalizedPlanCatalog.find("template:power-12-3"))))
    }

    /** Seis semanas (cinco de acumulación y una de descarga) con una sesión de banca por día de entreno. */
    private fun sixWeekProgram(): Program {
        fun session(week: Int, day: Int) = Session(
            id = "s$week-$day",
            name = "Día $day",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(
                    id = "e$week-$day",
                    name = "Press de banca",
                    sets = (1..4).map { ExerciseSet(id = "x$week-$day-$it", targetReps = 8) },
                    restTime = 120,
                ),
            ),
        )
        val weeks = (1..6).map { week ->
            ProgramWeek(
                id = "w$week",
                name = "Semana $week",
                sessions = listOf(1, 3, 5).map { session(week, it) },
                executionKind = if (week == 6) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
            )
        }
        return Program(
            id = "p",
            name = "Plan",
            macrocycles = listOf(
                Macrocycle(
                    "m",
                    "Macro",
                    blocks = listOf(Block("b", "Bloque", mesocycles = listOf(Mesocycle("me", "Meso", goal = MesocycleGoal.ACCUMULATION, weeks = weeks)))),
                ),
            ),
        )
    }
}
