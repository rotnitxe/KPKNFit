package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.split.DayRole
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState

/*
 * REPARTO · «Revisar cambios» (escenas `rp-revision` y `rp-revision-musculo`; la hoja de decisiones va encima, `rp-decisiones`).
 * Qué va a pasar con cada ejercicio antes de aplicar nada: el veredicto, tres cifras, lo que el motor decidió por ti («Con propuesta»), el mapa
 * de dónde sale cada sesión y cada sesión nueva con sus ejercicios (los que cambian de sesión, resaltados). «Aplicar reparto» solo vive aquí.
 */

internal enum class RpReviewTab { SESSION, MUSCLE }

/** Lo que dice «Con propuesta»: cada ejercicio con su día y, si el tiempo no cabe, el aviso. */
internal data class RpProposal(val bold: String, val rest: String)

internal data class RpReviewModel(
    val eyebrow: String,
    val summary: RpReviewSummary,
    val lockedNote: RpRich?,
    val proposals: List<RpProposal>,
    val timeWarning: String?,
    val tab: RpReviewTab,
    val map: List<RpMapRow>,
    val sessions: List<RpReviewSession>,
    val volume: List<RpVolumeRow>,
    val muscleNote: String,
    val applyEnabled: Boolean = true,
)

internal class RpReviewActions(
    val onBack: () -> Unit = {},
    val onTab: (RpReviewTab) -> Unit = {},
    val onDecide: () -> Unit = {},
    val onApply: () -> Unit = {},
)

