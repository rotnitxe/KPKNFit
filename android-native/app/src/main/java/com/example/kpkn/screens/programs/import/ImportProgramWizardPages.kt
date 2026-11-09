package com.example.kpkn.screens.programs.importer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import com.example.kpkn.domain.training.importer.ImportSourceEvidence
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.domain.training.importer.ImportReadingProgress
import com.example.kpkn.domain.training.importer.ImportReadingStage
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedWeek
import com.example.kpkn.screens.programs.ProgramLineIcons
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.DumbbellIcon
import com.example.kpkn.ui.components.icons.KpknLineIcons

private val ImportPrototypeText = Color(0xFFF2EEE6)
private val ImportPrototypeMuted = Color(0xFFC4BFB6)
private val ImportPrototypeFaint = Color(0xFF8E8980)
private val ImportPrototypeModule = Color(0xFF111111)
private val ImportPrototypeInset = Color(0xFF1A1A1A)
private val ImportPrototypeLine = Color.White.copy(alpha = 0.07f)
private val ImportPrototypeGlass = Color(0x991B1B1D)
private val ImportPrototypeGlassBorder = Color.White.copy(alpha = 0.133f)
private val ImportPrototypeDockSecondary = Color.White.copy(alpha = 0.06f)
private val ImportPrototypeDockSecondaryBorder = Color.White.copy(alpha = 0.12f)
private val ImportPrototypeDockCtaTop = Color(0xFFF4F0E9)
private val ImportPrototypeDockCtaBottom = Color(0xFFD6D0C6)
private val ImportPrototypeDockDisabled = Color(0xFF2E2E2E)
private val ImportPrototypeDockDisabledText = Color(0xFF7A7A7A)
private val ImportPrototypeAccent = Color(0xFFF49A6E)
private val ImportPrototypeEnergy = Color(0xFFF7CF73)
private val ImportPrototypeOk = Color(0xFF43D18C)
private val ImportPrototypeDanger = Color(0xFFFF9B92)
private val ImportPrototypeBlue = Color(0xFF74B5FF)
private val ImportCardShape = RoundedCornerShape(24.dp)

internal fun importCountNoun(count: Int, singular: String, plural: String): String =
    if (count == 1) singular else plural

internal fun importCountLabel(count: Int, singular: String, plural: String): String =
    "$count ${importCountNoun(count, singular, plural)}"

internal fun importReviewedSessionsLabel(reviewed: Int, total: Int): String =
    "$reviewed de ${importCountLabel(total, "sesión", "sesiones")} ${if (total == 1) "revisada" else "revisadas"}"

internal fun importSessionsLocatedLabel(count: Int): String =
    "${importCountLabel(count, "sesión", "sesiones")} ${if (count == 1) "ubicada" else "ubicadas"} por semana"

internal fun importWeekDaySummary(
    imported: ImportedProgramDraft,
    weekdays: Set<Int>,
    selectedWeekIndex: Int,
): String {
    val sessions = imported.weeks.firstOrNull { it.index == selectedWeekIndex }?.days.orEmpty().filterNot { it.isRestDay }
    val assignmentMap = effectiveImportedWeekdays(imported, weekdays)
    val assignedDayCount = sessions.mapNotNull { assignmentMap[it.id] }.distinct().size
    val unassignedCount = sessions.count { assignmentMap[it.id] == null }
    val restDays = (7 - assignedDayCount - unassignedCount).coerceAtLeast(0)
    return "${importCountLabel(sessions.size, "sesión", "sesiones")} · $restDays descansos"
}

internal val ImportPageTitleStyle = WizardTypography.stepTitle.copy(
    fontSize = 26.sp,
    lineHeight = 28.08.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.8).sp,
)

internal val ImportPageBodyStyle = WizardTypography.body.copy(
    fontSize = 13.sp,
    lineHeight = 20.15.sp,
)

internal fun importSessionDisplayName(dayNumber: Int, rawName: String): String {
    val explicitDay = Regex("(?i)^\\s*día\\s+(\\d+)\\s*(?:[·:—–-]\\s*)?(.*)$").matchEntire(rawName)
    val sourceDayNumber = explicitDay?.groupValues?.get(1)?.toIntOrNull() ?: dayNumber
    val cleanName = explicitDay?.groupValues?.get(2)?.trim() ?: rawName.trim()
    return if (cleanName.isBlank()) "Día $sourceDayNumber" else "Día $sourceDayNumber · $cleanName"
}

@Composable
internal fun ImportWizardHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(10.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(ImportPrototypeGlass)
            .border(1.dp, ImportPrototypeGlassBorder, RoundedCornerShape(30.dp))
            .padding(4.dp)
            .testTag("import-page-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.055f))
                .clickable(role = Role.Button, onClick = onBack)
                .testTag("import-back")
                .semantics { contentDescription = "Atrás" },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                KpknLineIcons.ChevronLeft,
                contentDescription = null,
                tint = ImportPrototypeText,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = WizardTypography.cardTitle.copy(fontSize = 14.5.sp, lineHeight = 16.7.sp),
                color = ImportPrototypeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(
                    it,
                    style = WizardTypography.note.copy(fontSize = 11.5.sp, lineHeight = 13.8.sp),
                    color = ImportPrototypeFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action,
                    color = ImportPrototypeText,
                    style = WizardTypography.note.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
internal fun ImportWizardStepProgress(step: Int, summary: String, totalSteps: Int = 4) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("import-step-$step"),
    ) {
        Text(
            "PASO $step DE $totalSteps",
            style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.5.sp),
            color = ImportPrototypeFaint,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 138.dp).weight(1f, fill = false)) {
            (1..totalSteps).forEach { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                index < step -> ImportPrototypeAccent.copy(alpha = 0.62f)
                                index == step -> ImportPrototypeText
                                else -> ImportPrototypeInset
                            },
                        ),
                )
            }
        }
        Text(
            summary,
            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
            color = ImportPrototypeMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun ImportWizardPanel(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrowTitle: Boolean = false,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(ImportCardShape).background(ImportPrototypeModule).border(1.dp, ImportPrototypeLine, ImportCardShape)
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (headerAction != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                title?.let {
                    Text(
                        if (eyebrowTitle) it.uppercase() else it,
                        style = if (eyebrowTitle) WizardTypography.eyebrow.copy(fontSize = 10.5.sp, lineHeight = 16.sp) else WizardTypography.controlLabel,
                        color = ImportPrototypeFaint,
                    )
                }
                Spacer(Modifier.weight(1f))
                headerAction()
            }
            subtitle?.let { Text(it, style = WizardTypography.note, color = ImportPrototypeMuted) }
        } else if (title != null || subtitle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                title?.let {
                    Text(
                        if (eyebrowTitle) it.uppercase() else it,
                        style = if (eyebrowTitle) WizardTypography.eyebrow.copy(fontSize = 10.5.sp, lineHeight = 16.sp) else WizardTypography.controlLabel,
                        color = if (eyebrowTitle) ImportPrototypeFaint else ImportPrototypeText,
                    )
                }
                subtitle?.let { Text(it, style = WizardTypography.note, color = ImportPrototypeMuted) }
            }
        }
        content()
    }
}

@Composable
internal fun ImportEntryPage(
    imported: ImportedProgramDraft?,
    evidence: ImportSourceEvidence,
    pendingDecisions: Int = 0,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    onUrl: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onResume: () -> Unit,
    onStartEmpty: () -> Unit,
    onPlanLimits: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImportWizardBackButton(onBack)
            Spacer(Modifier.weight(1f))
            Text("?", color = ImportPrototypeMuted, modifier = Modifier.clickable(onClick = onPlanLimits).padding(12.dp))
        }
        Text("PROGRAMAS · NUEVO PROGRAMA", style = WizardTypography.eyebrow, color = ImportPrototypeMuted)
        Text(
            "Tu plan, tal como lo escribiste.",
            style = WizardTypography.stepTitle.copy(fontSize = 22.sp, lineHeight = 24.2.sp, letterSpacing = (-0.7).sp),
            color = ImportPrototypeText,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "KPKN lo lee, ordena las sesiones y te deja revisar cada ejercicio antes de guardarlo.",
            style = WizardTypography.body.copy(fontSize = 14.5.sp, lineHeight = 22.5.sp),
            color = ImportPrototypeMuted,
        )
        ImportEntryProof(imported)
        if (imported != null) {
            ImportWizardPanel(modifier = Modifier.clickable(role = Role.Button, onClick = onResume).testTag("import-resume-card")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(ImportPrototypeModule),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(KpknLineIcons.Book, contentDescription = null, tint = ImportPrototypeAccent, modifier = Modifier.size(17.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(imported.title, style = WizardTypography.cardTitle.copy(fontSize = 14.sp, lineHeight = 18.sp), color = ImportPrototypeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(importExerciseCount(imported) - pendingDecisions).coerceAtLeast(0)} de ${importExerciseCount(imported)} decididos · borrador guardado",
                            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                            color = ImportPrototypeMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("Reanudar  ›", style = WizardTypography.note.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeAccent)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("ELIGE UNA FUENTE", style = WizardTypography.eyebrow, color = ImportPrototypeMuted)
            ImportSourceTile(
                title = "Archivo",
                subtitle = "Excel, CSV, Word, PDF o texto",
                icon = ProgramLineIcons.Document,
                accent = ImportPrototypeAccent,
                onClick = onPickFile,
                modifier = Modifier.testTag("import-entry-file"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ImportSourceTile(
                    title = "Pegar texto",
                    subtitle = "Día 1 · Pierna\nSentadilla 4×6 @120 kg",
                    icon = ProgramLineIcons.Paste,
                    accent = ImportPrototypeEnergy,
                    onClick = onPaste,
                    vertical = true,
                    modifier = Modifier.weight(1f).heightIn(min = 132.dp).testTag("import-entry-paste"),
                )
                ImportWizardPanel(modifier = Modifier.weight(1f).heightIn(min = 132.dp)) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(ImportPrototypeBlue.copy(alpha = 0.08f))
                            .border(1.dp, ImportPrototypeBlue.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(KpknLineIcons.Camera, contentDescription = null, tint = ImportPrototypeBlue, modifier = Modifier.size(18.dp))
                    }
                    Text("Foto", style = WizardTypography.controlLabel, color = ImportPrototypeText)
                    Text("Haz una foto o elige una de la galería", style = WizardTypography.note, color = ImportPrototypeMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImportTextAction(label = "Cámara", onClick = onCamera, modifier = Modifier.testTag("import-entry-camera"))
                        ImportTextAction(label = "Galería", onClick = onGallery, modifier = Modifier.testTag("import-entry-gallery"))
                    }
                }
            }
            Text(
                "En fotos, el OCR funciona mejor con texto impreso y bien enfocado. La escritura a mano puede requerir que corrijas o pegues el texto.",
                style = WizardTypography.note,
                color = ImportPrototypeMuted,
                modifier = Modifier.testTag("import-entry-photo-limit"),
            )
            ImportTextAction(label = "Importar desde un enlace público", onClick = onUrl, modifier = Modifier.testTag("import-entry-url"), color = ImportPrototypeBlue)
            ImportTextAction(label = "Empezar de cero", onClick = onStartEmpty, modifier = Modifier.testTag("import-start-empty"), color = ImportPrototypeMuted)
        }
        Text(
            "Nada se guarda hasta que confirmes.  ·  Límites del plan",
            style = WizardTypography.note,
            color = ImportPrototypeMuted,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPlanLimits).padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ImportEntryProof(imported: ImportedProgramDraft?) {
    val weeks = imported?.weeks?.size ?: 3
    val sessions = imported?.weeks?.sumOf { week -> week.days.count { !it.isRestDay } } ?: 12
    val exercises = imported?.let(::importExerciseCount) ?: 66
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ImportCardShape)
            .background(Brush.linearGradient(listOf(ImportPrototypeAccent.copy(alpha = 0.09f), ImportPrototypeModule)))
            .border(1.dp, ImportPrototypeAccent.copy(alpha = 0.14f), ImportCardShape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 36.dp, height = 44.dp).clip(RoundedCornerShape(9.dp))
                .background(ImportPrototypeInset).border(1.dp, ImportPrototypeGlassBorder, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ProgramLineIcons.Document, contentDescription = null, tint = ImportPrototypeAccent, modifier = Modifier.size(16.dp))
        }
        Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = ImportPrototypeMuted, modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ImportEntryMetric(weeks.toString(), importCountNoun(weeks, "semana", "semanas"), Modifier.weight(1f))
                ImportEntryMetric(sessions.toString(), importCountNoun(sessions, "sesión", "sesiones"), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ImportEntryMetric(exercises.toString(), importCountNoun(exercises, "ejercicio", "ejercicios"), Modifier.widthIn(min = 106.dp))
            }
        }
    }
}

