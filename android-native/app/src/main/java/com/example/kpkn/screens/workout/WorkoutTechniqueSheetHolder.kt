package com.example.kpkn.screens.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Petición de abrir la hoja «Técnica» de la serie activa (KPKN LS2b). El Info de la cabecera suma una petición; la tarjeta activa la
 * atiende y muestra su hoja. El contador evita abrir la hoja al montar una tarjeta nueva.
 */
internal class WorkoutTechniqueSheetHolder {
    var requests by mutableIntStateOf(0)
}