@Composable
internal fun SplitReviewScreen(
    model: RpReviewModel,
    actions: RpReviewActions,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas).testTag("split_revision")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 214.dp),
        ) {
            Text(model.eyebrow.uppercase(java.util.Locale("es")), style = RpType.eye, color = WizardColors.textFaint, modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 8.dp))
            Text("Revisar cambios", style = RpType.h1, color = WizardColors.text, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            Text(model.summary.verdict, style = RpType.verdict, color = WizardColors.text)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RpSummaryCell(model.summary.moved.toString(), model.summary.movedLabel, Modifier.weight(1f))
                RpSummaryCell(model.summary.exercises.toString(), model.summary.exercisesLabel, Modifier.weight(1f))
                RpSummaryCell(model.summary.sets.toString(), model.summary.setsLabel, Modifier.weight(1f))
            }
            model.lockedNote?.let { note ->
                Row(modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icon(KpknLineIcons.Check, contentDescription = null, tint = RpColors.done, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                    RpRichText(note, RpType.note, WizardColors.textMuted)
                }
            }
            if (model.proposals.isNotEmpty() || model.timeWarning != null) {
                RpSectionHeader("Con propuesta")
                RpModule(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    model.proposals.forEachIndexed { index, proposal ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                        RpNoticeRow(
                            notice = RpNotice(RpSeverity.INFO, listOf(RpSpan(proposal.bold, bold = true), RpSpan(proposal.rest))),
                            first = index == 0,
                            onAction = {},
                        )
                    }
                    model.timeWarning?.let { text ->
                        if (model.proposals.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                        RpNoticeRow(notice = RpNotice(RpSeverity.WARN, rpPlain(text)), first = model.proposals.isEmpty(), onAction = {})
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .heightIn(min = 36.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onDecide)
                            .testTag("split_decidir"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Revisar y cambiar", style = RpType.act, color = WizardColors.text)
                        Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Box(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                RpSegmented(options = listOf("Por sesión", "Por músculo"), selectedIndex = if (model.tab == RpReviewTab.SESSION) 0 else 1, onSelect = { actions.onTab(if (it == 0) RpReviewTab.SESSION else RpReviewTab.MUSCLE) }, tag = "split_revision_tab")
            }
            if (model.tab == RpReviewTab.SESSION) RpReviewBySession(model) else RpReviewByMuscle(model)
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp)) {
            RpBackGlassButton(hazeState = hazeState, onClick = actions.onBack, description = "Volver a la vista previa")
        }
        RpCtaBar(
            hazeState = hazeState,
            label = "Aplicar reparto",
            enabled = model.applyEnabled,
            onClick = actions.onApply,
            leadingText = "Volver",
            leadingDescription = "Volver a la vista previa",
            onLeading = actions.onBack,
            tag = "split_aplicar_reparto",
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 78.dp),
        )
        overlay()
    }
}

@Composable
private fun RpSummaryCell(number: String, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = modifier.clip(shape).background(KpknSurface.inset, shape).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(number, style = RpType.summaryNumber, color = WizardColors.text, maxLines = 1)
        Text(label, style = RpType.summaryLabel, color = WizardColors.textFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RpReviewBySession(model: RpReviewModel) {
    // `.rp-anim` envuelve esta parte en el lienzo, pero ninguna regla de rp-A04 (`.rp-day`, `.rp-vr`, `.rp-rv`) alcanza a sus hijos: no hay entrada que reproducir.
    Column(modifier = Modifier.padding(top = 14.dp)) {
        RpModule(padding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)) {
            Text("DE DÓNDE SALE CADA SESIÓN", style = RpType.ey, color = WizardColors.textMuted, modifier = Modifier.heightIn(min = 28.dp))
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.map.forEach { row ->
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.width(92.dp)) {
                                Text(row.name, style = RpType.mapName, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${com.example.kpkn.domain.text.SpanishPlurals.exercises(row.total)}${if (row.locked) " · hecha" else ""}",
                                    style = RpType.mapSmall,
                                    color = WizardColors.textFaint,
                                    maxLines = 1,
                                )
                            }
                            Row(modifier = Modifier.weight(1f).height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                row.parts.forEach { part ->
                                    Box(
                                        modifier = Modifier
                                            .weight(part.count.toFloat())
                                            .height(22.dp)
                                            .defaultMinSize(minWidth = 20.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(RpColors.of(part.role), RoundedCornerShape(7.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(part.count.toString(), style = RpType.mapPart, color = RpColors.onInk, maxLines = 1) }
                                }
                            }
                        }
                        Text(row.caption, style = RpType.mapSmall, color = WizardColors.textFaint, modifier = Modifier.padding(start = 102.dp, top = 4.dp))
                    }
                }
            }
        }
        RpSectionHeader("Cada sesión")
        Text(
            "Las series, repeticiones, RIR y descansos de cada ejercicio no cambian.",
            style = RpType.note,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
        )
        model.sessions.forEach { session -> RpReviewSessionModule(session) }
    }
}

@Composable
private fun RpReviewSessionModule(session: RpReviewSession) {
    RpModule(padding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        Row(modifier = Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(RpColors.of(session.role), CircleShape))
            Text(session.title, style = RpType.reviewTitle, color = WizardColors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.meta, style = RpType.reviewMeta, color = WizardColors.textFaint, maxLines = 1)
        }
        Text(session.from, style = RpType.small, color = WizardColors.textFaint, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
        var i = 0
        var first = true
        while (i < session.rows.size) {
            val row = session.rows[i]
            if (row.supersetId != null) {
                val group = mutableListOf<RpReviewRow>()
                while (i < session.rows.size && session.rows[i].supersetId == row.supersetId) {
                    group += session.rows[i]
                    i++
                }
                if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                Column(modifier = Modifier.padding(vertical = 4.dp).drawBehind { drawRect(Color(0x2EFFFFFF), size = Size(2.dp.toPx(), size.height)) }.padding(start = 8.dp)) {
                    Text("SUPERSERIE · VAN JUNTOS", style = RpType.superset, color = WizardColors.textFaint, modifier = Modifier.padding(top = 8.dp))
                    group.forEachIndexed { index, member ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                        RpReviewExercise(member)
                    }
                }
            } else {
                if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                RpReviewExercise(row)
                i++
            }
            first = false
        }
    }
}

@Composable
private fun RpReviewExercise(row: RpReviewRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.moved) Modifier.drawBehind { drawRoundRect(RpColors.legs, topLeft = androidx.compose.ui.geometry.Offset(0f, 9.dp.toPx()), size = Size(3.dp.toPx(), size.height - 18.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())) } else Modifier)
            .padding(start = 10.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = RpType.exerciseName, color = WizardColors.text)
            Text(row.prescription, style = RpType.exerciseDetail, color = WizardColors.textFaint, modifier = Modifier.padding(top = 2.dp))
        }
        if (row.tag != null) {
            val (background, color) = when {
                row.moved -> Color(0x1FF49A6E) to RpColors.legs
                row.locked -> Color(0x1C43D18C) to RpColors.done
                else -> RpColors.tonal to WizardColors.textMuted
            }
            Box(Modifier.clip(RoundedCornerShape(9.dp)).background(background).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(row.tag, style = RpType.tag, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RpReviewByMuscle(model: RpReviewModel) {
    RpModule(padding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp), modifier = Modifier.padding(top = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MÚSCULO", style = RpType.volHead, color = WizardColors.textFaint, modifier = Modifier.weight(1f))
            Text("ANTES", style = RpType.volHead, color = WizardColors.textFaint, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            Text("DESPUÉS", style = RpType.volHead, color = WizardColors.textFaint, textAlign = TextAlign.End, modifier = Modifier.width(54.dp), maxLines = 1)
            Text("VECES/SEM.", style = RpType.volHead, color = WizardColors.textFaint, textAlign = TextAlign.End, modifier = Modifier.width(64.dp), maxLines = 1)
        }
        model.volume.forEach { row ->
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = RpType.volName, color = WizardColors.text, modifier = Modifier.weight(1f), maxLines = 1)
                Text(SplitFlowModels.number(row.before), style = RpType.volCell, color = WizardColors.textMuted, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                Text(
                    SplitFlowModels.number(row.after),
                    style = RpType.volCell,
                    color = if (row.after < row.before - 0.01) RpColors.danger else WizardColors.textMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(54.dp),
                )
                Text(
                    row.freqText,
                    style = RpType.volCell,
                    color = if (row.freqUp) RpColors.done else if (row.freqDown) RpColors.danger else WizardColors.textMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                    maxLines = 1,
                )
            }
        }
        Text(model.muscleNote, style = RpType.note, color = WizardColors.textMuted, modifier = Modifier.padding(top = 10.dp))
    }
}
