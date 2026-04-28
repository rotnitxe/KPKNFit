package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.calculations.PlateCalculator
import com.example.kpkn.domain.calculations.PlateResult
import kotlin.math.roundToInt

private val PLATE_COLORS: Map<Double, Color> = mapOf(
    25.0 to Color(0xFFD32F2F),
    20.0 to Color(0xFF1976D2),
    15.0 to Color(0xFFFFC107),
    10.0 to Color(0xFF388E3C),
    5.0 to Color(0xFFFFFFFF),
    2.5 to Color(0xFF9E9E9E),
    1.25 to Color(0xFF757575),
    0.5 to Color(0xFFBDBDBD),
    0.25 to Color(0xFF9E9E9E),
)

private fun plateColor(weight: Double): Color =
    PLATE_COLORS.entries.firstOrNull { it.key == weight }?.value
        ?: Color(0xFF607D8B)

private fun plateHeight(weight: Double, maxHeight: Float): Float {
    return when {
        weight >= 25.0 -> maxHeight
        weight >= 20.0 -> maxHeight * 0.92f
        weight >= 15.0 -> maxHeight * 0.82f
        weight >= 10.0 -> maxHeight * 0.72f
        weight >= 5.0 -> maxHeight * 0.58f
        weight >= 2.5 -> maxHeight * 0.44f
        weight >= 1.25 -> maxHeight * 0.34f
        else -> maxHeight * 0.28f
    }
}

@Composable
fun BarbellPlateVisualizer(
    targetWeight: Double,
    barbellWeight: Double,
    availablePlates: List<Double>,
    modifier: Modifier = Modifier,
) {
    val result = PlateCalculator.calculatePlates(targetWeight, barbellWeight, availablePlates)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Barra + platos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val weightLabel = if (result.isExact) {
                    "${result.achievedWeight} kg"
                } else {
                    "${result.achievedWeight} kg (objetivo ${result.targetWeight} kg)"
                }
                Text(
                    weightLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.isExact) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2

                val barHeight = 14f
                val sleeveWidth = size.width * 0.20f
                val barWidth = size.width * 0.60f
                val plateStartX = centerX - barWidth / 2 + 8f
                val plateEndX = centerX + barWidth / 2 - 8f
                val collarWidth = 10f

                barbellAndCollars(centerX, centerY, sleeveWidth, barWidth, barHeight, collarWidth)

                val maxPlateHeight = size.height * 0.90f
                drawPlatesOnSide(
                    plates = result.platesPerSide,
                    startX = plateStartX,
                    centerY = centerY,
                    maxPlateHeight = maxPlateHeight,
                    direction = -1,
                )
                drawPlatesOnSide(
                    plates = result.platesPerSide,
                    startX = plateEndX,
                    centerY = centerY,
                    maxPlateHeight = maxPlateHeight,
                    direction = 1,
                )
            }

            if (result.platesPerSide.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Por lado: ${formatPlatesList(result.platesPerSide)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.barbellAndCollars(
    centerX: Float,
    centerY: Float,
    sleeveWidth: Float,
    barWidth: Float,
    barHeight: Float,
    collarWidth: Float,
) {
    val barColor = Color(0xFF78909C)
    val collarColor = Color(0xFF546E7A)
    val sleeveColor = Color(0xFF90A4AE)

    drawRoundRect(
        color = barColor,
        topLeft = Offset(centerX - barWidth / 2, centerY - barHeight / 2),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barHeight / 2, barHeight / 2),
    )

    drawRoundRect(
        color = collarColor,
        topLeft = Offset(centerX - barWidth / 2, centerY - barHeight / 2 - 2f),
        size = Size(collarWidth, barHeight + 4f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    drawRoundRect(
        color = collarColor,
        topLeft = Offset(centerX + barWidth / 2 - collarWidth, centerY - barHeight / 2 - 2f),
        size = Size(collarWidth, barHeight + 4f),
        cornerRadius = CornerRadius(3f, 3f),
    )

    drawRoundRect(
        color = sleeveColor,
        topLeft = Offset(centerX - barWidth / 2 - sleeveWidth + 4f, centerY - 6f),
        size = Size(sleeveWidth, 12f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawRoundRect(
        color = sleeveColor,
        topLeft = Offset(centerX + barWidth / 2 - 4f, centerY - 6f),
        size = Size(sleeveWidth, 12f),
        cornerRadius = CornerRadius(4f, 4f),
    )
}

private fun DrawScope.drawPlatesOnSide(
    plates: List<Double>,
    startX: Float,
    centerY: Float,
    maxPlateHeight: Float,
    direction: Int,
) {
    var currentX = startX
    val plateThickness = 10f
    val gap = 1.5f

    for (plate in plates) {
        val height = plateHeight(plate, maxPlateHeight)
        val color = plateColor(plate)
        val x = if (direction < 0) {
            currentX - plateThickness
        } else {
            currentX
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(x, centerY - height / 2),
            size = Size(plateThickness, height),
            cornerRadius = CornerRadius(3f, 3f),
        )

        if (height > 20f) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(x, centerY - height / 2),
                size = Size(plateThickness, height),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }

        currentX += (plateThickness + gap) * direction
    }
}

private fun formatPlatesList(plates: List<Double>): String {
    if (plates.isEmpty()) return "ninguno"
    val grouped = plates.groupingBy { it }.eachCount()
    return grouped.entries
        .sortedByDescending { it.key }
        .joinToString(", ") { (weight, count) ->
            if (count > 1) "${count}×${weight}" else "$weight"
        }
}
