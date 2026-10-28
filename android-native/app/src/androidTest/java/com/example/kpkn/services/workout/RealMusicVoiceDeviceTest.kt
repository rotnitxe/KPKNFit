package com.example.kpkn.services.workout

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.util.Collections
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Voluntary hardware QA. Run only with a person ready to say Listo after the READY log.
 * Requires instrumentation argument realMusicVoiceHumanPresent=true and an isolated .dbg target.
 * Uses the production dialog and remote speech engine, with no command parser, fake port or recorder.
 * Evidence contains engine metadata and native UI pixels, never audio or collected transcripts.
 */
@RunWith(AndroidJUnit4::class)
class RealMusicVoiceDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val voluntaryDebugGuard = object : ExternalResource() {
        override fun before() {
            check(context.packageName.endsWith(".dbg")) { "Real microphone QA requires the isolated .dbg application" }
            assumeTrue("Human-presence opt-in required", InstrumentationRegistry.getArguments()
                .getString("realMusicVoiceHumanPresent") == "true")
        }
    }
    private val microphonePermission = object : ExternalResource() {
        override fun before() {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { "Real microphone QA requires Android 9 or newer" }
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(voluntaryDebugGuard)
        .around(microphonePermission).around(compose)

    private var preferenceBackedUp = false
    private var musicExisted = false
    private var musicBefore = false
    private var engine: WorkoutVoiceEnginePort? = null
    private val observations = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val metadataLock = Any()
    private val stateEvents = JSONArray()
    private val routeEvents = JSONArray()
    private val phases = JSONArray()
    private var rmsSamples = 0
    private var minimumRms: Float? = null
    private var maximumRms: Float? = null
    private var activationStopConfirmed = false
    private var cleanupStopConfirmed: Boolean? = null
    private var result = "not_completed"
    private val chosen = Collections.synchronizedList(mutableListOf<VoiceCaptureMode>())
    private val directory get() = File(context.getExternalFilesDir(null), "real-music-voice").apply { mkdirs() }

    @Before fun prepareOnlyMusicVerification() {
        runBlocking(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            musicExisted = prefs.contains(VoiceCaptureMode.MUSIC.name)
            musicBefore = prefs.getBoolean(VoiceCaptureMode.MUSIC.name, false)
            preferenceBackedUp = true
            check(prefs.edit().putBoolean(VoiceCaptureMode.MUSIC.name, false).commit())
        }
        WorkoutVoiceRuntime.initialize(context)
        val actual = WorkoutVoiceRuntime.speechEngine()
        engine = actual
        assertTrue("Do not start while an earlier capture cannot be stopped", stopActualEngine())
        observations.launch { actual.captureState.collect { value -> synchronized(metadataLock) {
            stateEvents.put(event().put("captureState", value.name))
        } } }
        observations.launch { actual.activeRouteLabel.collect { value -> synchronized(metadataLock) {
            routeEvents.put(event().put("route", value ?: JSONObject.NULL))
        } } }
        observations.launch { actual.rmsLevel.collect { value -> if (value.isFinite()) synchronized(metadataLock) {
            rmsSamples += 1
            minimumRms = minimumRms?.let { minOf(it, value) } ?: value
            maximumRms = maximumRms?.let { maxOf(it, value) } ?: value
        } } }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun humanSaysListoInRealMusicModeThenActivatesOnceAndClosesCapture() {
        val actual = checkNotNull(engine)
        compose.setContent {
            KPKNTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalKpknOverlayHost provides null) {
                    var showing by remember { mutableStateOf(true) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas).testTag(Canvas)) {
                        if (showing) VoiceCaptureModeDialog(onChosen = {
                            chosen.add(it)
                            showing = false
                        }, onDismissRequest = { showing = false })
                    }
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Preparando controles de voz…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Música").assertIsDisplayed().performClick()
        compose.onNodeWithText("Probar este modo").assertIsDisplayed()
        // Pump Compose frames explicitly below; microphone and IPC keep their real wall time.
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Probar este modo").performClick()
        compose.waitUntil(25_000) {
            compose.mainClock.advanceTimeByFrame()
            actual.captureState.value == VoiceCaptureState.LISTENING &&
                compose.onAllNodesWithText("Di «Listo»").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Di «Listo»").assertIsDisplayed()
        phase("listening")
        capture("01-listening")
        writeEvidence()
        Log.i(Tag, "READY: real MUSIC capture is listening; say Listo now")

        compose.waitUntil(25_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Te escuchamos").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Te escuchamos").assertIsDisplayed()
        assertTrue("Recognition alone must not choose a mode", chosen.isEmpty())
        phase("recognized_expected_word")
        // Do not persist recognition hypotheses or a potentially unexpected transcript in a PNG.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Activar modo de voz").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            chosen.isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(listOf(VoiceCaptureMode.MUSIC), chosen.toList()) }
        activationStopConfirmed = stopActualEngine()
        assertTrue("Activation must leave a confirmed stopped engine", activationStopConfirmed)
        assertFalse(actual.isActive)
        assertEquals(VoiceCaptureState.IDLE, actual.captureState.value)
        assertTrue(context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .getBoolean(VoiceCaptureMode.MUSIC.name, false))
        phase("activated_and_stopped")
        capture("02-activated-and-stopped")
        result = "passed"
        writeEvidence()
    }

    @After fun stopAndRestoreOnlyMusicVerification() {
        try {
            if (engine != null) {
                cleanupStopConfirmed = stopActualEngine()
                phase("cleanup")
                writeEvidence()
            }
        } finally {
            observations.cancel()
            if (preferenceBackedUp) runBlocking(Dispatchers.IO) {
                val editor = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
                if (musicExisted) editor.putBoolean(VoiceCaptureMode.MUSIC.name, musicBefore)
                else editor.remove(VoiceCaptureMode.MUSIC.name)
                check(editor.commit()) { "Could not restore MUSIC verification preference" }
            }
        }
        if (engine != null) assertEquals("Cleanup must confirm the microphone stopped", true, cleanupStopConfirmed)
    }

    private fun stopActualEngine(): Boolean = runBlocking {
        withContext(Dispatchers.Main.immediate) { WorkoutVoiceRuntime.stopEngineAndAwaitWithoutUiCallback() }
    }

    private fun event() = JSONObject().put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
    private fun phase(name: String) {
        val actual = checkNotNull(engine)
        synchronized(metadataLock) {
            phases.put(event().put("phase", name).put("captureState", actual.captureState.value.name)
                .put("route", actual.activeRouteLabel.value ?: JSONObject.NULL)
                .put("rms", actual.rmsLevel.value.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("active", actual.isActive))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun capture(name: String) {
        compose.mainClock.advanceTimeByFrame()
        val bitmap = compose.onNodeWithTag(Canvas).assertIsDisplayed().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun writeEvidence() {
        val snapshot = synchronized(metadataLock) {
            JSONObject().put("test", "RealMusicVoiceDeviceTest").put("result", result)
                .put("mode", VoiceCaptureMode.MUSIC.name).put("phases", phases)
                .put("captureStates", stateEvents).put("routes", routeEvents)
                .put("rmsObservedEmissions", rmsSamples)
                .put("minimumObservedRms", minimumRms ?: JSONObject.NULL)
                .put("maximumObservedRms", maximumRms ?: JSONObject.NULL)
                .put("chosenCount", chosen.size).put("activationStopConfirmed", activationStopConfirmed)
                .put("cleanupStopConfirmed", cleanupStopConfirmed ?: JSONObject.NULL).toString(2)
        }
        File(directory, "evidence.json").writeText(snapshot)
    }

    private companion object {
        const val Prefs = "workout_voice_first_use_v2"
        const val Canvas = "real-music-voice-canvas"
        const val Tag = "RealMusicVoiceDeviceTest"
    }
}
