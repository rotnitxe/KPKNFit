package com.example.kpkn.telemetry.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** WP-U17: lo que llega al bus central cuando algo falla no lleva el mensaje de la excepción ni frames ajenos a la app. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [34])
class NutritionTelemetryErrorEventsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        KpknDiagnosticLogger.initialize(context)
        NutritionTelemetry.initialize(context)
    }

    private fun nutritionEvents(name: String): List<JsonObject> {
        assertTrue(KpknDiagnosticLogger.awaitIdle())
        val root = File(context.filesDir, KpknDiagnosticLogger.LOG_ROOT)
        return root.resolve("nutrition").walkTopDown()
            .filter { it.isFile && it.extension == "jsonl" }
            .flatMap { it.readLines().asSequence() }
            .filter(String::isNotBlank)
            .map { json.parseToJsonElement(it).jsonObject }
            .filter { it["event"]?.jsonPrimitive?.content == name }
            .toList()
    }

    private fun crashOn(thread: String): JsonObject =
        nutritionEvents("app_crash").last { it["thread"]?.jsonPrimitive?.content == thread }

    @Test
    fun `a crash carries the summary and only app frames and never the message`() {
        val thread = "crash-" + UUID.randomUUID()
        val secret = "ensalada de atun con 300 g de queso"

        NutritionTelemetry.recordCrash(context, thread, IllegalStateException(secret))

        val crash = crashOn(thread)
        assertFalse(crash.toString(), crash.toString().contains(secret))
        assertFalse(crash.containsKey("message"))
        assertEquals("java.lang.IllegalStateException", crash["errorType"]?.jsonPrimitive?.content)
        val summary = crash["errorSummary"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(summary, summary.startsWith("IllegalStateException at com.example.kpkn.telemetry.nutrition.NutritionTelemetryErrorEventsTest"))
        val stack = crash["stack"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(stack, stack.isNotBlank())
        assertTrue(stack, stack.lines().all { it.startsWith("at com.example.kpkn.") })
    }

    @Test
    fun `the in-flight stage set without blocking is already visible to the crash record`() {
        val thread = "inflight-" + UUID.randomUUID()
        NutritionTelemetry.markInFlight("trace42", "parse")

        NutritionTelemetry.recordCrash(context, thread, RuntimeException("x"))
        assertTrue(crashOn(thread)["inFlight"]?.jsonPrimitive?.content.orEmpty().startsWith("trace42|parse|"))

        NutritionTelemetry.clearInFlight()
        val cleared = "cleared-" + UUID.randomUUID()
        NutritionTelemetry.recordCrash(context, cleared, RuntimeException("y"))
        val inFlight = crashOn(cleared)["inFlight"]
        assertTrue(inFlight.toString(), inFlight == null || inFlight is JsonNull)
    }

    @Test
    fun `a failed stage records the type and the first app frame but not the message`() {
        val secret = "mi almuerzo secreto " + UUID.randomUUID()
        val stageName = "stage-" + UUID.randomUUID()
        val trace = NutritionTelemetry.startTrace("manual")

        try {
            runBlocking { trace.stage<Unit>(stageName) { throw IllegalStateException(secret) } }
        } catch (_: IllegalStateException) {
            // the stage rethrows: only its event matters here
        }

        val event = nutritionEvents("analysis_stage").last { it["stage"]?.jsonPrimitive?.content == stageName }
        assertFalse(event.toString(), event.toString().contains(secret))
        assertFalse(event.containsKey("errorMessage"))
        assertEquals("java.lang.IllegalStateException", event["errorType"]?.jsonPrimitive?.content)
        val summary = event["errorSummary"]?.jsonPrimitive?.content.orEmpty()
        assertTrue(summary, summary.startsWith("IllegalStateException at com.example.kpkn.telemetry.nutrition.NutritionTelemetryErrorEventsTest"))
    }

    @Test
    fun `nutrition telemetry has no switch to turn it off`() {
        assertTrue(NutritionTelemetry.isEnabled())
        val name = "probe_" + UUID.randomUUID().toString().replace("-", "")

        NutritionTelemetry.event(name)

        assertEquals(1, nutritionEvents(name).size)
    }
}