@Composable
private fun ImportEntryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = WizardTypography.cardTitle.copy(fontSize = 14.sp, lineHeight = 16.sp), color = ImportPrototypeText)
            Text(label, style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp), color = ImportPrototypeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ImportWizardBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ImportPrototypeModule)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Atrás" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(KpknLineIcons.ChevronLeft, contentDescription = null, tint = ImportPrototypeText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ImportSourceTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth().heightIn(min = 116.dp).clip(shape).background(ImportPrototypeModule)
                .border(1.dp, ImportPrototypeLine, shape).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.08f))
                    .border(1.dp, accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(title, color = ImportPrototypeText, style = WizardTypography.cardTitle.copy(fontSize = 13.5.sp, lineHeight = 17.sp))
            Text(subtitle, color = ImportPrototypeFaint, style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp))
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 78.dp).clip(ImportCardShape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.13f), ImportPrototypeModule)))
                .border(1.dp, accent.copy(alpha = 0.24f), ImportCardShape)
                .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = ImportPrototypeText, style = WizardTypography.stepTitle.copy(fontSize = 16.sp, lineHeight = 19.sp))
                Text(subtitle, color = ImportPrototypeMuted, style = WizardTypography.note.copy(fontSize = 11.5.sp, lineHeight = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = ImportPrototypeMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ImportPastePage(
    text: String,
    onTextChange: (String) -> Unit,
    onExample: () -> Unit,
    onRead: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Pega tu rutina.",
            style = ImportPageTitleStyle,
            color = ImportPrototypeText,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        Text(
            "Acepta días, ejercicios, series, repeticiones, cargas y descansos. No hace falta un formato exacto.",
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Text(
                "TEXTO DEL PROGRAMA",
                style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.4.sp),
                color = ImportPrototypeFaint,
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 20.6.sp),
                placeholder = {
                    Text(
                        "Día 1 — Pierna\nSentadilla 4×6 @120 kg\nPrensa 3×10",
                        style = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 20.6.sp),
                        color = ImportPrototypeMuted,
                    )
                },
                minLines = 6,
                maxLines = 13,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0B0B0B),
                    unfocusedContainerColor = Color(0xFF0B0B0B),
                    focusedTextColor = ImportPrototypeText,
                    unfocusedTextColor = ImportPrototypeText,
                    focusedBorderColor = ImportPrototypeLine,
                    unfocusedBorderColor = ImportPrototypeLine,
                    cursorColor = ImportPrototypeAccent,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp).testTag("import-paste-field"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(text.count { it == '\n' } + if (text.isEmpty()) 0 else 1)} líneas",
                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.4.sp),
                color = ImportPrototypeFaint,
            )
            ImportTextAction("Usar un ejemplo", onExample, modifier = Modifier.testTag("import-paste-example"), color = ImportPrototypeAccent)
        }
        ImportCta(
            "Leer el texto",
            enabled = text.isNotBlank(),
            onClick = onRead,
            testTag = "import-read-text",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            trailingArrow = true,
        )
        ImportWizardPanel(
            title = "Qué reconoce",
            eyebrowTitle = true,
            headerAction = {
                Box(
                    modifier = Modifier.height(28.dp).clip(CircleShape)
                        .background(ImportPrototypeOk.copy(alpha = 0.11f))
                        .border(1.dp, ImportPrototypeOk.copy(alpha = 0.2f), CircleShape)
                        .padding(horizontal = 10.dp),
                ) {
                    Text("Local", style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 13.sp), color = ImportPrototypeOk)
                }
            },
            modifier = Modifier.padding(top = 18.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(ImportPrototypeLine))
            Text(
                "Días, ejercicios, series × repeticiones, kg/lb, %1RM, RPE, RIR, descansos y notas. Lo que no entienda quedará como aviso, no se inventará.",
                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.4.sp),
                color = ImportPrototypeFaint,
            )
        }
    }
}

@Composable
internal fun ImportReadingPage(
    readingProgress: ImportReadingProgress,
    detail: String?,
    onCancel: () -> Unit,
    evidence: ImportSourceEvidence? = null,
    completed: Boolean = false,
) {
    val stageIndex = readingProgress.stage.ordinal
    val stageNames = listOf("Documento", "Estructura", "Ejercicios", "Catálogo", "Días")
    val stageDetails = listOf(
        when {
            evidence?.sheetCount != null && evidence.rowCount != null -> "${evidence.sheetCount} hojas · ${evidence.rowCount} filas"
            evidence?.sheetCount != null -> importCountLabel(evidence.sheetCount, "hoja", "hojas")
            evidence?.pageCount != null -> importCountLabel(evidence.pageCount, "página", "páginas")
            readingProgress.currentPage != null && readingProgress.totalPages != null ->
            "Página " + readingProgress.currentPage + " de " + readingProgress.totalPages
            else -> "Leyendo el origen"
        },
        readingProgress.weeks?.let {
            importCountLabel(it, "semana", "semanas") + " · " + importCountLabel(readingProgress.sessions ?: 0, "sesión", "sesiones")
        }
            ?: "Ordenando semanas y sesiones",
        readingProgress.exercises?.let { it.toString() + " ejercicios encontrados" } ?: "Buscando ejercicios",
        readingProgress.matchedNames?.let { it.toString() + " coincidencias seguras" } ?: "Comparando con el catálogo compartido",
        readingProgress.sessions?.let(::importSessionsLocatedLabel) ?: "Revisando la distribución semanal",
    )
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 680.dp).padding(top = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancel)
                    .semantics { contentDescription = "Cancelar" }
                    .testTag("import-cancel"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Close, contentDescription = null, tint = ImportPrototypeMuted, modifier = Modifier.size(18.dp))
            }
            Text("IMPORTANDO", style = WizardTypography.eyebrow.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = ImportPrototypeMuted)
            Text(
                "Cancelar",
                style = WizardTypography.note.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = ImportPrototypeMuted,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
        ImportReadingVisual()
        Text(
            if (completed) "Listo. Vamos a revisarlo." else "Leyendo tu programa",
            style = WizardTypography.stepTitle.copy(fontSize = 25.sp, lineHeight = 27.5.sp, letterSpacing = (-0.7).sp),
            color = ImportPrototypeText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            if (completed) (detail ?: "Listo. Abriendo el resumen…") else "Análisis local · sin subir el documento",
            style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = ImportPrototypeMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(
            progress = { if (completed) 1f else (stageIndex + 1) / 5f },
            color = ImportPrototypeAccent,
            trackColor = ImportPrototypeInset,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(3.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Análisis local · sin subir el documento",
                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = ImportPrototypeFaint,
            )
            Text(
                if (completed) "100 %" else "${((stageIndex + 1) * 20)} %",
                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = ImportPrototypeFaint,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull(
                readingProgress.weeks?.let { Triple(it.toString(), importCountNoun(it, "Semana", "Semanas"), "weeks") },
                readingProgress.sessions?.let { Triple(it.toString(), importCountNoun(it, "Sesión", "Sesiones"), "sessions") },
                readingProgress.exercises?.let { Triple(it.toString(), importCountNoun(it, "Ejercicio", "Ejercicios"), "exercises") },
            ).forEach { (value, label, tag) ->
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(ImportPrototypeModule)
                        .border(1.dp, ImportPrototypeLine, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("import-reading-count-$tag"),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(value, style = WizardTypography.controlValue.copy(fontSize = 19.sp, lineHeight = 20.sp), color = ImportPrototypeText)
                    Text(label, style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp), color = ImportPrototypeMuted)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            stageNames.forEachIndexed { index, title ->
                ImportReadingStage(
                    title = title,
                    detail = stageDetails[index],
                    done = completed || index < stageIndex,
                    active = !completed && index == stageIndex,
                )
            }
        }
        Text(
            "El análisis ocurre en tu teléfono. Nada se envía.",
            style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
            color = ImportPrototypeFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).testTag("import-reading-footer"),
        )
    }
}

@Composable
private fun ImportReadingVisual() {
    val reducedMotion = wizardReducedMotion()
    val scanPosition = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "importDocumentScan")
        val position by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "importDocumentScanPosition",
        )
        position
    }
    Box(modifier = Modifier.size(206.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp).clip(CircleShape).border(1.dp, ImportPrototypeAccent.copy(alpha = 0.14f), CircleShape))
        Box(Modifier.size(156.dp).clip(CircleShape).border(1.dp, ImportPrototypeAccent.copy(alpha = 0.2f), CircleShape))
        Box(Modifier.size(112.dp).clip(CircleShape).border(1.dp, ImportPrototypeAccent.copy(alpha = 0.28f), CircleShape))
        Box(
            modifier = Modifier
                .size(width = 78.dp, height = 108.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ImportPrototypeModule)
                .border(1.dp, ImportPrototypeLine, RoundedCornerShape(12.dp)),
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 3) 0.54f else if (index == 1) 0.72f else 0.9f)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(ImportPrototypeMuted.copy(alpha = if (index == 0) 0.8f else 0.35f)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = scanPosition.times(82f).dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ImportPrototypeAccent),
            )
        }
    }
}

@Composable
private fun ImportReadingStage(title: String, detail: String, done: Boolean, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier.size(14.dp).clip(CircleShape)
                .background(if (done) ImportPrototypeOk.copy(alpha = 0.15f) else if (active) ImportPrototypeAccent.copy(alpha = 0.14f) else ImportPrototypeDockSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else if (active) "·" else "",
                style = WizardTypography.note.copy(fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold),
                color = if (done) ImportPrototypeOk else ImportPrototypeAccent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (done || active) ImportPrototypeText else ImportPrototypeMuted,
            )
            Text(detail, style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp), color = ImportPrototypeFaint)
        }
    }
}

