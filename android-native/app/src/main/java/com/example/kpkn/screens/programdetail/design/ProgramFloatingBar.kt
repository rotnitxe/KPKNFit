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
import androidx.compose.ui.layout.layout
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
import dev.chrisbanes.haze.HazeState
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
 * La barra flotante de Program Detail (`topbar2` de Arreglos v1): atrás a la izquierda y, a su derecha, la píldora de vidrio con
 * «Semana · Progreso · Estructura», que ocupa todo el ancho que queda. Va sobre el contenido, bajo la barra de estado. ⋯ ya no está aquí:
 * vive en la fila del nombre del programa ([ProgramMoreButton]).
 *
 * El vidrio desenfoca [hazeState]: la fuente (`hazeSource`) es el contenido desplazable de la pantalla, y esta barra es su hermana dibujada
 * después. Sin él, el vidrio cae a su velo sin desenfoque.
 */
@Composable
internal fun ProgramFloatingBar(
    selected: StructureSubTab,
    onSelect: (StructureSubTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(icon = KpknLineIcons.ChevronLeft, label = "Atrás", onClick = onBack, hazeState = hazeState)
        FloatingTabs(selected = selected, onSelect = onSelect, hazeState = hazeState, modifier = Modifier.weight(1f))
    }
}

/**
 * ⋯ de la fila del nombre (`.ib.pd-more`): botón de 40 dp sin fondo, icono de 20 dp en `textMuted`, con margen de −4 dp arriba y −6 dp a
 * la derecha (ocupa 34 × 36 dp en la fila y sobresale lo que dice el margen). Con [pendingDot], punto de 8 dp en #F49A6E con anillo negro de
 * 2 dp, a 7 dp del borde superior y del derecho. Abre el mismo menú del programa de siempre.
 */
@Composable
internal fun ProgramMoreButton(onClick: () -> Unit, pendingDot: Boolean, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val overhangEnd = 6.dp
    val overhangTop = 4.dp
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                // Margen negativo del CSS: la fila cuenta 34 × 36 dp, pero el botón mide 40 × 40 y se pinta desde −4 dp.
                val placeable = measurable.measure(constraints)
                val endPx = overhangEnd.roundToPx()
                val topPx = overhangTop.roundToPx()
                layout(placeable.width - endPx, placeable.height - topPx) { placeable.place(0, -topPx) }
            }
            .size(40.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = if (pendingDot) "Más opciones del programa, hay pendientes" else "Más opciones del programa"
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = KpknLineIcons.More, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.textMuted)
        if (pendingDot) {
            // Anillo negro de 2 dp alrededor de un punto de 8 dp: el punto queda a 7 dp del borde superior y del derecho.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PendingDotColor, CircleShape))
            }
        }
    }
}

/** Círculo de vidrio de 48 dp con un icono. */
@Composable
private fun GlassCircleButton(icon: ImageVector, label: String, onClick: () -> Unit, hazeState: HazeState?) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(ProgramFloatingBarHeight)
            .kpknGlassOrFallback(hazeState, CircleShape)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.text)
    }
}

/**
 * La píldora de vidrio con las tres pestañas. El texto crece como mucho un 8 % con la escala de letra del sistema, como en el prototipo.
 */
@Composable
private fun FloatingTabs(
    selected: StructureSubTab,
    onSelect: (StructureSubTab) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cappedDensity = Density(density = density.density, fontScale = min(density.fontScale, 1.08f))
    Row(
        modifier = modifier
            .height(ProgramFloatingBarHeight)
            .kpknGlassOrFallback(hazeState, RoundedCornerShape(24.dp))
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
