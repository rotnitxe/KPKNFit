package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Tu rango habitual al empezar («78–92 %») y su barra de 0 a 100: pista blanca al 10 %, la franja del rango en el [accent] al 30 %
 * y una marca de 2 × 12 dp en tinta en el valor de hoy ([score]; sin dato, sin marca). La marca se desliza la primera vez.
 */
@Composable
fun HomeRingRange(
    range: IntRange,
    score: Int?,
    accent: Color,
    key: String,
    modifier: Modifier = Modifier,
) {
    val mark = rememberHomeTrace(key = "range-$key", target = (score ?: 0).coerceIn(0, 100) / 100f)
    val description = "Tu rango habitual, de ${range.first} a ${range.last} %" + (score?.let { ", hoy $it %" } ?: "")
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tu rango habitual al empezar: ${range.first}–${range.last} %",
            style = WizardTypography.bodySmall,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .semantics { contentDescription = description },
        ) {
            val barHeight = 4.dp.toPx()
            val midY = size.height / 2f
            val top = midY - barHeight / 2f
            drawRect(color = Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, top), size = Size(size.width, barHeight))
            val from = range.first.coerceIn(0, 100) / 100f * size.width
            val to = range.last.coerceIn(0, 100) / 100f * size.width
            drawRect(
                color = accent.copy(alpha = 0.30f),
                topLeft = Offset(from, top),
                size = Size((to - from).coerceAtLeast(0f), barHeight),
            )
            if (score != null) {
                val markWidth = 2.dp.toPx()
                val markHeight = 12.dp.toPx()
                val x = (mark * size.width - markWidth / 2f).coerceIn(0f, size.width - markWidth)
                drawRect(
                    color = WizardColors.text,
                    topLeft = Offset(x, midY - markHeight / 2f),
                    size = Size(markWidth, markHeight),
                )
            }
        }
    }
}