@Composable
internal fun ImportSummaryPage(
    imported: ImportedProgramDraft,
    title: String,
    onTitleChange: (String) -> Unit,
    resolutions: Map<String, String?>,
    weekdays: Set<Int>,
    freeLimitReached: Boolean,
    draftPersistencePending: Boolean,
    draftPersistenceError: String?,
    pendingDecisions: Int,
    pendingWarnings: Int,
    onStructure: () -> Unit,
    onReviewMatches: () -> Unit,
    onReviewWarnings: () -> Unit,
    onPlanLimits: () -> Unit,
) {
    val sessions = imported.weeks.sumOf { week -> week.days.count { !it.isRestDay } }
    val exercises = importExerciseCount(imported)
    val unresolved = imported.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
        .filter { it.catalogSelectionId == null && !resolutions.containsKey(it.rawName) }
    val unresolvedNames = unresolved.map { it.rawName }.distinct()
    val pendingWarningRows = imported.warnings.filter { it.id !in imported.reviewedWarnings }
    val assignments = effectiveImportedWeekdays(imported, weekdays)
    val sessionsPerWeek = imported.weeks.map { week -> week.days.count { !it.isRestDay } }
    val weeklySessionLabel = sessionsPerWeek.distinct().singleOrNull()?.let { "$it por semana" } ?: "varía por semana"
    Column(verticalArrangement = Arrangement.spacedBy(15.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("LO QUE HEMOS LEÍDO", style = WizardTypography.eyebrow, color = ImportPrototypeFaint)
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(
                    when {
                        draftPersistenceError != null -> ImportPrototypeDanger
                        draftPersistencePending -> ImportPrototypeEnergy
                        else -> ImportPrototypeOk
                    },
                ),
            )
            Text(
                when {
                    draftPersistencePending -> "Guardando…"
                    draftPersistenceError != null -> "No se guardó"
                    else -> "Borrador guardado"
                },
                style = WizardTypography.note,
                color = if (draftPersistenceError == null) ImportPrototypeMuted else ImportPrototypeDanger,
            )
        }
        ImportTitleField(title, onTitleChange)
        Text("Puedes cambiarlo ahora o desde Programas.", style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 16.sp), color = ImportPrototypeFaint)
        Column(
            modifier = Modifier.fillMaxWidth().clip(ImportCardShape).background(ImportPrototypeModule)
                .border(1.dp, ImportPrototypeLine, ImportCardShape)
                .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportMetric("${imported.weeks.size}", "de entrenamiento", Modifier.weight(1f), kicker = "SEMANAS")
                ImportMetric("$sessions", weeklySessionLabel, Modifier.weight(1f), kicker = "SESIONES")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportMetric("$exercises", "en el programa", Modifier.weight(1f), kicker = "EJERCICIOS")
                ImportMetric("$pendingDecisions", "necesitan tu elección", Modifier.weight(1f), kicker = "POR DECIDIR", valueColor = ImportPrototypeEnergy)
            }
        }
        ImportWizardPanel(
            title = "Estructura",
            eyebrowTitle = true,
            headerAction = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onStructure)
                        .padding(horizontal = 2.dp, vertical = 5.dp)
                        .testTag("import-summary-structure"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Editar", style = WizardTypography.note.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp), color = ImportPrototypeText)
                    Text("›", style = WizardTypography.note.copy(fontSize = 17.sp, lineHeight = 17.sp), color = ImportPrototypeText)
                }
            },
        ) {
            imported.weeks.take(6).forEachIndexed { index, week ->
                val weekSessions = week.days.filterNot { it.isRestDay }
                val kind = when {
                    week.executionKind.name.contains("DELOAD", ignoreCase = true) -> " · descarga"
                    week.executionKind.name.contains("REST", ignoreCase = true) -> " · descanso"
                    else -> ""
                }
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
                ImportSummaryWeek(
                    weekIndex = week.index,
                    detail = weekSessions.mapNotNull { day ->
                        val dayName = day.name.replaceFirst(
                            Regex("(?i)^\\s*día\\s+\\d+\\s*(?:[·:—–-]\\s*)?"),
                            "",
                        ).trim().ifBlank { day.name }
                        assignments[day.id]?.let(::weekdayShort)?.let { "$it $dayName" } ?: dayName
                    }.ifEmpty { listOf("Sin sesiones de entrenamiento") }.joinToString(" · "),
                    sessionCount = weekSessions.size,
                    activeWeekdays = weekSessions.mapNotNull { assignments[it.id] }.toSet(),
                    kind = kind,
                )
            }
            if (imported.weeks.size > 6) Text("+ ${importCountLabel(imported.weeks.size - 6, "semana", "semanas")}", style = WizardTypography.note, color = ImportPrototypeMuted)
        }
        ImportWizardPanel(title = "Por decidir", eyebrowTitle = true, subtitle = if (pendingDecisions == 0) "Todo resuelto" else "${importCountLabel(pendingDecisions, "ejercicio", "ejercicios")} pendientes") {
            if (unresolvedNames.isNotEmpty()) {
                Text(
                    if (unresolvedNames.size == 1) "Un nombre puede tener varias variantes." else "${unresolvedNames.size} nombres pueden tener varias variantes.",
                    style = WizardTypography.controlLabel.copy(fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp),
                    color = ImportPrototypeEnergy,
                )
                Text("Conservamos el texto original: ${unresolvedNames.take(3).joinToString(" · ") { "«$it»" }}", style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.sp), color = ImportPrototypeMuted)
            } else {
                Text("Los ejercicios reconocidos quedan en el borrador.", style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 17.sp), color = ImportPrototypeOk)
            }
            if (pendingDecisions > 0) ImportTextAction("Revisar los $pendingDecisions", onReviewMatches, modifier = Modifier.testTag("import-summary-review-matches"), color = ImportPrototypeAccent)
        }
        ImportWizardPanel(title = "Avisos del documento", eyebrowTitle = true, subtitle = "$pendingWarnings sin revisar") {
            if (pendingWarningRows.isEmpty()) {
                Text("No hay avisos pendientes.", style = WizardTypography.note, color = ImportPrototypeMuted)
            } else {
                pendingWarningRows.take(2).forEach { warning ->
                    Text(warning.message, style = WizardTypography.note, color = ImportPrototypeText)
                }
                ImportTextAction("Ver los $pendingWarnings", onReviewWarnings, modifier = Modifier.testTag("import-summary-review-warnings"), color = ImportPrototypeAccent)
            }
        }
        ImportWizardPanel(title = "KPKN Pro", eyebrowTitle = true, subtitle = "${importCountLabel(imported.weeks.size, "semana", "semanas")} · ${importCountLabel(sessions, "sesión", "sesiones")}") {
            Text(
                if (freeLimitReached) "Este programa supera el límite Free. Puedes seguir revisándolo y decidir después."
                else "Incluido en tu plan. El programa se guardará como borrador.",
                style = WizardTypography.note,
                color = ImportPrototypeMuted,
            )
            ImportTextAction("¿Por qué?", onPlanLimits, modifier = Modifier.testTag("import-summary-plan-limits"), color = ImportPrototypeAccent)
        }
    }
}

@Composable
private fun ImportSummaryWeek(
    weekIndex: Int,
    detail: String,
    sessionCount: Int,
    activeWeekdays: Set<Int>,
    kind: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Semana $weekIndex$kind", style = WizardTypography.cardSubtitle.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeText)
                Text(detail, style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = ImportPrototypeFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(ImportPrototypeInset)
                    .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(importCountLabel(sessionCount, "sesión", "sesiones"), style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp), color = ImportPrototypeMuted, maxLines = 1)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (1..7).forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (day in activeWeekdays) ImportPrototypeAccent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f)),
                )
            }
        }
    }
}

@Composable
internal fun ImportTitleField(title: String, onTitleChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "TÍTULO DEL PROGRAMA",
            style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.4.sp),
            color = ImportPrototypeFaint,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle = WizardTypography.cardTitle.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0D0D0D),
                unfocusedContainerColor = Color(0xFF0D0D0D),
                focusedTextColor = ImportPrototypeText,
                unfocusedTextColor = ImportPrototypeText,
                focusedBorderColor = ImportPrototypeAccent.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("import-title"),
        )
    }
}

@Composable
private fun ImportMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    valueColor: Color = ImportPrototypeText,
) {
    val compactKpi = kicker != null
    val metricShape = RoundedCornerShape(if (compactKpi) 18.dp else 14.dp)
    Column(
        modifier = modifier.clip(metricShape).background(ImportPrototypeInset)
            .then(if (compactKpi) Modifier else Modifier.border(1.dp, ImportPrototypeLine, metricShape))
            .padding(vertical = if (compactKpi) 11.dp else 12.dp, horizontal = 13.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        kicker?.let { Text(it, style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp), color = ImportPrototypeFaint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(
            value,
            style = if (compactKpi) WizardTypography.controlValue.copy(fontSize = 24.sp, lineHeight = 24.sp, letterSpacing = (-0.8).sp)
            else WizardTypography.cardTitle.copy(fontSize = 21.sp),
            color = valueColor,
        )
        Text(label, style = WizardTypography.note.copy(fontSize = if (compactKpi) 10.5.sp else 13.sp, lineHeight = if (compactKpi) 13.5.sp else 18.sp), color = if (compactKpi) ImportPrototypeFaint else ImportPrototypeMuted)
    }
}

@Composable
internal fun ImportDaysIntro(
    summary: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().testTag("import-week-distribution-section"),
    ) {
        Text("Distribución semanal", style = WizardTypography.cardTitle, color = ImportPrototypeText)
        Text(
            "$summary · Comprueba los días y corrige solo lo que no coincida con tu documento.",
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
        )
    }
}

@Composable
internal fun ImportWeeklyPlanner(
    imported: ImportedProgramDraft,
    weekdays: Set<Int>,
    selectedWeekIndex: Int,
    onWeekSelected: (Int) -> Unit,
    onAssign: (String, Int) -> Unit,
    onSuggest: (Int) -> Unit,
    onApplyToAll: (Int) -> Unit,
    onApplyPatternToWeek: (Int, Int) -> Unit,
    onOpenMenu: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = imported.weeks.sortedBy { it.index }
    val week = weeks.firstOrNull { it.index == selectedWeekIndex } ?: weeks.firstOrNull() ?: return
    var selectedSessionId by rememberSaveable(week.index) { mutableStateOf<String?>(null) }
    var confirmApplyToAll by rememberSaveable(week.index) { mutableStateOf(false) }
    val sessions = week.days.filterNot { it.isRestDay }
    val assignments = effectiveImportedWeekdays(imported, weekdays)
    val sessionsByDay = sessions.groupBy { assignments[it.id] }
    val collisionDays = sessionsByDay.filterValues { it.size > 1 }.keys.filterNotNull().toSet()
    val assignedSessionCount = sessions.count { assignments[it.id]?.let { day -> day in 1..7 } == true }
    val unassignedSessionCount = sessions.size - assignedSessionCount
    val restDayCount = (7 - sessionsByDay.keys.filterNotNull().size - unassignedSessionCount).coerceAtLeast(0)
    val firstWeek = weeks.firstOrNull()
    val selectedPattern = sessions.map { day -> assignments[day.id] }
    val firstPattern = firstWeek?.days.orEmpty().filterNot { it.isRestDay }.map { day -> assignments[day.id] }
    val differsFromFirstWeek = firstWeek != null && week.index != firstWeek.index && selectedPattern != firstPattern

    Column(
        modifier = modifier.fillMaxWidth().testTag("import-week-planner"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            weeks.forEach { item ->
                ImportWeekTabChip(
                    label = "Semana ${item.index}",
                    selected = item.index == week.index,
                    onClick = { onWeekSelected(item.index) },
                    modifier = Modifier.testTag("import-week-tab-${item.index}"),
                )
            }
            if (weeks.size > 1) {
                ImportWeekTabChip(
                    label = "Aplicar a todas",
                    selected = false,
                    onClick = { confirmApplyToAll = true },
                    modifier = Modifier.testTag("import-week-apply-all"),
                )
            }
        }
        if (differsFromFirstWeek && firstWeek != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Solo esta semana", style = WizardTypography.note, color = ImportPrototypeEnergy)
                ImportTextAction(
                    "Igualar a la semana 1",
                    onClick = { onApplyPatternToWeek(firstWeek.index, week.index) },
                    modifier = Modifier.testTag("import-week-match-first"),
                    color = ImportPrototypeMuted,
                )
            }
        }
        if (confirmApplyToAll) {
            val affectedWeeks = weeks.filter { it.index != week.index }.joinToString(", ") { "${it.index}" }
            Row(
                modifier = Modifier.fillMaxWidth().testTag("import-week-apply-all-confirmation"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Copiará este reparto a las semanas $affectedWeeks. Puedes deshacerlo.",
                    style = WizardTypography.note,
                    color = ImportPrototypeMuted,
                    modifier = Modifier.weight(1f),
                )
                ImportTextAction(
                    "Confirmar",
                    onClick = { onApplyToAll(week.index); confirmApplyToAll = false },
                    modifier = Modifier.testTag("import-week-apply-all-confirm"),
                )
                ImportTextAction(
                    "Cancelar",
                    onClick = { confirmApplyToAll = false },
                    modifier = Modifier.testTag("import-week-apply-all-cancel"),
                    color = ImportPrototypeMuted,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(ImportCardShape)
                .background(ImportPrototypeModule)
                .border(1.dp, ImportPrototypeLine, ImportCardShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (1..7).forEach { weekday ->
                    val daySessions = sessionsByDay[weekday].orEmpty()
                    val dayName = importWeekdayName(weekday)
                    val label = dayName + ", " + daySessions.size + if (daySessions.size == 1) " sesión" else " sesiones"
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (daySessions.isNotEmpty()) ImportPrototypeInset else ImportPrototypeModule)
                            .border(
                                1.dp,
                                when {
                                    daySessions.size > 1 -> ImportPrototypeEnergy
                                    selectedSessionId != null -> ImportPrototypeAccent.copy(alpha = 0.45f)
                                    daySessions.isNotEmpty() -> ImportPrototypeAccent.copy(alpha = 0.2f)
                                    else -> ImportPrototypeLine
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .clickable(role = Role.Button, onClick = {
                                val selected = selectedSessionId
                                if (selected != null) {
                                    onAssign(selected, weekday)
                                    selectedSessionId = null
                                }
                            })
                            .semantics { contentDescription = label }
                            .testTag("import-weekday-" + weekday)
                            .padding(vertical = 6.dp, horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(weekdayShort(weekday), style = WizardTypography.note.copy(fontSize = 10.sp), color = ImportPrototypeMuted)
                        Text(
                            if (daySessions.isEmpty()) "—" else daySessions.size.toString(),
                            style = WizardTypography.controlLabel.copy(fontSize = 13.sp),
                            color = if (daySessions.size > 1) ImportPrototypeEnergy else ImportPrototypeText,
                        )
                        Box(
                            Modifier.size(4.dp).clip(CircleShape)
                                .background(if (daySessions.isNotEmpty()) ImportPrototypeAccent else Color.White.copy(alpha = 0.12f)),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    importCountLabel(sessions.size, "sesión", "sesiones") + " · " + restDayCount + " descansos",
                    style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
                    color = ImportPrototypeMuted,
                )
                if (collisionDays.isNotEmpty()) {
                    Text(
                        collisionDays.size.toString() + " días con choque",
                        style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
                        color = ImportPrototypeEnergy,
                        modifier = Modifier.testTag("import-week-collisions"),
                    )
                }
            }
            val weekVolume = externalLoadVolume(imported, listOf(week))
            val missingAssignments = sessions.filter { it.dayOfWeek == null && assignments[it.id] == null }
            val suggestedDays = suggestedImportedWeekdays(missingAssignments.size).filterNotNull().distinct()
                .joinToString(", ") { weekdayShort(it) }
            if (missingAssignments.isNotEmpty()) Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Sugerencia para ${missingAssignments.size} sesión${if (missingAssignments.size == 1) "" else "es"}: $suggestedDays",
                    style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
                    color = ImportPrototypeFaint,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ImportTextAction(
                    label = "Sugerir reparto",
                    onClick = { onSuggest(week.index); selectedSessionId = null },
                    modifier = Modifier.testTag("import-week-suggest"),
                    color = ImportPrototypeText,
                )
            }
            Text(
                "Duración no indicada por la fuente · Volumen externo estimado ${weekVolume ?: "no disponible"}",
                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = ImportPrototypeFaint,
                modifier = Modifier.testTag("import-week-estimates-${week.index}"),
            )
        }
        if (selectedSessionId != null) {
            Text(
                "Sesión seleccionada · toca un día de la semana",
                style = WizardTypography.note,
                color = ImportPrototypeAccent,
                modifier = Modifier.testTag("import-week-selected-session"),
            )
        }
        sessions.forEach { day ->
            val selected = day.id == selectedSessionId
            val assigned = assignments[day.id]
            val dayNumber = sessions.takeWhile { it.id != day.id }.count() + 1
            val formattedName = importSessionDisplayName(dayNumber, day.name)
            val displayName = formattedName.substringAfter(" · ", formattedName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.width(34.dp).heightIn(min = 42.dp)
                        .clickable(role = Role.Button, onClick = { selectedSessionId = if (selected) null else day.id })
                        .semantics { contentDescription = displayName + ", " + (assigned?.let(::importWeekdayName) ?: "sin día") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(assigned?.let(::weekdayShort)?.uppercase() ?: "—", style = WizardTypography.note.copy(fontSize = 10.sp), color = if (assigned != null) ImportPrototypeText else ImportPrototypeFaint)
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (assigned != null) ImportPrototypeAccent else Color.Transparent))
                }
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) ImportPrototypeInset else ImportPrototypeModule)
                        .border(1.dp, if (selected) ImportPrototypeAccent else ImportPrototypeLine, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = { selectedSessionId = if (selected) null else day.id })
                        .semantics { contentDescription = displayName + ", " + (assigned?.let(::importWeekdayName) ?: "sin día") }
                        .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp)
                        .testTag("import-week-session-${day.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("⠿", style = WizardTypography.note.copy(fontSize = 13.sp), color = ImportPrototypeFaint)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val dayVolume = externalLoadVolume(imported, listOf(week.copy(days = listOf(day))))
                        Text(
                            "${importCountLabel(day.exercises.size, "ejercicio", "ejercicios")} · ${dayVolume ?: "volumen no disponible"}",
                            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
                            color = ImportPrototypeMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (assigned != null && assigned in collisionDays) {
                        Text("Choque", style = WizardTypography.note.copy(fontSize = 10.sp), color = ImportPrototypeEnergy)
                    }
                    Box(
                        modifier = Modifier.size(width = 34.dp, height = 42.dp)
                            .clickable(role = Role.Button, onClick = { onOpenMenu(week.index, day.id) })
                            .testTag("import-week-session-menu-${day.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("···", style = WizardTypography.note.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeMuted)
                    }
                }
            }
        }
        week.days.filter { it.isRestDay }.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).testTag("import-week-rest-${day.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("—", style = WizardTypography.note.copy(fontSize = 10.sp), color = ImportPrototypeFaint, modifier = Modifier.width(34.dp))
                Text(day.name + " · Sin sesión", style = WizardTypography.note.copy(fontSize = 10.5.sp), color = ImportPrototypeFaint)
            }
        }
        if (assignedSessionCount < sessions.size) {
            Text(
                importCountLabel(sessions.size - assignedSessionCount, "sesión", "sesiones") + " todavía sin día.",
                style = WizardTypography.note,
                color = ImportPrototypeEnergy,
            )
        }
    }
}

