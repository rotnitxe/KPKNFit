package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.NutritionCalibrationProfile
import kotlin.math.abs

/** The six short, resumable steps of food-habit calibration. */
enum class NutritionCalibrationWizardStep(val index: Int, val title: String) {
    WEIGHING_CONVENTION(0, "Convención de pesaje"),
    UTENSILS(1, "Medidas domésticas"),
    STAPLE_PORTIONS(2, "Porciones de arroz, pasta, papas y legumbres"),
    PROTEIN_PORTIONS(3, "Porciones de proteínas"),
    PREPARATION_OIL(4, "Preparación y aceite"),
    REVIEW(5, "Resumen editable"),
}

data class NutritionCalibrationWizardState(
    val profile: NutritionCalibrationProfile = NutritionCalibrationProfile(),
    val step: NutritionCalibrationWizardStep = NutritionCalibrationWizardStep.WEIGHING_CONVENTION,
    val skippedSteps: Set<NutritionCalibrationWizardStep> = emptySet(),
) {
    val isComplete: Boolean get() = profile.wizardCompleted
    val isSkipped: Boolean get() = profile.wizardSkipped
}

/**
 * Pure state machine for the food calibration wizard. Persistence belongs to
 * [NutritionCalibrationRepository]; this object only applies confirmed answers.
 */
object NutritionCalibrationWizardEngine {
    const val VERSION = 1
    const val MATURE_CONFIRMATIONS = 3
    const val MATURE_TOLERANCE = 0.15

    fun start(profile: NutritionCalibrationProfile? = null): NutritionCalibrationWizardState {
        val stored = profile ?: NutritionCalibrationProfile(wizardVersion = VERSION)
        val index = stored.wizardStep.coerceIn(0, NutritionCalibrationWizardStep.entries.lastIndex)
        return NutritionCalibrationWizardState(
            profile = stored.copy(wizardVersion = VERSION),
            step = NutritionCalibrationWizardStep.entries[index],
        )
    }

    fun answer(
        state: NutritionCalibrationWizardState,
        value: String,
        confirmed: Boolean = true,
    ): NutritionCalibrationWizardState {
        if (!confirmed || value.isBlank()) return state
        val now = System.currentTimeMillis()
        val updated = when (state.step) {
            NutritionCalibrationWizardStep.WEIGHING_CONVENTION ->
                state.profile.copy(weighingConvention = value.trim().uppercase())
            NutritionCalibrationWizardStep.UTENSILS ->
                state.profile.copy(utensilVolumesMl = parseMap(value, state.profile.utensilVolumesMl))
            NutritionCalibrationWizardStep.STAPLE_PORTIONS,
            NutritionCalibrationWizardStep.PROTEIN_PORTIONS -> state.profile
            NutritionCalibrationWizardStep.PREPARATION_OIL ->
                state.profile.copy(
                    preparationProfiles = parseStringMap(value, state.profile.preparationProfiles),
                )
            NutritionCalibrationWizardStep.REVIEW -> state.profile.copy(wizardCompleted = true, wizardSkipped = false)
        }.copy(
            wizardVersion = VERSION,
            wizardSkipped = false,
            lastWizardUpdatedAtEpochMs = now,
        )
        return advance(state.copy(profile = updated))
    }

