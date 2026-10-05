package com.example.kpkn.navigation

/**
 * Ruteo de creación/entrada a módulos UNA VEZ completado el onboarding
 * (post-alta). Tras el alta, cada módulo entra SIEMPRE en su editor directo:
 * nunca se reabren los wizards de módulo (TRAINING_ONLY / NUTRITION_ONLY /
 * RINGS_ONLY) como navegación ejecutable.
 *
 * Una sola excepción deliberada: [libraryPlanRoute], el plan que la persona
 * eligió en la biblioteca. Ese camino NO es «crear un programa» (el editor
 * directo no evalúa nada): es configurar un plan del catálogo, y el asistente
 * es el evaluador (material, días y tiempo, r2 §16.1).
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

    /**
     * Ruta del asistente para «Configurar este plan» desde la biblioteca de planes (E-18, H8): lleva el plan
     * [planId] al asistente como intención.
     *
     * - Alta completada: asistente de SOLO ENTRENAMIENTO (`TRAINING_ONLY`). La persona ya dio sus datos y ya tiene
     *   (o decidió no tener) su nutrición y sus anillos: el asistente completo le volvería a pedir un plan de
     *   nutrición y la calibración de Rings, y al activar los pisaría. Con `TRAINING_ONLY` el alta solo escribe el
     *   programa y su parche de Ajustes (`onboardingProgramDone`); no toca nutrición ni marca el alta como completa
     *   otra vez.
     * - Alta incompleta: asistente completo (`FULL`), como siempre.
     *
     * Cómo esquiva el guardia de este objeto: el guardia es de CREACIÓN de programa ([createProgramRoute]) y de los
     * enlaces externos (`DeepLinkRouter` manda `kpkn://setup/wizard?mode=TRAINING_ONLY` al editor directo, y no se
     * toca). Esta ruta es navegación interna con un plan elegido, no pasa por `DeepLinkRouter`, y el plan viaja en
     * `planId`: sin plan no hay nada que evaluar y el llamador usa [createProgramRoute].
     */
    fun libraryPlanRoute(onboardingCompleted: Boolean, planId: String): String =
        KpknRoute.SetupWizard.create(
            mode = if (onboardingCompleted) MODE_TRAINING_ONLY else MODE_FULL,
            planId = planId,
        )

    /** Valores de `SetupWizardMode` que viajan en la ruta del asistente (el nombre del enum). */
    private const val MODE_TRAINING_ONLY = "TRAINING_ONLY"
    private const val MODE_FULL = "FULL"
}
