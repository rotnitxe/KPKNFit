package com.example.kpkn.screens.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.components.buildExerciseRmReference
import com.example.kpkn.screens.workout.components.buildExerciseRmRows
import com.example.kpkn.screens.workout.components.exerciseRmSessionRanges
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseRmDeviceTest {
    private val today = LocalDate.of(2026, 10, 16)
    private fun history(date: String, vararg sets: CompletedSet) = ExerciseHistoryEntry(date, sets.toList(), e1rm = 9999.0)

    @Test fun maximumComesFromRealSetsEvenWhenOlderThanTheLatestEntry() {
        val best = CompletedSet("best", weight = 100.0, reps = 6)
        val recent = CompletedSet("recent", weight = 80.0, reps = 6)
        val reference = requireNotNull(buildExerciseRmReference(listOf(history("2026-10-01", best), history("2026-10-15", recent)), emptyList(), today))
        assertEquals("best", reference.sourceSet.id)
        assertEquals(116.1, reference.estimated1RM, .0001)
        assertFalse(reference.fromCurrentSession)
        assertEquals("2026-10-01", reference.sourceDate)
    }

    @Test fun tiesPreferTheRecentDateThenCurrentSessionToday() {
        val old = CompletedSet("old", 100.0, 1)
        val recent = old.copy(id = "recent")
        val entries = listOf(history("2026-10-01", old), history("2026-10-15", recent))
        assertEquals("recent", buildExerciseRmReference(entries, emptyList(), today)?.sourceSet?.id)
        val current = old.copy(id = "current")
        val reference = requireNotNull(buildExerciseRmReference(entries + history(today.toString(), recent), listOf(current), today))
        assertEquals("current", reference.sourceSet.id)
        assertTrue(reference.fromCurrentSession)
        assertEquals(today.toString(), reference.sourceDate)
    }

    @Test fun invalidWarmupSkippedAndFailedSetsNeverProvideAReference() {
        val invalid = listOf(
            CompletedSet("warmup", 1000.0, 10, isWarmup = true),
            CompletedSet("skipped", 1000.0, 10, skipped = true),
            CompletedSet("failed", 1000.0, 10, isFailedSet = true),
            CompletedSet("zero", 0.0, 10), CompletedSet("negative", -100.0, 10),
            CompletedSet("no-reps", 1000.0, 0), CompletedSet("negative-reps", 1000.0, -1),
            CompletedSet("nan", Double.NaN, 10), CompletedSet("infinite", Double.POSITIVE_INFINITY, 10),
        )
        assertNull(buildExerciseRmReference(listOf(history("2026-10-15", *invalid.toTypedArray())), invalid, today))
        // Muscular failure with completed reps is different from a failed attempt.
        val valid = CompletedSet("valid", 110.0, 1, isFailure = true)
        assertEquals("valid", buildExerciseRmReference(emptyList(), invalid + valid, today)?.sourceSet?.id)
        assertTrue(buildExerciseRmRows(null, emptyList()).isEmpty())
    }

    @Test fun rowsUseTheExistingInverseAndPreserveDisjointPlannedRanges() {
        val exercise = Exercise("press", "Press", sets = listOf(
            ExerciseSet("s0", targetRepsRange = RepRange(4, 6)),
            ExerciseSet("hole", isEmptySlot = true, targetReps = 20),
            ExerciseSet("s2", targetRepsRange = RepRange(8, 10)),
            ExerciseSet("s3", targetReps = 12),
        ))
        val current = listOf(CompletedSet("record", 100.0, 6, setIndex = 0))
        val ranges = exerciseRmSessionRanges(exercise, current)
        assertEquals(listOf(RepRange(8, 10), RepRange(12, 12)), ranges)
        val reference = buildExerciseRmReference(emptyList(), current, today)
        val rows = buildExerciseRmRows(reference, ranges)
        assertEquals((1..20).toList(), rows.map { it.reps })
        assertEquals(116.1, rows.first().weightKg, .0001)
        assertEquals(87.1, rows[9].weightKg, .0001)
        assertEquals(69.7, rows.last().weightKg, .0001)
        assertFalse(rows[4].inSessionRange)
        assertTrue(rows[8].inSessionRange)
        assertTrue(rows[11].inSessionRange)
        assertFalse(rows[12].inSessionRange)
        assertFalse(rows[19].inSessionRange)
    }

    @Test fun oneUnilateralSideDoesNotConsumeTheRemainingPlan() {
        val exercise = Exercise("one-arm", "Remo unilateral", isUnilateral = true, sets = listOf(
            ExerciseSet("s0", targetRepsRange = RepRange(4, 6)), ExerciseSet("s1", targetReps = 10),
        ))
        val left = CompletedSet("left", 30.0, 6, side = "left", setIndex = 0)
        assertEquals(listOf(RepRange(4, 6), RepRange(10, 10)), exerciseRmSessionRanges(exercise, listOf(left)))
        assertEquals(listOf(RepRange(10, 10)), exerciseRmSessionRanges(exercise, listOf(left, left.copy(id = "right", side = "right"))))
    }
}
