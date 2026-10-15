package com.example.kpkn.domain.relator

/** Prioridad de un momento: la alta sustituye el mensaje al instante; la media y la baja esperan 12 s (LR1g). */
enum class RelatorMomentLevel(val order: Int) {
    HIGH(0),
    MEDIUM(1),
    LOW(2),
}

/**
 * Momento en que el relator puede decir algo (LR1g). El mensaje solo cambia cuando pasa uno de estos.
 * [topics] son los temas que el motor prefiere para ese momento; si ninguno tiene frase, usa cualquiera.
 */
enum class RelatorMoment(val level: RelatorMomentLevel, val topics: Set<RelatorTopic>) {
    /** Entra un ejercicio nuevo, o su calentamiento. */
    EXERCISE_START(
        RelatorMomentLevel.HIGH,
        setOf(RelatorTopic.PLAN, RelatorTopic.WARMUP, RelatorTopic.SITUATE),
    ),

    /** Una serie pasa a ser la actual: acaba el descanso o la persona navega. */
    SET_READY(
        RelatorMomentLevel.HIGH,
        setOf(RelatorTopic.PLAN, RelatorTopic.SITUATE, RelatorTopic.READINESS, RelatorTopic.HISTORY, RelatorTopic.TECHNIQUE),
    ),

    /** Se registra una serie: la reacción es lo primero que se ve en el descanso. */
    SET_LOGGED(
        RelatorMomentLevel.HIGH,
        setOf(RelatorTopic.REACTION, RelatorTopic.MILESTONE),
    ),

    /** La persona cambia la carga, las reps o el RIR de la serie actual; se asienta 1,5 s tras la última edición. */
    USER_EDIT(
        RelatorMomentLevel.HIGH,
        setOf(RelatorTopic.REACTION),
    ),

    /** Aparece una ayuda de estructura. */
    ASSIST(
        RelatorMomentLevel.HIGH,
        setOf(RelatorTopic.STRUCTURE_ASSIST),
    ),

    /** Quedan 15 s o menos de descanso: una vez por descanso. */
    REST_PREP(
        RelatorMomentLevel.MEDIUM,
        setOf(RelatorTopic.REST),
    ),

    /** La mitad del descanso, si dura 150 s o más: una vez. */
    REST_MID(
        RelatorMomentLevel.LOW,
        setOf(RelatorTopic.REST, RelatorTopic.HISTORY),
    ),

    /** Mitad de la sesión, último ejercicio o tiempo justo: una vez cada uno. */
    MILESTONE(
        RelatorMomentLevel.LOW,
        setOf(RelatorTopic.MILESTONE, RelatorTopic.SESSION_PROGRESS),
    ),

    /** En la serie, 120 s sin registrar ni editar: una vez por serie. */
    IDLE_NUDGE(
        RelatorMomentLevel.LOW,
        setOf(RelatorTopic.SITUATE, RelatorTopic.TECHNIQUE, RelatorTopic.COACH),
    ),
}
