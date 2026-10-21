package com.example.kpkn.screens.programs

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.PowerliftingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El contenido de la hoja de TM (P6), en Robolectric: «Guardar» solo se activa con un 1RM y devuelve el perfil con esos kg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrainingMaxContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveStaysDisabledWithoutAnyMaximum() {
        composeRule.setContent {
            TrainingMaxContent(
                initial = null,
                autoEstimate = null,
                trainingMaxPercent = 0.90,
                confirmLabel = "Guardar TM",
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeRule.onNodeWithTag(TM_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun saveConfirmsTheDeclaredMaximaAndLeavesTheOthersEmpty() {
        var confirmed: PowerliftingProfile? = null
        composeRule.setContent {
            TrainingMaxContent(
                initial = PowerliftingProfile(squat1RM = 150.0, bench1RM = 100.0),
                autoEstimate = null,
                trainingMaxPercent = 0.90,
                confirmLabel = "Guardar TM",
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }
        composeRule.onNodeWithTag(TM_SAVE_TAG).assertIsEnabled().performClick()
        assertEquals(150.0, confirmed!!.squat1RM!!, 1e-9)
        assertEquals(100.0, confirmed!!.bench1RM!!, 1e-9)
        assertNull(confirmed!!.deadlift1RM)
    }
}
