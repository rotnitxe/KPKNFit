package com.example.kpkn.domain.onboarding

import kotlinx.serialization.Serializable

/*
 * Contratos del módulo de Entreno v2 (ver docs/WIZARD_ENTRENO_V2.md).
 *
 * Solo tipos puros y sus textos de interfaz. Los pasos del wizard, el borrador, los motores y los componentes
 * visuales comparten ESTOS enums para no duplicar vocabulario: nadie define otra lista de lugares, implementos,
 * músculos u objetivos.
 */

/**
 * Dónde se entrena. Se elige uno o varios; el material disponible es la UNIÓN de lo que ofrece cada lugar y,
 * si hay más de uno, cada día de entreno puede asignarse a un lugar concreto.
 */
@Serializable
enum class TrainingPlace(val label: String) {
    GYM("Gimnasio"),
    HOME("En casa"),
    PUBLIC("En espacios públicos"),
}

/**
 * Perfiles del paso GOAL. Los tres primeros son **generales** (el programa lo genera KPKN a partir de tu material,
 * tus días y tu tiempo); los siete últimos son **específicos** (llevan a planes de autor de esa disciplina y dependen
 * del material disponible).
 */
@Serializable
enum class TrainingGoalProfile(val label: String, val tagline: String, val isSpecific: Boolean) {
    STRENGTH_MUSCLE("Fuerza y masa muscular", "Pesas y máquinas con una semana sencilla que se repite.", false),
    STRENGTH_CARDIO("Fuerza y cardio", "Atleta híbrido: días de fuerza, de cardio y mixtos.", false),
    FUNCTIONAL_HEALTH("Funcional y saludable", "Fuerza, cardio, potencia y movilidad para sentirte bien.", false),
    POWERBUILDING("Powerbuilding", "Fuerza máxima y estética a la vez.", true),
    CALISTHENICS("Calistenia", "Domina tu peso corporal.", true),
    BODYBUILDING("Culturismo", "Volumen e hipertrofia al detalle.", true),
    WEIGHTLIFTING("Halterofilia", "Arranque y dos tiempos.", true),
    ARMWRESTLING("Armwrestling", "Antebrazo, agarre y fuerza de mesa.", true),
    STRONGMAN("Strongman", "Fuerza bruta y levantamientos de evento.", true),
    POWERLIFTING("Powerlifting", "Sentadilla, banca y peso muerto.", true);

    companion object {
        val general: List<TrainingGoalProfile> = entries.filterNot { it.isSpecific }
        val specific: List<TrainingGoalProfile> = entries.filter { it.isSpecific }
    }
}

/**
 * Implementos del selector de material. Un símbolo = un implemento reconocible; el motor sigue hablando en
 * categorías y llaves curadas (`EquipmentAvailability`), y `EquipmentSymbols` (dominio) traduce en los dos sentidos.
 */
@Serializable
enum class EquipmentSymbolId(val label: String) {
    BARBELL("Barra y discos"),
    RACK("Rack"),
    BENCH("Banco"),
    DUMBBELLS("Mancuernas"),
    KETTLEBELL("Kettlebell"),
    CABLE("Poleas"),
    MACHINES("Máquinas"),
    SMITH("Smith/Multipower"),
    PULL_UP_BAR("Barra de dominadas"),
    PARALLEL_BARS("Paralelas"),
    RINGS("Anillas o TRX"),
    BANDS("Bandas elásticas"),
    BALL("Balón"),
    JUMP_ROPE("Cuerda de saltar"),
    BOX("Cajón o step"),
    CARDIO("Cardio"),

    /** Exclusivo: sin ningún implemento; se entrena con el cuerpo. */
    BODYWEIGHT_ONLY("Solo peso corporal"),
}

/**
 * Músculos del selector «¿Qué músculos quieres mejorar más?». Solo los populares: nada de «redondo mayor» ni
 * «multífidos». La traducción a los músculos canónicos del motor vive en `MuscleSymbols` (dominio).
 */
@Serializable
enum class MuscleSymbol(val label: String) {
    CHEST("Pecho"),
    BACK("Espalda"),
    SHOULDERS("Hombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    ABS("Abdomen"),
    GLUTES("Glúteos"),
    QUADS("Cuádriceps"),
    HAMSTRINGS("Isquios"),
    CALVES("Pantorrillas"),
    TRAPS("Trapecio"),
}

/** Ejercicios de peso corporal por los que se pregunta («¿Qué ejercicios ya te salen?») para ofrecer variantes más fáciles o más difíciles. */
@Serializable
enum class CapabilitySkill(val label: String) {
    PULL_UP("Dominadas"),
    PUSH_UP("Flexiones"),
    DIP("Fondos"),
    PISTOL_SQUAT("Sentadilla a una pierna"),
}

/** Cuánto te sale un [CapabilitySkill]. El orden es el nivel (0, 1, 2). */
@Serializable
enum class CapabilityLevel(val label: String) {
    NONE("Aún no"),
    SOME("Algunas"),
    MANY("Varias"),
}

/** Levantamientos cuya marca (1RM o mejor serie) se puede declarar. Qué se pregunta depende del objetivo. */
@Serializable
enum class LiftMark(val label: String) {
    SQUAT("Sentadilla"),
    BENCH("Press banca"),
    DEADLIFT("Peso muerto"),
    OVERHEAD_PRESS("Press militar"),
    SNATCH("Arranque"),
    CLEAN_AND_JERK("Dos tiempos"),
}
