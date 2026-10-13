package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/*
 * Identidad de cada portada del motor «CoverStudio» (K3a): el estilo del grafismo, el fondo, dos acentos de `SymbolPalette` y el
 * dato que el estilo necesita. La tabla da una fila a cada programa «a medida» del generador (30). El resto (catálogo, programas
 * propios o importados) sigue su camino: el arte de hoy en PLAN, y [coverIdentityFor] en Home.
 */

/** Los estilos del motor. K3a: cinco; K3b añade otros cinco. */
internal enum class CoverStudioStyle { NUMERAL, TOPO, PLATES, GRID, WAVES }

/** El fondo: negro con un velo del acento, acento a tinta plena o papel cálido. */
internal enum class CoverPaletteKind { NOCHE, TINTA, PAPEL }

/**
 * La identidad de una portada. [primary] es el acento del perfil (`goalAccent`) y [secondary] otro acento distinto. [motif] es el
 * dato del estilo: el numeral (NUMERAL), las semanas (GRID), los discos por lado (PLATES), las curvas (TOPO) o las ondas (WAVES).
 * [days] son los días por semana que enciende GRID. [seed] es estable por id.
 */
internal data class CoverIdentity(
    val style: CoverStudioStyle,
    val palette: CoverPaletteKind,
    val primary: Color,
    val secondary: Color,
    val motif: Int,
    val seed: Int,
    val days: Int = 4,
)

/** Cómo pinta PlanCover su fondo: la foto propia manda; con identidad, el motor; si no, el arte procedural de siempre. */
internal enum class CoverArtMode { PHOTO, STUDIO, PROCEDURAL }

/** El negro de la marca: fondo de NOCHE, velo de la parte baja y tinta de TINTA y PAPEL. */
internal val COVER_INK = Color(0xFF0A0A0B)

/** El papel cálido de PAPEL (la tinta de la marca invertida). */
internal val COVER_PAPER = Color(0xFFF2EEE6)

private val MUSCULO = SymbolPalette.musculo
private val ENERGIA = SymbolPalette.energia
private val OK = SymbolPalette.ok
private val COLUMNA = SymbolPalette.columna
private val MENTE = SymbolPalette.mente

/** Los acentos que puede usar el motor: los cinco de `SymbolPalette`. */
private val ACCENTS = listOf(MUSCULO, ENERGIA, OK, COLUMNA, MENTE)

/** Los numerales posibles: una semana («1»), seis («6») o doce («12»). */
private val NUMERALS = listOf(1, 6, 12)

private fun row(
    id: String,
    style: CoverStudioStyle,
    palette: CoverPaletteKind,
    primary: Color,
    secondary: Color,
    motif: Int,
    days: Int = 4,
): Pair<String, CoverIdentity> =
    id to CoverIdentity(style, palette, primary, secondary, motif, seed = stableHash(id), days = days)

/**
 * Los 30 programas «a medida» (`generated:<perfil>`, `:blocks` y `:periodized`). En cada perfil, sus tres variantes van con tres
 * estilos y tres fondos distintos, y ninguna pareja (estilo, fondo, primario) se repite. El primario es el acento del perfil.
 */
