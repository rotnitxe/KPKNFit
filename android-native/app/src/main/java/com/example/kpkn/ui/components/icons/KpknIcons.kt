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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
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
            cap = StrokeCap.Round,
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

// ─── Bicep / Arm (Cuerpo tab) ───────────────────────────────────────────────
@Composable
fun BodyIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val strokeWidth = 1.6.dp.toPx()
        
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            val p1 = PathParser().parsePathString("M12.409 13.017A5 5 0 0 1 22 15c0 3.866-4 7-9 7-4.077 0-8.153-.82-10.371-2.462-.426-.316-.631-.832-.62-1.362C2.118 12.723 2.627 2 10 2a3 3 0 0 1 3 3 2 2 0 0 1-2 2c-1.105 0-1.64-.444-2-1 Z").toPath()
            val p2 = PathParser().parsePathString("M15 14a5 5 0 0 0-7.584 2").toPath()
            val p3 = PathParser().parsePathString("M9.964 6.825C8.019 7.977 9.5 13 8 15").toPath()
            
            // Solid filled arm body
            drawPath(p1, color = tint)
            drawPath(p1, color = tint, style = Stroke(width = 0.8.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            
            // Inner muscle definition lines
            drawPath(p2, color = Color(0xFF141414), style = Stroke(width = strokeWidth / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p3, color = Color(0xFF141414), style = Stroke(width = strokeWidth / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
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

