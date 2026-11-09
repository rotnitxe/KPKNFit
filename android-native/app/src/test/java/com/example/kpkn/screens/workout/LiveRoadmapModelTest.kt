package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura del roadmap de la sesión en vivo (Pulido v1, `lv-road-*`): variante A/B, tira, destinos de toque, entrada del texto. */
class LiveRoadmapModelTest {
    private fun lift(id: String, sets: Int = 3, group: String? = null) =
        Exercise(id, id.replaceFirstChar { it.uppercase() }, sets = List(sets) { ExerciseSet("$id-$it", targetReps = 5) }, supersetGroupRef = group)
    private val cardio = Exercise("walk", "Caminadora", cardioDetails = CardioDetails(CardioType.TREADMILL, targetDurationSeconds = 900))

    @Test fun variantBNeedsAtLeast820DpOfWindowHeight() {
        assertEquals(LiveRoadmapVariant.A, liveRoadmapVariantFor(819))
        assertEquals(LiveRoadmapVariant.B, liveRoadmapVariantFor(820))
        assertEquals(LiveRoadmapVariant.B, liveRoadmapVariantFor(880)) // el Flip5
        assertEquals(LiveRoadmapVariant.A, liveRoadmapVariantFor(740))
        assertEquals(112f, LiveRoadmapVariant.A.height.value, 0f)
        assertEquals(132f, LiveRoadmapVariant.B.height.value, 0f)
    }

    @Test fun cardioOnlyUsesDedicatedCardsWithoutChangingStrengthPreference() {
        val cardioOnly = Session(id = "cardio", name = "Cardio", exercises = listOf(cardio))
        val mixed = Session(id = "mixed", name = "Mixta", exercises = listOf(cardio, lift("press")))
        assertEquals(LiveSessionView.CARDS, effectiveLiveSessionView(cardioOnly, LiveSessionView.LIST.name))
        assertEquals(LiveSessionView.LIST, effectiveLiveSessionView(mixed, LiveSessionView.LIST.name))
        assertEquals(LiveSessionView.LIST, effectiveLiveSessionView(null, LiveSessionView.LIST.name))
    }

    @Test fun bandsAddUpToTheMeasuresOfTheCanvas() {
        // 12 + 32 + 8 + 48 + 12 = 112 · 12 + 30 + 8 + 14 + 8 + 48 + 12 = 132
        assertEquals(112f, 12f + LiveRoadmapVariant.A.band1.value + 8f + 48f + 12f, 0f)
        assertEquals(132f, 12f + LiveRoadmapVariant.B.band1.value + 8f + 14f + 8f + 48f + 12f, 0f)
    }

    @Test fun supersetMembersShareOneSegmentAndCardioWeighsTwo() {
        val a = lift("a"); val b = lift("b", group = "ss"); val c = lift("c", sets = 4, group = "ss")
        val groups = liveRouteGroups(listOf(a, b, c, cardio), listOf(SupersetGroup("ss", listOf("b", "c"))))
        assertEquals(3, groups.size)
        assertTrue(groups[1].superset); assertEquals(listOf("b", "c"), groups[1].members.map { it.id })
        assertTrue(groups[2].cardio)
        val segments = liveRoadmapSegments(groups, "b", emptyMap(), emptySet())
        assertEquals(listOf(3, 7, 2), segments.map { it.weight })
        assertEquals(listOf(false, true, false), segments.map { it.current })
        assertEquals(0, segments[2].sets)
    }

    @Test fun segmentFillFollowsTheRegisteredSetsAndOmittedOnesDoNotCount() {
        val a = lift("a"); val b = lift("b")
        val done = mapOf(workoutSetKey("a", 0) to CompletedSet("x"), workoutSetKey("a", 1) to CompletedSet("y"))
        val segments = liveRoadmapSegments(liveRouteGroups(listOf(a, b), emptyList()), "a", done, emptySet())
        assertEquals(2, segments[0].done); assertEquals(3, segments[0].sets)
        assertEquals(2f / 3f, segments[0].fill, 1e-4f)
        assertEquals(0f, segments[1].fill, 0f)
        val omitted = liveRoadmapSegments(liveRouteGroups(listOf(a, b), emptyList()), "a", done, setOf(workoutSetKey("a", 2)))
        assertEquals(2, omitted[0].sets); assertEquals(1f, omitted[0].fill, 0f)
    }

