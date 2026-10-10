package com.example.kpkn.data.backup

/** Explicit operation-local diagnostics; production callers use the no-op observer. */
internal enum class MaintenancePhase {
    IMPORT_COPYING, SNAPSHOT_ROW_COPIED, DATABASE_ROW_COPIED, ROLLBACK_DATABASE_COPIED, ROLLBACK_FILE_COPYING,
    PREPARED_SYNCED, PORTABLE_ROOT_REPLACED, DATABASE_APPLIED, PREFERENCES_APPLIED,
    COMMIT_JOURNAL_OPENED, COMMITTED_SYNCED,
    RECOVERY_ROOT_RESTORED, RECOVERY_DATABASE_APPLIED, RECOVERY_PREFERENCES_APPLIED,
    RECOVERY_SYNCED, REOPEN_STARTED, REOPEN_READBACK,
}

internal fun interface MaintenanceCheckpoint {
    fun reached(phase: MaintenancePhase)
    companion object { val None = MaintenanceCheckpoint { } }
}
