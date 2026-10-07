package com.example.kpkn.domain.onboarding

/**
 * Reglas del paso CAPABILITIES («¿Qué ejercicios ya te salen?»): cuándo se pregunta y por cuáles ejercicios de peso
 * corporal. El motor usa el nivel de cada uno para elegir la variante de flexión, dominada, fondo o sentadilla a una
 * pierna; por eso no se pregunta cuando el material ya cubre el trabajo (barra con rack o máquinas) ni a quien entrena
 * una disciplina de pesas.
 */
object CapabilityRules {

    /**
     * ¿Entra CAPABILITIES en la ruta? Solo con un objetivo general o con Calistenia **y** si la persona es novata o el
     * material es ligero (sin barra con rack ni máquinas): ahí el peso corporal es buena parte del programa.
     */
    fun asks(profile: TrainingGoalProfile?, novice: Boolean, symbols: Set<EquipmentSymbolId>): Boolean {
        if (profile == null) return false
        val bodyweightDriven = !profile.isSpecific || profile == TrainingGoalProfile.CALISTHENICS
        return bodyweightDriven && (novice || isLightMaterial(symbols))
    }

    /** Sin barra y rack, y sin máquinas: el programa se apoya en mancuernas, bandas, apoyos o el propio cuerpo. */
    fun isLightMaterial(symbols: Set<EquipmentSymbolId>): Boolean =
        !(EquipmentSymbolId.BARBELL in symbols && EquipmentSymbolId.RACK in symbols) &&
            EquipmentSymbolId.MACHINES !in symbols

    /**
     * Habilidades que se ofrecen con el material [symbols], en el orden del contrato: las dominadas solo con barra de
     * dominadas, los fondos con paralelas o con banco, y las flexiones y la sentadilla a una pierna siempre.
     */
    fun skillsFor(symbols: Set<EquipmentSymbolId>): List<CapabilitySkill> = CapabilitySkill.entries.filter { skill ->
        when (skill) {
            CapabilitySkill.PULL_UP -> EquipmentSymbolId.PULL_UP_BAR in symbols
            CapabilitySkill.DIP -> EquipmentSymbolId.PARALLEL_BARS in symbols || EquipmentSymbolId.BENCH in symbols
            CapabilitySkill.PUSH_UP, CapabilitySkill.PISTOL_SQUAT -> true
        }
    }
}
