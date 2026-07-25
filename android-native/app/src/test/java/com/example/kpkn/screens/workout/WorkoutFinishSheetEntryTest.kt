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
            .count()

        // Single writer inside openFinishSheet().
        assertEquals(
            "Expected exactly one `showFinishSheet = true` assignment (openFinishSheet).",
            1,
            trueAssignments,
        )
    }
}
