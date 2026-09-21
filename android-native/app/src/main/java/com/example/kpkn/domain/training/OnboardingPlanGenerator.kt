package com.example.kpkn.domain.training

/**
 * Stable onboarding boundary for custom plans. The wizard owns the answers;
 * the personalizer owns catalog compatibility, volume limits and provenance.
 */
class OnboardingPlanGenerator(
    private val personalizer: SimpleCyclePersonalizer,
) {
    fun generate(programId: String, input: PersonalizerInput): PersonalizationResult =
        personalizer.personalize(programId, input)
}
