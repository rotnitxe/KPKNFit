package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.split.StripCell
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import dev.chrisbanes.haze.HazeState

/*
 * REPARTO · la vista previa de tu semana (escenas `rp-semana`, `rp-mas-dias`, `rp-menos-dias`, `rp-en-curso`, `rp-vacia`, `rp-calculando`,
 * `rp-rechazo` y, bajo su diálogo, `rp-autor`). Destino de tocar una tarjeta del catálogo: qué es el reparto, tu semana de siete días con las
 * sesiones calculadas con tus ejercicios (Antes / Después, arrastrar, «Mover a»), los avisos, el selector «Repartos para tu semana» y el volumen y
 * la frecuencia antes → después. La barra de abajo abre «Cómo aplicar» y lleva a la revisión.
 */

/** Una tarjeta del selector «Repartos para tu semana». */
internal data class RpPickItem(val id: String, val name: String, val strip: List<StripCell>, val caption: String, val on: Boolean)

/** Un rechazo con su frase: la semana no se puede repartir. */
internal data class RpRefusal(val title: String, val body: String?, val action: String?, val note: String?)

/** Todo lo que pinta la vista previa. */
internal data class RpPreviewModel(
    val eyebrow: String,
    val title: String,
    val lead: String,
    val chips: List<Pair<String?, String?>>,
    val view: RpView,
    val days: List<RpDay>,
    val todayWeekday: Int? = null,
    val selectedWeekday: Int? = null,
    val dirty: Boolean = false,
    val scopeText: String = "",
    val notices: List<RpNotice> = emptyList(),
    val pickerTrailing: String = "",
    val picker: List<RpPickItem> = emptyList(),
    val pickerTotal: Int = 35,
    val volume: List<RpVolumeRow>? = null,
    val ctaLabel: String = "Revisar cambios",
    val ctaEnabled: Boolean = true,
    val calculating: Boolean = false,
    val refusal: RpRefusal? = null,
    val revision: Int = 0,
    val emptyWeek: Boolean = false,
)

internal class RpPreviewActions(
    val onBack: () -> Unit = {},
    val onView: (RpView) -> Unit = {},
    val agenda: RpAgendaActions = RpAgendaActions(),
    val onReset: () -> Unit = {},
    val onOpenOptions: () -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onCta: () -> Unit = {},
    val onAdjustTime: () -> Unit = {},
    val onRefusalAction: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitPreviewScreen(
    model: RpPreviewModel,
    actions: RpPreviewActions,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas).testTag("split_vista_previa")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 60.dp, bottom = 150.dp),
        ) {
            Text(model.eyebrow.uppercase(java.util.Locale("es")), style = RpType.eye, color = WizardColors.textFaint, modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 8.dp))
            Text(model.title, style = RpType.h1, color = WizardColors.text, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            Text(model.lead, style = RpType.lead, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 12.dp))
            if (model.chips.isNotEmpty()) {
                FlowRow(modifier = Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.chips.forEach { (bold, rest) -> RpMetaChip(bold = bold, rest = rest) }
                }
            }
            when {
                model.refusal != null -> RpRefusedBody(model, actions)
                model.calculating -> RpCalculatingBody(model)
                else -> RpReadyBody(model, actions)
            }
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp)) {
            RpBackGlassButton(hazeState = hazeState, onClick = actions.onBack, description = "Atrás")
        }
        RpCtaBar(
            hazeState = hazeState,
            label = model.ctaLabel,
            enabled = model.ctaEnabled && !model.calculating && model.refusal == null,
            onClick = actions.onCta,
            leadingIcon = RpSlidersIcon,
            leadingDescription = "Cómo aplicar: alcance y contenido de las sesiones",
            onLeading = actions.onOpenOptions,
            tag = "split_cta",
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        )
        overlay()
    }
}

@Composable
private fun RpReadyBody(model: RpPreviewModel, actions: RpPreviewActions) {
    // El margen de 12 dp del modulo se funde con los 22 dp del rotulo que le sigue (margenes de bloque contiguos): quedan 22.
    RpModule(padding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TU SEMANA", style = RpType.ey, color = WizardColors.textMuted)
            RpSegmentedSmall(options = listOf("Antes", "Después"), selectedIndex = if (model.view == RpView.BEFORE) 0 else 1, onSelect = { actions.onView(if (it == 0) RpView.BEFORE else RpView.AFTER) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = if (model.view == RpView.BEFORE) "Así está hoy tu semana." else "Arrastra una sesión por su asa, o tócala y elige un día.",
                style = RpType.small,
                color = WizardColors.textFaint,
                modifier = Modifier.weight(1f),
            )
            if (model.dirty && model.view == RpView.AFTER) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onReset)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Restablecer", style = RpType.smallStrong, color = WizardColors.text, maxLines = 1) }
            }
        }
        RpAgenda(
            days = model.days,
            selectedWeekday = if (model.view == RpView.AFTER) model.selectedWeekday else null,
            actions = actions.agenda,
            todayWeekday = model.todayWeekday,
            revision = model.revision,
        )
        if (model.scopeText.isNotEmpty()) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 45.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onOpenOptions)
                    .padding(horizontal = 4.dp)
                    .testTag("split_alcance"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model.scopeText, style = RpType.scope, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                Text("Cambiar", style = RpType.scopeAction, color = WizardColors.text, maxLines = 1)
            }
        }
    }
    if (model.notices.isNotEmpty() && model.view == RpView.AFTER) {
        RpSectionHeader("Avisos", model.notices.size.toString())
        RpModule(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            model.notices.forEachIndexed { index, notice ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                RpNoticeRow(notice = notice, first = index == 0, onAction = actions.onAdjustTime)
            }
        }
    }
    RpSectionHeader("Repartos para tu semana", model.pickerTrailing)
    RpPicker(model = model, onPick = actions.onPick, onSeeAll = actions.onSeeAll)
    model.volume?.let { rows ->
        RpSectionHeader("Volumen y frecuencia")
        RpModule(padding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp)) {
            RpVolumeTable(rows)
            RpVolumeLegend()
        }
    }
}

