package com.example.kpkn.screens.onboarding.entreno

import com.example.kpkn.data.programs.PlanEditorialTable
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel

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

/**
 * Las portadas del carrusel, una por programa: el carrusel exige ids únicos (el barrido ya los deduplica; esto solo
 * blinda la pantalla).
 */
internal fun cardModelsOf(reveals: List<SetupPlanReveal>): List<PlanCardModel> =
    reveals.distinctBy { it.planId }.map { it.toCardModel() }

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
