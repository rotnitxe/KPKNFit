package com.example.kpkn.screens.workout.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.screens.workout.Cal3CardContract
import com.example.kpkn.testutil.configureLiveSheetTestWindow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Compiled by CAL3; execution on the emulator belongs to the orchestrator. */
@RunWith(AndroidJUnit4::class)
class Cal3V7DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun configureWindow() { rule.activityRule.scenario.onActivity { it.configureLiveSheetTestWindow() } }
    @OptIn(ExperimentalTestApi::class)
    private fun run(height: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Cal3CardContract(rule, captureImage = { dir, name ->
            val bitmap = rule.onNodeWithTag("cal3-scene").captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }).walkthrough(height, File(context.getExternalFilesDir(null), "cal3-v7-renders"))
    }
    @Test fun v7At880RegistersAppliesRevertsEditsAndKeepsInsideTheRealFrame() = run(880)
    @Test fun v7At740RegistersAppliesRevertsEditsAndKeepsInsideTheRealFrame() = run(740)
}
