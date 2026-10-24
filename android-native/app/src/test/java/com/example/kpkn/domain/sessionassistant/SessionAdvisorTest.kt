package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.TrainingPhase
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.testsupport.MotCatalog
import com.example.kpkn.testsupport.MotRecipeLine
import com.example.kpkn.testsupport.MotSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * MOT · S1–S5 (AUD1 §2.7/§2.8): el asistente rediseñado con las recetas reales de §2.5. Contexto y roles, protección de los
 * principales, acciones verificadas con el reloj común, techo de tres avisos sin duplicados, tono neutro y sesiones previstas que
 * nunca se tratan como hechas.
 */
class SessionAdvisorTest {
    private val index = MotCatalog.index
    private val now = 1_790_000_000_000L

    private fun input(
        session: Session,
        week: List<Session> = emptyList(),
        logs: List<WorkoutLog> = emptyList(),
        program: Program? = Program(id = "p1", name = "P"),
        customDrain: PredictedDrain? = null,
        limits: SessionEditorRuleLimits = SessionEditorRuleLimits(),
        settings: Settings = Settings(),
    ) = SessionAssistantInput(
        allExercisesInSession = session.allExercises(),
        weekSessions = (week + session).distinctBy { it.id },
        currentSessionId = session.id,
        program = program,
        settings = settings,
        workoutLogs = logs,
        exerciseIndex = index,
        ruleLimits = limits,
        mesoIndex = 0,
        programId = "p1",
        targetDurationMinutes = session.targetDurationMinutes,
        customDrain = customDrain,
        supersetGroups = session.allSupersetGroups(),
        sessionWarmup = session.warmup,
        session = session,
        nowMs = now,
    )

    private fun clockSeconds(session: Session): Int = SessionDurationEstimator.estimate(session).totalSeconds

    private fun s11(targetMinutes: Int? = null) =
        MotSessions.session("s11", "Cuerpo completo exigente", MotSessions.S11, dayOfWeek = 3, targetDurationMinutes = targetMinutes)

    private fun everything(report: SessionAssistantReport): String =
        (report.ajustes.flatMap { listOf(it.title, it.message) + it.details.map { d -> d.label } } + report.notas).joinToString("\n").lowercase()

    // ─── Contexto y roles ────────────────────────────────────────────────────

    @Test
    fun roles_protectTheMainLifts_andLeaveTheRestAsAccessories() {
        val session = s11()
        val context = SessionAnalysisContextBuilder.build(input(session), session, clockSeconds(session))
        val byName = session.exercises.associate { it.name to context.roleOf(it.id) }
        assertEquals(ExerciseRole.MAIN, byName.getValue("Sentadilla Trasera con Barra Alta"))
        assertEquals(ExerciseRole.ACCESSORY, byName.getValue("Elevaciones Laterales de Pie"))
        assertEquals(ExerciseRole.ACCESSORY, byName.getValue("Elevación de Talones"))
        assertEquals(AssistantSessionKind.STRENGTH, context.kind)
    }

    @Test
    fun explicitMarksBeatTheHeuristic_competitionLiftT1AndPercentRm() {
        val base = s11()
        val marked = base.copy(
            exercises = base.exercises.mapIndexed { i, ex ->
                when (i) {
                    4 -> ex.copy(isCompetitionLift = true)                 // elevación lateral marcada como competición
                    5 -> ex.copy(slotRole = SlotRole.T1_MAIN)             // gemelo como T1
                    2 -> ex.copy(trainingMode = TrainingMode.RM, sets = ex.sets.map { it.copy(targetPercentageRM = 70.0) })
                    else -> ex
                }
            },
        )
        val context = SessionAnalysisContextBuilder.build(input(marked), marked, clockSeconds(marked))
        assertEquals(ExerciseRole.MAIN, context.roleOf(marked.exercises[4].id))
        assertEquals(ExerciseRole.MAIN, context.roleOf(marked.exercises[5].id))
        assertEquals(ExerciseRole.MAIN, context.roleOf(marked.exercises[2].id))
        // Con marcas explícitas la heurística no añade más principales: la sentadilla y la banca pasan a accesorios del plan.
        assertEquals(ExerciseRole.ACCESSORY, context.roleOf(marked.exercises[0].id))
    }

