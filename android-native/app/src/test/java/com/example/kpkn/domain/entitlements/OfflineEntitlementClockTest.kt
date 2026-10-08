package com.example.kpkn.domain.entitlements

import org.junit.Assert.*
import org.junit.Test

class OfflineEntitlementClockTest {
    private val server = 1_000_000L
    private val wall = 2_000_000L
    private val paid = ConfirmedEntitlement(PlanLevel.PRO, "ACTIVE", server + 600_000, server)
    private fun read(boot: Int = 7, elapsed: Long = 2000, currentWall: Long = wall,
        highWater: Long = server) = OfflineEntitlementClock.read(server, 1000, 7, wall, elapsed, boot, currentWall, highWater)
    @Test fun sameBootUsesMonotonicTimeDespiteClockEdits() {
        assertEquals(server + 1000, read(currentWall = 1).epochMs)
        assertEquals(server + 1000, read(currentWall = Long.MAX_VALUE).epochMs)
    }
    @Test fun offlineRebootRetainsVerifiedProUntilExpiry() {
        assertEquals(PlanLevel.PRO, EntitlementPolicy.level(paid, read(boot = 8, currentWall = wall + 300_000).epochMs))
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid, read(boot = 8, currentWall = wall + 600_000).epochMs))
    }
    @Test fun rollbackAcrossBootRequiresServerAndCannotReviveExpiry() {
        assertEquals("CLOCK_UNCERTAIN", read(boot = 8, currentWall = wall - 120_001).errorCode)
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid, read(boot = 8, highWater = paid.validUntilEpochMs).epochMs))
    }
    @Test fun clockCorrectionCannotDecreaseSignedHighWater() {
        assertEquals(server + 5000, read(boot = 8, currentWall = wall - 10_000, highWater = server + 5000).epochMs)
    }
    @Test fun legacyReceiptNeedsServerOnlyAfterReboot() {
        assertEquals(server + 1000, OfflineEntitlementClock.read(server, 1000, 7, 0, 2000, 7, wall, 0).epochMs)
        assertNull(OfflineEntitlementClock.read(server, 1000, 7, 0, 2000, 8, wall, 0).epochMs)
    }
    @Test fun invalidAndOverflowingTimesNeverUnlock() {
        assertNull(OfflineEntitlementClock.read(Long.MAX_VALUE, 0, 7, wall, 1, 7, wall, 0).epochMs)
        assertNull(read(boot = -1, currentWall = 0).epochMs)
    }
}
