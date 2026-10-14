package com.example.kpkn.domain.biomechanics.profiles

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tabla de los 23 perfiles frente a la del brief VT2a (vistas, amplitudes, lados y encuadre). */
class AnalysisProfilesTest {

    private class Row(
        val id: AnalysisProfileId,
        val count: Set<ViewKind>,
        val metric: Set<ViewKind>,
        val minAmplitude: Double,
        val plateMinAmplitude: Double?,
        val perSide: Boolean,
        val preferred: Set<ViewKind>,
        val feet: Boolean,
    )

    private companion object {
        val SIDE = ViewKind.SIDE
        val FRONT = ViewKind.FRONT
        val OBLIQUE = ViewKind.OBLIQUE
        val LYING = ViewKind.LYING

        /** La tabla del brief, fila a fila (`feet` = el encuadre pide los pies). */
        val brief = listOf(
            Row(SQUAT, setOf(SIDE, FRONT, OBLIQUE), setOf(SIDE), 0.08, 0.10, false, setOf(SIDE), feet = true),
            Row(LUNGE, setOf(SIDE, OBLIQUE), setOf(SIDE), 0.06, 0.08, false, setOf(SIDE), feet = true),
            Row(STEP_UP, setOf(SIDE, OBLIQUE), setOf(SIDE), 0.08, null, false, setOf(SIDE), feet = true),
            Row(MACHINE_SQUAT, setOf(SIDE), setOf(SIDE), 30.0, null, false, setOf(SIDE), feet = false),
            Row(DEADLIFT, setOf(SIDE, OBLIQUE), setOf(SIDE), 0.10, 0.10, false, setOf(SIDE), feet = true),
            Row(HINGE, setOf(SIDE, OBLIQUE), setOf(SIDE), 30.0, 0.08, false, setOf(SIDE), feet = true),
            Row(HIP_THRUST, setOf(SIDE, LYING), setOf(SIDE, LYING), 25.0, 0.05, false, setOf(SIDE), feet = true),
            Row(BACK_EXTENSION, setOf(SIDE, LYING, OBLIQUE), setOf(SIDE, LYING), 25.0, null, false, setOf(SIDE), feet = true),
            Row(BENCH, setOf(SIDE, LYING), setOf(SIDE, LYING), 0.05, 0.05, false, setOf(SIDE), feet = false),
            Row(DIP, setOf(SIDE, OBLIQUE), setOf(SIDE), 0.05, null, false, setOf(SIDE), feet = true),
            Row(PUSH_UP, setOf(SIDE, LYING), setOf(SIDE, LYING), 0.05, null, false, setOf(SIDE), feet = true),
            Row(OHP, setOf(SIDE, FRONT, OBLIQUE), setOf(SIDE), 40.0, 0.10, false, setOf(SIDE), feet = true),
            Row(PULL_VERTICAL, setOf(FRONT, SIDE, OBLIQUE), setOf(FRONT, SIDE), 0.25, null, false, setOf(FRONT, SIDE), feet = false),
            Row(ROW, setOf(SIDE, OBLIQUE), setOf(SIDE), 0.20, 0.05, true, setOf(SIDE), feet = true),
            Row(CURL, setOf(SIDE, FRONT, OBLIQUE), setOf(SIDE), 40.0, null, true, setOf(SIDE), feet = false),
            Row(TRICEPS_EXTENDED_REST, setOf(SIDE, OBLIQUE, LYING), setOf(SIDE), 35.0, null, true, setOf(SIDE), feet = false),
            Row(TRICEPS_FLEXED_REST, setOf(SIDE, OBLIQUE), setOf(SIDE), 35.0, null, true, setOf(SIDE), feet = false),
            Row(RAISE, setOf(FRONT, SIDE, OBLIQUE), setOf(FRONT, SIDE), 35.0, null, true, setOf(FRONT), feet = false),
            Row(LEG_EXTENSION, setOf(SIDE), setOf(SIDE), 35.0, null, false, setOf(SIDE), feet = false),
            Row(LEG_CURL, setOf(SIDE, LYING), setOf(SIDE, LYING), 35.0, null, false, setOf(SIDE), feet = false),
            Row(CALF, setOf(SIDE), setOf(SIDE), 0.015, null, false, setOf(SIDE), feet = true),
            Row(SHRUG, setOf(FRONT, SIDE), setOf(FRONT), 0.02, null, false, setOf(FRONT), feet = false),
            Row(FLY, setOf(SIDE, LYING), setOf(SIDE, LYING), 0.05, null, false, setOf(SIDE), feet = false),
        )
    }

