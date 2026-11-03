package com.example.kpkn.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetSpec
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.LocalKpknSheetDismiss
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes

/** Franja de estado que el lienzo dibuja (38 dp). En una pantalla real manda el inset del sistema; las hojas de render lo fijan aquí. */
internal val LocalAjStatusTop = compositionLocalOf<Dp?> { null }

/** Cierra la hoja de Ajustes que lo contiene con su salida animada (o a la vez, fuera de una hoja). */
internal val LocalAjSheetClose = compositionLocalOf<() -> Unit> { {} }

/** Variables de `:root` del lienzo que usa el paquete `aj`. */
internal object AjColors {
    val Ink = WizardColors.text
    val Muted = WizardColors.textMuted
    val Faint = WizardColors.textFaint
    val Dim = Color(0xFF5F5C57)
    val Done = WizardColors.done
    val Danger = WizardColors.danger
    val Ene = Color(0xFFF7CF73)
    val Module = KpknSurface.module
    val Inset = KpknSurface.inset
    val Sheet = Color(0xFF1B1B1D)
    val IconBg = Color(0x0DFFFFFF)
    val OnInk = Color(0xFF111111)
    val Scrim = Color(0xA8000000)
}

/** Tipografía de `aj.css` (px del lienzo = sp). Inter para el cuerpo, Syne 700 para los títulos. */
internal object AjType {
    private val body = WizardFonts.body
    private val tnum = "tnum, lnum"
    val h1 = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.24.sp, letterSpacing = (-0.8).sp)
    val lead = TextStyle(fontFamily = body, fontSize = 13.5.sp, lineHeight = 20.25.sp, color = AjColors.Muted)
    val group = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp, color = AjColors.Faint)
    val rowTitle = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, lineHeight = 19.375.sp, color = AjColors.Ink)
    val rowSub = TextStyle(fontFamily = body, fontSize = 12.5.sp, lineHeight = 16.875.sp, color = AjColors.Faint)
    val rowValue = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 17.5.sp, color = AjColors.Muted)
    val modTitle = rowTitle.copy(fontSize = 16.sp, lineHeight = 20.sp)
    val subLabel = TextStyle(fontFamily = body, fontSize = 14.5.sp, lineHeight = 18.sp, color = AjColors.Muted)
    val time = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp, color = AjColors.Ink, fontFeatureSettings = tnum)
    val note = TextStyle(fontFamily = body, fontSize = 12.5.sp, lineHeight = 18.125.sp, color = AjColors.Faint)
    val warn = TextStyle(fontFamily = body, fontSize = 13.5.sp, lineHeight = 19.575.sp, color = AjColors.Muted)
    val button = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
    val buttonSmall = button.copy(fontSize = 13.5.sp, lineHeight = 16.2.sp)
    val textButton = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, color = AjColors.Muted)
    val permState = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, color = AjColors.Done)
    val blocked = TextStyle(fontFamily = body, fontSize = 12.5.sp, lineHeight = 16.875.sp, color = AjColors.Danger)
    val sheetTitle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.3.sp, letterSpacing = (-0.4).sp, color = AjColors.Ink)
    val sheetSub = TextStyle(fontFamily = body, fontSize = 13.5.sp, lineHeight = 20.25.sp, color = AjColors.Muted)
    val error = TextStyle(fontFamily = body, fontSize = 13.5.sp, lineHeight = 18.9.sp, color = AjColors.Danger)
    val line = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.6.sp, color = AjColors.Ink)
    val fileName = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, color = AjColors.Ink)
    val fileKind = TextStyle(fontFamily = body, fontSize = 12.sp, lineHeight = 15.sp, color = AjColors.Faint)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.6.sp, color = AjColors.Muted)
    val drumFar = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 22.sp, color = AjColors.Dim, fontFeatureSettings = tnum)
    val drumCur = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp, color = AjColors.Ink, fontFeatureSettings = tnum)
    val drumSep = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, color = AjColors.Faint)
    val toast = TextStyle(fontFamily = body, fontSize = 13.5.sp, lineHeight = 18.225.sp, color = AjColors.Ink)
}

/** Animaciones propias de `aj.css` que no son una familia del catálogo con el mismo desplazamiento. */
internal object AjMotion {
    /** `aj-push` (aj-A02): la subpantalla entra desde la derecha, 0,3 s `cubic-bezier(.2,.8,.2,1)`, `both`. */
    val Push = KpknAnimation(
        kpknKeyframes("aj-push", "@keyframes aj-push { 0% { transform: translateX(24px); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { translateX(24f); alpha(0f) }
            at(1f) { transformNone(); alpha(1f) }
        },
        KpknDuration(300), KpknMotion.Estandar, fill = KpknFill.Both,
    )

