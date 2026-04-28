package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Detalles técnicos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TechDetailBadge(
                icon = Icons.Default.Timer,
                title = "Set-up",
                value = setupLabel,
                modifier = Modifier.weight(1f),
            )
            TechDetailBadge(
                icon = Icons.Default.Settings,
                title = "Técnica",
                value = techLabel,
                modifier = Modifier.weight(1f),
            )
            TechDetailBadge(
                icon = Icons.Default.SportsSoccer,
                title = "Transferencia",
                value = transferLabel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TechDetailBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Ejercicios similares", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (bands.lessSetup.isNotEmpty()) {
            SimilarBandSection(
                title = "Ahorra tiempo en setup",
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
                title = "Menor fatiga",
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { kinship ->
                SimilarChip(
                    name = kinship.exercise.name,
                    detail = kinship.rationale,
                    onClick = { onSelect(kinship.exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun SimilarChip(
    name: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
