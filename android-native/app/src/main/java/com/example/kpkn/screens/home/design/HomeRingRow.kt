package com.example.kpkn.screens.home.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import java.util.Locale

/*
 * Filas y acentos de RINGS en Home: la torre a la izquierda y, a la derecha, una fila por canal.
 */

/** Proporción ancho : alto de la torre del logo (76 × 46,5 unidades): la torre de Home mide su alto por esta proporción. */
internal const val RingTowerAspect = 76f / 46.5f

/** Acento de un canal de RINGS en Home: energía, músculo o columna (los mismos colores que la torre). */
internal fun RecoveryChannelId.homeAccent(): Color = when (this) {
    RecoveryChannelId.MUSCULAR -> SymbolPalette.musculo
    RecoveryChannelId.SYSTEM -> SymbolPalette.energia
    RecoveryChannelId.STRUCTURE -> SymbolPalette.columna
}

/**
 * Una fila de RINGS: el nombre del canal en `eyebrow` con su acento, la cifra en Syne con «%» y, al lado, la banda o la
 * procedencia ([secondary]). Sin dato ([score] nulo) la cifra es «—»; cargando, «…». Es un botón de al menos 56 dp que abre el
 * detalle del canal (etiqueta `home-rings-row-<canal>`).
 */
@Composable
fun HomeRingRow(
    channel: RecoveryChannelId,
    name: String,
    score: Int?,
    secondary: String?,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = channel.homeAccent()
    val shown = rememberHomeCount(key = "rings-row-${channel.name}", target = score ?: 0)
    val figure = when {
        loading -> "…"
        score == null -> "—"
        else -> "$shown"
    }
    val spoken = when {
        loading -> "calculando recuperación"
        score == null -> "sin datos"
        else -> "$score %"
    }
    val description = listOfNotNull(name, spoken, secondary).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("home-rings-row-${channel.name}")
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = name.uppercase(Locale("es")),
                style = HomeTypography.eyebrow,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = figure, style = WizardTypography.question, color = WizardColors.text, maxLines = 1)
                if (!loading && score != null) {
                    Text(
                        text = " %",
                        style = WizardTypography.header,
                        color = WizardColors.text,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            // La banda va bajo la cifra, a todo el ancho de la columna: al lado partía palabras («Moderad / a») a 360 dp y a 130 %.
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = WizardTypography.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