    /** `aj-pop` (aj-A01): al volver, la pantalla entra desde la izquierda; es la familia `entrada-izquierda` tal cual (24 px, 0,3 s). */
    val Pop: KpknAnimation = KpknFamilies.EntradaIzquierda.demo

    /** `aj-toast` (aj-A05): el aviso sube 16 px con fundido, 0,28 s. */
    val Toast = KpknAnimation(
        kpknKeyframes("aj-toast", "@keyframes aj-toast { 0% { transform: translateY(16px); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { translateY(16f); alpha(0f) }
            at(1f) { transformNone(); alpha(1f) }
        },
        KpknDuration(280), KpknMotion.Estandar, fill = KpknFill.Both,
    )

    /** `aj-spin` (aj-A06): `giro` de 0,8 s lineal, infinito. */
    val Spin: KpknAnimation = KpknFamilies.Giro.demo

    /** `.aj-sw` / `.aj-rad` / `.aj-hold` (aj-T01…T06). */
    val SwitchBackground = KpknDuration(180)
    val SwitchThumb = KpknDuration(220)
    val RadioBorder = KpknDuration(160)
    val RadioDot = KpknDuration(180)
    val HoldFill = KpknDuration(1200)
    val HoldRelease = KpknDuration(200)
    const val HoldMs = 1200L
    const val ToastMs = 2900L
}

/**
 * La hoja de Ajustes (`.pl-sheet` + `aj-sheet`/`aj-fade`): sombra `hoja` (aj-S03), velo 0,22 s ease-out (aj-A03) y entrada 0,36 s
 * `cubic-bezier(.2,.8,.2,1)` (aj-A04). El lienzo la sube 40 px con fundido; `KpknSheet` la sube desde el borde inferior (DESV-1401).
 */
internal val AjSheetSpec = KpknSheetSpec(
    shadow = KpknShadow.Hoja.spec,
    scrimInMs = 220,
    scrimEasing = KpknMotion.Salida,
    sheetInMs = 360,
    sheetEasing = KpknMotion.Estandar,
    contentDelayMs = 0,
    contentInMs = 1,
    contentEasing = KpknMotion.Lineal,
    contentRise = 0.dp,
    outMs = 240,
    outEasing = KpknMotion.Cae,
    outTravel = 1f,
    scrimOutMs = 200,
    heightMs = 1,
    heightEasing = KpknMotion.Lineal,
    scrimOutEasing = KpknMotion.Suave,
)

@Composable
internal fun AjSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // pulido:aj-S03 sombra de la hoja · aj-A03 velo · aj-A04 entrada
    KpknSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dismissible = dismissible,
        surfaceColor = AjColors.Sheet,
        maxHeightFraction = 0.9f,
        stableHeightFraction = if (tall) 0.82f else null,
        backdropScrim = AjColors.Scrim,
        lienzo = AjSheetSpec,
    ) {
        val animated = LocalKpknSheetDismiss.current
        CompositionLocalProvider(LocalAjSheetClose provides { if (animated != null) animated() else onDismiss() }) {
            AjSheetColumn(if (tall) Modifier.weight(1f) else Modifier, content = content)
        }
    }
}

/** El interior de `.pl-sheet`: 20 dp a los lados, 4 arriba (bajo el asa), 34 abajo y 12 entre piezas. */
@Composable
internal fun AjSheetColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun statusTop(): Dp = LocalAjStatusTop.current ?: WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** `.topbar .glass` con la flecha: botón de atrás de 48 dp de vidrio de barra (aj-S04, aj-V02, aj-G03). */
@Composable
internal fun AjBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haze = LocalHazeState.current
    Box(
        modifier = modifier
            .size(48.dp)
            // pulido:aj-S04 barra-vidrio · aj-V02 vidrio-barra · aj-G03 vidrio-relleno-barra
            .kpknShadow(KpknShadowSpec(KpknShadow.BarraVidrio.spec.outerLayers), CircleShape)
            .kpknGlassPreset(haze, KpknGlassPreset.VidrioBarra, CircleShape)
            .kpknGradient(KpknGradient.VidrioRellenoBarra, CircleShape)
            .kpknInset(KpknShadow.BarraVidrio, CircleShape)
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .semantics { contentDescription = "Atrás"; role = Role.Button }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .testTag("aj-back"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KpknLineIcons.ChevronLeft, contentDescription = null, tint = AjColors.Ink, modifier = Modifier.size(20.dp))
    }
}

