package com.example.kpkn.domain.account

object AccountIdentityPolicy {
    fun state(profileKey: String, selectedUid: String?, authenticatedUid: String?, sdkAvailable: Boolean,
        recoveryNeeded: Boolean, email: String? = null, displayName: String? = null): AccountState = when {
        recoveryNeeded || (!sdkAvailable && selectedUid != null) || authenticatedUid != selectedUid ->
            AccountState.NeedsRecovery(authenticatedUid, selectedUid)
        !sdkAvailable -> AccountState.Unavailable
        authenticatedUid == null -> AccountState.Guest(profileKey)
        else -> AccountState.SignedIn(authenticatedUid, email, displayName)
    }
}
