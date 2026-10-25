package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.training.RecipeContractPolicy

internal fun kpknMuscleGroupLabel(name: String): String =
    KpknMuscleGroup.entries.firstOrNull { it.name == name }?.let(RecipeContractPolicy::volumeGroupLabel) ?: name