/**
 * La página (`.scroll.aj-page`): negro continuo, título grande de Syne a 104 dp del borde (franja de 38 + 66), 16 dp a los lados, 64 abajo. El
 * botón de atrás flota encima (la lista pasa por debajo). [enter] es la entrada al navegar (aj-A01 volver / aj-A02 avanzar).
 */
@Composable
internal fun AjPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lead: String? = null,
    enter: KpknAnimation? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = statusTop()
    Box(
        modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .then(if (enter != null) Modifier.kpknAnimate(enter) else Modifier),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = top + 66.dp, bottom = 64.dp),
        ) {
            Text(title, style = AjType.h1, color = AjColors.Ink, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = if (lead != null) 4.dp else 0.dp).semantics { heading() })
            if (lead != null) Text(lead, style = AjType.lead, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
            content()
        }
        AjBackButton(onBack, Modifier.padding(start = 12.dp, top = top))
    }
}

@Composable
internal fun AjGroup(label: String) {
    Text(label.uppercase(), style = AjType.group, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 28.dp, bottom = 4.dp).semantics { heading() })
}

/** Fila pulsable sin onda: al pulsar se tiñe `#ffffff08` (`.aj-row:active`). */
@Composable
internal fun Modifier.ajPressable(onClick: () -> Unit, role: Role = Role.Button, enabled: Boolean = true): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .background(if (pressed && enabled) Color(0x08FFFFFF) else Color.Transparent)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

private fun Modifier.ajDivider(show: Boolean): Modifier = if (!show) this else drawBehind {
    drawLine(Color(0x0FFFFFFF), Offset(58.dp.toPx(), 0f), Offset(size.width - 4.dp.toPx(), 0f), 1.dp.toPx())
}

@Composable
internal fun AjIconCircle(icon: ImageVector, bad: Boolean = false, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (bad) Color(0x12FF9B92) else AjColors.IconBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (bad) AjColors.Danger else AjColors.Ink, modifier = Modifier.size(20.dp))
    }
}

/** `.aj-row`: icono en círculo, título (y descripción), valor a la derecha y flecha. */
@Composable
internal fun AjRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    value: String? = null,
    valueColor: Color = AjColors.Muted,
    dot: Boolean = false,
    bad: Boolean = false,
    chevron: Boolean = true,
    divider: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .ajDivider(divider)
            .ajPressable(onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AjIconCircle(icon, bad)
        Column(Modifier.weight(1f)) {
            Text(title, style = AjType.rowTitle, color = if (bad) AjColors.Danger else AjColors.Ink)
            if (sub != null) Text(sub, style = AjType.rowSub, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) {
            Row(Modifier.widthIn(max = 144.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (dot) Box(Modifier.size(7.dp).background(valueColor, CircleShape))
                Text(value, style = AjType.rowValue, color = valueColor, textAlign = TextAlign.End)
            }
        }
        if (chevron) Icon(AjIcons.Forward, contentDescription = null, tint = AjColors.Dim, modifier = Modifier.size(16.dp))
    }
}

enum class AjPermState { GRANTED, DENIED, BLOCKED }

/** `.aj-prow`: título y estado arriba, el para qué debajo a todo el ancho de la columna de texto. */
@Composable
internal fun AjPermRow(
    icon: ImageVector,
    title: String,
    description: String,
    state: AjPermState,
    systemOnly: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .ajDivider(divider)
            .ajPressable(onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AjIconCircle(icon)
            Text(title, style = AjType.rowTitle, modifier = Modifier.weight(1f))
            when {
                state == AjPermState.GRANTED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AjIcons.Ok, contentDescription = null, tint = AjColors.Done, modifier = Modifier.size(18.dp))
                    Text("Concedido", style = AjType.permState)
                }
                state == AjPermState.BLOCKED || systemOnly -> AjButtonFace("Abrir ajustes", AjButtonTone.Ghost, small = true)
                else -> AjButtonFace("Activar", AjButtonTone.Primary, small = true)
            }
        }
        Column(Modifier.padding(start = 54.dp)) {
            Text(description, style = AjType.rowSub, modifier = Modifier.padding(top = 2.dp))
            if (state == AjPermState.BLOCKED) Text("Bloqueado: el sistema ya no pregunta.", style = AjType.blocked, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

enum class AjButtonTone { Primary, Ghost, Danger, Line }

/** Solo el dibujo de `.aj-btn` (para filas que ya son pulsables). */
@Composable
internal fun AjButtonFace(
    text: String,
    tone: AjButtonTone,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val fs = LocalDensity.current.fontScale
    val (bg, fg, border) = when (tone) {
        AjButtonTone.Primary -> Triple(AjColors.Ink, AjColors.OnInk, null)
        AjButtonTone.Ghost -> Triple(Color(0x0FFFFFFF), AjColors.Ink, Color(0x1FFFFFFF))
        AjButtonTone.Danger -> Triple(Color(0x14FF9B92), AjColors.Danger, Color(0x33FF9B92))
        AjButtonTone.Line -> Triple(Color.Transparent, AjColors.Ink, Color(0x3DF2EEE6))
    }
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .defaultMinSize(minHeight = if (small) 44.dp else 52.dp * fs)
            .clip(shape)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .padding(horizontal = if (small) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) AjSpinner(fg)
        else if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = if (small) AjType.buttonSmall else AjType.button, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun AjButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AjButtonTone = AjButtonTone.Primary,
    small: Boolean = false,
    fill: Boolean = false,
    icon: ImageVector? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    tag: String? = null,
) {
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { if (!enabled) stateDescription = "No disponible" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        AjButtonFace(text, tone, if (fill) Modifier.fillMaxWidth() else Modifier, small, icon, busy, enabled)
    }
}

/** `.aj-txt`: acción secundaria de texto (Cancelar). */
@Composable
internal fun AjTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AjType.textButton) }
}

