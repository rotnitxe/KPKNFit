package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Insignia de la campana: el `musculo` del prototipo. */
private val PendingBadgeColor = Color(0xFFF49A6E)

/** La cuenta dentro de la insignia: Inter 11 sp. La negrita de Inter no va empaquetada; SemiBold es la más gruesa que hay. */
private val PendingBadgeStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

/** Título de una fila o del recuadro: Inter SemiBold 14,5 sp. */
private val PendingTitleStyle = KpknType.bodyStrong.copy(fontSize = 14.5f.sp, lineHeight = 20.sp)

/**
 * La campana de «Pendiente»: un círculo de 52 dp en `inset` con el icono y, arriba a la derecha, la cuenta en una insignia. Con [count]
 * 0 no pinta nada.
 */
@Composable
internal fun ProgramPendingBell(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "pendingBellScale",
    )
    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) { contentDescription = "Pendiente: $count" }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = KpknLineIcons.Bell, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.textMuted)
        }
        // Insignia: 20 dp como mínimo y un anillo negro de 3 dp por fuera; se corre -3 dp hacia arriba y a la derecha, como en el prototipo.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .clip(KpknShape.pill)
                .background(Color.Black, KpknShape.pill)
                .padding(3.dp)
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .clip(KpknShape.pill)
                .background(PendingBadgeColor, KpknShape.pill)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = count.toString(), style = PendingBadgeStyle, color = Color(0xFF111111), maxLines = 1)
        }
    }
}

/**
 * La hoja «Pendiente», la misma hoja KPKN que las decisiones. Lo más urgente va en un recuadro, con sus acciones si las tiene; el resto
 * va agrupado por plazo. Tocar una fila abre lo que la resuelve.
 */
@Composable
internal fun ProgramPendingSheet(items: List<ProgramPendingItem>, onDismiss: () -> Unit) {
    val ordered = orderedPending(items)
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.85f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pendiente", style = KpknType.dialogTitle, color = WizardColors.text)
            Text(pendingSubtitle(ordered.size), style = KpknType.caption, color = WizardColors.textMuted)
            ordered.firstOrNull()?.let { PendingFeatured(it) }
            groupedPending(ordered.drop(1)).forEach { (group, members) ->
                Column {
                    Text(
                        text = group.uppercase(Locale("es")),
                        style = KpknType.eyebrow,
                        color = WizardColors.textFaint,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                    )
                    members.forEachIndexed { index, item -> PendingRow(item = item, divider = index > 0) }
                }
            }
        }
    }
}

/** «3 cosas esperan tu decisión.»; en singular, «1 cosa espera tu decisión.». */
private fun pendingSubtitle(count: Int): String =
    if (count == 1) "1 cosa espera tu decisión." else "$count cosas esperan tu decisión."

/** El primero de la lista, en un recuadro: icono, título y detalle. Sus acciones, si las tiene, van en píldoras debajo. */
@Composable
private fun PendingFeatured(item: ProgramPendingItem) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    val primary = item.primaryLabel?.let { label -> item.onPrimary?.let { action -> label to action } }
    val secondary = item.secondaryLabel?.let { label -> item.onSecondary?.let { action -> label to action } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.03f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = item.onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PendingIcon(kind = item.kind)
            Spacer(Modifier.width(12.dp))
            PendingTexts(title = item.title, detail = item.detail, modifier = Modifier.weight(1f))
        }
        if (primary != null || secondary != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                primary?.let { (label, action) ->
                    KpknCompactPill(text = label, onClick = action, modifier = Modifier.weight(1f))
                }
                secondary?.let { (label, action) ->
                    KpknCompactPill(text = label, onClick = action, modifier = Modifier.weight(1f), filled = false)
                }
            }
        }
    }
}

/** Una fila del resto: icono, título y detalle, 54 dp como mínimo. Un filete la separa de la anterior; tocarla abre lo que la resuelve. */
@Composable
private fun PendingRow(item: ProgramPendingItem, divider: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = item.onOpen)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PendingIcon(kind = item.kind)
            Spacer(Modifier.width(14.dp))
            PendingTexts(title = item.title, detail = item.detail, modifier = Modifier.weight(1f))
        }
    }
}

/** Título y, si lo hay, detalle en `textFaint`. */
@Composable
private fun PendingTexts(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = PendingTitleStyle, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(text = detail, style = KpknType.caption, color = WizardColors.textFaint)
        }
    }
}

/** Icono de una fila: círculo de 38 dp con fondo blanco al 6 % e icono en `textMuted`. */
@Composable
private fun PendingIcon(kind: ProgramPendingKind) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = pendingIconOf(kind), contentDescription = null, modifier = Modifier.size(19.dp), tint = WizardColors.textMuted)
    }
}

/** El icono de cada tipo. «Diana» para el TM: es lo más parecido que hay en KpknLineIcons. */
private fun pendingIconOf(kind: ProgramPendingKind): ImageVector = when (kind) {
    ProgramPendingKind.PROGRESSION, ProgramPendingKind.AUTOREGULATION -> KpknLineIcons.TrendUp
    ProgramPendingKind.MISSING_TM -> KpknLineIcons.Target
    ProgramPendingKind.WEEKLY_CHECKIN -> KpknLineIcons.Check
    ProgramPendingKind.CONTINUATION -> KpknLineIcons.Repeat
    ProgramPendingKind.ONE_RM_TEST, ProgramPendingKind.MISSED_SESSIONS -> KpknLineIcons.Calendar
    ProgramPendingKind.BLOCK_REVIEW -> KpknLineIcons.Sliders
}
