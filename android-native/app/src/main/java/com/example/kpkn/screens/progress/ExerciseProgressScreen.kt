package com.example.kpkn.screens.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.design.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

val LocalExerciseProgressOpener = staticCompositionLocalOf<(String?) -> Unit> { {} }

@Composable
fun ExerciseProgressRoute(
    exerciseId: String?,
    onBack: () -> Unit,
    programId: String? = null,
) {
    val vm: ExerciseProgressViewModel = viewModel(
        key = "exercise-progress:${exerciseId.orEmpty()}:${programId.orEmpty()}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ExerciseProgressViewModel(exerciseId = exerciseId, programId = programId) as T
        },
    )
    val state by vm.state.collectAsState()
    ExerciseProgressScreen(
        state = state,
        onExercise = vm::selectExercise,
        onMetric = vm::selectMetric,
        onWindow = vm::selectWindow,
        onPoint = vm::selectPoint,
        onBack = onBack,
        onSearchQueryChange = vm::setSearchQuery,
        onToggleDirectory = vm::toggleDirectory,
    )
}

private fun modeTitle(mode: LoadModeV2): String = when (mode) {
    LoadModeV2.LOAD -> "Carga externa"
    LoadModeV2.BODYWEIGHT -> "Peso corporal"
    LoadModeV2.LASTRE -> "Lastre"
    LoadModeV2.ASSISTED -> "Asistencia"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseProgressScreen(
    state: ExerciseProgressUiState,
    onExercise: (String) -> Unit,
    onMetric: (ExerciseProgressMetric) -> Unit,
    onWindow: (ExerciseProgressWindow) -> Unit,
    onPoint: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleDirectory: (Boolean) -> Unit = {},
) {
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas),
    ) {
        // Contenido principal bajo hazeSource para la barra superior flotante
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KpknSurface.canvas)
                .hazeSource(state = hazeState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 76.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) {
                Text("Cargando historial…", style = KpknType.body, color = WizardColors.textMuted)
                return@Column
            }

            if (state.showDirectory && state.choices.isNotEmpty()) {
                ExerciseProgressDirectorySection(
                    choices = state.filteredChoices,
                    totalCount = state.choices.size,
                    selectedId = state.report?.choice?.id,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectExercise = { id ->
                        onExercise(id)
                        onToggleDirectory(false)
                    },
                )
            } else {
                ExerciseProgressDetailSection(
                    state = state,
                    onExercise = onExercise,
                    onMetric = onMetric,
                    onWindow = onWindow,
                    onPoint = onPoint,
                    onOpenDirectory = { onToggleDirectory(true) },
                )
            }
        }

        // Barra superior flotante en vidrio KPKN (hermana directa del hazeSource)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .kpknGlass(hazeState, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpknTextAction(
                    text = "Volver",
                    onClick = {
                        if (state.showDirectory && state.report?.choice != null && state.choices.size > 1) {
                            onToggleDirectory(false)
                        } else {
                            onBack()
                        }
                    },
                )
                Text(
                    text = "Progreso por ejercicio",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (state.choices.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KpknSurface.inset)
                        .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(999.dp))
                        .clickable { onToggleDirectory(!state.showDirectory) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (state.showDirectory) "Ver detalle" else "Directorio (${state.choices.size})",
                        style = KpknType.caption,
                        color = WizardColors.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseProgressDirectorySection(
    choices: List<ExerciseProgressChoice>,
    totalCount: Int,
    selectedId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectExercise: (String) -> Unit,
) {
    val positiveColor = Color(0xFF7FE3B0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Directorio de ejercicios ($totalCount)",
            style = KpknType.title,
            color = WizardColors.text,
        )
        Text(
            text = "Selecciona un ejercicio para inspeccionar su curva de fuerza, esfuerzo RIR/RPE, cumplimiento del plan e historial serie por serie.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )

        // Buscador nativo KPKN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(KpknSurface.module)
                .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Search,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Buscar ejercicio o variante…",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = KpknType.body.copy(color = WizardColors.text),
                    cursorBrush = SolidColor(WizardColors.text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "Limpiar",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.clickable { onSearchQueryChange("") },
                )
            }
        }

        if (choices.isEmpty()) {
            KpknModule(Modifier.fillMaxWidth()) {
                Text(
                    text = "Ningún ejercicio coincide con «$searchQuery».",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
        } else {
            choices.forEach { choice ->
                val isSelected = choice.id == selectedId
                KpknModule(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(1.dp, SymbolPalette.musculo.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { onSelectExercise(choice.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = choice.name,
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = choice.configuration.ifBlank { "Configuración estándar" },
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val metaText = if (choice.sessions > 0) {
                                buildString {
                                    choice.firstDate?.let { append("Desde $it · ") }
                                    append("${choice.sessions} ${if (choice.sessions == 1) "sesión" else "sesiones"}")
                                    if (choice.totalSets > 0) append(" · ${choice.totalSets} series")
                                    choice.avgRir?.let { append(" · RIR ${formatProgressNumber(it)}") }
                                }
                            } else {
                                "Planificado en tu programa · sin registros aún"
                            }
                            Text(
                                text = metaText,
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }

                        if (choice.sparkline.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            ExerciseMiniSparkline(
                                values = choice.sparkline,
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(28.dp),
                            )
                        }

                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            choice.latestE1rm?.let { e1rm ->
                                Text(
                                    text = "${formatProgressNumber(e1rm)} kg",
                                    style = KpknType.metricM,
                                    color = WizardColors.text,
                                )
                            }
                            choice.deltaE1rm?.let { delta ->
                                val sign = if (delta > 0.005) "+" else ""
                                Text(
                                    text = "$sign${formatProgressNumber(delta)} kg",
                                    style = KpknType.caption,
                                    color = if (delta > 0.005) positiveColor else WizardColors.textMuted,
                                )
                            } ?: if (choice.sessions > 0 && choice.latestE1rm == null) {
                                Text(
                                    text = "${choice.sessions} ses.",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            } else null
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgressDetailSection(
    state: ExerciseProgressUiState,
    onExercise: (String) -> Unit,
    onMetric: (ExerciseProgressMetric) -> Unit,
    onWindow: (ExerciseProgressWindow) -> Unit,
    onPoint: (Int) -> Unit,
    onOpenDirectory: () -> Unit,
) {
    val report = state.report
    val choice = report?.choice
    val meta = report?.timelineMeta
    val adherence = report?.adherence

    if (choice == null || (report.points.isEmpty() && choice.sessions == 0)) {
        Text(
            text = "Todavía no hay sesiones de este ejercicio. Su progreso aparecerá al registrarlas.",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )
    }

    // 1. Cabecera del ejercicio
    KpknModule(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = choice?.name ?: "Elige un ejercicio",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
                choice?.let {
                    Text(
                        text = it.configuration.ifBlank { "Sin detalle de configuración en el registro" },
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                if (meta != null && (meta.firstLoggedDate != null || meta.firstPlannedDate != null)) {
                    val timelineBadge = buildString {
                        meta.firstLoggedDate?.let { append("Desde $it · ${meta.totalSessions} sesiones · ${meta.totalEffectiveSets} series") }
                        if (meta.firstPlannedDate != null) {
                            if (isNotEmpty()) append(" · ")
                            append("En tu programa desde ${meta.firstPlannedDate}")
                        }
                    }
                    Text(
                        text = timelineBadge,
                        style = KpknType.caption,
                        color = SymbolPalette.musculo,
                    )
                }
            }
            if (state.choices.size > 1) {
                KpknTextAction(
                    text = "Cambiar",
                    onClick = onOpenDirectory,
                )
            }
        }

        if (state.choices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.choices.forEach { item ->
                    val label = item.name + item.configuration.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    KpknChip(
                        text = label,
                        selected = item.id == choice?.id,
                        onClick = { onExercise(item.id) },
                    )
                }
            }
        }
    }

    // 2. Franja de 4 KPIs maestros (2×2) cuando hay sesiones registradas
    if (report != null && meta != null && meta.totalSessions > 0) {
        val e1rmRecord = report.records.firstOrNull { it.metric == ExerciseProgressMetric.E1RM }
            ?: report.records.firstOrNull()
        val deltaText = choice?.deltaE1rm?.let { d ->
            val sign = if (d > 0.005) "+" else ""
            "$sign${formatProgressNumber(d)} kg vs. inicio"
        } ?: "${meta.totalSessions} sesiones"
        val tonnageDisplay = if (meta.totalTonnageKg >= 1000.0) {
            "${formatProgressNumber(meta.totalTonnageKg / 1000.0)} t"
        } else {
            "${formatProgressNumber(meta.totalTonnageKg)} kg"
        }
        val rirDisplay = meta.avgRir?.let { formatProgressNumber(it) } ?: "—"
        val rpeSub = meta.avgRir?.let { "RPE equiv. ${formatProgressNumber((10.0 - it).coerceIn(1.0, 10.0))}" } ?: "Sin reserva declarada"
        val complianceDisplay = adherence?.setsCompliancePct?.let { "$it%" }
            ?: "${meta.totalEffectiveSets} series"
        val complianceSub = if (adherence != null && adherence.setsPlanned > 0) {
            "${adherence.setsDone}/${adherence.setsPlanned} series del plan"
        } else {
            "${formatProgressNumber(meta.avgFrequencyPerWeek)} ses./sem."
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExerciseKpiTile(
                    label = e1rmRecord?.metric?.title ?: "Mejor marca",
                    value = e1rmRecord?.let { "${formatProgressNumber(it.point.value)} ${progressPointUnit(it.point, it.metric)}" } ?: "—",
                    subtitle = deltaText,
                    modifier = Modifier.weight(1f),
                )
                ExerciseKpiTile(
                    label = "Tonelaje total",
                    value = tonnageDisplay,
                    subtitle = "${meta.totalEffectiveSets} series efectivas",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ExerciseKpiTile(
                    label = "RIR medio",
                    value = rirDisplay,
                    subtitle = rpeSub,
                    modifier = Modifier.weight(1f),
                )
                ExerciseKpiTile(
                    label = "Cumplimiento",
                    value = complianceDisplay,
                    subtitle = complianceSub,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 3. Selectores de métrica y ventana temporal
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        report?.available?.sortedBy { it.ordinal }?.forEach { metric ->
            KpknChip(
                text = metric.title,
                selected = report.metric == metric,
                onClick = { onMetric(metric) },
                modifier = Modifier.testTag("progress-metric-${metric.name}"),
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExerciseProgressWindow.entries.forEach { window ->
            KpknChip(
                text = window.title,
                selected = window == state.window,
                onClick = { onWindow(window) },
            )
        }
    }
    Text(
        text = "Se excluyen series saltadas, fallidas, calentamientos y datos inválidos. No se mezclan configuraciones o modalidades.",
        style = KpknType.caption,
        color = WizardColors.textMuted,
    )

    if (report != null) {
        if (report.insufficient) {
            Text(
                text = "Sin datos suficientes: hacen falta dos sesiones comparables. Mostramos lo registrado.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        }

        // 4. Gráfico principal interactivo
        if (report.points.isNotEmpty()) {
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader(report.metric.title)
                Spacer(Modifier.height(8.dp))
                ExerciseProgressInteractiveChart(
                    points = report.points,
                    metric = report.metric,
                    selectedIndex = state.selectedPoint,
                    onSelectPoint = onPoint,
                )
            }
        }

        // 5. Módulo de Consistencia y Cumplimiento (Planificado vs. Realizado)
        if (adherence != null && meta != null && meta.totalSessions > 0) {
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader("Consistencia y cumplimiento")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val setsPct = adherence.setsCompliancePct ?: 100
                    ExerciseAdherenceBar(
                        label = "Series completadas vs. planificadas",
                        valueLabel = if (adherence.setsPlanned > 0) "${adherence.setsDone}/${adherence.setsPlanned} ($setsPct%)" else "${meta.totalEffectiveSets} completadas",
                        percentage = setsPct,
                    )
                    adherence.repsInTargetPct?.let { repsPct ->
                        ExerciseAdherenceBar(
                            label = "Repeticiones en rango objetivo",
                            valueLabel = "$repsPct%",
                            percentage = repsPct,
                        )
                    }
                    adherence.rirHitRatePct?.let { rirPct ->
                        ExerciseAdherenceBar(
                            label = "Precisión de reserva (RIR ±1)",
                            valueLabel = "$rirPct%",
                            percentage = rirPct,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Frecuencia media", style = KpknType.caption, color = WizardColors.textMuted)
                        Text(
                            text = "${formatProgressNumber(meta.avgFrequencyPerWeek)} sesiones / semana",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                    }
                }
            }
        }

        // 6. Récords históricos
        if (report.records.isNotEmpty()) {
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader("Récords históricos")
                Text(
                    text = "Por trayectoria y, en repeticiones, por carga. Los empates conservan la primera fecha.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.records.forEach { record ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(KpknSurface.inset)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "${record.metric.title}: ${formatProgressNumber(record.point.value)} ${progressPointUnit(record.point, record.metric)}",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            Text(
                                text = "${record.point.date} · ${record.point.session} · ${modeTitle(record.point.mode)} · ${record.point.context}",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                            if (record.metric == ExerciseProgressMetric.BEST_SET || record.metric == ExerciseProgressMetric.MAX_REPS) {
                                Text(
                                    text = record.point.explanation,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Línea de tiempo de Hitos y PRs
        if (report.milestones.isNotEmpty()) {
            KpknModule(Modifier.fillMaxWidth()) {
                KpknModuleHeader("Línea de tiempo e hitos")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.milestones.asReversed().forEach { milestone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (milestone.isPr) Color(0xFF7FE3B0) else SymbolPalette.musculo),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "${milestone.date}",
                                        style = KpknType.caption,
                                        color = WizardColors.textMuted,
                                    )
                                    if (milestone.isPr) {
                                        Text(
                                            text = "PR",
                                            style = KpknType.caption,
                                            color = Color(0xFF7FE3B0),
                                        )
                                    }
                                }
                                Text(
                                    text = milestone.title,
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                Text(
                                    text = milestone.subtitle,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Historial detallado de sesiones (serie por serie)
        KpknModule(Modifier.fillMaxWidth()) {
            KpknModuleHeader("Sesiones del intervalo")
            Spacer(Modifier.height(8.dp))
            val pointByLogId = remember(report.points) {
                report.points.associateBy { it.logId }
            }
            if (report.sessionLogs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.sessionLogs.forEach { entry ->
                        var expanded by remember(entry.logId) { mutableStateOf(true) }
                        val matchedPoint = pointByLogId[entry.logId]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(KpknSurface.inset)
                                .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(14.dp))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (matchedPoint?.breaksBefore == true) {
                                Text(
                                    text = "No comparable con lo anterior",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${entry.date} · ${entry.sessionName}${if (matchedPoint?.isPr == true || entry.sets.any { it.isPr }) " · PR" else ""}",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                Text(
                                    text = "${entry.setsDone}${entry.setsPlanned?.let { "/$it" }.orEmpty()} series",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                            matchedPoint?.let { pt ->
                                Text(
                                    text = pt.explanation,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                            val sessionMeta = buildList {
                                entry.programName?.takeIf(String::isNotBlank)?.let(::add)
                                if (entry.tonnageKg > 0.0) add("Tonelaje ${formatProgressNumber(entry.tonnageKg)} kg")
                                entry.avgRir?.let { add("RIR medio ${formatProgressNumber(it)}") }
                            }.joinToString(" · ")
                            if (sessionMeta.isNotEmpty()) {
                                Text(
                                    text = sessionMeta,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }

                            AnimatedVisibility(visible = expanded && entry.sets.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    entry.sets.forEach { setLog ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "S${setLog.setNumber} · ${setLog.displayLine}",
                                                style = KpknType.caption,
                                                color = WizardColors.text,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (setLog.isPr) ExerciseSetBadge("PR", Color(0xFF7FE3B0))
                                                if (setLog.isFailure) ExerciseSetBadge("Fallo", Color(0xFFF59E0B))
                                                if (setLog.isDropSet) ExerciseSetBadge("Drop", SymbolPalette.musculo)
                                                if (setLog.isRestPause) ExerciseSetBadge("R-P", SymbolPalette.musculo)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                report.points.asReversed().forEach { point ->
                    if (point.breaksBefore) {
                        Text("No comparable con lo anterior", style = KpknType.caption, color = WizardColors.textMuted)
                    }
                    Text(
                        text = "${point.date} · ${point.session}${if (point.isPr) " · PR" else ""}",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = point.explanation,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseKpiTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KpknSurface.module)
            .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExerciseAdherenceBar(
    label: String,
    valueLabel: String,
    percentage: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
            Text(text = valueLabel, style = KpknType.bodyStrong, color = WizardColors.text)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KpknSurface.inset),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percentage / 100f).coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(SymbolPalette.musculo),
            )
        }
    }
}

@Composable
private fun ExerciseSetBadge(
    text: String,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = KpknType.caption,
            color = tint,
        )
    }
}
