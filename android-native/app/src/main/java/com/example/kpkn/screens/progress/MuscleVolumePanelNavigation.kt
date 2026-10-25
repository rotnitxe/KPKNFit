package com.example.kpkn.screens.progress

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Cómo abrir el panel de volumen desde una pantalla que no conoce la navegación (la pestaña Volumen del programa). Recibe el id del
 * programa para contar solo sus registros. La navegación lo provee alrededor de la pantalla; sin proveedor vale null y el enlace no
 * se muestra (vistas previas, hojas de render).
 */
val LocalMuscleVolumePanelOpener: ProvidableCompositionLocal<((programId: String?) -> Unit)?> = compositionLocalOf { null }
