package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Tono de un aviso de Home: [DANGER] para el exceso de carga y [WARN] para el resto de avisos. */
enum class HomeNoticeTone { DANGER, WARN }

/**
 * Aviso de Home, sin caja: una línea fina arriba y otra abajo, un icono de línea en un círculo de 36 dp teñido del tono, el título,
 * el cuerpo y una acción de texto (48 dp) en el color del tono. Etiqueta de prueba: `home-notice-<tono>`.
 */
@Composable
fun HomeNotice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    tone: HomeNoticeTone,
    modifier: Modifier = Modifier,
) {
    val accent = noticeAccent(tone)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WizardSpacing.gutter)
            .testTag("home-notice-${tone.name.lowercase()}"),
    ) {
        NoticeRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            NoticeIcon(tone = tone, accent = accent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = WizardTypography.cardTitle, color = WizardColors.text)
                Spacer(Modifier.height(4.dp))
                Text(text = body, style = WizardTypography.body, color = WizardColors.textMuted)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = WizardSpacing.touchTarget)
                        .clickable(role = Role.Button, onClick = onAction),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(text = actionLabel, style = WizardTypography.controlLabel, color = accent)
                }
            }
        }
        NoticeRule()
    }
}

/** Color del tono: el rojo suave de la marca para el peligro y la energía (amarillo de los símbolos) para el aviso. */
private fun noticeAccent(tone: HomeNoticeTone): Color = when (tone) {
    HomeNoticeTone.DANGER -> WizardColors.danger
    HomeNoticeTone.WARN -> SymbolPalette.energia
}

/** Filete fino que separa el aviso de lo de arriba y de lo de abajo. */
@Composable
private fun NoticeRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider)) {}
}

/**
 * Icono de línea del tono sobre un círculo de 36 dp al 14 %: un triángulo con signo para el peligro y un círculo con signo para el
 * aviso. Es decorativo: el título ya lo dice todo.
 */
@Composable
private fun NoticeIcon(tone: HomeNoticeTone, accent: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = 1.8.dp.toPx()
            val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val cx = size.width / 2f
            when (tone) {
                HomeNoticeTone.DANGER -> {
                    val triangle = Path().apply {
                        moveTo(cx, w)
                        lineTo(size.width - w, size.height - w)
                        lineTo(w, size.height - w)
                        close()
                    }
                    drawPath(triangle, accent, style = stroke)
                    drawLine(accent, Offset(cx, size.height * 0.40f), Offset(cx, size.height * 0.66f), strokeWidth = w, cap = StrokeCap.Round)
                    drawCircle(accent, radius = w * 0.8f, center = Offset(cx, size.height * 0.80f))
                }
                HomeNoticeTone.WARN -> {
                    drawCircle(accent, radius = cx - w, center = Offset(cx, cx), style = stroke)
                    drawLine(accent, Offset(cx, size.height * 0.30f), Offset(cx, size.height * 0.58f), strokeWidth = w, cap = StrokeCap.Round)
                    drawCircle(accent, radius = w * 0.8f, center = Offset(cx, size.height * 0.72f))
                }
            }
        }
    }
}
