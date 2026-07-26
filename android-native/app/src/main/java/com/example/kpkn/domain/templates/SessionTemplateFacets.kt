package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion
import com.example.kpkn.domain.exercises.resolveExerciseRegion
import com.example.kpkn.domain.training.VolumeCalculator

/**
 * Cadena cinética agregada de una plantilla (filtro UI).
 * [ALL] representa “sin filtro”; nunca se guarda en [SessionTemplateFacets.chains].
 */
enum class SessionTemplateChain(val label: String) {
    ALL("Todas"),
    ANTERIOR("Cadena anterior"),
    POSTERIOR("Cadena posterior"),
    FULL("Cadena completa"),
}

/**
 * Buckets de duración real (minutos) para filtros del navegador.
 * [ALL] = sin filtro.
 */
enum class SessionTemplateDurationBucket(val label: String, val range: IntRange?) {
    ALL("Cualquiera", null),
    SHORT("Corta (≤45 min)", 0..45),
    MEDIUM("Media (46–75 min)", 46..75),
    LONG("Larga (≥76 min)", 76..Int.MAX_VALUE),
}

/**
 * Facetas derivadas de contenido real + catálogo de ejercicios.
 * Pensado para memoización UI ([SessionTemplateFacetsBuilder.buildAll]).
 */
data class SessionTemplateFacets(
    val templateId: String,
    /** Regiones presentes (nunca [ExerciseCatalogRegion.ALL]). */
    val regions: Set<ExerciseCatalogRegion>,
    /** Cadenas presentes (nunca [SessionTemplateChain.ALL]). */
    val chains: Set<SessionTemplateChain>,
    /** Músculos canónicos PRIMARY con ≥ [SessionTemplateFacetsBuilder.MIN_PRIMARY_SETS] series agregadas. */
    val primaryMuscles: Set<String>,
    val totalSets: Int,
    val realDurationMinutes: Int,
    val averageTargetRpe: Double?,
    val difficulty: Difficulty,
    val drain: PredictedDrain,
    val movementPatterns: Set<String>,
    val equipment: Set<String> = emptySet(),
) {
    /** Región dominante para chips/etiquetas (FULL si hay upper+lower, o la única no-CORE, etc.). */
    val dominantRegion: ExerciseCatalogRegion
        get() = SessionTemplateFacetsBuilder.dominantRegion(regions)
}

object SessionTemplateFacetsBuilder {

    const val MIN_PRIMARY_SETS = 3

    fun build(
        template: SessionTemplate,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): SessionTemplateFacets {
        val audit = SessionTemplateAudit.audit(template, exerciseIndex)

        val regions = linkedSetOf<ExerciseCatalogRegion>()
        val chains = linkedSetOf<SessionTemplateChain>()
        val patterns = linkedSetOf<String>()
        val equipment = linkedSetOf<String>()

        audit.exercises.forEach { exercise ->
            val info = SessionTemplateAudit.resolveCatalogInfo(exercise, exerciseIndex) ?: return@forEach
            val region = resolveExerciseRegion(info)
            if (region != ExerciseCatalogRegion.ALL) {
                regions += region
            }
            normalizeChain(info.chain)?.let { chains += it }
            info.movementPattern?.trim()?.takeIf { it.isNotEmpty() }?.let { patterns += it }
            info.equipment?.trim()?.takeIf { it.isNotEmpty() }?.let { equipment += it }
        }

        // Filtros claros: si la sesión mezcla tren superior e inferior, también es FULL.
        if (ExerciseCatalogRegion.UPPER in regions && ExerciseCatalogRegion.LOWER in regions) {
            regions += ExerciseCatalogRegion.FULL
        }

        val primaryMuscles = resolvePrimaryMuscles(audit.primaryMuscleSets)
        val drain = safeDrain(template, exerciseIndex)

        return SessionTemplateFacets(
            templateId = template.id,
            regions = regions,
            chains = chains,
            primaryMuscles = primaryMuscles,
            totalSets = audit.totalSets,
            realDurationMinutes = audit.estimatedDurationMinutes,
            averageTargetRpe = audit.averageTargetRpe,
            difficulty = template.difficulty,
            drain = drain,
            movementPatterns = patterns,
            equipment = equipment,
        )
    }

