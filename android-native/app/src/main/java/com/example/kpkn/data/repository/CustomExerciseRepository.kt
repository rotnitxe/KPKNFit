package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.exercises.removeCustomExerciseOverlay
import com.example.kpkn.data.exercises.setCustomExerciseOverlay
import com.example.kpkn.data.exercises.upsertCustomExerciseOverlay
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object CustomExerciseRepository {

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var writeOwner: com.example.kpkn.data.backup.DataSessionWriter? = null
    private val _customExercises = MutableStateFlow<List<ExerciseMuscleInfo>>(emptyList())
    val customExercises: StateFlow<List<ExerciseMuscleInfo>> = _customExercises.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var db: KpknDatabase
    private lateinit var appContext: Context

    fun initialize(context: Context): Unit = synchronized(this) {
        if (initialized) return@synchronized
        val owner = com.example.kpkn.data.backup.DataSessionWriter("custom-exercise-repository")
        owner.immediate {
            appContext = if (com.example.kpkn.data.account.ProfileStorage.initialized())
                com.example.kpkn.data.account.ProfileStorage.context(context.applicationContext) else context.applicationContext
            db = KpknDatabase.getInstance(appContext)
            writeOwner = owner
            scope = com.example.kpkn.data.backup.DataSessionWriterScope(CoroutineScope(Dispatchers.IO + SupervisorJob()), owner)
            initialized = true
            owner.launch(scope) { refreshFromStorage() }
        }
    }

    suspend fun refreshFromStorage() = requireNotNull(writeOwner).write accepted@ {
        if (!::appContext.isInitialized) return@accepted
        db = KpknDatabase.getInstance(appContext)
        val saved = runCatching {
            db.customExerciseDao().getAll().map { it.toExerciseMuscleInfo().copy(isCustom = true) }
        }.getOrDefault(emptyList())
        _customExercises.value = saved
        setCustomExerciseOverlay(saved)
    }

    fun upsert(exercise: ExerciseMuscleInfo) {
        if (!initialized) return
        val normalized = exercise.copy(isCustom = true)
        _customExercises.update { list ->
            val filtered = list.filterNot { it.id.equals(normalized.id, ignoreCase = true) }
            (filtered + normalized).sortedBy { it.name.lowercase() }
        }
        upsertCustomExerciseOverlay(normalized)
        requireNotNull(writeOwner).launch(scope) {
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
        requireNotNull(writeOwner).launch(scope) {
            runCatching { db.customExerciseDao().delete(exerciseId) }
        }
    }
    internal suspend fun closeForDataSession() {
        writeOwner?.closeAdmission()
        initialized = false
        kotlinx.coroutines.withTimeout(30_000) { scope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList()?.forEach { it.join() } }
        scope.coroutineContext[kotlinx.coroutines.Job]?.let { job -> job.cancel(); job.join() }
        _customExercises.value = emptyList()
        setCustomExerciseOverlay(emptyList())
        writeOwner = null
    }
}
