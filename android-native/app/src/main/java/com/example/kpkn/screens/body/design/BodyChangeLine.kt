package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs
import kotlin.math.sign

/** Sentido de un cambio frente a la meta: hacia ella (`done`), en contra (`danger`) o sin meta (`textMuted`). */
enum class BodyChangeTone { Neutral, Toward, Away }

/** Hacia la meta si el cambio va en el mismo sentido que lo que falta. Sin meta o ya alcanzada, neutro. */
fun bodyChangeTone(change: Double, remaining: Double?): BodyChangeTone {
    if (remaining == null || abs(remaining) < BODY_FLAT_CHANGE || abs(change) < BODY_FLAT_CHANGE) return BodyChangeTone.Neutral
    return if (sign(change) == sign(remaining)) BodyChangeTone.Toward else BodyChangeTone.Away
}

/**
 * Cambio desde el punto de partida: «−2,4 kg desde el 12 de agosto», con TrendDown o TrendUp de 16 dp. Sin cambio apreciable,
 * solo el texto. [since] es la parte final, p. ej. «desde el 12 de agosto».
 */
@Composable
fun BodyChangeLine(
    change: Double,
    unit: String,
    since: String,
    tone: BodyChangeTone,
    modifier: Modifier = Modifier,
) {
    val color: Color = when (tone) {
        BodyChangeTone.Toward -> WizardColors.done
        BodyChangeTone.Away -> WizardColors.danger
        BodyChangeTone.Neutral -> WizardColors.textMuted
    }
    val flat = abs(change) < BODY_FLAT_CHANGE
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!flat) {
            Icon(
                imageVector = if (change < 0.0) KpknLineIcons.TrendDown else KpknLineIcons.TrendUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = if (flat) "Sin cambio $since" else "${formatSignedDecimalEs(change, 1)} $unit $since",
            style = KpknType.caption,
            color = color,
        )
    }
}
