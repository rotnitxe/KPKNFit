package com.example.kpkn.screens.workout

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class FinishPhotosNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun realPhotoTouchOpensTheViewerAndBothArrowsNavigateAndReturnToThePage() {
        val folder = File("build/lv-bf-photo-fixtures").apply { mkdirs() }
        val photos = listOf("Sentadilla", "Peso muerto rumano").mapIndexed { index, title ->
            val file = File(folder, "photo-$index.png")
            val bitmap = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(if (index == 0) android.graphics.Color.DKGRAY else android.graphics.Color.GRAY)
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            StoryPhoto(file.absolutePath, title, "Foto sintética $index")
        }
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme { Box(Modifier.size(360.dp, 740.dp)) { FinishPhotosPage(photos) } }
            }
        }
        rule.onNodeWithContentDescription("Sentadilla", useUnmergedTree = true).performTouchInput { click() }
        rule.onNodeWithTag("workout-photo-image").assertContentDescriptionEquals("Sentadilla")
        rule.onNodeWithText("1/2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Foto siguiente").assertIsDisplayed().performClick()
        rule.onNodeWithTag("workout-photo-image").assertContentDescriptionEquals("Peso muerto rumano")
        rule.onNodeWithText("2/2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Foto anterior").performClick()
        rule.onNodeWithTag("workout-photo-image").assertContentDescriptionEquals("Sentadilla")
        rule.onNodeWithContentDescription("Cerrar foto").performClick()
        rule.onNodeWithTag("workout-photo-viewer").assertDoesNotExist()
        rule.onNodeWithTag("finish-photos").assertIsDisplayed()
    }
}