@Composable
private fun ImportWeekTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(if (selected) ImportPrototypeText else ImportPrototypeInset)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) Color(0xFF111111) else ImportPrototypeMuted,
            maxLines = 1,
        )
    }
}

private fun weekdayShort(day: Int): String = when (day) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    else -> "Dom"
}

internal fun importWeekdayName(day: Int): String = when (day) {
    1 -> "lunes"
    2 -> "martes"
    3 -> "miércoles"
    4 -> "jueves"
    5 -> "viernes"
    6 -> "sábado"
    else -> "domingo"
}

@Composable
internal fun ImportSessionsIntro(
    imported: ImportedProgramDraft,
    reviewedDayIds: Set<String>,
    onEditSession: (weekIndex: Int, dayId: String) -> Unit,
    onOpenMenu: (weekIndex: Int, dayId: String) -> Unit,
) {
    val sessions = imported.weeks.flatMap { week -> week.days.filterNot { it.isRestDay } }
    val reviewed = sessions.count { it.id in reviewedDayIds }
    val firstPendingSessionId = sessions.firstOrNull { it.id !in reviewedDayIds }?.id
    val sessionsPerWeek = imported.weeks.map { week -> week.days.count { !it.isRestDay } }
    val perWeekCounts = sessionsPerWeek.distinct()
    val sessionIntroduction = when {
        sessionsPerWeek.isEmpty() -> "La importación no contiene sesiones."
        sessionsPerWeek.size > 1 && perWeekCounts.size == 1 ->
            "La importación creó ${importCountLabel(sessions.size, "sesión", "sesiones")} (${importCountLabel(perWeekCounts.single(), "sesión", "sesiones")} por semana)."
        sessionsPerWeek.size > 1 ->
            "La importación creó ${importCountLabel(sessions.size, "sesión", "sesiones")} en ${importCountLabel(imported.weeks.size, "semana", "semanas")}."
        else -> "La importación creó ${importCountLabel(sessions.size, "sesión", "sesiones")}."
    }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.fillMaxWidth()) {
        ImportWizardStepProgress(3, "$reviewed de ${sessions.size} revisadas")
        Text("Ajusta cada sesión en su editor.", style = ImportPageTitleStyle, color = ImportPrototypeText)
        Text(
            "$sessionIntroduction Abre el editor de KPKN para cambiar series, repeticiones, RIR, descanso o notas. No hay un editor nuevo para la importación.",
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
        )
        imported.weeks.forEach { week ->
            val weekSessions = week.days.filterNot { it.isRestDay }
            if (weekSessions.isEmpty()) return@forEach
            val weekReviewed = weekSessions.count { it.id in reviewedDayIds }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "SEMANA ${week.index}",
                    style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.1.sp),
                    color = ImportPrototypeFaint,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    importCountLabel(weekSessions.size, "sesión", "sesiones"),
                    style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = if (weekReviewed == weekSessions.size) ImportPrototypeOk else ImportPrototypeMuted,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ImportPrototypeModule)
                    .border(1.dp, ImportPrototypeLine, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp),
            ) {
                weekSessions.forEachIndexed { index, day ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
                    val dayNumber = week.days.takeWhile { it.id != day.id }.count { !it.isRestDay } + 1
                    val sessionName = importSessionDisplayName(dayNumber, day.name)
                    val volume = externalLoadVolume(imported, listOf(week.copy(days = listOf(day))))
                    val nextLabel = if (day.id == firstPendingSessionId) "Siguiente" else "Abrir"
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .semantics {
                                contentDescription = buildString {
                                    append(sessionName)
                                    append(" · ")
                                    append(importCountLabel(day.exercises.size, "ejercicio", "ejercicios"))
                                    if (volume != null) append(" · volumen externo estimado $volume")
                                    if (day.id in reviewedDayIds) append(" · revisada")
                                }
                            }
                            .testTag("import-session-row-${day.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(ImportPrototypeAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            DumbbellIcon(tint = ImportPrototypeAccent, size = 16.dp)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                sessionName,
                                style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                                color = ImportPrototypeText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = buildString {
                                append(importCountLabel(day.exercises.size, "ejercicio", "ejercicios"))
                                append(" · ")
                                append(if (volume == null) "volumen no disponible" else "vol. ext. est. $volume")
                            }
                            Text(
                                subtitle,
                                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                                color = ImportPrototypeMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier.heightIn(min = 30.dp)
                                .clip(CircleShape)
                                .background(if (day.id == firstPendingSessionId) ImportPrototypeAccent.copy(alpha = 0.12f) else ImportPrototypeInset)
                                .border(1.dp, ImportPrototypeLine, CircleShape)
                                .clickable(role = Role.Button, onClick = { onEditSession(week.index, day.id) })
                                .padding(horizontal = 9.dp)
                                .testTag("import-session-edit-${day.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                nextLabel,
                                style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = if (day.id == firstPendingSessionId) ImportPrototypeAccent else ImportPrototypeMuted,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier = Modifier.size(width = 22.dp, height = 42.dp)
                                .clickable(role = Role.Button, onClick = { onOpenMenu(week.index, day.id) })
                                .testTag("import-session-menu-${day.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("›", style = WizardTypography.note.copy(fontSize = 19.sp, lineHeight = 19.sp), color = ImportPrototypeMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportProgramStructureOverview(
    imported: ImportedProgramDraft,
    onOpenWeek: (Int) -> Unit,
    onOpenDay: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        imported.weeks.forEachIndexed { weekPosition, week ->
            val sessions = week.days.filterNot { it.isRestDay }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ImportTextAction(
                        "Semana ${week.index}",
                        onClick = { onOpenWeek(week.index) },
                        modifier = Modifier.testTag("import-structure-week-${week.index}"),
                        color = ImportPrototypeText,
                    )
                    Text(importCountLabel(sessions.size, "sesión", "sesiones"), style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp), color = ImportPrototypeMuted)
                    Spacer(Modifier.weight(1f))
                    ImportTextAction(
                        "Duplicar",
                        onClick = { onEdit(ImportEditOp.DuplicateWeek(week.index)) },
                        modifier = Modifier.testTag("import-structure-week-duplicate-${week.index}"),
                        color = ImportPrototypeText,
                    )
                }
                sessions.forEachIndexed { sessionPosition, day ->
                    val dayNumber = week.days.takeWhile { it.id != day.id }.count { !it.isRestDay } + 1
                    val displayName = importSessionDisplayName(dayNumber, day.name)
                    val sessionTitle = day.dayOfWeek?.let { "${weekdayShort(it)} · ${displayName.substringAfter(" · ", displayName)}" } ?: displayName
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = { onOpenDay(day.id) })
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .testTag("import-structure-day-${day.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(sessionTitle, style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = ImportPrototypeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("1 sesión", style = WizardTypography.note.copy(fontSize = 9.sp, lineHeight = 12.sp), color = ImportPrototypeFaint)
                    }
                }
                ImportTextAction(
                    "+ Añadir día",
                    onClick = { onEdit(ImportEditOp.AddDay(week.index, "")) },
                    modifier = Modifier.padding(start = 8.dp).testTag("import-structure-add-day-${week.index}"),
                    color = ImportPrototypeMuted,
                )
            }
            if (weekPosition < imported.weeks.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(CircleShape)
                .background(ImportPrototypeInset).border(1.dp, ImportPrototypeLine, CircleShape)
                .clickable(role = Role.Button, onClick = { onEdit(ImportEditOp.AddWeek) })
                .testTag("import-structure-add-week"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("+", style = WizardTypography.controlLabel.copy(fontSize = 16.sp), color = ImportPrototypeMuted)
            Text("Añadir semana", style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeText)
        }
    }
}

@Composable
internal fun ImportSessionMenuContent(
    name: String,
    dayLabel: String,
    weekIndex: Int,
    exerciseCount: Int,
    exercises: List<ImportedExercise>,
    defaultSplitName: String,
    selectedDayOfWeek: Int?,
    allowStructureChanges: Boolean = true,
    onClose: () -> Unit,
    onMove: (Int) -> Unit,
    onSplitAtExercise: (String, String) -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    var moveOptionsExpanded by rememberSaveable(name, weekIndex) { mutableStateOf(false) }
    var splitExpanded by rememberSaveable(name, weekIndex) { mutableStateOf(false) }
    var splitExerciseId by rememberSaveable(name, weekIndex) { mutableStateOf<String?>(null) }
    var splitName by rememberSaveable(name, weekIndex) { mutableStateOf(defaultSplitName) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(name, style = WizardTypography.stepTitle.copy(fontSize = 20.sp, lineHeight = 24.sp), color = ImportPrototypeText)
        Text(
            "$dayLabel · Semana $weekIndex · ${importCountLabel(exerciseCount, "ejercicio", "ejercicios")}",
            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
            color = ImportPrototypeMuted,
        )
        Spacer(Modifier.height(1.dp))
        ImportSessionMenuRow(
            icon = KpknLineIcons.Calendar,
            title = "Mover a otro día",
            subtitle = "Selecciona la sesión y toca el destino",
            onClick = { moveOptionsExpanded = !moveOptionsExpanded },
            testTag = "import-session-menu-move",
            color = ImportPrototypeText,
        )
        if (moveOptionsExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (1..7).forEach { dayOfWeek ->
                    val selected = selectedDayOfWeek == dayOfWeek
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .background(if (selected) ImportPrototypeAccent.copy(alpha = 0.14f) else ImportPrototypeInset)
                            .border(1.dp, if (selected) ImportPrototypeAccent.copy(alpha = 0.45f) else ImportPrototypeLine, CircleShape)
                            .clickable(role = Role.Button, onClick = { onMove(dayOfWeek) })
                            .padding(horizontal = 4.dp)
                            .testTag("import-session-move-$dayOfWeek"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            weekdayShort(dayOfWeek),
                            style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = if (selected) ImportPrototypeAccent else ImportPrototypeMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (allowStructureChanges) {
            if (exercises.isNotEmpty()) {
                ImportSessionMenuRow(
                    icon = KpknLineIcons.Layers,
                    title = "Dividir desde un ejercicio",
                    subtitle = "Crea otra sesión y conserva los datos importados",
                    onClick = { splitExpanded = !splitExpanded },
                    testTag = "import-session-menu-split",
                    color = ImportPrototypeText,
                )
                if (splitExpanded) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SetupFormTextField(
                            value = splitName,
                            onValueChange = { splitName = it.take(80) },
                            label = "Nombre de la sesión nueva",
                            testTag = "import-session-split-name",
                        )
                        exercises.forEach { exercise ->
                            ImportTextAction(
                                label = (if (splitExerciseId == exercise.id) "✓ Desde " else "Desde ") + exercise.rawName,
                                onClick = { splitExerciseId = exercise.id },
                                modifier = Modifier.fillMaxWidth().testTag("import-session-split-exercise-${exercise.id}"),
                                color = if (splitExerciseId == exercise.id) ImportPrototypeAccent else ImportPrototypeMuted,
                            )
                        }
                        ImportTextAction(
                            label = "Crear sesión",
                            enabled = splitExerciseId != null && splitName.isNotBlank(),
                            onClick = { splitExerciseId?.let { onSplitAtExercise(it, splitName.trim()) } },
                            modifier = Modifier.fillMaxWidth().testTag("import-session-split-confirm"),
                            color = ImportPrototypeText,
                        )
                        Text(
                            "Los ejercicios movidos conservan cargas, notas, series y selección de catálogo. Después asigna su día.",
                            style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                            color = ImportPrototypeMuted,
                        )
                    }
                }
            }
            ImportSessionMenuRow(
                icon = KpknLineIcons.Layers,
                title = "Duplicar sesión",
                subtitle = "Se añade justo después en el mismo día",
                onClick = onDuplicate,
                testTag = "import-session-menu-duplicate",
                color = ImportPrototypeText,
            )
            ImportSessionMenuRow(
                icon = KpknLineIcons.Trash,
                title = "Quitar del día",
                subtitle = "Puedes deshacer este cambio mientras revisas",
                onClick = onRemove,
                testTag = "import-session-menu-remove",
                color = ImportPrototypeDanger,
            )
        } else {
            Text(
                "La estructura original se conserva en este borrador.",
                style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                color = ImportPrototypeMuted,
                modifier = Modifier.padding(start = 46.dp, top = 1.dp, bottom = 1.dp).testTag("import-session-menu-structure-note"),
            )
        }
        Spacer(Modifier.height(1.dp))
        ImportTextAction("Cancelar", onClose, modifier = Modifier.fillMaxWidth().testTag("import-session-menu-cancel"), color = ImportPrototypeMuted)
    }
}

@Composable
private fun ImportSessionMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 3.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(ImportPrototypeInset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = WizardTypography.note.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), color = color)
            Text(subtitle, style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp), color = ImportPrototypeMuted)
        }
        Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = ImportPrototypeMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun ImportReviewIntro(
    imported: ImportedProgramDraft,
    muscleDistribution: ImportMuscleDistribution?,
    requiresPro: Boolean,
    issueCount: Int,
    pendingDecisions: Int,
    weekdays: Set<Int>,
    reviewedDayIds: Set<String>,
    pendingWarnings: Int,
    catalogUnavailable: Boolean,
    catalogRetrying: Boolean,
    onReviewMatches: () -> Unit,
    onReviewDays: () -> Unit,
    onReviewSessions: () -> Unit,
    onWarnings: () -> Unit,
    onPlanLimits: () -> Unit,
    onBackToSessions: () -> Unit,
    onRetryCatalog: () -> Unit,
) {
    val exercises = importExerciseCount(imported)
    val sessions = imported.weeks.flatMap { week -> week.days.filterNot { it.isRestDay } }
    val reviewedSessions = sessions.count { it.id in reviewedDayIds }
    val missingAssignments = sessions.count { effectiveImportedWeekdays(imported, weekdays)[it.id] == null }
    val checksComplete = listOf(
        pendingDecisions == 0,
        weekdays.isNotEmpty() && missingAssignments == 0,
        pendingWarnings == 0,
    ).count { it }
    val ready = checksComplete == 3 && issueCount == 0 && !catalogUnavailable && !catalogRetrying
    Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.fillMaxWidth()) {
        val stepSummary = when {
            ready -> "Todo listo"
            pendingDecisions > 0 -> "$pendingDecisions pendientes"
            weekdays.isEmpty() || missingAssignments > 0 -> "Elige un día"
            pendingWarnings > 0 -> "Avisos por revisar"
            else -> "$checksComplete de 3 listas"
        }
        ImportWizardStepProgress(4, stepSummary)
        Text(if (ready) "Todo listo." else "Casi listo.", style = ImportPageTitleStyle, color = ImportPrototypeText)
        Text(
            if (ready) "Revisa la última vez. Todavía no se ha guardado nada." else "Resuelve lo que falta antes de guardar.",
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
        )
        ImportReviewPassport(
            title = imported.title,
            weeks = imported.weeks.size,
            sessions = sessions.size,
            exercises = exercises,
            requiresPro = requiresPro,
            onPlanLimits = onPlanLimits,
        )
        ImportReviewChecklist(checksComplete) {
            ImportReviewCheckRow(
                title = "${(exercises - pendingDecisions).coerceAtLeast(0)} de $exercises decididos",
                detail = if (pendingDecisions == 0) "Todos los ejercicios tienen una decisión." else "Falta${if (pendingDecisions == 1) "" else "n"} elegir ${importCountLabel(pendingDecisions, "ejercicio", "ejercicios")}.",
                action = "Revisar",
                onClick = onReviewMatches,
                complete = pendingDecisions == 0,
                modifier = Modifier.testTag("import-review-check-exercises"),
                showDivider = false,
            )
            ImportReviewCheckRow(
                title = if (weekdays.isEmpty()) "Días de entrenamiento sin asignar" else "${importCountLabel(weekdays.size, "día", "días")} de entrenamiento",
                detail = if (missingAssignments == 0) weekdays.sorted().joinToString(", ") { weekdayShort(it) } else "${importCountLabel(missingAssignments, "sesión", "sesiones")} sin día asignado.",
                action = "Cambiar",
                onClick = onReviewDays,
                complete = weekdays.isNotEmpty() && missingAssignments == 0,
                modifier = Modifier.testTag("import-review-check-days"),
            )
            ImportReviewCheckRow(
                title = importReviewedSessionsLabel(reviewedSessions, sessions.size),
                detail = "Puedes ajustarlas ahora o después de guardar.",
                action = "Revisar",
                onClick = onReviewSessions,
                complete = reviewedSessions == sessions.size,
                info = true,
                modifier = Modifier.testTag("import-review-check-sessions"),
            )
            ImportReviewCheckRow(
                title = "$pendingWarnings avisos sin revisar",
                detail = if (pendingWarnings == 0) "No quedan avisos pendientes." else "Ábrelos, corrígelos o márcalos como revisados.",
                action = if (imported.warnings.isEmpty()) null else if (pendingWarnings == 0) "Ver" else "Revisar",
                onClick = onWarnings.takeIf { imported.warnings.isNotEmpty() },
                complete = pendingWarnings == 0,
                modifier = Modifier.testTag("import-review-check-warnings"),
            )
            if (issueCount > 0) Text("Hay $issueCount observaciones pendientes en el borrador.", style = WizardTypography.note, color = ImportPrototypeEnergy)
        }
        if (catalogUnavailable || catalogRetrying) {
            ImportWizardPanel(
                title = if (catalogRetrying) "Comprobando el catálogo" else "Catálogo no disponible",
                subtitle = "El borrador sigue intacto",
            ) {
                Text(
                    if (catalogRetrying) "Abriendo el catálogo de ejercicios…" else "No pudimos verificar los ejercicios. Cuando el catálogo esté disponible, vuelve a intentarlo antes de guardar.",
                    style = WizardTypography.note,
                    color = ImportPrototypeMuted,
                )
                if (catalogUnavailable) {
                    ImportCta(
                        label = "Reintentar catálogo",
                        enabled = true,
                        onClick = onRetryCatalog,
                        testTag = "import-review-retry-catalog",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val weeklyVolume = imported.weeks.mapNotNull { week -> externalLoadRangeTonnes(imported, listOf(week))?.let { week.index to it } }
        if (weeklyVolume.isNotEmpty()) {
            ImportWizardPanel(title = "Volumen estimado por semana", subtitle = "t") {
                val maximum = weeklyVolume.maxOf { (_, range) -> range.endInclusive }.coerceAtLeast(0.01)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weeklyVolume.forEach { (weekIndex, range) ->
                        val height = (range.endInclusive / maximum).toFloat().coerceIn(0.12f, 1f) * 66f
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(formatTonnes(range.start) + if (kotlin.math.abs(range.endInclusive - range.start) < 0.05) "" else "–" + formatTonnes(range.endInclusive), style = WizardTypography.note.copy(fontSize = 10.sp), color = ImportPrototypeText, maxLines = 1)
                            Box(
                                modifier = Modifier.width(22.dp).height(height.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(ImportPrototypeAccent),
                            )
                            Text("Sem $weekIndex", style = WizardTypography.note.copy(fontSize = 10.sp), color = ImportPrototypeMuted)
                        }
                    }
                }
                Text("Estimado con series × repeticiones × carga. No incluye peso corporal.", style = WizardTypography.note, color = ImportPrototypeMuted)
            }
        }
        muscleDistribution?.let { distribution ->
            val groups = distribution.groups.filter { it.sets > 0 }
            if (groups.isNotEmpty()) {
                ImportWizardPanel(
                    title = "Series por grupo",
                    subtitle = "Semana ${distribution.weekIndex} · grupo principal",
                    modifier = Modifier.testTag("import-review-muscle-distribution"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        groups.forEachIndexed { index, group ->
                            Box(
                                modifier = Modifier.weight(group.sets.toFloat()).background(
                                    listOf(ImportPrototypeAccent, ImportPrototypeBlue, ImportPrototypeOk, ImportPrototypeEnergy, ImportPrototypeDanger)[index % 5],
                                ),
                            )
                        }
                    }
                    groups.withIndex().toList().chunked(2).forEach { rowGroups ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowGroups.forEach { (absoluteIndex, group) ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(CircleShape).background(
                                            listOf(ImportPrototypeAccent, ImportPrototypeBlue, ImportPrototypeOk, ImportPrototypeEnergy, ImportPrototypeDanger)[absoluteIndex % 5],
                                        ),
                                    )
                                    Text("${group.label} ${group.sets}", style = WizardTypography.note, color = ImportPrototypeText)
                                }
                            }
                            if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        buildString {
                            append("Cuenta las series de trabajo por grupo primario; no suma músculos secundarios.")
                            if (distribution.unassignedExercises > 0) {
                                append(" ").append(distribution.unassignedExercises)
                                    .append(if (distribution.unassignedExercises == 1) " ejercicio propio o sin grupo quedó fuera." else " ejercicios propios o sin grupo quedaron fuera.")
                            }
                        },
                        style = WizardTypography.note,
                        color = ImportPrototypeMuted,
                    )
                }
            }
        }
        ImportTextAction("Volver a las sesiones", onBackToSessions, modifier = Modifier.testTag("import-review-back-sessions"), color = ImportPrototypeMuted)
        Text("Se guardará como borrador. No se activará automáticamente.", style = WizardTypography.note, color = ImportPrototypeMuted)
    }
}

