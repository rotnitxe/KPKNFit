package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Código muerto, a la espera de la tarjeta de la serie (LV8): las acciones de la serie del modo dios («Convertir a dropset / rest-pause»,
 * «Eliminar serie» y «Omitir serie») ya viven en las opciones de la serie (`SeriesOptionsSheet`, LV11): «Saltar» y «Borrar» con su
 * «Deshacer», y el tipo de serie con su alcance. La tarjeta (`SetExecutionCard`) aún la llama con `visible = false`; cuando LV8 suelte esa
 * llamada y sus parámetros (`godModeActionsVisible`, `isDropSet`, `isRestPause`, `canDeleteSet`, `onDropSet`, `onRestPause`,
 * `onDeleteSet`, `onOmitSet`), este stub se borra.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun GodModeSetActionsOverlay(
    visible: Boolean,
    isDropSet: Boolean,
    isRestPause: Boolean,
    canDeleteSet: Boolean,
    onDropSet: () -> Unit,
    onRestPause: () -> Unit,
    onDeleteSet: () -> Unit,
    onOmitSet: () -> Unit,
    modifier: Modifier = Modifier,
) = Unit
