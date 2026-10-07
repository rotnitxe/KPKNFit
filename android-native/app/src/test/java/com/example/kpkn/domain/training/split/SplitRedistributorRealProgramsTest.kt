package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CoverageFixtures
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.split.SplitTestSupport.exercisesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * El redistribuidor sobre programas REALES del motor: planes propios generados por el personalizador nativo (varias
 * semanas, partes, cardio) y un plan de autor (PHUL) que solo se mueve con permiso explícito.
 */
class SplitRedistributorRealProgramsTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        /** Gimnasio completo con aparatos y soportes confirmados. */
        private val FULL_GYM = CoverageFixtures.legacyFixtures().first { it.id == "E6" }.availability
    }

    private val generator by lazy { CoverageFixtures.personalizer() }
    private val resolver get() = SplitTestSupport.resolver
    private val structural = RedistributionOptions(completeApproach = false)

    private class SequentialIds : IdProvider {
        private var next = 0
        override fun newId(): String = "real_${++next}"
    }

    private fun nativeProgram(kind: NativeProfileKind, days: Int): Program {
        val result = generator.personalize(
            programId = "d5-${kind.sourceId}-$days",
            input = PersonalizerInput(
                catalogEntryId = kind.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = days,
                weekdays = CoverageFixtures.weekdays(days),
                equipment = emptySet(),
                level = CatalogLevel.INTERMEDIATE,
                availableMinutes = 90,
                cardio = if (kind == NativeProfileKind.COMPLETE_ATHLETE) CardioPreference(CardioType.WALK, 20) else null,
            ),
            options = TrainingOptions(availability = FULL_GYM),
        )
        return requireNotNull(result.program) { "${result.report.reasonCode}: ${result.report.limitations}" }
    }

    private fun phul(): Program = PlanMaterializer.materialize(
        program = Program(id = "phul-test", name = "PHUL", startDay = 1),
        recipe = AuthoredPhulPhatRecipes.phulOriginal,
        metadata = CatalogCompositionTestSupport.metadata,
        idProvider = SequentialIds(),
        strict = false,
    )

    private fun redistribute(
        program: Program,
        splitId: String,
        weekdays: List<Int>,
        propagate: Boolean = false,
        allowAuthored: Boolean = false,
        options: RedistributionOptions = structural.copy(propagateToOtherWeeks = propagate),
    ): RedistributionResult = SplitRedistributor.redistribute(
        program = program,
        split = SPLIT_TEMPLATES.first { it.id == splitId },
        weekdays = weekdays,
        resolver = resolver,
        allowAuthoredRecipes = allowAuthored,
        options = options,
    )

    private fun effectiveSessions(program: Program, weekIndex: Int) =
        ProgramWeeks.effective(program)[weekIndex].week.sessions

    // ─── Planes propios ───────────────────────────────────────────────────────────────────────────

    @Test
    fun a_native_muscle_plan_keeps_every_exercise_and_the_volume_in_every_week() {
        val original = nativeProgram(NativeProfileKind.MUSCLE, 4)
        val weekdays = listOf(1, 2, 4, 5)
        val result = redistribute(original, "push_pull_x4", weekdays, propagate = true)
        assertTrue(result.reason, result.compatible)

        val weeksBefore = ProgramWeeks.effective(original)
        val weeksAfter = ProgramWeeks.effective(result.program)
        assertEquals(weeksBefore.size, weeksAfter.size)
        weeksBefore.indices.forEach { index ->
            val before = weeksBefore[index].week.sessions
            val after = weeksAfter[index].week.sessions
            assertEquals("semana $index", before.flatMap { it.allExercises() }.map { it.id }.sorted(), after.flatMap { it.allExercises() }.map { it.id }.sorted())
            assertEquals(weekdays, after.map { it.dayOfWeek })
            assertEquals(
                "volumen de la semana $index",
                MuscleVolume.of(before, resolver),
                MuscleVolume.of(after, resolver),
            )
        }
        assertEquals(0.0, result.report.maxVolumeDeviation, 1e-9)
        assertEquals("push_pull_x4", result.program.selectedSplitId)
        assertEquals(setOf(1, 2, 4, 5), result.program.schedulePlan?.trainingDays)
        assertTrue(ProgramExecutionContract.validate(result.program).size <= ProgramExecutionContract.validate(original).size)
        assertTrue(result.notes.any { it.contains("cambia su estructura original") })
        assertTrue(result.notes.any { it.contains("Se aplicó a las ${weeksAfter.size} semanas") })
    }

    @Test
    fun a_three_day_native_plan_that_alternates_its_weeks_is_adapted_in_all_of_them() {
        val original = nativeProgram(NativeProfileKind.MUSCLE, 3)
        val weekdays = listOf(1, 2, 4, 5)
        val result = redistribute(original, "ul_x4", weekdays, propagate = true)
        assertTrue(result.reason, result.compatible)

        val before = ProgramWeeks.effective(original)
        val after = ProgramWeeks.effective(result.program)
        assertEquals(before.size, after.size)
        before.indices.forEach { index ->
            val ids = before[index].week.sessions.flatMap { it.allExercises() }.map { it.id }.sorted()
            assertEquals("semana $index", ids, after[index].week.sessions.flatMap { it.allExercises() }.map { it.id }.sorted())
            assertEquals(weekdays, after[index].week.sessions.map { it.dayOfWeek })
        }
        assertEquals("ul_x4", result.program.selectedSplitId)
        assertTrue(result.notes.any { it.contains("Se aplicó a las ${after.size} semanas") })
        assertTrue(result.notes.any { it.contains("su reparto se calculó por separado") })

        // El mismo ejercicio repetido en varias sesiones no se amontona en un solo día.
        val firstWeek = after[0].week.sessions
        val perConfiguration = firstWeek.flatMap { day -> day.allExercises().map { day.id to it.catalogConfigurationId } }
            .groupBy { it.second }
        perConfiguration.forEach { (configuration, placements) ->
            if (placements.size < 2) return@forEach
            val maxInOneDay = placements.groupingBy { it.first }.eachCount().values.max()
            assertTrue("$configuration: $maxInOneDay veces en un día de ${placements.size}", maxInOneDay <= (placements.size + 1) / 2)
        }
    }

    @Test
    fun a_native_plan_week_with_sealed_durations_is_resealed_with_the_real_minutes() {
        val original = nativeProgram(NativeProfileKind.MUSCLE, 3)
        assertTrue("el plan propio sella la duración de cada sesión", effectiveSessions(original, 0).all { it.targetDurationMinutes != null })
        val result = redistribute(original, "ul_fb_x3", listOf(1, 3, 5))
        assertTrue(result.reason, result.compatible)
        val sessions = effectiveSessions(result.program, 0)
        assertEquals(result.report.days.map { it.minutes }, sessions.map { it.targetDurationMinutes })
    }

    @Test
    fun the_new_sessions_of_a_native_plan_survive_rebuilding_the_week_from_its_recipe() {
        val original = nativeProgram(NativeProfileKind.MUSCLE, 4)
        val result = redistribute(original, "ant_post_x4", listOf(1, 2, 4, 5))
        assertTrue(result.reason, result.compatible)
        val week = ProgramWeeks.effective(result.program)[0].week
        assertTrue(
            "cada sesión nueva queda marcada como personalizada",
            week.sessions.all { session -> result.program.manualSessionOverrides.any { it.sessionId == session.id } },
        )
        val rebuilt = PlanMaterializer.rematerializeWeek(result.program, week.id, metadata = CatalogCompositionTestSupport.metadata)
        val after = ProgramWeeks.effective(rebuilt)[0].week
        fun layout(sessions: List<com.example.kpkn.data.models.Session>) =
            sessions.associate { it.dayOfWeek to it.allExercises().map { exercise -> exercise.id } }
        assertEquals(layout(week.sessions), layout(after.sessions))
    }

    @Test
    fun without_the_protection_a_rebuild_goes_back_to_the_recipe() {
        val original = nativeProgram(NativeProfileKind.MUSCLE, 4)
        val result = redistribute(original, "ant_post_x4", listOf(1, 2, 4, 5), options = structural.copy(protectFromRematerialization = false))
        assertTrue(result.compatible)
        assertTrue(result.program.manualSessionOverrides.isEmpty())
    }

    @Test
    fun a_complete_athlete_plan_moves_its_cardio_blocks_intact() {
        val original = nativeProgram(NativeProfileKind.COMPLETE_ATHLETE, 4)
        val before = effectiveSessions(original, 0)
        val cardioBefore = before.flatMap { it.parts }.filter { it.isCardioGroup }
        assertTrue("el atleta completo trae cardio", cardioBefore.isNotEmpty())

        val result = redistribute(original, "ul_x4", listOf(1, 2, 4, 5))
        assertTrue(result.reason, result.compatible)
        val cardioAfter = effectiveSessions(result.program, 0).flatMap { it.parts }.filter { it.isCardioGroup }
        assertEquals(cardioBefore.size, cardioAfter.size)
        assertEquals(cardioBefore.map { it.exercises }.toSet(), cardioAfter.map { it.exercises }.toSet())
        assertEquals(cardioBefore.sumOf { it.targetDurationMinutes ?: 0 }, cardioAfter.sumOf { it.targetDurationMinutes ?: 0 })
        assertEquals(0.0, result.report.maxVolumeDeviation, 1e-9)
        // El cardio queda al final de su sesión, o al principio si esa sesión lo pedía así.
        effectiveSessions(result.program, 0).forEach { session ->
            val cardioIndexes = session.parts.withIndex().filter { it.value.isCardioGroup }.map { it.index }
            if (cardioIndexes.isEmpty()) return@forEach
            if (session.cardioFirst) {
                assertEquals((0 until cardioIndexes.size).toList(), cardioIndexes)
            } else {
                assertEquals((session.parts.size - cardioIndexes.size until session.parts.size).toList(), cardioIndexes)
            }
        }
    }

    @Test
    fun every_published_split_with_three_and_four_days_keeps_the_invariants_on_native_plans() {
        val failures = mutableListOf<String>()
        listOf(3, 4).forEach { days ->
            val original = nativeProgram(NativeProfileKind.MUSCLE, days)
            val before = ProgramWeeks.effective(original)[0].week.sessions
            val ids = before.flatMap { it.allExercises() }.map { it.id }.sorted()
            SplitCatalogRules.compatible(days, null).forEach { split ->
                val weekdays = SplitTestSupport.spreadDays(days)
                val result = SplitRedistributor.redistribute(original, split, weekdays, resolver, options = structural)
                if (!result.compatible) {
                    if (!result.reason.orEmpty().startsWith("Tu programa no tiene")) failures += "${split.id}: ${result.reason}"
                    return@forEach
                }
                val after = ProgramWeeks.effective(result.program)[0].week.sessions
                if (after.flatMap { it.allExercises() }.map { it.id }.sorted() != ids) failures += "${split.id}: ejercicios distintos"
                if (after.map { it.dayOfWeek } != weekdays) failures += "${split.id}: días ${after.map { it.dayOfWeek }}"
                if (result.report.maxVolumeDeviation > 1e-9) failures += "${split.id}: volumen ${result.report.maxVolumeDeviation}"
                if (after.any { it.allExercises().isEmpty() }) failures += "${split.id}: día vacío"
                if (ProgramExecutionContract.validate(result.program).isNotEmpty()) failures += "${split.id}: no ejecutable"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun every_real_program_keeps_the_invariants_for_every_compatible_split() {
        val programs = mutableListOf<Triple<String, Program, Boolean>>()
        listOf(
            NativeProfileKind.MUSCLE to listOf(3, 4, 5, 6),
            NativeProfileKind.POWERBUILDING to listOf(3, 4, 5, 6),
            NativeProfileKind.STRENGTH to listOf(3, 4),
            NativeProfileKind.COMPLETE_ATHLETE to listOf(4, 5),
        ).forEach { (kind, daysList) ->
            daysList.forEach { days ->
                runCatching { nativeProgram(kind, days) }.getOrNull()?.let { programs += Triple("nativo ${kind.sourceId} $days días", it, false) }
            }
        }
        listOf(
            "PHUL original" to AuthoredPhulPhatRecipes.phulOriginal,
            "PHUL adaptado" to AuthoredPhulPhatRecipes.phulAdapted,
            "PHAT original" to AuthoredPhulPhatRecipes.phatOriginal,
            "PHAT adaptado" to AuthoredPhulPhatRecipes.phatAdapted,
        ).forEach { (name, recipe) ->
            val program = runCatching {
                PlanMaterializer.materialize(
                    program = Program(id = "real-" + name.hashCode(), name = name, startDay = 1),
                    recipe = recipe,
                    metadata = CatalogCompositionTestSupport.metadata,
                    idProvider = SequentialIds(),
                    strict = false,
                )
            }.getOrNull()
            if (program != null) programs += Triple(name, program, true)
        }
        assertTrue("se generaron programas reales: ${programs.map { it.first }}", programs.size >= 8)

        val failures = mutableListOf<String>()
        val summary = StringBuilder()
        var applied = 0
        programs.forEach { (name, program, authored) ->
            val week = ProgramWeeks.effective(program)[0].week
            val ids = week.sessions.flatMap { it.allExercises() }.map { it.id }.sorted()
            val originalById = week.sessions.flatMap { it.allExercises() }.associateBy { it.id }
            (2..6).forEach { days ->
                val weekdays = SplitTestSupport.spreadDays(days)
                SplitCatalogRules.compatible(days, null).forEach { split ->
                    val label = "$name → ${split.id}"
                    val result = SplitRedistributor.redistribute(
                        program, split, weekdays, resolver, allowAuthoredRecipes = authored, options = structural,
                    )
                    if (!result.compatible) {
                        if (!result.reason.orEmpty().startsWith("Tu programa no tiene") &&
                            !result.reason.orEmpty().startsWith("Hay menos ejercicios")
                        ) {
                            failures += "$label: rechazo inesperado «${result.reason}»"
                        }
                        return@forEach
                    }
                    applied++
                    val after = ProgramWeeks.effective(result.program)[0].week.sessions
                    val exercises = after.flatMap { it.allExercises() }
                    if (exercises.map { it.id }.sorted() != ids) failures += "$label: ejercicios distintos"
                    if (exercises.any { it != originalById[it.id] }) failures += "$label: un ejercicio cambió"
                    if (after.map { it.dayOfWeek } != weekdays) failures += "$label: días ${after.map { it.dayOfWeek }}"
                    if (result.report.maxVolumeDeviation > 1e-9) failures += "$label: volumen ${result.report.maxVolumeDeviation}"
                    if (after.any { it.allExercises().isEmpty() }) failures += "$label: día vacío"
                    if (ProgramExecutionContract.validate(result.program).size > ProgramExecutionContract.validate(program).size) {
                        failures += "$label: deja de ser ejecutable"
                    }
                    if (days == after.size && days == 4) {
                        summary.append(
                            String.format(
                                java.util.Locale.ROOT, "  %-26s %-22s ejercicios %-18s minutos %s%n",
                                name, split.id, after.map { it.allExercises().size }, result.report.days.map { it.minutes },
                            ),
                        )
                    }
                }
            }
        }
        println("Programas reales: ${programs.size}, repartos aplicados: $applied" + System.lineSeparator() + summary)
        assertEquals(emptyList<String>(), failures)
        assertTrue(applied > 100)
    }

    // ─── Planes de autor ──────────────────────────────────────────────────────────────────────────

    @Test
    fun an_authored_plan_is_refused_by_default_and_moved_only_with_explicit_permission() {
        val original = phul()
        val sessions = effectiveSessions(original, 0)
        assertEquals(4, sessions.size)

        val refused = redistribute(original, "ant_post_x4", listOf(1, 2, 4, 5))
        assertFalse(refused.compatible)
        assertEquals(SplitRedistributor.AUTHORED_REASON, refused.reason)
        assertEquals(original, refused.program)

        val allowed = redistribute(original, "ant_post_x4", listOf(1, 2, 4, 5), allowAuthored = true)
        assertTrue(allowed.reason, allowed.compatible)
        val after = effectiveSessions(allowed.program, 0)
        assertEquals(sessions.flatMap { it.allExercises() }.map { it.id }.sorted(), after.flatMap { it.allExercises() }.map { it.id }.sorted())
        // Lo que el autor prescribió en cada ejercicio no cambia al moverlo.
        val byId = sessions.flatMap { it.allExercises() }.associateBy { it.id }
        after.flatMap { it.allExercises() }.forEach { assertEquals(byId.getValue(it.id), it) }
        assertEquals(0.0, allowed.report.maxVolumeDeviation, 1e-9)
        assertTrue(allowed.notes.any { it.contains("PHUL") && it.contains("cambia su estructura original") })
        assertNotNull(allowed.program.manualSessionOverrides.firstOrNull())
    }

    @Test
    fun an_authored_plan_with_a_provenance_is_also_recognized() {
        val original = phul().copy(
            sourceRecipe = null,
            planProvenance = com.example.kpkn.data.protocols.PlanProvenance(
                planId = "original:phul-ms-2021-r1",
                category = com.example.kpkn.data.protocols.PlanProvenanceClass.ORIGINAL,
            ),
        )
        val refused = redistribute(original, "ant_post_x4", listOf(1, 2, 4, 5))
        assertFalse(refused.compatible)
        assertEquals(SplitRedistributor.AUTHORED_REASON, refused.reason)
    }

    @Test
    fun native_plans_are_not_taken_for_authored_plans() {
        val native = nativeProgram(NativeProfileKind.MUSCLE, 4)
        assertFalse(AuthoredPlans.hasFixedRecipe(native))
        assertTrue(AuthoredPlans.hasFixedRecipe(phul()))
        assertFalse(AuthoredPlans.hasFixedRecipe(SplitTestSupport.ppl()))
        assertTrue(exercisesOf(native).isNotEmpty())
    }
}
