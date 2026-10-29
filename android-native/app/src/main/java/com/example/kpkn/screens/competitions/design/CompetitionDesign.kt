package com.example.kpkn.screens.competitions.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionMedalHonor
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import java.util.Locale
import kotlin.math.pow

/** Los tokens del lienzo (`styles.css` de Program Detail + `extra.css` de Registros v4). */
internal object CompColors {
    val ink = WizardColors.text
    val muted = Color(0xFFC4BFB6)
    val faint = Color(0xFF8E8980)
    val dim = Color(0xFF5F5C57)
    val module = KpknSurface.module
    val inset = Color(0xFF1A1A1A)
    val line = Color(0x12FFFFFF)
    val div = Color(0x0FFFFFFF)
    val mus = Color(0xFFF49A6E)
    val ene = Color(0xFFF7CF73)
    val col = Color(0xFF8FB2FF)
    val done = Color(0xFF43D18C)
    val danger = Color(0xFFFF9B92)
    val card = Color(0xFF0C0C0C)
}

/** Los colores de las medallas (`MEDAL` de `data.js`). */
internal object MedalColors {
    fun of(medal: CompetitionMedalHonor?): Pair<Color, Color> = when (medal) {
        CompetitionMedalHonor.GOLD -> Color(0xFFF7CF73) to Color(0xFFB8913A)
        CompetitionMedalHonor.SILVER -> Color(0xFFD9D6D0) to Color(0xFF8F8B84)
        CompetitionMedalHonor.BRONZE -> Color(0xFFE39A6A) to Color(0xFF9A5B37)
        null -> Color(0xFF8FB2FF) to Color(0xFF4F6EA8)
    }
}

/** Tipos del lienzo, con el mínimo de 13 sp de la app (KpknType) donde el lienzo baja a 10–12 px. */
internal object CompType {
    private val tnum = "'lnum' 1, 'tnum' 1"
    fun syne(size: Int, weight: FontWeight = FontWeight.ExtraBold, line: Float = 1.05f, spacing: Float = 0f) = TextStyle(
        fontFamily = WizardFonts.display, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp,
        letterSpacing = spacing.sp, fontFeatureSettings = tnum,
    )
    fun inter(size: Float, weight: FontWeight = FontWeight.Normal, line: Float = 1.35f, spacing: Float = 0f) = TextStyle(
        fontFamily = WizardFonts.body, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp, letterSpacing = spacing.sp,
    )
    val ptitle = syne(28, spacing = -0.8f)
    val stepTitle = syne(26, spacing = -0.8f)
    val lbl = inter(13f, FontWeight.SemiBold, 1.3f, 1.3f)
    val cap = inter(13f)
    val body = inter(14f, line = 1.45f)
    val ey = inter(13f, FontWeight.SemiBold, 1.3f, 1.2f)
    val back = inter(15f, FontWeight.SemiBold, 1.3f)
    val act = inter(13f, FontWeight.SemiBold, 1.3f)
    val rowTitle = inter(14.5f, FontWeight.SemiBold, 1.3f)
    val pill = inter(15f, FontWeight.SemiBold, 1.2f)
    val pillSm = inter(13f, FontWeight.SemiBold, 1.2f)
    val unit = inter(15f, FontWeight.SemiBold, 1.2f)
    val chip = inter(13f, FontWeight.Medium, 1.2f)
    val sheetTitle = syne(21, FontWeight.Bold, 1.15f, -0.4f)
}

private val CompEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** La pantalla del lienzo (`.scroll`): negro, 52 dp arriba con la barra de estado, 16 a los lados y aire abajo. */
@Composable
internal fun CompScreen(modifier: Modifier = Modifier, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(KpknSurface.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            content()
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** El botón redondo relleno de 44 dp (`.ib.fill`). */
@Composable
internal fun CompIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, glass: Boolean = false) {
    Box(
        modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (glass) Brush.linearGradient(listOf(Color(0xB82C2C2C), Color(0xB0161616))) else SolidColor(CompColors.inset))
                .then(if (glass) Modifier.border(1.dp, Color(0x22FFFFFF), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (glass) CompColors.ink else CompColors.muted)
        }
    }
}

/** La fila de atrás (`back()`): flecha rellena, título de 15 y lo que vaya a la derecha. */
@Composable
internal fun CompBackRow(title: String, onBack: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().offset(x = (-6).dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompIconButton(CompetitionIcons.Back, "Atrás", onBack)
        Text(title, Modifier.weight(1f).semantics { heading() }, style = CompType.back, color = CompColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        extra()
    }
}

@Composable
internal fun CompPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 6.dp, bottom = 2.dp).semantics { heading() }, style = CompType.ptitle, color = CompColors.ink)
}

@Composable
internal fun CompStepTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = CompType.stepTitle, color = CompColors.ink)
}

