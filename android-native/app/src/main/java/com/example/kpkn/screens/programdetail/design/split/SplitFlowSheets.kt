package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.kpknAnimate

/*
 * REPARTO · hojas y diálogos del flujo (punto 23): «Antes de aplicar» (`rp-decisiones`), el resumen tras aplicar y tras deshacer
 * (`rp-aplicado`, `rp-deshecho`) y el aviso del plan de autor (`rp-autor`). La hoja es la `.pl-sheet` del lienzo: lisa `#1b1b1d`, velo `#000000a8`,
 * y la píldora de abajo se queda fija con el desvanecido `velo-hoja`.
 */

internal val RpSheetSurface = Color(0xFF1B1B1D)
private val RpSheetScrim = Color(0xA8000000)

/** La hoja `.pl-sheet` con su contenido desplazable y, si hay [sticky], su píldora fija abajo con el velo `velo-hoja`. */
@Composable
internal fun RpSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sticky: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // pulido:profundidad rp-S03 (hoja: 0 -14 44 #000c + inset 0 1 #ffffff10) · entrada de la hoja: KpknSheet
    KpknSheet(onDismissRequest = onDismiss, surfaceColor = RpSheetSurface, backdropScrim = RpSheetScrim, modifier = modifier) {
        RpSheetColumn(sticky = sticky, content = content)
    }
}

/** El contenido de la hoja, separado para que las hojas de render lo dibujen sin ventana propia. */
@Composable
internal fun RpSheetColumn(sticky: (@Composable ColumnScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = if (sticky == null) 34.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (sticky != null) {
            // pulido:profundidad rp-G03 (velo-hoja: el contenido se desvanece bajo la píldora fija)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kpknGradient(KpknGradient.VeloHoja)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 34.dp),
                content = sticky,
            )
        }
    }
}

@Composable
internal fun RpSheetTitle(title: String, sub: String? = null) {
    Column {
        Text(title, style = RpType.sheetTitle, color = WizardColors.text, modifier = Modifier.padding(top = 2.dp).semantics { heading() })
        if (sub != null) Text(sub, style = RpType.sheetSub, color = WizardColors.textMuted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** `.rp-mod`: el módulo de las hojas, `#111`, radio 22, relleno 14 / 16 / 12. */
@Composable
internal fun RpSheetModule(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF111111), shape)
            .border(1.dp, RpColors.line, shape)
            .padding(1.dp)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
        content = content,
    )
}

// ─── Antes de aplicar ───────────────────────────────────────────────────────────────────────────

internal class RpDecisionActions(
    val onPlace: (unitKey: String, dayIndex: Int) -> Unit = { _, _ -> },
    val onFit: (Boolean) -> Unit = {},
    val onDone: () -> Unit = {},
)

@Composable
internal fun RpDecisionsSheet(model: RpDecisionsModel, actions: RpDecisionActions, onDismiss: () -> Unit) {
    RpSheet(
        onDismiss = onDismiss,
        sticky = { RpSheetPill("Listo", onClick = actions.onDone, modifier = Modifier.fillMaxWidth(), tag = "split_decisiones_listo") },
    ) { RpDecisionsContent(model, actions) }
}

