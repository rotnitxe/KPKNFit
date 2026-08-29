package com.example.kpkn.screens.workout

enum class CarouselValueTone {
    Neutral,
    OnPlan,
    BelowPlan,
    AbovePlan,
}

internal fun carouselToneForRepEvaluation(evaluation: RepRangeEvaluation?): CarouselValueTone {
    if (evaluation == null) return CarouselValueTone.Neutral
    return when {
        evaluation.isInRange -> CarouselValueTone.OnPlan
        evaluation.delta < 0.0 -> CarouselValueTone.BelowPlan
        evaluation.delta > 0.0 -> CarouselValueTone.AbovePlan
        else -> CarouselValueTone.OnPlan
    }
}

internal fun carouselToneForIntensityFeedback(
    isExecutionError: Boolean,
    reachedFailure: Boolean,
    difficultyLabel: String?,
    intensityDelta: Double?,
    matchesPlanned: Boolean = false,
): CarouselValueTone {
    if (isExecutionError) return CarouselValueTone.Neutral
    if (reachedFailure) return CarouselValueTone.BelowPlan
    return when (difficultyLabel) {
        "Más fácil" -> CarouselValueTone.BelowPlan
        "Más difícil" -> CarouselValueTone.AbovePlan
        "Igual" -> CarouselValueTone.OnPlan
        else -> when {
            matchesPlanned -> CarouselValueTone.OnPlan
            intensityDelta == null -> CarouselValueTone.Neutral
            intensityDelta <= -0.5 -> CarouselValueTone.BelowPlan
            intensityDelta >= 0.5 -> CarouselValueTone.AbovePlan
            else -> CarouselValueTone.OnPlan
        }
    }
}
