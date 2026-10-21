package com.example.kpkn.screens.workout

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-only coverage: never presses Comenzar prueba or requests microphone permission. */
@RunWith(AndroidJUnit4::class)
class VoiceFirstUseDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var preferences: SharedPreferences
    private var savedPreferences: Map<String, Any?>? = null

    @Before fun isolateOnlyFirstUsePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        assertTrue("Run this UI fixture in the .dbg app", context.packageName.endsWith(".dbg"))
        preferences = context.getSharedPreferences("workout_voice_first_use_v2", Context.MODE_PRIVATE)
        savedPreferences = preferences.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        assertTrue(preferences.edit().clear().commit())
    }

    @After fun restoreOnlyFirstUsePreferences() {
        val snapshot = savedPreferences ?: return
        val editor = preferences.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    assertTrue(value.all { it is String })
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                else -> error("Unexpected preference type for " + key)
            }
        }
        assertTrue(editor.commit())
    }

    private fun awaitChoicesLoaded() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Preparando controles de voz…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Modo de voz").assertIsDisplayed()
    }

    @Test fun unverifiedHandsFreeOpensIntroWithoutChoosingTheMode() {
        var chosen = 0
        compose.setContent { KPKNTheme { VoiceCaptureModeDialog(onChosen = { chosen++ }, onDismissRequest = {}) } }
        awaitChoicesLoaded()
        compose.onNodeWithText("Activar").assertIsNotEnabled()
        compose.onNodeWithText("Manos libres").performClick()
        compose.onNodeWithText("Probemos tu voz").assertIsDisplayed()
        compose.onNodeWithText("Comenzar prueba").assertIsEnabled()
        compose.onNodeWithText("Listo").assertExists()
        compose.onNodeWithText("Activar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, chosen) }
        assertFalse(preferences.contains(VoiceCaptureMode.HANDS_FREE.name))
    }

    @Test fun unverifiedMusicAlsoRequiresTheRealFirstUseCheck() {
        var chosen = 0
        compose.setContent { KPKNTheme { VoiceCaptureModeDialog(onChosen = { chosen++ }, onDismissRequest = {}) } }
        awaitChoicesLoaded()
        compose.onNodeWithText("Modo música").performClick()
        compose.onNodeWithText("Comenzar prueba").assertIsEnabled()
        compose.onNodeWithText("Activar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, chosen) }
        assertFalse(preferences.contains(VoiceCaptureMode.MUSIC.name))
    }

    @Test fun cancellingTwiceDuringTheSameTransitionDismissesOnce() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        var chosen = 0
        compose.setContent { KPKNTheme {
            if (visible.value) VoiceCaptureModeDialog(onChosen = { chosen++ }, onDismissRequest = {
                dismissals++; visible.value = false
            })
        } }
        awaitChoicesLoaded()
        compose.onNodeWithText("Manos libres").performClick()
        compose.onNodeWithText("Comenzar prueba").assertExists()
        // Two actual semantics clicks before recomposition exercise the in-flight transition guard.
        compose.onNodeWithText("Ahora no").performSemanticsAction(SemanticsActions.OnClick) { click ->
            assertTrue(click()); assertTrue(click())
        }
        compose.waitUntil(5_000) { !visible.value }
        compose.onNodeWithText("Probemos tu voz").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, chosen) }
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun verifiedMusicStillRequiresAnExplicitSingleActivation() {
        assertTrue(preferences.edit().putBoolean(VoiceCaptureMode.MUSIC.name, true).commit())
        val visible = mutableStateOf(true)
        val chosen = mutableListOf<VoiceCaptureMode>()
        compose.setContent { KPKNTheme {
            if (visible.value) VoiceCaptureModeDialog(onChosen = { mode ->
                chosen += mode; visible.value = false
            }, onDismissRequest = { visible.value = false })
        } }
        awaitChoicesLoaded()
        compose.onNodeWithText("Activar").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(chosen.isEmpty()) }
        compose.onNodeWithText("Modo música").performClick()
        compose.onNodeWithText("Modo de voz").assertIsDisplayed()
        compose.onNodeWithText("Comenzar prueba").assertDoesNotExist()
        compose.onNodeWithText("Activar").assertIsEnabled()
        compose.runOnIdle { assertTrue(chosen.isEmpty()) }
        compose.onNodeWithText("Activar").performSemanticsAction(SemanticsActions.OnClick) { click ->
            assertTrue(click()); assertTrue(click())
        }
        compose.waitUntil(5_000) { !visible.value }
        compose.onNodeWithText("Modo de voz").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(VoiceCaptureMode.MUSIC), chosen) }
        assertTrue(preferences.getBoolean(VoiceCaptureMode.MUSIC.name, false))
        assertFalse(preferences.contains(VoiceCaptureMode.HANDS_FREE.name))
    }
}
