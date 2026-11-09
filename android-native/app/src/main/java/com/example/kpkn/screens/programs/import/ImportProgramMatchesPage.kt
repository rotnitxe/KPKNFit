package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import androidx.compose.ui.unit.sp

internal data class ImportPendingOccurrence(
    val weekIndex: Int,
    val dayPosition: Int,
    val dayName: String,
    val exercise: ImportedExercise,
)

internal data class ImportPendingGroup(
    val rawName: String,
    val occurrences: List<ImportPendingOccurrence>,
)

internal data class ImportPendingDayGroup(
    val dayPosition: Int,
    val dayName: String,
    val rows: List<ImportPendingMatchRow>,
    val weekIndex: Int = 1,
)

internal data class ImportPendingMatchRow(
    val rawName: String,
    val occurrences: List<ImportPendingOccurrence>,
)

internal fun importPendingMatchGroups(
    imported: ImportedProgramDraft,
    resolutions: Map<String, String?>,
): List<ImportPendingGroup> {
    val occurrences = imported.weeks.sortedBy { it.index }.flatMap { week ->
        week.days.flatMapIndexed { dayIndex, day ->
            day.exercises.filter { exercise ->
                exercise.catalogSelectionId == null && !resolutions.containsKey(exercise.rawName)
            }.map { exercise -> ImportPendingOccurrence(week.index, dayIndex + 1, day.name, exercise) }
        }
    }
    return occurrences.groupBy { it.exercise.rawName }.map { (rawName, rows) ->
        ImportPendingGroup(rawName, rows)
    }
}

internal fun importPendingMatchDays(
    imported: ImportedProgramDraft,
    resolutions: Map<String, String?>,
): List<ImportPendingDayGroup> {
    val pending = imported.weeks.sortedBy { it.index }.flatMap { week ->
        week.days.flatMapIndexed { dayIndex, day ->
            day.exercises.filter { exercise ->
                exercise.catalogSelectionId == null && !resolutions.containsKey(exercise.rawName)
            }.map { exercise -> ImportPendingOccurrence(week.index, dayIndex + 1, day.name, exercise) }
        }
    }
    return pending.groupBy { Triple(it.weekIndex, it.dayPosition, it.dayName.trim().lowercase()) }.map { (_, occurrences) ->
        val first = occurrences.first()
        ImportPendingDayGroup(
            weekIndex = first.weekIndex,
            dayPosition = first.dayPosition,
            dayName = first.dayName,
            rows = occurrences.groupBy { it.exercise.rawName }.map { (rawName, sameName) ->
                ImportPendingMatchRow(rawName, sameName)
            },
        )
    }
}

