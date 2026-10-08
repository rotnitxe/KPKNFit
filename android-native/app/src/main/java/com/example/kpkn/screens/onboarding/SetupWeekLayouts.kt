package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.onboarding.SessionPlaceFit
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.split.AuthoredPlans
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.ProgramWeeks
import com.example.kpkn.domain.training.split.RedistributionOptions
import com.example.kpkn.domain.training.split.SplitChoice
import com.example.kpkn.domain.training.split.SplitRedistributor
import com.example.kpkn.domain.training.split.WeekAssignment

/*
 * Entreno v2 · la semana armada del paso WEEK_LAYOUT: un único punto ([applyLayout]) aplica al programa preparado lo que
 * la persona decidió sobre su semana, y lo usan por igual la vista previa y la activación.
 */

/**
 * Resultado de [applyLayout]: el programa con la semana armada, los avisos del reparto, por qué no se pudo adaptar y las
 * sesiones que caen en un día cuyo lugar no tiene su material ([placeConflicts], vacío con un solo lugar).
 */
internal data class LayoutOutcome(
    val program: Program,
    val notes: List<String> = emptyList(),
    val refusal: String? = null,
    val placeConflicts: List<SetupPlaceConflict> = emptyList(),
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
 * 3. El lugar de cada sesión ([reconcileSessionPlaces], solo con [placeFit] y dos o más lugares): una sesión que cae en un
 *    día de otro lugar pasa a él si su material cabe y, si no, conserva su lugar y queda avisada
 *    ([LayoutOutcome.placeConflicts]). Es la regla que cumple el invariante «el programa que se activa nunca trae una sesión
 *    que no se pueda hacer con el material declarado de su día sin que la persona lo sepa».
 */
internal fun applyLayout(
    draft: SetupWizardDraft,
    program: Program,
    resolver: ExerciseTraitResolver?,
    placeFit: SessionPlaceFit? = null,
): LayoutOutcome {
    val checksPlaces = placeFit != null && draft.trainingPlaces.size >= 2
    if (!draft.hasWeekLayout && !checksPlaces) return LayoutOutcome(program)
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
    val placed = if (checksPlaces && placeFit != null) reconcileSessionPlaces(draft, current, placeFit) else null
    return LayoutOutcome(placed?.program ?: current, notes.distinct(), refusal, placed?.conflicts.orEmpty())
}

/** El programa con el lugar de cada sesión puesto al día y las sesiones que no caben en el lugar de su día. */
internal data class PlaceReconciliation(val program: Program, val conflicts: List<SetupPlaceConflict>)

/**
 * Pone al día el lugar de cada sesión (`Session.placeId`) según el día en que cae, con el material que la persona
 * declaró para ese día ([placeForDay]); solo con dos o más lugares (con uno no hay a qué contrastar):
 *
 * - La sesión ya está en el lugar de su día: no cambia nada (un programa «a medida» se armó con el material de ese lugar).
 * - Cae en un día de otro lugar y SU MATERIAL CABE ahí ([SessionPlaceFit], el filtro único): pasa a ese lugar, sin ruido.
 * - Cae en un día de otro lugar y NO cabe: se mueve igual (la persona manda), conserva su lugar y se lista en
 *   [PlaceReconciliation.conflicts] para avisarlo. Volver a moverla o «Restablecer» deshace el aviso.
 *
 * Se aplica a todas las semanas del programa (cada sesión por su día); los avisos son los de la semana que dibuja el
 * tablero. Puro y determinista: parte del programa con la semana armada ya colocada, sin acumular nada de antes.
 */
internal fun reconcileSessionPlaces(draft: SetupWizardDraft, program: Program, fit: SessionPlaceFit): PlaceReconciliation {
    val places = TrainingPlace.entries.filter { it in draft.trainingPlaces }
    if (places.size < 2) return PlaceReconciliation(program, emptyList())
    val boardWeek = ProgramWeeks.effective(program).firstOrNull()?.position
    val conflicts = mutableListOf<SetupPlaceConflict>()
    val reconciled = ProgramWeeks.map(program) { ref ->
        if (ref.week.sessions.isEmpty()) return@map null
        val onBoard = ref.position == boardWeek
        ref.week.copy(
            sessions = ref.week.sessions.map { session ->
                val day = session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 }
                val dayPlace = day?.let { draft.placeForDay(it) }
                if (day == null || dayPlace == null || session.placeId == dayPlace.name) {
                    session
                } else if (fit.fits(session, dayPlace)) {
                    session.copy(placeId = dayPlace.name)
                } else {
                    if (onBoard) {
                        conflicts += SetupPlaceConflict(session.id, session.name, day, originPlaceOf(session, places, fit), dayPlace)
                    }
                    session
                }
            },
        )
    }
    return PlaceReconciliation(reconciled, conflicts.sortedBy { it.day })
}

/** De qué lugar es el material de [session]: el que dice su `placeId` o, si no lo dice, el primero de [places] que la cubre. */
private fun originPlaceOf(session: Session, places: List<TrainingPlace>, fit: SessionPlaceFit): TrainingPlace? =
    TrainingPlace.entries.firstOrNull { it.name == session.placeId } ?: places.firstOrNull { fit.fits(session, it) }

/** De qué lugar es un material, para una frase: «del gimnasio», «de casa», «de espacios públicos». */
private fun TrainingPlace.materialOf(): String = when (this) {
    TrainingPlace.GYM -> "del gimnasio"
    TrainingPlace.HOME -> "de casa"
    TrainingPlace.PUBLIC -> "de espacios públicos"
}

/** Dónde se entrena, para una frase: «en el gimnasio», «en casa», «en espacios públicos». */
internal fun TrainingPlace.trainingAt(): String = when (this) {
    TrainingPlace.GYM -> "en el gimnasio"
    TrainingPlace.HOME -> "en casa"
    TrainingPlace.PUBLIC -> "en espacios públicos"
}

/**
 * El aviso de una sesión que no cabe en el lugar de su día (COPY · Reparto): una frase que se explica sola. Si el programa
 * no dice de qué lugar es su material ni ninguno de los declarados lo cubre, la frase habla solo del lugar de ese día.
 */
internal fun placeConflictSentence(conflict: SetupPlaceConflict): String {
    val origin = conflict.sessionPlace
    return if (origin != null) {
        "Esta sesión usa material ${origin.materialOf()}; ese día entrenas ${conflict.dayPlace.trainingAt()}."
    } else {
        "Esta sesión usa material que no hay ${conflict.dayPlace.trainingAt()}, donde entrenas ese día."
    }
}

private val WEEKDAY_NAMES_ES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Nombre del día (1 = lunes … 7 = domingo) en minúscula, para una frase; vacío si no es un día. */
internal fun placeConflictDayName(day: Int): String = WEEKDAY_NAMES_ES.getOrElse(day - 1) { "" }

/**
 * El mismo aviso con el nombre de la sesión y su día por delante, para una línea suelta (la revisión final):
 * «Pierna A (miércoles): esta sesión usa material del gimnasio; ese día entrenas en casa.»
 */
internal fun placeConflictLine(conflict: SetupPlaceConflict): String =
    "${conflict.title} (${placeConflictDayName(conflict.day)}): ${placeConflictSentence(conflict).replaceFirstChar { it.lowercaseChar() }}"

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
        placeConflicts = outcome.placeConflicts,
    )
}
