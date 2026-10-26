package com.example.kpkn.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

private val ReadinessInk = WizardColors.text
private val ReadinessMuted = WizardColors.textMuted
private val ReadinessFaint = WizardColors.textFaint

/**
 * «Antes de entrenar»: estado de llegada del día. Se guarda como observación de bienestar y no
 * cambia cargas ni series. Sin scroll: la hoja mide lo que mide su contenido.
 */
@Composable
internal fun WorkoutReadinessSheet(
    session: Session,
    weekLabel: String?,
    initial: DailyWellbeingLog?,
    previousDiscomforts: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (DailyWellbeingLog) -> Unit,
    saving: Boolean = false,
    error: String? = null,
    voiceEnabled: Boolean = false,
    captureMode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
    onVoiceMode: (VoiceCaptureMode) -> Unit = {},
    onContext: () -> Unit = {},
) {
    val context = LocalContext.current
    var energy by rememberSaveable(initial) { mutableStateOf(initial?.energy) }
    var sleep by rememberSaveable(initial) { mutableStateOf(initial?.sleepQuality?.takeIf { "sleep" in initial.capturedFields }) }
    var stress by rememberSaveable(initial) { mutableStateOf(initial?.stressLevel?.takeIf { "stress" in initial.capturedFields }) }
    var doms by remember(initial) { mutableStateOf(initial?.muscleDoms ?: emptyMap()) }
    var discomforts by remember(initial) { mutableStateOf(initial?.preWorkoutDiscomforts ?: emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var voiceMenu by remember { mutableStateOf(false) }
    var pendingVoiceMode by rememberSaveable { mutableStateOf<VoiceCaptureMode?>(null) }
    var discomfortTouched by rememberSaveable { mutableStateOf(false) }

    val muscles = remember(session) { readinessMuscles(session) }
    val applyVoice: (VoiceCaptureMode) -> Unit = { mode ->
        onVoiceMode(mode)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingVoiceMode?.let(applyVoice)
        pendingVoiceMode = null
    }

    KpknSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        dismissible = !saving,
        stableHeightFraction = 0.94f,
        maxHeightFraction = 0.94f,
        hazeState = remember { HazeState() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 8.dp)
                .testTag("readiness-sheet"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("ANTES DE ENTRENAR", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = ReadinessFaint)
            AutofitTitle("¿Cómo llegas hoy?")
            Text(
                text = listOfNotNull(session.name, weekLabel).joinToString(" · "),
                style = KpknType.caption.copy(fontSize = 12.sp),
                color = ReadinessMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text("Notas y disciplina", style = KpknType.action, color = ReadinessMuted,
                modifier = Modifier.clickable(onClick = onContext).testTag("readiness-context"))
            ScaleRow("ENERGÍA", energy, 0..10) { energy = it }
            ScaleRow("SUEÑO", sleep, 1..5) { sleep = it }
            ScaleRow("ESTRÉS", stress, 1..5) { stress = it }

            DomsGrid(
                muscles = muscles,
                values = doms,
                onChange = { muscle, value -> doms = doms + (muscle to value) },
            )

            DiscomfortPicker(
                selected = discomforts,
                frequent = previousDiscomforts,
                query = query,
                onQuery = { query = it },
                onToggle = { id ->
                    discomfortTouched = true
                    discomforts = if (id == "none") {
                        if (discomforts == listOf("none")) emptyList() else listOf("none")
                    } else {
                        val current = discomforts.filter { it != "none" }
                        if (id in current) current - id else current + id
                    }
                },
            )

            error?.let { Text(it, style = KpknType.caption, color = ReadinessInk) }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    val voiceOn = voiceEnabled
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, ReadinessInk.copy(alpha = 0.20f), RoundedCornerShape(24.dp))
                            .clickable { voiceMenu = true }
                            .padding(horizontal = 16.dp)
                            .testTag("readiness-voice"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (voiceOn) voiceLabel(captureMode) else "Habilitar el modo de voz",
                            style = KpknType.action.copy(fontSize = 13.sp),
                            color = ReadinessInk,
                        )
                    }
                    DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                        listOf(
                            VoiceCaptureMode.HANDS_FREE to "Manos libres",
                            VoiceCaptureMode.MUSIC to "Con música",
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = KpknType.body, color = ReadinessInk) },
                                onClick = {
                                    voiceMenu = false
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                        PackageManager.PERMISSION_GRANTED
                                    if (granted) applyVoice(mode) else {
                                        pendingVoiceMode = mode
                                        permission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ReadinessInk)
                        .clickable(enabled = !saving) {
                            if (energy == null && sleep == null && stress == null && doms.isEmpty() && !discomfortTouched) {
                                onDismiss()
                                return@clickable
                            }
                            onConfirm(
                                DailyWellbeingLog(
                                    id = "wellbeing-${LocalDate.now()}",
                                    date = LocalDate.now().toString(),
                                    sleepQuality = sleep ?: 3,
                                    stressLevel = stress ?: 3,
                                    energy = energy,
                                    doms = initial?.doms ?: 1,
                                    muscleDoms = doms,
                                    preWorkoutDiscomforts = discomforts,
                                    source = WellbeingSource.DAILY_CHECK_IN,
                                    capturedFields = buildSet {
                                        if (energy != null) add("energy")
                                        if (sleep != null) add("sleep")
                                        if (stress != null) add("stress")
                                        if (doms.isNotEmpty()) add("muscleDoms")
                                        if (discomfortTouched) add("discomforts")
                                    },
                                ),
                            )
                        }
                        .testTag("readiness-confirm"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", style = KpknType.title.copy(fontSize = 26.sp), color = WizardColors.background)
                }
            }
        }
    }
}

