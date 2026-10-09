package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.programs.PROGRAM_COVER_SCHEME

/*
 * Qué fondo pinta el hero para `Program.coverImage`. Sin Compose: la decisión se prueba en la JVM.
 */

/** El fondo del hero: la portada empaquetada (`kpkn-cover://`), el degradado o una foto remota que carga Coil. */
internal enum class HeroCoverKind { BUNDLED, GRADIENT, REMOTE }

/**
 * `kpkn-cover://<id>` usa la portada empaquetada si existe ([hasBundledCover]) y, si no, el degradado Ember.
 * `gradient://…` o un valor vacío usan el degradado. Cualquier otro valor (URL, `content://`, `file://`) es una foto
 * remota que carga Coil, como antes.
 */
internal fun heroCoverKind(coverValue: String?, hasBundledCover: Boolean): HeroCoverKind {
    if (coverValue.isNullOrBlank() || coverValue.startsWith("gradient://")) return HeroCoverKind.GRADIENT
    if (coverValue.startsWith(PROGRAM_COVER_SCHEME)) {
        return if (hasBundledCover) HeroCoverKind.BUNDLED else HeroCoverKind.GRADIENT
    }
    return HeroCoverKind.REMOTE
}
