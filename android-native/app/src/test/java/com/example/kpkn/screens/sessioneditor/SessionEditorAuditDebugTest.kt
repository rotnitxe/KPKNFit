package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.templates.SessionTemplateQualityRules
import com.example.kpkn.ui.components.KpknSheetTokens
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime audit probes for session-editor regressions (writes NDJSON to debug-9ba5f2.log).
 */
class SessionEditorAuditDebugTest {

    @Test
    fun `H-B token contrast ControlFill is translucent and ControlLabel is white`() {
        val fill = KpknSheetTokens.ControlFill
        val label = KpknSheetTokens.ControlLabel
        SessionEditorDebugLog.log(
            hypothesisId = "H-B",
            location = "SessionEditorAuditDebugTest.kt:tokens",
            message = "KpknSheetWhiteButton token snapshot",
            data = mapOf(
                "controlFillAlpha" to fill.alpha,
                "controlLabelAlpha" to label.alpha,
                "controlLabelIsNearWhite" to (label.red > 0.9f && label.green > 0.9f && label.blue > 0.9f),
                "chipIdleAlpha" to KpknSheetTokens.ChipIdle.alpha,
                "chipLabelIsNearWhite" to (
                    KpknSheetTokens.ChipLabel.red > 0.9f &&
                        KpknSheetTokens.ChipLabel.green > 0.9f &&
                        KpknSheetTokens.ChipLabel.blue > 0.9f
                    ),
            ),
        )
        // Evidence: CTA fill is glass (~0.14) with white label → not solid white+black
        assertTrue("ControlFill should be translucent glass", fill.alpha < 0.4f)
        assertTrue("ControlLabel is near-white", label.red > 0.9f)
    }

    @Test
    fun `H-C History isCurrent raw equality fails when structuralEquals passes`() {
        val live = Session(
            id = "s1",
            name = "Pierna",
            background = null,
            lastModifiedAtMs = 999L,
            exercises = listOf(
                Exercise(id = "e1", name = "Squat", sets = listOf(ExerciseSet(id = "set1", targetReps = 6))),
            ),
        )
        val snapshotSession = TrainedSessionVersionStore.sessionForVersioning(live)
        val rawEqual = live == snapshotSession
        val structural = TrainedSessionVersionStore.structuralEquals(live, snapshotSession)
        SessionEditorDebugLog.log(
            hypothesisId = "H-C",
            location = "SessionEditorAuditDebugTest.kt:isCurrent",
            message = "History isCurrent comparison modes",
            data = mapOf(
                "rawEquality" to rawEqual,
                "structuralEquals" to structural,
                "liveHasBackground" to (live.background != null),
                "snapBackgroundCleared" to (snapshotSession.background == null),
                "liveLastModified" to live.lastModifiedAtMs,
                "snapLastModified" to snapshotSession.lastModifiedAtMs,
            ),
        )
        assertFalse("Raw == should fail due to cosmetic fields", rawEqual)
        assertTrue("structuralEquals should pass", structural)
    }

    @Test
    fun `H-D ambiguous Accesorio is neither compound nor isolation`() {
        val ambiguous = ExerciseMuscleInfo(
            id = "acc_press_maquina",
            name = "Press en máquina",
            type = "Accesorio",
            force = "Empuje",
            technicalDifficulty = null,
            involvedMuscles = listOf(InvolvedMuscle("Pectoral", MuscleRole.PRIMARY)),
        )
        val basic = ExerciseMuscleInfo(
            id = "basic_squat",
            name = "Sentadilla",
            type = "Básico",
            force = "Sentadilla",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
                InvolvedMuscle("Glúteo", MuscleRole.PRIMARY),
            ),
        )
        val isolation = ExerciseMuscleInfo(
            id = "iso_curl",
            name = "Curl",
            type = "Aislamiento",
            force = "Monoarticular",
            involvedMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY)),
        )
        val aCompound = SessionTemplateQualityRules.isCompound(ambiguous)
        val aIso = SessionTemplateQualityRules.isIsolation(ambiguous)
        SessionEditorDebugLog.log(
            hypothesisId = "H-D",
            location = "SessionEditorAuditDebugTest.kt:classify",
            message = "Compound/isolation classification coverage",
            data = mapOf(
                "ambiguousCompound" to aCompound,
                "ambiguousIsolation" to aIso,
                "ambiguousUnclassified" to (!aCompound && !aIso),
                "basicCompound" to SessionTemplateQualityRules.isCompound(basic),
                "isolationIsIso" to SessionTemplateQualityRules.isIsolation(isolation),
            ),
        )
        assertTrue("Ambiguous Accesorio falls through classification", !aCompound && !aIso)
        assertTrue(SessionTemplateQualityRules.isCompound(basic))
        assertTrue(SessionTemplateQualityRules.isIsolation(isolation))
    }

    @Test
    fun `H-E compoundIsolationExpanded init only from remember scopePartId`() {
        // Documents that expansion state is remember(scopePartId)-only; applying a template
        // that sets overrides does not re-run the initializer.
        val before = SessionEditorRuleDefaults()
        val afterTemplate = before.copy(
            compoundRestSeconds = 180,
            compoundReps = 6,
            isolationRestSeconds = 105,
            isolationReps = 8,
            isolationRpe = 1.0,
            isolationIntensityType = DefaultIntensityType.RIR,
        )
        val wouldExpandOnInit = afterTemplate.hasCompoundOverrides || afterTemplate.hasIsolationOverrides
        val rememberKeyUnchanged = true // scopePartId unchanged in applyRuleTemplate path
        SessionEditorDebugLog.log(
            hypothesisId = "H-E",
            location = "SessionEditorAuditDebugTest.kt:expand",
            message = "Template apply vs compoundIsolationExpanded remember key",
            data = mapOf(
                "beforeHasOverrides" to (before.hasCompoundOverrides || before.hasIsolationOverrides),
                "afterHasOverrides" to wouldExpandOnInit,
                "rememberKeyUnchanged" to rememberKeyUnchanged,
                "uiWouldStayCollapsed" to (rememberKeyUnchanged && wouldExpandOnInit),
            ),
        )
        assertFalse(before.hasCompoundOverrides)
        assertTrue(wouldExpandOnInit)
    }
}
