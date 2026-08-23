package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.buildExerciseKinships
import com.example.kpkn.domain.exercises.calculateFriendlyFatigue
import com.example.kpkn.domain.exercises.inferLearningCurveLabel
import com.example.kpkn.domain.exercises.inferSetupTimeLabel
import com.example.kpkn.domain.exercises.resolveExerciseRegion
import com.example.kpkn.domain.exercises.ExerciseCatalogRegion
import com.example.kpkn.domain.exercises.resolvePrimaryMuscleLabel
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay

@Composable
internal fun ExerciseCatalogInfoDialog(
    exercise: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    associatedDiscomforts: List<Pair<String, Int>>,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenVariantFlow: ((ExerciseMuscleInfo) -> Unit)? = null,
) {
    val fatigue = remember(exercise.id) { calculateFriendlyFatigue(exercise) }
    val kinship = remember(exercise.id, catalog) { buildExerciseKinships(exercise, catalog) }
    var chipExplain by remember { mutableStateOf<Pair<String, String>?>(null) }
    var canonicalExplain by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    val richMetadata = remember(exercise.id, exercise.catalogRichMetadataJson) { exercise.decodeCatalogRichMetadata() }

    KpknGlassDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(resolvePrimaryMuscleLabel(exercise), exercise.equipment, exercise.type)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.85f))
                }
            }

            if (onOpenVariantFlow != null && !exercise.variantGroupId.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenVariantFlow(exercise)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aspectos técnicos", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                shortExerciseBlurb(exercise),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
            )

            TechnicalAspectDescriptionChips(
                exercise = exercise,
                onChipTap = { title, description -> chipExplain = title to description },
            )
            CanonicalKnowledgeChips(
                richMetadata = richMetadata,
                onChipTap = { canonicalExplain = it },
            )

            CatalogDescriptorChips(
                exercise = exercise,
                fatigueScore = fatigue.overall,
                onChipTap = { title, why -> chipExplain = title to why },
            )

            chipExplain?.let { (title, why) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(title, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.labelMedium)
                        Text(why, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                    }
                }
            }
            canonicalExplain?.let { knowledge ->
                CanonicalKnowledgeOverlay(
                    knowledge = knowledge,
                    onDismiss = { canonicalExplain = null },
                )
            }

            val muscleContributions = remember(exercise.id, exercise.involvedMuscles) {
                oneSeriesVolumeContributions(exercise)
            }
            if (muscleContributions.isNotEmpty()) {
                Text(
                    "Músculos involucrados",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                )
                muscleContributions.take(6).forEach { item ->
                    val knowledge = canonicalMuscleKnowledgeForVolumeLabel(item.muscle)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .then(if (knowledge != null) Modifier.clickable { canonicalExplain = knowledge } else Modifier),
                        shape = RoundedCornerShape(9.dp),
                        color = Color.White.copy(alpha = 0.045f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    item.muscle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    roleVolumeLabel(item.role).substringBefore(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f),
                                )
                            }
                            Text(
                                "${formatSeriesEquivalent(item.seriesEquivalent)} · ${formatVolumePercent(item.seriesEquivalent)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.80f),
                            )
                        }
                    }
                }
            }

            if (associatedDiscomforts.isNotEmpty()) {
                Text(
                    "Molestias asociadas",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                )
                associatedDiscomforts.take(4).forEach { (label, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                        Text("×$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }

            if (kinship.similar.isNotEmpty()) {
                Text(
                    "Otras opciones",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(kinship.similar.take(6), key = { it.exercise.id }) { similar ->
                        Surface(
                            modifier = Modifier
                                .width(168.dp)
                                .clickable {
                                    onDismiss()
                                    onOpenExercise(similar.exercise.id)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    similar.exercise.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    similar.rationale,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            val cues = (exercise.setupCues.orEmpty().take(1) + exercise.executionCues.orEmpty().take(1))
            if (cues.isNotEmpty()) {
                Text(
                    "Claves",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                )
                cues.forEach { cue ->
                    Text("· $cue", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TechnicalAspectDescriptionChips(
    exercise: ExerciseMuscleInfo,
    onChipTap: (String, String) -> Unit,
) {
    val options = exercise.catalogOptionAxes.orEmpty().flatMap { aspect ->
        aspect.options.map { aspect to it }
    }
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Variantes técnicas",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.92f),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { (aspect, option) ->
                Surface(
                    modifier = Modifier.clickable {
                        onChipTap(
                            option.name,
                            "Configuración exacta: ${option.name}.",
                        )
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        option.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CanonicalKnowledgeChips(
    richMetadata: com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2?,
    onChipTap: (CanonicalKnowledge) -> Unit,
) {
    val anatomy = richMetadata?.anatomy ?: return
    val jointChips = anatomy.jointInvolvement.mapNotNull { joint ->
        canonicalJointKnowledge(joint.jointId)
    }.distinctBy { it.id }
    val patternChip = canonicalPatternKnowledge(richMetadata.biomechanics.movementPatternId)
    if (jointChips.isEmpty() && patternChip == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Información canónica",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.90f),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(jointChips + listOfNotNull(patternChip)) { knowledge ->
                Surface(
                    modifier = Modifier.clickable { onChipTap(knowledge) },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        knowledge.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogDescriptorChips(
    exercise: ExerciseMuscleInfo,
    fatigueScore: Int,
    onChipTap: (title: String, why: String) -> Unit,
) {
    val setupRaw = remember(exercise.id) { inferSetupTimeLabel(exercise) }
    val setup = when {
        setupRaw.contains("Muy rápido", ignoreCase = true) || setupRaw.contains("Rápido", ignoreCase = true) ->
            "Setup rápido" to "Poco tiempo de montaje: entra fácil entre series o en circuitos."
        setupRaw.contains("45", ignoreCase = true) || setupRaw.contains("1-2", ignoreCase = true) || setupRaw.contains("1 min", ignoreCase = true) ->
            "Setup moderado" to "Requiere ajustar equipo o posición; cuenta algo de tiempo de transición."
        else ->
            "Setup lento" to "Montaje o fijación más exigente; mejor al inicio del bloque."
    }
    val curveRaw = remember(exercise.id) { inferLearningCurveLabel(exercise) }
    val curve = when (curveRaw) {
        "Baja" -> "Técnica simple" to "Fácil de ejecutar con buena forma; apto para progresar carga pronto."
        "Alta" -> "Técnica compleja" to "Pide práctica y control; prioriza calidad antes de intensidad."
        else -> "Técnica intermedia" to "Curva media: útil con cues claros y carga controlada."
    }
    val fatigue = when {
        fatigueScore <= 3 -> "Poco fatigante" to "Bajo coste sistémico por serie; encaja al final o en volumen alto."
        fatigueScore <= 6 -> "Fatiga moderada" to "Coste medio; equilibra volumen y descanso entre series."
        fatigueScore <= 8 -> "Bastante fatigante" to "Alto impacto local/sistémico; limita series cercanas al fallo."
        else -> "Muy fatigante" to "Muy exigente; suele ir al inicio y con menos volumen."
    }
    val region = when (resolveExerciseRegion(exercise)) {
        ExerciseCatalogRegion.LOWER -> "Tren inferior" to "Enfoque en piernas/cadena inferior."
        ExerciseCatalogRegion.UPPER -> "Tren superior" to "Enfoque en torso y cintura escapular."
        ExerciseCatalogRegion.CORE -> "Core" to "Estabilidad y control del tronco."
        else -> "Cuerpo completo" to "Patrón multi-región; aporta fatiga global."
    }
    val chips = listOf(setup, curve, fatigue, region)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(chips) { (label, why) ->
            Surface(
                modifier = Modifier.clickable { onChipTap(label, why) },
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

private fun shortExerciseBlurb(exercise: ExerciseMuscleInfo): String {
    val fromDescription = exercise.description?.trim()?.takeIf { it.isNotBlank() }
    if (fromDescription != null) {
        return if (fromDescription.length <= 160) fromDescription else fromDescription.take(157).trimEnd() + "…"
    }
    val primary = resolvePrimaryMuscleLabel(exercise) ?: "músculo objetivo"
    val equipment = exercise.equipment?.takeIf { it.isNotBlank() } ?: "carga libre"
    val type = exercise.type?.takeIf { it.isNotBlank() } ?: "patrón de fuerza"
    return "Trabajo de $primary con $equipment ($type). Enfócate en rango controlado y progresión de carga."
}
