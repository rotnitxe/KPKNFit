package com.example.kpkn.screens.onboarding.entreno

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
