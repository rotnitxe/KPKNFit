package com.example.kpkn.data.db

import android.content.Context
import com.example.kpkn.data.models.ExerciseMuscleInfo
import r4.jvm.unavailable

class KpknDatabase {
    init { unavailable("KpknDatabase constructor") }
    fun customExerciseDao(): JvmCustomExerciseDao = unavailable("KpknDatabase.customExerciseDao")
    companion object {
        fun getInstance(context: Context): KpknDatabase = unavailable("KpknDatabase.getInstance")
    }
}
class JvmCustomExerciseDao {
    init { unavailable("JvmCustomExerciseDao constructor") }
    suspend fun getAll(): List<JvmCustomExerciseEntity> = unavailable("customExerciseDao.getAll")
    suspend fun upsert(entity: JvmCustomExerciseEntity): Unit = unavailable("customExerciseDao.upsert")
}
class JvmCustomExerciseEntity {
    init { unavailable("JvmCustomExerciseEntity constructor") }
}
fun JvmCustomExerciseEntity.toExerciseMuscleInfo(): ExerciseMuscleInfo = unavailable("customExercise.toExerciseMuscleInfo")
fun ExerciseMuscleInfo.toEntity(): JvmCustomExerciseEntity = unavailable("ExerciseMuscleInfo.toEntity")