    @Test
    fun cardio_isItsOwnRole_andTheKindIsMixed() {
        val strength = MotSessions.squatAndDeadlift(setsEach = 3)
        val mixed = strength.copy(exercises = strength.exercises + MotSessions.cardioZ2())
        val context = SessionAnalysisContextBuilder.build(input(mixed), mixed, clockSeconds(mixed))
        assertEquals(AssistantSessionKind.MIXED, context.kind)
        assertEquals(ExerciseRole.CARDIO, context.roleOf(MotSessions.cardioZ2().id))
    }

    @Test
    fun objectiveAndPhaseComeFromTheProgram() {
        val session = s11()
        val program = Program(id = "p1", name = "P", mode = ProgramMode.POWERLIFTING, trainingPhase = TrainingPhase.REALIZATION)
        val context = SessionAnalysisContextBuilder.build(input(session, program = program), session, clockSeconds(session))
        assertEquals(AssistantObjective.PERFORMANCE, context.objective)
        assertEquals(AssistantPhase.REALIZATION, context.phase)
        val general = SessionAnalysisContextBuilder.build(input(session, program = null), session, clockSeconds(session))
        assertEquals(AssistantObjective.GENERAL, general.objective)
        assertEquals(AssistantPhase.UNSPECIFIED, general.phase)
    }

    // ─── Tiempo: acciones verificadas con el reloj común ─────────────────────

    @Test
    fun timeCard_everyDetailSavesRealSeconds_onTheSharedClock() {
        val session = s11(targetMinutes = 25)
        val report = SessionAssistantEngine.evaluate(input(session))
        val card = report.ajustes.firstOrNull { it.id == "time_overage" }
        assertNotNull("sin tarjeta de tiempo: ${report.ajustes.map { it.id }} notas=${report.notas}", card)
        val params = AssistantActionParameters(exerciseIndex = index)
        val before = clockSeconds(session)
        card!!.details.forEach { detail ->
            val after = SessionAssistantActions.apply(session, detail.action, params)
            val saved = before - clockSeconds(after)
            assertTrue("«${detail.label}» debe ahorrar tiempo y ahorra $saved s", saved >= 15)
            assertEquals("el efecto anunciado es el real", -saved, detail.effect!!.deltaSeconds)
            assertTrue(detail.label, detail.label.contains("ahorra"))
        }
        assertTrue(card.details.isNotEmpty())
    }

    @Test
    fun timeCard_neverTouchesTheMainLifts_norOffersADropSet() {
        val session = s11(targetMinutes = 25)
        val context = SessionAnalysisContextBuilder.build(input(session), session, clockSeconds(session))
        val mains = session.exercises.filter { context.roleOf(it.id) == ExerciseRole.MAIN }.map { it.id }.toSet()
        assertTrue(mains.isNotEmpty())
        val report = SessionAssistantEngine.evaluate(input(session))
        val actions = report.ajustes.flatMap { it.details }.map { it.action }
        actions.forEach { action ->
            when (action) {
                is AssistantDetailAction.ReduceSet -> assertFalse("ReduceSet sobre un principal", action.exerciseId in mains)
                is AssistantDetailAction.ReduceRest -> assertTrue("ReduceRest sobre un principal", action.exerciseIds.none { it in mains })
                is AssistantDetailAction.LowerRpe -> assertFalse(action.exerciseId in mains)
                is AssistantDetailAction.ConvertToSuperset -> assertFalse(action.exerciseId in mains)
                is AssistantDetailAction.ConvertToDropSet -> throw AssertionError("el drop-set no es una acción de tiempo")
                AssistantDetailAction.RemoveFailure -> Unit
            }
        }
    }

