package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.StructureSubTab
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import kotlin.math.min

/** Orden de las pestañas en la barra flotante: el enum no se reordena. */
internal val programDetailTabOrder: List<Pair<StructureSubTab, String>> = listOf(
    StructureSubTab.SEMANA to "Semana",
    StructureSubTab.VOLUMEN to "Progreso",
    StructureSubTab.MACROCICLO to "Estructura",
)

/** Alto de la barra flotante: el círculo y la píldora miden 48 dp. */
internal val ProgramFloatingBarHeight = 48.dp

/** Hueco que deja el contenido bajo la barra (sin contar la barra de estado): 12 de margen + 48 + 16. */
internal val ProgramFloatingBarContentInset = 76.dp

/** Punto de «hay pendientes» en ⋯: músculo del prototipo. */
private val PendingDotColor = Color(0xFFF49A6E)

/** Pestaña elegida: crema con tinta negra. */
private val TabSelectedFill = Color(0xFFF2EEE6)
private val TabSelectedInk = Color(0xFF111111)

/** Texto de las pestañas: Inter SemiBold 13 sp, una línea. */
private val FloatingTabStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
)

/**
 * La barra flotante de Program Detail: atrás a la izquierda, la píldora de vidrio con «Semana · Progreso · Estructura» en medio y ⋯ a
 * la derecha. Va sobre el contenido, bajo la barra de estado. Con [pendingDot], ⋯ lleva un punto de «hay pendientes».
 */
@Composable
internal fun ProgramFloatingBar(
    selected: StructureSubTab,
    onSelect: (StructureSubTab) -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    pendingDot: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(icon = KpknLineIcons.ChevronLeft, label = "Atrás", onClick = onBack)
        FloatingTabs(selected = selected, onSelect = onSelect, modifier = Modifier.weight(1f))
        GlassCircleButton(
            icon = KpknLineIcons.More,
            label = if (pendingDot) "Más opciones, hay pendientes" else "Más opciones",
            onClick = onMore,
            dot = pendingDot,
        )
    }
}

/** Círculo de vidrio de 48 dp con un icono. Con [dot], el punto de pendientes va arriba a la derecha, a 10 dp del borde. */
@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, onClick: () -> Unit, dot: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(ProgramFloatingBarHeight)
            .kpknGlassOrFallback(LocalHazeState.current, CircleShape)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.text)
        if (dot) {
            // Anillo de 2 dp en #1B1B1B alrededor de un punto de 8 dp: el punto queda a 10 dp del borde superior y del derecho.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B1B1B), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PendingDotColor, CircleShape))
            }
        }
    }
}

/**
 * La píldora de vidrio con las tres pestañas. El texto crece como mucho un 8 % con la escala de letra del sistema, como en el prototipo.
 */
@Composable
private fun FloatingTabs(selected: StructureSubTab, onSelect: (StructureSubTab) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val cappedDensity = Density(density = density.density, fontScale = min(density.fontScale, 1.08f))
    Row(
        modifier = modifier
            .height(ProgramFloatingBarHeight)
            .kpknGlassOrFallback(LocalHazeState.current, RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalDensity provides cappedDensity) {
            programDetailTabOrder.forEach { (tab, label) ->
                FloatingTab(
                    label = label,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Una pestaña. Toda su altura (48 dp) es zona táctil; la elegida pinta un fondo crema de 40 dp con radio 20 dp. Es `Role.Tab` y dice si
 * está seleccionada.
 */
@Composable
private fun FloatingTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .clip(shape)
                .then(if (selected) Modifier.background(TabSelectedFill, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = FloatingTabStyle,
                color = if (selected) TabSelectedInk else WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
