package com.example.kpkn.data.programs

import com.example.kpkn.data.models.Program

/*
 * STUBS DEL ARNÉS JVM: copia de las funciones puras de ProgramCoverRepository.kt (excluido: usa Context, AssetManager
 * y Bitmap). Sin assets en el arnés, la portada solo se fija como referencia de texto (igual que en la app).
 */

/** Esquema del `coverImage` que apunta a la portada de una entrada del catálogo (copia de ProgramCoverRepository.kt). */
const val PROGRAM_COVER_SCHEME = "kpkn-cover://"

/** Copia de sanitizeCoverId de ProgramCoverRepository.kt. */
fun sanitizeCoverId(id: String): String = buildString(id.length) {
    for (ch in id.lowercase()) append(if (ch in 'a'..'z' || ch in '0'..'9') ch else '_')
}

/** Copia de programCoverValueOf de ProgramCoverRepository.kt. */
fun programCoverValueOf(entryId: String): String = PROGRAM_COVER_SCHEME + entryId

/** Copia de programCoverEntryIdOf de ProgramCoverRepository.kt. */
fun programCoverEntryIdOf(coverImage: String?): String? =
    coverImage?.takeIf { it.startsWith(PROGRAM_COVER_SCHEME) }
        ?.removePrefix(PROGRAM_COVER_SCHEME)
        ?.takeIf { it.isNotBlank() }

/** Copia de withCatalogCover de ProgramCoverRepository.kt: el programa con la portada de [entryId], si no tiene una. */
fun Program.withCatalogCover(entryId: String): Program =
    if (coverImage != null) this else copy(coverImage = programCoverValueOf(entryId))
