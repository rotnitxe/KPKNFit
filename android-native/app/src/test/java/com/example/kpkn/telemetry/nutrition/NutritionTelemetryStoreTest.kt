package com.example.kpkn.telemetry.nutrition

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NutritionTelemetryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun jsonlLines(dir: File): List<String> =
        dir.listFiles().orEmpty()
            .filter { it.name.endsWith(".jsonl") }
            .sortedBy { it.name }
            .flatMap { it.readLines() }
            .filter { it.isNotBlank() }

    @Test
    fun `writeSync persiste eventos JSONL de una linea parseables`() {
        val dir = tmp.newFolder("telemetry")
        val store = NutritionTelemetryStore(baseDir = dir, sessionId = "test01")
        repeat(3) { i ->
            store.writeSync(
                mapOf(
                    "event" to "analysis_stage",
                    "seq" to i,
                    "ok" to true,
                    "note" to "línea\ncon salto",
                ),
            )
        }
        store.shutdown()

        val lines = jsonlLines(dir)
        assertEquals(3, lines.size)
        val parsed = lines.map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(listOf(0, 1, 2), parsed.map { it.getValue("seq").jsonPrimitive.content.toInt() })
        // Los saltos de línea del payload quedan escapados dentro de la misma línea JSON.
        assertEquals("línea\ncon salto", parsed[0].getValue("note").jsonPrimitive.content)
        assertTrue(parsed.all { it.getValue("ok").jsonPrimitive.content == "true" })
    }

    @Test
    fun `sanitizer redacta claves sensibles, patrones secretos y trunca`() {
        val sanitized = NutritionTelemetrySanitizer.sanitize(
            mapOf(
                "apiKey" to "sk-supersecret123",
                "message" to "Falló con Bearer abcdef123456789 y sk-secret_98765",
                "deep" to mapOf("token" to "xyz", "count" to 4),
                "long" to "x".repeat(500),
            ),
        )
        assertEquals("<redacted>", sanitized["apiKey"])
        val message = sanitized["message"] as String
        assertFalse(message.contains("abcdef123456789"))
        assertFalse(message.contains("sk-secret_98765"))
        val deep = sanitized["deep"] as Map<*, *>
        assertEquals("<redacted>", deep["token"])
        assertEquals(4, deep["count"])
        assertEquals(320, (sanitized["long"] as String).length)
    }

    @Test
    fun `rotacion y retencion acotan recuento y tamano total`() {
        val dir = tmp.newFolder("telemetry")
        val store = NutritionTelemetryStore(
            baseDir = dir,
            sessionId = "test02",
            maxFileBytes = 128,
            maxTotalBytes = 800,
            maxFiles = 5,
        )
        repeat(30) { i ->
            store.writeSync(mapOf("event" to "stage", "seq" to i, "payload" to "y".repeat(120)))
        }
        store.pruneIfNeeded()
        store.shutdown()

        val files = store.listFiles()
        assertTrue("files=${files.size}", files.size in 1..5)
        assertTrue(files.sumOf { it.length() } <= 800L)
    }

    @Test
    fun `write asincrono drena por el hilo del writer`() {
        val dir = tmp.newFolder("telemetry")
        val store = NutritionTelemetryStore(baseDir = dir, sessionId = "test03")
        val latch = CountDownLatch(1)
        store.write(mapOf("event" to "queued"))
        store.executeAsync { latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        store.shutdown()

        val lines = jsonlLines(dir)
        assertEquals(1, lines.size)
        assertEquals("queued", Json.parseToJsonElement(lines.single()).jsonObject.getValue("event").jsonPrimitive.content)
    }
}
