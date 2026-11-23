package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlannedRomTarget
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.RomScaleMode
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.workout.RomTargetCatalog
import com.example.kpkn.screens.workout.completedSetContentMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorTagsRomAndApreTest {

    @Test
    fun romTargetCatalogSupportsFormattingFractionAndFromFractionAcrossAllFourModes() {
        RomScaleMode.entries.forEach { mode ->
            val defaultTarget = RomTargetCatalog.defaultForMode(mode)
            assertTrue(RomTargetCatalog.format(defaultTarget).isNotBlank())
            val fraction = RomTargetCatalog.sliderFraction(defaultTarget)
            assertTrue(fraction in 0f..1f)
            val approx = RomTargetCatalog.toApproxPercent(defaultTarget)
            assertTrue(approx in 10..120)

            val minReconstructed = RomTargetCatalog.fromFraction(mode, 0f)
            val maxReconstructed = RomTargetCatalog.fromFraction(mode, 1f)
            assertEquals(mode, minReconstructed.mode)
            assertEquals(mode, maxReconstructed.mode)
        }

        val degrees90 = RomTargetCatalog.fromFraction(RomScaleMode.DEGREES, (90f - 15f) / 165f)
        assertEquals(90, degrees90.numericValue)
        assertEquals("90°", RomTargetCatalog.format(degrees90))

        val qualitative = RomTargetCatalog.fromFraction(RomScaleMode.QUALITATIVE, 0.5f)
        assertEquals(2, qualitative.numericValue)
        assertEquals("Hasta la paralela", RomTargetCatalog.format(qualitative))

        val scale = RomTargetCatalog.fromFraction(RomScaleMode.SCALE_1_10, 1f)
        assertEquals("10/10", RomTargetCatalog.format(scale))

        val pct = RomTargetCatalog.fromFraction(RomScaleMode.PERCENT, 0f)
        assertEquals("10%", RomTargetCatalog.format(pct))
    }

    @Test
    fun completedSetContentMatchesDetectsSubTagAndRecordedRomTargetChanges() {
        val payload = RecordedSetPayload(
            exerciseId = "e1",
            loadInputMode = LoadModeV2.LOAD,
            externalLoad = 100.0,
            unitMode = UnitModeV2.REPS,
            completedReps = 8,
            actualValue = 8.0,
        )
        val baseV3 = CompletedSet(
            id = "c1",
            weight = 100.0,
            reps = 8,
            techniqueStatus = com.example.kpkn.data.models.RecordedTechniqueStatus.COMPLETED,
            recordedPayloadV3 = payload,
            subTagIds = listOf("sub-1"),
            recordedRomTarget = PlannedRomTarget(RomScaleMode.DEGREES, 90),
        )
        assertTrue(completedSetContentMatches(baseV3, baseV3.copy(), null))
        assertFalse(completedSetContentMatches(baseV3, baseV3.copy(subTagIds = listOf("sub-1", "sub-2")), null))
        assertFalse(
            completedSetContentMatches(
                baseV3,
                baseV3.copy(recordedRomTarget = PlannedRomTarget(RomScaleMode.DEGREES, 105)),
                null,
            ),
        )

        val baseLegacy = baseV3.copy(recordedPayloadV3 = null)
        assertTrue(completedSetContentMatches(baseLegacy, baseLegacy.copy(), null))
        assertFalse(completedSetContentMatches(baseLegacy, baseLegacy.copy(subTagIds = emptyList()), null))
        assertFalse(
            completedSetContentMatches(
                baseLegacy,
                baseLegacy.copy(recordedRomTarget = PlannedRomTarget(RomScaleMode.QUALITATIVE, 3, "Rompiendo paralela")),
                null,
            ),
        )
    }

    @Test
    fun apreNextLoadKgEnforcesMinimumOneStepOnNonHoldAndLeavesHoldUnchanged() {
        val hold = Apre.nextLoadKg(loadKg = 100.0, validReps = 6, variant = ApreVariant.APRE_6, incrementKg = 2.5)
        assertEquals(100.0, hold, 0.001)

        val smallRaise = Apre.nextLoadKg(loadKg = 40.0, validReps = 8, variant = ApreVariant.APRE_6, incrementKg = 2.5)
        assertTrue("Expected >= 42.5 kg (+1 step), got $smallRaise", smallRaise >= 42.5)

        val largeRaise = Apre.nextLoadKg(loadKg = 40.0, validReps = 11, variant = ApreVariant.APRE_6, incrementKg = 2.5)
        assertTrue("Expected >= 42.5 kg (+1 step minimum), got $largeRaise", largeRaise >= 42.5)

        val smallDrop = Apre.nextLoadKg(loadKg = 40.0, validReps = 4, variant = ApreVariant.APRE_6, incrementKg = 2.5)
        assertTrue("Expected <= 37.5 kg (-1 step), got $smallDrop", smallDrop <= 37.5)
    }

    @Test
    fun normalizeSetPreservesFailureFlagOnAmrapSets() {
        val exercise = Exercise(id = "e1", name = "Press banca")
        val amrapFailureSet = ExerciseSet(
            id = "s1",
            targetReps = 8,
            purpose = SetPurpose.AMRAP,
            isFailure = true,
        )
        val normalized = amrapFailureSet.normalizeSet(exercise)
        assertTrue(normalized.purpose.isAmrap)
        assertTrue(normalized.isFailure)
    }
}
