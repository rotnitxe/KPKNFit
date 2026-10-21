package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.exp
import kotlin.math.ln

object AugeAdaptiveCacheMigration {
    const val CURRENT_SCHEMA = 4
    const val OBSERVATION_LIMIT = 200

    val codecJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        allowSpecialFloatingPointValues = true
    }

    fun encodeAdaptiveCache(cache: AugeAdaptiveCache): String {
        val normalized = if (cache.schemaVersion == CURRENT_SCHEMA) normalize(cache) else cache
        return codecJson.encodeToString(normalized)
    }

    fun decodeAdaptiveCache(data: String): AugeAdaptiveCacheDecodeResult {
        val diagnostics = mutableListOf<String>()
        return runCatching {
            val element = codecJson.parseToJsonElement(data)
            val version = element.jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
            if (version > CURRENT_SCHEMA) {
                return AugeAdaptiveCacheDecodeResult(AugeAdaptiveCache(), false, listOf("future_schema:$version"))
            }
            val decoded = codecJson.decodeFromString<AugeAdaptiveCache>(data)
            if (version < CURRENT_SCHEMA) diagnostics += "learning_reset_v4:$version"
            val migrated = migrate(decoded, version)
            val normalized = if (version == CURRENT_SCHEMA) normalize(decoded) {
                diagnostics += it
            } else migrated
            AugeAdaptiveCacheDecodeResult(normalized, diagnostics = diagnostics)
        }.getOrElse {
            AugeAdaptiveCacheDecodeResult(AugeAdaptiveCache(), diagnostics = listOf("unreadable_json"))
        }
    }

    fun toAdaptiveCache(data: String): AugeAdaptiveCache = decodeAdaptiveCache(data).cache

    fun parameterKey(raw: String): String = when (raw.lowercase().trim()) {
        "cns", "energía", "energia", "snc" -> "cns"
        "spinal", "columna" -> "spinal"
        else -> toAugeAdaptiveMuscleKey(raw)
    }

    fun prior(key: String): AugeParameterState = AugeParameterState(
        mean = ln(when (parameterKey(key)) {
            "cns" -> AugeParameters.DEFAULT_TAU_ENERGY_H
            "spinal" -> AugeParameters.DEFAULT_TAU_SPINAL_H
            else -> AugeParameters.baseTauHours(key)
        }),
        paramsVersion = AugeParameters.VERSION,
    )

    /** Único puente de la media logarítmica a horas; la proyección se activa en 3.2. */
    fun tauHours(state: AugeParameterState): Double = exp(state.mean)

    /** Id reproducible, con longitud de cadenas para evitar colisiones por separadores. */
    fun observationId(key: String, anchorMs: Long, y: Double, relevantLogId: String?): String {
        val canonical = parameterKey(key)
        return "${canonical.length}:$canonical|$anchorMs|${y.toBits()}|${relevantLogId?.length ?: -1}:${relevantLogId.orEmpty()}"
    }

    /** Los τ v1–v3 nacieron de una inversión sesgada: se descartan sin transferir n. */
    fun migrate(cache: AugeAdaptiveCache, storedVersion: Int): AugeAdaptiveCache = when {
        storedVersion > CURRENT_SCHEMA -> AugeAdaptiveCache()
        storedVersion < CURRENT_SCHEMA -> cache.copy(
            personalizedRecoveryHours = emptyMap(), cnsRecoveryHours = null, spinalRecoveryHours = null,
            parameterStates = emptyMap(), observations = emptyList(), schemaVersion = CURRENT_SCHEMA,
            modelUpdateNoticeShown = false,
        )
        else -> normalize(cache)
    }

    /** Cada parámetro inválido vuelve a su prior; el resto del caché y sus avisos se conservan. */
    fun normalize(cache: AugeAdaptiveCache, diagnostic: (String) -> Unit = {}): AugeAdaptiveCache {
        val states = linkedMapOf<String, AugeParameterState>()
        val invalidKeys = mutableSetOf<String>()
        cache.parameterStates.toSortedMap().forEach { (raw, state) ->
            val key = parameterKey(raw)
            val valid = state.mean.isFinite() && state.variance.isFinite() && state.variance >= 0.0 &&
                state.n >= 0 && state.lastUpdatedAtMs >= 0 && state.replayWatermarkMs >= 0 && state.paramsVersion > 0
            states[key] = if (valid) state else prior(key).also { invalidKeys += key; diagnostic("invalid_parameter:$key") }
        }
        val records = cache.observations.filter { record ->
            val valid = record.id.isNotBlank() && record.y.isFinite() && record.predicted.isFinite() &&
                record.deltaHours.isFinite() && record.deltaHours >= 0.0 && record.tauUsedHours.isFinite() &&
                record.tauUsedHours > 0.0 && record.observedAtMs >= 0 && record.anchorMs >= 0 &&
                record.paramsVersion > 0 && (record.priorPredicted?.isFinite() != false)
            if (!valid) diagnostic("invalid_observation")
            valid
        }.distinctBy { it.id }.map { it.copy(parameterKey = parameterKey(it.parameterKey)) }
            .sortedWith(compareBy({ it.observedAtMs }, { it.id })).takeLast(OBSERVATION_LIMIT)
        return cache.copy(
            parameterStates = states, observations = records,
            cnsRecoveryHours = if ("cns" in invalidKeys) null else cache.cnsRecoveryHours,
            spinalRecoveryHours = if ("spinal" in invalidKeys) null else cache.spinalRecoveryHours,
            personalizedRecoveryHours = cache.personalizedRecoveryHours.filterKeys { parameterKey(it) !in invalidKeys },
        )
    }

    /**
     * Orden cronológico por fecha e id lexicográfico en empate; duplicados/replays devuelven la misma instancia.
     * Solo registra auditoría y marca de agua: no modifica media, varianza, n ni fecha del posterior.
     */
    fun record(cache: AugeAdaptiveCache, observation: AugeObservationRecord): AugeAdaptiveCache {
        if (cache.schemaVersion != CURRENT_SCHEMA) return cache
        if (normalize(AugeAdaptiveCache(observations = listOf(observation))).observations.isEmpty()) return cache
        if (cache.observations.any { it.id == observation.id }) return cache
        val key = parameterKey(observation.parameterKey)
        val state = normalize(cache).parameterStates[key] ?: prior(key)
        if (observation.anchorMs <= state.replayWatermarkMs) return cache
        val next = cache.copy(
            parameterStates = if (observation.outcome.status == AugeObservationStatus.REJECTED) cache.parameterStates
                else cache.parameterStates + (key to state.copy(replayWatermarkMs = observation.anchorMs)),
            observations = cache.observations + observation.copy(parameterKey = key),
        )
        return normalize(next)
    }
}
