package com.example.kpkn.screens.onboarding.entreno

import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.screens.onboarding.SetupExperience
import com.example.kpkn.screens.onboarding.SetupPlanPhase
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanPhaseModel

/*
 * Entreno v2 · del revelado que publica el ViewModel ([SetupPlanReveal], sacado del programa ya preparado) a los modelos
 * de los componentes del programa (portada, carrusel y detalle). Traducción 1:1, sin texto propio.
 */

/** La portada y la tarjeta del carrusel de un programa. */
internal fun SetupPlanReveal.toCardModel(): PlanCardModel = PlanCardModel(
    id = planId,
    title = title,
    kicker = kicker,
    blurb = blurb,
    profile = profile,
    daysLabel = daysLabel,
    minutesLabel = minutesLabel,
    levelLabel = levelLabel,
    badge = badge,
    coverSeed = coverSeed,
    scopeLabel = generatedScopeLabelOf(this),
)

/** El detalle de un programa: ejercicios, estructura, semana tipo, razones, notas y atribución. */
internal fun SetupPlanReveal.toDetailModel(): PlanDetailModel = PlanDetailModel(
    card = toCardModel(),
    description = description,
    mainExercises = mainExercises,
    blocks = blocks.map { block -> PlanBlockModel(label = block.label, weeksLabel = block.weeksLabel, detail = block.detail) },
    week = week.map { day ->
        PlanDayModel(day = day.day, title = day.title, minutes = day.minutes, exerciseCount = day.exerciseCount, isMain = day.isMain)
    },
    reasons = reasons,
    notes = notes,
    attribution = attribution,
)

/** La fase de la franja del detalle (1:1, sin texto propio). */
internal fun SetupPlanPhase.toPhaseModel(): PlanPhaseModel = PlanPhaseModel(label = label, weeks = weeks, goal = goal)

/**
 * Las portadas del carrusel, una por programa: el carrusel exige ids únicos (el barrido ya los deduplica; esto solo
 * blinda la pantalla). [recommendedId] lleva la insignia «Para ti».
 */
internal fun cardModelsOf(reveals: List<SetupPlanReveal>, recommendedId: String? = null): List<PlanCardModel> =
    reveals.distinctBy { it.planId }.map { reveal ->
        val card = reveal.toCardModel()
        if (reveal.planId == recommendedId) card.copy(badge = RECOMMENDED_BADGE) else card
    }

/**
 * Huella ESTABLE de un resultado del barrido (la misma tras girar la pantalla o tras la muerte del proceso, a
 * diferencia de `hashCode`, que en los enums cambia de un proceso a otro): el paso la guarda al terminar el overlay
 * «preparando…» para no repetirlo con el mismo resultado. Cubre lo que se ve del programa: cambia con «Otra versión»
 * (otra semilla de portada y otros ejercicios) y con cualquier respuesta que cambie el programa.
 */
internal fun revealKeyOf(reveals: List<SetupPlanReveal>): String = reveals.joinToString(separator = "\u001E") { reveal ->
    listOf(
        reveal.planId,
        reveal.coverSeed.toString(),
        reveal.title,
        reveal.blurb,
        reveal.daysLabel,
        reveal.minutesLabel,
        reveal.levelLabel,
        reveal.mainExercises.joinToString(","),
        reveal.week.joinToString(",") { day -> "${day.day}:${day.title}:${day.minutes}:${day.exerciseCount}:${day.isMain}" },
    ).joinToString("\u001F")
}

/** Etiquetas de la acción del detalle que lleva a la otra versión de PHUL o PHAT. */
internal const val SIBLING_TO_ORIGINAL_LABEL = "Ver la versión original"
internal const val SIBLING_TO_ADAPTED_LABEL = "Ver la versión adaptada a tu material"

/** La familia de autor de [planId] («phul», «phat»), o null si el plan no tiene dos versiones. */
private fun familyOf(planId: String): String? = PlanEditorialTable.byId[planId]?.familyId

/** Si [planId] es la versión adaptada a tu material de su familia. */
private fun isAdaptedVersion(planId: String): Boolean = PlanEditorialTable.byId[planId]?.origin == PlanOrigin.ADAPTED

