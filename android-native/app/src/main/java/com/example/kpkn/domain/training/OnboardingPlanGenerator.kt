package com.example.kpkn.domain.training

import com.example.kpkn.domain.onboarding.SetupTrainingOptions

/**
 * Stable onboarding boundary for custom plans. The wizard owns the answers;
 * the personalizer owns catalog compatibility, volume limits and provenance.
 */
class OnboardingPlanGenerator(
    private val personalizer: SimpleCyclePersonalizer,
) {
    /**
     * Boundary that the onboarding ViewModel calls. [options] is the real
     * training contract (order bag, autoregulation mode, inventory, warmups);
     * defaulted so every existing caller keeps compiling while the engine
     * applies it in the native route.
     *
     * Lo que deja escrito en el `Program` generado (persiste en su JSON, no en
     * el estado del onboarding, y por eso sobrevive a rematerializaciones):
     * - `autoregulationMode` (PROPOSE por defecto; AUTO solo con confirmación
     *   explícita, si no se rechaza el alta con el motivo).
     * - `planWarmupConfig` (null = preset 40 % × 8 / 60 % × 5 / 80 % × 3,
     *   vacío = sin aproximaciones, lista = pasos propios).
     * - `planOrderPriorities` (la bolsa de orden realmente aplicada; ver
     *   [OrderPrioritiesContract.capabilitiesOf] para contrastarla).
     * El inventario declarado no se copia al programa: se guarda en Settings y
     * lo consume la ruta que realiza cargas (`realizeWarmupLoads` +
     * `WarmupFeasibility`).
     */
    fun generate(
        programId: String,
        input: PersonalizerInput,
        options: SetupTrainingOptions = SetupTrainingOptions(),
    ): PersonalizationResult =
        personalizer.personalize(programId, input, options)
}
