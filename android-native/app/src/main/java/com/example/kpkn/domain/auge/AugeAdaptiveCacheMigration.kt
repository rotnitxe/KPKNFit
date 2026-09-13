package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache

object AugeAdaptiveCacheMigration {
    const val CURRENT_SCHEMA = 3

    /**
     * v1→v2: τ/deltas/multiplicadores musculares aprendidos con hoursSince≈0.5.
     * v2→v3: τ de Energía/Columna invertidos con k=1/τ (e-folding) mientras el
     * learner usaba k=2.9957/τ; deltas y multiplicadores de sistema zombis.
     * Los τ musculares de v2 se conservan.
     */
    fun migrate(cache: AugeAdaptiveCache, storedVersion: Int): AugeAdaptiveCache {
        var next = cache
        if (storedVersion < 2) {
            next = next.copy(
                muscleDeltas = emptyMap(),
                muscleDrainMultipliers = emptyMap(),
                personalizedRecoveryHours = emptyMap(),
            )
        }
        if (storedVersion < 3) {
            next = next.copy(
                cnsLearningDelta = 0.0,
                spinalLearningDelta = 0.0,
                cnsRecoveryHours = null,
                spinalRecoveryHours = null,
                cnsDrainMultiplier = 1.0,
                spinalDrainMultiplier = 1.0,
                schemaVersion = CURRENT_SCHEMA,
            )
        } else {
            next = next.copy(schemaVersion = maxOf(next.schemaVersion, CURRENT_SCHEMA))
        }
        return next
    }
}
