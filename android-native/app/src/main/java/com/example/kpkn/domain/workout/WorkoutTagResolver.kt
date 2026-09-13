package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.models.SubTagCategory
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Canonical tag identity for live workout loads.
 * Resolves UUID vs. legacy name, untagged-history adoption, and reset cutoffs.
 */
object WorkoutTagResolver {
    const val DEFAULT_TAG_NAME = "Default"

    fun normalizeName(name: String): String {
        if (name.isBlank()) return ""
        val stripped = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return stripped.lowercase().replace("\\s+".toRegex(), " ").trim()
    }

    fun namesMatch(left: String?, right: String?): Boolean {
        val a = normalizeName(left.orEmpty())
        val b = normalizeName(right.orEmpty())
        return a.isNotEmpty() && a == b
    }

    fun isInternalId(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return runCatching { UUID.fromString(trimmed) }.isSuccess
    }

    fun isDefaultName(name: String?): Boolean =
        namesMatch(name, DEFAULT_TAG_NAME)

    fun withNormalized(tag: WorkoutTag): WorkoutTag {
        val norm = normalizeName(tag.name)
        return if (tag.normalizedName == norm) tag else tag.copy(normalizedName = norm)
    }

    fun displayChipParenthetical(tagName: String?): String? {
        val name = tagName?.trim().orEmpty()
        if (name.isEmpty() || isDefaultName(name) || isInternalId(name)) return null
        return name
    }

    fun anteriorChipLabel(tagName: String?): String {
        val paren = displayChipParenthetical(tagName) ?: return "Anterior"
        return "Anterior ($paren)"
    }

    fun profilePersistentName(profile: WorkoutContextProfile): String? {
        val setupName = profile.setupLabel?.trim()?.takeIf { it.isNotEmpty() }
        val brandName = profile.machineBrand?.trim()?.takeIf { it.isNotEmpty() }
        val legacyId = profile.tagId?.trim()?.takeIf { it.isNotEmpty() && !isInternalId(it) }
        return setupName ?: brandName ?: legacyId
    }

    fun seedFromProfiles(
        profiles: Collection<WorkoutContextProfile>,
        exerciseKey: String,
    ): List<WorkoutTag> {
        return profiles
            .filter { it.exerciseKey == exerciseKey }
            .filter { !it.tagId.isNullOrBlank() || !it.setupLabel.isNullOrBlank() }
            .distinctBy { it.tagId ?: it.setupLabel ?: it.id }
            .map { profile ->
                val rawId = profile.tagId?.trim().orEmpty()
                val id = when {
                    rawId.isNotEmpty() && isInternalId(rawId) -> rawId
                    else -> profile.id
                }
                val persistentName = profilePersistentName(profile)
                val subTags = buildList {
                    profile.machineBrand?.let {
                        add(WorkoutSubTag(name = it, category = SubTagCategory.MARCA))
                    }
                    profile.setupDetails?.seatPosition?.let {
                        add(WorkoutSubTag(name = "Asiento: $it", category = SubTagCategory.SETUP))
                    }
                    profile.setupDetails?.pinPosition?.let {
                        add(WorkoutSubTag(name = "Pin: $it", category = SubTagCategory.SETUP))
                    }
                    BaseLoadPolicy.resolvedFromProfile(profile)?.let {
                        add(WorkoutSubTag(name = "Carga base: ${it}kg", category = SubTagCategory.SETUP))
                    }
                    profile.setupDetails?.equipmentNotes?.let {
                        add(WorkoutSubTag(name = it, category = SubTagCategory.SETUP))
                    }
                }
                withNormalized(
                    WorkoutTag(
                        id = id,
                        name = persistentName ?: "Migrado",
                        exerciseKey = profile.exerciseKey,
                        subTags = subTags,
                        createdAtIso = profile.createdAtIso.orEmpty(),
                        lastUsedAtIso = profile.lastUsedAtIso.orEmpty(),
                        usageCount = profile.usageCount,
                    ),
                )
            }
    }

    fun resolveTag(
        preferred: String?,
        tags: List<WorkoutTag>,
    ): WorkoutTag? {
        val needle = preferred?.trim().orEmpty()
        if (needle.isEmpty()) return null
        return tags.firstOrNull { it.id == needle || namesMatch(it.name, needle) }
    }

    fun logIdentityKeys(exercise: CompletedExercise): List<String> =
        listOfNotNull(
            exercise.exerciseId.takeIf { it.isNotBlank() },
            exercise.exerciseDbId?.takeIf { it.isNotBlank() },
            exercise.canonicalExerciseId?.takeIf { it.isNotBlank() },
            exercise.occurrenceId?.takeIf { it.isNotBlank() },
        )