@Composable
private fun AutofitTitle(text: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = KpknType.title.copy(fontSize = 24.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = with(density) { maxWidth.toPx() }
        val size = remember(available, text) {
            var sp = 24f
            while (sp > 18f) {
                val width = measurer.measure(AnnotatedString(text), base.copy(fontSize = sp.sp), maxLines = 1).size.width
                if (width <= available) break
                sp -= 0.5f
            }
            sp.sp
        }
        Text(text = text, style = base.copy(fontSize = size), color = ReadinessInk, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ScaleRow(label: String, value: Int?, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = KpknType.eyebrow.copy(fontSize = 12.sp), color = ReadinessFaint)
            Spacer(Modifier.weight(1f))
            Text(value?.toString() ?: "—", style = KpknType.metricM.copy(fontSize = 16.sp), color = ReadinessInk)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            range.forEach { option ->
                val selected = option == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ReadinessInk else ReadinessInk.copy(alpha = 0.13f))
                        .clickable { onChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$option",
                        style = KpknType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = if (selected) WizardColors.background else ReadinessMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun DomsGrid(muscles: List<String>, values: Map<String, Int>, onChange: (String, Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AGUJETAS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = ReadinessFaint)
            Spacer(Modifier.weight(1f))
            Text("0–5 por zona", style = KpknType.caption.copy(fontSize = 12.sp), color = ReadinessFaint)
        }
        Spacer(Modifier.height(6.dp))
        muscles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { muscle ->
                    Row(
                        Modifier.weight(1f).padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = muscle,
                            style = KpknType.caption.copy(fontSize = 12.sp),
                            color = ReadinessMuted,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        repeat(5) { index ->
                            val filled = (values[muscle] ?: 0) > index
                            Box(
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) ReadinessInk else ReadinessInk.copy(alpha = 0.16f))
                                    .clickable {
                                        onChange(muscle, if (values[muscle] == index + 1) 0 else index + 1)
                                    },
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiscomfortPicker(
    selected: List<String>,
    frequent: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MOLESTIAS", style = KpknType.eyebrow.copy(fontSize = 12.sp), color = ReadinessFaint)
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    selected.isEmpty() -> "Ninguna"
                    selected == listOf("none") -> "Ninguna"
                    else -> "${selected.size} marcada(s)"
                },
                style = KpknType.caption.copy(fontSize = 12.sp),
                color = ReadinessFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WizardColors.text.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = KpknType.body.copy(fontSize = 14.sp, color = ReadinessInk),
                modifier = Modifier.fillMaxWidth().testTag("readiness-search"),
            )
            if (query.isEmpty()) {
                Text("Buscar molestia", style = KpknType.body.copy(fontSize = 14.sp), color = ReadinessFaint)
            }
        }
        Spacer(Modifier.height(8.dp))
        val chips = remember(frequent, query) { discomfortChips(frequent, query) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (id, label) ->
                        val isNone = id == "none"
                        val on = if (isNone) selected == listOf("none") else id in selected
                        Box(
                            Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (on) ReadinessInk else ReadinessInk.copy(alpha = 0.05f))
                                .border(1.dp, if (on) ReadinessInk else ReadinessInk.copy(alpha = 0.14f), RoundedCornerShape(15.dp))
                                .clickable { onToggle(id) }
                                .padding(horizontal = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = KpknType.chip.copy(fontSize = 12.sp),
                                color = if (on) WizardColors.background else ReadinessInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readinessMuscles(session: Session): List<String> {
    val setsByMuscle = mutableMapOf<String, Int>()
    session.allExercises().forEach { exercise ->
        val count = exercise.sets.count { !it.isIneffective }
        exercise.effectiveMuscles.orEmpty().forEach { muscle ->
            setsByMuscle[muscle.muscle] = (setsByMuscle[muscle.muscle] ?: 0) + count
        }
    }
    return setsByMuscle.entries.sortedByDescending { it.value }.take(6).map { it.key }
}

private fun discomfortChips(frequent: List<String>, query: String): List<Pair<String, String>> {
    val normalized = query.trim().lowercase()
    val all = DISCOMFORT_CATALOG_BY_ID.map { (id, entry) -> id to entry.label }
        .filter { normalized.isEmpty() || it.second.lowercase().contains(normalized) }
    val ordered = buildList {
        add("none" to "Sin molestias")
        frequent.forEach { id -> all.firstOrNull { it.first == id }?.let { add(it) } }
        all.filter { candidate -> none { it.first == candidate.first } }.forEach { add(it) }
    }
    return ordered.take(if (normalized.isEmpty()) 3 else 6)
}

private fun voiceLabel(mode: VoiceCaptureMode): String = when (mode) {
    VoiceCaptureMode.HANDS_FREE -> "Voz · manos libres"
    VoiceCaptureMode.MUSIC -> "Voz · con música"
}
