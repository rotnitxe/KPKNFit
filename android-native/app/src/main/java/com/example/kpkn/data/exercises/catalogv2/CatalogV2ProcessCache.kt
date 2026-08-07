package com.example.kpkn.data.exercises.catalogv2

import android.content.Context
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-level cache for the approved v2 exercise catalog.
 *
 * The bundled asset only changes with an app update, so decoding the ~3 MB JSON
 * and building the search index once per process — instead of on every
 * "Agregar ejercicio" open — turns repeat openings into in-memory lookups.
 */
object CatalogV2ProcessCache {
    private const val ASSET_NAME = "exercise_catalog_v2.json"

    /** Decoded catalog plus its prebuilt searchable repository. */
    class Entry(
        val catalog: ExerciseCatalogV2,
        val repository: InMemoryExerciseCatalogRepositoryV2,
    )

    @Volatile
    private var entry: Entry? = null

    private val mutex = Mutex()

    /** Non-suspending fast path for callers that only work with a warm cache. */
    fun peek(): Entry? = entry

    /**
     * Returns the cached entry, decoding the bundled asset on first access.
     * Concurrent calls are serialized through [mutex]; the asset is decoded at
     * most once per process. Call from a background dispatcher.
     */
    suspend fun getOrLoad(context: Context): Entry {
        peek()?.let { return it }
        return mutex.withLock {
            entry ?: decodeAndIndex(context.applicationContext).also { entry = it }
        }
    }

    private suspend fun decodeAndIndex(context: Context): Entry {
        val payload = context.assets
            .open(ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }
        val catalog = ExerciseCatalogV2Loader.decodeApproved(payload)
        val repository = InMemoryExerciseCatalogRepositoryV2(catalog)
        repository.load()
        return Entry(catalog, repository)
    }

    /** Test hook / explicit invalidation. */
    internal fun clear() {
        entry = null
    }
}
