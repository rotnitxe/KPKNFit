package com.example.kpkn.screens.workout.technique.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.framing.FramingIssue
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Chip flotante que muestra el veredicto del encuadre en tiempo real (VT8a).
 * Muestra «✓ Encuadre» en verde cuando la racha de comprobaciones es correcta,
 * o el primer problema detectado para corregir la posición antes de grabar.
 */
@Composable
fun FramingChip(
    verdict: FramingVerdict,
    modifier: Modifier = Modifier,
    isFrontPreferred: Boolean = false,
) {
    val isOk = verdict.ok
    val primaryIssue = verdict.issues.firstOrNull()

    val text = when {
        isOk -> "✓ Encuadre"
        primaryIssue != null -> framingIssueText(primaryIssue, isFrontPreferred)
        else -> "Comprobando encuadre…"
    }

    val warningColor = Color(0xFFFFA726)
    val (bgColor, borderColor, textColor) = when {
        isOk -> Triple(
            WizardColors.done.copy(alpha = 0.22f),
            WizardColors.done.copy(alpha = 0.60f),
            WizardColors.done,
        )
        primaryIssue == FramingIssue.NO_PERSON -> Triple(
            KpknSurface.module.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.85f),
        )
        else -> Triple(
            warningColor.copy(alpha = 0.18f),
            warningColor.copy(alpha = 0.50f),
            Color.White,
        )
    }

    Surface(
        modifier = modifier,
        shape = KpknShape.pill,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isOk && primaryIssue != null) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = KpknShape.pill,
                    color = warningColor,
                ) {}
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

/**
 * Devuelve el texto descriptivo del problema de encuadre en español neutro.
 */
fun framingIssueText(issue: FramingIssue, isFrontPreferred: Boolean = false): String = when (issue) {
    FramingIssue.NO_PERSON -> "Ponte en cuadro"
    FramingIssue.TWO_PEOPLE -> "Hay 2 personas"
    FramingIssue.TOO_CLOSE -> "Demasiado cerca"
    FramingIssue.FEET_OUT -> "Aléjate: no se ven los pies"
    FramingIssue.HEAD_OUT -> "Aléjate: no se ve la cabeza"
    FramingIssue.TOO_SMALL -> "Acércate más"
    FramingIssue.WRONG_VIEW -> if (isFrontPreferred) "Gírate de frente" else "Gírate de lado"
    FramingIssue.FACE_HIDDEN -> "Cara no visible"
    FramingIssue.TILTED -> "Endereza el móvil"
    FramingIssue.NO_PLATE -> "No veo el disco"
}
