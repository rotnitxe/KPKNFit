package com.example.kpkn.data.account

import com.example.kpkn.domain.entitlements.EntitlementState
import com.example.kpkn.domain.entitlements.PlanLevel

/*
 * SOLO DEPURACIÓN. Interruptor de Pro para grabar la presentación. Vive en memoria y no se persiste: al morir el proceso
 * vuelve a Free. La versión publicada (`src/release`) tiene la misma firma y no tiene interruptor.
 */
internal object BuildEntitlementOverride {
    @Volatile private var forcedPro = false

    fun setForcedPro(enabled: Boolean) {
        forcedPro = enabled
    }

    fun apply(state: EntitlementState): EntitlementState =
        if (forcedPro) state.copy(effectiveLevel = PlanLevel.PRO) else state
}