    @Test fun theFingerBelongsToTheSegmentUnderItOrToTheNearestOne() {
        val bounds = listOf(0f to 100f, 104f to 160f, 164f to 300f)
        assertEquals(0, liveRoadmapSegmentAt(40f, bounds))
        assertEquals(1, liveRoadmapSegmentAt(110f, bounds))
        assertEquals(0, liveRoadmapSegmentAt(102f, bounds)) // en el hueco, a 2 de cada lado: gana el primero
        assertEquals(1, liveRoadmapSegmentAt(103f, bounds)) // a 1 del segundo
        assertEquals(2, liveRoadmapSegmentAt(999f, bounds)) // fuera de la tira: el último
        assertEquals(0, liveRoadmapSegmentAt(-50f, bounds))
        assertEquals(-1, liveRoadmapSegmentAt(10f, emptyList()))
    }

    @Test fun releaseIsInsideTheStripWithin16DpSidewaysAnd22DpUpOrDown() {
        assertTrue(liveRoadmapReleaseInside(150f, 22f, 0f, 304f, 22f))
        assertTrue(liveRoadmapReleaseInside(-16f, 22f, 0f, 304f, 22f))
        assertFalse(liveRoadmapReleaseInside(-17f, 22f, 0f, 304f, 22f))
        assertTrue(liveRoadmapReleaseInside(320f, 44f, 0f, 304f, 22f))
        assertFalse(liveRoadmapReleaseInside(150f, 45f, 0f, 304f, 22f))
        assertFalse(liveRoadmapReleaseInside(150f, -1f, 0f, 304f, 22f))
    }

    @Test fun bandTextEntersFromTheSideOfTheMovement() {
        val set2 = LiveRoadmapPosition("a", 2, LiveRoadmapPhase.Set)
        assertNull(liveRoadmapSlide(null, set2))
        assertNull(liveRoadmapSlide(set2, set2))
        assertEquals(LiveRoadmapSlide.Right, liveRoadmapSlide(set2, set2.copy(setNumber = 3)))
        assertEquals(LiveRoadmapSlide.Left, liveRoadmapSlide(set2, set2.copy(setNumber = 1)))
        assertEquals(LiveRoadmapSlide.Up, liveRoadmapSlide(set2, set2.copy(exerciseId = "b", setNumber = 1)))
        assertEquals(LiveRoadmapSlide.Up, liveRoadmapSlide(set2, set2.copy(phase = LiveRoadmapPhase.Rest)))
        assertEquals(LiveRoadmapSlide.Right, liveRoadmapSlide(set2.copy(phase = LiveRoadmapPhase.Rest), set2.copy(setNumber = 3)))
        assertEquals(LiveRoadmapSlide.Left, liveRoadmapSlide(set2, set2.copy(phase = LiveRoadmapPhase.Prep)))
    }

    private fun step(label: String, description: String = "Serie", progress: Float? = null) =
        LiveSessionLineStep(key = "k-$label", label = label, description = description, active = true, preparationProgress = progress)

    @Test fun leadSaysWhichSetOfHowMany() {
        val ex = lift("a", 3)
        assertEquals("Serie 2 de 3", liveRoadmapLead(LiveRoadmapPhase.Set, step("S2"), ex, null))
        assertEquals("Serie 2 izq. de 3", liveRoadmapLead(LiveRoadmapPhase.Set, step("S2 L"), ex, null))
        assertEquals("Serie 1 de 3", liveRoadmapLead(LiveRoadmapPhase.Set, null, ex, null))
        assertEquals("Descanso 2:41", liveRoadmapLead(LiveRoadmapPhase.Rest, step("2:41", "Descanso"), ex, "2:41"))
    }

    @Test fun preparationLeadCountsTheDoneApproachesAndHasAShortVersion() {
        val ex = lift("a").copy(warmupSets = listOf(WarmupSetDefinition("w1", 50.0, 8), WarmupSetDefinition("w2", 70.0, 5)))
        val first = step("APROX", "Aproximación", 0f)
        val second = step("APROX", "Aproximación", .5f)
        assertEquals("Aproximación 1 de 2", liveRoadmapLead(LiveRoadmapPhase.Prep, first, ex, null))
        assertEquals("Aproximación 2 de 2", liveRoadmapLead(LiveRoadmapPhase.Prep, second, ex, null))
        assertEquals("Aprox. 2/2", liveRoadmapLeadShort(LiveRoadmapPhase.Prep, second, ex, null))
        assertEquals(2, liveRoadmapPrepIndex(1f, 2))
        assertEquals(1, liveRoadmapPrepIndex(null, 3))
    }

    @Test fun setNumberAndSideComeFromTheStepLabel() {
        assertEquals(2, liveRoadmapSetNumber("S2 R"))
        assertEquals(" der.", liveRoadmapSideSuffix("S2 R"))
        assertEquals("", liveRoadmapSideSuffix("S2"))
        assertNull(liveRoadmapSetNumber("MOV"))
        val rest = step("2:41", "Descanso")
        assertFalse(liveRoadmapIsSetStep(rest))
        assertTrue(liveRoadmapIsSetStep(step("S3")))
    }
}
