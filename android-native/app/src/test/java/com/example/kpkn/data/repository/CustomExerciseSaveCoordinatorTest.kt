package com.example.kpkn.data.repository

import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CustomExerciseSaveCoordinatorTest {
    private val exercise = ExerciseMuscleInfo(id = "custom:durable", name = "Propio")

    @Test fun noPublicaNiRespondeAntesDeTerminarLaEscritura() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val published = mutableListOf<ExerciseMuscleInfo>()
        val saves = CustomExerciseSaveCoordinator({ entered.complete(Unit); finish.await() }, { published += it })
        val result = async { saves.save(exercise) }
        entered.await()
        assertFalse(result.isCompleted)
        assertTrue(published.isEmpty())
        finish.complete(Unit)
        assertEquals(exercise, result.await().getOrThrow())
        assertEquals(listOf(exercise), published)
    }

    @Test fun falloNoPublicaYReintentoGuardaElMismoUuidYMetadatos() = runTest {
        var attempts = 0
        val published = mutableListOf<ExerciseMuscleInfo>()
        val saves = CustomExerciseSaveCoordinator({ if (++attempts == 1) error("escritor rechazado") }, { published += it })
        val personal = exercise.copy(description = "Mi descripción", descriptionOrigin = "USER",
            cover = com.example.kpkn.data.models.ExerciseCover.userPhoto("photo"))
        assertTrue(saves.save(personal).isFailure)
        assertTrue(published.isEmpty())
        assertEquals(personal, saves.save(personal).getOrThrow())
        assertEquals(listOf(personal), published)
        assertEquals(2, attempts)
    }
}
