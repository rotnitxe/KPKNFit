package com.example.kpkn.screens.body.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknFloatingActionIsland
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import dev.chrisbanes.haze.HazeState

/**
 * La isla flotante «Nueva medición» de Cuerpo: el mismo componente que «Registrar comida» de Nutrición ([KpknFloatingActionIsland]), con
 * otro icono y otra etiqueta. Es el acceso primario a la hoja de registro y lleva el ancla del recorrido de Cuerpo. Quien la pone decide
 * dónde cae (sobre la barra inferior, igual que la de Nutrición).
 */
@Composable
fun BodyNewRecordIsland(
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknFloatingActionIsland(
        hazeState = hazeState,
        icon = KpknLineIcons.Plus,
        label = "Nueva medición",
        onClickLabel = "Abrir el registro de una medición",
        onClick = onClick,
        modifier = modifier,
        anchorModifier = Modifier.tourAnchor(TourAnchors.BODY_PESAJES),
    )
}
