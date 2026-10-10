package com.example.kpkn.data.account

import com.example.kpkn.domain.entitlements.EntitlementState

/** Versión publicada: no hay interruptor de Pro. Es la identidad; la de depuración está en `src/debug`. */
internal object BuildEntitlementOverride {
    fun apply(state: EntitlementState): EntitlementState = state
}
