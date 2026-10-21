package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.ProgramMode

/**
 * Nombre del enfoque de un programa con el vocabulario del asistente: «Fuerza», «Fuerza y músculo» y «Músculo».
 * No se añade un valor nuevo a [ProgramMode]: una app antigua no sabría leerlo; solo cambia lo que se dice.
 */
internal fun focusModeLabel(mode: ProgramMode): String = when (mode) {
    ProgramMode.POWERLIFTING -> "Fuerza"
    ProgramMode.POWERBUILDING -> "Fuerza y músculo"
    ProgramMode.HYPERTROPHY -> "Músculo"
}
