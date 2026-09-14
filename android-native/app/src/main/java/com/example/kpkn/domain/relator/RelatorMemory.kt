package com.example.kpkn.domain.relator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

internal const val RELATOR_FINGERPRINT_MEMORY = 12
internal const val RELATOR_CONCEPT_COOLDOWN_DAYS = 7L

@Serializable
data class RelatorLongTermMemory(
    val conceptsShownEpochDay: Map<String, Long> = emptyMap(),
    val exerciseCounts: Map<String, Int> = emptyMap(),
    val openersUsed: List<String> = emptyList(),
) {
    fun recordConcept(conceptId: String, epochDay: Long = LocalDate.now().toEpochDay()): RelatorLongTermMemory {
        if (conceptId.isBlank()) return this
        return copy(conceptsShownEpochDay = conceptsShownEpochDay + (conceptId to epochDay))
    }

    fun recordOpener(opener: String): RelatorLongTermMemory {
        val trimmed = opener.trim()
        if (trimmed.isEmpty()) return this
        return copy(openersUsed = (openersUsed + trimmed).takeLast(24))
    }

    fun bumpExercise(exerciseId: String): RelatorLongTermMemory {
        if (exerciseId.isBlank()) return this
        val next = (exerciseCounts[exerciseId] ?: 0) + 1
        return copy(exerciseCounts = exerciseCounts + (exerciseId to next))
    }

    fun conceptWasShownRecently(
        conceptId: String,
        epochDay: Long = LocalDate.now().toEpochDay(),
        cooldownDays: Long = RELATOR_CONCEPT_COOLDOWN_DAYS,
    ): Boolean {
        val last = conceptsShownEpochDay[conceptId] ?: return false
        return epochDay - last < cooldownDays
    }

    fun encode(): String = json.encodeToString(RelatorLongTermMemory.serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): RelatorLongTermMemory {
            if (raw.isNullOrBlank()) return RelatorLongTermMemory()
            return runCatching { json.decodeFromString(RelatorLongTermMemory.serializer(), raw) }
                .getOrElse { RelatorLongTermMemory() }
        }
    }
}

data class RelatorSelectorState(
    val fingerprints: List<String> = emptyList(),
    val lastTopic: RelatorTopic? = null,
    val lastSetKey: String = "",
    val setsSeen: Int = 0,
    val lastTopicAtSet: Map<RelatorTopic, Int> = emptyMap(),
    val conceptSpokenThisSession: Set<String> = emptySet(),
    val conceptSpokenForExercise: Set<String> = emptySet(),
) {
    fun record(fingerprint: String, topic: RelatorTopic, setKey: String, conceptId: String?, exerciseId: String): RelatorSelectorState {
        val trimmed = fingerprint.trim()
        if (trimmed.isEmpty()) return this
        val advanced = if (setKey.isNotBlank() && setKey != lastSetKey) setsSeen + 1 else setsSeen
        return copy(
            fingerprints = (fingerprints + trimmed).takeLast(RELATOR_FINGERPRINT_MEMORY),
            lastTopic = topic,
            lastSetKey = setKey.ifBlank { lastSetKey },
            setsSeen = advanced,
            lastTopicAtSet = lastTopicAtSet + (topic to advanced),
            conceptSpokenThisSession = if (conceptId.isNullOrBlank()) {
                conceptSpokenThisSession
            } else {
                conceptSpokenThisSession + conceptId
            },
            conceptSpokenForExercise = if (conceptId.isNullOrBlank() || exerciseId.isBlank()) {
                conceptSpokenForExercise
            } else {
                conceptSpokenForExercise + "${exerciseId}|$conceptId"
            },
        )
    }
}
