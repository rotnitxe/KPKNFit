package com.example.kpkn.domain.entitlements

import org.junit.Assert.*
import org.junit.Test

class EntitlementPolicyTest {
    private val paid = ConfirmedEntitlement(PlanLevel.PRO, "ACTIVE", 2000, 1000)
    @Test fun confirmedPeriodUsesTrustedTimeAndEndsAtVerifiedExpiry() {
        assertEquals(PlanLevel.PRO, EntitlementPolicy.level(paid, 1999))
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid, 2000))
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid, null))
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid, 999))
    }
    @Test fun pendingAndHoldNeverUnlockAndCancellationKeepsRemainingAccess() {
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid.copy(state = "PENDING"), 1500))
        assertEquals(PlanLevel.FREE, EntitlementPolicy.level(paid.copy(state = "ON_HOLD"), 1500))
        assertEquals(PlanLevel.PRO, EntitlementPolicy.level(paid.copy(state = "CANCELED"), 1500))
        assertEquals(PlanLevel.PRO, EntitlementPolicy.level(paid.copy(state = "GRACE"), 1500))
    }
}
