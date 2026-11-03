package com.example.kpkn.screens.nutrition.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknFloatingActionIsland
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import dev.chrisbanes.haze.HazeState

/**
 * La isla «Registrar comida» (N2, NUTRITION_V1 §2.8): píldora de vidrio v2 de 56 dp con «+» y el texto en `bodyStrong`. Es el único acceso
 * primario al registro. Abre el registro (lo decide la pantalla, con [onClick]) y conserva el ancla del recorrido. Se esconde mientras hay
 * una hoja o un diálogo abiertos; entra con un fundido y 8 dp de subida (220 ms). Con movimiento reducido, solo fundidos de 120 ms. Quien
 * la pone decide dónde cae (sobre la barra inferior). El componente es el compartido [KpknFloatingActionIsland].
 */
@Composable
fun NutritionLogIsland(
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknFloatingActionIsland(
        hazeState = hazeState,
        icon = KpknLineIcons.Plus,
        label = "Registrar comida",
        onClickLabel = "Abrir el registro de comida",
        onClick = onClick,
        modifier = modifier,
        anchorModifier = Modifier.tourAnchor(TourAnchors.NUTRITION_REGISTRO),
    )
}
