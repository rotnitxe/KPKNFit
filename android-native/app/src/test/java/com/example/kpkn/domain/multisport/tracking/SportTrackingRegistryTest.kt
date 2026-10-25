package com.example.kpkn.domain.multisport.tracking

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SportTrackingRegistryTest {
    private val engine = object : SportDocumentEngine {
        override val id = "test.rounds"
        override val version = 1
        override val sports = setOf(SportId.MMA)
        override val purposes = setOf(SportDocumentPurpose.PLAN, SportDocumentPurpose.RESULT)
        override fun validate(payload: JsonObject, purpose: SportDocumentPurpose) =
            if ((payload["seconds"] as? JsonPrimitive)?.intOrNull?.let { it > 0 } == true) emptyList()
            else listOf(SportDocumentIssue("seconds", "POSITIVE_REQUIRED"))
        override fun plannedDurationSeconds(payload: JsonObject): Long? = payload["seconds"]!!.jsonPrimitive.long
    }
    private val registry = SportTrackingRegistry(listOf(engine))
    private fun document() = SportTrackingDocument("block", engine.id, 1, SportId.MMA.name,
        SportDocumentPurpose.PLAN.name, buildJsonObject { put("seconds", 180) })

    @Test fun `new valid document has explicit engine sport purpose and duration`() {
        assertEquals(SportDocumentValidation.Valid, registry.validate(document()))
        assertEquals(180L, registry.plannedDurationSeconds(document()))
        assertNull(registry.plannedDurationSeconds(document().copy(purpose = "RESULT")))
    }

    @Test fun `unknown engine and future version stay distinct from invalid known payload`() {
        assertTrue(registry.validate(document().copy(engineId = "future")) is SportDocumentValidation.UnsupportedEngine)
        assertTrue(registry.validate(document().copy(version = 2)) is SportDocumentValidation.UnsupportedVersion)
        assertTrue(registry.validate(document().copy(payload = buildJsonObject { put("seconds", -1) })) is SportDocumentValidation.InvalidPayload)
        assertTrue(registry.validate(document().copy(sportId = "RUNNING")) is SportDocumentValidation.InvalidMetadata)
        assertTrue(registry.validate(document().copy(purpose = "OBSERVATION")) is SportDocumentValidation.InvalidMetadata)
    }

    @Test fun `duplicate ids and log prescription mix are rejected before writing`() {
        assertTrue(registry.validateAll(listOf(document(), document()), setOf(SportDocumentPurpose.PLAN)).isNotEmpty())
        assertTrue(registry.validateAll(listOf(document()), setOf(SportDocumentPurpose.RESULT)).isNotEmpty())
    }

    @Test fun `legacy and future engine content roundtrip without sport guesses`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val old = json.decodeFromString<Session>("""{"id":"old","name":"Gym MMA Run"}""")
        assertTrue(old.sportDocuments.isEmpty())
        val future = document().copy(engineId = "next-engine", version = 99, sportId = "FUTURE_SPORT", purpose = "FUTURE_ROLE",
            payload = buildJsonObject { putJsonObject("opaque") { putJsonArray("future") { add("keep"); add(42) } } })
        val session = old.copy(sportDocuments = listOf(future))
        assertEquals(session, json.decodeFromString<Session>(json.encodeToString(session)))
        val log = WorkoutLog("log", "p", "old", "Gym MMA Run", "2026-10-18", 30, sportDocuments = listOf(future))
        assertEquals(log, json.decodeFromString<WorkoutLog>(json.encodeToString(log)))
        assertTrue(registry.validate(future) is SportDocumentValidation.UnsupportedEngine)
    }

    @Test fun `duplicate registration cannot select an arbitrary engine`() {
        assertThrows(IllegalArgumentException::class.java) { SportTrackingRegistry(listOf(engine, engine)) }
    }
}
