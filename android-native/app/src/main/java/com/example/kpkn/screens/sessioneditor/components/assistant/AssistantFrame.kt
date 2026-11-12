package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.AssistantTab

/** Lo que cuelga de una pestaña: un contador de avisos o un punto de alerta. */
internal sealed interface AsTabBadge {
    data class Count(val value: Int) : AsTabBadge
    data object Dot : AsTabBadge
}

/**
 * El contenido de la hoja «Asistente de sesión» (`.as-sheet`): asa, título con el nombre de la sesión, pestañas en línea fina con su
 * indicador y el cuerpo de la pestaña. La hoja la dibuja quien la contiene (la hoja de la app o la hoja de dibujo de las pruebas).
 */
@Composable
internal fun AssistantSheetFrame(
    sessionName: String,
    tab: AssistantTab,
    badges: Map<AssistantTab, AsTabBadge>,
    onTab: (AssistantTab) -> Unit,
    onGrab: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        // .as-grab: 30 de alto, asa de 40 × 4 en blanco al 20 %; con el puntero encima (`:hover`) o al pulsar, al 33 %
        val grabSource = remember { MutableInteractionSource() }
        val grabHovered by grabSource.collectIsHoveredAsState()
        val grabPressed by grabSource.collectIsPressedAsState()
        // as-T06 as-grab i — el asa cambia de fondo en 0,2 s (ease)
        val grabColor by asColorState(if (grabHovered || grabPressed) AsColors.white(0x55) else AsColors.white(0x33), 200)
        Box(
            Modifier.fillMaxWidth().height(30.dp).hoverable(grabSource).semantics { contentDescription = "Cambiar la altura de la hoja" }
                .asClick(source = grabSource, onClick = onGrab),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(grabColor))
        }
        // .as-head
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        ) {
            AsText("Asistente de sesión", 20f, Modifier.weight(1f), weight = FontWeight.Bold, family = AsSyne, cap = 1.1f, lineHeight = 1.1f, letterSpacing = -0.4f, maxLines = 1)
            AsText(sessionName, 12f, Modifier.widthIn(max = 140.dp), color = AsColors.faint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        // .as-tabs: cuatro etiquetas repartidas en una línea fina; el indicador es una barra de 2 de tinta bajo la activa
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 6.dp)
                .drawBehind { drawLine(AsColors.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) },
        ) {
            val scrollTabs = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
            Row(
                Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth).padding(horizontal = 20.dp),
                horizontalArrangement = if (scrollTabs) Arrangement.spacedBy(18.dp) else Arrangement.SpaceBetween,
            ) {
                AssistantTab.entries.forEach { entry ->
                val on = entry == tab
                // as-T16 as-tabs button — color 0,2 s ease · as-T17 as-tabs button::after — el indicador pasa de scaleX(0) a scaleX(1) en 0,26 s (as-X09 / as-X10)
                val tabColor by asColorState(if (on) AsColors.ink else AsColors.faint, 200)
                val indicator = asFloatState(if (on) 1f else 0f, 260, AsAnim.Std)
                Row(
                    Modifier.height(46.dp).padding(horizontal = 2.dp)
                        .semantics { role = Role.Tab; selected = on }
                        .drawBehind {
                            val scale = indicator.value
                            if (scale > 0f) {
                                val w = size.width * scale
                                drawRoundRect(AsColors.ink, Offset((size.width - w) / 2f, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                            }
                        }
                        .asClick(role = Role.Tab, onClick = { onTab(entry) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AsText(entry.label, 14f, color = tabColor, weight = FontWeight.SemiBold, cap = 1.08f, maxLines = 1)
                    when (val badge = badges[entry]) {
                        is AsTabBadge.Count -> Box(
                            Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(RoundedCornerShape(9.dp)).background(AsColors.energy).padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) { AsText("${badge.value}", 10.5f, color = Color14110a, weight = FontWeight.Bold, cap = 1.1f, maxLines = 1) }
                        AsTabBadge.Dot -> Box(Modifier.size(7.dp).clip(CircleShape).background(AsColors.energy).semantics { contentDescription = "aviso" })
                        null -> Unit
                    }
                    }
                }
            }
        }
        // as-A11 `.as-pane.on` — cada pestaña entra con asIn de 0,26 s (10 px desde la derecha) al mostrarse
        Box(Modifier.weight(1f).fillMaxWidth().asEnter(AsAnim.PaneIn, key = tab)) { body() }
    }
}

private val Color14110a = androidx.compose.ui.graphics.Color(0xFF14110A)

/** `.as-sc`: cuerpo con scroll de una pestaña (16 / 20 / 36 de relleno, 12 de hueco). */
@Composable
internal fun AsScrollPane(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 36.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // as-X07 as-sc — máscara de degradado en los últimos 26 px del scroll (la máscara va antes del scroll: cae sobre la ventana)
    Column(
        modifier.fillMaxSize().asBottomMask(26.dp).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** `.as-toast`: aviso dentro de la hoja (Reglas aplicadas, Sin cambios…). */
@Composable
internal fun AsToast(title: String, sub: String?, modifier: Modifier = Modifier) {
    // as-S07 as-toast — sombra 0 12px 34px #000000cc (preset aviso) · as-A19 as-toast.open — entra con asRise de 0,3 s al abrirse
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).asEnter(AsAnim.ToastRise).kpknShadow(KpknShadow.Aviso, shape).clip(shape).background(AsColors.toast)
            .border(1.dp, AsColors.white(0x14), shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(AsColors.tint(AsColors.done, 0x24)), contentAlignment = Alignment.Center) {
            AsIcon(AsIcons.Check, 16.dp, AsColors.done)
        }
        Column(Modifier.weight(1f)) {
            AsText(title, 13.5f, lineHeight = 1.35f)
            if (sub != null) AsText(sub, 12f, Modifier.padding(top = 2.dp), color = AsColors.muted)
        }
    }
}
