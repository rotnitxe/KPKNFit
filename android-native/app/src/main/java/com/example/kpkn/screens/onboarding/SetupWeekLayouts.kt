package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.split.AuthoredPlans
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.RedistributionOptions
import com.example.kpkn.domain.training.split.SplitChoice
import com.example.kpkn.domain.training.split.SplitRedistributor
import com.example.kpkn.domain.training.split.WeekAssignment

/*
 * Entreno v2 · la semana armada del paso WEEK_LAYOUT: un único punto ([applyLayout]) aplica al programa preparado lo que
 * la persona decidió sobre su semana, y lo usan por igual la vista previa y la activación.
 */

/** Resultado de [applyLayout]: el programa con la semana armada, los avisos del reparto y por qué no se pudo adaptar. */
internal data class LayoutOutcome(
    val program: Program,
    val notes: List<String> = emptyList(),
    val refusal: String? = null,
)

/** ¿El borrador trae alguna decisión sobre la semana (sesiones movidas o reparto adaptado)? */
internal val SetupWizardDraft.hasWeekLayout: Boolean
    get() = weekLayoutOverrides.isNotEmpty() || adaptedSplitId != null

/**
 * El ÚNICO punto que aplica la semana armada del borrador al programa [program] ya preparado (el de la evaluación del
 * plan elegido). Puro y determinista; la vista previa lo llama al final de la materialización y la activación guarda
 * exactamente ese programa.
 *
 * 1. `adaptedSplitId`: adapta el programa a ese reparto repartiendo los ejercicios ([SplitRedistributor.redistribute]) en
 *    los días de entreno en el orden de la semana, en todas las semanas que tienen la misma estructura. El permiso para
 *    los planes de autor (`allowAuthoredRecipes`) va siempre: el asistente solo escribe `adaptedSplitId` en un plan de
 *    autor después de que la persona confirme el aviso de estructura. Si el reparto ya no encaja, el programa queda como
 *    estaba y [LayoutOutcome.refusal] dice por qué.
 * 2. `weekLayoutOverrides` (sesión → día): coloca las sesiones donde la persona las dejó ([WeekAssignment.applyAssignment],
 *    sin tocar ningún ejercicio), en todas las semanas paralelas.
 */
internal fun applyLayout(draft: SetupWizardDraft, program: Program, resolver: ExerciseTraitResolver?): LayoutOutcome {
    if (!draft.hasWeekLayout) return LayoutOutcome(program)
    var current = program
    val notes = mutableListOf<String>()
    var refusal: String? = null
    draft.adaptedSplitId?.let { splitId ->
        val split = SPLIT_TEMPLATES.firstOrNull { it.id == splitId }
        val places = sessionPlacesOf(program)
        when {
            split == null -> refusal = SPLIT_UNAVAILABLE
            // Repartir de nuevo mezclaría ejercicios armados con el material de lugares distintos.
            places.size > 1 -> refusal = SPLIT_MIXED_PLACES
            resolver == null -> refusal = SPLIT_UNAVAILABLE
            else -> {
                val result = SplitRedistributor.redistribute(
                    program = current,
                    split = split,
                    weekdays = draft.orderedWeekdays(),
                    resolver = resolver,
                    allowAuthoredRecipes = true,
                    options = RedistributionOptions(
                        propagateToOtherWeeks = true,
                        targetMinutes = draft.minutesPerSession,
                    ),
                )
                if (result.compatible) {
                    // Las sesiones nuevas del reparto salen del mismo lugar que todas las de antes.
                    current = places.singleOrNull()?.let { place -> result.program.withSessionPlace(place) } ?: result.program
                    notes += result.notes
                } else {
                    refusal = result.reason ?: SPLIT_UNAVAILABLE
                }
            }
        }
    }
    if (draft.weekLayoutOverrides.isNotEmpty()) {
        current = WeekAssignment.applyAssignment(current, assignmentOf(draft.weekLayoutOverrides))
    }
    return LayoutOutcome(current, notes.distinct(), refusal)
}

/** Texto cuando el reparto pedido ya no se puede aplicar (no está en el catálogo o falta el catálogo de ejercicios). */
internal const val SPLIT_UNAVAILABLE = "Este reparto no está disponible."

