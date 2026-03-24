package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateProgramCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Canvas for halo effect
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            drawHaloEffect(
                primaryColor = MaterialTheme.colorScheme.primary,
                size = size,
            )
        }

        // Card content
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shadowElevation = 0.dp, // We're doing our own glow effect
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
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Comienza creando tu primer programa de entrenamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun DrawScope.drawHaloEffect(
    primaryColor: Color,
    size: androidx.compose.ui.geometry.Size,
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
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            radius = maxRadius,
        ),
        radius = maxRadius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
    )
}
