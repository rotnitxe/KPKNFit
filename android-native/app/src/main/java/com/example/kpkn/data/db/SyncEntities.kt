package com.example.kpkn.data.db

import androidx.room.*

@Entity(tableName = "sync_configuration")
data class SyncConfigurationEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val applyingRemote: Boolean = false,
    val accountGeneration: Long = 0,
    val deviceId: String = "",
    val pullCursor: Long = 0,
    val confirmedAtEpochMs: Long = 0,
)

/** One durable dirty marker per entity. The immutable in-flight operation survives process death. */
@Entity(tableName = "sync_mutations", primaryKeys = ["entityTable", "rawKey"])
data class SyncMutationEntity(
    val entityTable: String,
    val rawKey: String,
    val dirtyVersion: Long,
    val deleted: Boolean,
    val baseRevision: Long = 0,
    val operationId: String? = null,
    val operationJson: String? = null,
    val sentVersion: Long = 0,
    val status: String = "WAITING",
)

@Entity(tableName = "sync_shadows", primaryKeys = ["entityTable", "rawKey"])
data class SyncShadowEntity(
    val entityTable: String,
    val rawKey: String,
    val serverRevision: Long,
    val payloadJson: String?,
    val deleted: Boolean,
)

@Dao
interface SyncJournalDao {
    @Query("SELECT * FROM sync_configuration WHERE id=1") suspend fun configuration(): SyncConfigurationEntity?
    @Upsert suspend fun configure(value: SyncConfigurationEntity)
    @Query("SELECT * FROM sync_mutations WHERE status != 'CONFLICT' AND (status != 'CAPABILITY_WAIT' OR deleted=1) ORDER BY entityTable, rawKey LIMIT :limit")
    suspend fun pending(limit: Int = 8): List<SyncMutationEntity>
    @Query("SELECT COUNT(*) FROM sync_mutations WHERE status='CAPABILITY_WAIT' AND deleted=0")
    suspend fun waitingForProCount(): Int
    @Query("UPDATE sync_mutations SET status=CASE WHEN operationJson IS NULL THEN 'WAITING' ELSE 'SENDING' END WHERE status='CAPABILITY_WAIT'")
    suspend fun releaseCapabilityWait()
    @Query("SELECT * FROM sync_mutations WHERE entityTable=:table AND rawKey=:key")
    suspend fun mutation(table: String, key: String): SyncMutationEntity?
    @Upsert suspend fun upsert(value: SyncMutationEntity)
    @Query("DELETE FROM sync_mutations WHERE entityTable=:table AND rawKey=:key") suspend fun remove(table: String, key: String)
    @Query("SELECT * FROM sync_shadows WHERE entityTable=:table AND rawKey=:key") suspend fun shadow(table: String, key: String): SyncShadowEntity?
    @Upsert suspend fun confirm(value: SyncShadowEntity)
    @Query("SELECT COUNT(*) FROM sync_mutations") suspend fun pendingCount(): Int
    @Query("SELECT * FROM sync_mutations WHERE status='CONFLICT' AND operationId=:operationId LIMIT 1")
    suspend fun conflicted(operationId: String): SyncMutationEntity?
}