    @Test
    fun dropSet_addsSeconds_soItIsNotATimeSaving() {
        val session = s11()
        val squat = session.exercises.first()
        val dropped = SessionAssistantActions.convertExerciseToDropSet(session, squat.id)
        assertTrue("el drop-set debe alargar la sesión", SessionAssistantActions.measure(session, dropped).deltaSeconds > 0)
    }

    @Test
    fun smallExcess_isANote_notACard() {
        val session = s11()
        val clock = clockSeconds(session)
        // Un objetivo un minuto por debajo del reloj: exceso entre 1 y 60 s, por debajo del umbral de 120 s.
        val target = (clock + 59) / 60 - 1
        val tight = session.copy(targetDurationMinutes = target)
        val report = SessionAssistantEngine.evaluate(input(tight))
        assertTrue("no debe haber tarjeta de tiempo: ${report.ajustes.map { it.id }}", report.ajustes.none { it.id == "time_overage" })
        assertTrue("debe haber una nota con la estimación: ${report.notas}", report.notas.any { it.contains("supera") })
    }

    @Test
    fun timeBudgetThreshold_hasNoJumpBetween7And8Minutes_against30() {
        // max(120 s, 10 %) de 30 min = 180 s: tanto 7 como 8 minutos de exceso lo superan y reciben el mismo trato.
        val available = 30 * 60
        val threshold = maxOf(120, (available * 0.10).toInt())
        assertEquals(180, threshold)
        assertTrue(7 * 60 > threshold && 8 * 60 > threshold)
        assertFalse(2 * 60 > threshold)
    }

    @Test
    fun groupedPair_isNeitherOfferedAgain_norGetsAnInertRestCut() {
        val base = MotSessions.session("s13", "Brazos", MotSessions.S9, dayOfWeek = 3, targetDurationMinutes = 5)
        val first = base.exercises[0].copy(supersetId = "g1", supersetGroupRef = "g1")
        val second = base.exercises[1].copy(supersetId = "g1", supersetGroupRef = "g1")
        val grouped = base.copy(
            exercises = listOf(first, second) + base.exercises.drop(2),
            supersetGroups = listOf(SupersetGroup("g1", listOf(first.id, second.id), restBetweenExercises = 30, restAfterSuperset = 75, rounds = 2)),
        )
        val report = SessionAssistantEngine.evaluate(input(grouped))
        val actions = report.ajustes.flatMap { it.details }.map { it.action }
        assertTrue(actions.filterIsInstance<AssistantDetailAction.ConvertToSuperset>().none { it.exerciseId == first.id || it.exerciseId == second.id })
        actions.filterIsInstance<AssistantDetailAction.ReduceRest>().forEach { rest ->
            assertTrue("el descanso de un miembro de superserie no cambia el reloj", rest.exerciseIds.none { it == first.id || it == second.id })
        }
    }

    // ─── Tono, techo y duplicados ────────────────────────────────────────────

    @Test
    fun costBelowTheSignal_saysNothing() {
        val session = MotSessions.session("s10", "Cadena anterior", MotSessions.S10, dayOfWeek = 3)
        val report = SessionAssistantEngine.evaluate(input(session, customDrain = PredictedDrain(59, 59, 59)))
        assertTrue(report.ajustes.isEmpty() && report.notas.isEmpty())
    }

    @Test
    fun theSameActionOnTheSameExercise_appearsOnlyOnce_acrossCards() {
        val bench = MotSessions.exercise(MotRecipeLine("bench_press__barbell", 3, 5, 8.0, 180, 100.0), 1).copy(isCompetitionLift = true)
        val pushdown = MotSessions.exercise(MotRecipeLine("triceps_pushdown__bilateral__cable", 5, 12, 8.0, 90, 30.0), 2)
        val session = Session(id = "dup", name = "Banca", exercises = listOf(bench, pushdown), targetDurationMinutes = 5, dayOfWeek = 2)
        val report = SessionAssistantEngine.evaluate(
            input(session, limits = SessionEditorRuleLimits(maxVolumePerMuscleSession = 1.0, rigidLimits = true)),
        )
        val reducePushdown = report.ajustes.flatMap { it.details }.map { it.action }
            .filterIsInstance<AssistantDetailAction.ReduceSet>().filter { it.exerciseId == pushdown.id }
        assertTrue("la misma serie no se propone dos veces: ${report.ajustes.map { it.id to it.details.map { d -> d.label } }}", reducePushdown.size <= 1)
        assertTrue(report.ajustes.size + report.notas.size <= SessionAdvisor.MAX_NOTICES)
    }