/** Una fila de aviso (`.rp-av`): un círculo de 26 dp con su icono y el texto con negritas; el de tiempo ofrece «Ver cómo ajustarlo». */
@Composable
internal fun RpNoticeRow(notice: RpNotice, first: Boolean, onAction: () -> Unit) {
    val (background, tint, icon) = when (notice.severity) {
        RpSeverity.WARN -> Triple(Color(0x1AF7CF73), RpColors.warn, RpWarnIcon)
        RpSeverity.OK -> Triple(Color(0x1C43D18C), RpColors.done, KpknLineIcons.Check)
        RpSeverity.INFO -> Triple(RpColors.tonal, WizardColors.textMuted, RpInfoIcon)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (first) 2.dp else 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            RpRichText(notice.text, RpType.notice, WizardColors.textMuted)
            if (notice.action == RpNoticeAction.ADJUST_TIME) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(min = 32.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAction),
                    contentAlignment = Alignment.CenterStart,
                ) { Text("Ver cómo ajustarlo", style = RpType.smallStrong, color = WizardColors.text) }
            }
        }
    }
}

/** El selector «Repartos para tu semana»: tarjetas de 138 dp que se deslizan, la elegida con filete de tinta, y «Ver los 35». */
@Composable
private fun RpPicker(model: RpPreviewModel, onPick: (String) -> Unit, onSeeAll: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        model.picker.forEach { item ->
            // rp-T04: el borde de la tarjeta cambia en 200 ms con la curva suave
            val pickLine by animateKpknColorAsState(if (item.on) WizardColors.text else RpColors.line, KpknMotion.D200, KpknMotion.Suave)
            Column(
                modifier = Modifier
                    .width(192.dp)
                    .heightIn(min = 112.dp)
                    .clip(shape)
                    .background(if (item.on) RpColors.selected else KpknSurface.module, shape)
                    .border(1.dp, pickLine, shape)
                    .semantics { contentDescription = item.name }
                    .testTag("split_pick_${item.id}")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(item.id) }
                    .padding(1.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(item.name, style = RpType.pickTitle, color = WizardColors.text, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                RpStrip(cells = item.strip, small = true)
                Text(item.caption, style = RpType.pickCaption, color = WizardColors.textFaint, maxLines = 1)
            }
        }
        Column(
            modifier = Modifier
                .width(138.dp)
                .heightIn(min = 112.dp)
                .clip(shape)
                .background(KpknSurface.module, shape)
                .drawBehind {
                    drawRoundRect(
                        color = Color(0x12FFFFFF),
                        topLeft = Offset(0.5f, 0.5f),
                        size = Size(size.width - 1f, size.height - 1f),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                .semantics { contentDescription = "Ver todos los repartos" }
                .testTag("split_pick_todos")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSeeAll)
                .padding(1.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(RpFilterIcon, contentDescription = null, tint = WizardColors.textMuted, modifier = Modifier.size(20.dp))
            Text("Ver los ${model.pickerTotal}", style = RpType.pickCaption, color = WizardColors.textFaint, maxLines = 1)
        }
    }
}

/** Cabecera y filas de «Volumen y frecuencia»: nombre, barra con la marca de «antes», series y veces por semana. */
@Composable
internal fun RpVolumeTable(rows: List<RpVolumeRow>) {
    val maxValue = (rows.maxOfOrNull { maxOf(it.after, it.before) } ?: 1.0).coerceAtLeast(1.0) * 1.08
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MÚSCULO", style = RpType.volHead, color = WizardColors.textFaint, modifier = Modifier.width(76.dp), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("SERIES", style = RpType.volHead, color = WizardColors.textFaint, modifier = Modifier.width(34.dp), maxLines = 1, textAlign = TextAlign.End, softWrap = false)
        Text("VECES", style = RpType.volHead, color = WizardColors.textFaint, modifier = Modifier.width(54.dp), maxLines = 1, textAlign = TextAlign.Center)
    }
    rows.forEachIndexed { index, row ->
        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x08FFFFFF)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, style = RpType.volName, color = WizardColors.text, modifier = Modifier.width(76.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // rp-T01: el ancho de la barra cambia en 520 ms con la curva estandar
            val animatedAfter by animateKpknFloatAsState((row.after / maxValue).toFloat().coerceIn(0f, 1f), KpknMotion.D520, KpknMotion.Estandar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .drawBehind {
                        val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        drawRoundRect(Color(0x08FFFFFF), size = size, cornerRadius = corner)
                        val after = animatedAfter
                        if (after > 0f) drawRoundRect(RpColors.ink, size = Size(size.width * after, size.height), cornerRadius = corner)
                        val before = (row.before / maxValue).toFloat().coerceIn(0f, 1f)
                        drawRoundRect(
                            color = Color(0x99F2EEE6),
                            topLeft = Offset(size.width * before, -3.dp.toPx()),
                            size = Size(2.dp.toPx(), size.height + 6.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                        )
                    },
            )
            Text(SplitFlowModels.number(row.after), style = RpType.volValue, color = WizardColors.text, modifier = Modifier.width(34.dp), maxLines = 1, textAlign = TextAlign.End, softWrap = false)
            val (fqBackground, fqColor) = when {
                row.freqUp -> Color(0x1A43D18C) to RpColors.done
                row.freqDown -> Color(0x20FF9B92) to RpColors.danger
                else -> Color(0x0AFFFFFF) to WizardColors.textMuted
            }
            Box(Modifier.width(54.dp).clip(RoundedCornerShape(11.dp)).background(fqBackground).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                Text(row.freqText, style = RpType.volFreq, color = fqColor, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun RpVolumeLegend() {
    FlowRow(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(RpColors.ink))
            Text("después", style = RpType.legend, color = WizardColors.textFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 2.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(Color(0x99F2EEE6)))
            Text("antes", style = RpType.legend, color = WizardColors.textFaint)
        }
        Text("Los ejercicios se mueven enteros: las series no cambian.", style = RpType.legend, color = WizardColors.textFaint)
    }
}

/** `rp-calculando`: la semana con esqueletos mientras el motor reparte; la barra, apagada. */
@Composable
private fun RpCalculatingBody(model: RpPreviewModel) {
    RpModule(padding = PaddingValues(14.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TU SEMANA", style = RpType.ey, color = WizardColors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RpSpinner()
                Text("Adaptando tu semana…", style = RpType.small, color = WizardColors.textFaint)
            }
        }
        model.days.forEach { day ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (day.rest) 0.dp else 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.width(44.dp).padding(top = if (day.rest) 5.dp else 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SplitFlowModels.WEEKDAY_CAPS[day.weekday - 1], style = RpType.dayName, color = if (day.rest) RpColors.dim else WizardColors.textFaint, maxLines = 1)
                    Text(day.date?.toString().orEmpty(), style = if (day.rest) RpType.dayNumberRest else RpType.dayNumber, color = if (day.rest) RpColors.dim else WizardColors.text, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                }
                if (day.rest) {
                    Row(modifier = Modifier.weight(1f).heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Descanso", style = RpType.rest, color = RpColors.dim)
                        Box(Modifier.weight(1f).height(1.dp).background(Color(0x0DFFFFFF)))
                    }
                } else {
                    RpSkeleton(RoundedCornerShape(16.dp), Modifier.weight(1f).height(68.dp))
                }
            }
        }
    }
}

/** `rp-rechazo`: el motivo en una frase, la semana tal como está (apagada) y lo que sigue disponible. */
@Composable
private fun RpRefusedBody(model: RpPreviewModel, actions: RpPreviewActions) {
    val refusal = model.refusal ?: return
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape)
            .background(Color(0x10FF9B92), shape)
            .border(1.dp, Color(0x33FF9B92), shape)
            .padding(1.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(RpWarnIcon, contentDescription = null, tint = RpColors.danger, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(refusal.title, style = RpType.banner, color = WizardColors.text)
            if (refusal.body != null) Text(refusal.body, style = RpType.bannerBody, color = WizardColors.textMuted, modifier = Modifier.padding(top = 3.dp))
            if (refusal.action != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 36.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onRefusalAction)
                        .testTag("split_rechazo_accion"),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(refusal.action, style = RpType.smallStrong, color = WizardColors.text) }
            }
        }
    }
    RpModule(padding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp), modifier = Modifier.padding(bottom = if (refusal.note != null) 0.dp else 12.dp).then(Modifier.graphicsAlpha(0.5f))) {
        Text("TU SEMANA", style = RpType.ey, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 8.dp))
        RpAgenda(days = model.days.filterNot { it.rest }, selectedWeekday = null, actions = RpAgendaActions(), compact = true)
    }
    if (refusal.note != null) {
        RpSectionHeader("Otros repartos", "también se bloquean")
        Text(refusal.note, style = RpType.lead, color = WizardColors.textMuted, modifier = Modifier.offset(y = (-2).dp))
    }
}

private fun Modifier.graphicsAlpha(alpha: Float): Modifier = this.then(Modifier.graphicsLayer { this.alpha = alpha })