/** The guided screen keeps uncertain names first and leaves confident matches available in a compact list. */
@Composable
internal fun ImportProgramMatchesPage(
    imported: ImportedProgramDraft,
    resolutions: Map<String, String?>,
    pendingWarnings: Int,
    catalogUnavailable: Boolean,
    catalogRetrying: Boolean,
    suggest: suspend (String) -> List<com.example.kpkn.domain.training.importer.ExerciseNameCandidate>,
    search: suspend (String) -> List<com.example.kpkn.domain.training.importer.ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String>,
    onEdit: (ImportEditOp) -> Unit,
    onOpenCatalog: (exerciseId: String, rawName: String, perOccurrence: Boolean) -> Unit,
    onOpenWarnings: () -> Unit,
    onRetryCatalog: () -> Unit,
    onAddExercise: () -> Unit,
    initialActiveRawName: String? = null,
    modifier: Modifier = Modifier,
) {
    val groups = remember(imported, resolutions) { importPendingMatchGroups(imported, resolutions) }
    val pendingDays = remember(imported, resolutions) { importPendingMatchDays(imported, resolutions) }
    val allOccurrences = remember(imported) {
        imported.weeks.sortedBy { it.index }.flatMap { week ->
            week.days.flatMapIndexed { dayIndex, day ->
                day.exercises.map { exercise -> ImportPendingOccurrence(week.index, dayIndex + 1, day.name, exercise) }
            }
        }
    }
    val allExercises = remember(allOccurrences) { allOccurrences.map { it.exercise } }
    val total = allExercises.size
    val pending = groups.sumOf { it.occurrences.size }
    val decided = (total - pending).coerceAtLeast(0)
    val catalogResolved = allExercises.count { exercise ->
        when {
            exercise.catalogSelectionId != null -> !exercise.catalogSelectionId.equals("own", ignoreCase = true)
            resolutions.containsKey(exercise.rawName) -> resolutions[exercise.rawName] != null
            else -> false
        }
    }
    val selectedIds = remember(imported, resolutions) {
        allExercises.mapNotNull { exercise ->
            val id = exercise.catalogSelectionId ?: resolutions[exercise.rawName]
            id?.takeIf { !it.equals("own", ignoreCase = true) }
        }.toSet()
    }
    var selectedNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(selectedIds) {
        selectedNames = try {
            namesOf(selectedIds)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyMap()
        }
    }
    var selectedFilter by rememberSaveable(imported.title) { mutableStateOf("pending") }
    val showAll = selectedFilter == "all"
    LaunchedEffect(pending) {
        if (pending == 0 && selectedFilter == "pending") selectedFilter = "all"
    }
    var activeRawName by rememberSaveable(imported.title) { mutableStateOf(initialActiveRawName) }
    var separateRawName by rememberSaveable(imported.title) { mutableStateOf<String?>(null) }
    var activeExerciseId by rememberSaveable(imported.title) { mutableStateOf<String?>(null) }
    LaunchedEffect(groups) {
        if (groups.none { it.rawName == activeRawName }) activeRawName = groups.firstOrNull()?.rawName
        val active = groups.firstOrNull { it.rawName == activeRawName }
        if (active == null || active.occurrences.none { it.exercise.id == activeExerciseId }) {
            activeExerciseId = active?.occurrences?.firstOrNull()?.exercise?.id
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag("import-matches-page"),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ImportWizardStepProgress(1, "$decided de $total", totalSteps = 1)
        Text("Confirma las coincidencias.", style = ImportPageTitleStyle, color = WizardColors.text)
        Text(
            if (pending > 0) "$decided ya apuntan a una variante del catálogo. Revisa los $pending dudosos o abre «Todas» para comprobarlos."
            else "Los $total apuntan a una variante del catálogo. Puedes cambiarlos cuando quieras.",
            style = ImportPageBodyStyle,
            color = WizardColors.textMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1A1A1A)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ImportMatchFilterChip(
                label = "Pendientes",
                count = pending,
                selected = selectedFilter == "pending",
                onClick = { selectedFilter = "pending" },
                testTag = "import-match-filter-pending",
                modifier = Modifier.weight(1f),
            )
            ImportMatchFilterChip(
                label = "Todas",
                count = total,
                selected = selectedFilter == "all",
                onClick = { selectedFilter = "all" },
                testTag = "import-match-toggle-all",
                modifier = Modifier.weight(1f),
            )
            ImportMatchFilterChip(
                label = "Avisos",
                count = pendingWarnings,
                selected = selectedFilter == "warn",
                onClick = { selectedFilter = "warn" },
                testTag = "import-match-filter-warnings",
                modifier = Modifier.weight(1f),
            )
        }
        if (selectedFilter == "warn") {
            ImportWizardPanel(title = "Avisos del documento", subtitle = "$pendingWarnings sin revisar") {
                Text(
                    if (pendingWarnings == 0) "No hay avisos pendientes."
                    else "Abre los avisos para revisar el texto original, corregir lo necesario o marcarlos como revisados.",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                )
                if (pendingWarnings > 0) {
                    ImportTextAction(
                        "Abrir los $pendingWarnings avisos",
                        onClick = onOpenWarnings,
                        modifier = Modifier.testTag("import-match-open-warnings"),
                        color = WizardColors.cta,
                    )
                }
            }
        } else {
            if (catalogResolved > 0) ImportMatchHighBanner(
                count = catalogResolved,
                onShowAll = { selectedFilter = "all" },
            )
            if (showAll) {
                ImportWizardPanel(
                    title = "Coincidencias resueltas",
                    subtitle = "$decided de $total ejercicios decididos",
                ) {
                    val grouped = allOccurrences.filter { occurrence ->
                        val exercise = occurrence.exercise
                        exercise.catalogSelectionId != null || resolutions.containsKey(exercise.rawName)
                    }.groupBy { occurrence ->
                        val exercise = occurrence.exercise
                        val id = exercise.catalogSelectionId ?: resolutions[exercise.rawName]
                        val isOwn = id == null || id.equals("own", ignoreCase = true)
                        Triple(exercise.rawName, if (isOwn) exercise.rawName else selectedNames[id.orEmpty()].orEmpty().ifBlank { exercise.rawName }, isOwn)
                    }
                    grouped.entries.forEach { (key, occurrences) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .semantics(mergeDescendants = true) { stateDescription = if (key.third) "Propio" else "Reconocido" }
                                .testTag("import-exercise-${key.first}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(key.second, style = WizardTypography.controlLabel, color = WizardColors.text)
                                if (key.first != key.second) Text("Documento: ${key.first}", style = WizardTypography.note, color = WizardColors.textMuted)
                                val prescription = importPrescriptionOf(occurrences.first().exercise, imported.loadUnit)
                                if (prescription.isNotBlank()) Text(prescription, style = WizardTypography.note, color = WizardColors.textMuted)
                                val locations = occurrences.distinctBy { Triple(it.weekIndex, it.dayPosition, it.dayName) }
                                    .joinToString(" · ") { "Sem. ${it.weekIndex} · Día ${it.dayPosition}: ${it.dayName}" }
                                if (locations.isNotBlank()) Text(
                                    locations,
                                    style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                                    color = WizardColors.textFaint,
                                    modifier = Modifier.testTag("import-match-location-${key.first}"),
                                )
                            }
                            Text("${occurrences.size} · ${if (key.third) "Propio" else "Catálogo"}", style = WizardTypography.note, color = WizardColors.textMuted)
                            ImportTextAction(
                                label = "Cambiar",
                                onClick = { onOpenCatalog(occurrences.first().exercise.id, key.first, false) },
                                modifier = Modifier.testTag("import-match-resolved-change-${key.first}"),
                                color = WizardColors.cta,
                            )
                        }
                    }
                }
            }
            if (catalogUnavailable || catalogRetrying) {
                ImportWizardPanel(
                    title = if (catalogRetrying) "Abriendo el catálogo…" else "Catálogo no disponible",
                    subtitle = "El borrador y el texto original siguen guardados.",
                ) {
                    Text(
                        if (catalogRetrying) "Estamos buscando coincidencias seguras. Tus decisiones manuales se conservan."
                        else "Puedes continuar revisando cada nombre o volver a intentar el emparejado automático.",
                        style = WizardTypography.note,
                        color = WizardColors.textMuted,
                    )
                    if (catalogRetrying) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.testTag("import-match-catalog-progress"),
                            color = WizardColors.cta,
                        )
                    } else {
                        ImportTextAction(
                            "Reintentar catálogo",
                            onClick = onRetryCatalog,
                            modifier = Modifier.testTag("import-match-retry-catalog"),
                            color = WizardColors.cta,
                        )
                    }
                }
            }
            if (groups.isEmpty()) {
                ImportWizardPanel(title = "Todo decidido", subtitle = "Las coincidencias quedan guardadas en el borrador.") {
                    Text("Puedes abrir la lista de resueltos para comprobar las variantes elegidas.", style = WizardTypography.note, color = WizardColors.textMuted)
                }
            } else {
                val groupsByName = remember(groups) { groups.associateBy { it.rawName } }
                pendingDays.forEach { day ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 3.dp, top = 5.dp, end = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                importMatchDayTitle(day),
                                style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.7.sp, fontWeight = FontWeight.SemiBold),
                                color = WizardColors.textFaint,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${day.rows.size} por decidir",
                                style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                                color = WizardColors.textFaint,
                                maxLines = 1,
                            )
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF111111))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp)),
                        ) {
                            day.rows.forEachIndexed { index, dayRow ->
                                val group = groupsByName[dayRow.rawName] ?: return@forEachIndexed
                                val occurrence = dayRow.occurrences.first()
                                val separate = separateRawName == group.rawName
                                val expanded = activeRawName == group.rawName && dayRow.occurrences.any { it.exercise.id == activeExerciseId }
                                if (index > 0) Spacer(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.07f)),
                                )
                                ImportMatchOccurrenceRow(
                                    dayRow = dayRow,
                                    group = group,
                                    expanded = expanded,
                                    separate = separate,
                                    activeExerciseId = activeExerciseId,
                                    showGroupTag = group.occurrences.firstOrNull()?.exercise?.id == occurrence.exercise.id,
                                    loadUnit = imported.loadUnit,
                                    suggest = suggest,
                                    onToggle = {
                                        if (expanded) {
                                            activeRawName = null
                                            activeExerciseId = null
                                        } else {
                                            activeRawName = group.rawName
                                            activeExerciseId = occurrence.exercise.id
                                        }
                                    },
                                    onSelectOccurrence = { exerciseId ->
                                        activeRawName = group.rawName
                                        activeExerciseId = exerciseId
                                    },
                                    onSeparateToggle = {
                                        separateRawName = if (separate) null else group.rawName
                                        activeExerciseId = occurrence.exercise.id
                                    },
                                    onPick = { configId, name ->
                                        if (separate) onEdit(ImportEditOp.DecideOccurrence(activeExerciseId ?: occurrence.exercise.id, configId ?: "own", name))
                                        else onEdit(ImportEditOp.Decide(group.rawName, configId, name))
                                    },
                                    onOwn = {
                                        if (separate) onEdit(ImportEditOp.DecideOccurrence(activeExerciseId ?: occurrence.exercise.id, "own", group.rawName))
                                        else onEdit(ImportEditOp.Decide(group.rawName, null, group.rawName))
                                    },
                                    onOpenCatalog = { onOpenCatalog(activeExerciseId ?: occurrence.exercise.id, group.rawName, separate) },
                                )
                            }
                        }
                    }
                }
            }
        }
        ImportTextAction("Añadir ejercicio", onAddExercise, modifier = Modifier.testTag("import-match-add-exercise"), color = WizardColors.cta)
    }
}