    @Test
    fun thereAreTwentyThreeProfilesInTheOrderOfTheEnumAndOfIsTheirOwnEntry() {
        assertEquals(23, AnalysisProfileId.entries.size)
        assertEquals(AnalysisProfileId.entries.toList(), AnalysisProfiles.all.map { it.id })
        for (id in AnalysisProfileId.entries) assertSame(id, AnalysisProfiles.of(id).id)
        assertEquals(AnalysisProfileId.entries.toList(), brief.map { it.id })
    }

    @Test
    fun viewsAmplitudesSidesAndFramingMatchTheBriefTable() {
        for (row in brief) {
            val profile = AnalysisProfiles.of(row.id)
            assertEquals("${row.id} countViews", row.count, profile.countViews)
            assertEquals("${row.id} metricViews", row.metric, profile.metricViews)
            assertEquals("${row.id} minAmplitude", row.minAmplitude, profile.minAmplitude, 0.0)
            assertEquals("${row.id} plateMinAmplitude", row.plateMinAmplitude, profile.plateMinAmplitude)
            assertEquals("${row.id} perSide", row.perSide, profile.perSide)
            assertEquals("${row.id} preferredViews", row.preferred, profile.framing.preferredViews)
            assertEquals("${row.id} needsFeet", row.feet, profile.framing.needsFeet)
            assertTrue("${row.id} needsFace", profile.framing.needsFace)
        }
    }

    @Test
    fun theBaseProfilesCarryNoMaterial() {
        for (profile in AnalysisProfiles.all) {
            assertFalse("${profile.id} usesPlate", profile.usesPlate)
            assertNull("${profile.id} mvtMps", profile.mvtMps)
            assertFalse("${profile.id} needsPlate", profile.framing.needsPlate)
        }
    }

    @Test
    fun thePlateSignalAndItsMinimumAmplitudeGoTogetherWithTheRightOrientation() {
        val withPlate = AnalysisProfiles.all.filter { it.plateSignal != null }
        assertEquals(
            setOf(SQUAT, LUNGE, DEADLIFT, HINGE, HIP_THRUST, BENCH, OHP, ROW),
            withPlate.map { it.id }.toSet(),
        )
        for (profile in AnalysisProfiles.all) {
            assertEquals("${profile.id}", profile.plateSignal != null, profile.plateMinAmplitude != null)
        }
        for (profile in withPlate) {
            // El reposo es el mínimo: si se arranca bajando, el disco BAJA al irse al giro; si se arranca subiendo, SUBE.
            val expected = if (profile.startPhase == StartPhase.ECCENTRIC_FIRST) CountSignal.PLATE_DROP else CountSignal.PLATE_RISE
            assertEquals("${profile.id}", expected, profile.plateSignal)
            assertNotNull(profile.plateMinAmplitude)
            assertTrue("${profile.id}: el disco no se usa con ángulos", profile.plateMinAmplitude!! < 1.0)
        }
        // La señal de pose nunca es la del disco.
        assertTrue(AnalysisProfiles.all.none { it.poseSignal == CountSignal.PLATE_DROP || it.poseSignal == CountSignal.PLATE_RISE })
    }

    @Test
    fun metricViewsAreCountedViewsAndFramingPrefersOnlyViewsTheEvaluatorCanSee() {
        val seenByThePhoto = setOf(ViewKind.SIDE, ViewKind.FRONT, ViewKind.OBLIQUE)
        for (profile in AnalysisProfiles.all) {
            assertTrue("${profile.id}", profile.countViews.containsAll(profile.metricViews))
            assertTrue("${profile.id}", profile.metricViews.isNotEmpty())
            assertTrue("${profile.id}", seenByThePhoto.containsAll(profile.framing.preferredViews))
            assertTrue("${profile.id}", profile.metricViews.containsAll(profile.framing.preferredViews))
            // Nunca de espaldas ni sin vista: BACK y UNKNOWN no cuentan.
            assertFalse("${profile.id}", ViewKind.BACK in profile.countViews || ViewKind.UNKNOWN in profile.countViews)
        }
    }

