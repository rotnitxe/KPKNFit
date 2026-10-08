package com.example.kpkn.domain.entitlements

/** Anchors are authenticated by the platform repository, never accepted from backups. */
object OfflineEntitlementClock {
    data class Reading(val epochMs: Long?, val errorCode: String? = null)
    private const val ROLLBACK_TOLERANCE_MS = 120_000L
    fun read(serverEpochMs: Long, savedElapsedMs: Long, savedBoot: Int, savedWallMs: Long,
        elapsedMs: Long, boot: Int, wallMs: Long, highWaterMs: Long): Reading {
        if (serverEpochMs <= 0 || elapsedMs < 0 || highWaterMs < 0) return Reading(null, "CLOCK_UNCERTAIN")
        val epoch = if (boot >= 0 && savedBoot == boot && elapsedMs >= savedElapsedMs) {
            safeAdd(serverEpochMs, elapsedMs - savedElapsedMs)
        } else {
            // Across reboot use the signed server/wall offset. A rollback requires
            // fresh server time; it can never extend or revive an expired receipt.
            if (savedWallMs <= 0 || wallMs < savedWallMs - ROLLBACK_TOLERANCE_MS)
                return Reading(null, "CLOCK_UNCERTAIN")
            safeAdd(serverEpochMs, maxOf(0, wallMs - savedWallMs))
        }
        return Reading(epoch?.let { maxOf(it, highWaterMs) }, if (epoch == null) "CLOCK_UNCERTAIN" else null)
    }
    private fun safeAdd(base: Long, delta: Long): Long? =
        if (delta < 0 || base > Long.MAX_VALUE - delta) null else base + delta
}
