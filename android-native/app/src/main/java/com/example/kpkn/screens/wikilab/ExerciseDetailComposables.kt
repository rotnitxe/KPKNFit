package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.data.models.ExerciseMuscleInfo

/** Small editorial tags. They describe the selected catalog material only. */
@Composable
fun ExerciseMinimalistChipsCarousel(
    exercise: ExerciseMuscleInfo,
    modifier: Modifier = Modifier,
) {
    val metadata = exercise.decodeCatalogRichMetadata()
    val chips = buildList {
        addAll(exercise.catalogVariantChips)
        exercise.equipment?.takeIf { it.isNotBlank() }?.let { add(it) }
        metadata?.biomechanics?.movementPatternId?.let { add(it) }
    }.distinct()
    if (chips.isEmpty()) return
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { chip ->
            Surface(shape = RoundedCornerShape(4.dp), color = APRENDE_MUTED_FILL) {
                Text(chip, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
fun ExerciseTechnicalDetails(info: ExerciseMuscleInfo, modifier: Modifier = Modifier) {
    val metadata = info.decodeCatalogRichMetadata()
    val setup = metadata?.coaching?.setup.orEmpty()
    val execution = metadata?.coaching?.execution.orEmpty()
    if (metadata == null && setup.isEmpty() && execution.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Detalles técnicos", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, color = Color.White), fontWeight = FontWeight.Bold)
        HorizontalDivider(color = APRENDE_DIVIDER)
        metadata?.biomechanics?.loadMode?.let { FlatTechnicalRow("Resistencia", it) }
        metadata?.biomechanics?.resistanceProfile?.let { FlatTechnicalRow("Perfil", it) }
        setup.firstOrNull()?.let { FlatTechnicalRow("Preparación", it) }
        execution.firstOrNull()?.let { FlatTechnicalRow("Ejecución", it) }
    }
}

@Composable
private fun FlatTechnicalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
internal fun ExerciseSimilarThreeBand(
    info: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    relations: AprendeExerciseRelations = buildAprendeExerciseRelations(info, catalog),
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (relations.equivalent.isNotEmpty()) {
            SimilarBandSection("Equivalentes · misma intención", Icons.Default.SwapHoriz, relations.equivalent, onOpenExercise)
        }
        if (relations.patternVariants.isNotEmpty()) {
            SimilarBandSection("Variantes del patrón", Icons.Default.AccountTree, relations.patternVariants, onOpenExercise)
        }
        if (relations.anatomicalTransfer.isNotEmpty()) {
            SimilarBandSection("Transferencia anatómica", Icons.Default.Hub, relations.anatomicalTransfer, onOpenExercise)
        }
        if (relations.equivalent.isEmpty() && relations.patternVariants.isEmpty() && relations.anatomicalTransfer.isEmpty()) {
            Text("No hay relaciones editoriales resueltas para este artículo.", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.58f))
        }
    }
}

@Composable
private fun SimilarBandSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<AprendeSimilarItem>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.58f))
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.72f)), fontWeight = FontWeight.Bold)
        }
        items.forEach { item ->
            SimilarRow(item.exercise.name, item.rationale, onClick = { onSelect(item.exercise.id) })
        }
    }
}

@Composable
private fun SimilarRow(name: String, detail: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = APRENDE_LINK_COLOR), fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.78f), lineHeight = 17.sp)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = APRENDE_DIVIDER)
    }
}