    @Test
    fun userLimits_comeFirst_andLowerOnlyWhatExceedsThem() {
        val session = MotSessions.session("lim", "Banca", listOf(MotRecipeLine("bench_press__barbell", 3, 5, 9.0, 180, 100.0)), dayOfWeek = 2)
        val report = SessionAssistantEngine.evaluate(input(session, limits = SessionEditorRuleLimits(maxRPE = 8.0, rigidLimits = true)))
        val card = report.ajustes.firstOrNull { it.id == "user_limits" }
        assertNotNull("se esperaba la tarjeta de límites: ${report.ajustes.map { it.id }}", card)
        assertEquals(AssistantNoticeKind.USER_LIMIT, card!!.kind)
        assertEquals("Límite que elegiste", card.kind.label)
        assertTrue(card.details.all { it.action is AssistantDetailAction.LowerRpe && it.defaultAccepted })
        // Sin límites rígidos no hay tarjeta de límites.
        val relaxed = SessionAssistantEngine.evaluate(input(session, limits = SessionEditorRuleLimits(maxRPE = 8.0, rigidLimits = false)))
        assertTrue(relaxed.ajustes.none { it.id == "user_limits" })
    }

    // ─── Cruces entre sesiones: previsto no es hecho ─────────────────────────

    @Test
    fun plannedOverlap_isDescribedAsPlanned_andBelow25IsOnlyANote() {
        val s1 = MotSessions.session("s1", "SBD Sentadilla", MotSessions.S1, dayOfWeek = 1)
        val s2 = MotSessions.session("s2", "SBD Peso muerto", MotSessions.S2, dayOfWeek = 2)
        val hits = InterferenceEngine.analyzeUpcomingSession(s2, listOf(s1, s2), emptyList(), index, Settings(), nowMs = now)
        val percent = hits.first().interferencePercent
        val report = SessionAssistantEngine.evaluate(input(s2, week = listOf(s1), customDrain = PredictedDrain(30, 30, 20)))
        val text = everything(report)
        assertFalse(text, text.contains("entrenaste") || text.contains("hiciste"))
        assertTrue("debe hablar de la sesión prevista: $text", text.contains("prevista"))
        if (percent < 25) {
            assertTrue("un cruce de $percent % no es una tarjeta: ${report.ajustes.map { it.id }}", report.ajustes.none { it.id.startsWith("overlap-") })
            assertTrue(report.notas.any { it.contains("Puedes mantener el plan") })
        } else {
            assertTrue(report.ajustes.any { it.id.startsWith("overlap-") })
        }
    }

    @Test
    fun realizedOverlap_isTheOnlyOneToSayItWasDone() {
        val s2 = MotSessions.session("s2", "SBD Peso muerto", MotSessions.S2, dayOfWeek = 2)
        val doneLog = WorkoutLog(
            id = "done", programId = "p1", sessionId = "old", sessionName = "Pierna de ayer",
            date = Instant.ofEpochMilli(now - 20L * 3600_000).toString(), durationMinutes = 60,
            completedExercises = MotSessions.S1.mapIndexed { i, line ->
                val info = index.getValue(line.configurationId.lowercase())
                CompletedExercise(
                    exerciseId = "old-$i", exerciseName = info.name, exerciseDbId = info.catalogDefinitionId,
                    catalogConfigurationId = line.configurationId, restTime = line.restSeconds,
                    sets = List(line.sets) { j -> CompletedSet(id = "d$i-$j", weight = 100.0, reps = line.reps, rpe = line.rpe) },
                )
            },
        )
        val report = SessionAssistantEngine.evaluate(input(s2, logs = listOf(doneLog), customDrain = PredictedDrain(30, 30, 20)))
        val text = everything(report)
        assertTrue("lo realizado sí se cuenta como hecho: $text", text.contains("hiciste"))
        assertTrue(text, text.contains("pierna de ayer"))
    }

