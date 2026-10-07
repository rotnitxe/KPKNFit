package com.example.kpkn.domain.training.generator

/*
 * Vocabulario interno del generador: huecos de patrón, planes de sesión y su región.
 */

/** Papel de un hueco en la sesión. Decide series base, repeticiones, descansos y orden de ejecución. */
internal enum class ItemRole(val execRank: Double) {
    POWER(0.0),
    MAIN(1.0),
    SECONDARY(2.0),
    ACCESSORY(3.0),
    ISOLATION(4.0),
    CARRY(5.0),
    CORE(6.0),
}

/** Región de la sesión: qué huecos admite cuando una prioridad pide uno extra. */
internal enum class SessionRegion { PUSH, PULL, LEGS, UPPER, FULL, NONE }

/**
 * Un hueco de la sesión: qué patrón cubre y con qué papel. [pairKey] marca superseries posibles (dos huecos de la
 * misma sesión con la misma clave se agrupan si la sesión usa superseries). [extra] = hueco añadido por una prioridad.
 */
internal data class SlotSpec(
    val pattern: RoutinePattern,
    val role: ItemRole,
    val sets: Int? = null,
    val pairKey: Int? = null,
    val extra: Boolean = false,
    val boosted: Boolean = false,
)

internal enum class CardioMode {
    /** Bloque fijo (minutos del plan) tras la fuerza. */
    BLOCK,

    /** La sesión es cardio continuo (zona 2) y ocupa el tiempo de la sesión. */
    ZONE2_FILL,

    /** La sesión es cardio por intervalos y ocupa el tiempo de la sesión. */
    INTERVALS_FILL,

    /** Cardio suave (caminata) que completa el tiempo de una sesión de recuperación. */
    LIGHT_FILL,
}

internal enum class MobilityFocus { LOWER, UPPER, FULL, RECOVERY }

/** Plan de movilidad de una sesión: [seconds] fijos (si es > 0) o el relleno de la sesión de recuperación. */
internal data class MobilitySpec(val focus: MobilityFocus, val seconds: Int, val fill: Boolean = false)

/**
 * Plantilla de una sesión de la semana. [slots] va en ORDEN DE PRIORIDAD de inclusión (con poco tiempo se queda con los
 * primeros); el orden de ejecución sale después de elegir los ejercicios (potencia, principales, secundarios, accesorios,
 * aislamiento, acarreo y core). [demand] mide lo exigente que es (cuántos compuestos pesados); la sesión de mayor demanda
 * es la «principal» y cae en el día más fresco.
 */
internal data class SessionPlan(
    val key: String,
    val title: String,
    val focus: String,
    val kind: RoutineSessionKind,
    val region: SessionRegion,
    val slots: List<SlotSpec>,
    val cardio: CardioMode? = null,
    val mobility: MobilitySpec? = null,
    val supersets: Boolean = false,
    val demand: Double = 0.0,
)

/** Músculos canónicos de volumen (los de `VolumeCalculator`). */
internal object Muscles {
    const val CHEST = "Pectorales"
    const val LATS = "Dorsales"
    const val TRAPS = "Trapecio"
    const val RHOMBOIDS = "Romboides"
    const val DELTS = "Deltoides"
    const val BICEPS = "Bíceps"
    const val TRICEPS = "Tríceps"
    const val FOREARMS = "Antebrazo"
    const val QUADS = "Cuádriceps"
    const val HAMS = "Isquiosurales"
    const val GLUTES = "Glúteos"
    const val CALVES = "Pantorrillas"
    const val ABS = "Abdomen"
    const val CORE = "Core"
    const val ERECTORS = "Erectores Espinales"
    const val ADDUCTORS = "Aductores"
    const val NECK = "Cuello"
}

/** Qué hace cada patrón: región, músculos que trabaja de forma principal y peso para medir la demanda de una sesión. */
internal data class PatternInfo(
    val region: SessionRegion,
    val muscles: Set<String>,
    val demandWeight: Double,
    /** Un patrón de aislamiento nunca es el principal de la sesión. */
    val isIsolation: Boolean,
)

internal object PatternCatalog {
    private fun info(region: SessionRegion, vararg muscles: String, weight: Double = 1.0, isolation: Boolean = false) =
        PatternInfo(region, muscles.toSet(), weight, isolation)

