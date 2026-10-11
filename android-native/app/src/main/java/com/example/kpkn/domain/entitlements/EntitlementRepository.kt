package com.example.kpkn.domain.entitlements

data class ConfirmedEntitlement(val level: PlanLevel = PlanLevel.FREE, val state: String = "NONE",
    val validUntilEpochMs: Long = 0, val confirmedAtEpochMs: Long = 0)
data class EntitlementState(val confirmed: ConfirmedEntitlement = ConfirmedEntitlement(), val effectiveLevel: PlanLevel = PlanLevel.FREE,
    val checking: Boolean = false, val errorCode: String? = null)
