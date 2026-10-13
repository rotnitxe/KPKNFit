package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.canonicalSelection

/**
 * Traducción entre los símbolos de músculo del paso PRIORITIES ([MuscleSymbol]) y los **músculos canónicos** del motor
 * de orden (la bolsa `orderPriorities`: `ORDER_MUSCLE_OPTIONS` + `orderPointsFromBag`). Un símbolo elegido escribe un
 * punto en cada uno de sus músculos canónicos («Brazos» escribe en bíceps y en tríceps); la bolsa solo reordena
 * ejercicios dentro de la sesión.
 */
object MuscleSymbols {

    /**
     * Cuántos SÍMBOLOS de músculo se pueden priorizar a la vez. La bolsa del motor admite más puntos que símbolos porque
     * «Brazos» escribe dos (bíceps y tríceps): ver `SimpleCyclePersonalizer.MAX_ORDER_POINTS_TOTAL`.
     */
    const val MAX_SELECTION = 5

    /**
     * «Trapecio» salió de los símbolos (va incluido en Espalda), pero sigue siendo un músculo de la bolsa del motor:
     * es el nombre que traen los borradores antiguos. `SetupDraftCompatibility` lo pasa a Espalda al abrirlos.
     */
    const val RETIRED_TRAPS_KEY = "Trapecio"

    /**
     * Nombres canónicos del motor de [symbol], todos claves de `ORDER_MUSCLE_OPTIONS`. Casi todo símbolo es un músculo;
     * «Brazos» son dos (bíceps y tríceps), con un punto cada uno en la bolsa.
     */
    fun canonicals(symbol: MuscleSymbol): List<String> = when (symbol) {
        MuscleSymbol.CHEST -> listOf("Pectorales")
        MuscleSymbol.BACK -> listOf("Dorsales")
        MuscleSymbol.SHOULDERS -> listOf("Deltoides")
        MuscleSymbol.ARMS -> listOf("Bíceps", "Tríceps")
        // El catálogo y el motor lo llaman «Antebrazo» (singular): así lo cuentan el volumen y la bolsa de orden.
        MuscleSymbol.FOREARMS -> listOf("Antebrazo")
        MuscleSymbol.ABS -> listOf("Abdomen")
        MuscleSymbol.GLUTES -> listOf("Glúteos")
        MuscleSymbol.QUADS -> listOf("Cuádriceps")
        MuscleSymbol.HAMSTRINGS -> listOf("Isquiosurales")
        MuscleSymbol.CALVES -> listOf("Pantorrillas")
    }

    private val byCanonical: Map<String, MuscleSymbol> =
        MuscleSymbol.entries.flatMap { symbol -> canonicals(symbol).map { it to symbol } }.toMap()

    /**
     * Símbolo de un músculo de la bolsa. Acepta el nombre canónico y los sinónimos que el motor normaliza («pecho»,
     * «antebrazos», «brazos»…); null para los músculos de la bolsa sin símbolo (p. ej. «Erectores Espinales», de borradores antiguos).
     */
    fun fromCanonical(name: String): MuscleSymbol? = byCanonical[name] ?: byCanonical[canonicalSelection(name)]

    /** Músculos canónicos de [symbols], en el orden del contrato de símbolos y sin repetir. */
    fun canonicalSet(symbols: Set<MuscleSymbol>): Set<String> =
        MuscleSymbol.entries.filter { it in symbols }.flatMapTo(linkedSetOf<String>()) { canonicals(it) }

    /**
     * La bolsa de orden de [symbols]: un punto por músculo canónico, de los primeros [MAX_SELECTION] símbolos en el orden
     * del contrato. Con «Brazos» salen dos puntos (bíceps y tríceps), así que la bolsa puede llegar a seis.
     */
    fun orderBagOf(symbols: Set<MuscleSymbol>): Map<String, Int> =
        MuscleSymbol.entries.filter { it in symbols }.take(MAX_SELECTION)
            .flatMap { canonicals(it) }
            .associateWith { 1 }

    /**
     * Los símbolos que dice una bolsa de orden (los músculos sin símbolo se ignoran). «Bíceps» o «Tríceps», solos o
     * juntos, dicen «Brazos».
     */
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
        TrainingGoalProfile.ARMWRESTLING -> linkedSetOf(MuscleSymbol.FOREARMS, MuscleSymbol.ARMS)
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