@Composable
private fun ImportReviewPassport(
    title: String,
    weeks: Int,
    sessions: Int,
    exercises: Int,
    requiresPro: Boolean,
    onPlanLimits: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(ImportPrototypeModule)
            .border(1.dp, ImportPrototypeLine, shape)
            .testTag("import-review-passport"),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(124.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A201A), Color(0xFF101010)),
                    ),
                ),
        ) {
            Box(
                Modifier.size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-52).dp)
                    .border(1.dp, ImportPrototypeAccent.copy(alpha = 0.18f), CircleShape),
            )
            Box(
                Modifier.size(226.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 54.dp, y = (-76).dp)
                    .border(20.dp, ImportPrototypeAccent.copy(alpha = 0.035f), CircleShape),
            )
            Column(
                modifier = Modifier.fillMaxWidth().height(124.dp).padding(15.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "LISTO PARA GUARDAR",
                    style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.2.sp),
                    color = ImportPrototypeText.copy(alpha = 0.62f),
                )
                Text(
                    title,
                    style = WizardTypography.stepTitle.copy(
                        fontSize = 25.sp,
                        lineHeight = 25.75.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                    ),
                    color = ImportPrototypeText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ImportReviewPassportChip(importCountLabel(weeks, "semana", "semanas"))
                    ImportReviewPassportChip(importCountLabel(sessions, "sesión", "sesiones"))
                    ImportReviewPassportChip(importCountLabel(exercises, "ejercicio", "ejercicios"))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Borrador · no se activa", style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp), color = ImportPrototypeFaint)
            Spacer(Modifier.weight(1f))
            if (requiresPro) {
                Box(
                    modifier = Modifier.clip(CircleShape)
                        .background(Color(0x19C9B8FF))
                        .border(1.dp, Color(0x33C9B8FF), CircleShape)
                        .clickable(onClick = onPlanLimits)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text("KPKN Pro", style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp), color = Color(0xFFC9B8FF))
                }
            }
        }
    }
}