    /** Memoización para UI: una pasada sobre el catálogo. */
    fun buildAll(
        templates: List<SessionTemplate>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, SessionTemplateFacets> =
        templates.associate { it.id to build(it, exerciseIndex) }

    fun dominantRegion(regions: Set<ExerciseCatalogRegion>): ExerciseCatalogRegion {
        val meaningful = regions.filter { it != ExerciseCatalogRegion.ALL }
        if (meaningful.isEmpty()) return ExerciseCatalogRegion.FULL
        if (ExerciseCatalogRegion.FULL in meaningful) return ExerciseCatalogRegion.FULL
        if (ExerciseCatalogRegion.UPPER in meaningful && ExerciseCatalogRegion.LOWER in meaningful) {
            return ExerciseCatalogRegion.FULL
        }
        val nonCore = meaningful.filter { it != ExerciseCatalogRegion.CORE }
        return nonCore.firstOrNull() ?: meaningful.first()
    }

    fun normalizeChain(raw: String?): SessionTemplateChain? {
        val value = raw?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return null
        return when (value) {
            "anterior" -> SessionTemplateChain.ANTERIOR
            "posterior" -> SessionTemplateChain.POSTERIOR
            "full", "completa", "complete" -> SessionTemplateChain.FULL
            else -> null
        }
    }

    fun durationBucket(minutes: Int): SessionTemplateDurationBucket = when {
        minutes <= 45 -> SessionTemplateDurationBucket.SHORT
        minutes <= 75 -> SessionTemplateDurationBucket.MEDIUM
        else -> SessionTemplateDurationBucket.LONG
    }

    fun matchesRegion(facets: SessionTemplateFacets, filter: ExerciseCatalogRegion): Boolean {
        if (filter == ExerciseCatalogRegion.ALL) return true
        if (filter == ExerciseCatalogRegion.FULL) {
            return ExerciseCatalogRegion.FULL in facets.regions ||
                (ExerciseCatalogRegion.UPPER in facets.regions && ExerciseCatalogRegion.LOWER in facets.regions)
        }
        return filter in facets.regions
    }

    fun matchesChain(facets: SessionTemplateFacets, filter: SessionTemplateChain): Boolean {
        if (filter == SessionTemplateChain.ALL) return true
        return filter in facets.chains
    }

    fun matchesMuscle(facets: SessionTemplateFacets, muscle: String?): Boolean {
        val needle = muscle?.trim().orEmpty()
        if (needle.isEmpty()) return true
        return facets.primaryMuscles.any { it.equals(needle, ignoreCase = true) }
    }

    fun matchesDuration(
        facets: SessionTemplateFacets,
        filter: SessionTemplateDurationBucket,
    ): Boolean {
        if (filter == SessionTemplateDurationBucket.ALL) return true
        val range = filter.range ?: return true
        return facets.realDurationMinutes in range
    }

    fun matchesDifficulty(facets: SessionTemplateFacets, filter: Difficulty?): Boolean {
        if (filter == null) return true
        return facets.difficulty == filter
    }

    private fun resolvePrimaryMuscles(primaryMuscleSets: Map<String, Int>): Set<String> {
        // Audit ya canónica; re-normalizamos por si el índice llega con aliases/énfasis.
        val canonicalSets = linkedMapOf<String, Int>()
        primaryMuscleSets.forEach { (muscle, sets) ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
            if (canonical.isBlank() || sets <= 0) return@forEach
            canonicalSets[canonical] = (canonicalSets[canonical] ?: 0) + sets
        }

        val aboveThreshold = canonicalSets
            .filter { it.value >= MIN_PRIMARY_SETS }
            .keys
            .toCollection(linkedSetOf())
        if (aboveThreshold.isNotEmpty()) return aboveThreshold

        // Fallback seguro: si ningún músculo llega a 3 (sesiones muy cortas),
        // conservar los de mayor volumen primario para no dejar facetas vacías.
        val maxSets = canonicalSets.values.maxOrNull() ?: return emptySet()
        if (maxSets <= 0) return emptySet()
        return canonicalSets
            .filter { it.value == maxSets }
            .keys
            .toCollection(linkedSetOf())
    }

    private fun safeDrain(
        template: SessionTemplate,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): PredictedDrain {
        return try {
            val raw = SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseIndex)
            PredictedDrain(
                cns = raw.cns.coerceIn(0, 100),
                muscular = raw.muscular.coerceIn(0, 100),
                spinal = raw.spinal.coerceIn(0, 100),
            )
        } catch (_: Throwable) {
            PredictedDrain(cns = 0, muscular = 0, spinal = 0)
        }
    }
}