    val info: Map<RoutinePattern, PatternInfo> = mapOf(
        RoutinePattern.SQUAT to info(SessionRegion.LEGS, Muscles.QUADS, Muscles.GLUTES, weight = 4.0),
        RoutinePattern.SINGLE_LEG to info(SessionRegion.LEGS, Muscles.QUADS, Muscles.GLUTES, weight = 1.5),
        RoutinePattern.HINGE to info(SessionRegion.LEGS, Muscles.HAMS, Muscles.GLUTES, Muscles.ERECTORS, weight = 4.0),
        RoutinePattern.GLUTE to info(SessionRegion.LEGS, Muscles.GLUTES, weight = 1.0),
        RoutinePattern.HORIZONTAL_PUSH to info(SessionRegion.PUSH, Muscles.CHEST, weight = 2.0),
        RoutinePattern.VERTICAL_PUSH to info(SessionRegion.PUSH, Muscles.DELTS, weight = 1.5),
        RoutinePattern.HORIZONTAL_PULL to info(SessionRegion.PULL, Muscles.LATS, Muscles.TRAPS, weight = 2.0),
        RoutinePattern.VERTICAL_PULL to info(SessionRegion.PULL, Muscles.LATS, weight = 1.5),
        RoutinePattern.CHEST_ISOLATION to info(SessionRegion.PUSH, Muscles.CHEST, isolation = true),
        RoutinePattern.SHOULDER_LATERAL to info(SessionRegion.PUSH, Muscles.DELTS, isolation = true),
        RoutinePattern.REAR_DELT to info(SessionRegion.PULL, Muscles.DELTS, isolation = true),
        RoutinePattern.BICEPS to info(SessionRegion.PULL, Muscles.BICEPS, isolation = true),
        RoutinePattern.TRICEPS to info(SessionRegion.PUSH, Muscles.TRICEPS, isolation = true),
        RoutinePattern.TRAPS to info(SessionRegion.PULL, Muscles.TRAPS, isolation = true),
        RoutinePattern.GRIP to info(SessionRegion.PULL, Muscles.FOREARMS, isolation = true),
        RoutinePattern.QUAD_ISOLATION to info(SessionRegion.LEGS, Muscles.QUADS, isolation = true),
        RoutinePattern.HAMSTRING_CURL to info(SessionRegion.LEGS, Muscles.HAMS, isolation = true),
        RoutinePattern.CALF to info(SessionRegion.LEGS, Muscles.CALVES, isolation = true),
        RoutinePattern.CORE_STABILITY to info(SessionRegion.NONE, Muscles.ABS, Muscles.CORE, isolation = true),
        RoutinePattern.CORE_ROTATION to info(SessionRegion.NONE, Muscles.ABS, Muscles.CORE, isolation = true),
        RoutinePattern.BACK_EXTENSION to info(SessionRegion.NONE, Muscles.ERECTORS, isolation = true),
        RoutinePattern.CARRY to info(SessionRegion.NONE, Muscles.FOREARMS, Muscles.TRAPS, weight = 1.0),
        RoutinePattern.POWER to info(SessionRegion.NONE, Muscles.GLUTES, Muscles.HAMS, weight = 1.0),
        RoutinePattern.CARDIO to info(SessionRegion.NONE, weight = 0.0),
        RoutinePattern.MOBILITY to info(SessionRegion.NONE, weight = 0.0),
    )

    fun of(pattern: RoutinePattern): PatternInfo = info.getValue(pattern)

    /** ¿La región de la sesión admite el patrón? Cardio y movilidad no admiten nada de fuerza. */
    fun accepts(region: SessionRegion, pattern: RoutinePattern): Boolean {
        val patternRegion = of(pattern).region
        return when (region) {
            SessionRegion.FULL -> true
            SessionRegion.NONE -> false
            SessionRegion.UPPER -> patternRegion == SessionRegion.PUSH || patternRegion == SessionRegion.PULL || patternRegion == SessionRegion.NONE
            SessionRegion.PUSH -> patternRegion == SessionRegion.PUSH || patternRegion == SessionRegion.NONE
            SessionRegion.PULL -> patternRegion == SessionRegion.PULL || patternRegion == SessionRegion.NONE
            SessionRegion.LEGS -> patternRegion == SessionRegion.LEGS || patternRegion == SessionRegion.NONE
        }
    }
}

/** Músculos que el selector «¿qué músculos quieres mejorar?» refuerza y por qué huecos. */
internal data class PriorityTarget(val muscles: Set<String>, val patterns: List<RoutinePattern>, val extra: List<Pair<RoutinePattern, ItemRole>>)