/**
 * Con sesiones de lugares distintos cada una se armó con el material de su día: repartir de nuevo sus ejercicios los
 * mezclaría, así que el programa no se adapta a otro reparto (sí se pueden mover sus sesiones).
 */
internal const val SPLIT_MIXED_PLACES =
    "Cada sesión usa el material de su lugar: mueve las sesiones en lugar de cambiar el reparto."

/** Los lugares (`Session.placeId`) de las sesiones del programa, sin repetir. */
internal fun sessionPlacesOf(program: Program): Set<String> =
    program.macrocycles.asSequence()
        .flatMap { it.blocks.asSequence() }
        .flatMap { it.mesocycles.asSequence() }
        .flatMap { it.weeks.asSequence() }
        .flatMap { it.sessions.asSequence() }
        .mapNotNull { it.placeId }
        .toSet()

/** El programa con todas sus sesiones en el lugar [place] (las del reparto nuevo no lo traen). */
internal fun Program.withSessionPlace(place: String): Program = copy(
    macrocycles = macrocycles.map { macro ->
        macro.copy(
            blocks = macro.blocks.map { block ->
                block.copy(
                    mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week -> week.copy(sessions = week.sessions.map { it.copy(placeId = place) }) })
                    },
                )
            },
        )
    },
)

/** `weekLayoutOverrides` (sesión → día) como asignación del tablero (día → sesión). */
internal fun assignmentOf(overrides: Map<String, Int>): Map<Int, String> =
    overrides.entries.filter { it.value in 1..7 }.associate { (sessionId, day) -> day to sessionId }.toSortedMap()

/** Una asignación del tablero (día → sesión) como `weekLayoutOverrides` (sesión → día). */
internal fun overridesOf(assignment: Map<Int, String>): Map<String, Int> =
    assignment.entries.filter { it.key in 1..7 }.associate { (day, sessionId) -> sessionId to day }

/** Un reparto del catálogo para el selector del tablero. */
internal fun SplitChoice.toSplitOption(): SetupSplitOption = SetupSplitOption(id = id, name = name, summary = summary, dayTitles = dayTitles)

/**
 * La semana que dibuja el tablero para el programa ya armado [program] ([base] = el mismo antes de la semana armada,
 * para saber si trae reparto de autor). La sesión principal solo se marca cuando la semana tiene UNA (el generador marca
 * la más exigente; otros motores marcan la principal de cada día y entonces no significa nada aquí).
 */
internal fun weekLayoutOf(
    draft: SetupWizardDraft,
    program: Program,
    base: Program,
    resolver: ExerciseTraitResolver,
    outcome: LayoutOutcome,
): SetupWeekLayout {
    val infos = WeekAssignment.sessionsOf(program, resolver)
    val singleMain = infos.count { it.isMain } == 1
    val placeOf = SetupPlanReveals.firstTrainingWeek(program)?.sessions.orEmpty()
        .associate { session -> session.id to session.placeId?.let { id -> TrainingPlace.entries.firstOrNull { it.name == id } } }
    // Con sesiones de varios lugares no se ofrece cambiar el reparto ([SPLIT_MIXED_PLACES]); mover sesiones, sí.
    val mixedPlaces = sessionPlacesOf(base).size > 1
    val options = if (mixedPlaces) {
        emptyList()
    } else {
        SplitRedistributor.optionsFor(draft.selectedWeekdays.size, draft.goalProfile).map { it.toSplitOption() }
    }
    return SetupWeekLayout(
        weekStartDay = draft.effectiveWeekStart(),
        sessions = infos.map { info ->
            SetupLayoutSession(
                id = info.id,
                title = info.title,
                focus = info.focus,
                minutes = info.minutes,
                exerciseCount = info.exerciseCount,
                isMain = singleMain && info.isMain,
                place = placeOf[info.id],
            )
        },
        assignment = WeekAssignment.of(program),
        splitOptions = options,
        selectedSplitId = draft.adaptedSplitId ?: program.selectedSplitId?.takeIf { id -> options.any { it.id == id } },
        canReset = draft.hasWeekLayout,
        authoredStructure = AuthoredPlans.hasFixedRecipe(base),
        notes = outcome.notes + listOfNotNull(SPLIT_MIXED_PLACES.takeIf { mixedPlaces }),
        refusal = outcome.refusal,
    )
}