/** Los tres segmentos de paso (`.steps`). */
@Composable
internal fun CompSteps(on: Int, total: Int = 3) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp).semantics { contentDescription = "Paso $on de $total" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < on) CompColors.ink else Color(0x22FFFFFF)))
        }
    }
}

@Composable
internal fun CompLabel(text: String, modifier: Modifier = Modifier, color: Color = CompColors.faint) {
    Text(text.uppercase(Locale("es")), modifier, style = CompType.lbl, color = color)
}

@Composable
internal fun CompCap(text: String, modifier: Modifier = Modifier, color: Color = CompColors.faint, style: TextStyle = CompType.cap) {
    Text(text, modifier, style = style, color = color)
}

/** `.group-l`: rótulo de grupo con lo que vaya a la derecha. */
@Composable
internal fun CompGroupLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(Locale("es")), Modifier.weight(1f).semantics { heading() }, style = CompType.lbl, color = CompColors.faint)
        trailing?.let { Text(it, style = CompType.inter(13f, FontWeight.SemiBold), color = CompColors.faint) }
    }
}

/** `.mgroup`: rótulo pequeño de una sección dentro de un paso. */
@Composable
internal fun CompMGroup(text: String) {
    Text(text.uppercase(Locale("es")), Modifier.padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = CompType.lbl, color = CompColors.faint)
}

/** `.mod` / `.mod.tight`. */
@Composable
internal fun CompModule(modifier: Modifier = Modifier, tight: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape)
            .background(CompColors.module)
            .border(1.dp, CompColors.line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(if (tight) PaddingValues(horizontal = 14.dp, vertical = 12.dp) else PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)),
        content = content,
    )
}

/** `.mod-h`: eyebrow a la izquierda y lo que vaya a la derecha. */
@Composable
internal fun CompModHeader(eyebrow: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow.uppercase(Locale("es")), Modifier.weight(1f), style = CompType.ey, color = CompColors.muted)
        trailing()
    }
}

/** La píldora del lienzo (`.pill`): 52 dp (40 en `.sm`), principal de tinta o secundaria con filete. */
@Composable
internal fun CompPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp? = null,
    container: Color? = null,
    content: Color? = null,
    border: Color? = null,
    textStyle: TextStyle? = null,
) {
    val h = height ?: if (small) 40.dp else 52.dp
    val shape = RoundedCornerShape(percent = 50)
    val fill = container ?: if (!enabled) WizardColors.ctaDisabled else if (primary) CompColors.ink else Color.Transparent
    val ink = content ?: if (!enabled) WizardColors.ctaDisabledContent else if (primary) Color(0xFF0C0C0C) else CompColors.ink
    val edge = border ?: if (primary || !enabled) null else Color(0x3DF2EEE6)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .height(h)
            .clip(shape)
            .background(fill, shape)
            .then(if (edge != null) Modifier.border(1.dp, edge, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { Icon(it, null, Modifier.size(if (small) 16.dp else 18.dp), tint = ink) }
            Text(text, style = textStyle ?: if (small) CompType.pillSm else CompType.pill, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** `.act`: acción de texto (36 dp de alto, zona táctil de 48). */
@Composable
internal fun CompTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, faint: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), tint = if (faint) CompColors.muted else CompColors.ink) }
        Text(text, style = CompType.act, color = if (faint) CompColors.muted else CompColors.ink)
    }
}

/** `.chip` del lienzo (alto 34 en `chips()`): elegida de tinta. */
@Composable
internal fun CompChip(text: String, selected: Boolean, onClick: () -> Unit, height: Dp = 34.dp, leading: @Composable (() -> Unit)? = null) {
    val shape = RoundedCornerShape(percent = 50)
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.height(height).clip(shape).background(if (selected) CompColors.ink else CompColors.inset, shape).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            Text(text, style = CompType.chip, color = if (selected) Color(0xFF111111) else CompColors.muted, maxLines = 1)
        }
    }
}