@Composable
private fun ImportReviewPassportChip(label: String) {
    Box(
        modifier = Modifier.clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = ImportPrototypeText,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ImportReviewChecklist(
    checksComplete: Int,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(ImportPrototypeModule)
            .border(1.dp, ImportPrototypeLine, shape)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag("import-review-checklist"),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CHECKLIST",
                style = WizardTypography.eyebrow.copy(fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.2.sp),
                color = ImportPrototypeFaint,
            )
            Spacer(Modifier.weight(1f))
            val complete = checksComplete == 3
            Box(
                modifier = Modifier.clip(CircleShape)
                    .background(if (complete) ImportPrototypeOk.copy(alpha = 0.10f) else ImportPrototypeEnergy.copy(alpha = 0.09f))
                    .border(1.dp, (if (complete) ImportPrototypeOk else ImportPrototypeEnergy).copy(alpha = 0.22f), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(
                    "$checksComplete de 3",
                    style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (complete) ImportPrototypeOk else ImportPrototypeEnergy,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(ImportPrototypeLine))
        content()
    }
}

@Composable
private fun ImportReviewCheckRow(
    title: String,
    detail: String,
    action: String?,
    onClick: (() -> Unit)?,
    complete: Boolean,
    info: Boolean = false,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val iconColor = if (complete) ImportPrototypeOk else if (info) ImportPrototypeBlue else ImportPrototypeEnergy
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (complete) "✓" else if (info) "i" else "!",
                    style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                    color = iconColor,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = WizardTypography.note.copy(fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = ImportPrototypeText,
                )
                Text(detail, style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 14.sp), color = ImportPrototypeFaint)
            }
            if (action != null && onClick != null) {
                ImportTextAction(action, onClick, color = ImportPrototypeFaint)
            }
        }
    }
}

private fun externalLoadRangeTonnes(
    imported: ImportedProgramDraft,
    weeks: List<ImportedWeek> = imported.weeks,
): ClosedFloatingPointRange<Double>? {
    val exercises = weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
        .filter { it.loadKg != null && it.repsMin > 0 && it.repsMax >= it.repsMin }
    if (exercises.isEmpty()) return null
    val minKgReps = exercises.sumOf { exercise ->
        exercise.sets * exercise.repsMin * ImportedProgramBuilder.kilograms(
            requireNotNull(exercise.loadKg),
            exercise.loadUnit ?: imported.loadUnit,
        )
    }
    val maxKgReps = exercises.sumOf { exercise ->
        exercise.sets * exercise.repsMax * ImportedProgramBuilder.kilograms(
            requireNotNull(exercise.loadKg),
            exercise.loadUnit ?: imported.loadUnit,
        )
    }
    return (minKgReps / 1000.0)..(maxKgReps / 1000.0)
}

private fun formatTonnes(value: Double): String = String.format(java.util.Locale.forLanguageTag("es-CL"), "%.1f", value)

private fun externalLoadVolume(imported: ImportedProgramDraft, weeks: List<ImportedWeek> = imported.weeks): String? {
    val range = externalLoadRangeTonnes(imported, weeks) ?: return null
    val min = formatTonnes(range.start) + " t"
    val max = formatTonnes(range.endInclusive) + " t"
    return if (kotlin.math.abs(range.endInclusive - range.start) < 0.0005) min else "$min–$max"
}

@Composable
internal fun ImportEmptyPage(
    evidence: ImportSourceEvidence,
    imported: ImportedProgramDraft?,
    onEditText: () -> Unit,
    onChooseSource: () -> Unit,
    onStartOver: () -> Unit,
    message: String? = null,
) {
    var showExtractedText by remember(evidence.displayName) { mutableStateOf(false) }
    val exercises = imported?.let(::importExerciseCount) ?: 0
    val warnings = imported?.let { draft -> draft.warnings.count { it.id !in draft.reviewedWarnings } } ?: 0
    val rowsWithText = evidence.rowCount ?: evidence.sourceText?.lineSequence()?.count { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(78.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF17150F))
                    .border(1.dp, ImportPrototypeEnergy.copy(alpha = 0.28f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Search, contentDescription = null, tint = ImportPrototypeEnergy, modifier = Modifier.size(25.dp))
            }
        }
        Text(
            "Leímos el archivo, pero no encontramos ejercicios.",
            style = WizardTypography.stepTitle.copy(fontSize = 24.sp, lineHeight = 26.5.sp, letterSpacing = (-0.5).sp),
            color = ImportPrototypeText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Puede que el documento use una tabla de imágenes o que los nombres estén en una columna que no reconocemos. Revisa el texto extraído o prueba otra fuente.",
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (message != null) ImportWizardPanel(title = "Detalle") { Text(message, style = WizardTypography.note, color = ImportPrototypeDanger) }
        ImportWizardPanel(title = "Lo que hemos leído", subtitle = evidence.displayName ?: importCountLabel(exercises, "ejercicio", "ejercicios")) {
            val sourceParts = when {
                evidence.pageCount != null -> Triple("Páginas", evidence.pageCount.toString(), "Páginas detectadas")
                evidence.sheetCount != null -> Triple("Hojas", evidence.sheetCount.toString(), "Hojas detectadas")
                else -> Triple("Hojas o páginas", "—", "Metadatos no disponibles")
            }
            ImportReadDiagnostic(sourceParts.first, sourceParts.second, sourceParts.third)
            ImportReadDiagnostic("Filas con texto", rowsWithText?.toString() ?: "—", "Ninguna se interpretó como ejercicio")
            ImportReadDiagnostic("Ejercicios reconocidos", exercises.toString(), "Sin coincidencias en el catálogo")
            ImportReadDiagnostic("Avisos", warnings.toString(), "No se inventaron datos")
            if (evidence.sourceText != null) {
                ImportTextAction(
                    if (showExtractedText) "Ocultar texto extraído" else "Ver el texto extraído",
                    onClick = { showExtractedText = !showExtractedText },
                    modifier = Modifier.testTag("import-empty-show-text"),
                    color = ImportPrototypeAccent,
                )
                if (showExtractedText) {
                    Text(
                        evidence.sourceText.take(2_000) + if (evidence.sourceTextTruncated || evidence.sourceText.length > 2_000) "\n…" else "",
                        style = WizardTypography.note,
                        color = ImportPrototypeMuted,
                        modifier = Modifier.testTag("import-empty-extracted-text"),
                    )
                }
            }
        }
        ImportCta("Revisar el texto", true, onEditText, "import-empty-edit-text", Modifier.fillMaxWidth())
        ImportTextAction("Elegir otro archivo", onChooseSource, modifier = Modifier.testTag("import-empty-new-source"), color = ImportPrototypeMuted)
        ImportTextAction("Empezar de cero", onStartOver, modifier = Modifier.testTag("import-empty-start-over"), color = ImportPrototypeMuted)
        if (evidence.kind == com.example.kpkn.domain.training.importer.ImportSourceKind.PHOTO) {
            Text("Si es una foto, prueba con más luz y sin sombras sobre el papel.", style = WizardTypography.note, color = ImportPrototypeMuted)
        }
    }
}

@Composable
private fun ImportReadDiagnostic(label: String, value: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = WizardTypography.controlLabel, color = ImportPrototypeText)
            Text(detail, style = WizardTypography.note, color = ImportPrototypeMuted)
        }
        Text(value, style = WizardTypography.cardTitle, color = ImportPrototypeText)
    }
}

