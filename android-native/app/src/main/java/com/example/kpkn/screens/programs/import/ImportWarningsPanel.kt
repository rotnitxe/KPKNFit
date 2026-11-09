package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.importer.ocr.OcrImportSource
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportWarning
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportedDay
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ProgramImportParser
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.programs.plural
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Lo que «Añadir a:» elige para un día nuevo (los ids de día nunca están vacíos). */
private const val NEW_DAY_TARGET = ""

/** Largo máximo del nombre que se propone para una fila no entendida. */
private const val PROPOSED_NAME_MAX = 60

/** Palabras de notación que no son un nombre: se quitan al proponer el nombre de una fila. */
private val NOTATION_WORDS = setOf(
    "x", "×", "kg", "kgs", "lb", "lbs", "rpe", "rir", "rm", "series", "serie", "sets", "set", "reps", "rep",
    "repeticiones", "descanso", "min", "seg", "s",
)

/** Una cifra, con coma o punto, o un «%»: lo que se quita del texto al proponer un nombre. */
private val NUMBERS = Regex("""[\d%]+([.,]\d+)?""")

/** Series y repeticiones escritas como «4 x 8», «3x8» o «3x8-12». */
private val SCHEME_IN_TEXT = Regex("""(\d{1,3})\s*[x×]\s*(\d{1,3})(?:\s*[-–]\s*(\d{1,3}))?""", RegexOption.IGNORE_CASE)

/** Series y repeticiones con las que se propone añadir una fila: lo que se lee en su texto, o 3 × 10. */
internal data class ImportAddReading(val sets: Int, val repsMin: Int, val repsMax: Int)

/** Con más avisos sin revisar que esto, el panel empieza plegado. */
internal const val IMPORT_WARNINGS_FOLD_FROM = 3

/**
 * Los avisos de la importación, cada uno con su acción. Un aviso sin revisar se resuelve con lo que dice: «Añadir como
 * ejercicio» (una fila que no se entendió), «Revisar el ejercicio» (un valor descartado) o «Entendido» / «Ignorar». Cada acción
 * deja deshacer. Los revisados salen de la lista y se ven atenuados si se piden. Sin nada sin revisar, el panel se pliega en una
 * línea. Con más de [IMPORT_WARNINGS_FOLD_FROM] sin revisar, empieza plegado: la cuenta, «Ver avisos» e «Ignorar todos», y el aviso
 * de la imagen, que siempre sale. [reveal] sube al tocar «Ver avisos» de la revisión final: el panel se despliega y sube a la vista.
 */