/**
 * Las fichas que muestra PLAN. Si las dos versiones de una familia de autor (PHUL, PHAT) son viables, se enseña una sola: la
 * adaptada si cambia algún ejercicio por tu material y, si no, la original. La otra se alcanza desde el detalle
 * ([siblingRevealOf]). Los demás programas pasan tal cual, en su orden.
 *
 * El revelado no trae la lista completa de ejercicios, así que el cambio se mide con los ejercicios principales y con el
 * número de ejercicios por día. Una sustitución que solo toque accesorios pasa desapercibida, pero la original también es
 * viable.
 */
internal fun planCardRevealsOf(reveals: List<SetupPlanReveal>): List<SetupPlanReveal> {
    val shownFamilies = mutableSetOf<String>()
    return reveals.mapNotNull { reveal ->
        val family = familyOf(reveal.planId) ?: return@mapNotNull reveal
        if (!shownFamilies.add(family)) return@mapNotNull null
        val members = reveals.filter { familyOf(it.planId) == family }
        val adapted = members.firstOrNull { isAdaptedVersion(it.planId) }
        val original = members.firstOrNull { !isAdaptedVersion(it.planId) }
        if (adapted != null && original != null && changesExercises(adapted, original)) adapted else original ?: adapted ?: reveal
    }
}

/** La otra versión de la familia de [planId] que también es viable (la que se alcanza desde su detalle). */
internal fun siblingRevealOf(reveals: List<SetupPlanReveal>, planId: String): SetupPlanReveal? {
    val family = familyOf(planId) ?: return null
    return reveals.firstOrNull { it.planId != planId && familyOf(it.planId) == family }
}

/** La etiqueta de la acción del detalle de [planId] que lleva a su hermana. */
internal fun siblingActionLabelOf(planId: String): String =
    if (isAdaptedVersion(planId)) SIBLING_TO_ORIGINAL_LABEL else SIBLING_TO_ADAPTED_LABEL

/**
 * El plan elegido en las fichas que se muestran. Si la elegida es la versión que no se enseña de su familia, queda marcada
 * la ficha visible de esa familia.
 */
internal fun shownSelectedIdOf(selectedId: String?, shown: List<SetupPlanReveal>): String? {
    if (selectedId == null || shown.any { it.planId == selectedId }) return selectedId
    val family = familyOf(selectedId) ?: return selectedId
    return shown.firstOrNull { familyOf(it.planId) == family }?.planId ?: selectedId
}

/** Cambia algún ejercicio por tu material: otros principales o distinto número de ejercicios por día. */
private fun changesExercises(adapted: SetupPlanReveal, original: SetupPlanReveal): Boolean =
    adapted.mainExercises != original.mainExercises ||
        adapted.week.map { it.exerciseCount } != original.week.map { it.exerciseCount }

// ─── Oferta de PLAN (D2c): orden, «Ver más» y recomendación ─────────────────────────────────────────────

/** La insignia de la recomendación en la portada. */
internal const val RECOMMENDED_BADGE = "Para ti"

/** Cuántos programas enseña PLAN de entrada; los demás se abren con «Ver más programas (N)». */
internal const val PLAN_OFFER_LIMIT = 6

/** Lo que enseña PLAN: las fichas visibles y cuántos programas quedan tras «Ver más programas». */
internal data class PlanOffer(val shown: List<SetupPlanReveal>, val hidden: Int)

/**
 * La forma que se recomienda según la experiencia: quien empieza o vuelve, la simple (una semana que se repite); con experiencia
 * intermedia, la de bloques; con experiencia avanzada, la periodizada. Sin experiencia, la simple.
 */
internal fun recommendedVariantOf(experience: SetupExperience?): ProgramVariant = when (experience) {
    SetupExperience.INTERMEDIATE -> ProgramVariant.BLOCKS
    SetupExperience.ADVANCED -> ProgramVariant.PERIODIZED
    SetupExperience.NEW, SetupExperience.RETURNING, null -> ProgramVariant.SIMPLE
}

