package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.workout.VoiceHistoryEntry
import com.example.kpkn.domain.workout.VoiceHistoryStatus
import com.example.kpkn.domain.workout.VoiceHistoryValues
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val voiceTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val voiceModuleShape = RoundedCornerShape(16.dp)

/**
 * Superficie de conversación de Modo Voz (iteración 02).
 *
 * No graba audio ni registra series por sí sola: muestra las capturas confirmadas,
 * permite corregirlas y deja el registro en el mismo camino que una serie manual.
 */
@Composable
internal fun VoiceConversationSurface(
    entries: List<VoiceHistoryEntry>,
    voiceEnabled: Boolean,
    captureModeLabel: String,
    sessionName: String,
    currentExerciseName: String,
    currentSetLabel: String,
    notice: String?,
    onClose: () -> Unit,
    onTogglePause: () -> Unit,
    onDisable: () -> Unit,
    onListenExample: () -> Unit,
    onConfirmCorrection: (String, Double, Int, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var filter by remember { mutableStateOf(VoiceHistoryFilter.ALL) }
    var editing by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    var auditing by remember { mutableStateOf<VoiceHistoryEntry?>(null) }
    val pendingCount = entries.count { it.status == VoiceHistoryStatus.REVIEW }
    val visible = when (filter) {
        VoiceHistoryFilter.ALL -> entries
        VoiceHistoryFilter.REVIEW -> entries.filter { it.status == VoiceHistoryStatus.REVIEW }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("voice-conversation-surface"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MODO VOZ", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text(captureModeLabel, style = KpknType.title, color = WizardColors.text)
            }
            KpknIconButton(KpknLineIcons.Close, "Cerrar conversación de voz", onClose)
        }
        VoiceListeningRow(
            voiceEnabled = voiceEnabled,
            exerciseName = currentExerciseName,
            setLabel = currentSetLabel,
            onTogglePause = onTogglePause,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VoiceTab(
                text = "Conversación",
                selected = filter == VoiceHistoryFilter.ALL,
                onClick = { filter = VoiceHistoryFilter.ALL },
                modifier = Modifier.weight(1f).testTag("voice-tab-all"),
            )
            VoiceTab(
                text = "Por revisar · $pendingCount",
                selected = filter == VoiceHistoryFilter.REVIEW,
                onClick = { filter = VoiceHistoryFilter.REVIEW },
                modifier = Modifier.weight(1f).testTag("voice-tab-review"),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (sessionName.isBlank()) "HOY" else "HOY · ${sessionName.uppercase()}",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            if (visible.isEmpty()) {
                Text(
                    "Todas las capturas están revisadas.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("voice-history-empty"),
                )
            }
            visible.forEach { entry ->
                VoiceHistoryCard(
                    entry = entry,
                    onEdit = { editing = entry; auditing = null },
                    onReview = { editing = entry },
                    onAudit = { auditing = entry },
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (notice != null) {
                Text(
                    notice,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("voice-history-notice"),
                )
            }
            HeaderSheetAction(
                "Escuchar frase de ejemplo",
                onListenExample,
                Modifier.fillMaxWidth().testTag("voice-example"),
                enabled = voiceEnabled,
            )
            Text("Puedes revisar y corregir cada captura.", style = KpknType.caption, color = WizardColors.textMuted)
            Text(
                "Desactivar modo voz",
                style = KpknType.action,
                color = WizardColors.textMuted,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onDisable)
                    .padding(horizontal = 12.dp)
                    .testTag("voice-disable"),
            )
        }
    }

    editing?.let { entry ->
        VoiceCorrectionSheet(
            entry = entry,
            onDismiss = { editing = null },
            onConfirm = { weight, reps, rir ->
                editing = null
                onConfirmCorrection(entry.id, weight, reps, rir)
            },
        )
    }
    auditing?.let { entry ->
        VoiceAuditSheet(entry = entry, onDismiss = { auditing = null })
    }
}

private enum class VoiceHistoryFilter { ALL, REVIEW }

@Composable
private fun VoiceListeningRow(
    voiceEnabled: Boolean,
    exerciseName: String,
    setLabel: String,
    onTogglePause: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .background(KpknSurface.module, voiceModuleShape)
            .border(1.dp, KpknSurface.moduleBorder, voiceModuleShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("voice-listening"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceWaveform(active = voiceEnabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (voiceEnabled) "Te escucho" else "En pausa",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.testTag("voice-listening-state"),
            )
            Text(
                "$exerciseName · $setLabel",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        KpknIconButton(
            icon = if (voiceEnabled) KpknLineIcons.Pause else KpknLineIcons.Mic,
            contentDescription = if (voiceEnabled) "Pausar voz" else "Reanudar voz",
            onClick = onTogglePause,
            modifier = Modifier.testTag("voice-pause"),
        )
    }
}

@Composable
private fun VoiceWaveform(active: Boolean) {
    Row(
        Modifier.height(34.dp).width(74.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(25) { index ->
            val bar = 8 + ((index * 7) % 26)
            Box(
                Modifier.width(2.dp).height(bar.dp)
                    .background(
                        WizardColors.text.copy(alpha = if (active) 0.75f else 0.25f),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

@Composable
private fun VoiceTab(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().height(36.dp)
                .background(if (selected) KpknSurface.modulePressed else Color.Transparent, RoundedCornerShape(18.dp))
                .border(1.dp, if (selected) KpknSurface.moduleBorder else Color.Transparent, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = KpknType.action,
                color = if (selected) WizardColors.text else WizardColors.textMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun VoiceHistoryCard(
    entry: VoiceHistoryEntry,
    onEdit: () -> Unit,
    onReview: () -> Unit,
    onAudit: () -> Unit,
) {
    val review = entry.status == VoiceHistoryStatus.REVIEW
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "TÚ · ${voiceClock(entry.createdAtEpochMs)}",
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        Text("«${entry.transcript}»", style = KpknType.body, color = WizardColors.text)
        Column(
            Modifier.fillMaxWidth()
                .background(if (review) KpknSurface.inset else KpknSurface.module, voiceModuleShape)
                .border(1.dp, KpknSurface.moduleBorder, voiceModuleShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        review -> "Revisar antes de registrar"
                        entry.revision > 1 -> "Corregida · v${entry.revision}"
                        else -> "Serie registrada"
                    },
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f).testTag("voice-entry-status-${entry.id}"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    voiceValuesLine(entry.values),
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f).testTag("voice-entry-values-${entry.id}"),
                )
                Text(
                    entry.values.intensityValue?.let { "RIR ${voiceNumber(it)}" } ?: "Falta RIR",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("voice-entry-rir-${entry.id}"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${entry.exerciseName} · serie ${entry.setIdx + 1}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Editar",
                    style = KpknType.action,
                    color = WizardColors.textMuted,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 8.dp)
                        .testTag("voice-entry-edit-${entry.id}"),
                )
            }
            if (review) {
                HeaderSheetAction(
                    "Revisar captura",
                    onReview,
                    Modifier.fillMaxWidth().testTag("voice-entry-review-${entry.id}"),
                    primary = true,
                )
            }
            entry.audit?.let { audit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Antes: ${voiceValuesLine(audit.previous)} · ${voiceRir(audit.previous)}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Ver cambios",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = onAudit)
                            .padding(horizontal = 8.dp)
                            .testTag("voice-entry-audit-${entry.id}"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoiceCorrectionSheet(
    entry: VoiceHistoryEntry,
    onDismiss: () -> Unit,
    onConfirm: (Double, Int, Double) -> Unit,
) {
    var weight by remember(entry.id) { mutableStateOf(entry.values.weightKg?.let(::voiceNumber).orEmpty()) }
    var metric by remember(entry.id) {
        mutableStateOf((entry.values.reps ?: entry.values.seconds)?.toString().orEmpty())
    }
    var rir by remember(entry.id) { mutableStateOf(entry.values.intensityValue?.let(::voiceNumber).orEmpty()) }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    fun submit() {
        val parsedWeight = weight.trim().replace(',', '.').toDoubleOrNull()
        val parsedMetric = metric.trim().toIntOrNull()
        val parsedRir = rir.trim().replace(',', '.').toDoubleOrNull()
        if (parsedWeight == null || parsedMetric == null || parsedRir == null ||
            parsedWeight < 0 || parsedMetric <= 0 || parsedRir < 0 || parsedRir > 10
        ) {
            error = "Completa carga, repeticiones y RIR entre 0 y 10."
        } else {
            onConfirm(parsedWeight, parsedMetric, parsedRir)
        }
    }
    KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, onDismissRequest = onDismiss) {
        HeaderSheetFrame(
            title = "Corregir captura",
            footer = {
                HeaderSheetAction(
                    if (entry.status == VoiceHistoryStatus.REVIEW) "Confirmar y registrar" else "Guardar corrección",
                    ::submit,
                    Modifier.fillMaxWidth().testTag("voice-correction-confirm"),
                    primary = true,
                    multiline = true,
                )
            },
            body = {
                Text("SE ESCUCHÓ", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text("«${entry.transcript}»", style = KpknType.body, color = WizardColors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = "Carga · kg",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("voice-correction-weight"),
                    )
                    KpknTextField(
                        value = metric,
                        onValueChange = { metric = it },
                        label = voiceMetricLabel(entry.values),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("voice-correction-reps"),
                    )
                }
                KpknTextField(
                    value = rir,
                    onValueChange = { rir = it },
                    label = "RIR realizado",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("voice-correction-rir"),
                )
                Text(
                    "Corregir actualiza esta captura. No crea otra serie.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                if (error != null) {
                    Text(
                        error!!,
                        style = KpknType.caption,
                        color = WizardColors.danger,
                        modifier = Modifier.testTag("voice-correction-error"),
                    )
                }
            },
        )
    }
}

@Composable
internal fun VoiceAuditSheet(entry: VoiceHistoryEntry, onDismiss: () -> Unit) {
    val audit = entry.audit ?: return
    KpknSheet(backdropScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Veil, additionalGlassScrim = com.example.kpkn.screens.workout.components.LiveSheetTokens.Panel, onDismissRequest = onDismiss) {
        HeaderSheetFrame(
            title = "Historial de correcciones",
            footer = { HeaderSheetAction("Cerrar", onDismiss, Modifier.fillMaxWidth(), primary = true) },
            body = {
                Text(
                    "La transcripción original se conserva.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Text("ORIGINAL", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text("«${entry.transcript}»", style = KpknType.body, color = WizardColors.text)
                Text("VALOR ANTERIOR", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text(voiceAuditValues(audit.previous), style = KpknType.body, color = WizardColors.text)
                Text("ACTUAL · V${entry.revision}", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text(voiceAuditValues(audit.next), style = KpknType.body, color = WizardColors.text)
            },
        )
    }
}

private fun voiceMetricLabel(values: VoiceHistoryValues): String = when {
    values.reps != null -> "Repeticiones"
    values.seconds != null -> "Segundos"
    else -> "Repeticiones"
}

private fun voiceAuditValues(values: VoiceHistoryValues): String =
    "${voiceValuesLine(values)} · ${voiceRir(values)}"

private fun voiceRir(values: VoiceHistoryValues): String =
    values.intensityValue?.let { "RIR ${voiceNumber(it)}" } ?: "Falta RIR"

private fun voiceValuesLine(values: VoiceHistoryValues): String {
    val weight = values.weightKg?.let { "${voiceNumber(it)} kg" } ?: "— kg"
    val metric = when {
        values.reps != null -> "${values.reps} reps"
        values.seconds != null -> "${values.seconds} s"
        values.distanceKm != null -> "${voiceNumber(values.distanceKm)} km"
        else -> "— reps"
    }
    return "$weight × $metric"
}

private fun voiceNumber(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun voiceClock(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(voiceTimeFormat)
