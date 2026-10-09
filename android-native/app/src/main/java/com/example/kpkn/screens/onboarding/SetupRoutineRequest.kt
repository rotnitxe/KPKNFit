package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.LiftVariants
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

/**
 * La preferencia de cardio declarada, solo cuando el objetivo la pide y está completa. «Lo que haya» viaja sin tipo
 * (`type` nulo): el generador elige entre los aparatos de cada día.
 */
internal fun SetupWizardDraft.cardioPreference(): CardioPreference? {
    if (!requiresCardio) return null
    val minutes = cardioMinutes ?: return null
    val intensity = cardioBase.toCardioIntensity()
    return when {
        cardioType != null -> CardioPreference(cardioType, minutes, intensity)
        cardioNoPreference -> CardioPreference(null, minutes, intensity)
        else -> null
    }
}

/**
 * La intensidad del cardio que fija la base de hoy: «Me cuesta 10 min» → baja; «Aguanto 20–30 min» → media; «Corro 45 min» y
 * «Hago series rápidas» → alta. Sin base, media (como antes).
 */
internal fun CardioBase?.toCardioIntensity(): CardioIntensity = when (this) {
    CardioBase.LOW -> CardioIntensity.BAJA
    CardioBase.MODERATE, null -> CardioIntensity.MEDIA
    CardioBase.HIGH, CardioBase.INTERVALS -> CardioIntensity.ALTA
}

/**
 * Las variantes que usa el generador: solo las que se apartan de la de siempre, que el material aún permite y que pide el nivel
 * (intermedio o avanzado). Lo guardado no se borra: si ya no cabe, el generador lee la de siempre.
 */
private fun SetupWizardDraft.variantsForRequest(availability: EquipmentAvailability): Map<LiftMark, String> {
    val level = experience.toRoutineLevel()
    if (level != RoutineLevel.INTERMEDIATE && level != RoutineLevel.ADVANCED) return emptyMap()
    val lifts = LiftVariants.liftsFor(goalProfile)
    return liftVariants.filter { (lift, configId) ->
        lift in lifts && configId != LiftVariants.defaultFor(lift) &&
            LiftVariants.optionsFor(goalProfile, lift, availability).any { it.configId == configId }
    }
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
        availabilityByPlace = PlaceMaterial.byPlace(availability, places),
        priorityMuscles = priorityMuscleSymbols(),
        capabilities = capabilities,
        cardio = cardioPreference(),
        marks = liftMarks,
        // Punto de partida: el PR con repeticiones solo cuenta con repeticiones de 2 a 10; el 1 es un 1RM.
        markReps = liftMarkReps.filter { (lift, reps) -> reps > 1 && lift in liftMarks },
        liftVariants = variantsForRequest(availability),
        avoided = avoidedExercises,
        cardioBase = cardioBase,
        armStyle = armStyle,
        maxReps = maxReps,
        catalog = catalog,
        variantSeed = planVariantSeed,
        programId = programId,
    )
}

private const val DEFAULT_SESSION_MINUTES = 60

/**
 * El rango de minutos que acepta el generador (`RoutineGenerator`: 20–180), no el del reloj del asistente
 * ([com.example.kpkn.domain.onboarding.EntrenoStepValues.SESSION_MINUTES_MIN] = 30): el pedido solo se acota a lo que el
 * generador soporta, así que nunca falla aunque un borrador traiga menos de lo que el reloj ofrece.
 */
private const val MIN_SESSION_MINUTES = 20
private const val MAX_SESSION_MINUTES = 180
