package com.example.kpkn.data.settings

import com.example.kpkn.data.sync.CanonicalJson
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalJsonTest {
    @Test fun sharedProgramRunEnvelopePreservesPriorExecutionAndFutureMetadata() {
        val fixture = generateSequence(java.io.File(System.getProperty("user.dir"))) { it.parentFile }
            .map { java.io.File(it, "cloud-sync/contracts/program-run.fixture.json") }.first { it.isFile }
        val source = Json.parseToJsonElement(fixture.readText()).jsonObject
        val operation = Json.decodeFromJsonElement(com.example.kpkn.domain.sync.PersonalOperation.serializer(), source)
        val roundTrip = Json.encodeToJsonElement(com.example.kpkn.domain.sync.PersonalOperation.serializer(), operation).jsonObject
        assertEquals(source.getValue("payload"), roundTrip.getValue("payload"))
        assertEquals("run-current", operation.payload!!.getValue("runState").jsonObject.getValue("runId").jsonPrimitive.content)
        assertEquals("run-previous", operation.payload!!.getValue("nativeProgressionAudit").jsonArray[0].jsonObject.getValue("programRunId").jsonPrimitive.content)
        assertEquals(source.getValue("zoneId"), roundTrip.getValue("zoneId"))
        assertEquals(source.getValue("unitSystem"), roundTrip.getValue("unitSystem"))
    }
    @org.junit.Test fun sharedSourceIdentityHasTheExpectedUtf8Hash() {
        org.junit.Assert.assertEquals("r_27db345fabd14c5e5a84e456105a8ef57fd70e3c4ef6873911153c9d80d85830",
            com.example.kpkn.data.sync.PersonalRecordCodec.wireId("program/session/week/exercise/tag"))
    }
    @Test fun scaleOrderingAndUnicodeMatchTheSharedManifest() {
        assertEquals("{\"a\":[1,0,0.000001,1e-7],\"z\":\"é\"}",
            CanonicalJson.encode(Json.parseToJsonElement("{\"z\":\"é\",\"a\":[1.0,-0.0,0.0000010,0.0000001]}")))
    }
}
