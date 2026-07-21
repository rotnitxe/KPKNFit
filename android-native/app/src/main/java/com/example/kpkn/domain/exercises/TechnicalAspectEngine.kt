package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.ModifierType
import com.example.kpkn.data.models.MuscleModifier
import com.example.kpkn.data.models.MuscleRole

object TechnicalAspectEngine {

    data class EffectiveMuscleResult(
        val effectiveMuscles: List<InvolvedMuscle>,
        val summary: String,
    )

    fun computeEffectiveMuscles(
        baseMuscles: List<InvolvedMuscle>,
        selectedOptions: List<AspectOption>,
    ): EffectiveMuscleResult {
        val modifiers = selectedOptions.flatMap { it.modifiers }
        if (modifiers.isEmpty()) {
            return EffectiveMuscleResult(baseMuscles, "Sin modificaciones")
        }

        val builder = EffectiveMuscleBuilder(baseMuscles)
        modifiers.forEach { modifier -> builder.apply(modifier) }
        val effective = builder.build()

        val descriptions = selectedOptions.mapNotNull { opt ->
            if (opt.modifiers.isNotEmpty()) opt.name else null
        }
        val summary = if (descriptions.isNotEmpty()) {
            descriptions.joinToString(" + ")
        } else {
            "Sin modificaciones"
        }

        return EffectiveMuscleResult(effective, summary)
    }

    private class EffectiveMuscleBuilder(private val baseMuscles: List<InvolvedMuscle>) {
        private val muscleMap = baseMuscles.associateBy { it.muscle }.toMutableMap()
        private val order = baseMuscles.map { it.muscle }.toMutableList()
        private val additions = mutableListOf<InvolvedMuscle>()

        fun apply(modifier: MuscleModifier) {
            when (modifier.type) {
                ModifierType.SET -> applySet(modifier)
                ModifierType.ADD -> applyAdd(modifier)
                ModifierType.MULT -> applyMult(modifier)
            }
        }

        private fun applySet(modifier: MuscleModifier) {
            val key = modifier.muscle
            val existing = muscleMap[key]
            val newEntry = InvolvedMuscle(
                muscle = key,
                role = modifier.role ?: existing?.role ?: MuscleRole.PRIMARY,
                volumeContribution = modifier.value,
                emphasis = modifier.emphasis ?: existing?.emphasis,
            )
            muscleMap[key] = newEntry
            if (key !in order) order.add(key)
        }

        private fun applyAdd(modifier: MuscleModifier) {
            val key = modifier.muscle
            val existing = muscleMap[key]
            if (existing != null) {
                val currentVC = existing.volumeContribution ?: 1.0
                muscleMap[key] = existing.copy(
                    role = modifier.role ?: existing.role,
                    volumeContribution = (currentVC + modifier.value).coerceIn(0.0, 1.0),
                    emphasis = modifier.emphasis ?: existing.emphasis,
                )
            } else {
                muscleMap[key] = InvolvedMuscle(
                    muscle = key,
                    role = modifier.role ?: MuscleRole.PRIMARY,
                    volumeContribution = modifier.value.coerceIn(0.0, 1.0),
                    emphasis = modifier.emphasis,
                )
                if (key !in order) order.add(key)
            }
        }

        private fun applyMult(modifier: MuscleModifier) {
            val key = modifier.muscle
            val existing = muscleMap[key] ?: InvolvedMuscle(
                muscle = key,
                role = modifier.role ?: MuscleRole.PRIMARY,
                volumeContribution = 1.0,
                emphasis = modifier.emphasis,
            )
            val currentVC = existing.volumeContribution ?: 1.0
            muscleMap[key] = existing.copy(
                role = modifier.role ?: existing.role,
                volumeContribution = (currentVC * modifier.value).coerceIn(0.0, 1.0),
                emphasis = modifier.emphasis ?: existing.emphasis,
            )
            if (key !in order) order.add(key)
        }

        fun build(): List<InvolvedMuscle> {
            val result = mutableListOf<InvolvedMuscle>()
            for (muscleName in order) {
                muscleMap[muscleName]?.let { result.add(it) }
            }
            for ((key, value) in muscleMap) {
                if (key !in order) result.add(value)
            }
            return result
        }
    }
}
