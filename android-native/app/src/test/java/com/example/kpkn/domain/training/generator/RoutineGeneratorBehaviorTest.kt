package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoutineGeneratorBehaviorTest {

    private val s = RoutineTestSupport
    private val entries by lazy { GeneratorCatalog.of(s.catalog) }

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun exercisesOf(program: Program): List<Exercise> = sessionsOf(program).flatMap { it.allExercises() }

    private fun idsOf(program: Program): Set<String> = exercisesOf(program).mapNotNull { it.catalogConfigurationId }.toSet()

    private fun directSets(routine: GeneratedRoutine, muscle: String): Double = routine.report.weeklyDirectSets[muscle] ?: 0.0

    // ─── Entradas imposibles ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun only_impossible_inputs_throw_the_generation_exception() {
        val base = s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60)
        listOf(
            base.copy(weekdays = emptyList(), weekStartDay = 1),
            base.copy(weekdays = listOf(1, 8)),
            base.copy(weekdays = listOf(1, 1, 3)),
            base.copy(targetMinutes = 19),
            base.copy(targetMinutes = 181),
            base.copy(weekStartDay = 0),
        ).forEachIndexed { index, request ->
            try {
                RoutineGenerator.generate(request)
                fail("la entrada imposible #$index debía lanzar RoutineGenerationException")
            } catch (expected: RoutineGenerationException) {
                assertTrue(expected.message.orEmpty().isNotBlank())
            }
        }
        // Los extremos válidos nunca fallan.
        val extremes = listOf(
            s.request(s.bodyOnly, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.NOVICE, 7, 20),
            s.request(s.bodyOnly, RoutineMode.GENERAL_HYBRID, RoutineLevel.ADVANCED, 1, 180),
            s.request(s.gym, RoutineMode.GENERAL_FUNCTIONAL, RoutineLevel.RETURNING, 7, 180),
        )
        extremes.forEach { assertNotNull(RoutineGenerator.generate(it)) }
    }

    // ─── Determinismo y variantes ──────────────────────────────────────────────────────────────────────────

    @Test
    fun the_same_request_gives_the_same_routine_and_ids() {
        listOf(s.gym, s.bodyOnly, s.gymAndHome).forEach { profile ->
            s.generalModes.forEach { mode ->
                val request = s.request(profile, mode, RoutineLevel.INTERMEDIATE, 4, 60, seed = 3, freshest = 4)
                val first = RoutineGenerator.generate(request)
                val second = RoutineGenerator.generate(request)
                assertEquals("${profile.id} ${mode.name}", first, second)
                assertTrue(first.program.id.startsWith("gen-"))
            }
        }
    }

    @Test
    fun another_seed_picks_another_allowed_variant_when_the_pool_has_alternatives() {
        val programs = (0..3).map {
            RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60, seed = it)).program
        }
        assertTrue("las cuatro semillas dieron la misma semana", programs.map { idsOf(it) }.toSet().size >= 2)
        // Cada variante es una semana válida con el mismo reparto.
        programs.forEach { program -> assertEquals(listOf("Torso A", "Pierna A", "Torso B", "Pierna B").toSet(), sessionsOf(program).map { it.name }.toSet()) }
    }

    // ─── Día fresco ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_hardest_session_lands_on_the_freshest_day_or_the_next_training_day() {
        val days = listOf(1, 2, 4, 5)
        mapOf(
            4 to 4,       // se entrena ese día
            3 to 4,       // no se entrena: el primer día posterior
            7 to 1,       // domingo: el lunes siguiente (circular)
            5 to 5,
        ).forEach { (freshest, expected) ->
            val routine = RoutineGenerator.generate(
                s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60, freshest = freshest, weekdays = days),
            )
            assertEquals("fresco $freshest", expected, routine.summary.mainSessionDay)
            val main = sessionsOf(routine.program).single { it.isMainSession }
            assertEquals(expected, main.dayOfWeek)
            // La principal es una sesión de pierna con sentadilla o bisagra pesada.
            assertTrue(main.name.startsWith("Pierna"))
        }
    }

    @Test
    fun the_week_starts_on_the_chosen_start_day_and_keeps_the_training_days() {
        val routine = RoutineGenerator.generate(
            s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 60, weekdays = listOf(5, 1, 3), weekStart = 5),
        )
        assertEquals(listOf(5, 1, 3), sessionsOf(routine.program).map { it.dayOfWeek })
        assertEquals(5, routine.program.schedulePlan?.weekStartDay)
        assertEquals(setOf(1, 3, 5), routine.program.schedulePlan?.trainingDays)
    }

    // ─── Prioridades ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_priority_muscle_never_loses_volume_and_gains_it_when_it_has_room() {
        val cases = mapOf(
            MuscleSymbol.CHEST to "Pectorales",
            MuscleSymbol.BICEPS to "Bíceps",
            MuscleSymbol.TRICEPS to "Tríceps",
            MuscleSymbol.SHOULDERS to "Deltoides",
            MuscleSymbol.CALVES to "Pantorrillas",
            MuscleSymbol.QUADS to "Cuádriceps",
            MuscleSymbol.ABS to "Abdomen",
            MuscleSymbol.TRAPS to "Trapecio",
            MuscleSymbol.BACK to "Dorsales",
        )
        val base = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60))
        cases.forEach { (symbol, muscle) ->
            val boosted = RoutineGenerator.generate(
                s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60, priorities = listOf(symbol)),
            )
            val before = directSets(base, muscle)
            val after = directSets(boosted, muscle)
            val target = base.report.weeklyDirectTargets.getValue(muscle)
            assertTrue("$muscle bajó de $before a $after con prioridad", after >= before)
            // Con margen hasta su objetivo semanal (MAV) la prioridad tiene que notarse.
            if (before <= target - 2) assertTrue("$muscle no subió ($before → $after) con margen hasta su objetivo $target", after > before)
        }
    }

    @Test
    fun a_priority_isolation_muscle_moves_earlier_in_its_sessions() {
        fun firstIndex(session: Session, muscle: String): Int? =
            session.exercises.indexOfFirst { (entries.entry(it.catalogConfigurationId!!)!!.contributions[muscle]?.direct ?: 0.0) > 0.0 }
                .takeIf { it >= 0 }
        mapOf(MuscleSymbol.BICEPS to "Bíceps", MuscleSymbol.CALVES to "Pantorrillas").forEach { (symbol, muscle) ->
            val base = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 5, 90))
            val boosted = RoutineGenerator.generate(
                s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 5, 90, priorities = listOf(symbol)),
            )
            var strictlyEarlier = 0
            sessionsOf(base.program).indices.forEach { index ->
                val before = firstIndex(sessionsOf(base.program)[index], muscle) ?: return@forEach
                val after = firstIndex(sessionsOf(boosted.program)[index], muscle) ?: return@forEach
                assertTrue("$muscle en la sesión $index: posición $before → $after", after <= before)
                if (after < before) strictlyEarlier++
            }
            assertTrue("$muscle no se adelantó en ninguna sesión", strictlyEarlier >= 1)
        }
    }

    @Test
    fun priorities_never_pass_the_weekly_ceiling() {
        val routine = RoutineGenerator.generate(
            s.request(
                s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.ADVANCED, 6, 120,
                priorities = listOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS),
            ),
        )
        routine.report.weeklyDirectSets.forEach { (muscle, sets) ->
            val ceiling = routine.report.weeklyDirectCeilings.getValue(muscle)
            assertTrue("$muscle $sets > $ceiling", sets <= ceiling + 1e-6)
        }
    }

    // ─── Capacidades y peldaños ────────────────────────────────────────────────────────────────────────────

    private fun pushUpIds(routine: GeneratedRoutine): Set<String> = idsOf(routine.program).filter {
        it.startsWith("push_up__") || it == "knee_push_up__default" || it == "diamond_push_up__default" || it == "archer_push_up__default"
    }.toSet()

    @Test
    fun push_up_capability_picks_the_ladder_rung() {
        fun rungs(level: RoutineLevel, capability: CapabilityLevel?, profile: MaterialProfile = s.bodyOnly): Set<String> {
            val capabilities = if (capability == null) emptyMap() else mapOf(CapabilitySkill.PUSH_UP to capability)
            return pushUpIds(
                RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_FUNCTIONAL, level, 3, 45, capabilities = capabilities)),
            )
        }
        assertTrue(rungs(RoutineLevel.INTERMEDIATE, CapabilityLevel.NONE).all { it == "push_up__hands_elevated" || it == "knee_push_up__default" })
        assertTrue("SOME → estándar", "push_up__flat" in rungs(RoutineLevel.INTERMEDIATE, CapabilityLevel.SOME))
        // Lote BW-1: el tramo difícil suma el diamante y el arquero (sin apoyo para los pies parte del diamante) y la estándar lenta.
        val manyWithoutSupport = rungs(RoutineLevel.INTERMEDIATE, CapabilityLevel.MANY)
        assertTrue("MANY sin apoyo → diamante o estándar lento: $manyWithoutSupport", "diamond_push_up__default" in manyWithoutSupport)
        assertTrue(manyWithoutSupport.all { it == "diamond_push_up__default" || it == "push_up__flat" || it == "archer_push_up__default" })
        assertTrue("MANY con parque → pies elevados", "push_up__feet_elevated" in rungs(RoutineLevel.INTERMEDIATE, CapabilityLevel.MANY, s.park))
        // El novato nunca arranca en la flexión estándar (dificultad 6,0), aunque diga que le sale.
        assertFalse("push_up__flat" in rungs(RoutineLevel.NOVICE, CapabilityLevel.SOME))
        assertTrue(rungs(RoutineLevel.NOVICE, null).all { it == "push_up__hands_elevated" || it == "knee_push_up__default" })
    }

    @Test
    fun pull_up_capability_picks_regressions_or_harder_variants_in_the_park() {
        fun pulls(capability: CapabilityLevel): Set<String> {
            val routine = RoutineGenerator.generate(
                s.request(s.park, RoutineMode.GENERAL_FUNCTIONAL, RoutineLevel.INTERMEDIATE, 3, 45, capabilities = mapOf(CapabilitySkill.PULL_UP to capability)),
            )
            return idsOf(routine.program).filter { it.startsWith("pull_up__") || it.startsWith("back_dominadas") || it.startsWith("forearms_suspension") }.toSet()
        }
        assertTrue(pulls(CapabilityLevel.NONE).all { it.startsWith("back_dominadas") || it.startsWith("forearms_suspension") })
        assertTrue(pulls(CapabilityLevel.SOME).any { it == "pull_up__supinated__medium" || it == "pull_up__pronated__medium" })
        assertTrue(pulls(CapabilityLevel.MANY).any { it == "pull_up__pronated__wide" || it == "pull_up__neutral__wide" || it == "pull_up__pronated__medium" })
    }

    @Test
    fun a_novice_only_gets_hard_exercises_when_they_are_curated_basics() {
        val basics = MovementPools.byPattern.values.flatten().flatMap { it.entries }.filter { it.basic }.map { it.id }.toSet()
        listOf(s.gym, s.homeBarbell, s.homeDumbbellsBand, s.park, s.bodyOnly).forEach { profile ->
            val routine = RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.NOVICE, 4, 60))
            idsOf(routine.program).forEach { id ->
                val difficulty = entries.entry(id)!!.difficulty
                assertTrue("${profile.id}: el novato recibe $id (dificultad $difficulty) sin ser un básico", difficulty <= 5.2 || id in basics)
            }
        }
    }

    // ─── Marcas y cargas ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun marks_turn_into_percentages_of_the_matching_basics_and_the_rest_is_pending() {
        val routine = RoutineGenerator.generate(
            s.request(
                s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 3, 90,
                marks = mapOf(LiftMark.SQUAT to 120.0, LiftMark.BENCH to 90.0, LiftMark.DEADLIFT to 150.0),
            ),
        )
        val exercises = exercisesOf(routine.program)
        val marked = exercises.filter { it.reference1RM != null }
        assertTrue("debe haber básicos con marca", marked.isNotEmpty())
        marked.forEach { exercise ->
            exercise.sets.forEach { set ->
                val percent = set.targetPercentageRM
                assertNotNull(percent)
                assertTrue("${exercise.name}: $percent %", percent!! in 50.0..95.0)
                assertEquals(0.0, (set.weight!! / 2.5) - Math.round(set.weight!! / 2.5), 1e-9)
            }
        }
        val pending = exercises.filter { it.cardioDetails == null && it.reference1RM == null && it.loadReference != null }
        assertTrue("sin marca la carga queda pendiente", pending.isNotEmpty() && pending.all { it.loadReference!!.state == PlanLoadReferenceState.PENDING })
    }

    // ─── Lugares y material ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun a_home_day_never_uses_gym_equipment() {
        val request = s.request(s.gymAndHome, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60)
        val routine = RoutineGenerator.generate(request)
        routine.program.let { sessionsOf(it) }.forEachIndexed { index, session ->
            val place = routine.report.sessions[index].place
            val allowed = if (place == TrainingPlace.HOME) setOf("dumbbells", "band", "bodyweight") else null
            session.allExercises().filter { it.cardioDetails == null }.forEach { exercise ->
                val equipment = entries.entry(exercise.catalogConfigurationId!!)!!.equipmentId
                if (allowed != null) assertTrue("${session.name} (casa) usa $equipment", equipment in allowed)
            }
        }
        assertTrue(routine.report.sessions.any { it.place == TrainingPlace.GYM } && routine.report.sessions.any { it.place == TrainingPlace.HOME })
    }

    // ─── Híbrido y funcional ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun hybrid_keeps_the_declared_cardio_minutes_and_never_trims_them() {
        val routine = RoutineGenerator.generate(
            s.request(
                s.gym, RoutineMode.GENERAL_HYBRID, RoutineLevel.INTERMEDIATE, 4, 60,
                cardio = CardioPreference(CardioType.BIKE_STATIONARY, 20, CardioIntensity.MEDIA),
            ),
        )
        val mixed = routine.report.sessions.filter { it.kind == RoutineSessionKind.MIXED && it.strengthExerciseCount >= 3 }
        assertTrue("debe haber sesiones mixtas", mixed.isNotEmpty())
        sessionsOf(routine.program).filter { session -> session.name.startsWith("Mixta") }.forEach { session ->
            val cardio = session.allExercises().filter { it.cardioDetails != null }.map { it.cardioDetails!! }
            assertTrue(cardio.any { it.targetDurationSeconds == 20 * 60 && it.type == CardioType.BIKE_STATIONARY })
        }
        assertTrue(routine.report.sessions.any { it.kind == RoutineSessionKind.CARDIO })
    }

    @Test
    fun functional_sessions_carry_mobility_and_cardio_when_time_allows() {
        val routine = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_FUNCTIONAL, RoutineLevel.INTERMEDIATE, 4, 60))
        routine.report.sessions.forEach { report ->
            assertTrue("${report.title}: movilidad", report.hasMobility)
            assertTrue("${report.title}: cardio", report.hasCardio)
        }
        assertTrue(sessionsOf(routine.program).all { it.supersetGroups.isNotEmpty() })
    }

    @Test
    fun seven_days_include_a_mobility_and_light_cardio_day() {
        val routine = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 7, 60))
        val recovery = routine.report.sessions.single { it.kind == RoutineSessionKind.MOBILITY }
        assertTrue(recovery.hasMobility && recovery.hasCardio)
        assertEquals(6, routine.report.sessions.count { it.kind == RoutineSessionKind.STRENGTH })
    }

    // ─── Notas de límites ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun missing_patterns_come_with_an_actionable_note() {
        val bodyOnly = RoutineGenerator.generate(s.request(s.bodyOnly, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60))
        assertTrue(RoutinePattern.VERTICAL_PULL in bodyOnly.report.patternsMissing && RoutinePattern.HORIZONTAL_PULL in bodyOnly.report.patternsMissing)
        assertTrue(bodyOnly.notes.any { it.contains("tracción vertical") && it.contains("añadir") })
        assertTrue(bodyOnly.notes.any { it.contains("remo") && it.contains("añadir") })
        assertTrue(bodyOnly.notes.any { it.contains("bisagra") })
        val gym = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 4, 60))
        assertTrue("el gimnasio completo no deja huecos: ${gym.report.patternsMissing}", gym.report.patternsMissing.isEmpty())
    }

    @Test
    fun a_bodyweight_week_never_leaves_a_strength_session_with_a_single_exercise() {
        s.generalModes.forEach { mode ->
            listOf(3, 4, 5, 6).forEach { days ->
                val routine = RoutineGenerator.generate(s.request(s.bodyOnly, mode, RoutineLevel.INTERMEDIATE, days, 60))
                routine.report.sessions.filter { it.kind == RoutineSessionKind.STRENGTH || it.kind == RoutineSessionKind.MIXED }.forEach {
                    assertTrue("${mode.name} ${days}d: ${it.title} solo tiene ${it.strengthExerciseCount} ejercicio(s) de fuerza", it.strengthExerciseCount >= 3)
                }
            }
        }
    }

    @Test
    fun a_session_uses_at_most_one_exercise_of_each_bodyweight_ladder() {
        listOf(s.bodyOnly, s.park).forEach { profile ->
            s.generalModes.forEach { mode ->
                val routine = RoutineGenerator.generate(s.request(profile, mode, RoutineLevel.INTERMEDIATE, 5, 60))
                sessionsOf(routine.program).forEach { session ->
                    val ids = session.allExercises().mapNotNull { it.catalogConfigurationId }
                    // Solo las escaleras con ids propios: la sentadilla, el puente y la potencia comparten ids con otras.
                    listOf(
                        BodyweightLadders.pushUp, BodyweightLadders.verticalPush, BodyweightLadders.pullUp, BodyweightLadders.row,
                        BodyweightLadders.dips, BodyweightLadders.singleLeg,
                    ).forEach { ladder ->
                        // El colgado de barra también es del grupo de agarre (patrón GRIP), no solo de la escalera de dominadas.
                        val fromLadder = ids.filter { it in ladder.allIds && it != "forearms_suspension_isometrica_barra_fija__default" }
                        assertTrue("${profile.id} ${mode.name} ${session.name}: varios ejercicios de la escalera ${ladder.pattern}: $fromLadder", fromLadder.size <= 1)
                    }
                }
            }
        }
    }

    @Test
    fun long_cardio_is_split_into_sensible_blocks() {
        val routine = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_HYBRID, RoutineLevel.ADVANCED, 3, 150))
        val cardio = sessionsOf(routine.program).flatMap { session -> session.allExercises().filter { it.cardioDetails != null } }
        assertTrue("debe haber cardio", cardio.isNotEmpty())
        cardio.forEach { exercise ->
            val details = exercise.cardioDetails!!
            val minutes = details.effectiveDurationSeconds() / 60
            if (details.intervalBlocks.isNotEmpty()) assertTrue("intervalos de $minutes min", minutes <= 40)
            else assertTrue("bloque continuo de $minutes min", minutes <= 75)
        }
        val powerDay = sessionsOf(routine.program).single { it.name.startsWith("Cardio y potencia") }
        val powerCardio = powerDay.allExercises().filter { it.cardioDetails != null }.sumOf { it.cardioDetails!!.effectiveDurationSeconds() } / 60
        assertTrue("el día de cardio y potencia tiene solo $powerCardio min de cardio", powerCardio >= 60)
    }

    @Test
    fun functional_weeks_cover_legs_pushes_and_pulls_even_with_little_time() {
        listOf(30, 45, 60).forEach { minutes ->
            listOf(2, 3, 4).forEach { days ->
                val routine = RoutineGenerator.generate(s.request(s.gym, RoutineMode.GENERAL_FUNCTIONAL, RoutineLevel.INTERMEDIATE, days, minutes))
                val covered = routine.report.patternsCovered
                val label = "funcional ${days}d ${minutes}min"
                assertTrue("$label sin sentadilla", RoutinePattern.SQUAT in covered)
                assertTrue("$label sin bisagra", RoutinePattern.HINGE in covered)
                assertTrue("$label sin empuje", RoutinePattern.HORIZONTAL_PUSH in covered || RoutinePattern.VERTICAL_PUSH in covered)
                assertTrue("$label sin tirón", RoutinePattern.HORIZONTAL_PULL in covered || RoutinePattern.VERTICAL_PULL in covered)
            }
        }
    }

    @Test
    fun the_summary_has_three_to_five_reasons_and_one_line_per_day() {
        s.profiles.forEach { profile ->
            val routine = RoutineGenerator.generate(s.request(profile, RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineLevel.INTERMEDIATE, 5, 60, priorities = listOf(MuscleSymbol.CHEST)))
            assertTrue(routine.summary.reasons.size in 3..5)
            assertEquals(5, routine.summary.days.size)
            assertTrue(routine.summary.suggestedName.isNotBlank() && routine.summary.oneLiner.isNotBlank())
            assertEquals(1, routine.summary.days.count { it.isMain })
        }
    }
}
