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

// ─── Fork+Plate (Nutrición tab) ─────────────────────────────────────────────
@Composable
fun NutritionIcon(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val sw = 1.5.dp.toPx()
        // Fork prongs
        drawLine(tint, Offset(6.dp.toPx(), 4.dp.toPx()), Offset(6.dp.toPx(), 12.dp.toPx()), sw)
        drawLine(tint, Offset(9.dp.toPx(), 4.dp.toPx()), Offset(9.dp.toPx(), 12.dp.toPx()), sw)
        drawLine(tint, Offset(6.dp.toPx(), 12.dp.toPx()), Offset(9.dp.toPx(), 12.dp.toPx()), sw)
        drawLine(tint, Offset(7.5.dp.toPx(), 12.dp.toPx()), Offset(7.5.dp.toPx(), 20.dp.toPx()), 2.dp.toPx())
        // Plate (oval)
        drawOval(tint, Offset(14.dp.toPx(), 4.dp.toPx()), Size(6.dp.toPx(), 10.dp.toPx()), style = Stroke(sw))
        drawLine(tint, Offset(17.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 20.dp.toPx()), 2.dp.toPx())
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

// ─── Caupolican placeholder icon ─────────────────────────────────────────────
@Composable
fun CaupolicanIcon(
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
