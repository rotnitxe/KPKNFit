package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WikiLabDao {

    // ─── Muscle Groups ───────────────────────────────────────────────────

    @Query("SELECT * FROM muscle_groups ORDER BY name")
    fun getAllMuscles(): Flow<List<MuscleGroupEntity>>

    @Query("SELECT * FROM muscle_groups WHERE id = :id")
    fun getMuscleById(id: String): Flow<MuscleGroupEntity?>

    @Query("SELECT * FROM muscle_groups WHERE bodyPart = :bodyPart ORDER BY name")
    fun getMusclesByBodyPart(bodyPart: String): Flow<List<MuscleGroupEntity>>

    @Query("SELECT COUNT(*) FROM muscle_groups")
    suspend fun getMuscleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscles(muscles: List<MuscleGroupEntity>)

    @Query("DELETE FROM muscle_groups")
    suspend fun clearMuscles()

    // ─── Joints ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM joints ORDER BY name")
    fun getAllJoints(): Flow<List<JointEntity>>

    @Query("SELECT * FROM joints WHERE id = :id")
    fun getJointById(id: String): Flow<JointEntity?>

    @Query("SELECT * FROM joints WHERE bodyPart = :bodyPart ORDER BY name")
    fun getJointsByBodyPart(bodyPart: String): Flow<List<JointEntity>>

    @Query("SELECT COUNT(*) FROM joints")
    suspend fun getJointCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoints(joints: List<JointEntity>)

    @Query("DELETE FROM joints")
    suspend fun clearJoints()

    // ─── Tendons ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM tendons ORDER BY name")
    fun getAllTendons(): Flow<List<TendonEntity>>

    @Query("SELECT * FROM tendons WHERE id = :id")
    fun getTendonById(id: String): Flow<TendonEntity?>

    @Query("SELECT * FROM tendons WHERE muscleId = :muscleId")
    fun getTendonsByMuscle(muscleId: String): Flow<List<TendonEntity>>

    @Query("SELECT * FROM tendons WHERE jointId = :jointId")
    fun getTendonsByJoint(jointId: String): Flow<List<TendonEntity>>

    @Query("SELECT COUNT(*) FROM tendons")
    suspend fun getTendonCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTendons(tendons: List<TendonEntity>)

    @Query("DELETE FROM tendons")
    suspend fun clearTendons()

    // ─── Movement Patterns ───────────────────────────────────────────────

    @Query("SELECT * FROM movement_patterns ORDER BY name")
    fun getAllPatterns(): Flow<List<MovementPatternEntity>>

    @Query("SELECT * FROM movement_patterns WHERE id = :id")
    fun getPatternById(id: String): Flow<MovementPatternEntity?>

    @Query("SELECT COUNT(*) FROM movement_patterns")
    suspend fun getPatternCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<MovementPatternEntity>)

    @Query("DELETE FROM movement_patterns")
    suspend fun clearPatterns()

    // ─── Kinetic Chains ──────────────────────────────────────────────────

    @Query("SELECT * FROM kinetic_chains ORDER BY name")
    fun getAllChains(): Flow<List<KineticChainEntity>>

    @Query("SELECT * FROM kinetic_chains WHERE id = :id")
    fun getChainById(id: String): Flow<KineticChainEntity?>

    @Query("SELECT COUNT(*) FROM kinetic_chains")
    suspend fun getChainCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChains(chains: List<KineticChainEntity>)

    @Query("DELETE FROM kinetic_chains")
    suspend fun clearChains()

    // ─── Bulk Count (for prepopulate check) ──────────────────────────────

    @Query("SELECT COUNT(*) FROM muscle_groups")
    fun getMuscleCountFlow(): Flow<Int>
}
