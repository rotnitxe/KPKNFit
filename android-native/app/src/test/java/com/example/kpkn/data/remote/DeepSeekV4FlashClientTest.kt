package com.example.kpkn.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepSeekV4FlashClientTest {

    @Test
    fun fixesTheOnlyAllowedModelAndEndpoint() {
        assertEquals("deepseek-v4-flash", DeepSeekV4FlashClient.MODEL)
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekV4FlashClient.ENDPOINT,
        )
        assertFalse(DeepSeekV4FlashClient.MODEL.contains("pro", ignoreCase = true))
        assertFalse(DeepSeekV4FlashClient.MODEL.contains("chat", ignoreCase = true))
        assertFalse(DeepSeekV4FlashClient.MODEL.contains("reasoner", ignoreCase = true))
    }
}