internal object PriorityTargets {
    private fun t(muscles: Set<String>, patterns: List<RoutinePattern>, extra: List<Pair<RoutinePattern, ItemRole>>) =
        PriorityTarget(muscles, patterns, extra)

    fun of(symbol: com.example.kpkn.domain.onboarding.MuscleSymbol): PriorityTarget = when (symbol) {
        com.example.kpkn.domain.onboarding.MuscleSymbol.CHEST -> t(
            setOf(Muscles.CHEST),
            listOf(RoutinePattern.HORIZONTAL_PUSH, RoutinePattern.CHEST_ISOLATION),
            listOf(RoutinePattern.CHEST_ISOLATION to ItemRole.ISOLATION, RoutinePattern.HORIZONTAL_PUSH to ItemRole.ACCESSORY),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.BACK -> t(
            setOf(Muscles.LATS, Muscles.RHOMBOIDS),
            listOf(RoutinePattern.HORIZONTAL_PULL, RoutinePattern.VERTICAL_PULL),
            listOf(RoutinePattern.VERTICAL_PULL to ItemRole.ACCESSORY, RoutinePattern.HORIZONTAL_PULL to ItemRole.ACCESSORY),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.SHOULDERS -> t(
            setOf(Muscles.DELTS),
            listOf(RoutinePattern.VERTICAL_PUSH, RoutinePattern.SHOULDER_LATERAL, RoutinePattern.REAR_DELT),
            listOf(RoutinePattern.SHOULDER_LATERAL to ItemRole.ISOLATION, RoutinePattern.REAR_DELT to ItemRole.ISOLATION),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.BICEPS -> t(
            setOf(Muscles.BICEPS),
            listOf(RoutinePattern.BICEPS),
            listOf(RoutinePattern.BICEPS to ItemRole.ISOLATION),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.TRICEPS -> t(
            setOf(Muscles.TRICEPS),
            listOf(RoutinePattern.TRICEPS),
            listOf(RoutinePattern.TRICEPS to ItemRole.ISOLATION),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.FOREARMS -> t(
            setOf(Muscles.FOREARMS),
            listOf(RoutinePattern.GRIP, RoutinePattern.CARRY),
            listOf(RoutinePattern.GRIP to ItemRole.ISOLATION, RoutinePattern.CARRY to ItemRole.CARRY),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.ABS -> t(
            setOf(Muscles.ABS, Muscles.CORE),
            listOf(RoutinePattern.CORE_STABILITY, RoutinePattern.CORE_ROTATION),
            listOf(RoutinePattern.CORE_ROTATION to ItemRole.CORE, RoutinePattern.CORE_STABILITY to ItemRole.CORE),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.GLUTES -> t(
            setOf(Muscles.GLUTES),
            listOf(RoutinePattern.GLUTE, RoutinePattern.HINGE, RoutinePattern.SINGLE_LEG),
            listOf(RoutinePattern.GLUTE to ItemRole.ACCESSORY, RoutinePattern.SINGLE_LEG to ItemRole.ACCESSORY),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.QUADS -> t(
            setOf(Muscles.QUADS),
            listOf(RoutinePattern.SQUAT, RoutinePattern.SINGLE_LEG, RoutinePattern.QUAD_ISOLATION),
            listOf(RoutinePattern.SINGLE_LEG to ItemRole.ACCESSORY, RoutinePattern.QUAD_ISOLATION to ItemRole.ISOLATION),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.HAMSTRINGS -> t(
            setOf(Muscles.HAMS),
            listOf(RoutinePattern.HINGE, RoutinePattern.HAMSTRING_CURL),
            listOf(RoutinePattern.HAMSTRING_CURL to ItemRole.ISOLATION, RoutinePattern.HINGE to ItemRole.ACCESSORY),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.CALVES -> t(
            setOf(Muscles.CALVES),
            listOf(RoutinePattern.CALF),
            listOf(RoutinePattern.CALF to ItemRole.ISOLATION),
        )
        com.example.kpkn.domain.onboarding.MuscleSymbol.TRAPS -> t(
            setOf(Muscles.TRAPS),
            listOf(RoutinePattern.TRAPS),
            listOf(RoutinePattern.TRAPS to ItemRole.ISOLATION, RoutinePattern.CARRY to ItemRole.CARRY),
        )
    }
}