/** `.aj-spin` (aj-A06): 18 dp, anillo de `#ffffff30` con el cuarto superior en tinta, 0,8 s lineal infinito. */
@Composable
internal fun AjSpinner(color: Color = AjColors.Ink, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).kpknAnimate(AjMotion.Spin)) {
        val w = 2.dp.toPx()
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        drawArc(Color(0x30FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        drawArc(color, -135f, 90f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
    }
}

/** `.aj-sw` (52 × 32, pulgar 24): aj-T05 fondo 0,18 s `ease`, aj-T06 pulgar 0,22 s y color 0,18 s, aj-X05 `translateX(20px)`. */
@Composable
internal fun AjSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val track by animateKpknColorAsState(if (on) AjColors.Ink else Color(0xFF2A2A2A), AjMotion.SwitchBackground, KpknMotion.Suave)
    val thumbColor by animateKpknColorAsState(if (on) Color(0xFF111111) else AjColors.Faint, AjMotion.SwitchBackground, KpknMotion.Suave)
    val x by animateKpknFloatAsState(if (on) 20f else 0f, AjMotion.SwitchThumb, KpknMotion.Estandar)
    Box(modifier.size(width = 52.dp, height = 32.dp).background(track, RoundedCornerShape(16.dp))) {
        Box(Modifier.offset(x = 4.dp + x.dp, y = 4.dp).size(24.dp).background(thumbColor, CircleShape))
    }
}

/** `.aj-opt`: opción de hoja con radio (aj-T03, aj-T04, aj-X03, aj-X04) o casilla. */
@Composable
internal fun AjOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    check: Boolean = false,
    enabled: Boolean = true,
    chevron: Boolean = false,
    divider: Boolean = true,
    tag: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (divider) Modifier.drawBehind { drawLine(Color(0x0FFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { this.selected = selected }
            .ajPressable(onClick, role = if (check) Role.Checkbox else if (chevron) Role.Button else Role.RadioButton, enabled = enabled)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AjType.rowTitle)
            if (sub != null) Text(sub, style = AjType.rowSub, modifier = Modifier.padding(top = 3.dp))
        }
        when {
            chevron -> Icon(AjIcons.Forward, contentDescription = null, tint = AjColors.Dim, modifier = Modifier.size(16.dp))
            check -> {
                val bg by animateKpknColorAsState(if (selected) AjColors.Ink else Color.Transparent, AjMotion.RadioBorder, KpknMotion.Suave)
                val bd by animateKpknColorAsState(if (selected) AjColors.Ink else Color(0x40FFFFFF), AjMotion.RadioBorder, KpknMotion.Suave)
                Box(
                    Modifier.size(22.dp).background(bg, RoundedCornerShape(7.dp)).border(2.dp, bd, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(AjIcons.Check, contentDescription = null, tint = AjColors.OnInk, modifier = Modifier.size(14.dp)) }
            }
            else -> {
                val bd by animateKpknColorAsState(if (selected) AjColors.Ink else Color(0x40FFFFFF), AjMotion.RadioBorder, KpknMotion.Suave)
                val dot by animateKpknFloatAsState(if (selected) 1f else 0f, AjMotion.RadioDot, KpknMotion.Estandar)
                Box(Modifier.size(22.dp).border(2.dp, bd, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(10.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(AjColors.Ink, CircleShape))
                }
            }
        }
    }
}

/** `.aj-mod`: módulo `#111` con filete, solo donde hay controles de datos. */
@Composable
internal fun AjModule(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp).background(AjColors.Module, shape).border(1.dp, Color(0x12FFFFFF), shape).padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** Cabecera de un módulo de recordatorio: icono, título y descripción, interruptor. Toda la fila es el interruptor. */
@Composable
internal fun AjModuleHeader(icon: ImageVector, title: String, sub: String, on: Boolean, onToggle: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .testTag(tag)
            .toggleable(value = on, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AjIconCircle(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = AjType.modTitle)
            Text(sub, style = AjType.rowSub, modifier = Modifier.padding(top = 2.dp))
        }
        AjSwitch(on)
    }
}

/** Fila de módulo con hora: etiqueta a la izquierda y la hora en una píldora `inset` a la derecha. */
@Composable
internal fun AjTimeLine(label: String, value: String, onClick: () -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind { drawLine(Color(0x0FFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AjType.subLabel)
        Box(
            Modifier
                .testTag(tag)
                .defaultMinSize(minWidth = 84.dp, minHeight = 44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AjColors.Inset, RoundedCornerShape(14.dp))
                .semantics { contentDescription = "Cambiar hora, ahora $value" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(value, style = AjType.time) }
    }
}

/** `.aj-note`: «Recibirás: «texto»» con el texto citado en `muted`. */
@Composable
internal fun AjReceives(text: String) {
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        append("Recibirás: ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = AjColors.Muted))
        append("«$text»")
        pop()
    }
    Text(annotated, style = AjType.note, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
}

/** `.aj-warn`: aviso de permiso sobre Recordatorios, con su botón. */
@Composable
internal fun AjWarn(icon: ImageVector, bold: String, rest: String, blocked: Boolean, buttonText: String, ghost: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val tint = if (blocked) AjColors.Danger else AjColors.Ene
    val border = if (blocked) Color(0x38FF9B92) else Color(0x38F7CF73)
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).background(AjColors.Module, shape).border(1.dp, border, shape).padding(16.dp).testTag("aj-warn"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(22.dp))
            val text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = AjColors.Ink, fontWeight = FontWeight.SemiBold))
                append(bold)
                pop()
                append(" ")
                append(rest)
            }
            Text(text, style = AjType.warn)
        }
        AjButton(buttonText, onClick, tone = if (ghost) AjButtonTone.Ghost else AjButtonTone.Primary, small = true, fill = true, tag = "aj-warn-button")
    }
}

