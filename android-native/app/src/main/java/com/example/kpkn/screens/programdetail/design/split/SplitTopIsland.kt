package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.runtime.Composable
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTopIsland

/**
 * Isla superior de «Cambiar reparto» (P5): aparece al hacer scroll, cuando el título sale de la vista, con «Atrás» y el título. Sin
 * [onBack] no lleva icono. Sin estado: [visible] lo calcula quien la usa.
 */
@Composable
internal fun SplitTopIsland(
    visible: Boolean,
    onBack: (() -> Unit)?,
) {
    if (onBack != null) {
        KpknTopIsland(
            visible = visible,
            title = "Cambiar reparto",
            leading = {
                KpknIconButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Atrás",
                    onClick = onBack,
                )
            },
        )
    } else {
        KpknTopIsland(visible = visible, title = "Cambiar reparto")
    }
}
