package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo

data class VariantGroup(
    val id: String,
    val name: String,
    val variants: List<ExerciseMuscleInfo>,
    val sharedAspects: List<String>, // technical aspect IDs common to all variants
)

object VariantGroupIndex {

    private var groups: Map<String, VariantGroup> = emptyMap()

    fun rebuild(catalog: List<ExerciseMuscleInfo>) {
        val byGroupId = catalog
            .filter { !it.variantGroupId.isNullOrBlank() }
            .groupBy { it.variantGroupId!! }

        groups = byGroupId.mapValues { (groupId, exercises) ->
            val groupName = exercises.firstNotNullOfOrNull { it.variantGroupName } ?: groupId
            val allAspectIds = exercises
                .flatMap { it.technicalAspects.orEmpty() }
                .map { it.id }
                .distinct()
            VariantGroup(
                id = groupId,
                name = groupName,
                variants = exercises.sortedBy { it.variantOrder ?: Int.MAX_VALUE },
                sharedAspects = allAspectIds,
            )
        }
    }

    fun getGroup(variantGroupId: String): VariantGroup? = groups[variantGroupId]

    fun hasGroup(exercise: ExerciseMuscleInfo): Boolean =
        !exercise.variantGroupId.isNullOrBlank()

    fun allGroupIds(): Set<String> = groups.keys

    fun clear() {
        groups = emptyMap()
    }
}