@Composable
internal fun ImportWarningsPanel(
    imported: ImportedProgramDraft,
    reveal: Int,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String> = { emptyMap() },
    useSharedCatalog: Boolean = false,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    /** Product routes open the real editor instead of offering a reduced add form with guessed prescriptions. */
    onOpenSessionEditor: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val warnings = imported.warnings
    val pending = warnings.filter { it.id !in imported.reviewedWarnings }
    val reviewed = warnings.filter { it.id in imported.reviewedWarnings }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showReviewed by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val requester = remember { BringIntoViewRequester() }
    // «Ver avisos» de la revisión final: se despliega y sube a la vista, aunque ya estuviera desplegado.
    LaunchedEffect(reveal) {
        if (reveal > 0 && warnings.isNotEmpty()) {
            expanded = true
            requester.bringIntoView()
        }
    }
    if (warnings.isEmpty()) return
    if (pending.isEmpty() && !expanded) {
        Row(
            modifier = modifier.fillMaxWidth().bringIntoViewRequester(requester).testTag("import-warnings-folded"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Avisos: todo revisado ·", style = WizardTypography.note, color = WizardColors.textMuted)
            ImportTextAction(label = "Ver", onClick = { expanded = true }, modifier = Modifier.testTag("import-warnings-open"))
        }
        return
    }
    if (pending.size > IMPORT_WARNINGS_FOLD_FROM && !expanded) {
        ImportWarningsFolded(
            imported = imported,
            pending = pending,
            search = search,
            namesOf = namesOf,
            useSharedCatalog = useSharedCatalog,
            onOpen = { expanded = true },
            onEdit = onEdit,
            onOpenSessionEditor = onOpenSessionEditor,
            requester = requester,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .semantics { paneTitle = "Avisos de la importación" }
            .testTag("import-warnings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Avisos de la importación",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (pending.isEmpty()) "Todo revisado" else sinRevisar(pending.size),
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (pending.any { it.kind == ImportWarningKind.SHEET_NOTE }) {
                ImportTextAction(
                    label = "Ignorar notas informativas",
                    onClick = { onEdit(ImportEditOp.IgnoreAllWarnings) },
                    modifier = Modifier.testTag("import-warnings-ignore-all"),
                )
            }
        }
        pending.forEach { warning ->
            ImportWarningRow(
                warning = warning,
                imported = imported,
                adding = adding == warning.id,
                onAddingChange = { open -> adding = if (open) warning.id else null },
                search = search,
                namesOf = namesOf,
                useSharedCatalog = useSharedCatalog,
                onOpenExercise = onOpenExercise,
                onEdit = onEdit,
                onOpenSessionEditor = onOpenSessionEditor,
            )
        }
        if (reviewed.isNotEmpty()) {
            ImportTextAction(
                label = if (showReviewed) "Ocultar los revisados" else "Ver los revisados (${reviewed.size})",
                onClick = { showReviewed = !showReviewed },
                modifier = Modifier.testTag("import-warnings-reviewed"),
            )
            if (showReviewed) {
                reviewed.forEach { warning -> ReviewedWarningRow(warning) }
            }
        }
        if (pending.isEmpty() || pending.size > IMPORT_WARNINGS_FOLD_FROM) {
            ImportTextAction(
                label = "Ocultar avisos",
                onClick = { expanded = false },
                modifier = Modifier.testTag("import-warnings-close"),
            )
        }
    }
}

/** Hoja compacta del prototipo v4: conserva acciones del panel, con filas de 48 dp y el original disponible al abrir «Ver». */
@Composable
internal fun ImportWarningsSheetContent(
    imported: ImportedProgramDraft,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onOpenSessionEditor: (String?) -> Unit,
    onClose: () -> Unit,
    hasOriginalSource: Boolean = false,
    onOpenOriginalSource: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pending = imported.warnings.filter { it.id !in imported.reviewedWarnings }
    val informationalPending = pending.filter { it.kind == ImportWarningKind.SHEET_NOTE }
    var expandedId by rememberSaveable(imported.warnings.map { it.id }, imported.reviewedWarnings) { mutableStateOf<String?>(null) }
    var confirmIgnoreAll by rememberSaveable(imported.warnings.map { it.id }, imported.reviewedWarnings) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { paneTitle = "Avisos del documento" }
            .testTag("import-warnings-sheet"),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            "Avisos del documento",
            style = WizardTypography.stepTitle.copy(fontSize = 18.sp, lineHeight = 20.2.sp, letterSpacing = (-0.5).sp),
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "${plural(imported.warnings.size, "aviso", "avisos")} · revisa cada fila y decide si corresponde a un ejercicio.",
            style = WizardTypography.note.copy(fontSize = 11.5.sp, lineHeight = 16.7.sp),
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (pending.isEmpty()) {
            Text(
                "Todos los avisos están revisados.",
                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = WizardColors.textMuted,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            pending.forEachIndexed { index, warning ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                }
                val exerciseId = warning.exerciseId?.takeIf { importExerciseOf(imported, it) != null }
                val opensStructure = warning.kind == ImportWarningKind.STRUCTURE_REVIEW && warning.dayId != null
                val opensEditor = warning.kind == ImportWarningKind.SKIPPED_ROW && warning.dayId != null
                val opensExercise = warning.kind in setOf(ImportWarningKind.INCOMPLETE_ROW, ImportWarningKind.IGNORED_VALUE) && exerciseId != null
                val actionLabel = when {
                    opensStructure -> "Asignar día"
                    opensEditor -> "Corregir"
                    opensExercise -> "Corregir"
                    warning.kind == ImportWarningKind.SKIPPED_ROW && warning.dayId != null -> "Añadir"
                    else -> if (expandedId == warning.id) "Ocultar" else "Ver"
                }
                ImportWarningsSheetRow(
                    imported = imported,
                    warning = warning,
                    actionLabel = actionLabel,
                    expanded = expandedId == warning.id,
                    hasOriginalSource = hasOriginalSource,
                    onOpenOriginalSource = onOpenOriginalSource,
                    onAction = {
                        when {
                            opensStructure -> onOpenSessionEditor(warning.dayId)
                            opensEditor -> onOpenSessionEditor(warning.dayId)
                            opensExercise -> exerciseId?.let(onOpenExercise)
            warning.kind == ImportWarningKind.SKIPPED_ROW && warning.dayId != null -> onOpenSessionEditor(warning.dayId)
                            else -> expandedId = if (expandedId == warning.id) null else warning.id
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (confirmIgnoreAll && informationalPending.isNotEmpty()) {
            Text(
                "¿Ignorar ${plural(informationalPending.size, "nota informativa", "notas informativas")} y marcar${if (informationalPending.size == 1) "la" else "las"} como revisad${if (informationalPending.size == 1) "a" else "as"}? Los avisos de estructura y de ejercicios seguirán pendientes.",
                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            ImportWarningsSheetButton(
                label = "Marcar notas revisadas",
                enabled = true,
                onClick = {
                    onEdit(ImportEditOp.IgnoreAllWarnings)
                    confirmIgnoreAll = false
                },
                testTag = "import-warnings-sheet-confirm-ignore-all",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            ImportWarningsSheetButton(
                label = "Cancelar",
                enabled = true,
                onClick = { confirmIgnoreAll = false },
                testTag = "import-warnings-sheet-cancel-ignore-all",
                modifier = Modifier.fillMaxWidth(),
                ghost = true,
            )
        } else {
            ImportWarningsSheetButton(
                label = "Ignorar notas informativas",
                enabled = informationalPending.isNotEmpty(),
                onClick = { confirmIgnoreAll = true },
                testTag = "import-warnings-sheet-ignore-all",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        ImportWarningsSheetButton(
            label = "Cerrar",
            enabled = true,
            onClick = onClose,
            testTag = "import-warnings-sheet-close",
            modifier = Modifier.fillMaxWidth(),
            ghost = true,
        )
    }
}

@Composable
private fun ImportWarningsSheetRow(
    imported: ImportedProgramDraft,
    warning: ImportWarning,
    actionLabel: String,
    expanded: Boolean,
    hasOriginalSource: Boolean,
    onOpenOriginalSource: () -> Unit,
    onAction: () -> Unit,
) {
    val warningAccent = Color(0xFFF7CF73)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("import-warning-sheet-row-${warning.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.055f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KpknLineIcons.Alert, contentDescription = null, tint = warningAccent, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                importWarningSheetTitle(warning),
                style = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded) {
                Text(
                    importWarningSheetSubtitle(imported, warning),
                    style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                Text(importWarningDetail(warning), style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = WizardColors.textMuted)
                importWarningSource(warning)?.let { source ->
                    Text(
                        "Tu archivo dice «$source${if (warning.sourceTextTruncated) "…" else ""}».",
                        style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                        color = WizardColors.textFaint,
                    )
                }
                warning.itemCount?.let { count ->
                    Text("Este aviso resume $count elementos.", style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = WizardColors.textFaint)
                }
                warning.sourceDocumentType.takeIf { it != com.example.kpkn.domain.training.importer.ImportDocumentType.UNKNOWN }?.let { type ->
                    Text("Origen: ${importDocumentTypeLabel(type)}", style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = WizardColors.textFaint)
                }
            }
            if (hasOriginalSource) {
                ImportTextAction(
                    label = "Ver original importado",
                    onClick = onOpenOriginalSource,
                    modifier = Modifier.testTag("import-warning-sheet-open-original-${warning.id}"),
                    color = WizardColors.text,
                )
            }
        }
        ImportTextAction(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier.testTag("import-warning-sheet-action-${warning.id}"),
            color = if (actionLabel == "Corregir" || actionLabel == "Añadir") warningAccent else WizardColors.text,
        )
    }
}

@Composable
private fun ImportWarningsSheetButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (ghost) Modifier else Modifier.background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.12f), shape))
            .testTag(testTag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) WizardColors.text else WizardColors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun importWarningSheetTitle(warning: ImportWarning): String {
    val searchable = "${warning.message} ${warning.sourceText.orEmpty()}".lowercase()
    return when (warning.kind) {
        ImportWarningKind.STRUCTURE_REVIEW -> "Cabecera de sesión dudosa"
        ImportWarningKind.SKIPPED_ROW -> "Fila omitida"
        ImportWarningKind.INCOMPLETE_ROW -> "Dato incompleto"
        ImportWarningKind.IGNORED_VALUE -> when {
            "rpe" in searchable -> "RPE fuera de rango"
            "rir" in searchable -> "RIR fuera de rango"
            "descanso" in searchable -> "Descanso no legible"
            "carga" in searchable || " kg" in searchable || " lb" in searchable -> if ("parcial" in searchable) "Carga parcial" else "Carga no legible"
            else -> "Valor descartado"
        }
        ImportWarningKind.EMPTY_DAY -> "Sesión vacía"
        ImportWarningKind.NOTHING_FOUND -> "Sin ejercicios"
        ImportWarningKind.SHEET_NOTE -> if ("columna" in searchable) "Columna ignorada" else "Nota del documento"
    }
}

private fun importWarningSheetSubtitle(imported: ImportedProgramDraft, warning: ImportWarning): String {
    if (warning.kind == ImportWarningKind.SHEET_NOTE && warning.message.contains("columna", ignoreCase = true)) {
        return importWarningDetail(warning)
    }
    if (warning.kind == ImportWarningKind.SHEET_NOTE) {
        return warning.sourceText?.trim()?.takeIf { it.isNotEmpty() }?.let { "«$it»" } ?: importWarningDetail(warning)
    }
    val sourceName = importWarningSource(warning)?.substringBefore(" · ")?.takeIf { it.isNotBlank() }
    val subject = sourceName ?: warning.exerciseId?.let { importExerciseOf(imported, it)?.rawName } ?: importWarningDetail(warning)
    val location = warning.location.takeIf { it.isNotBlank() && !subject.contains(it, ignoreCase = true) }
    return listOfNotNull(subject, location).distinct().joinToString(" · ")
}

/**
 * El panel con más de [IMPORT_WARNINGS_FOLD_FROM] avisos sin revisar, plegado: la cuenta, «Ver avisos» e «Ignorar todos». El aviso de
 * la imagen («revisa los números antes de guardar») sale siempre, con su «Entendido».
 */
@Composable
private fun ImportWarningsFolded(
    imported: ImportedProgramDraft,
    pending: List<ImportWarning>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String>,
    useSharedCatalog: Boolean,
    onOpen: () -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onOpenSessionEditor: ((String?) -> Unit)?,
    requester: BringIntoViewRequester,
    modifier: Modifier,
) {
    val imageNotices = pending.filter { it.kind == ImportWarningKind.SHEET_NOTE && it.message.contains(OcrImportSource.IMAGE_NOTE) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .semantics { paneTitle = "Avisos de la importación" }
            .testTag("import-warnings-pending-folded"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Avisos de la importación",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(avisosSinRevisar(pending.size), style = WizardTypography.note, color = WizardColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImportTextAction(label = "Ver avisos", onClick = onOpen, modifier = Modifier.testTag("import-warnings-see"))
            ImportTextAction(
                label = "Ignorar notas informativas",
                onClick = { onEdit(ImportEditOp.IgnoreAllWarnings) },
                modifier = Modifier.testTag("import-warnings-ignore-all"),
                enabled = pending.any { it.kind == ImportWarningKind.SHEET_NOTE },
            )
        }
        imageNotices.forEach { warning ->
            ImportWarningRow(
                warning = warning,
                imported = imported,
                adding = false,
                onAddingChange = {},
                search = search,
                namesOf = namesOf,
                useSharedCatalog = useSharedCatalog,
                onOpenExercise = {},
                onEdit = onEdit,
                onOpenSessionEditor = onOpenSessionEditor,
            )
        }
    }
}

/** Un aviso sin revisar: su ubicación, qué pasó, lo que dice el archivo y sus acciones, o el panel de añadir si está abierto. */
@Composable
private fun ImportWarningRow(
    warning: ImportWarning,
    imported: ImportedProgramDraft,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String>,
    useSharedCatalog: Boolean,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onOpenSessionEditor: ((String?) -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("import-warning-${warning.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        warning.location.takeIf { it.isNotBlank() }?.let { location ->
            Text(location, style = WizardTypography.note, color = WizardColors.textMuted)
        }
        Text(importWarningDetail(warning), style = WizardTypography.bodySmall, color = WizardColors.text)
        importWarningSource(warning)?.let { source ->
            Text(
                "Tu archivo dice «$source${if (warning.sourceTextTruncated) "…" else ""}».",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        warning.itemCount?.let { count ->
            Text("Este aviso resume $count elementos.", style = WizardTypography.note, color = WizardColors.textMuted)
        }
        warning.sourceDocumentType.takeIf { it != com.example.kpkn.domain.training.importer.ImportDocumentType.UNKNOWN }?.let { type ->
            Text("Origen: ${importDocumentTypeLabel(type)}", style = WizardTypography.note, color = WizardColors.textFaint)
        }
        if (adding) {
            ImportWarningAdd(
                warning = warning,
                imported = imported,
                search = search,
                namesOf = namesOf,
                useSharedCatalog = useSharedCatalog,
                onEdit = onEdit,
                onClose = { onAddingChange(false) },
            )
        } else {
            ImportWarningActions(
                warning = warning,
                imported = imported,
                onAdd = { onAddingChange(true) },
                onOpenExercise = onOpenExercise,
                onEdit = onEdit,
                onOpenSessionEditor = onOpenSessionEditor,
            )
        }
    }
}

/** Las acciones de un aviso según su tipo; «Ignorar» y «Entendido» lo dan por revisado. */
@Composable
private fun ImportWarningActions(
    warning: ImportWarning,
    imported: ImportedProgramDraft,
    onAdd: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onOpenSessionEditor: ((String?) -> Unit)?,
) {
    val ignore: () -> Unit = { onEdit(ImportEditOp.IgnoreWarning(warning.id)) }
    // Un valor descartado solo se revisa si su ejercicio sigue en el borrador.
    val exerciseId = warning.exerciseId?.takeIf { importExerciseOf(imported, it) != null }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            warning.kind == ImportWarningKind.STRUCTURE_REVIEW && warning.dayId != null -> {
                ImportTextAction(
                    label = "Asignar día",
                    onClick = { onOpenSessionEditor?.invoke(warning.dayId) },
                    modifier = Modifier.weight(1f).testTag("import-warning-assign-day-${warning.id}"),
                    enabled = onOpenSessionEditor != null,
                )
            }
            warning.kind == ImportWarningKind.INCOMPLETE_ROW && exerciseId != null -> {
                ImportTextAction(
                    label = "Completar",
                    onClick = { onOpenExercise(exerciseId) },
                    modifier = Modifier.weight(1f).testTag("import-warning-complete-${warning.id}"),
                )
                ImportTextAction(
                    label = "Entendido",
                    onClick = ignore,
                    modifier = Modifier.testTag("import-warning-ok-${warning.id}"),
                )
            }
            warning.kind == ImportWarningKind.SKIPPED_ROW -> {
                ImportTextAction(
                    label = if (onOpenSessionEditor == null) "Añadir como ejercicio" else "Abrir editor completo",
                    onClick = {
                        if (onOpenSessionEditor == null) onAdd() else onOpenSessionEditor(warning.dayId)
                    },
                    modifier = Modifier.weight(1f).testTag("import-warning-add-${warning.id}"),
                )
                ImportTextAction(
                    label = "Ignorar",
                    onClick = ignore,
                    modifier = Modifier.testTag("import-warning-ignore-${warning.id}"),
                )
            }
            warning.kind == ImportWarningKind.IGNORED_VALUE && exerciseId != null -> {
                ImportTextAction(
                    label = "Revisar el ejercicio",
                    onClick = { onOpenExercise(exerciseId) },
                    modifier = Modifier.weight(1f).testTag("import-warning-review-${warning.id}"),
                )
                ImportTextAction(
                    label = "Entendido",
                    onClick = ignore,
                    modifier = Modifier.testTag("import-warning-ok-${warning.id}"),
                )
            }
            else -> ImportTextAction(
                label = "Entendido",
                onClick = ignore,
                modifier = Modifier.testTag("import-warning-ok-${warning.id}"),
            )
        }
    }
}

/**
 * «Añadir como ejercicio» de una fila no entendida: el panel de añadir con el nombre propuesto, sus series y repeticiones (las
 * que se lean o 3 × 10) y el selector de día. Al añadir, `AddFromWarning` deja el aviso revisado.
 */
@Composable
private fun ImportWarningAdd(
    warning: ImportWarning,
    imported: ImportedProgramDraft,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String>,
    useSharedCatalog: Boolean,
    onEdit: (ImportEditOp) -> Unit,
    onClose: () -> Unit,
) {
    val days = importWarningDays(imported, warning)
    val reading = remember(warning.id) { importAddReading(warning.sourceText) }
    var target by rememberSaveable(warning.id) { mutableStateOf(importDefaultTarget(warning, days)) }
    ImportAddExercisePanel(
        key = warning.id,
        search = search,
        namesOf = namesOf,
        useSharedCatalog = useSharedCatalog,
        onAdd = { name, configId, sets, repsMin, repsMax ->
            onEdit(
                ImportEditOp.AddFromWarning(
                    warningId = warning.id,
                    toDayId = target.takeIf { it != NEW_DAY_TARGET },
                    rawName = name,
                    sets = sets,
                    repsMin = repsMin,
                    repsMax = repsMax,
                    configId = configId,
                    decided = true,
                    newDay = target == NEW_DAY_TARGET,
                ),
            )
        },
        onClose = onClose,
        initialName = importProposedName(warning.sourceText),
        initialSets = reading.sets,
        initialRepsMin = reading.repsMin,
        initialRepsMax = reading.repsMax,
        leading = { ImportDayTargets(days = days, selected = target, onSelected = { target = it }) },
    )
}

/** «Añadir a:»: los días de la semana del aviso y «Un día nuevo». La elegida lleva «✓». */
@Composable
private fun ImportDayTargets(days: List<ImportedDay>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("import-warning-targets")) {
        Text("Añadir a:", style = WizardTypography.note, color = WizardColors.textMuted)
        days.forEachIndexed { position, day ->
            ImportTargetOption(
                label = importMoveLabelOf(position, day.name),
                chosen = day.id == selected,
                testTag = "import-warning-day-${day.id}",
                onClick = { onSelected(day.id) },
            )
        }
        ImportTargetOption(
            label = "Un día nuevo",
            chosen = selected == NEW_DAY_TARGET,
            testTag = "import-warning-day-new",
            onClick = { onSelected(NEW_DAY_TARGET) },
        )
    }
}

@Composable
private fun ImportTargetOption(label: String, chosen: Boolean, testTag: String, onClick: () -> Unit) {
    ImportTextAction(
        label = if (chosen) "✓ $label" else label,
        onClick = onClick,
        modifier = Modifier.testTag(testTag).semantics { stateDescription = if (chosen) "elegido" else "no elegido" },
    )
}

/** Un aviso ya revisado: atenuado y sin acciones. */
@Composable
private fun ReviewedWarningRow(warning: ImportWarning) {
    Column(modifier = Modifier.fillMaxWidth().testTag("import-warning-reviewed-${warning.id}")) {
        warning.location.takeIf { it.isNotBlank() }?.let { location ->
            Text(location, style = WizardTypography.note, color = WizardColors.textFaint)
        }
        Text(importWarningDetail(warning), style = WizardTypography.note, color = WizardColors.textFaint)
    }
}

private fun sinRevisar(count: Int): String = if (count == 1) "1 sin revisar" else "$count sin revisar"

private fun importDocumentTypeLabel(type: com.example.kpkn.domain.training.importer.ImportDocumentType): String = when (type) {
    com.example.kpkn.domain.training.importer.ImportDocumentType.UNKNOWN -> "Documento"
    com.example.kpkn.domain.training.importer.ImportDocumentType.PASTED_TEXT -> "Texto pegado"
    com.example.kpkn.domain.training.importer.ImportDocumentType.TEXT_FILE -> "Archivo de texto"
    com.example.kpkn.domain.training.importer.ImportDocumentType.CSV -> "CSV"
    com.example.kpkn.domain.training.importer.ImportDocumentType.XLS_BIFF8 -> "Excel .xls"
    com.example.kpkn.domain.training.importer.ImportDocumentType.XLSX -> "Excel .xlsx"
    com.example.kpkn.domain.training.importer.ImportDocumentType.XLSM -> "Excel .xlsm"
    com.example.kpkn.domain.training.importer.ImportDocumentType.ODS -> "ODS"
    com.example.kpkn.domain.training.importer.ImportDocumentType.DOCX -> "Word .docx"
    com.example.kpkn.domain.training.importer.ImportDocumentType.IMAGE,
    com.example.kpkn.domain.training.importer.ImportDocumentType.URL_IMAGE -> "Foto"
    com.example.kpkn.domain.training.importer.ImportDocumentType.PDF,
    com.example.kpkn.domain.training.importer.ImportDocumentType.URL_PDF -> "PDF"
    com.example.kpkn.domain.training.importer.ImportDocumentType.URL_HTML -> "Página web"
}

/** La cuenta del panel plegado: «47 avisos sin revisar.». */
private fun avisosSinRevisar(count: Int): String = "${plural(count, "aviso", "avisos")} sin revisar."

/** El mensaje sin su ubicación, que sale en la línea de arriba: «Hoja «X», fila 4: sin series» → «Sin series». */
internal fun importWarningDetail(warning: ImportWarning): String {
    val prefix = "${warning.location}: "
    val detail = if (warning.location.isNotBlank() && warning.message.startsWith(prefix)) {
        warning.message.removePrefix(prefix)
    } else {
        warning.message
    }
    return detail.replaceFirstChar { it.titlecase() }
}

/**
 * El texto original de la fila, si aporta algo distinto del mensaje. Si el mensaje ya lo cita entre comillas angulares
 * («No se entiende «Hacer 3 vueltas»»), no se repite.
 */
internal fun importWarningSource(warning: ImportWarning): String? {
    val source = warning.sourceText?.trim()?.takeIf { it.isNotEmpty() && it != warning.message.trim() } ?: return null
    return source.takeUnless { warning.message.contains("«$source»") }
}

/** El ejercicio con ese id, o null si ya no está en el borrador. */
internal fun importExerciseOf(imported: ImportedProgramDraft, exerciseId: String): ImportedExercise? =
    imported.weeks.firstNotNullOfOrNull { week -> week.days.firstNotNullOfOrNull { day -> day.exercises.firstOrNull { it.id == exerciseId } } }

/** Los días de la semana del aviso (la 1 si no dice semana): son las opciones de «Añadir a:». */
internal fun importWarningDays(imported: ImportedProgramDraft, warning: ImportWarning): List<ImportedDay> =
    imported.weeks.firstOrNull { it.index == (warning.weekIndex ?: 1) }?.days.orEmpty()

/** El día que sale elegido: el del aviso si está en su semana; si no, el primero; sin días, «Un día nuevo». */
internal fun importDefaultTarget(warning: ImportWarning, days: List<ImportedDay>): String =
    warning.dayId?.takeIf { id -> days.any { it.id == id } } ?: days.firstOrNull()?.id ?: NEW_DAY_TARGET

/** El nombre que se propone para una fila no entendida: el primer tramo de su texto con 3 letras o más, sin cifras ni notación. */
internal fun importProposedName(sourceText: String?): String {
    val segments = sourceText?.split(" · ").orEmpty()
    return segments.firstNotNullOfOrNull { segment ->
        val words = segment.replace(NUMBERS, " ").split(' ').filter { it.isNotBlank() && it.lowercase() !in NOTATION_WORDS }
        val name = words.joinToString(" ").trim(' ', '-', '–', '—', ':', '|', ',', '/', '+', '.', '·', '(', ')')
        name.takeIf { candidate -> candidate.count { it.isLetter() } >= 3 }?.take(PROPOSED_NAME_MAX)?.trim()
    }.orEmpty()
}

/** Series y repeticiones que se leen en el texto de una fila («4 x 8», «3x8-12»); si no hay una notación válida, 3 × 10. */
internal fun importAddReading(sourceText: String?): ImportAddReading {
    val default = ImportAddReading(ADD_SETS_DEFAULT, ADD_REPS_DEFAULT, ADD_REPS_DEFAULT)
    val match = sourceText?.let { SCHEME_IN_TEXT.find(it) } ?: return default
    val sets = match.groupValues[1].toIntOrNull()
    val low = match.groupValues[2].toIntOrNull()
    val high = match.groupValues[3].toIntOrNull() ?: low
    if (sets == null || low == null || high == null) return default
    val valid = sets in 1..ProgramImportParser.MAX_SETS &&
        low in 1..ProgramImportParser.MAX_REPS &&
        high in 1..ProgramImportParser.MAX_REPS &&
        low <= high
    return if (valid) ImportAddReading(sets, low, high) else default
}
