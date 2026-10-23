package com.example.kpkn.data.repository

import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Un resultado exitoso significa que el escritor durable terminó, nunca que solo se lanzó una coroutine. */
internal class CustomExerciseSaveCoordinator(
    private val persist: suspend (ExerciseMuscleInfo) -> Unit,
    private val committed: (ExerciseMuscleInfo) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun save(exercise: ExerciseMuscleInfo): Result<ExerciseMuscleInfo> = mutex.withLock {
        try {
            persist(exercise)
            committed(exercise)
            Result.success(exercise)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
}
