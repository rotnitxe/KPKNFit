package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate

/**
 * Cache estable de facetas del catálogo de plantillas.
 * Evita recalcular audit + rings al reabrir el tab Plantillas o al teclear búsqueda.
 */
object SessionTemplateFacetsCache {
    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cached: Map<String, SessionTemplateFacets> = emptyMap()

    fun getOrBuild(
        templates: List<SessionTemplate>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, SessionTemplateFacets> {
        val key = buildString {
            append(templates.size)
            append('#')
            append(exerciseIndex.size)
            append('#')
            templates.forEach { append(it.id).append('|') }
        }
        val hit = cachedKey
        if (hit == key) return cached
        val built = SessionTemplateFacetsBuilder.buildAll(templates, exerciseIndex)
        cachedKey = key
        cached = built
        return built
    }

    /** Solo para tests. */
    fun clear() {
        cachedKey = null
        cached = emptyMap()
    }
}
