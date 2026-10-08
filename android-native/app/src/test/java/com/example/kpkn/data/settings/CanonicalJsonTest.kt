package com.example.kpkn.data.settings

import com.example.kpkn.data.sync.CanonicalJson
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CanonicalJsonTest {
    @org.junit.Test fun sharedSourceIdentityHasTheExpectedUtf8Hash() {
        org.junit.Assert.assertEquals("r_27db345fabd14c5e5a84e456105a8ef57fd70e3c4ef6873911153c9d80d85830",
            com.example.kpkn.data.sync.PersonalRecordCodec.wireId("program/session/week/exercise/tag"))
    }
    @Test fun scaleOrderingAndUnicodeMatchTheSharedManifest() {
        assertEquals("{\"a\":[1,0,0.000001,1e-7],\"z\":\"é\"}",
            CanonicalJson.encode(Json.parseToJsonElement("{\"z\":\"é\",\"a\":[1.0,-0.0,0.0000010,0.0000001]}")))
    }
}