@Composable
internal fun ColumnScope.RpDecisionsContent(model: RpDecisionsModel, actions: RpDecisionActions) {
    val sub = when {
        model.places.isNotEmpty() && model.time != null -> "Estos ejercicios no piden un día concreto y algún día se pasa de tiempo. Ya tienen una propuesta: cámbiala si quieres."
        model.time != null -> "Algún día se pasa de tiempo. Ya tiene una propuesta: cámbiala si quieres."
        else -> "Estos ejercicios no piden un día concreto. Ya tienen una propuesta: cámbiala si quieres."
    }
    RpSheetTitle("Antes de aplicar", sub)
    RpSheetModule {
        model.places.forEachIndexed { index, decision ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Column(modifier = Modifier.padding(top = if (index == 0) 4.dp else 14.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(decision.title, style = RpType.decisionTitle, color = WizardColors.text, modifier = Modifier.weight(1f, fill = false))
                    Box(
                        modifier = Modifier.padding(start = 6.dp).height(20.dp).clip(RoundedCornerShape(7.dp)).background(Color(0x1AF7CF73)).padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("PROPUESTA", style = RpType.proposal, color = RpColors.warn, maxLines = 1) }
                }
                RpRichText(
                    rich = listOf(RpSpan(decision.explanation + " "), RpSpan(decision.current, bold = true), RpSpan(".")),
                    style = RpType.decisionBody,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                RpChipsFlow {
                    decision.options.forEach { option ->
                        RpChip(text = option.title, on = option.on, onClick = { actions.onPlace(decision.unitKey, option.dayIndex) }, height = 36.dp, tag = "split_decision_${decision.unitKey}_${option.dayIndex}")
                    }
                }
            }
        }
        model.time?.let { time ->
            if (model.places.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Column(modifier = Modifier.padding(top = if (model.places.isEmpty()) 4.dp else 14.dp, bottom = 14.dp)) {
                Text("Tiempo por sesión: ${time.targetMinutes} min", style = RpType.decisionTitle, color = WizardColors.text)
                val bold = time.boldPrefix?.takeIf { time.explanation.startsWith(it) }
                RpRichText(
                    rich = if (bold != null) listOf(RpSpan(bold, bold = true), RpSpan(time.explanation.removePrefix(bold))) else rpPlain(time.explanation),
                    style = RpType.decisionBody,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                RpChipsFlow {
                    RpChip(text = "Dejarlo así", on = !time.fit, onClick = { actions.onFit(false) }, height = 36.dp, tag = "split_tiempo_no")
                    RpChip(text = "Ajustar a ${time.targetMinutes} min", on = time.fit, onClick = { actions.onFit(true) }, height = 36.dp, tag = "split_tiempo_si")
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RpChipsFlow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

// ─── Resumen tras aplicar / deshacer ────────────────────────────────────────────────────────────

/** Lo que dice la hoja tras aplicar (o tras deshacer): el icono, el título, la línea, las cifras y la nota. */
internal data class RpAppliedModel(
    val undone: Boolean,
    val title: String,
    val sub: String,
    val stats: List<Pair<String, String>>,
    val note: String?,
    /** «Deshacer» solo se ofrece si hay algo que deshacer. */
    val canUndo: Boolean = true,
)

internal class RpAppliedActions(
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@Composable
internal fun RpAppliedSheet(model: RpAppliedModel, actions: RpAppliedActions, onDismiss: () -> Unit) {
    RpSheet(onDismiss = onDismiss, sticky = { RpAppliedButtons(model, actions) }) { RpAppliedContent(model) }
}

@Composable
internal fun RpAppliedButtons(model: RpAppliedModel, actions: RpAppliedActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.undone) {
            RpSheetPill("Rehacer", onClick = actions.onRedo, modifier = Modifier.weight(1f), primary = false, leadingIcon = RpRedoIcon, tag = "split_rehacer")
        } else if (model.canUndo) {
            RpSheetPill("Deshacer", onClick = actions.onUndo, modifier = Modifier.weight(1f), primary = false, leadingIcon = RpUndoIcon, tag = "split_deshacer")
        }
        RpSheetPill("Listo", onClick = actions.onDone, modifier = Modifier.weight(1f), tag = "split_aplicado_listo")
    }
}

@Composable
internal fun ColumnScope.RpAppliedContent(model: RpAppliedModel) {
    // rp-A07: el icono aparece creciendo (rp-pop, 500 ms estandar). Los trazos `dibujo-trazo` (rp-A01 a rp-A03) no salen en estas escenas.
    Box(
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).size(56.dp).clip(CircleShape).background(if (model.undone) Color(0x10FFFFFF) else Color(0x2243D18C), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (model.undone) RpUndoIcon else KpknLineIcons.Check,
            contentDescription = null,
            tint = if (model.undone) WizardColors.text else RpColors.done,
            modifier = Modifier.size(if (model.undone) 26.dp else 28.dp).kpknAnimate(RpMotion.Pop),
        )
    }
    RpSheetTitle(model.title, model.sub)
    if (model.stats.isNotEmpty()) {
        RpSheetModule {
            model.stats.forEachIndexed { index, (label, value) ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, style = RpType.statLabel, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                    Text(value, style = RpType.statValue, color = WizardColors.text, textAlign = TextAlign.End)
                }
            }
        }
    }
    model.note?.let { Text(it, style = RpType.note, color = WizardColors.textMuted, modifier = Modifier.padding(top = 0.dp)) }
}

// ─── Plan de autor ──────────────────────────────────────────────────────────────────────────────

/** La tarjeta del diálogo `.rp-dlg`: 28 dp de radio, `#1b1b1d`, la sombra `dialogo-2`. */
@Composable
internal fun RpDialogCard(title: String, body: String, cancel: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            // pulido:profundidad rp-S06 (dialogo-2: 0 24 60 #000d + inset 0 1 #ffffff10)
            .kpknShadow(KpknShadowSpec(KpknShadow.Dialogo2.spec.outerLayers), shape)
            .clip(shape)
            .background(RpSheetSurface, shape)
            .kpknInset(KpknShadow.Dialogo2, shape)
            .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 14.dp),
    ) {
        Text(title, style = RpType.dialogTitle, color = WizardColors.text, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
        Text(body, style = RpType.dialogBody, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            RpDialogButton(cancel, primary = false, onClick = onCancel, tag = "split_dialogo_cancelar")
            RpDialogButton(confirm, primary = true, onClick = onConfirm, tag = "split_dialogo_confirmar")
        }
    }
}

@Composable
private fun RpDialogButton(text: String, primary: Boolean, onClick: () -> Unit, tag: String) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (primary) WizardColors.text else Color.Transparent, shape)
            .testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RpType.dialogButton, color = if (primary) RpColors.onInk else WizardColors.text, maxLines = 1) }
}

/** `rp-autor`: el aviso de un plan con receta de autor; «Mantener mi reparto» vuelve al catálogo y «Adaptar» sigue con la vista previa. */
@Composable
internal fun RpAuthorDialog(onKeep: () -> Unit, onAdapt: () -> Unit) {
    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            RpAuthorDialogCard(onKeep, onAdapt)
        }
    }
}

@Composable
internal fun RpAuthorDialogCard(onKeep: () -> Unit, onAdapt: () -> Unit, modifier: Modifier = Modifier) {
    RpDialogCard(
        title = "Este programa trae su reparto de autor",
        body = "Si lo adaptas, cambia su estructura original. Reubicamos los ejercicios y mantenemos tu volumen semanal. Lo ves todo antes de aplicar y puedes deshacerlo.",
        cancel = "Mantener mi reparto",
        confirm = "Adaptar",
        onCancel = onKeep,
        onConfirm = onAdapt,
        modifier = modifier,
    )
}
