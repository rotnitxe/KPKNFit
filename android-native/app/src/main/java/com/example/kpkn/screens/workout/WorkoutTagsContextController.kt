package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.workout.WorkoutTagResolver
import java.time.Instant
import java.util.UUID

data class TagSetupInput(
    val machineBrand: String? = null,
    val baseLoadKg: Double? = null,
    val setupNotes: String? = null,
) {
    val hasContent: Boolean
        get() = !machineBrand.isNullOrBlank() || baseLoadKg != null || !setupNotes.isNullOrBlank()
}

sealed class CreateTagResult {
    data class Created(val tag: WorkoutTag, val untaggedSessionCount: Int = 0) : CreateTagResult()
    data class Duplicate(val existingName: String) : CreateTagResult()
    data object InvalidName : CreateTagResult()
}

sealed class RenameTagResult {
    data object Success : RenameTagResult()
    data class Duplicate(val existingName: String) : RenameTagResult()
    data object InvalidName : RenameTagResult()
    data object NotFound : RenameTagResult()
}

/**
 * Tag CRUD, active tags, and context-profile hydrate/upsert/migrate/sync.
 */
class WorkoutTagsContextController(
    private val repository: ProgramRepository,
    private val getState: () -> WorkoutUiState,
    private val updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
    private val persistOngoingState: () -> Unit,
    private val ports: Ports,
) {
    interface Ports {
        fun visibleExercises(state: WorkoutUiState): List<Exercise>
        fun canonicalExerciseKey(exercise: Exercise): String
        fun refreshLoadSuggestions()
        fun clearDraftsForExercise(exerciseId: String)
        fun untaggedSessionCount(exercise: Exercise, tags: List<WorkoutTag>): Int
    }

    fun defaultContextProfileForExercise(exercise: Exercise): WorkoutContextProfile {
        val exerciseKey = ports.canonicalExerciseKey(exercise)
        return WorkoutContextProfile(
            id = "$exerciseKey|default",
            exerciseKey = exerciseKey,
            tagId = exercise.sets.firstNotNullOfOrNull { it.defaultTagIdV3 ?: it.tagId } ?: exercise.variantName,
            setupProfileId = exercise.sets.firstNotNullOfOrNull { it.defaultSetupProfileIdV3 ?: it.setupId },
            setupLabel = exercise.setupDetails?.seatPosition ?: exercise.setupDetails?.pinPosition,
            machineBrand = exercise.sets.firstNotNullOfOrNull { it.machineBrand },
            setupDetails = exercise.setupDetails,
            createdAtIso = Instant.now().toString(),
            lastUsedAtIso = Instant.now().toString(),
            usageCount = 1,
        )
    }

    fun hydrateContextProfiles(
        exercises: List<Exercise>,
        resumedState: OngoingWorkoutState?,
    ): Pair<Map<String, WorkoutContextProfile>, Map<String, String>> {
        val mergedProfiles = repository.contextProfiles.value.toMutableMap()
        val activeProfiles = resumedState?.activeContextProfileByExerciseId?.toMutableMap() ?: mutableMapOf()

        exercises.forEach { exercise ->
            val exerciseKey = ports.canonicalExerciseKey(exercise)
            val candidates = buildList {
                addAll(exercise.contextProfilesV3)
                addAll(repository.getContextProfilesForExercise(exerciseKey))
                resumedState?.contextProfilesV3?.values
                    ?.filter { it.exerciseKey == exerciseKey }
                    ?.let { addAll(it) }
            }
                .distinctBy { it.id }
                .ifEmpty { listOf(defaultContextProfileForExercise(exercise)) }

            candidates.forEach { profile ->
                mergedProfiles[profile.id] = profile
                repository.upsertContextProfile(profile)
            }

            val preferredId = resumedState?.activeContextProfileByExerciseId?.get(exercise.id)
                ?: exercise.defaultContextProfileIdV3
                ?: candidates.firstOrNull()?.id
            val resolvedId = candidates.firstOrNull { it.id == preferredId }?.id ?: candidates.first().id
            activeProfiles[exercise.id] = resolvedId
        }

        return mergedProfiles to activeProfiles
    }

    fun profilesForExercise(exercise: Exercise): List<WorkoutContextProfile> {
        val key = ports.canonicalExerciseKey(exercise)
        return getState().contextProfilesV3.values
            .filter { it.exerciseKey == key }
            .sortedByDescending { it.lastUsedAtIso.orEmpty() }
    }

    fun activeContextProfile(exerciseId: String): WorkoutContextProfile? {
        val profileId = getState().activeContextProfileByExerciseId[exerciseId] ?: return null
        return getState().contextProfilesV3[profileId]
    }

    fun setActiveContextProfile(exerciseId: String, profileId: String) {
        val profile = getState().contextProfilesV3[profileId] ?: return
        updateState {
            val existingTags = tagsForExercise(exerciseId)
            val match = profile.tagId?.let { tagId ->
                existingTags.firstOrNull { it.id == tagId || WorkoutTagResolver.namesMatch(it.name, tagId) }
            }
            val tagIds = match?.let { listOf(it.id) }.orEmpty()
            val tagName = match?.name ?: profile.legacyTagName()
            it.copy(
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId + (exerciseId to profileId),
                exerciseTags = if (tagName != null) it.exerciseTags + (exerciseId to tagName) else it.exerciseTags,
                activeTagsByExercise = if (tagIds.isNotEmpty()) it.activeTagsByExercise + (exerciseId to tagIds) else it.activeTagsByExercise,
            )
        }
        persistOngoingState()
    }

    fun upsertContextProfile(
        exercise: Exercise,
        profile: WorkoutContextProfile,
        makeActive: Boolean = true,
    ) {
        val updated = profile.copy(
            exerciseKey = ports.canonicalExerciseKey(exercise),
            lastUsedAtIso = Instant.now().toString(),
            usageCount = profile.usageCount + 1,
        )
        repository.upsertContextProfile(updated)
        updateState {
            val existingTags = tagsForExercise(exercise.id)
            val match = updated.tagId?.let { tagId ->
                existingTags.firstOrNull { it.id == tagId || WorkoutTagResolver.namesMatch(it.name, tagId) }
            }
            val tagIds = if (makeActive) match?.let { listOf(it.id) }.orEmpty() else emptyList()
            val tagName = match?.name ?: updated.legacyTagName()
            it.copy(
                contextProfilesV3 = it.contextProfilesV3 + (updated.id to updated),
                activeContextProfileByExerciseId = if (makeActive) {
                    it.activeContextProfileByExerciseId + (exercise.id to updated.id)
                } else {
                    it.activeContextProfileByExerciseId
                },
                exerciseTags = tagName?.let { name ->
                    it.exerciseTags + (exercise.id to name)
                } ?: it.exerciseTags,
                activeTagsByExercise = if (tagIds.isNotEmpty()) {
                    it.activeTagsByExercise + (exercise.id to tagIds)
                } else {
                    it.activeTagsByExercise
                },
            )
        }
        persistOngoingState()
    }

    fun createTag(
        exerciseId: String,
        name: String,
        setup: TagSetupInput? = null,
        isDefault: Boolean = false,
        ownsUntaggedHistory: Boolean = false,
    ): CreateTagResult {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?: return CreateTagResult.InvalidName
        val exKey = ports.canonicalExerciseKey(exercise)
        val normalizedName = name.trim().ifBlank { setup?.machineBrand?.trim().orEmpty() }
        if (normalizedName.isBlank()) return CreateTagResult.InvalidName
        val existingForEx = mergedTagsForExercise(exerciseId, exKey)
        val duplicate = existingForEx.firstOrNull { WorkoutTagResolver.namesMatch(it.name, normalizedName) }
        if (duplicate != null) return CreateTagResult.Duplicate(duplicate.name)
        val wasEmpty = existingForEx.isEmpty()
        val now = Instant.now().toString()
        val tag = WorkoutTagResolver.withNormalized(
            WorkoutTag(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                exerciseKey = exKey,
                createdAtIso = now,
                lastUsedAtIso = now,
                usageCount = 0,
                ownsUntaggedHistory = ownsUntaggedHistory,
                isDefault = isDefault || WorkoutTagResolver.isDefaultName(normalizedName),
            ),
        )
        persistTag(tag)
        replaceTags(exKey) { current -> current + tag }
        persistOngoingState()
        selectMainTag(exerciseId, tag.id)
        upsertTagSetup(exerciseId, tag.id, setup ?: TagSetupInput(), makeActive = true)
        val untagged = if (wasEmpty && !ownsUntaggedHistory) {
            ports.untaggedSessionCount(exercise, listOf(tag))
        } else {
            0
        }
        return CreateTagResult.Created(tag, untagged)
    }

    fun profileForTag(exerciseId: String, tagId: String): WorkoutContextProfile? {
        val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId } ?: return null
        val exKey = ports.canonicalExerciseKey(exercise)
        val tag = tagsForExercise(exerciseId).firstOrNull { it.id == tagId }
        return getState().contextProfilesV3.values.firstOrNull { profile ->
            profile.exerciseKey == exKey &&
                (profile.tagId == tagId || (tag != null && profile.tagId == tag.name))
        }
    }

    fun upsertTagSetup(
        exerciseId: String,
        tagId: String,
        setup: TagSetupInput,
        makeActive: Boolean = true,
    ) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val tag = tagsForExercise(exerciseId).firstOrNull { it.id == tagId } ?: return
        val existing = profileForTag(exerciseId, tagId)
        val brand = setup.machineBrand?.trim()?.takeIf { it.isNotBlank() }
        val notes = setup.setupNotes?.trim()?.takeIf { it.isNotBlank() }
        val baseLoad = setup.baseLoadKg?.takeIf { it > 0 }
        val profile = (existing ?: WorkoutContextProfile(
            id = "$exKey|tag|$tagId",
            exerciseKey = exKey,
            tagId = tagId,
            createdAtIso = Instant.now().toString(),
        )).copy(
            tagId = tagId,
            setupLabel = tag.name,
            machineBrand = brand,
            baseLoadKg = baseLoad,
            barWeightKg = baseLoad,
            setupDetails = ExerciseSetupDetails(
                seatPosition = existing?.setupDetails?.seatPosition,
                pinPosition = existing?.setupDetails?.pinPosition,
                equipmentNotes = notes,
                barWeightKg = baseLoad,
                baseLoadKg = baseLoad,
            ),
            notes = notes,
        )
        upsertContextProfile(exercise, profile, makeActive = makeActive)
    }

    fun deleteTag(exerciseId: String, tagId: String) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val tagName = state.userCreatedTags[exKey].orEmpty().firstOrNull { it.id == tagId }?.name
            ?: repository.getWorkoutTagsForExercise(exKey).firstOrNull { it.id == tagId }?.name
        val existingForEx = mergedTagsForExercise(exerciseId, exKey).filter { it.id != tagId }
        val profileId = profileForTag(exerciseId, tagId)?.id
        val deletedProfileIds = setOfNotNull(profileId, "$exKey|tag|$tagId")
        repository.deleteContextProfile(profileId ?: "$exKey|tag|$tagId")
        if (profileId != null && profileId != "$exKey|tag|$tagId") {
            repository.deleteContextProfile("$exKey|tag|$tagId")
        }
        repository.deleteWorkoutTag(tagId)
        updateState {
            it.copy(
                userCreatedTags = it.userCreatedTags + (exKey to existingForEx),
                contextProfilesV3 = it.contextProfilesV3 - listOfNotNull(profileId, "$exKey|tag|$tagId"),
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId
                    .filterValues { activeProfileId -> activeProfileId !in deletedProfileIds },
                activeTagsByExercise = it.activeTagsByExercise.mapValues { (exId, tagIds) ->
                    if (exId == exerciseId) tagIds.filter { id -> id != tagId } else tagIds
                },
                exerciseTags = if (tagName != null && state.exerciseTags[exerciseId] == tagName) {
                    it.exerciseTags - exerciseId
                } else {
                    it.exerciseTags
                },
            )
        }
        persistOngoingState()
        val remaining = existingForEx.maxByOrNull { it.lastUsedAtIso }
        if (remaining != null) {
            selectMainTag(exerciseId, remaining.id)
        } else {
            ports.clearDraftsForExercise(exerciseId)
            ports.refreshLoadSuggestions()
        }
    }

    fun renameTag(exerciseId: String, tagId: String, newName: String): RenameTagResult {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId }
            ?: return RenameTagResult.NotFound
        val exKey = ports.canonicalExerciseKey(exercise)
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return RenameTagResult.InvalidName
        val oldTag = mergedTagsForExercise(exerciseId, exKey).firstOrNull { it.id == tagId }
            ?: return RenameTagResult.NotFound
        val duplicate = mergedTagsForExercise(exerciseId, exKey).firstOrNull {
            it.id != tagId && WorkoutTagResolver.namesMatch(it.name, trimmedName)
        }
        if (duplicate != null) return RenameTagResult.Duplicate(duplicate.name)
        val oldName = oldTag.name
        val existingProfile = profileForTag(exerciseId, tagId)
        val updated = WorkoutTagResolver.withNormalized(
            oldTag.copy(
                name = trimmedName,
                isDefault = oldTag.isDefault || WorkoutTagResolver.isDefaultName(trimmedName),
            ),
        )
        persistTag(updated)
        replaceTags(exKey) { current -> current.map { tag -> if (tag.id == tagId) updated else tag } }
        updateState {
            it.copy(
                exerciseTags = if (oldName == it.exerciseTags[exerciseId]) {
                    it.exerciseTags + (exerciseId to trimmedName)
                } else {
                    it.exerciseTags
                },
            )
        }
        upsertTagSetup(
            exerciseId = exerciseId,
            tagId = tagId,
            setup = TagSetupInput(
                machineBrand = existingProfile?.machineBrand,
                baseLoadKg = existingProfile?.baseLoadKg ?: existingProfile?.setupDetails?.baseLoadKg,
                setupNotes = existingProfile?.notes ?: existingProfile?.setupDetails?.equipmentNotes,
            ),
            makeActive = false,
        )
        persistOngoingState()
        return RenameTagResult.Success
    }

    fun resetTagHistory(exerciseId: String, tagId: String) {
        val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val existing = mergedTagsForExercise(exerciseId, exKey).firstOrNull { it.id == tagId } ?: return
        val updated = existing.copy(historyResetAtIso = Instant.now().toString())
        persistTag(updated)
        replaceTags(exKey) { current -> current.map { tag -> if (tag.id == tagId) updated else tag } }
        persistOngoingState()
        ports.clearDraftsForExercise(exerciseId)
        ports.refreshLoadSuggestions()
    }

    fun adoptUntaggedHistory(exerciseId: String, tagId: String) {
        val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        replaceTags(exKey) { current ->
            current.map { tag ->
                val next = tag.copy(ownsUntaggedHistory = tag.id == tagId)
                persistTag(next)
                next
            }
        }
        persistOngoingState()
        ports.refreshLoadSuggestions()
    }

    fun rejectUntaggedAdoption(exerciseId: String, newTagId: String) {
        ensureDefaultTag(exerciseId, ownsUntaggedHistory = true)
        val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        replaceTags(exKey) { current ->
            current.map { tag ->
                val next = when {
                    tag.id == newTagId -> tag.copy(ownsUntaggedHistory = false)
                    tag.isDefault || WorkoutTagResolver.isDefaultName(tag.name) ->
                        tag.copy(ownsUntaggedHistory = true)
                    else -> tag.copy(ownsUntaggedHistory = false)
                }
                persistTag(next)
                next
            }
        }
        persistOngoingState()
        selectMainTag(exerciseId, newTagId)
    }

    fun ensureDefaultTag(exerciseId: String, ownsUntaggedHistory: Boolean): WorkoutTag {
        val existing = tagsForExercise(exerciseId).firstOrNull {
            it.isDefault || WorkoutTagResolver.isDefaultName(it.name)
        }
        if (existing != null) {
            if (ownsUntaggedHistory && !existing.ownsUntaggedHistory) {
                val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId }
                    ?: return existing
                val updated = existing.copy(ownsUntaggedHistory = true)
                persistTag(updated)
                replaceTags(ports.canonicalExerciseKey(exercise)) { current ->
                    current.map { tag -> if (tag.id == existing.id) updated else tag }
                }
                persistOngoingState()
                return updated
            }
            return existing
        }
        return when (
            val created = createTag(
                exerciseId = exerciseId,
                name = WorkoutTagResolver.DEFAULT_TAG_NAME,
                isDefault = true,
                ownsUntaggedHistory = ownsUntaggedHistory,
            )
        ) {
            is CreateTagResult.Created -> created.tag
            is CreateTagResult.Duplicate -> tagsForExercise(exerciseId).first { WorkoutTagResolver.isDefaultName(it.name) }
            CreateTagResult.InvalidName -> WorkoutTag()
        }
    }

    fun toggleMainTagActive(exerciseId: String, tagId: String) {
        val currentTags = getState().activeTagsByExercise[exerciseId].orEmpty()
        if (tagId in currentTags && currentTags.size == 1) {
            selectMainTag(exerciseId, tagId)
            return
        }
        if (tagId in currentTags) {
            val remaining = currentTags - tagId
            val remainingTag = tagsForExercise(exerciseId).firstOrNull { it.id in remaining }
            if (remainingTag != null) {
                selectMainTag(exerciseId, remainingTag.id)
            } else {
                selectMainTag(exerciseId, tagId)
            }
        } else {
            selectMainTag(exerciseId, tagId)
        }
    }

    fun selectMainTag(exerciseId: String, tagId: String) {
        val tag = tagsForExercise(exerciseId).firstOrNull { it.id == tagId } ?: return
        val now = Instant.now().toString()
        val bumped = tag.copy(lastUsedAtIso = now, usageCount = tag.usageCount + 1)
        val exercise = ports.visibleExercises(getState()).firstOrNull { it.id == exerciseId }
        if (exercise != null) {
            persistTag(bumped)
            replaceTags(ports.canonicalExerciseKey(exercise)) { current ->
                current.map { item -> if (item.id == tagId) bumped else item }
            }
        }
        updateState { state ->
            state.copy(
                activeTagsByExercise = state.activeTagsByExercise + (exerciseId to listOf(tagId)),
                exerciseTags = state.exerciseTags + (exerciseId to tag.name),
            )
        }
        if (profileForTag(exerciseId, tagId) != null) {
            setActiveContextProfile(exerciseId, profileForTag(exerciseId, tagId)!!.id)
        } else {
            upsertTagSetup(exerciseId, tagId, TagSetupInput(), makeActive = true)
        }
        ports.clearDraftsForExercise(exerciseId)
        ports.refreshLoadSuggestions()
        persistOngoingState()
    }

    fun addSubTag(exerciseId: String, tagId: String, name: String, category: SubTagCategory) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val subTag = WorkoutSubTag(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            category = category,
        )
        replaceTags(exKey) { existingForEx ->
            existingForEx.map { tag ->
                if (tag.id == tagId) {
                    val updated = tag.copy(subTags = tag.subTags + subTag)
                    persistTag(updated)
                    updated
                } else {
                    tag
                }
            }
        }
        persistOngoingState()
    }

    fun removeSubTag(exerciseId: String, tagId: String, subTagId: String) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        replaceTags(exKey) { existingForEx ->
            existingForEx.map { tag ->
                if (tag.id == tagId) {
                    val updated = tag.copy(subTags = tag.subTags.filter { it.id != subTagId })
                    persistTag(updated)
                    updated
                } else {
                    tag
                }
            }
        }
        updateState {
            it.copy(
                activeSubTagsByExercise = it.activeSubTagsByExercise.mapValues { (exId, subIds) ->
                    if (exId == exerciseId) subIds.filter { id -> id != subTagId } else subIds
                },
            )
        }
        persistOngoingState()
    }

    fun toggleSubTagActive(exerciseId: String, subTagId: String) {
        updateState { state ->
            val currentSubIds = state.activeSubTagsByExercise[exerciseId].orEmpty()
            val updatedSubIds = if (subTagId in currentSubIds) {
                currentSubIds - subTagId
            } else {
                currentSubIds + subTagId
            }
            state.copy(
                activeSubTagsByExercise = state.activeSubTagsByExercise + (exerciseId to updatedSubIds),
            )
        }
        persistOngoingState()
    }

    fun clearAllTags(exerciseId: String) {
        updateState {
            it.copy(
                activeTagsByExercise = it.activeTagsByExercise - exerciseId,
                activeSubTagsByExercise = it.activeSubTagsByExercise - exerciseId,
                exerciseTags = it.exerciseTags - exerciseId,
            )
        }
        persistOngoingState()
        ports.clearDraftsForExercise(exerciseId)
        ports.refreshLoadSuggestions()
    }

    fun tagsForExercise(exerciseId: String): List<WorkoutTag> {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return emptyList()
        val exKey = ports.canonicalExerciseKey(exercise)
        return mergedTagsForExercise(exerciseId, exKey)
    }

    fun activeMainTags(exerciseId: String): List<WorkoutTag> {
        val tagIds = getState().activeTagsByExercise[exerciseId].orEmpty()
        return tagsForExercise(exerciseId).filter { it.id in tagIds }
    }

    fun activeSubTags(exerciseId: String): List<WorkoutSubTag> {
        val subTagIds = getState().activeSubTagsByExercise[exerciseId].orEmpty()
        return tagsForExercise(exerciseId).flatMap { it.subTags }.filter { it.id in subTagIds }
    }

    fun migrateContextProfilesToTags(
        profiles: Map<String, WorkoutContextProfile>,
        exerciseKey: String,
    ): List<WorkoutTag> = WorkoutTagResolver.seedFromProfiles(profiles.values, exerciseKey)

    fun mergeDurableTags(
        exerciseKey: String,
        resumed: List<WorkoutTag>,
        profiles: Map<String, WorkoutContextProfile>,
    ): List<WorkoutTag> {
        val fromRepo = repository.getWorkoutTagsForExercise(exerciseKey)
        val seeded = if (fromRepo.isEmpty()) {
            migrateContextProfilesToTags(profiles, exerciseKey).also { migrated ->
                migrated.forEach(::persistTag)
            }
        } else {
            fromRepo
        }
        val merged = buildList {
            addAll(resumed)
            (seeded + fromRepo).forEach { candidate ->
                if (none { existing ->
                        existing.id == candidate.id ||
                            WorkoutTagResolver.namesMatch(existing.name, candidate.name)
                    }
                ) {
                    add(candidate)
                }
            }
        }.map(WorkoutTagResolver::withNormalized)
        merged.forEach(::persistTag)
        return merged
    }

    fun resolveTagIdFromToken(exerciseId: String, token: String?): String? {
        if (token.isNullOrBlank()) return null
        return tagsForExercise(exerciseId).firstOrNull {
            it.id == token || WorkoutTagResolver.namesMatch(it.name, token)
        }?.id
    }

    private fun persistTag(tag: WorkoutTag) {
        repository.upsertWorkoutTag(WorkoutTagResolver.withNormalized(tag))
    }

    private fun replaceTags(exKey: String, transform: (List<WorkoutTag>) -> List<WorkoutTag>) {
        updateState {
            val next = transform(it.userCreatedTags[exKey].orEmpty()).map(WorkoutTagResolver::withNormalized)
            it.copy(userCreatedTags = it.userCreatedTags + (exKey to next))
        }
    }

    private fun mergedTagsForExercise(exerciseId: String, exKey: String): List<WorkoutTag> {
        val fromState = getState().userCreatedTags[exKey].orEmpty()
        if (fromState.isNotEmpty()) return fromState
        return repository.getWorkoutTagsForExercise(exKey)
    }
}
