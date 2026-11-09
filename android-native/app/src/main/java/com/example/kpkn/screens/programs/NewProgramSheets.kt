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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** El título de las hojas de Programas (`.sheet h3` del lienzo): Syne Bold 21 sp. La página del catálogo lo reutiliza para sus filtros. */
internal val NewProgramTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.4).sp,
)

/** Las portadas que ofrece «Desde cero», en su orden; la primera es la de por defecto. */
private val BLANK_COVER_STYLES = listOf(
    LineCoverStyle.PERFIL, LineCoverStyle.RELIEVE, LineCoverStyle.ONDAS,
    LineCoverStyle.INTERFERENCIA, LineCoverStyle.ABANICO, LineCoverStyle.ESPIRAL,
)

/** Etiquetas deportivas del programa; solo describen su identidad y no eligen un motor de prescripción. */
private val BLANK_MODALITIES = listOf(
    "Fuerza general", "Hipertrofia", "Powerlifting", "Powerbuilding", "Levantamiento olímpico",
    "Calistenia", "CrossFit", "HYROX", "Running", "Ciclismo", "Natación", "Remo", "Movilidad", "Personalizada",
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
        ) {
            Text(text = "Nuevo programa", style = NewProgramTitleStyle, color = WizardColors.text)
            Spacer(Modifier.height(4.dp))
            Text(text = "Elige cómo empezar.", style = KpknType.body, color = WizardColors.textMuted)
            // `.opt-grid`: margen superior de 6 dp sobre los 12 dp bajo el subtítulo, y 8 dp entre opciones.
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onTailored?.let { tailored ->
                    NewProgramRow(
                        title = "A tu medida",
                        subtitle = "KPKN lo arma con tu material, tus días y tu objetivo",
                        icon = ProgramLineIcons.Spark,
                        iconTint = SymbolPalette.musculo,
                        fill = Brush.linearGradient(
                            listOf(SymbolPalette.musculo.copy(alpha = 0.13f), Color.White.copy(alpha = 0.024f)),
                        ),
                        border = SymbolPalette.musculo.copy(alpha = 0.25f),
                        tag = "new-program-tailored",
                        onClick = tailored,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        icon = ProgramLineIcons.Upload,
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
                    fill = SolidColor(OptFill),
                    border = OptBorder,
                    tag = "new-program-blank",
                    onClick = onBlank,
                )
            }
        }
    }
}

/** El fondo y el filete de una opción (`.opt`): blanco al 3 % y al 7 %. */
private val OptFill = Color.White.copy(alpha = 0.03f)
private val OptBorder = Color.White.copy(alpha = 0.07f)

/** El círculo de 36 dp de una opción (`.opt .ic`): blanco al 6 % con el icono de 20 dp. */
@Composable
private fun NewProgramIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.063f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * La hoja «Desde cero»: un programa vacío con nombre, modalidad y portada. El nombre empieza vacío y, si se queda así, se usa
 * [suggestedName] (el «Nuevo programa N» siguiente). [onCreate] recibe el nombre, la modalidad y la portada `kpkn-lines://` lista para
 * guardar. Las semanas y las sesiones se arman después, dentro del programa.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BlankProgramSheet(
    suggestedName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, coverImage: String, identityLabel: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedModality by remember { mutableStateOf<String?>(null) }
    var customModality by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(BLANK_COVER_STYLES.first()) }
    var background by remember { mutableStateOf(LineCoverBackground.CARBON) }
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
            BlankGroup(label = "Modalidad del programa · etiqueta") {
                FlowRowOfModalities(selected = selectedModality, onSelect = { selectedModality = it })
                if (selectedModality == "Personalizada") {
                    KpknTextField(
                        value = customModality,
                        onValueChange = { customModality = it.take(48) },
                        label = "Etiqueta personalizada",
                        placeholder = "Ej. Escalada",
                        modifier = Modifier.testTag("blank-program-custom-modality"),
                    )
                }
            }
            BlankGroup(label = "Portada") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BLANK_COVER_STYLES.forEach { option ->
                        BlankCoverChoice(
                            style = option,
                            background = background,
                            selected = option == style,
                            onSelect = { style = option },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineCoverBackground.entries.forEach { bgOption ->
                        BlankBackgroundDot(
                            background = bgOption,
                            selected = bgOption == background,
                            onSelect = { background = bgOption },
                        )
                    }
                }
            }
            KpknPrimaryPill(
                text = "Crear y abrir",
                onClick = {
                    val modality = selectedModality?.let { if (it == "Personalizada") customModality.trim() else it }
                        ?.takeIf(String::isNotBlank) ?: return@KpknPrimaryPill
                    onCreate(
                        name.trim().ifBlank { suggestedName },
                        lineCoverValue(style, background),
                        modality,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                tag = "blank-program-create",
                enabled = selectedModality != null && (selectedModality != "Personalizada" || customModality.isNotBlank()),
            )
        }
    }
}

/** Los chips de modalidad de «Desde cero»: el elegido va relleno. Si no caben en una línea, bajan a la siguiente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOfModalities(selected: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BLANK_MODALITIES.forEach { label ->
            KpknChip(
                height = 30.dp,
                text = label,
                selected = label == selected,
                onClick = { onSelect(label) },
                modifier = Modifier.testTag("blank-modality-${label.lowercase().replace(' ', '-')}") ,
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
internal fun BlankCoverChoice(
    style: LineCoverStyle,
    background: LineCoverBackground,
    selected: Boolean,
    onSelect: () -> Unit,
) {
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
            .semantics {
                contentDescription = "Portada ${style.label}, fondo ${background.label}"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        LineCoverArt(
            spec = LineCoverSpec(style = style, background = background, seed = 3),
            modifier = Modifier.size(width = 52.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

/**
 * Una opción a todo el ancho de «Nuevo programa» (`.opt.wide`): icono en círculo, título, la línea que lo explica y una flecha. Mínimo
 * 72 dp; [fill] y [border] dan su color (el degradado de «A tu medida» o el de las opciones).
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
            .heightIn(min = 72.dp)
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
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewProgramIcon(icon = icon, tint = iconTint)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(text = subtitle, style = KpknType.caption, color = WizardColors.textFaint)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = WizardColors.textFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Una tarjeta de la fila de dos de «Nuevo programa» (`.opt`): icono en círculo arriba, título y una línea. Mínimo 112 dp. */
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
            .heightIn(min = 112.dp)
            .testTag(tag)
            .clip(shape)
            .background(OptFill, shape)
            .border(1.dp, OptBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NewProgramIcon(icon = icon, tint = WizardColors.text)
        Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
        Text(text = caption, style = KpknType.caption, color = WizardColors.textFaint)
    }
}

@Composable
private fun BlankBackgroundDot(
    background: LineCoverBackground,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val bg = Color(background.bg)
    val accent = Color(background.accent)
    val ring = WizardColors.text
    Box(
        modifier = Modifier
            .size(26.dp)
            .drawBehind {
                if (selected) {
                    drawCircle(color = ring, radius = size.minDimension / 2f + 3.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .background(bg)
            .drawBehind {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, accent)
            }
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .testTag("blank-cover-bg-${background.name.lowercase()}"),
    )
}
