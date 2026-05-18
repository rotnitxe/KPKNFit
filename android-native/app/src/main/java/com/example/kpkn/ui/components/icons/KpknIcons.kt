package com.example.kpkn.ui.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

// ─── Dumbbell (Entreno tab) ──────────────────────────────────────────────────
@Composable
fun DumbbellIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val sw = 2.dp.toPx()
        val h = this.size.height / 2
        val w = this.size.width
        drawLine(tint, Offset(4.dp.toPx(), h), Offset(w - 4.dp.toPx(), h), sw)
        drawRoundRect(tint, Offset(0f, h - 6.dp.toPx()), Size(4.dp.toPx(), 12.dp.toPx()), CornerRadius(1.dp.toPx()))
        drawRoundRect(tint, Offset(w - 4.dp.toPx(), h - 6.dp.toPx()), Size(4.dp.toPx(), 12.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}

// ─── Apple (Nutrición tab) ──────────────────────────────────────────────────
@Composable
fun NutritionIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val apple = Path().apply {
            moveTo(12.dp.toPx(), 8.1.dp.toPx())
            cubicTo(10.5.dp.toPx(), 6.8.dp.toPx(), 7.7.dp.toPx(), 6.7.dp.toPx(), 6.1.dp.toPx(), 8.6.dp.toPx())
            cubicTo(3.8.dp.toPx(), 11.2.dp.toPx(), 5.0.dp.toPx(), 17.5.dp.toPx(), 7.7.dp.toPx(), 20.0.dp.toPx())
            cubicTo(9.0.dp.toPx(), 21.2.dp.toPx(), 10.4.dp.toPx(), 20.2.dp.toPx(), 12.0.dp.toPx(), 20.2.dp.toPx())
            cubicTo(13.6.dp.toPx(), 20.2.dp.toPx(), 15.0.dp.toPx(), 21.2.dp.toPx(), 16.3.dp.toPx(), 20.0.dp.toPx())
            cubicTo(19.0.dp.toPx(), 17.5.dp.toPx(), 20.2.dp.toPx(), 11.2.dp.toPx(), 17.9.dp.toPx(), 8.6.dp.toPx())
            cubicTo(16.3.dp.toPx(), 6.7.dp.toPx(), 13.5.dp.toPx(), 6.8.dp.toPx(), 12.0.dp.toPx(), 8.1.dp.toPx())
            close()
        }
        drawPath(apple, tint)

        drawLine(
            color = tint,
            start = Offset(12.dp.toPx(), 7.5.dp.toPx()),
            end = Offset(13.3.dp.toPx(), 4.2.dp.toPx()),
            strokeWidth = 1.7.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )

        val leaf = Path().apply {
            moveTo(14.2.dp.toPx(), 5.2.dp.toPx())
            cubicTo(15.5.dp.toPx(), 3.4.dp.toPx(), 18.1.dp.toPx(), 3.2.dp.toPx(), 19.5.dp.toPx(), 4.1.dp.toPx())
            cubicTo(18.5.dp.toPx(), 5.9.dp.toPx(), 16.1.dp.toPx(), 6.9.dp.toPx(), 14.2.dp.toPx(), 5.2.dp.toPx())
            close()
        }
        drawPath(leaf, tint)
    }
}

// ─── W (WikiLab tab) ─────────────────────────────────────────────────────────
@Composable
fun WikiIcon(tint: Color) {
    Text(
        text = "W",
        color = tint,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
    )
}

// ─── Intertwined Rings (toggle: vista combinada) ─────────────────────────────
@Composable
fun IntertwinedRingsIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val r = size.toPx() / 5f
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val sw = 2.dp.toPx()
        drawCircle(tint, r, Offset(center.x - r * 0.6f, center.y), style = Stroke(sw))
        drawCircle(tint, r, Offset(center.x + r * 0.6f, center.y), style = Stroke(sw))
    }
}

// ─── Single Ring (toggle: vista individual) ──────────────────────────────────
@Composable
fun SingleRingIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val r = minOf(this.size.width, this.size.height) / 2f - 3.dp.toPx()
        drawCircle(tint, r, style = Stroke(2.dp.toPx()))
        drawCircle(tint, r * 0.5f)
    }
}

// ─── Powerlifter corner placeholder icon ─────────────────────────────────────
@Composable
fun PowerlifterCornerIcon(
    tint: Color,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    // Stylized "C" silhouette as geometric placeholder
    Canvas(modifier.size(size)) {
        val r = minOf(this.size.width, this.size.height) / 2f - 1.dp.toPx()
        val center = Offset(this.size.width / 2, this.size.height / 2)
        drawArc(
            color = tint,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