    /** Records a confirmed household portion; estimates and Unsure must not call this. */
    fun recordConfirmedPortion(
        profile: NutritionCalibrationProfile,
        key: String,
        grams: Double,
        confirmed: Boolean = true,
    ): NutritionCalibrationProfile {
        if (!confirmed || grams <= 0.0 || !grams.isFinite() || key.isBlank()) return profile
        val normalizedKey = key.trim().lowercase()
        val samples = (profile.confirmedPortions[normalizedKey].orEmpty() + grams)
            .filter { it.isFinite() && it > 0.0 }
            .takeLast(MATURE_CONFIRMATIONS)
        val center = samples.sorted()[samples.size / 2]
        val mature = samples.size >= MATURE_CONFIRMATIONS &&
            samples.all { abs(it - center) / center.coerceAtLeast(1.0) <= MATURE_TOLERANCE }
        val habitual = profile.habitualPortionsGrams + (normalizedKey to center)
        val matureValues = if (mature) profile.maturePortionsGrams + (normalizedKey to center)
        else profile.maturePortionsGrams - normalizedKey
        return profile.copy(
            wizardVersion = VERSION,
            confirmedPortions = profile.confirmedPortions + (normalizedKey to samples),
            habitualPortionsGrams = habitual,
            maturePortionsGrams = matureValues,
            lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun recordConfirmedIdentity(
        profile: NutritionCalibrationProfile,
        query: String,
        canonicalId: String,
        confirmed: Boolean = true,
    ): NutritionCalibrationProfile = if (!confirmed || query.isBlank() || canonicalId.isBlank()) profile else profile.copy(
        identityMappings = profile.identityMappings + (query.trim().lowercase() to canonicalId.trim()),
        lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
    )

    fun recordConfirmedState(
        profile: NutritionCalibrationProfile,
        family: String,
        state: WeightBasis,
        confirmed: Boolean = true,
    ): NutritionCalibrationProfile = if (!confirmed || family.isBlank() || state == WeightBasis.UNKNOWN) profile else profile.copy(
        statePreferences = profile.statePreferences + (family.trim().lowercase() to state.name),
        lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
    )

    fun recordConfirmedOil(
        profile: NutritionCalibrationProfile,
        family: String,
        gramsPer100g: Double,
        confirmed: Boolean = true,
    ): NutritionCalibrationProfile = if (!confirmed || family.isBlank() || !gramsPer100g.isFinite() || gramsPer100g < 0.0) profile else profile.copy(
        oilProfiles = profile.oilProfiles + (family.trim().lowercase() to gramsPer100g),
        lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
    )

    fun skip(state: NutritionCalibrationWizardState): NutritionCalibrationWizardState {
        val skipped = state.skippedSteps + state.step
        val profile = state.profile.copy(
            wizardVersion = VERSION,
            wizardSkipped = true,
            wizardStep = nextIndex(state.step),
            lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
        )
        return NutritionCalibrationWizardState(profile, nextStep(state.step), skipped)
    }

    fun resume(profile: NutritionCalibrationProfile): NutritionCalibrationWizardState = start(
        profile.copy(wizardSkipped = false),
    )

    fun reset(profile: NutritionCalibrationProfile = NutritionCalibrationProfile()): NutritionCalibrationProfile =
        profile.copy(
            wizardVersion = VERSION,
            wizardStep = 0,
            wizardSkipped = false,
            wizardCompleted = false,
            weighingConvention = null,
            utensilVolumesMl = emptyMap(),
            habitualPortionsGrams = emptyMap(),
            maturePortionsGrams = emptyMap(),
            confirmedPortions = emptyMap(),
            identityMappings = emptyMap(),
            statePreferences = emptyMap(),
            preparationProfiles = emptyMap(),
            oilProfiles = emptyMap(),
            lastWizardUpdatedAtEpochMs = System.currentTimeMillis(),
        )

    fun maturePortion(profile: NutritionCalibrationProfile?, key: String): Double? =
        profile?.maturePortionsGrams?.get(key.trim().lowercase())

    private fun advance(state: NutritionCalibrationWizardState): NutritionCalibrationWizardState {
        val next = nextStep(state.step)
        val completed = state.step == NutritionCalibrationWizardStep.REVIEW
        return state.copy(
            profile = state.profile.copy(
                wizardStep = next.index,
                wizardCompleted = completed,
            ),
            step = next,
        )
    }

    private fun nextStep(step: NutritionCalibrationWizardStep): NutritionCalibrationWizardStep =
        NutritionCalibrationWizardStep.entries[(step.index + 1).coerceAtMost(NutritionCalibrationWizardStep.entries.lastIndex)]

    private fun nextIndex(step: NutritionCalibrationWizardStep): Int = nextStep(step).index

    /** Compact UI input format: `taza=240,vaso=250`; never stores free meal text. */
    private fun parseMap(value: String, previous: Map<String, Double>): Map<String, Double> {
        val parsed = value.split(',').mapNotNull { token ->
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val number = parts[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapNotNull null
            parts[0].trim().lowercase().takeIf { it.isNotBlank() }?.let { it to number }
        }
        return previous + parsed
    }

    private fun parseStringMap(value: String, previous: Map<String, String>): Map<String, String> {
        val parsed = value.split(',').mapNotNull { token ->
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val key = parts[0].trim().lowercase().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val answer = parts[1].trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            key to answer
        }
        return previous + parsed
    }
}
