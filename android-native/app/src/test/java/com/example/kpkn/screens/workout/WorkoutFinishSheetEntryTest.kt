package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards against regressions that reintroduce direct `showFinishSheet = true` writers
 * outside [WorkoutViewModel.openFinishSheet].
 */
class WorkoutFinishSheetEntryTest {
    @Test
    fun onlyOpenFinishSheetAssignsShowFinishSheetTrue() {
        val viewModelFile = File(
            "../main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt",
        ).takeIf { it.exists() }
            ?: File("src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt")

        val source = viewModelFile.readText()
        val trueAssignments = Regex("""showFinishSheet\s*=\s*true""")
            .findAll(source)
            .toList()

        // Both resume and fresh-finish paths live inside openFinishSheet().
        assertEquals(
            "Expected exactly two `showFinishSheet = true` assignments inside openFinishSheet.",
            2,
            trueAssignments.size,
        )
        val openFinishIdx = source.indexOf("private fun openFinishSheet()")
        require(openFinishIdx >= 0) { "openFinishSheet() not found" }
        trueAssignments.forEach { match ->
            assert(match.range.first > openFinishIdx) {
                "Found showFinishSheet = true outside openFinishSheet() at ${match.range.first}"
            }
        }
    }
}