private fun importMatchDayTitle(day: ImportPendingDayGroup): String {
    val name = day.dayName.trim().ifBlank { "Día ${day.dayPosition}" }
    val dayPrefix = Regex("^d[ií]a\\s+(\\d+)\\s*[·—-]?\\s*", RegexOption.IGNORE_CASE)
    val dayMatch = dayPrefix.find(name)
    val dayLabel = if (dayMatch != null) {
        "Día ${dayMatch.groupValues[1]} · ${name.substring(dayMatch.range.last + 1).trimStart()}"
    } else {
        "Día ${day.dayPosition} · $name"
    }
    return "Semana ${day.weekIndex} · $dayLabel"
}

@Composable
private fun ImportMatchHighBanner(
    count: Int,
    onShowAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF43D18C).copy(alpha = 0.05f))
            .border(1.dp, Color(0xFF43D18C).copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ImportMatchStateIcon(resolved = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "$count coincidencias altas",
                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF2EEE6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Apuntan a una variante del catálogo. Puedes cambiarlas cuando quieras.",
                style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.8.sp),
                color = Color(0xFF8E8980),
            )
        }
        Text(
            "Ver",
            style = WizardTypography.note.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
            color = Color(0xFF8E8980),
            modifier = Modifier.clickable(role = Role.Button, onClick = onShowAll)
                .testTag("import-match-show-resolved")
                .padding(horizontal = 4.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ImportMatchStateIcon(resolved: Boolean) {
    val color = if (resolved) Color(0xFF43D18C) else Color(0xFFF7CF73)
    Box(
        modifier = Modifier.size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (resolved) 0.13f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val stroke = 1.6.dp.toPx()
            if (resolved) {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .16f, size.height * .53f), androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .78f), stroke, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .78f), androidx.compose.ui.geometry.Offset(size.width * .86f, size.height * .24f), stroke, cap = StrokeCap.Round)
            } else {
                val path = Path().apply {
                    moveTo(size.width * .5f, size.height * .08f)
                    lineTo(size.width * .94f, size.height * .9f)
                    lineTo(size.width * .06f, size.height * .9f)
                    close()
                }
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .36f), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .61f), stroke, cap = StrokeCap.Round)
                drawCircle(color, radius = stroke * .75f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .78f))
            }
        }
    }
}

