package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.SubTagCategory
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.repository.ProgramRepository
import java.util.UUID

data class TagSetupInput(
    val machineBrand: String? = null,
    val baseLoadKg: Double? = null,
    val setupNotes: String? = null,
) {
    val hasContent: Boolean
        get() = !machineBrand.isNullOrBlank() || baseLoadKg != null || !setupNotes.isNullOrBlank()
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
            createdAtIso = java.time.Instant.now().toString(),
            lastUsedAtIso = java.time.Instant.now().toString(),
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
                existingTags.firstOrNull { it.id == tagId || it.name == tagId }
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
            lastUsedAtIso = java.time.Instant.now().toString(),
            usageCount = profile.usageCount + 1,
        )
        repository.upsertContextProfile(updated)
        updateState {
            val existingTags = tagsForExercise(exercise.id)
            val match = updated.tagId?.let { tagId -> existingTags.firstOrNull { it.id == tagId || it.name == tagId } }
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
                activeTagsByExercise = if (tagIds.isNotEmpty()) it.activeTagsByExercise + (exercise.id to tagIds) else it.activeTagsByExercise,
            )
        }
        persistOngoingState()
    }

    fun createTag(
        exerciseId: String,
        name: String,
        setup: TagSetupInput? = null,
    ): WorkoutTag {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return WorkoutTag()
        val exKey = ports.canonicalExerciseKey(exercise)
        val normalizedName = name.trim().ifBlank { setup?.machineBrand?.trim().orEmpty() }
        if (normalizedName.isBlank()) return WorkoutTag()
        val tag = WorkoutTag(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            exerciseKey = exKey,
            createdAtIso = java.time.Instant.now().toString(),
            lastUsedAtIso = java.time.Instant.now().toString(),
            usageCount = 0,
        )
        val existingForEx = state.userCreatedTags[exKey].orEmpty()
        updateState {
            it.copy(userCreatedTags = it.userCreatedTags + (exKey to (existingForEx + tag)))
        }
        persistOngoingState()
        toggleMainTagActive(exerciseId, tag.id)
        upsertTagSetup(exerciseId, tag.id, setup ?: TagSetupInput(), makeActive = true)
        return tag
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
            createdAtIso = java.time.Instant.now().toString(),
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
        val existingForEx = state.userCreatedTags[exKey].orEmpty().filter { it.id != tagId }
        val profileId = profileForTag(exerciseId, tagId)?.id
        val deletedProfileIds = setOfNotNull(profileId, "$exKey|tag|$tagId")
        repository.deleteContextProfile(profileId ?: "$exKey|tag|$tagId")
        if (profileId != null && profileId != "$exKey|tag|$tagId") {
            repository.deleteContextProfile("$exKey|tag|$tagId")
        }
        updateState {
            it.copy(
                userCreatedTags = it.userCreatedTags + (exKey to existingForEx),
                contextProfilesV3 = it.contextProfilesV3 - listOfNotNull(profileId, "$exKey|tag|$tagId"),
                activeContextProfileByExerciseId = it.activeContextProfileByExerciseId
                    .filterValues { activeProfileId -> activeProfileId !in deletedProfileIds },
                activeTagsByExercise = it.activeTagsByExercise.mapValues { (exId, tagIds) ->
                    if (exId == exerciseId) tagIds.filter { it != tagId } else tagIds
                },
                exerciseTags = if (tagName != null && state.exerciseTags[exerciseId] == tagName) it.exerciseTags - exerciseId else it.exerciseTags,
            )
        }
        persistOngoingState()
    }

    fun renameTag(exerciseId: String, tagId: String, newName: String) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val trimmedName = newName.trim()
        val oldTag = state.userCreatedTags[exKey].orEmpty().firstOrNull { it.id == tagId } ?: return
        val oldName = oldTag.name
        val existingProfile = profileForTag(exerciseId, tagId)
        val updatedForEx = state.userCreatedTags[exKey].orEmpty().map { tag ->
            if (tag.id == tagId) tag.copy(name = trimmedName) else tag
        }
        updateState {
            it.copy(
                userCreatedTags = it.userCreatedTags + (exKey to updatedForEx),
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
    }

    fun toggleMainTagActive(exerciseId: String, tagId: String) {
        val stateBefore = getState()
        val currentTags = stateBefore.activeTagsByExercise[exerciseId].orEmpty()
        val activating = tagId !in currentTags
        updateState { state ->
            val tags = state.activeTagsByExercise[exerciseId].orEmpty()
            val updatedTags = if (tagId in tags) {
                tags - tagId
            } else {
                tags + tagId
            }
            val activeTagName = state.userCreatedTags.values.flatten()
                .firstOrNull { it.id == tagId }?.name
            state.copy(
                activeTagsByExercise = state.activeTagsByExercise + (exerciseId to updatedTags),
                exerciseTags = if (activeTagName != null && activating) {
                    state.exerciseTags + (exerciseId to activeTagName)
                } else if (!activating && state.exerciseTags[exerciseId] == activeTagName) {
                    state.exerciseTags - exerciseId
                } else {
                    state.exerciseTags
                },
            )
        }
        if (activating) {
            profileForTag(exerciseId, tagId)?.let { profile ->
                setActiveContextProfile(exerciseId, profile.id)
            }
        }
        persistOngoingState()
    }

    fun selectMainTag(exerciseId: String, tagId: String) {
        val tag = tagsForExercise(exerciseId).firstOrNull { it.id == tagId } ?: return
        updateState { state ->
            state.copy(
                activeTagsByExercise = state.activeTagsByExercise + (exerciseId to listOf(tagId)),
                exerciseTags = state.exerciseTags + (exerciseId to tag.name),
            )
        }
        profileForTag(exerciseId, tagId)?.let { profile ->
            setActiveContextProfile(exerciseId, profile.id)
        }
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
        val existingForEx = state.userCreatedTags[exKey].orEmpty()
        val updatedForEx = existingForEx.map { tag ->
            if (tag.id == tagId) tag.copy(subTags = tag.subTags + subTag) else tag
        }
        updateState {
            it.copy(userCreatedTags = it.userCreatedTags + (exKey to updatedForEx))
        }
        persistOngoingState()
    }

    fun removeSubTag(exerciseId: String, tagId: String, subTagId: String) {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return
        val exKey = ports.canonicalExerciseKey(exercise)
        val existingForEx = state.userCreatedTags[exKey].orEmpty()
        val updatedForEx = existingForEx.map { tag ->
            if (tag.id == tagId) tag.copy(subTags = tag.subTags.filter { it.id != subTagId }) else tag
        }
        updateState {
            it.copy(
                userCreatedTags = it.userCreatedTags + (exKey to updatedForEx),
                activeSubTagsByExercise = it.activeSubTagsByExercise.mapValues { (exId, subIds) ->
                    if (exId == exerciseId) subIds.filter { it != subTagId } else subIds
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
    }

    fun tagsForExercise(exerciseId: String): List<WorkoutTag> {
        val state = getState()
        val exercise = ports.visibleExercises(state).firstOrNull { it.id == exerciseId } ?: return emptyList()
        val exKey = ports.canonicalExerciseKey(exercise)
        return state.userCreatedTags[exKey].orEmpty()
    }

    fun activeMainTags(exerciseId: String): List<WorkoutTag> {
        val state = getState()
        val tagIds = state.activeTagsByExercise[exerciseId].orEmpty()
        return tagsForExercise(exerciseId).filter { it.id in tagIds }
    }

    fun activeSubTags(exerciseId: String): List<WorkoutSubTag> {
        val state = getState()
        val subTagIds = state.activeSubTagsByExercise[exerciseId].orEmpty()
        return tagsForExercise(exerciseId).flatMap { it.subTags }.filter { it.id in subTagIds }
    }

    fun migrateContextProfilesToTags(
        profiles: Map<String, WorkoutContextProfile>,
        exerciseKey: String,
    ): List<WorkoutTag> {
        return profiles.values
            .filter { it.exerciseKey == exerciseKey }
            .filter { it.tagId != null || it.setupLabel != null }
            .distinctBy { (it.tagId ?: it.setupLabel ?: it.id) }
            .map { profile ->
                val subTags = buildList {
                    profile.machineBrand?.let { add(WorkoutSubTag(name = it, category = SubTagCategory.MARCA)) }
                    profile.setupDetails?.seatPosition?.let { add(WorkoutSubTag(name = "Asiento: $it", category = SubTagCategory.SETUP)) }
                    profile.setupDetails?.pinPosition?.let { add(WorkoutSubTag(name = "Pin: $it", category = SubTagCategory.SETUP)) }
                    com.example.kpkn.domain.workout.BaseLoadPolicy.resolvedFromProfile(profile)?.let {
                        add(WorkoutSubTag(name = "Carga base: ${it}kg", category = SubTagCategory.SETUP))
                    }
                    profile.setupDetails?.equipmentNotes?.let { add(WorkoutSubTag(name = it, category = SubTagCategory.SETUP)) }
                }
                val persistentName = profile.persistentTagName()
                WorkoutTag(
                    id = profile.tagId ?: profile.id,
                    name = persistentName ?: "Migrado",
                    exerciseKey = profile.exerciseKey,
                    subTags = subTags,
                    createdAtIso = profile.createdAtIso ?: "",
                    lastUsedAtIso = profile.lastUsedAtIso ?: "",
                    usageCount = profile.usageCount,
                )
            }
    }
}
