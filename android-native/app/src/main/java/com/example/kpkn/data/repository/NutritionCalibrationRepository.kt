package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionCalibrationProfileEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.NutritionCalibrationProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class NutritionCalibrationRepository private constructor(context: Context, private val db: KpknDatabase = KpknDatabase.getInstance(context.applicationContext)) {

    private val writeOwner = com.example.kpkn.data.backup.DataSessionWriter("nutrition-calibration")

    suspend fun get(): NutritionCalibrationProfile? = writeOwner.write {
        db.nutritionDao().getCalibrationProfile()?.let { entity ->
            runCatching { dbJson.decodeFromString<NutritionCalibrationProfile>(entity.data) }.getOrNull()
        }
    }

    suspend fun save(profile: NutritionCalibrationProfile) = writeOwner.durableWrite { updateMutex.withLock { write(profile) } }

    /** Read-modify-write serialized across every logger and calibration caller. */
    suspend fun update(transform: (NutritionCalibrationProfile) -> NutritionCalibrationProfile) = writeOwner.durableWrite {
        updateMutex.withLock { write(transform(get() ?: NutritionCalibrationProfile())) }
    }

    private suspend fun write(profile: NutritionCalibrationProfile) = withContext(Dispatchers.IO) {
        db.nutritionDao().upsertCalibrationProfile(
            NutritionCalibrationProfileEntity(
                schemaVersion = profile.schemaVersion,
                data = dbJson.encodeToString(profile),
                updatedAt = profile.updatedAtEpochMs,
            ),
        )
    }

    suspend fun clear() = writeOwner.durableWrite {
        updateMutex.withLock { db.nutritionDao().clearCalibrationProfile() }
    }

    companion object {
        private val updateMutex = Mutex()
        internal fun forDatabase(context: Context, db: KpknDatabase) = NutritionCalibrationRepository(context, db)
        @Volatile private var instance: NutritionCalibrationRepository? = null
        internal suspend fun closeForDataSession() = updateMutex.withLock { instance?.writeOwner?.closeAdmission(); instance = null }

        fun getInstance(context: Context): NutritionCalibrationRepository =
            instance ?: synchronized(this) {
                instance ?: NutritionCalibrationRepository(context.applicationContext).also { instance = it }
            }
    }
}
