package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.onboarding.LiftMark
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Registrar tus máximos» (P6), en Robolectric. Se monta el cuerpo suelto: dentro de `KpknSheet` los toques no llegan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramOneRmSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveStaysDisabledUntilAllThreeMaximaArePositive() {
        composeRule.setContent {
            OneRmSheetBody(
                valuesKg = mapOf(LiftMark.SQUAT to 150.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 0.0),
                unit = "kg",
                onValueKg = { _, _ -> },
                onUnit = {},
                onSave = { _, _, _ -> },
            )
        }
        composeRule.onNodeWithTag(ONE_RM_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun saveSendsTheThreeMaximaInKilogramsWhateverTheDisplayUnit() {
        var saved: List<Double>? = null
        composeRule.setContent {
            OneRmSheetBody(
                valuesKg = mapOf(LiftMark.SQUAT to 150.0, LiftMark.BENCH to 100.0, LiftMark.DEADLIFT to 180.0),
                unit = "lb",
                onValueKg = { _, _ -> },
                onUnit = {},
                onSave = { squat, bench, deadlift -> saved = listOf(squat, bench, deadlift) },
            )
        }
        composeRule.onNodeWithTag(ONE_RM_SAVE_TAG).assertIsEnabled().performClick()
        assertEquals(listOf(150.0, 100.0, 180.0), saved)
    }
}