/**
 * El programa recomendado entre [reveals]: la forma de [recommendedVariantOf] del perfil; si no está entre los viables, la
 * simple y, si tampoco, el primero a medida. Null si no hay ningún programa a medida.
 */
internal fun recommendedIdOf(
    reveals: List<SetupPlanReveal>,
    profile: TrainingGoalProfile?,
    experience: SetupExperience?,
): String? {
    val byForm = profile?.let { GeneratedPlans.entryIdFor(it, recommendedVariantOf(experience)) }
    val simple = profile?.let { GeneratedPlans.entryIdFor(it) }
    return reveals.firstOrNull { it.planId == byForm }?.planId
        ?: reveals.firstOrNull { it.planId == simple }?.planId
        ?: reveals.firstOrNull { it.generated }?.planId
}

/**
 * El orden de la oferta: primero los programas a medida del perfil (simple, en bloques y periodizada, en su orden) y, detrás, los de
 * autor y propios, tal como los deja el barrido. Una sola ficha por familia de autor (PHUL, PHAT).
 */
internal fun orderedOfferOf(reveals: List<SetupPlanReveal>): List<SetupPlanReveal> =
    planCardRevealsOf(reveals.sortedBy { if (it.generated) 0 else 1 })

/**
 * Lo que enseña PLAN: todo el orden si está abierto o si caben en [PLAN_OFFER_LIMIT]; si no, los primeros y cuántos quedan. Si lo
 * elegido quedaría escondido, se enseña todo: nunca se oculta la elección.
 */
internal fun planOfferOf(reveals: List<SetupPlanReveal>, expanded: Boolean, selectedId: String?): PlanOffer {
    val all = orderedOfferOf(reveals)
    if (expanded || all.size <= PLAN_OFFER_LIMIT) return PlanOffer(all, hidden = 0)
    val shown = all.take(PLAN_OFFER_LIMIT)
    val chosen = shownSelectedIdOf(selectedId, all)
    val chosenHidden = chosen != null && all.any { it.planId == chosen } && shown.none { it.planId == chosen }
    return if (chosenHidden) PlanOffer(all, hidden = 0) else PlanOffer(shown, hidden = all.size - shown.size)
}

internal data class SplitPlanOffer(val tailored: List<SetupPlanReveal>,val catalog: List<SetupPlanReveal>,val catalogHidden: Int)

/** Mantiene visible toda la oferta a medida y cualquier familia de catálogo ya elegida. */
internal fun splitPlanOfferOf(reveals: List<SetupPlanReveal>,catalogExpanded: Boolean,selectedId: String?): SplitPlanOffer {
    val all = orderedOfferOf(reveals)
    val tailored = all.filter { it.generated }
    val catalog = all.filterNot { it.generated }
    val chosen = shownSelectedIdOf(selectedId,all)
    return if (catalogExpanded || catalog.any { it.planId == chosen }) SplitPlanOffer(tailored,catalog,0)
        else SplitPlanOffer(tailored,emptyList(),catalog.size)
}

/** Usa fases reales cuando la fecha de competición cambió el horizonte; no anuncia siempre 6/12 semanas. */
internal fun generatedScopeLabelOf(reveal: SetupPlanReveal): String? {
    if (!reveal.generated) return null
    val variant = GeneratedPlans.variantOf(reveal.planId) ?: return null
    if (variant == ProgramVariant.SIMPLE) return com.example.kpkn.data.programs.PlanLabels.scopeLabel(
        com.example.kpkn.data.programs.CatalogDuration.REPEATING_WEEK,1,null)
    val weeks = reveal.phases.sumOf { it.weeks }.takeIf { it > 0 }
        ?: com.example.kpkn.data.programs.PersonalizedPlanCatalog.find(reveal.planId)?.durationWeeks?.takeIf { it > 0 } ?: return null
    return com.example.kpkn.data.programs.PlanLabels.scopeLabel(com.example.kpkn.data.programs.CatalogDuration.FINITE_CYCLE,
        weeks,reveal.blocks.size.takeIf { it > 1 })
}