@Composable
internal fun ImportErrorPage(
    message: String,
    evidence: ImportSourceEvidence,
    readingProgress: ImportReadingProgress?,
    onShowText: () -> Unit,
    onRetryText: () -> Unit,
    onPasteText: () -> Unit,
    onChooseSource: () -> Unit,
) {
    val isPublicLink = evidence.kind == ImportSourceKind.PUBLIC_URL
    val failure = importFailurePresentation(message, evidence.kind)
    val clipboard = LocalClipboardManager.current
    val sourceLabel = when (evidence.kind) {
        ImportSourceKind.PUBLIC_URL -> "Enlace"
        ImportSourceKind.PASTED_TEXT -> "Texto"
        ImportSourceKind.PHOTO -> "Foto"
        ImportSourceKind.FILE -> "Archivo"
        ImportSourceKind.NONE -> "Fuente"
    }
    val displayName = evidence.displayName ?: when (evidence.kind) {
        ImportSourceKind.PUBLIC_URL -> "Enlace público"
        ImportSourceKind.PASTED_TEXT -> "Texto pegado"
        ImportSourceKind.PHOTO -> "Foto seleccionada"
        else -> "Fuente seleccionada"
    }
    val metadata = listOfNotNull(evidence.mediaType, evidence.pageCount?.let { "$it páginas" })
        .joinToString(" · ").ifBlank { "Metadatos no disponibles" }
    val explanation = when {
        isPublicLink -> "Comprueba que la página sea pública y pueda abrirse sin iniciar sesión."
        evidence.kind == ImportSourceKind.PHOTO -> "Prueba con otra foto bien enfocada y con el texto completo dentro del encuadre."
        evidence.kind == ImportSourceKind.PASTED_TEXT -> "Corrige el texto o comprueba que incluya nombres de ejercicios y sesiones."
        failure.code.startsWith("PDF_") -> "El PDF puede estar protegido o dañado. Exporta una copia legible o pega su texto."
        else -> "Comprueba el formato del archivo o pega el texto del programa."
    }
    val copyDetails = {
        clipboard.setText(AnnotatedString(buildString {
            appendLine("${failure.code}: ${failure.detail}")
            appendLine("$sourceLabel: $displayName")
            appendLine("Tipo: ${evidence.mediaType ?: "desconocido"}")
            readingProgress?.takeUnless { isPublicLink }?.let { appendLine("Etapa: ${it.stage}") }
            evidence.pageCount?.let { appendLine("Páginas: $it") }
        }.trim()))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(78.dp).clip(CircleShape)
                    .border(1.dp, ImportPrototypeDanger.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF211514))
                        .border(1.dp, ImportPrototypeDanger.copy(alpha = 0.26f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPublicLink) KpknLineIcons.Link else KpknLineIcons.Alert,
                        contentDescription = null,
                        tint = ImportPrototypeDanger,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        Text(
            when (evidence.kind) {
                ImportSourceKind.PUBLIC_URL -> "No pudimos leer el enlace."
                ImportSourceKind.PASTED_TEXT -> "No pudimos leer el texto."
                ImportSourceKind.PHOTO -> "No pudimos leer la foto."
                else -> "No pudimos leer el archivo."
            },
            style = WizardTypography.stepTitle.copy(fontSize = 25.sp, lineHeight = 27.5.sp),
            color = ImportPrototypeText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            explanation,
            style = ImportPageBodyStyle,
            color = ImportPrototypeMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF211716), Color(0xFF151111))))
                .border(1.dp, ImportPrototypeDanger.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MOTIVO", style = WizardTypography.eyebrow, color = ImportPrototypeDanger)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFF30201E))
                        .border(1.dp, ImportPrototypeDanger.copy(alpha = 0.24f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        failure.code,
                        style = WizardTypography.eyebrow.copy(fontSize = 9.sp, lineHeight = 12.sp),
                        color = ImportPrototypeDanger,
                    )
                }
            }
            Text(
                failure.detail,
                style = WizardTypography.note,
                color = ImportPrototypeMuted,
                modifier = Modifier.testTag("import-error-message"),
            )
            Text("No se modificó ningún borrador.", style = WizardTypography.note, color = ImportPrototypeFaint)
        }
        ImportWizardPanel(
            title = "Detalles",
            eyebrowTitle = true,
            headerAction = {
                ImportTextAction("Copiar", onClick = copyDetails, modifier = Modifier.testTag("import-error-copy"), color = ImportPrototypeText)
            },
        ) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(ImportPrototypeLine))
            ImportErrorDiagnostic(sourceLabel, displayName, metadata)
            if (!isPublicLink) {
                readingProgress?.let { progress ->
                    val stage = listOf("Lectura del documento", "Estructura", "Ejercicios", "Catálogo", "Días")[progress.stage.ordinal]
                    val detail = if (progress.currentPage != null && progress.totalPages != null) {
                        "Página ${progress.currentPage} de ${progress.totalPages}"
                    } else if (progress.stage == ImportReadingStage.DOCUMENT) {
                        "Antes de interpretar ejercicios"
                    } else {
                        "La lectura se detuvo en esta etapa"
                    }
                    ImportErrorDiagnostic("Etapa", stage, detail)
                }
            }
            ImportErrorDiagnostic("Borrador", "Intacto", "No se ha sobrescrito nada")
        }
        ImportCta(
            when (evidence.kind) {
                ImportSourceKind.PUBLIC_URL -> "Cambiar enlace"
                ImportSourceKind.PASTED_TEXT -> "Corregir el texto"
                ImportSourceKind.PHOTO -> "Elegir otra foto"
                ImportSourceKind.FILE -> "Elegir otro archivo"
                ImportSourceKind.NONE -> "Elegir otra fuente"
            },
            true,
            onChooseSource,
            "import-error-choose-source",
            Modifier.fillMaxWidth(),
        )
        if (evidence.kind != ImportSourceKind.PASTED_TEXT) {
            ImportTextAction("Pegar el texto", onPasteText, modifier = Modifier.testTag("import-error-paste-text"), color = ImportPrototypeAccent)
        }
        if (evidence.sourceText != null) {
            ImportTextAction("Ver el texto que sí se leyó", onShowText, modifier = Modifier.testTag("import-error-show-text"), color = ImportPrototypeAccent)
            ImportTextAction("Reintentar con el texto corregido", onRetryText, modifier = Modifier.testTag("import-error-retry-text"), color = ImportPrototypeMuted)
        }
    }
}

@Composable
private fun ImportErrorDiagnostic(label: String, value: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold), color = ImportPrototypeMuted)
            Text(detail, style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 14.sp), color = ImportPrototypeFaint)
        }
        Text(value, style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold), color = ImportPrototypeText, textAlign = TextAlign.End)
    }
}

internal data class ImportFailurePresentation(val code: String, val detail: String)

internal fun importFailurePresentation(
    message: String,
    sourceKind: ImportSourceKind = ImportSourceKind.NONE,
): ImportFailurePresentation {
    val normalized = message.lowercase(java.util.Locale.ROOT)
    if (sourceKind == ImportSourceKind.PUBLIC_URL) {
        return ImportFailurePresentation(
            code = "ENLACE_PUBLICO_NO_LEGIBLE",
            detail = "No pudimos leer ese enlace. Usa una página pública HTTPS o pega el texto del programa.",
        )
    }
    return when {
        "demasiado grande" in normalized -> ImportFailurePresentation(
            code = "FUENTE_DEMASIADO_GRANDE",
            detail = "El archivo supera el tamaño permitido. Elige una versión más ligera o pega el texto.",
        )
        "formato antiguo" in normalized || "formato antiguo de office" in normalized -> ImportFailurePresentation(
            code = "FORMATO_OFFICE_ANTIGUO",
            detail = "Este formato de Office no se puede leer. Guárdalo como .xlsx o .docx y vuelve a intentarlo.",
        )
        "no es un archivo que sepamos leer" in normalized -> ImportFailurePresentation(
            code = "FORMATO_NO_COMPATIBLE",
            detail = "Este formato no es compatible. Prueba con Excel, CSV, Word, PDF, una foto o texto.",
        )
        "pdf" in normalized && listOf("proteg", "contraseña", "cifrad", "password", "encrypted").any(normalized::contains) -> ImportFailurePresentation(
            code = "PDF_ENCRYPTED",
            detail = "El PDF está protegido con contraseña. Quita la protección o expórtalo de nuevo sin contraseña.",
        )
        "pdf" in normalized && ("abrir" in normalized || "proteg" in normalized) -> ImportFailurePresentation(
            code = "PDF_NO_LEGIBLE",
            detail = "No pudimos abrir este PDF. Puede estar protegido o dañado; prueba a exportarlo de nuevo o pega su texto.",
        )
        "pdf" in normalized -> ImportFailurePresentation(
            code = "PDF_REQUIERE_TEXTO",
            detail = "No pudimos leer el contenido del PDF. Pega su texto o elige una copia sin protección.",
        )
        "imagen" in normalized -> ImportFailurePresentation(
            code = "IMAGEN_NO_LEGIBLE",
            detail = "No pudimos abrir la imagen. Elige otra foto con buena luz y el texto completo dentro del encuadre.",
        )
        else -> ImportFailurePresentation(
            code = "LECTURA_DEL_DOCUMENTO",
            detail = "La lectura se detuvo antes de analizar el contenido. Prueba con otra fuente o pega el texto.",
        )
    }
}

@Composable
internal fun ImportSuccessPage(
    imported: ImportedProgramDraft,
    weekdays: Set<Int>,
    firstSessionId: String?,
    onViewProgram: () -> Unit,
    onStartFirstSession: ((sessionId: String) -> Unit)?,
) {
    val firstSession = imported.weeks.sortedBy { it.index }.firstNotNullOfOrNull { week ->
        week.days.firstOrNull { !it.isRestDay }?.let { week to it }
    }
    val assignments = effectiveImportedWeekdays(imported, weekdays)
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 640.dp).padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp).clip(CircleShape).background(ImportPrototypeModule).border(1.dp, ImportPrototypeAccent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("✓", style = WizardTypography.stepTitle.copy(fontSize = 44.sp), color = ImportPrototypeAccent) }
        Text("GUARDADO", style = WizardTypography.eyebrow, color = ImportPrototypeAccent)
        Text("Ya es tuyo.", style = WizardTypography.stepTitle.copy(fontSize = 38.sp, lineHeight = 40.sp), color = ImportPrototypeText, modifier = Modifier.semantics { heading() })
        Text(imported.title, style = WizardTypography.controlLabel, color = ImportPrototypeText)
          val sessionCount = imported.weeks.sumOf { week -> week.days.count { !it.isRestDay } }
          Text("${importCountLabel(imported.weeks.size, "semana", "semanas")} · ${importCountLabel(sessionCount, "sesión", "sesiones")} · ${importCountLabel(importExerciseCount(imported), "ejercicio", "ejercicios")}", style = WizardTypography.note, color = ImportPrototypeMuted)
        ImportWizardPanel(title = "Borrador · no activado", subtitle = "Podrás activarlo desde Programas.") {
            Text("Nada empieza hasta que tú lo decidas.", style = WizardTypography.note, color = ImportPrototypeMuted)
              firstSession?.let { (week, day) ->
                  val assigned = assignments[day.id]?.let(::weekdayShort)
                  val volume = externalLoadVolume(imported, listOf(week.copy(days = listOf(day))))
                  Text("Primera sesión", style = WizardTypography.controlLabel, color = ImportPrototypeText)
                  Text(
                      buildString {
                          append(day.name)
                          if (assigned != null) append(" · ").append(assigned)
                          append(" · ").append(importCountLabel(day.exercises.size, "ejercicio", "ejercicios"))
                          append(" · ").append(volume ?: "sin volumen legible")
                      },
                      style = WizardTypography.note,
                      color = ImportPrototypeMuted,
                  )
                  Text("Duración no indicada por la fuente.", style = WizardTypography.note, color = ImportPrototypeMuted)
              } ?: Text("No hay sesiones de entrenamiento en el borrador.", style = WizardTypography.note, color = ImportPrototypeMuted)
        }
        Spacer(Modifier.weight(1f))
        if (firstSession != null && firstSessionId != null && onStartFirstSession != null) {
            ImportCta(
                "Empezar la primera sesión",
                true,
                { onStartFirstSession.invoke(firstSessionId) },
                "import-success-start-first",
                Modifier.fillMaxWidth(),
            )
            ImportDockSecondaryButton(
                "Ver programa",
                onViewProgram,
                "import-success-view",
                Modifier.fillMaxWidth(),
            )
        } else {
            ImportCta("Ver programa", true, onViewProgram, "import-success-view", Modifier.fillMaxWidth())
        }
        Text(
            "Guardado como borrador. Podrás activarlo cuando quieras.",
            style = WizardTypography.note,
            color = ImportPrototypeMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun ImportResumeSheetContent(
    imported: ImportedProgramDraft,
    resumePage: ImportWizardPage,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDiscardDraft: () -> Unit,
) {
    val sessionCount = imported.weeks.sumOf { week -> week.days.count { !it.isRestDay } }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Tienes un borrador.", style = WizardTypography.stepTitle.copy(fontSize = 21.sp, lineHeight = 23.5.sp), color = ImportPrototypeText)
        Text("Se guardó automáticamente. Puedes continuar donde lo dejaste.", style = WizardTypography.note.copy(fontSize = 11.sp, lineHeight = 15.sp), color = ImportPrototypeMuted)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ImportPrototypeInset)
                .border(1.dp, ImportPrototypeLine, RoundedCornerShape(18.dp)).padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(ImportPrototypeAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▤", style = WizardTypography.cardTitle.copy(fontSize = 22.sp), color = ImportPrototypeAccent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(imported.title, style = WizardTypography.note.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${importCountLabel(imported.weeks.size, "semana", "semanas")} · ${importCountLabel(sessionCount, "sesión", "sesiones")} · ${importCountLabel(importExerciseCount(imported), "ejercicio", "ejercicios")}",
                    style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = ImportPrototypeMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Continuar desde ${resumePageLabel(resumePage)}", style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp), color = ImportPrototypeFaint)
            }
        }
        ImportCta("Reanudar", true, onResume, "import-resume-continue", Modifier.fillMaxWidth())
        ImportDockSecondaryButton(
            label = "Empezar de nuevo",
            onClick = onDiscard,
            testTag = "import-resume-discard",
            modifier = Modifier.fillMaxWidth(),
        )
        ImportTextAction(
            "Descartar borrador",
            onDiscardDraft,
            modifier = Modifier.fillMaxWidth().testTag("import-resume-delete"),
            color = ImportPrototypeDanger,
        )
    }
}

