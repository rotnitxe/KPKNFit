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
import kotlinx.coroutines.async

/** Operaciones puras sobre la lista de ejercicios propios: lo que se publica de forma optimista y cómo se revierte un guardado fallido. */
internal object CustomExerciseListOps {
    private fun sameId(a: String, b: String) = a.equals(b, ignoreCase = true)

    fun withExercise(list: List<ExerciseMuscleInfo>, exercise: ExerciseMuscleInfo): List<ExerciseMuscleInfo> =
        (list.filterNot { sameId(it.id, exercise.id) } + exercise).sortedBy { it.name.lowercase() }

    fun withoutExercise(list: List<ExerciseMuscleInfo>, id: String): List<ExerciseMuscleInfo> =
        list.filterNot { sameId(it.id, id) }

    /** El estado tras un guardado fallido: vuelve el ejercicio anterior, o desaparece si era nuevo. */
    fun reverted(list: List<ExerciseMuscleInfo>, failed: ExerciseMuscleInfo, previous: ExerciseMuscleInfo?): List<ExerciseMuscleInfo> =
        if (previous != null) withExercise(list, previous) else withoutExercise(list, failed.id)
}

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

    private val saves = CustomExerciseSaveCoordinator(
        persist = { exercise ->
            val existing = db.customExerciseDao().getById(exercise.id)
            db.customExerciseDao().upsert(exercise.toEntity(createdAt = existing?.createdAt))
        },
        committed = { exercise ->
            _customExercises.update { CustomExerciseListOps.withExercise(it, exercise) }
            upsertCustomExerciseOverlay(exercise)
            _lastPersistError.value = null
        },
    )

    /** CAT-B: no publica ni cierra el formulario hasta que Room confirma el guardado. */
    suspend fun upsertAndAwait(exercise: ExerciseMuscleInfo): Result<ExerciseMuscleInfo> {
        if (!initialized) {
            val message = "El almacenamiento de ejercicios todavía no está disponible. Inténtalo de nuevo."
            _lastPersistError.value = message
            return Result.failure(IllegalStateException(message))
        }
        val normalized = exercise.copy(isCustom = true)
        // El trabajo pertenece al repositorio: una recreación del formulario no interrumpe una escritura iniciada.
        return scope.async {
            saves.save(normalized).onFailure {
                _lastPersistError.value = "No se pudo guardar «${normalized.name}»."
            }
        }.await()
    }

    fun upsert(exercise: ExerciseMuscleInfo) {
        if (!initialized) return
        val normalized = exercise.copy(isCustom = true)
        val previous = _customExercises.value.firstOrNull { it.id.equals(normalized.id, ignoreCase = true) }
        _customExercises.update { list -> CustomExerciseListOps.withExercise(list, normalized) }
        upsertCustomExerciseOverlay(normalized)
        scope.launch {
            saves.save(normalized).onFailure {
                revertUpsert(normalized, previous)
            }
        }
    }

    /** Deshace una publicación optimista cuyo guardado falló: vuelve el ejercicio anterior o lo quita si era nuevo. */
    private fun revertUpsert(failed: ExerciseMuscleInfo, previous: ExerciseMuscleInfo?) {
        val current = _customExercises.value.firstOrNull { it.id.equals(failed.id, ignoreCase = true) }
        if (current == failed) {
            _customExercises.update { list -> CustomExerciseListOps.reverted(list, failed, previous) }
            if (previous != null) upsertCustomExerciseOverlay(previous) else removeCustomExerciseOverlay(failed.id)
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
