package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.testsupport.MotCatalog
import com.example.kpkn.testsupport.MotSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · S1–S5 (AUD1 §2): reproducciones de los fallos del asistente con las recetas reales de §2.5. Usan solo la API anterior
 * (`SessionAssistantEngine.evaluate`): sin el rediseño fallan; con él, cumplen el contrato nuevo (tono neutro, como mucho tres
 * avisos, acciones sobre un ejercicio concreto, ninguna acción que alargue o no cambie la sesión, sesiones previstas no «hechas»).
 */
class SessionAssistantRedesignReproTest {
    private val index = MotCatalog.index
    private val program = Program(id = "p1", name = "P")

    private fun input(
        session: Session,
        week: List<Session> = emptyList(),
        customDrain: PredictedDrain? = null,
    ): SessionAssistantInput = SessionAssistantInput(
        allExercisesInSession = session.allExercises(),
        weekSessions = (week + session).distinctBy { it.id },
        currentSessionId = session.id,
        program = program,
        settings = Settings(),
        workoutLogs = emptyList(),
        exerciseIndex = index,
        ruleLimits = SessionEditorRuleLimits(),
        mesoIndex = 0,
        programId = "p1",
        targetDurationMinutes = session.targetDurationMinutes,
        customDrain = customDrain,
        supersetGroups = session.allSupersetGroups(),
        sessionWarmup = session.warmup,
    )

    private fun allText(suggestions: List<AssistantSuggestion>): String =
        suggestions.joinToString("\n") { s ->
            (listOf(s.title, s.message) + s.details.map { it.label }).joinToString(" | ")
        }.lowercase()

    @Test
    fun s11_timeCardNeverOffersDropSetAsATimeSaving() {
        val session = MotSessions.session("s11", "Cuerpo completo exigente", MotSessions.S11, dayOfWeek = 3, targetDurationMinutes = 25)
        val report = SessionAssistantEngine.evaluate(input(session))
        val time = report.ajustes.firstOrNull { it.id == "time_overage" }
        assertTrue("La receta S11 con 25 min debe generar la tarjeta de tiempo: ${report.ajustes.map { it.id }}", time != null)
        assertTrue(
            "Un drop-set añade series y segundos: no puede ofrecerse para ahorrar tiempo: ${time!!.details.map { it.label }}",
            time.details.none { it.action is AssistantDetailAction.ConvertToDropSet },
        )
    }

    @Test
    fun costCardUsesNeutralLanguage_noClaimsAboutRecoveryOrInjury() {
        val session = MotSessions.session("s10", "Cadena anterior", MotSessions.S10, dayOfWeek = 3)
        val report = SessionAssistantEngine.evaluate(input(session, customDrain = PredictedDrain(cns = 70, muscular = 70, spinal = 65)))
        val text = allText(report.ajustes)
        listOf("demasiado", "fatigante", "recuperación", "lesión", "lesion", "columna", "proteg", "peligro").forEach { word ->
            assertFalse("El asistente no debe usar «$word»: $text", text.contains(word))
        }
    }

    @Test
    fun reduceSetActionsPointAtAnExplicitExercise_notAMuscleName() {
        val session = MotSessions.session("s10", "Cadena anterior", MotSessions.S10, dayOfWeek = 3, targetDurationMinutes = 20)
        val report = SessionAssistantEngine.evaluate(input(session, customDrain = PredictedDrain(cns = 70, muscular = 70, spinal = 65)))
        val reduce = report.ajustes.flatMap { it.details }.map { it.action }.filterIsInstance<AssistantDetailAction.ReduceSet>()
        assertTrue(
            "Cada «quitar una serie» debe apuntar a un ejercicio concreto: $reduce",
            reduce.all { it.exerciseId != null },
        )
    }

    @Test
    fun atMostThreeCardsEvenWhenCostTimeAndOverlapAllTrigger() {
        val current = MotSessions.session("cur", "Cadena anterior", MotSessions.S10, dayOfWeek = 2, targetDurationMinutes = 10)
        val monday = MotSessions.session("mon", "Cadena anterior lunes", MotSessions.S10, dayOfWeek = 1)
        val sunday = MotSessions.session("sun", "Cadena anterior domingo", MotSessions.S10, dayOfWeek = 7)
        val report = SessionAssistantEngine.evaluate(
            input(current, week = listOf(monday, sunday), customDrain = PredictedDrain(cns = 70, muscular = 70, spinal = 65)),
        )
        assertTrue("Como mucho 3 avisos, hay ${report.ajustes.size}: ${report.ajustes.map { it.id }}", report.ajustes.size <= 3)
    }

    @Test
    fun plannedSessionsAreNeverPresentedAsDone() {
        val s1 = MotSessions.session("s1", "SBD Sentadilla", MotSessions.S1, dayOfWeek = 1)
        val s2 = MotSessions.session("s2", "SBD Peso muerto", MotSessions.S2, dayOfWeek = 2)
        // Precondición (motor de interferencia sin tocar): dos sesiones PREVISTAS, ≈ 13 %, no vienen del historial.
        val hits = InterferenceEngine.analyzeUpcomingSession(s2, listOf(s1, s2), emptyList(), index, Settings())
        assertTrue("S1 → S2 debe producir un cruce previsto: $hits", hits.isNotEmpty() && hits.all { !it.isFromHistory })
        assertTrue("≈ 13 % según AUD1: ${hits.map { it.interferencePercent }}", hits.first().interferencePercent in 10..24)

        val report = SessionAssistantEngine.evaluate(input(s2, week = listOf(s1), customDrain = PredictedDrain(30, 30, 20)))
        val text = allText(report.ajustes)
        assertFalse("Una sesión prevista no se «entrenó»: $text", text.contains("entrenaste"))
        assertFalse("Un cruce bajo (10–24 %) no es «poco descanso»: $text", text.contains("poco descanso"))
    }

    @Test
    fun s13_alreadyGroupedPairIsNotOfferedAgainAsSuperset() {
        val base = MotSessions.session("s13", "Brazos con superserie", MotSessions.S9, dayOfWeek = 3, targetDurationMinutes = 5)
        val first = base.exercises[0].copy(supersetId = "g1", supersetGroupRef = "g1")
        val second = base.exercises[1].copy(supersetId = "g1", supersetGroupRef = "g1")
        val grouped = base.copy(
            exercises = listOf(first, second) + base.exercises.drop(2),
            supersetGroups = listOf(
                SupersetGroup(id = "g1", exerciseOrder = listOf(first.id, second.id), restBetweenExercises = 30, restAfterSuperset = 75, rounds = 2),
            ),
        )
        val report = SessionAssistantEngine.evaluate(input(grouped))
        val supersets = report.ajustes.flatMap { it.details }.map { it.action }.filterIsInstance<AssistantDetailAction.ConvertToSuperset>()
        assertTrue(
            "No se ofrece unir de nuevo una pareja ya agrupada: $supersets",
            supersets.none { it.exerciseId == first.id || it.exerciseId == second.id },
        )
    }

    @Test
    fun sanity_costOnlyCard_stillSurfacesAtTheThreshold() {
        // Control: el umbral interno 59/60 sigue existiendo como señal; solo cambia lo que se dice y se propone.
        val session = MotSessions.session("s10", "Cadena anterior", MotSessions.S10, dayOfWeek = 3)
        val below = SessionAssistantEngine.evaluate(input(session, customDrain = PredictedDrain(59, 59, 59)))
        assertEquals(0, below.ajustes.size)
    }
}
