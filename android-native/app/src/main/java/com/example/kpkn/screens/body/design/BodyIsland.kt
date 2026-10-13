package com.example.kpkn.screens.body.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopIsland

/**
 * Isla superior de Cuerpo (BODY_V1 §2.1): «64,0 kg» con «−0,6 kg en 30 días», el icono de Cuerpo en la columna y el «+» en un círculo
 * de tinta. Aparece cuando la cabecera sale de la vista.
 */
@Composable
fun BodyIsland(
    visible: Boolean,
    title: String,
    subtitle: String?,
    onAdd: () -> Unit,
) {
    KpknTopIsland(
        visible = visible,
        title = title,
        subtitle = subtitle,
        leading = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(KpknSurface.inset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Body,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = SymbolPalette.columna,
                )
            }
        },
        actions = {
            KpknIconButton(
                icon = KpknLineIcons.Plus,
                contentDescription = "Registrar medidas",
                onClick = onAdd,
                filled = true,
            )
        },
    )
}
