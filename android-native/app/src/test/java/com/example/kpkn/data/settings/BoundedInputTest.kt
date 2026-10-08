package com.example.kpkn.data.settings

import com.example.kpkn.data.backup.BoundedInput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun unknownLengthInputIsRejectedBeforeUnboundedAllocation() {
        val input = ByteArrayInputStream(ByteArray(100000))
        val output = ByteArrayOutputStream()
        try { BoundedInput.copy(input, output, 12000); fail("Oversized input accepted") }
        catch (_: IllegalArgumentException) { assertTrue(output.size() <= 12000) }
    }
    @Test fun exactLimitIsAccepted() {
        assertArrayEquals(byteArrayOf(1, 2, 3), BoundedInput.bytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
    }
}
