package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.CatalogCompositionTestSupport

/** Perfiles de material de las pruebas del generador (los seis del brief). */
internal data class MaterialProfile(
    val id: String,
    val places: Set<TrainingPlace>,
    val availability: EquipmentAvailability,
    val byPlace: Map<TrainingPlace, EquipmentAvailability> = emptyMap(),
    /** Si no es null, reparte los días entre estos lugares en orden (día 0 → primer lugar…). */
    val alternatePlaces: List<TrainingPlace> = emptyList(),
)

internal object RoutineTestSupport {

    val catalog: ExerciseCatalogV2 get() = CatalogCompositionTestSupport.catalog

    private fun availability(symbols: Set<EquipmentSymbolId>, places: Set<TrainingPlace>) =
        EquipmentSymbols.availabilityOf(symbols, places)

    val bodyOnly = MaterialProfile(
        id = "solo cuerpo",
        places = setOf(TrainingPlace.HOME),
        availability = availability(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), setOf(TrainingPlace.HOME)),
    )

    val park = MaterialProfile(
        id = "parque",
        places = setOf(TrainingPlace.PUBLIC),
        availability = availability(
            setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH),
            setOf(TrainingPlace.PUBLIC),
        ),
    )

    val homeDumbbellsBand = MaterialProfile(
        id = "casa mancuernas+banda",
        places = setOf(TrainingPlace.HOME),
        availability = availability(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS), setOf(TrainingPlace.HOME)),
    )

    val homeBarbell = MaterialProfile(
        id = "casa barra+rack+banco",
        places = setOf(TrainingPlace.HOME),
        availability = availability(
            setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH),
            setOf(TrainingPlace.HOME),
        ),
    )

    val gym = MaterialProfile(
        id = "gimnasio completo",
        places = setOf(TrainingPlace.GYM),
        availability = availability(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM)),
    )

    val gymAndHome: MaterialProfile = run {
        val gymAvailability = availability(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM))
        val homeAvailability = availability(setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS), setOf(TrainingPlace.HOME))
        MaterialProfile(
            id = "gimnasio+casa por día",
            places = setOf(TrainingPlace.GYM, TrainingPlace.HOME),
            availability = availability(
                EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) + setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS),
                setOf(TrainingPlace.GYM, TrainingPlace.HOME),
            ),
            byPlace = mapOf(TrainingPlace.GYM to gymAvailability, TrainingPlace.HOME to homeAvailability),
            alternatePlaces = listOf(TrainingPlace.GYM, TrainingPlace.HOME),
        )
    }

    val profiles: List<MaterialProfile> = listOf(bodyOnly, park, homeDumbbellsBand, homeBarbell, gym, gymAndHome)

    val generalModes = listOf(RoutineMode.GENERAL_STRENGTH_MUSCLE, RoutineMode.GENERAL_HYBRID, RoutineMode.GENERAL_FUNCTIONAL)

    val levels = RoutineLevel.entries.toList()

    val minutes = listOf(20, 30, 45, 60, 90, 120, 150, 180)

    /** Días de entreno típicos por cantidad de días (lunes = 1). */
    fun weekdays(count: Int): List<Int> = when (count) {
        1 -> listOf(3)
        2 -> listOf(1, 4)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        5 -> listOf(1, 2, 4, 5, 6)
        6 -> listOf(1, 2, 3, 4, 5, 6)
        else -> listOf(1, 2, 3, 4, 5, 6, 7)
    }

    fun request(
        profile: MaterialProfile,
        mode: RoutineMode,
        level: RoutineLevel,
        dayCount: Int,
        minutes: Int,
        seed: Int = 0,
        freshest: Int? = null,
        weekdays: List<Int> = weekdays(dayCount),
        priorities: List<MuscleSymbol> = emptyList(),
        capabilities: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
        cardio: CardioPreference? = null,
        marks: Map<LiftMark, Double> = emptyMap(),
        weekStart: Int = 1,
    ): RoutineRequest = RoutineRequest(
        mode = mode,
        weekdays = weekdays,
        weekStartDay = weekStart,
        freshestDay = freshest,
        targetMinutes = minutes,
        level = level,
        availability = profile.availability,
        places = profile.places,
        dayPlaces = if (profile.alternatePlaces.isEmpty()) emptyMap() else weekdays.withIndex().associate { (index, day) ->
            day to profile.alternatePlaces[index % profile.alternatePlaces.size]
        },
        availabilityByPlace = profile.byPlace,
        priorityMuscles = priorities,
        capabilities = capabilities,
        cardio = cardio,
        marks = marks,
        catalog = catalog,
        variantSeed = seed,
    )

    /** Ventana de minutos por sesión: [85 %, 110 %] del objetivo y nunca menos de 20 (regla del brief, calculada aparte). */
    fun window(target: Int): IntRange {
        val lo = maxOf(20, Math.ceil(target * 0.85).toInt())
        val hi = Math.floor(target * 1.10).toInt()
        return lo..maxOf(lo, hi)
    }

    /** Día de la sesión principal que debe resultar de la petición (independiente de la implementación). */
    fun expectedMainDay(days: List<Int>, freshest: Int?): Int? {
        if (freshest == null) return null
        if (freshest in days) return freshest
        return days.minByOrNull { (it - freshest + 7) % 7 }
    }
}
