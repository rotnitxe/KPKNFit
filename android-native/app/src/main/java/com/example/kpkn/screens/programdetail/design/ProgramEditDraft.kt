package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.programStudioCoverValue
import com.example.kpkn.screens.programdetail.components.HeroCoverKind
import com.example.kpkn.screens.programdetail.components.heroCoverKind

/*
 * Borrador de «Editar programa» (P1): lo que el usuario ha tocado hasta pulsar «Guardar». Sin Compose: la regla de qué se puede
 * guardar y cuándo se prueba en la JVM.
 */

/**
 * @property cover el `coverImage` que se guardará: la foto propia, una portada de estudio o la de siempre.
 * @property style el estilo elegido en el estudio; aunque haya foto, es lo que aplica «Usar portada KPKN».
 * @property palette la paleta elegida en el estudio, con la misma regla.
 */
internal data class ProgramEditDraft(
    val name: String,
    val description: String,
    val cover: String?,
    val style: CoverStudioStyle,
    val palette: CoverPaletteKind,
) {
    val trimmedName: String get() = name.trim()

    val trimmedDescription: String? get() = description.trim().takeIf { it.isNotEmpty() }

    /** Hay una foto propia (URL o `content://`) y manda sobre el estudio hasta que el usuario la sustituye. */
    val hasPhoto: Boolean get() = heroCoverKind(cover, hasBundledCover = false) == HeroCoverKind.REMOTE

    /** La portada de estudio con el estilo y la paleta elegidos. */
    val studioCover: String get() = programStudioCoverValue(style, palette)

    /** Elige otro estilo. Con foto propia, la foto sigue mandando; sin ella, la portada pasa a ser de estudio. */
    fun withStyle(next: CoverStudioStyle): ProgramEditDraft =
        copy(style = next, cover = if (hasPhoto) cover else programStudioCoverValue(next, palette))

    /** Elige otra paleta, con la misma regla que [withStyle]. */
    fun withPalette(next: CoverPaletteKind): ProgramEditDraft =
        copy(palette = next, cover = if (hasPhoto) cover else programStudioCoverValue(style, next))

    /** «Usar portada KPKN»: sustituye la foto por la portada de estudio elegida. */
    fun usingStudio(): ProgramEditDraft = copy(cover = studioCover)

    /** Hay algo que guardar y el nombre no está vacío. */
    fun canSave(program: Program): Boolean = trimmedName.isNotEmpty() && isChangedFrom(program)

    /** Nombre, descripción o portada distintos de los del programa. */
    fun isChangedFrom(program: Program): Boolean =
        trimmedName != program.name ||
            trimmedDescription != program.description?.trim()?.takeIf { it.isNotEmpty() } ||
            cover != program.coverImage
}

/** El borrador inicial de [program]: su nombre, su descripción, su portada y el estilo y la paleta que pinta hoy. */
internal fun programEditDraftOf(program: Program): ProgramEditDraft {
    val identity = coverIdentityForProgram(program)
    return ProgramEditDraft(
        name = program.name,
        description = program.description.orEmpty(),
        cover = program.coverImage,
        style = identity.style,
        palette = identity.palette,
    )
}

/** Nombre en español de cada estilo del estudio. */
internal fun coverStyleLabel(style: CoverStudioStyle): String = when (style) {
    CoverStudioStyle.NUMERAL -> "Numeral"
    CoverStudioStyle.TOPO -> "Curvas"
    CoverStudioStyle.PLATES -> "Discos"
    CoverStudioStyle.GRID -> "Semanas"
    CoverStudioStyle.WAVES -> "Ondas"
    CoverStudioStyle.HALFTONE -> "Trama"
    CoverStudioStyle.TRACK -> "Pista"
    CoverStudioStyle.TRAIL -> "Ruta"
    CoverStudioStyle.ENGRAVING -> "Grabado"
    CoverStudioStyle.BAUHAUS -> "Formas"
}

/** Nombre en español de cada paleta del estudio. */
internal fun coverPaletteLabel(palette: CoverPaletteKind): String = when (palette) {
    CoverPaletteKind.NOCHE -> "Noche"
    CoverPaletteKind.TINTA -> "Tinta"
    CoverPaletteKind.PAPEL -> "Papel"
}
