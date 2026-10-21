package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.programs.PROGRAM_COVER_SCHEME
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.PROGRAM_STUDIO_SCHEME

/*
 * Qué fondo pinta la cabecera para `Program.coverImage`. Sin Compose: la decisión se prueba en la JVM.
 */

/**
 * El fondo de la cabecera: la portada empaquetada (`kpkn-cover://`), el degradado, la portada de estudio (`kpkn-studio://`, que pinta
 * el motor de portadas) o una foto remota que carga Coil.
 */
internal enum class HeroCoverKind { BUNDLED, GRADIENT, STUDIO, REMOTE }

/**
 * `kpkn-cover://<id>` usa la portada empaquetada si existe ([hasBundledCover]) y, si no, el degradado Ember. `kpkn-studio://` es la
 * portada de estudio del motor, nunca una foto. `gradient://…` o un valor vacío usan el degradado. Cualquier otro valor (URL,
 * `content://`, `file://`) es una foto remota que carga Coil, como antes.
 */
internal fun heroCoverKind(coverValue: String?, hasBundledCover: Boolean): HeroCoverKind {
    if (coverValue.isNullOrBlank() || coverValue.startsWith("gradient://")) return HeroCoverKind.GRADIENT
    if (coverValue.startsWith(PROGRAM_STUDIO_SCHEME)) return HeroCoverKind.STUDIO
    if (coverValue.startsWith(PROGRAM_COVER_SCHEME)) {
        return if (hasBundledCover) HeroCoverKind.BUNDLED else HeroCoverKind.GRADIENT
    }
    return HeroCoverKind.REMOTE
}