    @Test
    fun theTiersAndTheStartPhasesFollowSectionFive() {
        assertEquals(
            setOf(SQUAT, DEADLIFT, HINGE, HIP_THRUST, PUSH_UP, OHP, PULL_VERTICAL),
            AnalysisProfiles.all.filter { it.tier == ProfileTier.A }.map { it.id }.toSet(),
        )
        assertEquals(setOf(CALF, SHRUG, FLY), AnalysisProfiles.all.filter { it.tier == ProfileTier.C }.map { it.id }.toSet())
        assertEquals(
            setOf(SQUAT, LUNGE, MACHINE_SQUAT, HINGE, BACK_EXTENSION, BENCH, DIP, PUSH_UP, TRICEPS_EXTENDED_REST, FLY),
            AnalysisProfiles.all.filter { it.startPhase == StartPhase.ECCENTRIC_FIRST }.map { it.id }.toSet(),
        )
    }

    @Test
    fun theRulesAreSetOnlyWhereSectionSevenUsesThem() {
        fun idsWith(predicate: (AnalysisProfile) -> Boolean) = AnalysisProfiles.all.filter(predicate).map { it.id }.toSet()
        assertEquals(setOf(SQUAT, PULL_VERTICAL, PUSH_UP, DIP, BENCH), idsWith { it.depthRule != null })
        assertEquals(setOf(DEADLIFT, HIP_THRUST, OHP), idsWith { it.lockoutRule != null })
        assertEquals(setOf(SQUAT, DEADLIFT, HINGE, OHP), idsWith { it.pathRule == PathRule.MIDFOOT_VERTICAL })
        assertEquals(setOf(BENCH), idsWith { it.pathRule == PathRule.CONSISTENCY })
        assertEquals(DepthRule.SQUAT_PARALLEL, AnalysisProfiles.of(SQUAT).depthRule)
        assertEquals(DepthRule.CHIN_OVER_BAR, AnalysisProfiles.of(PULL_VERTICAL).depthRule)
        assertEquals(DepthRule.PUSH_UP_ELBOW, AnalysisProfiles.of(PUSH_UP).depthRule)
        assertEquals(DepthRule.DIP_SHOULDER, AnalysisProfiles.of(DIP).depthRule)
        assertEquals(DepthRule.BENCH_RELATIVE, AnalysisProfiles.of(BENCH).depthRule)
        assertEquals(LockoutRule.DEADLIFT, AnalysisProfiles.of(DEADLIFT).lockoutRule)
        assertEquals(LockoutRule.HIP_THRUST, AnalysisProfiles.of(HIP_THRUST).lockoutRule)
        assertEquals(LockoutRule.OHP, AnalysisProfiles.of(OHP).lockoutRule)
    }

    @Test
    fun theRomAngleIsMissingOnlyWhereThereIsNoAngle() {
        assertEquals(setOf(CALF, SHRUG, FLY), AnalysisProfiles.all.filter { it.romAngle == null }.map { it.id }.toSet())
        assertEquals(RomAngle.ARM_ELEVATION, AnalysisProfiles.of(RAISE).romAngle)
        assertEquals(RomAngle.KNEE, AnalysisProfiles.of(SQUAT).romAngle)
        assertEquals(RomAngle.HIP, AnalysisProfiles.of(DEADLIFT).romAngle)
        assertEquals(RomAngle.ELBOW, AnalysisProfiles.of(BENCH).romAngle)
    }

    @Test
    fun theSeatedAndLyingProfilesAskNoFeetAndTheOnesWithFeetAreStanding() {
        val withoutFeet = AnalysisProfiles.all.filter { !it.framing.needsFeet }.map { it.id }.toSet()
        assertEquals(
            setOf(MACHINE_SQUAT, BENCH, PULL_VERTICAL, CURL, TRICEPS_EXTENDED_REST, TRICEPS_FLEXED_REST, RAISE, LEG_EXTENSION, LEG_CURL, SHRUG, FLY),
            withoutFeet,
        )
    }
}
