package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
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
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
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

@Composable
internal fun WorkoutPostExerciseFeedbackContent(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    sessionAccentColor: Color,
    viewModel: WorkoutViewModel,
) {
    PostExerciseFeedbackPanel(
        feedbackExercises = feedbackExercises,
        postExerciseFeedbackByExerciseId = postExerciseFeedbackByExerciseId,
        sessionAccentColor = sessionAccentColor,
        canonicalKeyOf = { exercise -> viewModel.canonicalExerciseKey(exercise) },
        onSave = { payloads -> viewModel.savePostExerciseFeedbacks(payloads) },
    )
}

/**
 * Panel del feedback tras el ejercicio (LR1c1): la misma lógica de siempre con las piezas de la piel nueva. Recibe la clave canónica y
 * el guardado como lambdas para poder pintarlo en la hoja de render sin el ViewModel. [initialDiscomfortQuery] y [initiallyExpanded]
 * solo sirven para esa hoja; en la app van por defecto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostExerciseFeedbackPanel(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    sessionAccentColor: Color,
    canonicalKeyOf: (Exercise) -> String,
    onSave: (List<PostExerciseFeedback>) -> Unit,
    initialDiscomfortQuery: String = "",
    initiallyExpanded: Boolean = false,
) {
    val discomfortSearchQuery = remember { mutableStateOf(initialDiscomfortQuery) }
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
    var isDiscomfortExpanded by remember { mutableStateOf(initiallyExpanded) }

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

            KpknModule(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        ex.name,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )

                    Text(
                        "Calidad técnica".uppercase(Locale("es")),
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
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
                            colors = feedbackSliderColors(),
                        )
                        FeedbackValuePill(text = "$techVal / 10")
                    }

                    if (showPerceivedIntensity) {
                        Text(
                            "Qué tan intenso fue".uppercase(Locale("es")),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
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
                                colors = feedbackSliderColors(),
                            )
                            KpknChip(
                                text = "Fallo",
                                selected = isFailed,
                                onClick = {
                                    val nextVal = !isFailed
                                    failureValues[ex.id] = nextVal
                                    if (nextVal) intensityValues[ex.id] = 10f
                                },
                            )
                        }
                        Text(
                            "${(intensityValues[ex.id] ?: 8f).roundToInt()} / 10",
                            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                            color = WizardColors.textMuted,
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
            // Aviso en `ok`: relleno del verde al 8 % y filete al 24 % (LR1c1).
            KpknModule(
                modifier = Modifier.fillMaxWidth(),
                accent = SymbolPalette.ok,
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = SymbolPalette.ok, modifier = Modifier.size(18.dp))
                        Text("Molestias previas relacionadas", style = KpknType.bodyStrong, color = WizardColors.text)
                    }
                    Text(
                        "Reportaste estas molestias en otros ejercicios. Comparten articulación con el actual.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    linkedDiscomforts.forEach { (id, label, reportedIn) ->
                        val stillPresent = linkedStillPresent[id] ?: true
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    label,
                                    style = KpknType.bodyStrong,
                                    color = if (stillPresent) WizardColors.text else WizardColors.textFaint,
                                    textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough,
                                )
                                Text("Reportada en: $reportedIn", style = KpknType.caption, color = WizardColors.textFaint)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

        val headerInteraction = remember { MutableInteractionSource() }
        KpknModule(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = headerInteraction,
                            indication = null,
                            role = Role.Button,
                        ) { isDiscomfortExpanded = !isDiscomfortExpanded }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = sessionAccentColor)
                        Text("¿Sientes alguna molestia?", style = KpknType.bodyStrong, color = WizardColors.text)
                    }
                    Icon(
                        if (isDiscomfortExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = WizardColors.textMuted,
                    )
                }

                AnimatedVisibility(visible = isDiscomfortExpanded) {
                    Column(modifier = Modifier.padding(14.dp).padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpknTextField(
                            value = discomfortSearchQuery.value,
                            onValueChange = { discomfortSearchQuery.value = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "Buscar molestia",
                            leadingIcon = Icons.Default.Search,
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
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
                                            )
                                        }
                                        KpknIconButton(
                                            icon = KpknLineIcons.Info,
                                            contentDescription = "Detalle",
                                            onClick = { infoDiscomfortEntry = entry },
                                        )
                                    }
                                }
                            }
                        } else if (discomfortSearchQuery.value.isBlank()) {
                            Text(
                                "Escribe para buscar molestias…",
                                style = KpknType.caption,
                                color = WizardColors.textFaint,
                            )
                        } else {
                            Text(
                                "No se encontraron resultados para \"${discomfortSearchQuery.value}\"",
                                style = KpknType.caption,
                                color = WizardColors.textFaint,
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
                    DiscomfortSelectedTag(
                        label = label,
                        onRemove = { selectedDiscomfortIds.remove(id) },
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
                        exerciseDbId = ex.exerciseDbId ?: canonicalKeyOf(ex),
                        canonicalExerciseId = canonicalKeyOf(ex),
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
                onSave(payloads)
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

/** Colores de los sliders de la piel nueva (LR1c1): pista blanca al 8 %, progreso y pulgar en tinta. */
@Composable
private fun feedbackSliderColors() = SliderDefaults.colors(
    thumbColor = WizardColors.text,
    activeTrackColor = WizardColors.text,
    inactiveTrackColor = Color.White.copy(alpha = 0.08f),
    activeTickColor = WizardColors.ctaContent,
    inactiveTickColor = Color.White.copy(alpha = 0.20f),
)

/** Cifra «8 / 10» (LR1c1): píldora `inset` con la cifra tabular en tinta. */
@Composable
private fun FeedbackValuePill(text: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}

/**
 * Molestia elegida (LR1c1): píldora de tinta con el nombre en negro y una X para quitarla. Solo la X quita la molestia, como antes.
 * La usan este panel y el cajón de «Reportar molestias».
 */
@Composable
internal fun DiscomfortSelectedTag(
    label: String,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(WizardColors.text, KpknShape.pill)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.chip,
            color = WizardColors.ctaContent,
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Close,
                contentDescription = "Quitar",
                modifier = Modifier.size(14.dp),
                tint = WizardColors.ctaContent,
            )
        }
    }
}