/** `chips()`: fila que se parte con 6 dp entre píldoras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompChips(options: List<String>, selected: (Int) -> Boolean, onClick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label -> CompChip(label, selected(i), { onClick(i) }) }
    }
}

/** `.field` de solo lectura que se toca para editar (fecha, hora, federación). */
@Composable
internal fun CompField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, placeholder: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x3A000000), shape)
            .border(1.dp, Color(0x1AFFFFFF), shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = CompType.cap, color = CompColors.faint)
        Text(value, Modifier.padding(top = 3.dp), style = CompType.inter(15f, line = 1.3f), color = if (placeholder) CompColors.faint else CompColors.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** `.field` que se escribe en el sitio (nombre, lugar, peso con teclado numérico). */
@Composable
internal fun CompInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Escribe…",
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x3A000000), shape)
            .border(1.dp, Color(0x1AFFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = CompType.cap, color = CompColors.faint)
        Box(Modifier.padding(top = 3.dp).fillMaxWidth()) {
            if (value.isEmpty()) Text(placeholder, style = CompType.inter(15f, line = 1.3f), color = CompColors.faint)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboard,
                textStyle = CompType.inter(15f, line = 1.3f).copy(color = CompColors.ink),
                cursorBrush = SolidColor(CompColors.mus),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

/** `.rowx` con su interruptor (`.toggle`). */
@Composable
internal fun CompToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CompType.rowTitle, color = CompColors.ink)
            subtitle?.let { Text(it, Modifier.padding(top = 2.dp), style = CompType.cap, color = CompColors.faint) }
        }
        KpknSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** `.ratio`: rótulo a la izquierda y cifra a la derecha, con filete arriba. */
@Composable
internal fun CompRatioRow(label: String, first: Boolean = false, sub: String? = null, value: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (!first) Modifier.drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = CompType.inter(13f), color = CompColors.ink)
            sub?.let { Text(it, style = CompType.cap, color = CompColors.faint) }
        }
        value()
    }
}

/** Disco de medalla (`.medal`): degradado del metal con brillo y número; sin podio, aro con [icon]. */
@Composable
internal fun CompMedal(medal: CompetitionMedalHonor?, size: Dp, icon: ImageVector = CompetitionIcons.Trophy, modifier: Modifier = Modifier, iconSize: Dp? = null) {
    if (medal == null) {
        Box(modifier.size(size).clip(CircleShape).border(1.5.dp, Color(0x30FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(iconSize ?: (size * 0.5f)), tint = CompColors.muted)
        }
        return
    }
    val (c, c2) = MedalColors.of(medal)
    Box(
        modifier
            .size(size)
            .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c2)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x88FFFFFF), Color.Transparent), center = Offset(this.size.width * 0.35f, this.size.height * 0.3f), radius = this.size.minDimension * 0.42f),
                )
                drawCircle(Color(0x33000000), style = Stroke(2.dp.toPx()), radius = this.size.minDimension / 2 - 1.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(medal.place.toString(), style = CompType.syne((size.value * 0.36f).toInt().coerceAtLeast(13), line = 1f), color = Color(0xFF1B1206))
    }
}

/** Una celda de la tabla de intentos (`.ab`): cifra Syne y estado; válido en verde, nulo tachado en rojo, pendiente discontinuo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AttemptButton(
    weight: Double?,
    result: CompetitionAttemptResult,
    current: Boolean,
    pendingDashed: Boolean,
    description: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val (fill, edge) = when (result) {
        CompetitionAttemptResult.GOOD_LIFT -> Color(0x1C43D18C) to Color(0x6643D18C)
        CompetitionAttemptResult.NO_LIFT -> Color(0x14FF9B92) to Color(0x55FF9B92)
        else -> Color(0x08FFFFFF) to Color(0x12FFFFFF)
    }
    val dashed = pendingDashed && result == CompetitionAttemptResult.PENDING && !current
    Column(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(fill, shape)
            .then(
                when {
                    current -> Modifier.border(2.dp, CompColors.ink, shape)
                    dashed -> Modifier.drawBehind {
                        drawRoundRect(edge, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                    }
                    else -> Modifier.border(1.dp, edge, shape)
                },
            )
            .semantics { contentDescription = description }
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                    onLongClickLabel = if (onLongClick != null) "Cambiar el peso" else null,
                    role = Role.Button,
                ) else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            CompetitionFormat.kg(weight),
            style = CompType.syne(15, FontWeight.Bold, 1.1f).copy(
                textDecoration = if (result == CompetitionAttemptResult.NO_LIFT) TextDecoration.LineThrough else null,
            ),
            color = if (result == CompetitionAttemptResult.NO_LIFT) CompColors.muted else CompColors.ink,
        )
        Text(
            when (result) {
                CompetitionAttemptResult.GOOD_LIFT -> "Válido"
                CompetitionAttemptResult.NO_LIFT -> "Nulo"
                CompetitionAttemptResult.SKIPPED -> "Saltado"
                CompetitionAttemptResult.PENDING -> if (current) "Ahora" else "—"
            },
            style = CompType.inter(13f, line = 1.1f),
            color = when (result) {
                CompetitionAttemptResult.GOOD_LIFT -> CompColors.done
                CompetitionAttemptResult.NO_LIFT -> CompColors.danger
                else -> CompColors.faint
            },
        )
    }
}

/** `.tot-bar`: barra cálida del total con la parte izquierda y la derecha. */
@Composable
internal fun CompTotalBar(modifier: Modifier = Modifier, left: @Composable ColumnScope.() -> Unit, right: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(0f to Color(0x1CF49A6E), 0.7f to Color(0xFF111111)), shape)
            .border(1.dp, Color(0x33F49A6E), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), content = left)
        right()
    }
}

