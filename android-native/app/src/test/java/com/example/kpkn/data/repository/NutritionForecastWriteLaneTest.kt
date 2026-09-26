package com.example.kpkn.data.repository

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vía de escritura de la previsión (M6): CAS de origen + publicación dentro de
 * la MISMA sección crítica que usa el editor, de modo que la caché nunca queda
 * detrás de la BD ni se pisa un plan más nuevo. Sin Gradle aquí: contratos
 * verificados en JVM con fachadas que replican el orden productivo real.
 */
class NutritionForecastWriteLaneTest {

    private val source = NutritionPlan(
        id = "p1",
        name = "Plan",
        calorieTarget = 2_000,
        proteinGoal = 150,
        carbGoal = 250,
        fatGoal = 60,
    )
    private val revised = source.copy(name = "Plan (revisión de previsión)")

    @Test
    fun `cache publish only applies while the entry is still the source`() {
        val cache = MutableStateFlow(listOf(source))
        assertTrue(cache.publishForecastIfSourceUnchanged(source, revised))
        assertEquals(revised, cache.value.first())

        // Otra escritura ya publicó un plan más nuevo: la revisión calculada
        // con datos viejos NO debe pisarla.
        val newer = source.copy(name = "Del editor", calorieTarget = 2_400)
        val occupied = MutableStateFlow(listOf(newer))
        assertFalse(occupied.publishForecastIfSourceUnchanged(source, revised))
        assertEquals(newer, occupied.value.first())

        // Plan ajeno a la caché: nada que publicar (sin éxitos ficticios).
        val empty = MutableStateFlow(emptyList<NutritionPlan>())
        assertFalse(empty.publishForecastIfSourceUnchanged(source, revised))
    }

    @Test
    fun `interleaved editor write after our commit is never clobbered and cache converges to db`() = runBlocking {
        var db: NutritionPlan = source
        val cache = MutableStateFlow(listOf(source))
        val lane = NutritionForecastWriteLane(
            readCurrent = { db },
            commitRevision = { ourRevision ->
                db = ourRevision
                // El editor escribe DESPUÉS de nuestro commit y ANTES de
                // nuestra publicación (peor caso sin su propia vía).
                val fromEditor = ourRevision.copy(name = "Del editor", calorieTarget = 2_400)
                db = fromEditor
                cache.publishForecastIfSourceUnchanged(source, fromEditor)
            },
            publish = { s, r -> cache.publishForecastIfSourceUnchanged(s, r) },
        )

        assertTrue(lane.write(source, revised))

        // Nuestra publicación se rechaza por CAS: la caché NO retrocede al
        // plan viejo y queda CONVERGIDA con la BD.
        assertEquals(2_400, cache.value.first().calorieTarget)
        assertEquals(cache.value.first(), db)
    }

    @Test
    fun `production mutex keeps an editor write outside the write-publish section`() = runBlocking {
        var db: NutritionPlan = source
        val cache = MutableStateFlow(listOf(source))
        val enteredRead = CompletableDeferred<Unit>()
        val mayProceed = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        // Misma sección crítica real que en producción (mutex por defecto).
        val lane = NutritionForecastWriteLane(
            readCurrent = {
                enteredRead.complete(Unit)
                mayProceed.await()
                db
            },
            commitRevision = { db = it },
            publish = { s, r -> cache.publishForecastIfSourceUnchanged(s, r) },
        )

        val reforecast = launch {
            lane.write(source, revised)
            order += "reforecast"
        }
        enteredRead.await() // la vía ya tiene el mutex

        var editorInside = false
        val editor = launch {
            PersistenceWriteCoordinator.mutex.withLock {
                editorInside = true
                db = revised.copy(name = "Del editor", calorieTarget = 2_400)
                cache.value = listOf(db)
                order += "editor"
            }
        }
        yield()
        assertFalse("El editor no puede entrar entre commit y publicación", editorInside)

        mayProceed.complete(Unit)
        reforecast.join()
        editor.join()

        assertTrue(editorInside)
        assertEquals(listOf("reforecast", "editor"), order)
        // El editor queda después de nuestra sección: su plan es el último y
        // la caché refleja exactamente la BD (sin carreras de publicación).
        assertEquals(cache.value.first(), db)
        assertEquals(2_400, cache.value.first().calorieTarget)
    }
}
