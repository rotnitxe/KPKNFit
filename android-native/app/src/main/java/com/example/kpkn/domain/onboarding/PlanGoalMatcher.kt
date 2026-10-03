package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingReference

/**
 * Regla ÚNICA de «¿esta entrada del catálogo sirve a este objetivo?» (DEC-w2-06).
 *
 * La comparten el wizard (prefiltro de capacidades de Atleta completo en
 * [SetupTrainingPlanner]) y la biblioteca de planes (`CreateProgramTemplateSheet`),
 * de modo que el filtro «Fuerza» de la biblioteca ofrezca exactamente lo que
 * ofrece el objetivo Fuerza del wizard, y no lo que cuelga de otro objetivo:
 * `native:powerbuilding-foundation-v2` declara la capacidad de fuerza, pero es de
 * «Fuerza y músculo» y no de powerlifting.
 *
 * Es una función pura sobre los metadatos de la entrada: nada de Android, nada
 * de borradores.
 *
 * | Objetivo            | La entrada sirve cuando…                                    |
 * |---------------------|-------------------------------------------------------------|
 * | STRENGTH            | `POWERLIFTING` está en `references`                         |
 * | MUSCLE              | `HYPERTROPHY` está en `references`                          |
 * | STRENGTH_MUSCLE     | `POWERBUILDING` está en `references`                        |
 * | COMPLETE_ATHLETE    | `capabilities` contiene fuerza, hipertrofia, potencia y cardio |
 * | LEGACY_MIXED        | programa cardio de verdad ([CatalogEntry.schedulesCardio])  |
 * | LEGACY_HEALTH       | siempre: no filtra por disciplina (ver [matches])           |
 */
object PlanGoalMatcher {
    /** Atleta completo exige las cuatro capacidades en la misma entrada (§15.1). */
    private val COMPLETE_ATHLETE_CAPABILITIES: Set<TrainingCapability> = setOf(
        TrainingCapability.STRENGTH,
        TrainingCapability.HYPERTROPHY,
        TrainingCapability.POWER,
        TrainingCapability.CARDIO,
    )

    /**
     * ¿Sirve [entry] al objetivo [goal]?
     *
     * `LEGACY_HEALTH` devuelve `true` porque el wizard no le asigna disciplina propia:
     * su referencia sale del estilo que la persona declara en la calibración de volumen
     * (`SetupWizardDraft.trainingReference()`), y ese dato no viaja en [PlanGoalProfile].
     */
    fun matches(entry: CatalogEntry, goal: PlanGoalProfile): Boolean = when (goal) {
        PlanGoalProfile.STRENGTH -> TrainingReference.POWERLIFTING in entry.references
        PlanGoalProfile.MUSCLE -> TrainingReference.HYPERTROPHY in entry.references
        PlanGoalProfile.STRENGTH_MUSCLE -> TrainingReference.POWERBUILDING in entry.references
        PlanGoalProfile.COMPLETE_ATHLETE -> entry.capabilities.containsAll(requiredCapabilities(goal))
        PlanGoalProfile.LEGACY_MIXED -> entry.schedulesCardio
        PlanGoalProfile.LEGACY_HEALTH -> true
    }

    /**
     * Capacidades que una entrada debe declarar TODAS para servir a [goal]; vacío cuando
     * el objetivo se decide por disciplina (`references`) y no por capacidades. Es el
     * prefiltro barato que `SetupTrainingPlannerInput.requiredCapabilities` aplica a
     * Atleta completo, para que el evaluador no tenga que rechazar con PROFILE_MISMATCH
     * medio catálogo antes de llegar al plan propio.
     */
    fun requiredCapabilities(goal: PlanGoalProfile): Set<TrainingCapability> = when (goal) {
        PlanGoalProfile.COMPLETE_ATHLETE -> COMPLETE_ATHLETE_CAPABILITIES
        PlanGoalProfile.STRENGTH, PlanGoalProfile.MUSCLE, PlanGoalProfile.STRENGTH_MUSCLE,
        PlanGoalProfile.LEGACY_MIXED, PlanGoalProfile.LEGACY_HEALTH -> emptySet()
    }
}