    fun lookupLogTagId(log: WorkoutLog, exercise: CompletedExercise): String? {
        val keys = logIdentityKeys(exercise)
        return keys.firstNotNullOfOrNull { key ->
            log.exerciseTagIds[key]?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    fun lookupLogTagName(log: WorkoutLog, exercise: CompletedExercise): String? {
        val keys = logIdentityKeys(exercise)
        return keys.firstNotNullOfOrNull { key ->
            log.exerciseTags[key]?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    fun isUntagged(log: WorkoutLog, exercise: CompletedExercise): Boolean {
        if (!lookupLogTagId(log, exercise).isNullOrBlank()) return false
        if (!lookupLogTagName(log, exercise).isNullOrBlank()) return false
        return exercise.sets.none { set ->
            !set.tagId.isNullOrBlank() || !set.tagName.isNullOrBlank()
        }
    }

    fun resolvedTagIdFromLog(
        log: WorkoutLog,
        exercise: CompletedExercise,
        tags: List<WorkoutTag>,
    ): String? {
        val storedId = lookupLogTagId(log, exercise)
        if (!storedId.isNullOrBlank()) {
            resolveTag(storedId, tags)?.let { return it.id }
            if (isInternalId(storedId)) return storedId
        }
        val storedName = lookupLogTagName(log, exercise)
        if (!storedName.isNullOrBlank()) {
            resolveTag(storedName, tags)?.let { return it.id }
            if (isInternalId(storedName)) return storedName
        }
        val setId = exercise.sets.firstNotNullOfOrNull { it.tagId?.trim()?.takeIf { id -> id.isNotEmpty() } }
        if (!setId.isNullOrBlank()) {
            resolveTag(setId, tags)?.let { return it.id }
            if (isInternalId(setId)) return setId
        }
        val setName = exercise.sets.firstNotNullOfOrNull { it.tagName?.trim()?.takeIf { name -> name.isNotEmpty() } }
        if (!setName.isNullOrBlank()) {
            resolveTag(setName, tags)?.let { return it.id }
        }
        if (isUntagged(log, exercise)) {
            return tags.firstOrNull { it.ownsUntaggedHistory }?.id
        }
        return null
    }

    fun isAfterReset(logDateIso: String, historyResetAtIso: String?): Boolean {
        if (historyResetAtIso.isNullOrBlank()) return true
        val logMs = parseIsoMs(logDateIso) ?: return true
        val resetMs = parseIsoMs(historyResetAtIso) ?: return true
        return logMs >= resetMs
    }

    fun logMatchesTag(
        log: WorkoutLog,
        exercise: CompletedExercise,
        tag: WorkoutTag,
        allTags: List<WorkoutTag>,
    ): Boolean {
        if (!isAfterReset(log.date, tag.historyResetAtIso)) return false
        val resolved = resolvedTagIdFromLog(log, exercise, allTags)
        if (resolved != null) return resolved == tag.id
        return tag.ownsUntaggedHistory && isUntagged(log, exercise)
    }

    fun setMatchesTag(
        set: CompletedSet,
        tag: WorkoutTag,
        logExerciseTag: String?,
        logExerciseTagId: String?,
    ): Boolean {
        val id = set.tagId?.trim().orEmpty()
        if (id.isNotEmpty()) {
            return id == tag.id || namesMatch(id, tag.name)
        }
        val name = set.tagName?.trim().orEmpty()
        if (name.isNotEmpty()) {
            return namesMatch(name, tag.name)
        }
        if (!logExerciseTagId.isNullOrBlank()) {
            return logExerciseTagId == tag.id || namesMatch(logExerciseTagId, tag.name)
        }
        if (!logExerciseTag.isNullOrBlank()) {
            return logExerciseTag == tag.id || namesMatch(logExerciseTag, tag.name)
        }
        return tag.ownsUntaggedHistory
    }

    fun filterLogs(
        logs: List<WorkoutLog>,
        matchingExercise: (WorkoutLog) -> CompletedExercise?,
        tag: WorkoutTag?,
        allTags: List<WorkoutTag>,
        strict: Boolean,
    ): List<WorkoutLog> {
        if (!strict) return logs
        if (tag == null) {
            return logs.filter { log ->
                val exercise = matchingExercise(log) ?: return@filter false
                isUntagged(log, exercise)
            }
        }
        return logs.filter { log ->
            val exercise = matchingExercise(log) ?: return@filter false
            logMatchesTag(log, exercise, tag, allTags)
        }
    }

    private fun parseIsoMs(iso: String): Long? {
        val trimmed = iso.trim()
        if (trimmed.isEmpty()) return null
        runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { return it }
        return runCatching {
            LocalDate.parse(trimmed.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