@Composable
internal fun ImportProSheetContent(
    weeks: List<ImportedWeek>,
    onUpgrade: (() -> Unit)?,
    onKeepWeek: ((Int) -> Unit)?,
    onClose: () -> Unit,
) {
    val weekOptionsKey = weeks.map { week -> week.index to week.days.count { !it.isRestDay } }
    val eligibleWeeks = weeks.filter { week -> week.days.count { !it.isRestDay } in 1..5 }
    val firstEligibleWeek = eligibleWeeks.firstOrNull()?.index ?: -1
    var confirmingFreeWeek by rememberSaveable(weekOptionsKey) { mutableStateOf(false) }
    var selectedWeekIndex by rememberSaveable(weekOptionsKey) { mutableStateOf(firstEligibleWeek) }
    val selectedWeek = eligibleWeeks.firstOrNull { it.index == selectedWeekIndex } ?: eligibleWeeks.firstOrNull()
    val sessionCount = weeks.sumOf { week -> week.days.count { !it.isRestDay } }
    val exceedsFreeLimit = weeks.size > 1 || weeks.any { week -> week.days.count { !it.isRestDay } > 5 }

    if (confirmingFreeWeek && onKeepWeek != null) {
        ImportFreeWeekConfirmationContent(
            weeks = weeks,
            selectedWeekIndex = selectedWeek?.index,
            onSelectWeek = { selectedWeekIndex = it },
            onConfirm = { selectedWeek?.let { week -> onKeepWeek(week.index) } },
            onBack = { confirmingFreeWeek = false },
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier.clip(CircleShape).background(ImportPrototypeBlue.copy(alpha = 0.12f))
                .border(1.dp, ImportPrototypeBlue.copy(alpha = 0.3f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text("KPKN PRO", style = WizardTypography.note.copy(fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold), color = ImportPrototypeBlue)
        }
        Text(
            if (exceedsFreeLimit) "Este programa es de KPKN Pro." else "Conoce KPKN Pro.",
            style = WizardTypography.stepTitle.copy(fontSize = 21.sp, lineHeight = 23.5.sp),
            color = ImportPrototypeText,
        )
        Text(
            when {
                weeks.isEmpty() -> "El plan Free permite una semana y hasta cinco sesiones por semana."
                exceedsFreeLimit -> "${importCountLabel(weeks.size, "semana", "semanas")} y ${importCountLabel(sessionCount, "sesión", "sesiones")} superan el plan Free, que permite una semana y hasta cinco sesiones."
                else -> "Este borrador cabe en el plan Free: una semana y hasta cinco sesiones por semana."
            },
            style = WizardTypography.note.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
            color = ImportPrototypeMuted,
        )
        listOf(
            "Varias semanas con progresión y estructura propia",
            "Más de 5 sesiones por semana y biblioteca completa",
            "Historial, análisis y edición avanzada del programa",
        ).forEach { benefit ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KpknLineIcons.Check, contentDescription = null, tint = ImportPrototypeBlue, modifier = Modifier.size(13.dp))
                Text(benefit, style = WizardTypography.note.copy(fontSize = 10.sp, lineHeight = 13.sp), color = ImportPrototypeMuted)
            }
        }
        if (onUpgrade != null) {
            ImportCta("Ver KPKN Pro", true, onUpgrade, "import-pro-upgrade", Modifier.fillMaxWidth())
        } else {
            Text(
                "La opción para cambiar a Pro no está conectada desde esta pantalla. Tu borrador se conserva para que puedas seguir revisándolo.",
                style = WizardTypography.note,
                color = ImportPrototypeMuted,
                modifier = Modifier.testTag("import-pro-unavailable"),
            )
        }
        if (weeks.size > 1 && onKeepWeek != null && eligibleWeeks.isNotEmpty()) {
            ImportDockSecondaryButton(
                label = "Seguir con 1 semana",
                onClick = { confirmingFreeWeek = true },
                testTag = "import-pro-keep-week",
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (weeks.size > 1 && onKeepWeek == null) {
            Text(
                "Ya abriste el editor de sesión. Para conservar esos cambios, el recorte de semanas no está disponible desde aquí.",
                style = WizardTypography.note,
                color = ImportPrototypeMuted,
                modifier = Modifier.testTag("import-pro-week-cut-unavailable"),
            )
        } else if (weeks.isNotEmpty() && eligibleWeeks.isEmpty() && (weeks.size > 1 || sessionCount > 5)) {
            Text(
                "Ninguna semana cumple el límite de hasta cinco sesiones. Quita o combina sesiones antes de guardar con Free.",
                style = WizardTypography.note,
                color = ImportPrototypeEnergy,
                modifier = Modifier.testTag("import-pro-week-cut-unavailable"),
            )
        }
        ImportTextAction("Ahora no", onClose, modifier = Modifier.fillMaxWidth().testTag("import-pro-close"), color = ImportPrototypeMuted)
    }
}

@Composable
private fun ImportFreeWeekConfirmationContent(
    weeks: List<ImportedWeek>,
    selectedWeekIndex: Int?,
    onSelectWeek: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text("Seguir con una semana", style = WizardTypography.stepTitle.copy(fontSize = 23.sp), color = ImportPrototypeText)
        Text(
            "Elige cuál conservar. Las demás se quitarán del borrador; puedes deshacer el recorte antes de guardar.",
            style = WizardTypography.note,
            color = ImportPrototypeMuted,
        )
        weeks.forEach { week ->
            val sessions = week.days.count { !it.isRestDay }
            val eligible = sessions in 1..5
            val selected = week.index == selectedWeekIndex
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) ImportPrototypeInset else ImportPrototypeModule)
                    .clickable(enabled = eligible, role = Role.RadioButton) { onSelectWeek(week.index) }
                    .testTag("import-free-week-option-${week.index}")
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = eligible,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ImportPrototypeAccent,
                        unselectedColor = ImportPrototypeMuted,
                        disabledSelectedColor = ImportPrototypeAccent.copy(alpha = 0.45f),
                        disabledUnselectedColor = ImportPrototypeLine,
                    ),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Semana ${week.index}", style = WizardTypography.controlLabel, color = ImportPrototypeText)
                    Text(
                        if (eligible) "${importCountLabel(sessions, "sesión", "sesiones")} · dentro de Free" else "${importCountLabel(sessions, "sesión", "sesiones")} · supera el límite Free",
                        style = WizardTypography.note,
                        color = if (eligible) ImportPrototypeMuted else ImportPrototypeEnergy,
                    )
                }
            }
        }
        ImportCta(
            label = selectedWeekIndex?.let { "Conservar solo Semana $it" } ?: "No hay una semana elegible",
            enabled = selectedWeekIndex != null,
            onClick = onConfirm,
            testTag = "import-free-week-confirm",
            modifier = Modifier.fillMaxWidth(),
        )
        ImportTextAction(
            "Volver a KPKN Pro",
            onBack,
            modifier = Modifier.testTag("import-free-week-back"),
            color = ImportPrototypeMuted,
        )
    }
}

@Composable
internal fun ImportExtractedTextSheetContent(
    evidence: ImportSourceEvidence,
    text: String,
    onTextChange: (String) -> Unit,
    onReanalyze: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text("Revisa el texto extraído.", style = WizardTypography.stepTitle.copy(fontSize = 21.sp, lineHeight = 23.5.sp), color = ImportPrototypeText)
        Text(evidence.displayName ?: "Fuente local", style = WizardTypography.note, color = ImportPrototypeMuted)
        evidence.sourceUrl?.let { Text(it, style = WizardTypography.note, color = ImportPrototypeBlue) }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            minLines = 8,
            maxLines = 18,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ImportPrototypeInset,
                unfocusedContainerColor = ImportPrototypeInset,
                focusedTextColor = ImportPrototypeText,
                unfocusedTextColor = ImportPrototypeText,
                focusedBorderColor = ImportPrototypeAccent,
                unfocusedBorderColor = ImportPrototypeLine,
            ),
            modifier = Modifier.fillMaxWidth().testTag("import-extracted-text"),
        )
        if (evidence.sourceTextTruncated) Text("El texto es largo; esta copia muestra el primer tramo disponible.", style = WizardTypography.note, color = ImportPrototypeEnergy)
        evidence.discardedText?.let { discarded ->
            ImportWizardPanel(title = "Contenido descartado de la página", subtitle = "Se conserva separado del texto del programa.") {
                Text(discarded, style = WizardTypography.note, color = ImportPrototypeMuted)
                if (evidence.discardedTextTruncated) Text("El contenido descartado está recortado.", style = WizardTypography.note, color = ImportPrototypeEnergy)
            }
        }
        ImportCta("Volver a analizar", text.isNotBlank(), onReanalyze, "import-extracted-reanalyze", Modifier.fillMaxWidth())
        ImportTextAction("Cerrar", onClose, modifier = Modifier.testTag("import-extracted-close"), color = ImportPrototypeMuted)
    }
}

@Composable
internal fun ImportPlanLimitNote() {
    ImportWizardPanel(title = "Límite del plan Free", subtitle = "Una semana y hasta cinco sesiones por semana.") {
        Text("Este programa puede guardarse con KPKN Pro. Puedes continuar revisándolo antes de decidir.", style = WizardTypography.note, color = ImportPrototypeMuted)
    }
}

@Composable
internal fun ImportWizardDock(
    label: String,
    enabled: Boolean,
    onNext: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryTestTag: String? = null,
    note: String? = null,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val leftLabel = secondaryLabel ?: backLabel
    val leftAction = onSecondary ?: onBack
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.58f),
                        0.35f to Color.Black.copy(alpha = 0.93f),
                        1f to Color.Black,
                    ),
                ),
            )
            .padding(start = 12.dp, end = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        note?.let {
            Text(
                it,
                style = WizardTypography.note.copy(fontSize = 10.5.sp),
                color = ImportPrototypeFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.78f),
                                1f to Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    .padding(vertical = 7.dp)
                    .testTag("import-step-note"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(ImportPrototypeGlass)
                .border(1.dp, ImportPrototypeGlassBorder, RoundedCornerShape(32.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leftLabel != null && leftAction != null) {
                ImportDockSecondaryButton(
                    label = leftLabel,
                    onClick = leftAction,
                    testTag = secondaryTestTag ?: if (secondaryLabel != null) "import-step-secondary" else "import-step-back",
                )
            }
            ImportDockPrimaryButton(
                label = label,
                enabled = enabled,
                onClick = onNext,
                modifier = Modifier.weight(1f).testTag("import-step-next"),
            )
        }
    }
}

@Composable
private fun ImportDockSecondaryButton(label: String, onClick: () -> Unit, testTag: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(ImportPrototypeDockSecondary)
            .border(1.dp, ImportPrototypeDockSecondaryBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = WizardTypography.cardSubtitle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = ImportPrototypeText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImportDockPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(
                brush = if (enabled) {
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ImportPrototypeDockCtaTop, ImportPrototypeDockCtaBottom))
                } else {
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ImportPrototypeDockDisabled, ImportPrototypeDockDisabled))
                },
                shape = CircleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            style = WizardTypography.cardSubtitle.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = if (enabled) Color(0xFF0B0B0B) else ImportPrototypeDockDisabledText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("→", style = WizardTypography.cardTitle.copy(fontSize = 18.sp), color = if (enabled) Color(0xFF0B0B0B) else ImportPrototypeDockDisabledText)
    }
}

internal fun importExerciseCount(imported: ImportedProgramDraft): Int =
    imported.weeks.sumOf { week -> week.days.sumOf { day -> day.exercises.size } }

internal fun importIssueCount(state: ImportProgramState): Int =
    state.imported?.weeks.orEmpty().sumOf { week -> week.days.sumOf { day -> day.exercises.count { exercise ->
        exercise.catalogSelectionId == null && !state.resolutions.containsKey(exercise.rawName)
    } } }

internal fun resumePageLabel(page: ImportWizardPage): String = when (page) {
    ImportWizardPage.SUMMARY -> "Resumen"
    ImportWizardPage.EXERCISES -> "Coincidencias de ejercicios"
    ImportWizardPage.DAYS -> "Distribución de días"
    ImportWizardPage.SESSIONS, ImportWizardPage.SESSION -> "Sesiones"
    ImportWizardPage.REVIEW -> "Revisión final"
    ImportWizardPage.EMPTY -> "Texto sin ejercicios"
    ImportWizardPage.ERROR -> "Lectura interrumpida"
    ImportWizardPage.SUCCESS -> "Programa guardado"
    ImportWizardPage.ENTRY -> "Entrada"
    ImportWizardPage.PASTE -> "Pegar texto"
    ImportWizardPage.READING -> "Lectura"
}
