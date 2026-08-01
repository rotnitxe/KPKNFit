package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutContextProfile
import java.util.UUID

private fun isInternalWorkoutTagId(value: String): Boolean =
    runCatching { UUID.fromString(value) }.isSuccess

internal fun workoutTagDisplayTitle(
    tagName: String?,
    machineBrand: String?,
): String {
    val name = tagName?.trim().orEmpty().takeUnless(::isInternalWorkoutTagId).orEmpty()
    val brand = machineBrand?.trim().orEmpty()
    return when {
        name.isBlank() -> brand
        brand.isBlank() -> name
        name.equals(brand, ignoreCase = true) -> name
        name.substringAfterLast('·').trim().equals(brand, ignoreCase = true) -> name
        else -> "$name · $brand"
    }
}

internal fun WorkoutContextProfile.persistentTagName(): String? {
    val setupName = setupLabel?.trim().takeIf { !it.isNullOrBlank() }
    val brandName = machineBrand?.trim().takeIf { !it.isNullOrBlank() }
    val legacyId = tagId?.trim().takeIf { !it.isNullOrBlank() && !isInternalWorkoutTagId(it) }
    return setupName ?: brandName ?: legacyId
}

internal fun WorkoutContextProfile.tagDisplayTitle(): String =
    workoutTagDisplayTitle(persistentTagName(), machineBrand)

internal fun WorkoutContextProfile.legacyTagName(): String? =
    persistentTagName()
