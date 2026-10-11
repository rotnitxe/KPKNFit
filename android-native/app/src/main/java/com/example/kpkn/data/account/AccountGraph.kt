package com.example.kpkn.data.account

import com.example.kpkn.domain.entitlements.EntitlementState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** In-memory compatibility for the independently implemented debug entitlement override. */
object AccountGraph {
    private val _entitlement = MutableStateFlow(EntitlementState())
    private var rawEntitlement = EntitlementState()
    val entitlement = _entitlement.asStateFlow()

    private fun publish(value: EntitlementState) {
        rawEntitlement = value
        _entitlement.value = BuildEntitlementOverride.apply(value)
    }

    internal fun republishEntitlement() = publish(rawEntitlement)

    suspend fun stop(resetEntitlement: Boolean = true) {
        if (resetEntitlement) withContext(Dispatchers.Main) { publish(EntitlementState()) }
    }
}
