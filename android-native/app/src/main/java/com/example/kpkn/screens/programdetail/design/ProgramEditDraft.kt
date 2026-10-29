package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.lineCoverValue
import com.example.kpkn.screens.programdetail.components.HeroCoverKind
import com.example.kpkn.screens.programdetail.components.heroCoverKind

/*
 * Borrador de «Editar programa» (m-edit): lo que el usuario ha tocado hasta pulsar «Guardar». Sin Compose: la regla de qué se puede
 * guardar y cuándo se prueba en la JVM.
 */

/**
 * @property cover el `coverImage` que se guardará: la foto propia, una portada de líneas (`kpkn-lines://`) o la de siempre.
 * @property lineStyle el estilo de líneas elegido en la rejilla.
 * @property background el fondo elegido en los círculos.
 * @property mode la modalidad elegida: Hipertrofia, Powerbuilding o Powerlifting.
 */
internal data class ProgramEditDraft(
    val name: String,
    val description: String,
    val cover: String?,
    val lineStyle: LineCoverStyle,
    val background: LineCoverBackground,
    val mode: ProgramMode = ProgramMode.HYPERTROPHY,
) {
    val trimmedName: String get() = name.trim()

    val trimmedDescription: String? get() = description.trim().takeIf { it.isNotEmpty() }

    /** Hay una foto propia (URL o `content://`). */
    val hasPhoto: Boolean get() = heroCoverKind(cover, hasBundledCover = false) == HeroCoverKind.REMOTE

    /** Elige otro estilo: la portada pasa a ser de líneas con ese estilo y el fondo elegido (también si había foto). */
    fun withStyle(next: LineCoverStyle): ProgramEditDraft = copy(lineStyle = next, cover = lineCoverValue(next, background))

    /** Elige otro fondo, con la misma regla que [withStyle]. */
    fun withBackground(next: LineCoverBackground): ProgramEditDraft = copy(background = next, cover = lineCoverValue(lineStyle, next))

    /** «Usar una foto»: la foto elegida ([uri]) pasa a ser la portada. */
    fun withPhoto(uri: String): ProgramEditDraft = copy(cover = uri)

    /** Hay algo que guardar y el nombre no está vacío. */
    fun canSave(program: Program): Boolean = trimmedName.isNotEmpty() && isChangedFrom(program)

    /** Nombre, descripción, modalidad o portada distintos de los del programa. */
    fun isChangedFrom(program: Program): Boolean =
        trimmedName != program.name ||
            trimmedDescription != program.description?.trim()?.takeIf { it.isNotEmpty() } ||
            mode != program.mode ||
            cover != program.coverImage
}

/** Los estilos que se eligen en la rejilla (los ocho de la pestaña Portadas; «Sesión» es solo de las historias). */
internal val editableLineStyles: List<LineCoverStyle> = LineCoverStyle.entries.filter { it != LineCoverStyle.SESION }

/**
 * El borrador inicial de [program]: su nombre, su descripción, su modalidad, su portada y el estilo y el fondo de líneas que pinta hoy (los
 * de su portada de líneas o los de la portada por defecto; con otra portada, Perfil sobre Carbón).
 */
internal fun programEditDraftOf(program: Program): ProgramEditDraft {
    val lines = coverIdentityForProgram(program).lines
    return ProgramEditDraft(
        name = program.name,
        description = program.description.orEmpty(),
        cover = program.coverImage,
        lineStyle = lines?.style?.takeIf { it != LineCoverStyle.SESION } ?: LineCoverStyle.PERFIL,
        background = lines?.background ?: LineCoverBackground.CARBON,
        mode = program.mode,
    )
}

/** Nombre en español de cada modalidad del programa. */
internal fun programModeLabel(mode: ProgramMode): String = when (mode) {
    ProgramMode.HYPERTROPHY -> "Hipertrofia"
    ProgramMode.POWERBUILDING -> "Powerbuilding"
    ProgramMode.POWERLIFTING -> "Powerlifting"
}

/** Las modalidades que se eligen en «Editar programa», en el orden en que se muestran. */
internal val editableProgramModes: List<ProgramMode> = listOf(
    ProgramMode.HYPERTROPHY,
    ProgramMode.POWERBUILDING,
    ProgramMode.POWERLIFTING,
)
