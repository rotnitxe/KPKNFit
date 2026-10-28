package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import com.example.kpkn.data.models.Program

/*
 * Portada de estudio de un programa: `Program.coverImage = "kpkn-studio://<ESTILO>/<PALETA>"`, elegida en «Editar programa».
 * El estilo y la paleta sustituyen a los de la identidad de siempre; los acentos y la semilla vienen de [coverIdentityFor].
 * Sin Compose: la decisión se prueba en la JVM.
 */

/** Esquema del `coverImage` que guarda la portada de estudio de un programa. */
internal const val PROGRAM_STUDIO_SCHEME = "kpkn-studio://"

/** Estilo y paleta elegidos en el estudio de portada de un programa. */
internal data class ProgramStudioChoice(val style: CoverStudioStyle, val palette: CoverPaletteKind)

/** El `coverImage` de [style] y [palette]: `kpkn-studio://<ESTILO>/<PALETA>`, con los nombres del enum. */
internal fun programStudioCoverValue(style: CoverStudioStyle, palette: CoverPaletteKind): String =
    PROGRAM_STUDIO_SCHEME + style.name + "/" + palette.name

/**
 * La elección que guarda [coverImage], o null si no es de estudio o si su estilo o su paleta no existen. Un valor inválido se
 * pinta como la portada de siempre.
 */
internal fun parseProgramStudioCover(coverImage: String?): ProgramStudioChoice? {
    val rest = coverImage?.takeIf { it.startsWith(PROGRAM_STUDIO_SCHEME) }?.removePrefix(PROGRAM_STUDIO_SCHEME) ?: return null
    val parts = rest.split('/')
    if (parts.size != 2) return null
    val style = CoverStudioStyle.entries.firstOrNull { it.name == parts[0] } ?: return null
    val palette = CoverPaletteKind.entries.firstOrNull { it.name == parts[1] } ?: return null
    return ProgramStudioChoice(style, palette)
}

/**
 * La identidad de portada de [program] para la cabecera, Home y las miniaturas. Con `kpkn-lines://` (elegida en «Editar programa»),
 * la portada de líneas con su estilo y su fondo. Sin portada (null, vacía o `gradient://`), la de líneas por defecto: Perfil sobre
 * Carbón, o Ondas si el programa es una sola semana. Con `kpkn-studio://`, el estilo y la paleta del estudio. Lo demás (fotos,
 * `kpkn-cover://`, valores inválidos) es la de siempre: [coverIdentityFor] con el id del programa y su plantilla. Las portadas de
 * líneas llevan además la carga de sus semanas y la semana en curso ([LineCoverSpec]).
 */
internal fun coverIdentityForProgram(program: Program): CoverIdentity {
    val base = coverIdentityFor(program.id, program.structureTemplateId)
    val coverImage = program.coverImage
    val weeks = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
    val setsPerWeek = weeks.map { week -> week.sessions.sumOf { session -> session.allExercises().sumOf { it.sets.size } } }
    val loads = normalizedWeekLoads(setsPerWeek)
    val currentWeek = program.runState?.weekId?.let { id -> weeks.indexOfFirst { it.id == id } } ?: -1
    val weekCount = maxOf(1, weeks.size)
    parseLineCover(coverImage)?.let { (style, background) ->
        return base.copy(lines = LineCoverSpec(style, background, base.seed, loads, currentWeek, weekCount))
    }
    if (coverImage.isNullOrBlank() || coverImage.startsWith("gradient://")) {
        val style = if (weekCount > 1) LineCoverStyle.PERFIL else LineCoverStyle.ONDAS
        return base.copy(lines = LineCoverSpec(style, LineCoverBackground.CARBON, base.seed, loads, currentWeek, weekCount))
    }
    val choice = parseProgramStudioCover(coverImage) ?: return base
    return base.restyled(choice.style, choice.palette)
}

/**
 * La misma identidad con [style] y [palette], conservando los acentos y la semilla. Si el estilo no cambia, se conserva su dato
 * (un «531» sigue siendo «531»); si cambia, el dato se recalcula con la semilla y el numeral pasa a ser semanas.
 */
internal fun CoverIdentity.restyled(style: CoverStudioStyle, palette: CoverPaletteKind): CoverIdentity =
    if (style == this.style) {
        copy(palette = palette)
    } else {
        copy(
            style = style,
            palette = palette,
            motif = motifFor(style, (seed and Int.MAX_VALUE) / 7),
            numeralIsWeeks = true,
        )
    }
