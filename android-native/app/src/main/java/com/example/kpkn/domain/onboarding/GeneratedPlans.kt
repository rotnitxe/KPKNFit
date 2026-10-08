package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.training.generator.RoutineMode

/**
 * De dónde salen los programas que el paso PLAN ofrece a un perfil de objetivo (Entreno v2).
 */
enum class PlanCandidateSources {
    /** Los tres perfiles generales: solo el programa «a medida» del generador. */
    GENERATED,

    /**
     * Powerlifting, Powerbuilding y Culturismo: el «a medida» del modo `CUSTOM_*` al frente (así nunca falta un programa
     * compatible) y, detrás, los planes propios y de autor del planificador de siempre.
     */
    GENERATED_AND_CATALOG,

    /**
     * Calistenia, Armwrestling, Strongman y Halterofilia: no hay planes de autor, solo el generador en modo disciplina
     * (rotulado «versión inicial» mientras el catálogo no cubra la disciplina entera).
     */
    GENERATED_DISCIPLINE,

    /** Sin perfil (borradores sin migrar y pruebas del motor legado): solo el planificador de siempre. */
    CATALOG,
}

/**
 * Traducción pura entre el perfil de objetivo, la entrada del catálogo de su programa «a medida»
 * (`generated:<sourceId>`, ver [PersonalizedPlanCatalog.GENERATED_IDS]) y el modo del generador de rutinas.
 */
object GeneratedPlans {

    /** Clase de candidatos del perfil (null = sin perfil: el planificador legado). */
    fun sourcesFor(profile: TrainingGoalProfile?): PlanCandidateSources = when (profile) {
        null -> PlanCandidateSources.CATALOG
        TrainingGoalProfile.STRENGTH_MUSCLE,
        TrainingGoalProfile.STRENGTH_CARDIO,
        TrainingGoalProfile.FUNCTIONAL_HEALTH -> PlanCandidateSources.GENERATED
        TrainingGoalProfile.POWERLIFTING,
        TrainingGoalProfile.POWERBUILDING,
        TrainingGoalProfile.BODYBUILDING -> PlanCandidateSources.GENERATED_AND_CATALOG
        TrainingGoalProfile.CALISTHENICS,
        TrainingGoalProfile.ARMWRESTLING,
        TrainingGoalProfile.STRONGMAN,
        TrainingGoalProfile.WEIGHTLIFTING -> PlanCandidateSources.GENERATED_DISCIPLINE
    }

    /** Modo del generador que arma el programa «a medida» de [profile]. */
    fun modeFor(profile: TrainingGoalProfile): RoutineMode = when (profile) {
        TrainingGoalProfile.STRENGTH_MUSCLE -> RoutineMode.GENERAL_STRENGTH_MUSCLE
        TrainingGoalProfile.STRENGTH_CARDIO -> RoutineMode.GENERAL_HYBRID
        TrainingGoalProfile.FUNCTIONAL_HEALTH -> RoutineMode.GENERAL_FUNCTIONAL
        TrainingGoalProfile.CALISTHENICS -> RoutineMode.DISCIPLINE_CALISTHENICS
        TrainingGoalProfile.ARMWRESTLING -> RoutineMode.DISCIPLINE_ARMWRESTLING
        TrainingGoalProfile.STRONGMAN -> RoutineMode.DISCIPLINE_STRONGMAN
        TrainingGoalProfile.WEIGHTLIFTING -> RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE
        TrainingGoalProfile.POWERLIFTING -> RoutineMode.CUSTOM_POWERLIFTING
        TrainingGoalProfile.POWERBUILDING -> RoutineMode.CUSTOM_POWERBUILDING
        TrainingGoalProfile.BODYBUILDING -> RoutineMode.CUSTOM_BODYBUILDING
    }

    /** Id del catálogo del programa «a medida» de [profile] (`generated:strength-muscle`…). */
    fun entryIdFor(profile: TrainingGoalProfile): String = PersonalizedPlanCatalog.GENERATED_PREFIX + sourceIdOf(modeFor(profile))

    /** Modo del generador de la entrada [entryId], o null si no es un programa «a medida». */
    fun modeOf(entryId: String?): RoutineMode? {
        val sourceId = entryId?.takeIf { it.startsWith(PersonalizedPlanCatalog.GENERATED_PREFIX) }
            ?.removePrefix(PersonalizedPlanCatalog.GENERATED_PREFIX) ?: return null
        return RoutineMode.entries.firstOrNull { sourceIdOf(it) == sourceId }
    }

