package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1200dp-xxhdpi", application = android.app.Application::class)
class ExerciseFichaVideoAuditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun anAlbumWithOnlyVideosIsAccessibleFromTheFicha() {
        var opened = false
        val model = CatalogDetailModel(
            name = "Press", caption = null, description = null, imageVariants = emptyList(), selectedImplementation = null,
            axes = emptyList(), configurationLine = null, configurationIncomplete = false, muscleInfo = null,
            joints = emptyList(), patternId = null, isSelected = false, canSelect = false,
            media = listOf(WorkoutMedia(id = "video", kind = WorkoutMediaKind.VIDEO, filePath = "missing.mp4", createdAtMs = 1)),
        )
        rule.setContent {
            ExerciseFichaContent(model, onDismiss = {}, onToggleSelection = {}, onSelectOption = { _, _ -> },
                onImplementationSettled = {}, onOpenAlbum = { opened = true })
        }
        rule.onNodeWithText("Tus vídeos · 1").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.runOnIdle { assertTrue(opened) }
    }
}
