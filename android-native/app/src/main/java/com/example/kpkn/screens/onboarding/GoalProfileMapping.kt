package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile

/**
 * Traducciones puras del perfil de objetivo del paso GOAL ([TrainingGoalProfile], diez perfiles) a lo que el resto de
 * la app ya entiende. Vive junto a [SetupGoal] porque este enum es del wizard; no depende de Android.
 *
 * - [setupGoalOf]: el objetivo «legacy» que lee el motor actual (candidatos, planes propios, cardio). Para los
 *   generales y las cuatro disciplinas nuevas el programa lo servirá el generador nuevo; mientras tanto basta una
 *   asignación segura que no rompa los candidatos de hoy ([planGoalProfileOf] traduce el resto).
 * - [trainingStyleOf]: el estilo de calibración de volumen (POWERLIFTER / BODYBUILDER / POWERBUILDER).
 * - [athleteTypeOf]: el tipo de atleta de AUGE (capacidad de fatiga). El alta lo escribe en Ajustes al activar.
 * - [profileOfLegacy]: el perfil que hoy corresponde a un objetivo antiguo (migración de borradores).
 */
object GoalProfileMapping {

    fun setupGoalOf(profile: TrainingGoalProfile): SetupGoal = when (profile) {
        TrainingGoalProfile.STRENGTH_MUSCLE -> SetupGoal.STRENGTH_MUSCLE
        TrainingGoalProfile.STRENGTH_CARDIO -> SetupGoal.COMPLETE_ATHLETE
        TrainingGoalProfile.FUNCTIONAL_HEALTH -> SetupGoal.FUNCTIONAL
        TrainingGoalProfile.POWERBUILDING -> SetupGoal.STRENGTH_MUSCLE
        TrainingGoalProfile.CALISTHENICS -> SetupGoal.CALISTHENICS
        TrainingGoalProfile.BODYBUILDING -> SetupGoal.MUSCLE
        TrainingGoalProfile.WEIGHTLIFTING -> SetupGoal.WEIGHTLIFTING
        TrainingGoalProfile.ARMWRESTLING -> SetupGoal.ARMWRESTLING
        TrainingGoalProfile.STRONGMAN -> SetupGoal.STRONGMAN
        TrainingGoalProfile.POWERLIFTING -> SetupGoal.STRENGTH
    }

    /**
     * Estilo de calibración: Powerlifting y Halterofilia → POWERLIFTER; Culturismo, Calistenia y Armwrestling →
     * BODYBUILDER; el resto (Fuerza y masa muscular, Fuerza y cardio, Funcional, Powerbuilding, Strongman) → POWERBUILDER.
     */
    fun trainingStyleOf(profile: TrainingGoalProfile): TrainingStyle = when (profile) {
        TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.WEIGHTLIFTING -> TrainingStyle.POWERLIFTER
        TrainingGoalProfile.BODYBUILDING, TrainingGoalProfile.CALISTHENICS, TrainingGoalProfile.ARMWRESTLING ->
            TrainingStyle.BODYBUILDER
        TrainingGoalProfile.STRENGTH_MUSCLE, TrainingGoalProfile.STRENGTH_CARDIO,
        TrainingGoalProfile.FUNCTIONAL_HEALTH, TrainingGoalProfile.POWERBUILDING,
        TrainingGoalProfile.STRONGMAN -> TrainingStyle.POWERBUILDER
    }

    /**
     * Tipo de atleta de AUGE: Fuerza y masa muscular y Funcional y Armwrestling → ENTHUSIAST; Fuerza y cardio → HYBRID;
     * Powerbuilding → POWERBUILDER; Calistenia → CALISTHENICS; Culturismo → BODYBUILDER; Halterofilia → WEIGHTLIFTER;
     * Powerlifting y Strongman → POWERLIFTER.
     */
    fun athleteTypeOf(profile: TrainingGoalProfile): AthleteType = when (profile) {
        TrainingGoalProfile.STRENGTH_MUSCLE, TrainingGoalProfile.FUNCTIONAL_HEALTH,
        TrainingGoalProfile.ARMWRESTLING -> AthleteType.ENTHUSIAST
        TrainingGoalProfile.STRENGTH_CARDIO -> AthleteType.HYBRID
        TrainingGoalProfile.POWERBUILDING -> AthleteType.POWERBUILDER
        TrainingGoalProfile.CALISTHENICS -> AthleteType.CALISTHENICS
        TrainingGoalProfile.BODYBUILDING -> AthleteType.BODYBUILDER
        TrainingGoalProfile.WEIGHTLIFTING -> AthleteType.WEIGHTLIFTER
        TrainingGoalProfile.STRONGMAN, TrainingGoalProfile.POWERLIFTING -> AthleteType.POWERLIFTER
    }

    /**
     * El perfil que hoy corresponde a un objetivo de un borrador antiguo: Fuerza → Powerlifting, Músculo → Culturismo,
     * Fuerza y músculo → Fuerza y masa muscular, Atleta completo y Fuerza + cardio → Fuerza y cardio, Salud → Funcional
     * y saludable. Sirve para leer borradores; nada se confirma por ello.
     */
    fun profileOfLegacy(goal: SetupGoal): TrainingGoalProfile = when (goal) {
        SetupGoal.STRENGTH -> TrainingGoalProfile.POWERLIFTING
        SetupGoal.MUSCLE -> TrainingGoalProfile.BODYBUILDING
        SetupGoal.STRENGTH_MUSCLE -> TrainingGoalProfile.STRENGTH_MUSCLE
        SetupGoal.COMPLETE_ATHLETE, SetupGoal.MIXED -> TrainingGoalProfile.STRENGTH_CARDIO
        SetupGoal.HEALTH, SetupGoal.FUNCTIONAL -> TrainingGoalProfile.FUNCTIONAL_HEALTH
        SetupGoal.CALISTHENICS -> TrainingGoalProfile.CALISTHENICS
        SetupGoal.WEIGHTLIFTING -> TrainingGoalProfile.WEIGHTLIFTING
        SetupGoal.ARMWRESTLING -> TrainingGoalProfile.ARMWRESTLING
        SetupGoal.STRONGMAN -> TrainingGoalProfile.STRONGMAN
    }
}

/**
 * El tipo de atleta que el alta escribe en Ajustes al activar, o null si no debe tocarlo: solo cuando el perfil existe y
 * el paso GOAL se respondió en ESTE alta (la persona lo declaró o aceptó el perfil sugerido). Un perfil derivado de un
 * paso sin confirmar nunca pisa lo que Ajustes ya tenía.
 */
internal fun SetupWizardDraft.athleteTypeToPersist(): AthleteType? {
    val profile = goalProfile ?: return null
    val answered = isStepDeclared(SetupStepId.GOAL) ||
        stepProgress.answers[SetupStepId.GOAL]?.canPersistAsDeclared() == true
    return if (answered) GoalProfileMapping.athleteTypeOf(profile) else null
}