@Composable
private fun ImportMatchOccurrenceRow(
    dayRow: ImportPendingMatchRow,
    group: ImportPendingGroup,
    expanded: Boolean,
    separate: Boolean,
    activeExerciseId: String?,
    showGroupTag: Boolean,
    loadUnit: com.example.kpkn.domain.training.importer.ImportLoadUnit,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    onToggle: () -> Unit,
    onSelectOccurrence: (String) -> Unit,
    onSeparateToggle: () -> Unit,
    onPick: (configId: String?, name: String) -> Unit,
    onOwn: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val exercise = dayRow.occurrences.first().exercise
    Column(modifier = Modifier.fillMaxWidth().then(if (showGroupTag) Modifier.testTag("import-match-group-${group.rawName}") else Modifier)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ImportMatchStateIcon(resolved = false)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    exercise.rawName,
                    style = WizardTypography.note.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFF2EEE6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    importPrescriptionOf(exercise, loadUnit)
                        .takeIf { it.isNotBlank() }
                        ?.let { "$it · sin decidir" } ?: "Sin variante · sin decidir",
                    style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = Color(0xFF8E8980),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (separate && group.occurrences.size > 1 && !expanded) "Elegir" else "Revisar",
                    style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF8E8980),
                    maxLines = 1,
                )
                Canvas(Modifier.size(13.dp)) {
                    val stroke = 1.4.dp.toPx()
                    val y = if (expanded) size.height * .63f else size.height * .42f
                    drawLine(Color(0xFF77736D), androidx.compose.ui.geometry.Offset(size.width * .2f, y), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .72f), stroke, cap = StrokeCap.Round)
                    drawLine(Color(0xFF77736D), androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .72f), androidx.compose.ui.geometry.Offset(size.width * .8f, y), stroke, cap = StrokeCap.Round)
                }
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 50.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(2.dp).heightIn(min = 22.dp).background(Color(0x66F7CF73)))
                    Text(
                        "Tu documento dice «${group.rawName}».",
                        style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = Color(0xFF8E8980),
                        modifier = Modifier.testTag("import-match-source-${group.rawName}"),
                    )
                }
                Text(
                    "Elige la variante que vas a entrenar.",
                    style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = Color(0xFF8E8980),
                )
                ImportMatchCandidateList(rawName = group.rawName, suggest = suggest, onPick = onPick)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImportMatchAction(
                        label = "Buscar en el catálogo",
                        outlined = true,
                        onClick = onOpenCatalog,
                        modifier = Modifier.weight(1f).testTag("import-choice-shared-catalog"),
                    )
                    ImportMatchAction(
                        label = "Dejarlo como propio",
                        outlined = false,
                        onClick = onOwn,
                        modifier = Modifier.weight(1f).testTag("import-match-own-${exercise.id}"),
                    )
                }
                if (group.occurrences.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            if (separate) "Cada aparición tendrá su propia decisión." else "Aparece ${group.occurrences.size} veces. Se aplicará a todas.",
                            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
                            color = Color(0xFF8E8980),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (separate) "Aplicar a todas" else "Decidir por separado",
                            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFC4BFB6),
                            modifier = Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onSeparateToggle)
                                .testTag("import-match-separate-${group.rawName}")
                                .padding(vertical = 11.dp),
                        )
                    }
                    if (separate) {
                        group.occurrences.forEach { occurrence ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button, onClick = { onSelectOccurrence(occurrence.exercise.id) })
                                    .testTag("import-match-occurrence-${occurrence.exercise.id}")
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Semana ${occurrence.weekIndex} · ${occurrence.dayName}",
                                    style = WizardTypography.note.copy(fontSize = 10.5.sp),
                                    color = if (occurrence.exercise.id == activeExerciseId) Color(0xFFF2EEE6) else Color(0xFF8E8980),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (occurrence.exercise.id == activeExerciseId) Text("Actual", style = WizardTypography.note.copy(fontSize = 10.sp), color = Color(0xFF8E8980))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportMatchCandidateList(
    rawName: String,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    onPick: (configId: String?, name: String) -> Unit,
) {
    var suggestions by remember(rawName) { mutableStateOf<List<ExerciseNameCandidate>>(emptyList()) }
    var loaded by remember(rawName) { mutableStateOf(false) }
    LaunchedEffect(rawName) {
        loaded = false
        suggestions = try {
            suggest(rawName)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyList()
        }
        loaded = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (!loaded) {
            Text("Buscando variantes…", style = WizardTypography.note.copy(fontSize = 10.5.sp), color = Color(0xFF8E8980))
        } else if (suggestions.isEmpty()) {
            Text("No encontramos una variante segura. Puedes buscar en el catálogo o conservarlo como propio.", style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = Color(0xFF8E8980))
        } else {
            suggestions.forEach { candidate ->
                ImportMatchCandidateRow(candidate = candidate, onClick = { onPick(candidate.configurationId, candidate.displayName) })
            }
        }
    }
}

@Composable
private fun ImportMatchCandidateRow(candidate: ExerciseNameCandidate, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.025f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag("import-match-candidate-${candidate.configurationId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp).border(1.5.dp, Color.White.copy(alpha = 0.24f), CircleShape))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                candidate.displayName,
                style = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 15.6.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF2EEE6),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val equipment = importEquipmentLabelOf(candidate.equipmentId)
            if (equipment.isNotBlank()) Text(equipment, style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 13.8.sp), color = Color(0xFF8E8980), maxLines = 1)
        }
        Column(modifier = Modifier.width(44.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                Box(
                    Modifier.fillMaxWidth(candidate.confidence.toFloat().coerceIn(0f, 1f))
                        .height(3.dp).clip(CircleShape).background(Color(0xFFF7CF73)),
                )
            }
            Text(
                "${(candidate.confidence * 100).toInt().coerceIn(0, 100)} %",
                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFFC4BFB6),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ImportMatchAction(
    label: String,
    outlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier.heightIn(min = 42.dp)
            .clip(shape)
            .then(if (outlined) Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (outlined) Color(0xFFC4BFB6) else Color(0xFF8E8980),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImportMatchFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) WizardColors.cta.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = WizardTypography.note.copy(fontSize = 11.5.sp),
            color = if (selected) WizardColors.text else WizardColors.textMuted,
            maxLines = 1,
        )
        Text(
            " $count",
            style = WizardTypography.note.copy(fontSize = 11.5.sp),
            color = if (selected) WizardColors.cta else WizardColors.textFaint,
            maxLines = 1,
        )
    }
}
