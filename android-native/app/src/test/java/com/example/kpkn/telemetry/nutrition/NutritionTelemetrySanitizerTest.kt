package com.example.kpkn.telemetry.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WP-U17: un fallo se registra como tipo + primer frame de la app; nunca con el mensaje de la excepción. */
class NutritionTelemetrySanitizerTest {

    private val drawer = "com.example.kpkn.screens.nutrition.components.FoodLoggerDrawerKt"

    private fun frame(className: String, method: String = "run", line: Int = 10) =
        StackTraceElement(className, method, "$className.kt", line)

    private fun failure(message: String?, vararg frames: StackTraceElement): Throwable =
        IllegalStateException(message).also { it.stackTrace = arrayOf(*frames) }

    @Test
    fun `the summary never contains the exception message`() {
        val message = "pollo con arroz y 200 g de lentejas"
        val summary = NutritionTelemetrySanitizer.errorSummary(failure(message, frame(drawer, "analyze", 612)))

        assertFalse(summary, summary.contains("pollo"))
        assertFalse(summary, summary.contains(message))
        assertEquals("IllegalStateException at $drawer.analyze:612", summary)
    }

    @Test
    fun `a message that looks like a secret is dropped too`() {
        val summary = NutritionTelemetrySanitizer.errorSummary(IllegalArgumentException("Bearer abcdef123456 sk-AbCdEf1234567890"))
        assertFalse(summary, summary.contains("abcdef123456"))
        assertFalse(summary, summary.contains("Bearer"))
        assertTrue(summary, summary.startsWith("IllegalArgumentException"))
    }

    @Test
    fun `an exception thrown from app code names the app frame that threw it`() {
        val summary = NutritionTelemetrySanitizer.errorSummary(NumberFormatException("For input string: \"mi cena\""))

        assertTrue(summary, summary.startsWith("NumberFormatException at com.example.kpkn.telemetry.nutrition.NutritionTelemetrySanitizerTest."))
        assertFalse(summary, summary.contains("mi cena"))
    }

    @Test
    fun `a stack without app frames yields just the class name`() {
        val error = failure("boom", frame("java.util.ArrayList", "get", 427), frame("kotlinx.coroutines.DispatchedTask", "run", 100))

        assertEquals("IllegalStateException", NutritionTelemetrySanitizer.errorSummary(error))
    }

    @Test
    fun `an empty stack yields just the class name`() {
        assertEquals("IllegalStateException", NutritionTelemetrySanitizer.errorSummary(failure("x")))
    }

    @Test
    fun `the first app frame wins over framework frames above it and app frames below it`() {
        val error = failure(
            "x",
            frame("java.lang.Thread", "run"),
            frame("com.example.kpkn.domain.nutrition.FoodParser", "parse", 40),
            frame("com.example.kpkn.screens.nutrition.NutritionViewModel", "saveLog", 7),
        )

        assertEquals("IllegalStateException at com.example.kpkn.domain.nutrition.FoodParser.parse:40", NutritionTelemetrySanitizer.errorSummary(error))
    }

    @Test
    fun `a frame without a line number has no line suffix`() {
        val error = failure("x", StackTraceElement("com.example.kpkn.A", "b", null, -1))

        assertEquals("IllegalStateException at com.example.kpkn.A.b", NutritionTelemetrySanitizer.errorSummary(error))
    }

    @Test
    fun `a lookalike package is not the app`() {
        val error = failure("x", frame("com.example.kpknother.Thing", "run"), frame("com.example.kpkn", "run"))

        assertEquals("IllegalStateException", NutritionTelemetrySanitizer.errorSummary(error))
    }

    @Test
    fun `the cause is searched when the exception itself has no app frames`() {
        val cause = failure("inner secret", frame("com.example.kpkn.data.repository.NutritionRepository", "saveNutritionLog", 215))
        val error = RuntimeException("wrapper secret", cause).also { it.stackTrace = arrayOf(frame("java.util.concurrent.FutureTask", "get")) }

        val summary = NutritionTelemetrySanitizer.errorSummary(error)

        assertEquals("RuntimeException at com.example.kpkn.data.repository.NutritionRepository.saveNutritionLog:215", summary)
        assertFalse(summary.contains("secret"))
    }

    @Test
    fun `a cause chain that loops terminates`() {
        val a = failure("a", frame("java.lang.Thread", "run"))
        val b = failure("b", frame("java.lang.Thread", "run"))
        a.initCause(b)
        b.initCause(a)

        assertEquals("IllegalStateException", NutritionTelemetrySanitizer.errorSummary(a))
    }

    @Test
    fun `an anonymous exception class still gets a readable name`() {
        val anonymous = object : RuntimeException("secret") {}
        val summary = NutritionTelemetrySanitizer.errorSummary(anonymous)

        assertTrue(summary, summary.isNotBlank() && !summary.startsWith(" "))
        assertFalse(summary.contains("secret"))
    }

    @Test
    fun `the app stack keeps only app frames and never the message`() {
        val error = failure(
            "mi cena",
            frame("java.lang.Thread", "run"),
            frame("com.example.kpkn.A", "one", 1),
            frame("androidx.compose.runtime.Recomposer", "run"),
            frame("com.example.kpkn.B", "two", 2),
        )

        assertEquals("at com.example.kpkn.A.one:1\nat com.example.kpkn.B.two:2", NutritionTelemetrySanitizer.appStack(error))
    }

    @Test
    fun `the app stack respects its frame cap`() {
        val frames = (1..30).map { frame("com.example.kpkn.Deep", "m$it", it) }.toTypedArray()

        assertEquals(5, NutritionTelemetrySanitizer.appStack(failure("x", *frames), maxFrames = 5).lines().size)
        assertEquals(12, NutritionTelemetrySanitizer.appStack(failure("x", *frames)).lines().size)
    }

    @Test
    fun `the app stack of an exception without app frames is empty`() {
        assertEquals("", NutritionTelemetrySanitizer.appStack(failure("x", frame("java.lang.Thread", "run"))))
    }
}
