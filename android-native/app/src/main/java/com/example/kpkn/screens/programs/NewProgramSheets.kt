package com.example.kpkn.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.lineCoverValue
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** El título de las hojas de «Nuevo programa» y «Desde cero»: Syne Bold 22 sp. La página del catálogo lo reutiliza para sus filtros. */
internal val NewProgramTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.4).sp,
)

/** Las portadas que ofrece «Desde cero», en su orden; la primera es la de por defecto. */
private val BLANK_COVER_STYLES = listOf(LineCoverStyle.PERFIL, LineCoverStyle.RELIEVE, LineCoverStyle.ONDAS, LineCoverStyle.ESPIRAL)

/** Las modalidades de «Desde cero» con su etiqueta; la primera, Hipertrofia, es la de por defecto. */
private val BLANK_MODES: List<Pair<ProgramMode, String>> = listOf(
    ProgramMode.HYPERTROPHY to "Hipertrofia",
    ProgramMode.POWERBUILDING to "Powerbuilding",
    ProgramMode.POWERLIFTING to "Powerlifting",
)

/**
 * La hoja «Nuevo programa»: cuatro caminos para empezar. «A tu medida» (KPKN arma el programa) solo sale si la pantalla lo ofrece
 * ([onTailored] no null). «Del catálogo» abre la página de planes, «Importar» la importación y «Desde cero» su hoja propia.
 */
@Composable
internal fun NewProgramSheet(
    onDismiss: () -> Unit,
    onTailored: (() -> Unit)?,
    onCatalog: () -> Unit,
    onImport: () -> Unit,
    onBlank: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Nuevo programa", style = NewProgramTitleStyle, color = WizardColors.text)
            Text(text = "Elige cómo empezar.", style = KpknType.body, color = WizardColors.textMuted)
            onTailored?.let { tailored ->
                NewProgramRow(
                    title = "A tu medida",
                    subtitle = "KPKN lo arma con tu material, tus días y tu objetivo",
                    icon = KpknLineIcons.Bulb,
                    iconTint = SymbolPalette.musculo,
                    fill = Brush.linearGradient(
                        listOf(SymbolPalette.musculo.copy(alpha = 0.13f), Color.White.copy(alpha = 0.02f)),
                    ),
                    border = SymbolPalette.musculo.copy(alpha = 0.25f),
                    tag = "new-program-tailored",
                    onClick = tailored,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NewProgramCard(
                    title = "Del catálogo",
                    caption = "Planes conocidos y planes KPKN",
                    icon = KpknLineIcons.Book,
                    tag = "new-program-catalog",
                    onClick = onCatalog,
                    modifier = Modifier.weight(1f),
                )
                NewProgramCard(
                    title = "Importar",
                    caption = "Excel, Word, PDF, texto o una foto",
                    // No hay icono de importar en el set: «Layers» es el neutro que queda.
                    icon = KpknLineIcons.Layers,
                    tag = "new-program-import",
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
            }
            NewProgramRow(
                title = "Desde cero",
                subtitle = "Le pones nombre y armas las sesiones tú",
                icon = KpknLineIcons.Plus,
                iconTint = WizardColors.text,
                fill = SolidColor(KpknSurface.module),
                border = KpknSurface.moduleBorder,
                tag = "new-program-blank",
                onClick = onBlank,
            )
        }
    }
}

/**
 * La hoja «Desde cero»: un programa vacío con nombre, modalidad y portada. El nombre empieza vacío y, si se queda así, se usa
 * [suggestedName] (el «Nuevo programa N» siguiente). [onCreate] recibe el nombre, la modalidad y la portada `kpkn-lines://` lista para
 * guardar. Las semanas y las sesiones se arman después, dentro del programa.
 */
@Composable
internal fun BlankProgramSheet(
    suggestedName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, mode: ProgramMode, coverImage: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ProgramMode.HYPERTROPHY) }
    var style by remember { mutableStateOf(BLANK_COVER_STYLES.first()) }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Programa desde cero", style = NewProgramTitleStyle, color = WizardColors.text)
                Text(
                    text = "Después armas las semanas y sesiones dentro del programa.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
            KpknTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nombre",
                placeholder = suggestedName,
                modifier = Modifier.testTag("blank-program-name"),
            )
            BlankGroup(label = "Modalidad") {
                FlowRowOfModes(selected = mode, onSelect = { mode = it })
            }
            BlankGroup(label = "Portada") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BLANK_COVER_STYLES.forEach { option ->
                        BlankCoverChoice(
                            style = option,
                            selected = option == style,
                            onSelect = { style = option },
                        )
                    }
                }
            }
            KpknPrimaryPill(
                text = "Crear y abrir",
                onClick = {
                    onCreate(
                        name.trim().ifBlank { suggestedName },
                        mode,
                        lineCoverValue(style, LineCoverBackground.CARBON),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                tag = "blank-program-create",
            )
        }
    }
}

/** Los chips de modalidad de «Desde cero»: el elegido va relleno. Si no caben en una línea, bajan a la siguiente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOfModes(selected: ProgramMode, onSelect: (ProgramMode) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BLANK_MODES.forEach { (value, label) ->
            KpknChip(
                text = label,
                selected = value == selected,
                onClick = { onSelect(value) },
                modifier = Modifier.testTag("blank-mode-${value.name}"),
            )
        }
    }
}

/** Un grupo de «Desde cero»: su etiqueta en `caption` y su contenido debajo. */
@Composable
private fun BlankGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        content()
    }
}

/**
 * Una portada de «Desde cero»: la miniatura de 52 × 64 dp en carbón. La elegida lleva un filete de 2 dp en tinta con 2 dp de hueco
 * hasta la miniatura; la caja es siempre de 60 × 72 dp, así no salta el diseño al elegir.
 */
@Composable
private fun BlankCoverChoice(style: LineCoverStyle, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 72.dp)
            .testTag("blank-cover-${style.name}")
            .then(if (selected) Modifier.border(2.dp, WizardColors.text, shape) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = "Portada ${style.label}" },
        contentAlignment = Alignment.Center,
    ) {
        LineCoverArt(
            spec = LineCoverSpec(style = style, background = LineCoverBackground.CARBON, seed = 3),
            modifier = Modifier.size(width = 52.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

/**
 * Una opción a todo el ancho de «Nuevo programa»: icono, título, la línea que lo explica y una flecha. Mínimo 64 dp; [fill] y [border]
 * dan su color (el degradado de «A tu medida» o el módulo de siempre).
 */
@Composable
private fun NewProgramRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    fill: Brush,
    border: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(tag)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(text = subtitle, style = KpknType.caption, color = WizardColors.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = WizardColors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Una tarjeta de la fila de dos de «Nuevo programa»: icono arriba, título y una línea. Mínimo 64 dp, mismo módulo que Programas. */
@Composable
private fun NewProgramCard(
    title: String,
    caption: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .testTag(tag)
            .clip(shape)
            .background(KpknSurface.module, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(22.dp))
        Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
        Text(text = caption, style = KpknType.caption, color = WizardColors.textMuted)
    }
}
