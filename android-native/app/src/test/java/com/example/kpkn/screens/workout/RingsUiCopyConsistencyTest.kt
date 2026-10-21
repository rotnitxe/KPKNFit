package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.repository.adaptiveCacheWhenEntityIsMissing
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.SessionDrainCharacterizationFixtures
import com.example.kpkn.domain.relator.ReadinessObserver
import com.example.kpkn.domain.relator.RelatorActionKind
import com.example.kpkn.domain.relator.WarmupObserver
import com.example.kpkn.domain.relator.relatorContext
import com.example.kpkn.domain.sessionassistant.SessionAssistantEngine
import com.example.kpkn.domain.sessionassistant.SessionAssistantInput
import com.example.kpkn.domain.sessionassistant.SessionEditorRuleLimits
import com.example.kpkn.domain.sessionassistant.sessionOverlapMessage
import com.example.kpkn.screens.home.OvertrainingRecommendationIntro
import com.example.kpkn.screens.home.overtrainingMusclesMessage
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.rings.RingsSessionDaysNote
import com.example.kpkn.screens.onboarding.rings.highlightText
import com.example.kpkn.screens.sessioneditor.buildAugeSummary
import com.example.kpkn.screens.sessioneditor.buildAugeSummaryFromMetrics
import com.example.kpkn.screens.sessioneditor.computeSessionAugeComputation
import com.example.kpkn.screens.workout.components.setAdjustmentReadinessColor
import com.example.kpkn.ui.components.rings.RingAdjustMuscleTone
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.ringAdjustEnergyWord
import com.example.kpkn.ui.components.rings.ringAdjustMuscleTone
import com.example.kpkn.ui.components.rings.ringAdjustSpineWord
import com.example.kpkn.ui.components.rings.ringAdjustWord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class RingsUiCopyConsistencyTest {
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun fijarZonaUtc() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restaurarZona() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun `el editor describe gasto previsto y no se dispara solo por RPE`() {
        val fixture = SessionDrainCharacterizationFixtures
        val session = fixture.planned.getValue("axial")
        val liveSummary = buildAugeSummary(
            currentSession = session,
            weekSessions = listOf(session),
            exerciseIndex = fixture.db,
            settings = fixture.settings,
            programLogs = emptyList(),
            program = null,
            programId = "p",
            mesoIndex = 0,
        )
        assertFalse(liveSummary.suggestions.any { it.id == "system-neural" })

        val metrics = computeSessionAugeComputation(
            session = session,
            exerciseIndex = fixture.db,
            settings = fixture.settings,
            programLogs = emptyList(),
            programId = "p",
            mesoIndex = 0,
        ).copy(drain = PredictedDrain(cns = 85, muscular = 39, spinal = 9))
        val fallbackSummary = buildAugeSummaryFromMetrics(
            currentSession = session,
            weekSessions = listOf(session),
            currentMetrics = metrics,
            weeklyMetrics = listOf(metrics),
            program = null,
            settings = fixture.settings,
        )
        val warning = fallbackSummary.suggestions.single { it.id == "system-neural" }
        assertEquals(
            "La sesión podría gastar 85 % de Energía. Baja RPE o %1RM, o sube RIR.",
            warning.message,
        )
    }

    @Test
    fun `el aviso de déficit se presenta como consejo por objetivo`() {
        val fixture = SessionDrainCharacterizationFixtures
        val session = fixture.planned.getValue("axial")
        val metrics = computeSessionAugeComputation(
            session = session,
            exerciseIndex = fixture.db,
            settings = fixture.settings,
            programLogs = emptyList(),
            programId = "p",
            mesoIndex = 0,
        )
        val summary = buildAugeSummaryFromMetrics(
            currentSession = session,
            weekSessions = listOf(session),
            currentMetrics = metrics,
            weeklyMetrics = listOf(metrics),
            program = null,
            settings = fixture.settings.copy(calorieGoalObjective = CalorieGoalObjective.DEFICIT),
        )
        val advice = summary.suggestions.single { it.id == "info-deficit" }
        assertEquals("Objetivo de déficit", advice.title)
        assertEquals(
            "Con un objetivo de déficit calórico, puede convenirte moderar el volumen efectivo por sesión.",
            advice.message,
        )
    }

    @Test
    fun `el onboarding no excluye días anteriores y presenta los anillos como estimaciones`() {
        assertFalse(RingsSessionDaysNote.contains("últimos 7 días"))
        assertTrue(RingsSessionDaysNote.contains("días anteriores también pueden aportar contexto"))
        assertTrue(highlightText(com.example.kpkn.domain.auge.RingChannelId.MUSCULOS).startsWith("Estimación"))
        assertTrue(highlightText(com.example.kpkn.domain.auge.RingChannelId.ENERGIA).startsWith("Estimación"))
        assertTrue(highlightText(com.example.kpkn.domain.auge.RingChannelId.COLUMNA).startsWith("Estimación"))
    }

    @Test
    fun `las recomendaciones de Home describen señales del historial`() {
        assertTrue(OvertrainingRecommendationIntro.contains("historial de entrenamiento"))
        assertTrue(overtrainingMusclesMessage(listOf("Cuádriceps")).contains("según el historial"))
        assertEquals(
            "La estimación de Columna es alta. Mantén una técnica controlada.",
            RecoveryBands.action(RecoveryChannelId.STRUCTURE, RecoveryBands.HIGH_MIN),
        )
    }

    @Test
    fun `una instalación sin caché no recibe el aviso de modelo`() {
        val missing = adaptiveCacheWhenEntityIsMissing()
        val existingPending = AugeAdaptiveCache(modelUpdateNoticeShown = false)
        val existingShown = AugeAdaptiveCache(modelUpdateNoticeShown = true)

        assertTrue(missing.modelUpdateNoticeShown)
        assertFalse(existingPending.modelUpdateNoticeShown)
        assertTrue(existingShown.modelUpdateNoticeShown)
    }

    @Test
    fun `overlay relator calentamiento coach y panel comparten RecoveryBands`() {
        assertEquals(WizardColors.done, setAdjustmentReadinessColor(RecoveryBands.NORMAL_MIN))
        assertEquals(SymbolPalette.musculo, setAdjustmentReadinessColor(RecoveryBands.NORMAL_MIN - 1))
        assertEquals(SymbolPalette.musculo, setAdjustmentReadinessColor(RecoveryBands.MODERATE_MIN))
        assertEquals(WizardColors.danger, setAdjustmentReadinessColor(RecoveryBands.MODERATE_MIN - 1))

        assertEquals("Con mucha energía", ringAdjustEnergyWord(RecoveryBands.HIGH_MIN))
        assertEquals("Energía normal", ringAdjustEnergyWord(RecoveryBands.NORMAL_MIN))
        assertEquals("Algo cansado", ringAdjustSpineWord(RecoveryBands.MODERATE_MIN))
        assertEquals("en rango", ringAdjustWord(RingChannel.MUSCULOS, RecoveryBands.NORMAL_MIN))
        assertEquals(RingAdjustMuscleTone.CRITICAL, ringAdjustMuscleTone(RecoveryBands.LOW_MIN - 1))

        assertTrue(ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.HIGH_MIN)).isEmpty())
        assertTrue(ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.HIGH_MIN - 1)).isNotEmpty())
        val at70 = ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.NORMAL_MIN)).single()
        assertFalse(at70.actions.any { it.kind == RelatorActionKind.ADJUST_LOAD })
        val below70 = ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.NORMAL_MIN - 1)).single()
        assertEquals(-5.0, below70.actions.single { it.kind == RelatorActionKind.ADJUST_LOAD }.loadDeltaPercent, 0.0)
        val at50 = ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.MODERATE_MIN)).single()
        assertEquals(-5.0, at50.actions.single { it.kind == RelatorActionKind.ADJUST_LOAD }.loadDeltaPercent, 0.0)
        val below50 = ReadinessObserver.observe(relatorContext(dailyScore = RecoveryBands.MODERATE_MIN - 1)).single()
        assertEquals(-10.0, below50.actions.single { it.kind == RelatorActionKind.ADJUST_LOAD }.loadDeltaPercent, 0.0)

        fun skipsWarmup(score: Int?) = WarmupObserver.observe(
            relatorContext(dailyScore = score, warmupRemaining = 2),
        ).single().actions.any { it.kind == RelatorActionKind.SKIP_REMAINING_WARMUPS }
        assertTrue(skipsWarmup(RecoveryBands.HIGH_MIN))
        assertFalse(skipsWarmup(RecoveryBands.HIGH_MIN - 1))
        assertFalse(skipsWarmup(null))

        assertEquals("¡Energía al máximo!", WorkoutCoachMessages.getMessage(0.0, 85, 0.0).title)
        assertEquals("Seguimiento en tiempo real", WorkoutCoachMessages.getMessage(0.0, 84, 0.0).title)
        assertEquals("Seguimiento en tiempo real", WorkoutCoachMessages.getMessage(2.0, 70, 0.0).title)
        assertEquals("Sesión ligera — oportunidad", WorkoutCoachMessages.getMessage(2.0, 69, 0.0).title)
        assertEquals("Sesión ligera — oportunidad", WorkoutCoachMessages.getMessage(2.0, 50, 0.0).title)
        assertEquals("Seguimiento en tiempo real", WorkoutCoachMessages.getMessage(2.0, 49, 0.0).title)
        val unknown = WorkoutCoachMessages.getMessage(9.0, null, 0.8)
        assertFalse(unknown.title.contains("readiness", ignoreCase = true))
        assertFalse(unknown.body.contains("disponibilidad alta", ignoreCase = true))
    }

    @Test
    fun `el asistente usa pasado para historial y futuro para sesiones planificadas`() {
        assertEquals(
            "Entrenaste Pecho A hace pocas horas y esta sesión vuelve a pedir pectorales. Baja un poco el volumen o cambia el orden.",
            sessionOverlapMessage(true, "Pecho A", 12, "pectorales"),
        )
        assertEquals(
            "La sesión Pecho B está planificada con 24 h de separación respecto a esta; ambas piden pectorales. Baja un poco el volumen o cambia el orden.",
            sessionOverlapMessage(false, "Pecho B", 24, "pectorales"),
        )
    }
}
