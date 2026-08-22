package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.Json

class CardioHiitDefaultsTest {

    @Test
    fun hiitAndSitUseTheirProtocolDefaultRpe() {
        assertEquals(9.0, CardioHiitConfig(protocol = HiitProtocol.HIIT).targetRpe, 0.0)
        assertEquals(10.0, CardioHiitConfig(protocol = HiitProtocol.SIT).targetRpe, 0.0)
    }

    @Test
    fun explicitRpeStillOverridesTheProtocolDefault() {
        assertEquals(7.5, CardioHiitConfig(protocol = HiitProtocol.SIT, targetRpe = 7.5).targetRpe, 0.0)
    }

    @Test
    fun legacyJsonWithoutTargetRpeUsesProtocolDefault() {
        val json = Json { ignoreUnknownKeys = true }
        val hiit = json.decodeFromString<CardioHiitConfig>("{\"protocol\":\"HIIT\"}")
        val sit = json.decodeFromString<CardioHiitConfig>("{\"protocol\":\"SIT\"}")
        assertEquals(9.0, hiit.targetRpe, 0.0)
        assertEquals(10.0, sit.targetRpe, 0.0)
    }
}
