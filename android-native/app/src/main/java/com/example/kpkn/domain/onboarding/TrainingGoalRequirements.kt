package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability

/**
 * Qué material pide cada perfil de objetivo ([TrainingGoalProfile]). Los tres perfiles generales se adaptan a
 * cualquier material, incluido el cuerpo solo; las disciplinas llevan a planes de autor o a una versión del generador
 * que solo tiene sentido con ciertos implementos, así que el paso GOAL **no deja confirmar** una disciplina cuyo
 * material no está marcado (y el motivo se dice en una línea, sin culpar).
 *
 * | Perfil           | Necesita                                                        |
 * |------------------|-----------------------------------------------------------------|
 * | POWERLIFTING     | barra, rack y banco                                             |
 * | POWERBUILDING    | barra, rack y banco, o mancuernas                               |
 * | BODYBUILDING     | al menos uno de barra, mancuernas, máquinas, poleas o Smith     |
 * | CALISTHENICS     | barra de dominadas o anillas                                    |
 * | WEIGHTLIFTING    | barra y rack                                                    |
 * | STRONGMAN        | barra y (mancuernas o kettlebell)                               |
 * | ARMWRESTLING     | al menos uno de mancuernas, poleas, bandas, barra o kettlebell  |
 * | generales (3)    | nada: siempre compatibles                                       |
 *
 * Función pura sobre los símbolos de material ([EquipmentSymbolId]); la sobrecarga con [EquipmentAvailability] lee el
 * material declarado del borrador con [EquipmentSymbols.selectedFrom].
 */
object TrainingGoalRequirements {

    /** ¿El material [symbols] alcanza para [profile]? Los generales siempre. */
    fun isCompatible(profile: TrainingGoalProfile, symbols: Set<EquipmentSymbolId>): Boolean {
        val barbell = EquipmentSymbolId.BARBELL in symbols
        val rack = EquipmentSymbolId.RACK in symbols
        val bench = EquipmentSymbolId.BENCH in symbols
        val dumbbells = EquipmentSymbolId.DUMBBELLS in symbols
        val kettlebell = EquipmentSymbolId.KETTLEBELL in symbols
        return when (profile) {
            TrainingGoalProfile.STRENGTH_MUSCLE,
            TrainingGoalProfile.STRENGTH_CARDIO,
            TrainingGoalProfile.FUNCTIONAL_HEALTH -> true
            TrainingGoalProfile.POWERLIFTING -> barbell && rack && bench
            TrainingGoalProfile.POWERBUILDING -> (barbell && rack && bench) || dumbbells
            TrainingGoalProfile.BODYBUILDING ->
                barbell || dumbbells || EquipmentSymbolId.MACHINES in symbols ||
                    EquipmentSymbolId.CABLE in symbols || EquipmentSymbolId.SMITH in symbols
            TrainingGoalProfile.CALISTHENICS ->
                EquipmentSymbolId.PULL_UP_BAR in symbols || EquipmentSymbolId.RINGS in symbols
            TrainingGoalProfile.WEIGHTLIFTING -> barbell && rack
            TrainingGoalProfile.STRONGMAN -> barbell && (dumbbells || kettlebell)
            TrainingGoalProfile.ARMWRESTLING ->
                dumbbells || EquipmentSymbolId.CABLE in symbols || EquipmentSymbolId.BANDS in symbols ||
                    barbell || kettlebell
        }
    }

    /** Igual que [isCompatible], leyendo el material declarado; sin declarar equivale a ningún implemento. */
    fun isCompatible(profile: TrainingGoalProfile, availability: EquipmentAvailability?): Boolean =
        isCompatible(profile, EquipmentSymbols.selectedFrom(availability))

    /** Lo que le falta al material [symbols] para [profile] en una línea («Necesita barra, rack y banco.»); null si es compatible. */
    fun missingText(profile: TrainingGoalProfile, symbols: Set<EquipmentSymbolId>): String? =
        if (isCompatible(profile, symbols)) null else requirementText(profile)

    fun missingText(profile: TrainingGoalProfile, availability: EquipmentAvailability?): String? =
        missingText(profile, EquipmentSymbols.selectedFrom(availability))

    /** Texto fijo de lo que pide [profile]; null para los generales, que no piden nada. */
    fun requirementText(profile: TrainingGoalProfile): String? = when (profile) {
        TrainingGoalProfile.STRENGTH_MUSCLE,
        TrainingGoalProfile.STRENGTH_CARDIO,
        TrainingGoalProfile.FUNCTIONAL_HEALTH -> null
        TrainingGoalProfile.POWERLIFTING -> "Necesita barra, rack y banco."
        TrainingGoalProfile.POWERBUILDING -> "Necesita barra, rack y banco, o mancuernas."
        TrainingGoalProfile.BODYBUILDING -> "Necesita mancuernas, barra, poleas o máquinas."
        TrainingGoalProfile.CALISTHENICS -> "Necesita barra de dominadas o anillas."
        TrainingGoalProfile.WEIGHTLIFTING -> "Necesita barra y rack."
        TrainingGoalProfile.STRONGMAN -> "Necesita barra y mancuernas o kettlebell."
        TrainingGoalProfile.ARMWRESTLING -> "Necesita mancuernas, poleas, bandas, barra o kettlebell."
    }

    /** Los perfiles (generales y disciplinas) que el material [symbols] permite elegir, en el orden del contrato. */
    fun compatibleProfiles(symbols: Set<EquipmentSymbolId>): List<TrainingGoalProfile> =
        TrainingGoalProfile.entries.filter { isCompatible(it, symbols) }
}
