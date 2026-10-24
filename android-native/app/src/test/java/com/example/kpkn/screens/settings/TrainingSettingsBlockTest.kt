package com.example.kpkn.screens.settings

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.models.Settings
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h1800dp-mdpi")
class TrainingSettingsBlockTest {
    @get:Rule val rule=createComposeRule()
    @Test fun pasosLimitesYDosInterruptoresConservanRestoDePreferencias() {
        var settings by mutableStateOf(Settings(restTimerDefaultSeconds=30))
        rule.setContent { KPKNTheme { TrainingSettingsBlock(settings,{ f -> settings=f(settings) }) } }
        rule.onNodeWithTag("rest-default-less").performClick();rule.runOnIdle { assertEquals(30,settings.restTimerDefaultSeconds) }
        rule.onNodeWithTag("rest-default-more").performClick();rule.runOnIdle { assertEquals(45,settings.restTimerDefaultSeconds);settings=settings.copy(restTimerDefaultSeconds=300) }
        rule.onNodeWithTag("rest-default-more").performClick();rule.runOnIdle { assertEquals(300,settings.restTimerDefaultSeconds) }
        rule.onNodeWithTag("rest-auto-start").assertIsOn().performClick().assertIsOff()
        rule.onNodeWithTag("auge-deload-setting").assertIsOff().performClick().assertIsOn()
        rule.runOnIdle { assertFalse(settings.autoStartRestAfterRecording);assertTrue(settings.algorithmSettings.augeAutoDeload);assertEquals(300,settings.restTimerDefaultSeconds);assertEquals(Settings().restTimerAutoStart,settings.restTimerAutoStart) }
        listOf("rest-auto-start","auge-deload-setting","rest-default-more","rest-default-less").forEach { tag ->
            assertTrue(rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height>=48f)
        }
    }
}