/** Estado de un aviso de resultado. */
enum class AjToastKind { Neutral, Ok, Bad }

/**
 * `.aj-toast.glass`: cápsula de vidrio sobre la pantalla, a 40 dp del borde inferior; entra con `aj-toast` (aj-A05). Vidrio de hoja con su relleno
 * y la sombra `isla-vidrio` en el valor exacto de la fila (aj-S02: `0 10px 30px #0009` + realce `#ffffff12`).
 */
@Composable
internal fun AjToast(message: String, kind: AjToastKind, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(27.dp)
    val haze = LocalHazeState.current
    // pulido:aj-S02 isla-vidrio exacta · aj-V01 vidrio-hoja · aj-G02 vidrio-relleno-hoja
    val spec = KpknShadow.IslaVidrio.spec
        .copyLayer(0) { it.copy(y = 10.dp, blur = 30.dp, color = Color(0x99000000)) }
        .copyLayer(1) { it.copy(color = Color(0x12FFFFFF)) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 40.dp + navBottom)
            .fillMaxWidth()
            .kpknAnimate(AjMotion.Toast)
            .heightIn(min = 54.dp)
            .kpknShadow(KpknShadowSpec(spec.outerLayers), shape)
            .kpknGlassPreset(haze, KpknGlassPreset.VidrioHoja, shape)
            .kpknGradient(KpknGradient.VidrioRellenoHoja, shape)
            .kpknInset(spec, shape)
            .border(1.dp, Color(0x22FFFFFF), shape)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { contentDescription = message }
            .testTag("aj-toast"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (kind != AjToastKind.Neutral) {
            Icon(
                if (kind == AjToastKind.Bad) AjIcons.Warn else AjIcons.Ok,
                contentDescription = null,
                tint = if (kind == AjToastKind.Bad) AjColors.Danger else AjColors.Done,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(message, style = AjType.toast, overflow = TextOverflow.Clip)
    }
}

@Composable internal fun AjSheetTitle(text: String) = Text(text, style = AjType.sheetTitle, modifier = Modifier.padding(top = 2.dp).semantics { heading() })
@Composable internal fun AjSheetSub(text: String) = Text(text, style = AjType.sheetSub)
