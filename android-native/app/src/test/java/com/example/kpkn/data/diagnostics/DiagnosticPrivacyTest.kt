package com.example.kpkn.data.diagnostics

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticPrivacyTest {
    @Test fun nestedJsonCannotBypassRedaction() {
        val nested = JsonObject(mapOf("transcript" to JsonPrimitive("my private meal"), "latencyMs" to JsonPrimitive(3)))
        val result = DiagnosticRedactor.field("details", mapOf("items" to listOf(nested)))
        assertFalse(result.toString().contains("private meal"))
        assertTrue(result.toString().contains("REDACTED"))
        assertTrue(result.toString().contains("latencyMs"))
    }
    @Test fun recursiveAndUnboundedPayloadsAreLimited() {
        val cycle = mutableMapOf<String, Any?>()
        cycle["child"] = cycle
        assertTrue(DiagnosticRedactor.field("cycle", cycle).toString().contains("TRUNCATED"))
        assertEquals(64, (DiagnosticRedactor.field("list", (0..10000).toList()) as JsonArray).size)
        assertTrue(DiagnosticRedactor.text("x".repeat(10000)).length <= 512)
    }
    @Test fun boundedQueueKeepsCriticalEventsAndCountsLoss() {
        val queue = BoundedEventQueue<Int>(2) { it > 10 }
        assertTrue(queue.offer(1)); assertTrue(queue.offer(2))
        assertFalse(queue.offer(3)); assertTrue(queue.offer(11))
        assertEquals(2, queue.size); assertEquals(2L, queue.dropped.get())
        assertEquals(2, queue.poll()); assertEquals(11, queue.poll())
    }
    @Test fun encodedPayloadsAndProductionDataCannotBypassPrivacy() {
        assertFalse(DiagnosticRedactor.field("details", "{\"transcript\":\"private words\"}").toString().contains("private words"))
        assertEquals(JsonPrimitive("[REDACTED]"), DiagnosticRedactor.productionField("arbitrary", "private food"))
        assertEquals(JsonPrimitive("[REDACTED]"), DiagnosticRedactor.productionField("state", "private food"))
        assertEquals(JsonPrimitive("READY"), DiagnosticRedactor.productionField("state", "READY"))
    }
    @Test fun exceptionSanitizationDoesNotRetainMessagesOrCauses() {
        val original = IllegalArgumentException("private dictation", IllegalStateException("private health"))
        val sanitized = CrashReportingController.sanitized(original)
        assertFalse(sanitized.toString().contains("private")); assertNull(sanitized.cause)
        assertArrayEquals(original.stackTrace, sanitized.stackTrace)
    }
}
