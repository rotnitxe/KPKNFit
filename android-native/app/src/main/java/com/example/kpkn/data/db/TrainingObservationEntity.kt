package com.example.kpkn.data.db

import androidx.room.*

/** Source identity is unique. Equal values from different original observations remain distinct. */
@kotlinx.serialization.Serializable
@Entity(tableName = "training_observations")
data class TrainingObservationEntity(
    @PrimaryKey val sourceIdentity: String,
    val kind: String,
    val date: String,
    val payload: String,
    val provenance: String,
)

@Dao
interface TrainingObservationDao {
    @Query("SELECT * FROM training_observations ORDER BY date DESC, sourceIdentity")
    suspend fun all(): List<TrainingObservationEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun preserve(original: TrainingObservationEntity)
}