/** `.big` con su unidad (`.big small`). */
@Composable
internal fun CompBig(value: String, size: Int, unit: String? = null, modifier: Modifier = Modifier, color: Color = CompColors.ink) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = CompType.syne(size, line = 0.95f, spacing = -2f), color = color, maxLines = 1)
        unit?.let { Text(it, Modifier.padding(start = 6.dp, bottom = (size * 0.08f).dp), style = CompType.unit, color = CompColors.muted) }
    }
}

/** La cifra que cuenta hacia arriba al aparecer (`data-count`): 1,1 s con salida cúbica; con movimiento reducido, quieta. */
@Composable
internal fun CountUpValue(target: Double, decimals: Int, size: Int, unit: String? = null, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val anim = remember { Animatable(if (reduced) target.toFloat() else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) anim.snapTo(target.toFloat()) else anim.animateTo(target.toFloat(), tween(1100, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
    }
    val shown = anim.value.toDouble()
    val text = if (decimals == 0) CompetitionFormat.kg(Math.round(shown).toDouble())
    else String.format(Locale.ROOT, "%.${decimals}f", shown).replace('.', ',').let { if (it.endsWith(",0") && shown == target && target % 1.0 == 0.0) it.removeSuffix(",0") else it }
    CompBig(text, size, unit, modifier.semantics { contentDescription = "${CompetitionFormat.kg(target)} ${unit.orEmpty()}" })
}

/** `.kpis`: tres cifras en celdas tonales. */
@Composable
internal fun CompKpis(items: List<Triple<String, String, String>>) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (small, big, span) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CompColors.inset).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(small.uppercase(Locale("es")), style = CompType.inter(13f, FontWeight.SemiBold, 1.2f, 1f), color = CompColors.faint, maxLines = 1)
                Text(big, Modifier.padding(top = 6.dp), style = CompType.syne(20, line = 1.1f, spacing = -0.4f), color = CompColors.ink, maxLines = 1)
                Text(span, style = CompType.cap, color = CompColors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Una fila de menú (`.mi`): círculo de 38 con el icono, título y subtítulo; en peligro, en rojo. */
@Composable
internal fun CompMenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, danger: Boolean = false, divider: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = if (danger) CompColors.danger else CompColors.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = CompType.rowTitle, color = if (danger) CompColors.danger else CompColors.ink)
            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(top = 2.dp), style = CompType.cap, color = CompColors.faint) }
        }
    }
}

/** `.pchip`: píldora pequeña tonal («+12,5 kg», «Dentro», «Nueva MP»). */
@Composable
internal fun CompPChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, fill: Color = Color(0x1CF49A6E), ink: Color = CompColors.mus) {
    Row(
        modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(12.dp), tint = ink) }
        Text(text, style = CompType.inter(13f, FontWeight.SemiBold, 1.1f), color = ink)
    }
}

/** La tira de fotos (`.photos`): tres columnas cuadradas de radio 14 y, si se puede añadir, la celda «+» con la cámara. */
@Composable
internal fun CompPhotoGrid(count: Int, cell: @Composable BoxScope.(Int) -> Unit, onAdd: (() -> Unit)?) {
    val total = count + if (onAdd != null) 1 else 0
    val rows = (total + 2) / 3
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { c ->
                    val i = r * 3 + c
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        when {
                            i < count -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A322B), Color(0xFF16130F))))) { cell(i) }
                            i == count && onAdd != null -> Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).drawBehind {
                                    drawRoundRect(Color(0x30FFFFFF), cornerRadius = CornerRadius(14.dp.toPx()),
                                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                                }.clickable(role = Role.Button, onClickLabel = "Añadir foto o vídeo", onClick = onAdd)
                                    .semantics { contentDescription = "Añadir foto o vídeo" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(CompetitionIcons.Camera, null, Modifier.size(22.dp), tint = CompColors.faint) }
                        }
                    }
                }
            }
        }
    }
}

/** Texto centrado de «Saltar» y similares. */
@Composable
internal fun CompCenteredAction(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = CompType.act, color = CompColors.muted, textAlign = TextAlign.Center)
    }
}

/** `TextUnit` de un tamaño en sp (ayuda para los tamaños del lienzo). */
internal fun Int.spx(): TextUnit = this.sp

internal fun easeOutCubic(t: Float): Float = 1f - (1f - t).toDouble().pow(3).toFloat()

internal val CompDrawEasing = CompEasing

/** Ancho mínimo útil de la columna de rótulos de la tabla (`.att`: 72; c-lift: 96). */
internal val AttemptLabelWidth = 72.dp

internal fun Modifier.compWidth(dp: Dp): Modifier = this.width(dp)