    /** ¿[entryId] es un programa «a medida» del generador? */
    fun isGenerated(entryId: String?): Boolean = modeOf(entryId) != null

    /** Sufijo del id de catálogo de cada modo (el `sourceId` de su entrada). */
    fun sourceIdOf(mode: RoutineMode): String = when (mode) {
        RoutineMode.GENERAL_STRENGTH_MUSCLE -> "strength-muscle"
        RoutineMode.GENERAL_HYBRID -> "hybrid"
        RoutineMode.GENERAL_FUNCTIONAL -> "functional"
        RoutineMode.DISCIPLINE_CALISTHENICS -> "calisthenics"
        RoutineMode.DISCIPLINE_ARMWRESTLING -> "armwrestling"
        RoutineMode.DISCIPLINE_STRONGMAN -> "strongman"
        RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE -> "weightlifting-base"
        RoutineMode.CUSTOM_POWERLIFTING -> "powerlifting"
        RoutineMode.CUSTOM_POWERBUILDING -> "powerbuilding"
        RoutineMode.CUSTOM_BODYBUILDING -> "bodybuilding"
    }
}

/**
 * Material de cada lugar a partir de la ÚNICA selección del paso de material (la unión de todos los lugares), para que
 * el generador arme cada sesión solo con el material de su día. El borrador no guarda qué implemento está en qué lugar,
 * así que la regla es conservadora (nunca supone en casa lo que trae el gimnasio):
 *
 * - cada lugar conserva lo que trae de serie ([EquipmentSymbols.seedFor]) y la persona dejó marcado;
 * - lo que la persona añadió por encima de las semillas de sus lugares («extras») va a casa si casa está entre los
 *   lugares y se ofrece allí; al gimnasio y a los espacios públicos solo llegan los extras que se llevan encima
 *   (bandas, anillas o TRX);
 * - con un solo lugar no hay reparto: manda la disponibilidad declarada (mapa vacío).
 *
 * Ejemplo: gimnasio + casa con lo habitual del gimnasio y unas anillas → el día de gimnasio usa todo eso y el de casa,
 * solo las anillas (y el peso corporal).
 */
object PlaceMaterial {

    /** Lo que se lleva encima de un lugar a otro. */
    val PORTABLE: Set<EquipmentSymbolId> = setOf(EquipmentSymbolId.BANDS, EquipmentSymbolId.RINGS)

    /**
     * El material de cada lugar a partir de la disponibilidad declarada [declared] (la unión de todos los lugares): el
     * reparto por símbolos de [byPlace] más lo que es de la persona y no de un lugar, que viaja a todos ellos (la
     * bicicleta al aire libre, [SetupApparatusPanel.OUTDOOR_BIKE_KEY]). Es la entrada que usan el pedido del generador, el
     * contraste de las sesiones con los lugares y las opciones del paso de cardio.
     */
    fun byPlace(declared: EquipmentAvailability, places: Set<TrainingPlace>): Map<TrainingPlace, EquipmentAvailability> =
        byPlace(EquipmentSymbols.selectedFrom(declared), places).mapValues { (_, own) -> SetupApparatusPanel.withBikeOf(declared, own) }

    fun byPlace(
        selected: Set<EquipmentSymbolId>,
        places: Set<TrainingPlace>,
    ): Map<TrainingPlace, EquipmentAvailability> {
        if (places.size < 2) return emptyMap()
        val chosen = selected - EquipmentSymbolId.BODYWEIGHT_ONLY
        val seeded = places.flatMapTo(mutableSetOf()) { place -> EquipmentSymbols.seedFor(setOf(place)) }
        val extras = chosen - seeded
        return TrainingPlace.entries.filter { it in places }.associateWith { place ->
            val offered = EquipmentSymbols.symbolsFor(setOf(place)).toSet()
            val own = chosen.filterTo(linkedSetOf()) { it in offered && it in EquipmentSymbols.seedFor(setOf(place)) }
            val added = extras.filter { symbol ->
                symbol in offered && (place == TrainingPlace.HOME || symbol in PORTABLE)
            }
            EquipmentSymbols.availabilityOf(own + added, setOf(place))
        }
    }
}