private val ASSIGNED: Map<String, CoverIdentity> = mapOf(
    // Fuerza y masa muscular (primario ok)
    row("generated:strength-muscle", CoverStudioStyle.PLATES, CoverPaletteKind.NOCHE, OK, MUSCULO, motif = 4),
    row("generated:strength-muscle:blocks", CoverStudioStyle.GRID, CoverPaletteKind.TINTA, OK, MENTE, motif = 6),
    row("generated:strength-muscle:periodized", CoverStudioStyle.NUMERAL, CoverPaletteKind.PAPEL, OK, MUSCULO, motif = 12),
    // Fuerza y cardio (ok)
    row("generated:hybrid", CoverStudioStyle.WAVES, CoverPaletteKind.NOCHE, OK, ENERGIA, motif = 9),
    row("generated:hybrid:blocks", CoverStudioStyle.GRID, CoverPaletteKind.PAPEL, OK, COLUMNA, motif = 6, days = 3),
    row("generated:hybrid:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.TINTA, OK, MENTE, motif = 18),
    // Funcional y saludable (ok)
    row("generated:functional", CoverStudioStyle.GRID, CoverPaletteKind.NOCHE, OK, COLUMNA, motif = 1),
    row("generated:functional:blocks", CoverStudioStyle.WAVES, CoverPaletteKind.TINTA, OK, MUSCULO, motif = 10),
    row("generated:functional:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.PAPEL, OK, MUSCULO, motif = 14),
    // Calistenia (ok)
    row("generated:calisthenics", CoverStudioStyle.NUMERAL, CoverPaletteKind.NOCHE, OK, MENTE, motif = 1),
    row("generated:calisthenics:blocks", CoverStudioStyle.PLATES, CoverPaletteKind.TINTA, OK, COLUMNA, motif = 5),
    row("generated:calisthenics:periodized", CoverStudioStyle.WAVES, CoverPaletteKind.PAPEL, OK, MUSCULO, motif = 12),
    // Powerbuilding (energía)
    row("generated:powerbuilding", CoverStudioStyle.PLATES, CoverPaletteKind.TINTA, ENERGIA, COLUMNA, motif = 4),
    row("generated:powerbuilding:blocks", CoverStudioStyle.WAVES, CoverPaletteKind.NOCHE, ENERGIA, MUSCULO, motif = 10),
    row("generated:powerbuilding:periodized", CoverStudioStyle.NUMERAL, CoverPaletteKind.PAPEL, ENERGIA, COLUMNA, motif = 12),
    // Culturismo (mente)
    row("generated:bodybuilding", CoverStudioStyle.WAVES, CoverPaletteKind.TINTA, MENTE, MUSCULO, motif = 9),
    row("generated:bodybuilding:blocks", CoverStudioStyle.GRID, CoverPaletteKind.NOCHE, MENTE, ENERGIA, motif = 6, days = 5),
    row("generated:bodybuilding:periodized", CoverStudioStyle.NUMERAL, CoverPaletteKind.PAPEL, MENTE, MUSCULO, motif = 12),
    // Halterofilia (columna)
    row("generated:weightlifting-base", CoverStudioStyle.WAVES, CoverPaletteKind.NOCHE, COLUMNA, MENTE, motif = 9),
    row("generated:weightlifting-base:blocks", CoverStudioStyle.GRID, CoverPaletteKind.PAPEL, COLUMNA, MUSCULO, motif = 6),
    row("generated:weightlifting-base:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.TINTA, COLUMNA, ENERGIA, motif = 15),
    // Armwrestling (músculo)
    row("generated:armwrestling", CoverStudioStyle.NUMERAL, CoverPaletteKind.TINTA, MUSCULO, COLUMNA, motif = 1),
    row("generated:armwrestling:blocks", CoverStudioStyle.GRID, CoverPaletteKind.NOCHE, MUSCULO, OK, motif = 6),
    row("generated:armwrestling:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.PAPEL, MUSCULO, COLUMNA, motif = 16),
    // Strongman (energía)
    row("generated:strongman", CoverStudioStyle.PLATES, CoverPaletteKind.NOCHE, ENERGIA, MUSCULO, motif = 4),
    row("generated:strongman:blocks", CoverStudioStyle.WAVES, CoverPaletteKind.PAPEL, ENERGIA, OK, motif = 10),
    row("generated:strongman:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.TINTA, ENERGIA, COLUMNA, motif = 17),
    // Powerlifting (músculo)
    row("generated:powerlifting", CoverStudioStyle.PLATES, CoverPaletteKind.PAPEL, MUSCULO, COLUMNA, motif = 4),
    row("generated:powerlifting:blocks", CoverStudioStyle.WAVES, CoverPaletteKind.TINTA, MUSCULO, MENTE, motif = 10),
    row("generated:powerlifting:periodized", CoverStudioStyle.TOPO, CoverPaletteKind.NOCHE, MUSCULO, ENERGIA, motif = 16),
)

/** La identidad asignada a [programId] (solo los 30 «a medida»); null si el programa no tiene fila. */
internal fun assignedCoverIdentity(programId: String): CoverIdentity? = ASSIGNED[programId]

/** Los ids que tienen identidad asignada. */
internal val assignedCoverProgramIds: Set<String> get() = ASSIGNED.keys

/**
 * La identidad de la portada de un programa, para Home. Busca primero la fila de [templateId] (el id del catálogo o del programa a
 * medida con el que se creó el programa) y luego la de [programId]. Si ninguna existe (un programa propio o importado), devuelve
 * una identidad determinista del hash de [programId]: el mismo id da siempre la misma.
 */
internal fun coverIdentityFor(programId: String, templateId: String?): CoverIdentity =
    templateId?.let { ASSIGNED[it] } ?: ASSIGNED[programId] ?: fallbackCoverIdentity(programId)

/** La identidad de respaldo de [programId]: estilo, fondo, dos acentos distintos y semilla salen de su hash. */
internal fun fallbackCoverIdentity(programId: String): CoverIdentity {
    val h = stableHash(programId)
    val first = positive(h / 13) % ACCENTS.size
    val second = (first + 1 + positive(h / 29) % (ACCENTS.size - 1)) % ACCENTS.size
    val style = CoverStudioStyle.entries[positive(h) % CoverStudioStyle.entries.size]
    return CoverIdentity(
        style = style,
        palette = CoverPaletteKind.entries[positive(h / 7) % CoverPaletteKind.entries.size],
        primary = ACCENTS[first],
        secondary = ACCENTS[second],
        motif = motifFor(style, positive(h / 53)),
        seed = h,
        days = 3 + positive(h / 97) % 3,
    )
}

/** El dato de [style] para la variación [r] (no negativa): numeral, semanas, discos, curvas u ondas. */
internal fun motifFor(style: CoverStudioStyle, r: Int): Int = when (style) {
    CoverStudioStyle.NUMERAL -> NUMERALS[r % NUMERALS.size]
    CoverStudioStyle.TOPO -> 14 + r % 5
    CoverStudioStyle.PLATES -> 4 + r % 3
    CoverStudioStyle.GRID -> if (r % 2 == 0) 1 else 6
    CoverStudioStyle.WAVES -> 9 + r % 4
}

/** El arte de una portada: la foto propia manda; con identidad, el motor; si no, el procedural de siempre. */
internal fun coverArtModeOf(hasPhoto: Boolean, identity: CoverIdentity?): CoverArtMode = when {
    hasPhoto -> CoverArtMode.PHOTO
    identity != null -> CoverArtMode.STUDIO
    else -> CoverArtMode.PROCEDURAL
}

/** Hash FNV-1a de 32 bits: el mismo texto da siempre el mismo número, en cualquier proceso. */
internal fun stableHash(text: String): Int {
    var h = -2128831035 // 0x811C9DC5, la base de FNV-1a
    for (ch in text) h = (h xor ch.code) * 16777619
    return h
}

private fun positive(x: Int): Int = x and Int.MAX_VALUE
