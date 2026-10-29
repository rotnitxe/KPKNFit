package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Título de la pantalla vacía: Syne Bold 20 (`.syne`). */
private val EmptyTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    textAlign = TextAlign.Center,
)

/** Trazo del arco del dibujo (`#f2eee6` al 60 %). */
private val ArcColor = Color(0xFFF2EEE6).copy(alpha = 0.6f)

/**
 * Búsqueda sin resultados (lienzo s-none): un dibujo de líneas que se traza al aparecer, «No encontré «…»», la frase y las dos salidas:
 * «Describirla» ([onDescribe], pasa el texto a la pestaña Describir) y «Crear alimento» ([onCreate], con el nombre ya puesto).
 */
@Composable
fun LoggerSearchEmpty(
    query: String,
    onDescribe: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 40.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyDrawing()
        Text(
            text = "No encontré «$query»",
            style = EmptyTitleStyle,
            color = LoggerColors.ink,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        )
        Text(
            text = "Descríbela y la estimamos, o créala con sus datos.",
            style = LoggerText.body,
            color = LoggerColors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp, bottom = 24.dp).fillMaxWidth(),
        )
        LoggerPill(text = "Describirla", onClick = onDescribe, icon = KpknLineIcons.Edit, modifier = Modifier.fillMaxWidth())
        LoggerPill(
            text = "Crear alimento",
            onClick = onCreate,
            tone = LoggerPillTone.SECONDARY,
            icon = KpknLineIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/**
 * El dibujo del lienzo (`viewBox 120×80`, 160 dp de ancho): el arco «M10 60 Q60 10 110 60» y el círculo de centro (60, 48) y radio 16,
 * que se trazan a la vez en 1,6 s. Con movimiento reducido salen ya dibujados.
 */
@Composable
private fun EmptyDrawing() {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, tween(1600, easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)))
    }
    val arc = remember { Path() }
    val measure = remember { PathMeasure() }
    val part = remember { Path() }
    Canvas(Modifier.width(160.dp).height(160.dp * 80f / 120f).clearAndSetSemantics { }) {
        val unit = size.width / 120f
        arc.reset()
        arc.moveTo(10f * unit, 60f * unit)
        arc.quadraticTo(60f * unit, 10f * unit, 110f * unit, 60f * unit)
        measure.setPath(arc, false)
        part.reset()
        measure.getSegment(0f, measure.length * progress.value, part, true)
        drawPath(part, ArcColor, style = Stroke(width = 1.4f * unit, cap = StrokeCap.Butt))
        val radius = 16f * unit
        drawArc(
            color = LoggerColors.mus,
            startAngle = 0f,
            sweepAngle = 360f * progress.value,
            useCenter = false,
            topLeft = Offset(60f * unit - radius, 48f * unit - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 1.6f * unit),
        )
    }
}
