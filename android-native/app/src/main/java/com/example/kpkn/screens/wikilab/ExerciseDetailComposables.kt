package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.ExerciseKinship
import com.example.kpkn.domain.exercises.buildThreeBandKinships
import com.example.kpkn.domain.exercises.inferLearningCurveLabel
import com.example.kpkn.domain.exercises.inferSetupTimeLabel
import com.example.kpkn.domain.exercises.inferTransferLabel
import com.example.kpkn.domain.exercises.resolveExerciseRegion
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ExerciseMinimalistChipsCarousel(
    exercise: ExerciseMuscleInfo,
    fatigueScore: Int,
    modifier: Modifier = Modifier,
) {
    val rawSetup = remember(exercise.id) { inferSetupTimeLabel(exercise) }
    val setupText = remember(rawSetup) {
        when {
            rawSetup.contains("Muy rápido", ignoreCase = true) || rawSetup.contains("Rápido", ignoreCase = true) || rawSetup.contains("30-60", ignoreCase = true) -> "Setup rápido"
            rawSetup.contains("45-75", ignoreCase = true) || rawSetup.contains("45-90", ignoreCase = true) || rawSetup.contains("1-2 min", ignoreCase = true) || rawSetup.contains("1 min", ignoreCase = true) -> "Setup moderado"
            else -> "Setup lento"
        }
    }

    val rawCurve = remember(exercise.id) { inferLearningCurveLabel(exercise) }
    val curveText = remember(rawCurve) {
        when (rawCurve) {
            "Baja" -> "Técnica simple"
            "Alta" -> "Técnica compleja"
            else -> "Técnica intermedia"
        }
    }

    val fatigueText = remember(fatigueScore) {
        when {
            fatigueScore <= 3 -> "Poco fatigante"
            fatigueScore <= 6 -> "Fatiga moderada"
            fatigueScore <= 8 -> "Bastante fatigante"
            else -> "Muy fatigante"
        }
    }

    val regionText = remember(exercise.id) {
        when (resolveExerciseRegion(exercise)) {
            ExerciseCatalogRegion.LOWER -> "Tren inferior"
            ExerciseCatalogRegion.UPPER -> "Tren superior"
            ExerciseCatalogRegion.CORE -> "Core"
            else -> "Cuerpo completo"
        }
    }

    val chipItems = remember(setupText, curveText, fatigueText, regionText) {
        listOf(
            setupText to Color(0xFF2196F3),
            curveText to Color(0xFF9C27B0),
            fatigueText to Color(0xFFFF8F00),
            regionText to Color(0xFFE53935),
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(chipItems) { (text, color) ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
    }
}

@Composable
fun ExerciseTechnicalDetails(
    info: ExerciseMuscleInfo,
    modifier: Modifier = Modifier,
) {
    val setupLabel = remember(info.id) { inferSetupTimeLabel(info) }
    val techLabel = remember(info.id) { inferLearningCurveLabel(info) }
    val transferLabel = remember(info.id) { inferTransferLabel(info) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Detalles Técnicos",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                color = Color.White
            ),
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = Color(0xFF2C2C2C))
        
        FlatTechnicalRow("Tiempo de Setup", setupLabel)
        FlatTechnicalRow("Curva de Aprendizaje", techLabel)
        FlatTechnicalRow("Transferencia Deportiva", transferLabel)
    }
}

@Composable
private fun FlatTechnicalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
fun ExerciseSimilarThreeBand(
    info: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bands = remember(info.id) { buildThreeBandKinships(info, catalog) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (bands.lessSetup.isNotEmpty()) {
            SimilarBandSection(
                title = "Alternativas con menor setup",
                icon = Icons.Default.HourglassEmpty,
                items = bands.lessSetup,
                onSelect = onOpenExercise,
            )
        }
        if (bands.moreTransfer.isNotEmpty()) {
            SimilarBandSection(
                title = "Mayor transferencia deportiva",
                icon = Icons.Default.SwapHoriz,
                items = bands.moreTransfer,
                onSelect = onOpenExercise,
            )
        }
        if (bands.lessFatigue.isNotEmpty()) {
            SimilarBandSection(
                title = "Menor fatiga sistémica",
                icon = Icons.Default.FavoriteBorder,
                items = bands.lessFatigue,
                onSelect = onOpenExercise,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SimilarBandSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<ExerciseKinship>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    color = Color.White.copy(alpha = 0.6f)
                ),
                fontWeight = FontWeight.Bold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { kinship ->
                SimilarRow(
                    name = kinship.exercise.name,
                    detail = kinship.rationale,
                    onClick = { onSelect(kinship.exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun SimilarRow(
    name: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif,
                color = Color(0xFF29B6F6)
            ),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                color = Color.White.copy(alpha = 0.8f)
            ),
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF1A1A1A))
    }
}
