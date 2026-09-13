package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.domain.workout.WorkoutTagResolver

internal fun workoutTagDisplayTitle(
    tagName: String?,
    machineBrand: String?,
): String {
    val name = tagName?.trim().orEmpty().takeUnless(WorkoutTagResolver::isInternalId).orEmpty()
    val brand = machineBrand?.trim().orEmpty()
    return when {
        name.isBlank() -> brand
        brand.isBlank() -> name
        name.equals(brand, ignoreCase = true) -> name
        name.substringAfterLast('·').trim().equals(brand, ignoreCase = true) -> name
        else -> "$name · $brand"
    }
}

internal fun WorkoutContextProfile.persistentTagName(): String? =
    WorkoutTagResolver.profilePersistentName(this)

internal fun WorkoutContextProfile.tagDisplayTitle(): String =
    workoutTagDisplayTitle(persistentTagName(), machineBrand)

internal fun WorkoutContextProfile.legacyTagName(): String? =
    persistentTagName()
