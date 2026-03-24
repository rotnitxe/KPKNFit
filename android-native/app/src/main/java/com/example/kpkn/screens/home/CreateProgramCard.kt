package com.example.kpkn.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateProgramCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        // Canvas for halo effect
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = maxOf(size.width, size.height) * 0.6f

            // Subtle radial glow effect
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius,
                ),
                radius = maxRadius,
                center = Offset(centerX, centerY),
            )
        }

        // Card content — Liquid Glass Effect
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .blur(radiusX = 10.dp, radiusY = 10.dp)  // Efecto glass frosted
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(28.dp),
            color = primaryColor.copy(alpha = 0.22f),  // Menos traslúcida (0.12 → 0.22)
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "CREAR PROGRAMA",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = primaryColor,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Para disfrutar de todas las funciones avanzadas de KPKN para planificar tu rutina, crear macrociclos enteros y funciones automáticas de sobrecarga progresiva crea tu primer programa de entrenamiento.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
