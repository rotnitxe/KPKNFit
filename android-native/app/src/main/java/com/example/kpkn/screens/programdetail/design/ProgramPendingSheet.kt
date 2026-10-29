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
 * La hoja «Pendiente» (lienzo v6, p-pending): «Pendiente» y cuántas cosas esperan; la primera propuesta del motor en un recuadro con
 * «Aplicar» y «Mantener carga»; el resto en filas `.mi` que abren directamente su hoja; y, al final, «Más adelante». Una fila por tipo.
 */
@Composable
internal fun ProgramPendingSheet(items: List<ProgramPendingItem>, onDismiss: () -> Unit) {
    val featured = featuredPending(items)
    val now = pendingNowRows(items)
    val later = pendingLaterRows(items)
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = "Pendiente", sub = pendingSubtitle(pendingDecisionCount(items)))
        featured?.let { PendingFeatured(it) }
        now.forEachIndexed { index, item ->
            LienzoMenuRow(icon = pendingIconOf(item.kind), title = item.title, sub = item.detail, onClick = item.onOpen, divider = index > 0)
        }
        if (later.isNotEmpty()) {
            LienzoGroup("Más adelante")
            later.forEachIndexed { index, item ->
                LienzoMenuRow(
                    icon = pendingIconOf(item.kind),
                    title = item.title,
                    sub = item.detail,
                    onClick = item.onOpen,
                    divider = now.isNotEmpty() || index > 0,
                )
            }
        }
    }
}

/**
 * La primera propuesta (`.mod` de p-pending): fondo blanco al 3 %, filete al 8 %, 14 × 16 dp de relleno; su icono, su título y su detalle,
 * y debajo «Aplicar» y «Mantener carga» en píldoras pequeñas que la resuelven ahí mismo.
 */
@Composable
private fun PendingFeatured(item: ProgramPendingItem) {
    val shape = RoundedCornerShape(24.dp)
    val primary = item.primaryLabel?.let { label -> item.onPrimary?.let { action -> label to action } }
    val secondary = item.secondaryLabel?.let { label -> item.onSecondary?.let { action -> label to action } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.031f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.078f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.063f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = pendingIconOf(item.kind), contentDescription = null, modifier = Modifier.size(19.dp), tint = WizardColors.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = PendingTitleStyle, color = WizardColors.text)
                if (item.detail.isNotBlank()) Text(text = item.detail, style = LienzoCapStyle, color = WizardColors.textFaint)
            }
        }
        if (primary != null || secondary != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                primary?.let { (label, action) ->
                    ProgramLienzoPill(text = label, onClick = action, modifier = Modifier.weight(1f), small = true, tag = "pending_apply")
                }
                secondary?.let { (label, action) ->
                    ProgramLienzoPill(
                        text = label,
                        onClick = action,
                        modifier = Modifier.weight(1f),
                        filled = false,
                        small = true,
                        tag = "pending_keep",
                    )
                }
            }
        }
    }
}

/** El icono de cada tipo (p-pending: `trendUp`, `target`, `check`, `repeat`). */
internal fun pendingIconOf(kind: ProgramPendingKind): ImageVector = when (kind) {
    ProgramPendingKind.PROGRESSION, ProgramPendingKind.AUTOREGULATION -> KpknLineIcons.TrendUp
    ProgramPendingKind.MISSING_TM -> KpknLineIcons.Target
    ProgramPendingKind.WEEKLY_CHECKIN -> KpknLineIcons.Check
    ProgramPendingKind.CONTINUATION, ProgramPendingKind.PROGRAM_END_SOON -> KpknLineIcons.Repeat
    ProgramPendingKind.ONE_RM_TEST -> KpknLineIcons.Flag
    ProgramPendingKind.MISSED_SESSIONS -> KpknLineIcons.Calendar
    ProgramPendingKind.BLOCK_REVIEW, ProgramPendingKind.BLOCK_CHANGE -> KpknLineIcons.Sliders
}

/**
 * Una propuesta del motor que no es la destacada (p-pending: «los demás abren la hoja que abren hoy»), con el patrón corto de las hojas m-*:
 * su título, su detalle y «Aplicar» o «Mantener carga».
 */
@Composable
internal fun ProgramProposalSheet(item: ProgramPendingItem, onDismiss: () -> Unit) {
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = item.title, eyebrow = "Propuesta", sub = item.detail.ifBlank { null })
        item.primaryLabel?.let { label ->
            ProgramLienzoPill(
                text = label,
                onClick = {
                    onDismiss()
                    item.onPrimary?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                tag = "proposal_apply",
            )
        }
        item.secondaryLabel?.let { label ->
            ProgramLienzoPill(
                text = label,
                onClick = {
                    onDismiss()
                    item.onSecondary?.invoke()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                filled = false,
                tag = "proposal_keep",
            )
        }
    }
}

/** Un aviso sin decisión que tomar (el cambio de bloque, una propuesta ya vista) con el patrón corto: título, mensaje y «Entendido». */
@Composable
internal fun ProgramNoticeSheet(title: String, message: String, onAcknowledge: () -> Unit, onDismiss: () -> Unit) {
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = title, sub = message)
        ProgramLienzoPill(
            text = "Entendido",
            onClick = {
                onDismiss()
                onAcknowledge()
            },
            modifier = Modifier.fillMaxWidth(),
            tag = "notice_ack",
        )
    }
}
