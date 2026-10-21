package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.exercises.removeCustomExerciseOverlay
import com.example.kpkn.data.exercises.setCustomExerciseOverlay
import com.example.kpkn.data.exercises.upsertCustomExerciseOverlay
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseCover
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _customExercises = MutableStateFlow<List<ExerciseMuscleInfo>>(emptyList())
    val customExercises: StateFlow<List<ExerciseMuscleInfo>> = _customExercises.asStateFlow()

    /**
     * El último fallo al guardar un ejercicio propio (null = ninguno). Un guardado que Room rechaza se REVIERTE en la lista y en el
     * catálogo en vez de dejar un ejercicio «fantasma» que desaparece al reiniciar (AUD2-P05).
     */
    private val _lastPersistError = MutableStateFlow<String?>(null)
    val lastPersistError: StateFlow<String?> = _lastPersistError.asStateFlow()

    fun clearPersistError() {
        _lastPersistError.value = null
    }

    @Volatile
    private var initialized = false
    private lateinit var db: KpknDatabase
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        db = KpknDatabase.getInstance(appContext)
        if (initialized) return
        initialized = true
        scope.launch { refreshFromStorage() }
    }

    suspend fun refreshFromStorage() {
        if (!::appContext.isInitialized) return
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
        val previous = _customExercises.value.firstOrNull { it.id.equals(normalized.id, ignoreCase = true) }
        _customExercises.update { list ->
            val filtered = list.filterNot { it.id.equals(normalized.id, ignoreCase = true) }
            (filtered + normalized).sortedBy { it.name.lowercase() }
        }
        upsertCustomExerciseOverlay(normalized)
        scope.launch {
            runCatching {
                val existing = db.customExerciseDao().getById(normalized.id)
                db.customExerciseDao().upsert(normalized.toEntity(createdAt = existing?.createdAt))
            }.onFailure {
                revertUpsert(normalized, previous)
            }
        }
    }

    /** Deshace una publicación optimista cuyo guardado falló: vuelve el ejercicio anterior o lo quita si era nuevo. */
    private fun revertUpsert(failed: ExerciseMuscleInfo, previous: ExerciseMuscleInfo?) {
        if (previous != null) {
            _customExercises.update { list ->
                (list.filterNot { it.id.equals(failed.id, ignoreCase = true) } + previous).sortedBy { it.name.lowercase() }
            }
            upsertCustomExerciseOverlay(previous)
        } else {
            _customExercises.update { list -> list.filterNot { it.id.equals(failed.id, ignoreCase = true) } }
            removeCustomExerciseOverlay(failed.id)
        }
        _lastPersistError.value = "No se pudo guardar «${failed.name}»."
    }

    /**
     * Elige (o quita, con null) la portada de un ejercicio propio: una foto del álbum, la imagen de referencia de una variante
     * próxima o la ilustración del patrón. Conserva el UUID y todo lo demás del ejercicio (descripción, datos manuales).
     */
    fun setCover(exerciseId: String, cover: ExerciseCover?) {
        val current = _customExercises.value.firstOrNull { it.id.equals(exerciseId, ignoreCase = true) } ?: return
        upsert(current.copy(cover = cover))
    }

    fun delete(exerciseId: String) {
        if (!initialized) return
        _customExercises.update { list -> list.filterNot { it.id.equals(exerciseId, ignoreCase = true) } }
        removeCustomExerciseOverlay(exerciseId)
        scope.launch(NonCancellable) {
            runCatching { db.customExerciseDao().delete(exerciseId) }
        }
    }
}
