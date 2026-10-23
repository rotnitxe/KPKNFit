package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionEditorDropKind
import com.example.kpkn.screens.sessioneditor.SessionEditorDropPlan
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso de lo que pasaría si se soltara ahora el ejercicio que se arrastra (ED-A · A3). Flota sobre la lista, así que no mueve nada, y va
 * como región viva para que TalkBack lo lea. Con una superserie llena, o cualquier otro destino imposible, usa el tono de `danger`.
 * Sin frase (todavía no hay destino) no se pinta.
 */
@Composable
internal fun SessionEditorDropHintBanner(
    plan: SessionEditorDropPlan?,
    modifier: Modifier = Modifier,
) {
    val shown = plan?.takeIf { it.hint.isNotBlank() } ?: return
    val invalid = shown.kind == SessionEditorDropKind.INVALID
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("session_editor_drop_hint")
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(if (invalid) WizardColors.danger.copy(alpha = 0.92f) else KpknSurface.inset, shape)
            .border(
                1.dp,
                if (invalid) WizardColors.danger else WizardColors.text.copy(alpha = 0.18f),
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = shown.hint,
            style = KpknType.caption,
            color = if (invalid) WizardColors.ctaContent else WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La señal de «Sin grupo» cuando la sesión no tiene ejercicios sueltos: un recuadro de trazos que aparece justo ENCIMA del borde de arriba
 * del primer grupo sin ocupar sitio (no mueve la lista) mientras el destino sea sacarlo de su grupo. Va en un hueco de alto cero de la lista.
 */
@Composable
internal fun SessionEditorOutsideDropZone(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val ink = WizardColors.text
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp)
            .zIndex(2f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true, align = Alignment.CenterVertically)
                .offset(y = (-26).dp)
                .padding(horizontal = 16.dp)
                .height(44.dp)
                .testTag("session_editor_drop_outside")
                .background(KpknSurface.canvas, RoundedCornerShape(16.dp))
                .drawBehind {
                    drawRoundRect(
                        color = ink,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                        ),
                    )
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Sin grupo", style = KpknType.bodyStrong, color = ink, modifier = Modifier.weight(1f))
            Text(text = "Soltar aquí", style = KpknType.caption, color = ink)
        }
    }
}
