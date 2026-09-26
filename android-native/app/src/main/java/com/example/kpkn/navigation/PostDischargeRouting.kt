package com.example.kpkn.navigation

/**
 * Ruteo de creación/entrada a módulos UNA VEZ completado el onboarding
 * (post-alta). Tras el alta, cada módulo entra SIEMPRE en su editor directo:
 * nunca se reabren los wizards de módulo (TRAINING_ONLY / NUTRITION_ONLY /
 * RINGS_ONLY) como navegación ejecutable.
 */
object PostDischargeRouting {

    /**
     * Ruta de creación de programa según el estado de onboarding.
     *
     * - Alta completada: editor directo ([ProgramEditor]); no auto-activa nada
     *   y no muta estado hasta que el usuario guarda.
     * - Onboarding incompleto: wizard FULL real (puerta de bienvenida); la
     *   recuperación RESUME del borrador completo sigue intacta aguas arriba.
     */
    fun createProgramRoute(onboardingCompleted: Boolean): String =
        if (onboardingCompleted) {
            KpknRoute.ProgramEditor.create()
        } else {
            KpknRoute.SetupWizard.create()
        }
}