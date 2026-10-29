package com.example.kpkn.navigation

/**
 * Ruteo de creación/entrada a módulos UNA VEZ completado el onboarding
 * (post-alta). Tras el alta, cada módulo entra SIEMPRE en su editor directo:
 * nunca se reabren los wizards de módulo (TRAINING_ONLY / NUTRITION_ONLY /
 * RINGS_ONLY) como navegación ejecutable.
 *
 * [libraryPlanRoute] abre el asistente interno desde la biblioteca, con un plan
 * elegido o desde «Crear mi programa a medida». Evalúa material, días y objetivo.
 * La creación manual conserva su editor directo.
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
     * - Alta completada: la instancia «Nuevo programa» del asistente (`NEW_PROGRAM`, Registros v4: solo entreno, con
     *   «Lo que ya sabemos» plegado y el cierre «Listo para empezar»). La persona ya dio sus datos y ya tiene
     *   (o decidió no tener) su nutrición y sus anillos: el asistente completo le volvería a pedir un plan de
     *   nutrición y la calibración de Rings, y al activar los pisaría. Con `TRAINING_ONLY` el alta solo escribe el
     *   programa y su parche de Ajustes (`onboardingProgramDone`); no toca nutrición ni marca el alta como completa
     *   otra vez.
     * - Alta incompleta: asistente completo (`FULL`), como siempre.
     *
     * Cómo esquiva el guardia de este objeto: el guardia es de CREACIÓN de programa ([createProgramRoute]) y de los
     * enlaces externos (`DeepLinkRouter` manda `kpkn://setup/wizard?mode=TRAINING_ONLY` a «A tu medida» en la instancia
     * nueva, CR-D4). Esta ruta es navegación interna con un plan elegido, no pasa por `DeepLinkRouter`, y el plan viaja en
     * `planId`. Sin preselección evalúa los programas a medida de las respuestas.
     */
    fun libraryPlanRoute(onboardingCompleted: Boolean, planId: String? = null): String =
        KpknRoute.SetupWizard.create(
            mode = if (onboardingCompleted) MODE_NEW_PROGRAM else MODE_FULL,
            planId = planId,
        )

    /** Valores de `SetupWizardMode` que viajan en la ruta del asistente (el nombre del enum). */
    const val MODE_NEW_PROGRAM = "NEW_PROGRAM"
    private const val MODE_FULL = "FULL"
}
