package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.exercises.removeCustomExerciseOverlay
import com.example.kpkn.data.exercises.setCustomExerciseOverlay
import com.example.kpkn.data.exercises.upsertCustomExerciseOverlay
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object CustomExerciseRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _customExercises = MutableStateFlow<List<ExerciseMuscleInfo>>(emptyList())
    val customExercises: StateFlow<List<ExerciseMuscleInfo>> = _customExercises.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var db: KpknDatabase

    fun initialize(context: Context) {
        if (initialized) return
        db = KpknDatabase.getInstance(context.applicationContext)
        initialized = true
        scope.launch {
            val saved = runCatching { db.customExerciseDao().getAll().map { it.toExerciseMuscleInfo().copy(isCustom = true) } }
                .getOrDefault(emptyList())
            _customExercises.value = saved
            setCustomExerciseOverlay(saved)
        }
    }

    fun upsert(exercise: ExerciseMuscleInfo) {
        if (!initialized) return
        val normalized = exercise.copy(isCustom = true)
        _customExercises.update { list ->
            val filtered = list.filterNot { it.id.equals(normalized.id, ignoreCase = true) }
            (filtered + normalized).sortedBy { it.name.lowercase() }
        }
        upsertCustomExerciseOverlay(normalized)
        scope.launch {
            runCatching {
                val existing = db.customExerciseDao().getById(normalized.id)
                db.customExerciseDao().upsert(normalized.toEntity(createdAt = existing?.createdAt))
            }
        }
    }

    fun delete(exerciseId: String) {
        if (!initialized) return
        _customExercises.update { list -> list.filterNot { it.id.equals(exerciseId, ignoreCase = true) } }
        removeCustomExerciseOverlay(exerciseId)
        scope.launch {
            runCatching { db.customExerciseDao().delete(exerciseId) }
        }
    }
}
