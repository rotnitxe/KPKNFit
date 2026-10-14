package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.unresolvedDiscomfortIds
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.design.WorkoutScoreRule
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * Feedback del ejercicio (KPKN LS3 §3), dentro del descanso como hoy: la calidad técnica y la intensidad con [WorkoutScoreRule] (de 1 a 10),
 * el «Fallo» y las molestias con `KpknChip`, y «Registrar feedback» como píldora. Mismos datos y mismas llamadas que antes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutPostExerciseFeedbackContent(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        feedbackExercises.forEach { ex ->
            val showPerceivedIntensity = !exerciseHasPlannedIntensity(ex)
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = ex.name,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    FeedbackScoreBlock(
                        eyebrow = "Calidad técnica",
                        value = technicalValues[ex.id] ?: 8,
                        contentLabel = "Calidad técnica de ${ex.name}",
                        onValueChange = { technicalValues[ex.id] = it },
                    )
                    if (showPerceivedIntensity) {
                        val isFailed = failureValues[ex.id] == true
                        FeedbackScoreBlock(
                            eyebrow = "Qué tan intenso fue",
                            value = (intensityValues[ex.id] ?: 8f).roundToInt(),
                            contentLabel = "Intensidad percibida de ${ex.name}",
                            onValueChange = {
                                intensityValues[ex.id] = it.toFloat()
                                if (it < 10) failureValues[ex.id] = false
                            },
                            trailing = {
                                KpknChip(
                                    text = "Fallo",
                                    selected = isFailed,
                                    onClick = {
                                        val nextVal = !isFailed
                                        failureValues[ex.id] = nextVal
                                        if (nextVal) intensityValues[ex.id] = 10f
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        val currentArticulations = remember(feedbackExercises) {
            feedbackExercises.flatMap { ex ->
                val dbInfo = resolveCatalogExerciseInfo(
                    catalogConfigurationId = ex.catalogConfigurationId,
                    exerciseDbId = ex.exerciseDbId,
                    exerciseId = ex.exerciseId,
                    exerciseName = ex.name,
                )
                (ex.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: dbInfo?.involvedMuscles.orEmpty())
                .flatMap { im -> AugeTtcEngine.articularBatteriesFor(im.muscle, im.emphasis) }
            }.distinct()
        }
        val linkedDiscomforts = remember(currentArticulations, postExerciseFeedbackByExerciseId) {
            postExerciseFeedbackByExerciseId
                .filter { (eid, _) -> feedbackExercises.none { it.id == eid } }
                .flatMap { (_, prev) ->
                    prev.unresolvedDiscomfortIds().mapNotNull { did ->
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
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Molestias previas relacionadas",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = "Reportaste estas molestias en otros ejercicios. Comparten articulación con el actual.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    linkedDiscomforts.forEach { (id, label, reportedIn) ->
                        val stillPresent = linkedStillPresent[id] ?: true
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = label,
                                style = KpknType.body,
                                color = if (stillPresent) WizardColors.text else WizardColors.textFaint,
                                textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough,
                            )
                            Text(
                                text = "Reportada en: $reportedIn",
                                style = KpknType.caption,
                                color = WizardColors.textFaint,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KpknChip(
                                    text = "Sigue",
                                    selected = stillPresent,
                                    onClick = { linkedStillPresent[id] = true },
                                )
                                KpknChip(
                                    text = "Resuelta",
                                    selected = !stillPresent,
                                    onClick = { linkedStillPresent[id] = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeedbackToggleRow(
                    text = "¿Sientes alguna molestia?",
                    open = isDiscomfortExpanded,
                    onToggle = { isDiscomfortExpanded = !isDiscomfortExpanded },
                )
                AnimatedVisibility(visible = isDiscomfortExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpknTextField(
                            value = discomfortSearchQuery.value,
                            onValueChange = { discomfortSearchQuery.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Buscar molestia",
                            leadingIcon = KpknLineIcons.Search,
                        )
                        if (filteredDiscomforts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredDiscomforts.forEach { entry ->
                                    val selected = selectedDiscomfortIds.contains(entry.id)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        KpknChip(
                                            text = entry.label,
                                            selected = selected,
                                            onClick = {
                                                if (selected) {
                                                    selectedDiscomfortIds.remove(entry.id)
                                                } else {
                                                    selectedDiscomfortIds.add(entry.id)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        KpknIconButton(
                                            icon = KpknLineIcons.Info,
                                            contentDescription = "Detalle de ${entry.label}",
                                            onClick = { infoDiscomfortEntry = entry },
                                        )
                                    }
                                }
                            }
                        } else if (discomfortSearchQuery.value.isBlank()) {
                            Text(
                                text = "Escribe para buscar molestias…",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        } else {
                            Text(
                                text = "No se encontraron resultados para \"${discomfortSearchQuery.value}\"",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        if (selectedDiscomfortIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedDiscomfortIds.forEach { id ->
                    val label = DISCOMFORT_CATALOG.find { it.id == id }?.label ?: id
                    KpknChip(
                        text = label,
                        selected = true,
                        onClick = { selectedDiscomfortIds.remove(id) },
                    )
                }
            }
        }

        KpknPrimaryPill(
            text = "Registrar feedback",
            onClick = {
                val stillLinkedIds = linkedDiscomforts.map { it.first }.filter { id ->
                    linkedStillPresent[id] != false
                }
                val resolvedIds = linkedDiscomforts.map { it.first }.filter { id ->
                    linkedStillPresent[id] == false
                }.toSet()
                val stillForCurrent = (selectedDiscomfortIds.filter { it != "none" } + stillLinkedIds)
                    .distinct()
                    .ifEmpty { listOf("none") }
                val payloads = mutableListOf<PostExerciseFeedback>()
                feedbackExercises.forEach { ex ->
                    val tech = technicalValues[ex.id] ?: 8
                    val intensity = intensityValues[ex.id]?.toDouble()
                    val failed = failureValues[ex.id] == true
                    payloads += PostExerciseFeedback(
                        exerciseId = ex.id,
                        exerciseDbId = ex.exerciseDbId ?: viewModel.canonicalExerciseKey(ex),
                        canonicalExerciseId = viewModel.canonicalExerciseKey(ex),
                        exerciseName = displayWorkoutExerciseName(ex),
                        technicalQuality = tech,
                        discomfortIds = selectedDiscomfortIds.toList().ifEmpty { listOf("none") },
                        perceivedIntensityRpe = intensity,
                        perceivedFailure = failed,
                        stillPresentDiscomfortIds = stillForCurrent,
                    )
                }
                val feedbackIds = feedbackExercises.map { it.id }.toSet()
                postExerciseFeedbackByExerciseId.forEach { (eid, prev) ->
                    if (eid in feedbackIds) return@forEach
                    val remaining = prev.unresolvedDiscomfortIds().filter { it !in resolvedIds }
                    if (remaining != prev.unresolvedDiscomfortIds()) {
                        payloads += prev.copy(
                            stillPresentDiscomfortIds = remaining.ifEmpty { listOf("none") },
                        )
                    }
                }
                viewModel.savePostExerciseFeedbacks(payloads)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    infoDiscomfortEntry?.let { entry ->
        KpknAlertDialog(
            onDismissRequest = { infoDiscomfortEntry = null },
            title = { Text(entry.label, style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.description, style = KpknType.body, color = WizardColors.text)
                    Text(
                        "Sección: ${entry.section.label}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            },
            confirmButton = {
                KpknTextAction(text = "Entendido", onClick = { infoDiscomfortEntry = null })
            },
        )
    }
}

/** Un bloque de puntuación del feedback: la etiqueta en mayúsculas, el valor «8 / 10», la regla y, si hay, un extra (el «Fallo»). */
@Composable
internal fun FeedbackScoreBlock(
    eyebrow: String,
    value: Int,
    contentLabel: String,
    onValueChange: (Int) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // La etiqueta toma el ancho sobrante y se acorta con elipsis: a 130 % la cifra no se pega a ella.
            Text(
                text = eyebrow.uppercase(Locale("es")),
                modifier = Modifier.weight(1f),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$value / 10",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
        WorkoutScoreRule(
            value = value,
            onValueChange = onValueChange,
            contentLabel = contentLabel,
        )
        trailing?.invoke()
    }
}

/** La fila «¿Sientes alguna molestia?»: texto de 48 dp como mínimo con el chevron que gira al abrir (sin giro con movimiento reducido). */
@Composable
internal fun FeedbackToggleRow(text: String, open: Boolean, onToggle: () -> Unit) {
    val reduced = wizardReducedMotion()
    val angle by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(durationMillis = 220),
        label = "feedbackDiscomfortChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                role = Role.Button
                stateDescription = if (open) "Desplegado" else "Plegado"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
        )
        Icon(
            imageVector = KpknLineIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp).rotate(angle),
            tint = WizardColors.text,
        )
    }
}
