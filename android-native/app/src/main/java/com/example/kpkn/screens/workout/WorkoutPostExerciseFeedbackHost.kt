package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.auge.AugeTtcEngine
import java.util.Locale
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog

internal fun buildFeedbackExercisesForTarget(
    postExerciseTarget: Exercise?,
    visibleExercises: List<Exercise>,
): List<Exercise> {
    val supersetId = postExerciseTarget?.supersetGroupRefOrLegacyId()
    return if (!supersetId.isNullOrBlank()) {
        visibleExercises.filter { it.supersetGroupRefOrLegacyId() == supersetId }
    } else {
        listOfNotNull(postExerciseTarget)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutPostExerciseFeedbackContent(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    sessionAccentColor: Color,
    viewModel: WorkoutViewModel,
) {
    val discomfortSearchQuery = remember { mutableStateOf("") }
    val selectedDiscomfortIds = remember {
        mutableStateListOf<String>().apply {
            feedbackExercises.forEach { ex ->
                val hist = postExerciseFeedbackByExerciseId[ex.id]
                if (hist != null && hist.discomfortIds.isNotEmpty()) {
                    val histIds = hist.discomfortIds.filter { it != "none" }
                    histIds.forEach { id -> if (!contains(id)) add(id) }
                }
            }
        }
    }
    var infoDiscomfortEntry by remember { mutableStateOf<DiscomfortCatalogEntry?>(null) }
    var isDiscomfortExpanded by remember { mutableStateOf(false) }

    val technicalValues = remember {
        mutableStateMapOf<String, Int>().apply {
            feedbackExercises.forEach { ex ->
                val hist = postExerciseFeedbackByExerciseId[ex.id]
                put(ex.id, hist?.technicalQuality?.coerceIn(1, 10) ?: 8)
            }
        }
    }

    val intensityValues = remember {
        mutableStateMapOf<String, Float>().apply {
            feedbackExercises.forEach { ex ->
                val hist = postExerciseFeedbackByExerciseId[ex.id]
                put(ex.id, (hist?.perceivedIntensityRpe ?: 8.0).toFloat().coerceIn(1f, 10f))
            }
        }
    }

    val failureValues = remember {
        mutableStateMapOf<String, Boolean>().apply {
            feedbackExercises.forEach { ex ->
                val hist = postExerciseFeedbackByExerciseId[ex.id]
                put(ex.id, hist?.perceivedFailure == true)
            }
        }
    }

    val filteredDiscomforts = remember(discomfortSearchQuery.value) {
        val normalized = discomfortSearchQuery.value.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            emptyList()
        } else {
            DISCOMFORT_CATALOG
                .filter { entry ->
                    entry.label.lowercase(Locale.ROOT).contains(normalized) ||
                        entry.description.lowercase(Locale.ROOT).contains(normalized)
                }
                .sortedBy { it.label }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        feedbackExercises.forEach { ex ->
            val showPerceivedIntensity = !exerciseHasPlannedIntensity(ex)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        ex.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )

                    Text(
                        "Calidad técnica",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val techVal = technicalValues[ex.id] ?: 8
                        Slider(
                            value = techVal.toFloat(),
                            onValueChange = { technicalValues[ex.id] = it.toInt().coerceIn(1, 10) },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = sessionAccentColor,
                                activeTrackColor = sessionAccentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                            ),
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = sessionAccentColor.copy(alpha = 0.2f),
                        ) {
                            Text(
                                "$techVal / 10",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = sessionAccentColor,
                            )
                        }
                    }

                    if (showPerceivedIntensity) {
                        Text(
                            "Qué tan intenso fue",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val intensityVal = intensityValues[ex.id] ?: 8f
                            val isFailed = failureValues[ex.id] == true
                            Slider(
                                value = intensityVal,
                                onValueChange = {
                                    intensityValues[ex.id] = it.coerceIn(1f, 10f)
                                    if (it < 10f) failureValues[ex.id] = false
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = sessionAccentColor,
                                    activeTrackColor = sessionAccentColor,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                ),
                            )
                            FilterChip(
                                selected = isFailed,
                                onClick = {
                                    val nextVal = !isFailed
                                    failureValues[ex.id] = nextVal
                                    if (nextVal) intensityValues[ex.id] = 10f
                                },
                                label = { Text("Fallo") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sessionAccentColor.copy(alpha = 0.25f),
                                    selectedLabelColor = sessionAccentColor,
                                ),
                            )
                        }
                        Text(
                            "${(intensityValues[ex.id] ?: 8f).roundToInt()} / 10",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }

        val currentArticulations = remember(feedbackExercises) {
            feedbackExercises.flatMap { ex ->
                val dbInfo = EXERCISE_DATABASE_BY_ID[ex.exerciseDbId ?: ex.exerciseId]
                dbInfo?.involvedMuscles.orEmpty()
                    .flatMap { im -> AugeTtcEngine.MUSCLE_TO_ARTICULAR[im.muscle].orEmpty() }
            }.distinct()
        }
        val linkedDiscomforts = remember(currentArticulations, postExerciseFeedbackByExerciseId) {
            postExerciseFeedbackByExerciseId
                .filter { (eid, _) -> feedbackExercises.none { it.id == eid } }
                .flatMap { (_, prev) ->
                    prev.discomfortIds.filter { it != "none" }.mapNotNull { did ->
                        val entry = DISCOMFORT_CATALOG_BY_ID[did] ?: return@mapNotNull null
                        val shared = entry.relatedArticular.firstOrNull { it in currentArticulations }
                        if (shared != null) Triple(did, entry.label, prev.exerciseName) else null
                    }
                }.distinctBy { it.first }
        }
        val linkedStillPresent = remember { mutableStateMapOf<String, Boolean>() }

        if (linkedDiscomforts.isNotEmpty()) {
            LaunchedEffect(linkedDiscomforts) {
                linkedDiscomforts.forEach { (id, _, _) ->
                    if (id !in linkedStillPresent) linkedStillPresent[id] = true
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A2F)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF388E3C).copy(alpha = 0.3f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                        Text("Molestias previas relacionadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("Reportaste estas molestias en otros ejercicios. Comparten articulación con el actual.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    linkedDiscomforts.forEach { (id, label, reportedIn) ->
                        val stillPresent = linkedStillPresent[id] ?: true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = if (stillPresent) Color.White else Color.White.copy(alpha = 0.5f), textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough)
                                Text("Reportada en: $reportedIn", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = stillPresent,
                                    onClick = { linkedStillPresent[id] = true },
                                    label = { Text("Sigue", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF388E3C).copy(alpha = 0.3f),
                                        selectedLabelColor = Color(0xFF81C784),
                                        containerColor = Color(0xFF2A2A2A),
                                        labelColor = Color.White.copy(alpha = 0.5f),
                                    ),
                                )
                                FilterChip(
                                    selected = !stillPresent,
                                    onClick = { linkedStillPresent[id] = false },
                                    label = { Text("Resuelta", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF616161).copy(alpha = 0.3f),
                                        selectedLabelColor = Color.White.copy(alpha = 0.6f),
                                        containerColor = Color(0xFF2A2A2A),
                                        labelColor = Color.White.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDiscomfortExpanded = !isDiscomfortExpanded }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = sessionAccentColor)
                        Text("¿Sientes alguna molestia?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Icon(
                        if (isDiscomfortExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                    )
                }

                AnimatedVisibility(visible = isDiscomfortExpanded) {
                    Column(modifier = Modifier.padding(14.dp).padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = discomfortSearchQuery.value,
                            onValueChange = { discomfortSearchQuery.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Buscar molestia") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = sessionAccentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = Color.White.copy(alpha = 0.7f),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.03f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            ),
                        )

                        if (filteredDiscomforts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredDiscomforts.forEach { entry ->
                                    val selected = selectedDiscomfortIds.contains(entry.id)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                if (selected) {
                                                    selectedDiscomfortIds.remove(entry.id)
                                                } else {
                                                    selectedDiscomfortIds.add(entry.id)
                                                }
                                            },
                                            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = { infoDiscomfortEntry = entry }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Info, contentDescription = "Detalle", modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        } else if (discomfortSearchQuery.value.isBlank()) {
                            Text(
                                "Escribe para buscar molestias...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        } else {
                            Text(
                                "No se encontraron resultados para \"${discomfortSearchQuery.value}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }

        if (selectedDiscomfortIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                selectedDiscomfortIds.forEach { id ->
                    val entry = DISCOMFORT_CATALOG.find { it.id == id }
                    val label = entry?.label ?: id
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = sessionAccentColor.copy(alpha = 0.2f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = sessionAccentColor,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Quitar",
                                modifier = Modifier.size(14.dp).clickable { selectedDiscomfortIds.remove(id) },
                                tint = sessionAccentColor,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                feedbackExercises.forEach { ex ->
                    val tech = technicalValues[ex.id] ?: 8
                    val intensity = intensityValues[ex.id]?.toDouble()
                    val failed = failureValues[ex.id] == true
                    viewModel.savePostExerciseFeedback(
                        PostExerciseFeedback(
                            exerciseId = ex.id,
                            exerciseName = ex.name,
                            technicalQuality = tech,
                            discomfortIds = selectedDiscomfortIds.toList().ifEmpty { listOf("none") },
                            perceivedIntensityRpe = intensity,
                            perceivedFailure = failed,
                        ),
                    )
                }
                viewModel.dismissPostExerciseSheet()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = sessionAccentColor),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text("Registrar feedback", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }

    infoDiscomfortEntry?.let { entry ->
        KpknAlertDialog(
            onDismissRequest = { infoDiscomfortEntry = null },
            title = { Text(entry.label, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Sección: ${entry.section.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDiscomfortEntry = null }) { Text("Entendido") }
            },
        )
    }
}
