package com.example.kpkn.domain.entitlements

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable data class ConfirmedEntitlement(val level: PlanLevel = PlanLevel.FREE, val state: String = "NONE",
    val validUntilEpochMs: Long = 0, val confirmedAtEpochMs: Long = 0)
data class EntitlementState(val confirmed: ConfirmedEntitlement = ConfirmedEntitlement(), val effectiveLevel: PlanLevel = PlanLevel.FREE,
    val checking: Boolean = false, val errorCode: String? = null)
interface EntitlementRepository {
    val state: StateFlow<EntitlementState>
    suspend fun refresh()
    suspend fun verifyPurchase(purchaseToken: String)
}
object EntitlementPolicy {
    fun level(confirmed: ConfirmedEntitlement, trustedEpochMs: Long?): PlanLevel =
        if (trustedEpochMs != null && confirmed.level == PlanLevel.PRO && confirmed.state in setOf("ACTIVE", "GRACE", "CANCELED") &&
            trustedEpochMs >= confirmed.confirmedAtEpochMs && trustedEpochMs < confirmed.validUntilEpochMs) PlanLevel.PRO else PlanLevel.FREE
}