    // ─── Efecto de aplicar las acciones ──────────────────────────────────────

    @Test
    fun applyingTheTimeCard_actuallyBringsTheSessionCloserToTheTarget() {
        val session = s11(targetMinutes = 25)
        val report = SessionAssistantEngine.evaluate(input(session))
        val card = report.ajustes.first { it.id == "time_overage" }
        val params = AssistantActionParameters(exerciseIndex = index)
        val applied = card.details.filter { it.defaultAccepted }.fold(session) { acc, d -> SessionAssistantActions.apply(acc, d.action, params) }
        assertTrue(clockSeconds(applied) < clockSeconds(session))
        // El mensaje cuenta lo que pasaría de verdad con las opciones marcadas.
        val remaining = clockSeconds(applied) - 25 * 60
        if (remaining > 0) assertTrue(card.message, card.message.contains("por encima del objetivo"))
        else assertTrue(card.message, card.message.contains("cabría"))
    }

    @Test
    fun measurement_noticesPerRecipe_forTheReport() {
        val recipes = listOf(
            Triple("S1", MotSessions.S1, null as Int?),
            Triple("S2", MotSessions.S2, null),
            Triple("S9", MotSessions.S9, null),
            Triple("S10 (30 min)", MotSessions.S10, 30),
            Triple("S11 (30 min)", MotSessions.S11, 30),
            Triple("S11 (25 min)", MotSessions.S11, 25),
        )
        recipes.forEach { (label, recipe, target) ->
            val session = MotSessions.session("m-$label".replace(' ', '-'), label, recipe, dayOfWeek = 3, targetDurationMinutes = target)
            val report = SessionAssistantEngine.evaluate(input(session))
            println(
                "MOT-S medición $label · reloj ${clockSeconds(session) / 60} min · tarjetas=${report.ajustes.map { it.id }} " +
                    "notas=${report.notas.size} · acciones=${report.ajustes.flatMap { it.details }.map { it.label }}",
            )
            assertTrue(report.ajustes.size + report.notas.size <= SessionAdvisor.MAX_NOTICES)
        }
        // Escenario de AUD1 con todas las familias a la vez: 4 tarjetas antes (RINGS, tiempo y dos cruces); ahora, como mucho 3 avisos.
        val current = MotSessions.session("cur", "Cadena anterior", MotSessions.S10, dayOfWeek = 2, targetDurationMinutes = 10)
        val monday = MotSessions.session("mon", "Lunes", MotSessions.S10, dayOfWeek = 1)
        val sunday = MotSessions.session("sun", "Domingo", MotSessions.S10, dayOfWeek = 7)
        val all = SessionAssistantEngine.evaluate(input(current, week = listOf(monday, sunday), customDrain = PredictedDrain(70, 70, 65)))
        println("MOT-S medición todas las familias · tarjetas=${all.ajustes.map { it.id }} notas=${all.notas.size}")
    }

    @Test
    fun reportNeverExceedsThreeNotices() {
        val current = MotSessions.session("cur", "Cadena anterior", MotSessions.S10, dayOfWeek = 2, targetDurationMinutes = 10)
        val monday = MotSessions.session("mon", "Lunes", MotSessions.S10, dayOfWeek = 1)
        val sunday = MotSessions.session("sun", "Domingo", MotSessions.S10, dayOfWeek = 7)
        val report = SessionAssistantEngine.evaluate(input(current, week = listOf(monday, sunday), customDrain = PredictedDrain(70, 70, 65)))
        assertTrue("${report.ajustes.map { it.id }} + ${report.notas}", report.ajustes.size + report.notas.size <= SessionAdvisor.MAX_NOTICES)
        assertTrue(report.ajustes.all { it.details.isNotEmpty() && it.details.size <= 3 })
    }
}
