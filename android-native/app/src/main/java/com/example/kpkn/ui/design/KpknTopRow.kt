package com.example.kpkn.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Fila superior sin isla (KPKN_UI_V2 §3): «Atrás» a la izquierda con [KpknIconButton] y [actions] a la derecha, en 56 dp de alto.
 * El círculo del botón cae a 16 dp del borde, como el margen de los módulos. Sin [onBack] no hay atrás. No pone insets: la pantalla
 * da su `statusBarsPadding` por encima, como hace Program Detail.
 */
@Composable
fun KpknTopRow(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}
