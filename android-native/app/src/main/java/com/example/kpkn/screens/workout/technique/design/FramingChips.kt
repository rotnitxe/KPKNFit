package com.example.kpkn.screens.workout.technique.design

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
import com.example.kpkn.domain.biomechanics.framing.FramingCheckStatus
import com.example.kpkn.domain.biomechanics.framing.FramingInstruction
import com.example.kpkn.domain.biomechanics.framing.FramingIssue
import com.example.kpkn.domain.biomechanics.framing.FramingVerdict
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Chip flotante que muestra el veredicto del encuadre en tiempo real (VT8a).
 * Muestra «✓ Encuadre listo» en verde cuando la racha de comprobaciones es correcta,
 * o el primer problema detectado para corregir la posición antes de grabar.
 */
@Composable
fun FramingChip(
    verdict: FramingVerdict,
    modifier: Modifier = Modifier,
    isFrontPreferred: Boolean = false,
    visualMode: Boolean = false,
    status: FramingCheckStatus = FramingCheckStatus.EVALUATED,
    /** Con barra el visto bueno dice «Cuerpo y barra visibles»; sin ella, «Cuerpo visible». */
    hasBarbell: Boolean = false,
) {
    val isOk = verdict.ok && status == FramingCheckStatus.EVALUATED
    val primaryIssue = verdict.issues.firstOrNull()
    if (visualMode) {
        // `.v2-framing-status` (prototipo 02): franja oscura de radio 11, icono ✓ o «i» y la frase completa de la guía.
        val sentence = when {
            status == FramingCheckStatus.UNAVAILABLE -> FramingInstruction.UNAVAILABLE_SPEECH
            status == FramingCheckStatus.CHECKING -> FramingInstruction.CHECKING_SHORT
            isOk -> if (hasBarbell) "Cuerpo y barra visibles" else "Cuerpo visible"
            primaryIssue != null -> FramingInstruction.spoken(primaryIssue,
                if (isFrontPreferred) com.example.kpkn.domain.biomechanics.kinematics.ViewKind.FRONT else com.example.kpkn.domain.biomechanics.kinematics.ViewKind.SIDE)
            else -> FramingInstruction.CHECKING_SHORT
        }
        Row(
            modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp)).background(Color(0xBB111111))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.Icon(
                if (isOk) com.example.kpkn.ui.components.icons.KpknLineIcons.Check else com.example.kpkn.ui.components.icons.KpknLineIcons.Info,
                null, Modifier.size(16.dp), tint = if (isOk) Color(0xFFD7E5D5) else WizardColors.text,
            )
            Text(text = sentence, fontSize = 10.sp, lineHeight = 14.sp, color = WizardColors.text,
                fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.body)
        }
        return
    }

    val text = when {
        status == FramingCheckStatus.UNAVAILABLE -> FramingInstruction.UNAVAILABLE_SHORT
        status == FramingCheckStatus.CHECKING -> FramingInstruction.CHECKING_SHORT
        isOk -> "✓ ${FramingInstruction.READY}"
        primaryIssue != null -> framingIssueText(primaryIssue, isFrontPreferred)
        else -> FramingInstruction.CHECKING_SHORT
    }

    val warningColor = if (visualMode) WizardColors.textMuted else Color(0xFFFFA726)
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
fun framingIssueText(issue: FramingIssue, isFrontPreferred: Boolean = false): String =
    FramingInstruction.short(issue, isFrontPreferred)
