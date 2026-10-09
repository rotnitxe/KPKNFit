package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.canonicalSelection

/**
 * Traducción entre los símbolos de músculo del paso PRIORITIES ([MuscleSymbol]) y los **músculos canónicos** del motor
 * de orden (la bolsa `orderPriorities`: `ORDER_MUSCLE_OPTIONS` + `orderPointsFromBag`). Un símbolo elegido escribe un
 * punto en su músculo canónico; la bolsa solo reordena ejercicios dentro de la sesión.
 */
object MuscleSymbols {

    /** Cuántos músculos se pueden priorizar a la vez (la bolsa del motor admite 5 puntos en total). */
    const val MAX_SELECTION = 5

    /**
     * «Trapecio» salió de los símbolos (va incluido en Espalda), pero sigue siendo un músculo de la bolsa del motor:
     * es el nombre que traen los borradores antiguos. `SetupDraftCompatibility` lo pasa a Espalda al abrirlos.
     */
    const val RETIRED_TRAPS_KEY = "Trapecio"

    /** Nombre canónico del motor para [symbol]; es una clave de `ORDER_MUSCLE_OPTIONS`. */
    fun canonical(symbol: MuscleSymbol): String = when (symbol) {
        MuscleSymbol.CHEST -> "Pectorales"
        MuscleSymbol.BACK -> "Dorsales"
        MuscleSymbol.SHOULDERS -> "Deltoides"
        MuscleSymbol.BICEPS -> "Bíceps"
        MuscleSymbol.TRICEPS -> "Tríceps"
        // El catálogo y el motor lo llaman «Antebrazo» (singular): así lo cuentan el volumen y la bolsa de orden.
        MuscleSymbol.FOREARMS -> "Antebrazo"
        MuscleSymbol.ABS -> "Abdomen"
        MuscleSymbol.GLUTES -> "Glúteos"
        MuscleSymbol.QUADS -> "Cuádriceps"
        MuscleSymbol.HAMSTRINGS -> "Isquiosurales"
        MuscleSymbol.CALVES -> "Pantorrillas"
    }

    private val byCanonical: Map<String, MuscleSymbol> = MuscleSymbol.entries.associateBy { canonical(it) }

    /**
     * Símbolo de un músculo de la bolsa. Acepta el nombre canónico y los sinónimos que el motor normaliza («pecho»,
     * «antebrazos»…); null para los músculos de la bolsa sin símbolo (p. ej. «Erectores Espinales», de borradores antiguos).
     */
    fun fromCanonical(name: String): MuscleSymbol? = byCanonical[name] ?: byCanonical[canonicalSelection(name)]

    /** Músculos canónicos de [symbols], en el orden del contrato de símbolos y sin repetir. */
    fun canonicalSet(symbols: Set<MuscleSymbol>): Set<String> =
        MuscleSymbol.entries.filter { it in symbols }.mapTo(linkedSetOf<String>()) { canonical(it) }

    /** La bolsa de orden de [symbols]: un punto por músculo canónico (nunca más de [MAX_SELECTION] en total). */
    fun orderBagOf(symbols: Set<MuscleSymbol>): Map<String, Int> =
        MuscleSymbol.entries.filter { it in symbols }.take(MAX_SELECTION).associate { canonical(it) to 1 }

    /** Los símbolos que dice una bolsa de orden (los músculos sin símbolo se ignoran). */
    fun symbolsOf(bag: Map<String, Int>): Set<MuscleSymbol> =
        bag.filterValues { it > 0 }.keys.mapNotNullTo(linkedSetOf<MuscleSymbol>()) { fromCanonical(it) }
}

/**
 * Músculos que se **sugieren** (preseleccionados y rotulados «Sugerido») según el perfil de objetivo; la persona los
 * acepta, los cambia o los omite: nunca se confirman solos. Culturismo y los perfiles generales no sugieren nada
 * («libre»). Cada sugerencia cabe en el tope de [MuscleSymbols.MAX_SELECTION].
 */
object MuscleSuggestions {

    fun forProfile(profile: TrainingGoalProfile?): Set<MuscleSymbol> = when (profile) {
        TrainingGoalProfile.POWERLIFTING -> linkedSetOf(
            MuscleSymbol.CHEST, MuscleSymbol.GLUTES, MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS, MuscleSymbol.ABS,
        )
        TrainingGoalProfile.ARMWRESTLING -> linkedSetOf(MuscleSymbol.FOREARMS, MuscleSymbol.BICEPS)
        TrainingGoalProfile.POWERBUILDING -> linkedSetOf(
            MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.QUADS, MuscleSymbol.HAMSTRINGS,
        )
        TrainingGoalProfile.CALISTHENICS -> linkedSetOf(
            MuscleSymbol.BACK, MuscleSymbol.CHEST, MuscleSymbol.ABS, MuscleSymbol.SHOULDERS,
        )
        // El trapecio que sugerían estos dos perfiles va ahora en Espalda (sin duplicarla en Strongman).
        TrainingGoalProfile.WEIGHTLIFTING -> linkedSetOf(
            MuscleSymbol.SHOULDERS, MuscleSymbol.QUADS, MuscleSymbol.GLUTES, MuscleSymbol.BACK, MuscleSymbol.ABS,
        )
        TrainingGoalProfile.STRONGMAN -> linkedSetOf(
            MuscleSymbol.BACK, MuscleSymbol.ABS, MuscleSymbol.QUADS, MuscleSymbol.FOREARMS,
        )
        TrainingGoalProfile.BODYBUILDING,
        TrainingGoalProfile.STRENGTH_MUSCLE,
        TrainingGoalProfile.STRENGTH_CARDIO,
        TrainingGoalProfile.FUNCTIONAL_HEALTH,
        null -> emptySet()
    }
}
