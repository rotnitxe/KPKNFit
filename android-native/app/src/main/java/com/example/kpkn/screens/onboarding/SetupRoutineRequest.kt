package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.PlaceMaterial
import com.example.kpkn.domain.training.CardioPreference
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import com.example.kpkn.domain.training.generator.RoutineRequest

/*
 * Entreno v2 · el pedido del generador de rutinas a partir del borrador del alta. Es la ÚNICA traducción: el barrido
 * de programas, la vista previa y la activación generan con el mismo pedido, así lo que se revela es lo que se activa.
 */

/** Nivel del generador desde la experiencia declarada (sin experiencia: quien empieza). */
internal fun SetupExperience?.toRoutineLevel(): RoutineLevel = when (this) {
    SetupExperience.RETURNING -> RoutineLevel.RETURNING
    SetupExperience.INTERMEDIATE -> RoutineLevel.INTERMEDIATE
    SetupExperience.ADVANCED -> RoutineLevel.ADVANCED
    SetupExperience.NEW, null -> RoutineLevel.NOVICE
}

/** Nivel del catálogo de planes para el que se arma un programa «a medida» (la portada lo enseña). */
internal fun SetupExperience?.toPlanLevel(): CatalogLevel = when (this) {
    SetupExperience.ADVANCED -> CatalogLevel.ADVANCED
    SetupExperience.INTERMEDIATE -> CatalogLevel.INTERMEDIATE
    else -> CatalogLevel.BEGINNER
}

/**
 * Primer día de la semana del alta: el que eligió la persona o, si no lo tocó, su día con más energía (que es también
 * el primero de la semana mientras no se mueva); sin ninguno, el primer día de entreno o el lunes.
 */
internal fun SetupWizardDraft.effectiveWeekStart(): Int =
    weekStartDay?.takeIf { it in 1..7 } ?: freshestDay?.takeIf { it in 1..7 } ?: selectedWeekdays.minOrNull() ?: 1

/** Los días de entreno en el orden de la semana que empieza en [effectiveWeekStart]. */
internal fun SetupWizardDraft.orderedWeekdays(): List<Int> {
    val start = effectiveWeekStart()
    return selectedWeekdays.filter { it in 1..7 }.sortedBy { Math.floorMod(it - start, 7) }
}

/** Los músculos a mejorar, en el orden del contrato de símbolos (la bolsa de orden es su lectura canónica). */
internal fun SetupWizardDraft.priorityMuscleSymbols(): List<MuscleSymbol> =
    MuscleSymbol.entries.filter { it in MuscleSymbols.symbolsOf(trainingOptions.orderPriorities) }

/** La preferencia de cardio declarada, solo cuando el objetivo la pide y está completa. */
internal fun SetupWizardDraft.cardioPreference(): CardioPreference? {
    if (!requiresCardio) return null
    val type = cardioType ?: return null
    val minutes = cardioMinutes ?: return null
    return CardioPreference(type, minutes)
}

/**
 * El pedido del generador para el modo [mode]: días ordenados desde el inicio de semana, día con más energía, minutos,
 * nivel y calibraciones, material (la unión y, con varios lugares, el de cada lugar y el lugar de cada día), músculos a
 * mejorar, capacidades, cardio, marcas y la semilla de «otra versión». [programId] fija los ids (el del alta: así el
 * programa previsualizado y el activado son el mismo).
 */
internal fun SetupWizardDraft.routineRequest(
    mode: RoutineMode,
    catalog: ExerciseCatalogV2,
    programId: String? = commitId.takeIf { it.isNotBlank() },
): RoutineRequest {
    val availability = trainingOptions.availability ?: EquipmentAvailability()
    val places = trainingPlaces
    return RoutineRequest(
        mode = mode,
        weekdays = orderedWeekdays(),
        weekStartDay = effectiveWeekStart(),
        freshestDay = freshestDay?.takeIf { it in 1..7 },
        targetMinutes = (minutesPerSession ?: DEFAULT_SESSION_MINUTES).coerceIn(MIN_SESSION_MINUTES, MAX_SESSION_MINUTES),
        level = experience.toRoutineLevel(),
        technique = volumeAnswers.technique,
        consistency = volumeAnswers.consistency,
        strength = volumeAnswers.strength,
        mobility = volumeAnswers.mobility,
        availability = availability,
        places = places,
        dayPlaces = if (places.size >= 2) effectiveDayPlaces() else emptyMap(),
        availabilityByPlace = PlaceMaterial.byPlace(EquipmentSymbols.selectedFrom(availability), places),
        priorityMuscles = priorityMuscleSymbols(),
        capabilities = capabilities,
        cardio = cardioPreference(),
        marks = liftMarks,
        catalog = catalog,
        variantSeed = planVariantSeed,
        programId = programId,
    )
}

private const val DEFAULT_SESSION_MINUTES = 60
private const val MIN_SESSION_MINUTES = 20
private const val MAX_SESSION_MINUTES = 180
