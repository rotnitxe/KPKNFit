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

    suspend fun get(): NutritionCalibrationProfile? = withContext(Dispatchers.IO) {
        db.nutritionDao().getCalibrationProfile()?.let { entity ->
            runCatching { dbJson.decodeFromString<NutritionCalibrationProfile>(entity.data) }.getOrNull()
        }
    }

    suspend fun save(profile: NutritionCalibrationProfile) = updateMutex.withLock { write(profile) }

    /** Read-modify-write serialized across every logger and calibration caller. */
    suspend fun update(transform: (NutritionCalibrationProfile) -> NutritionCalibrationProfile) = updateMutex.withLock {
        write(transform(get() ?: NutritionCalibrationProfile()))
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

    suspend fun clear() = updateMutex.withLock {
        withContext(Dispatchers.IO) { db.nutritionDao().clearCalibrationProfile() }
    }

    companion object {
        private val updateMutex = Mutex()
        internal fun forDatabase(context: Context, db: KpknDatabase) = NutritionCalibrationRepository(context, db)
        @Volatile private var instance: NutritionCalibrationRepository? = null

        fun getInstance(context: Context): NutritionCalibrationRepository =
            instance ?: synchronized(this) {
                instance ?: NutritionCalibrationRepository(context.applicationContext).also { instance = it }
            }
    }
}
