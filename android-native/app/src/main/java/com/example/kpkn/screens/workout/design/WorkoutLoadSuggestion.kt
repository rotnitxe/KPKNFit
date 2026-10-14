package com.example.kpkn.screens.workout.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import androidx.compose.ui.unit.dp

/**
 * Carga sugerida de la serie (KPKN LS1). [suggestion] es la cifra con su unidad («82,5 kg»): la pieza escribe «Sugerida» delante.
 * Sin valor no pinta nada, ni siquiera hueco.
 *
 * Sin aplicar: «Sugerida 82,5 kg» con «Usar» y, debajo, [reason] («Por tu última sesión»). Aplicada: «Sugerida 82,5 kg · en uso», sin
 * acción. Los cambios entre los dos estados se funden (con movimiento reducido, de golpe).
 */
@Composable
fun WorkoutLoadSuggestion(
    suggestion: String?,
    applied: Boolean,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String? = null,
) {
    if (suggestion == null) return
    val reduced = wizardReducedMotion()
    AnimatedContent(
        targetState = applied,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val millis = if (reduced) 0 else 180
            (fadeIn(tween(millis)) togetherWith fadeOut(tween(millis))).using(SizeTransform(clip = false))
        },
        label = "workoutLoadSuggestion",
    ) { isApplied ->
        // El motivo va en la misma columna que «Sugerida», a 2 dp; «Usar» (48 dp de toque) queda centrado a la derecha.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isApplied) "Sugerida $suggestion · en uso" else "Sugerida $suggestion",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                if (!isApplied && reason != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = reason,
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }
            }
            if (!isApplied) {
                KpknTextAction(
                    text = "Usar",
                    onClick = onUse,
                    modifier = Modifier.semantics { this.contentDescription = "Usar $suggestion" },
                )
            }
        }
    }
}
