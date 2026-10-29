package com.example.kpkn.screens.nutrition.design.logger

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

/*
 * Piezas comunes del lienzo «Registros v4» (registro de comida y búsqueda): colores, tipos y bloques de hoja que el lienzo reutiliza
 * de Program Detail (`.sheet h3`, `.sub`, `.mi`, `.mgroup`, `.pill`, `.chip`, `.stepper`, `.field`, `.toast`). Las líneas de 11-12 px
 * del lienzo suben a 13 sp: es el mínimo de la app (KpknType).
 */

/** Colores del lienzo (`:root` de Program Detail). */
internal object LoggerColors {
    val ink = WizardColors.text
    val muted = WizardColors.textMuted
    val faint = WizardColors.textFaint
    /** `--dim`: marcadores de texto y estrellas sin marcar. */
    val dim = Color(0xFF5F5C57)
    /** `--mus`: proteína y acento. */
    val mus = Color(0xFFF49A6E)
    /** `--ene`: carbohidratos, dudas y favoritos. */
    val ene = Color(0xFFF7CF73)
    /** `--col`: grasas. */
    val col = Color(0xFF8FB2FF)
    val done = WizardColors.done
    val danger = WizardColors.danger
    /** Relleno de las tarjetas del registro (`#0f0f0f`). */
    val card = Color(0xFF0F0F0F)
    /** Filete de las tarjetas (`#ffffff12`). */
    val cardBorder = Color.White.copy(alpha = 0.07f)
    /** Tinta oscura sobre un fondo de tinta (`#111`). */
    val onInk = Color(0xFF111111)
    /** Arreglos v1 · superficies planas (`--np-s1`, `--np-s2`, `--np-s3`) y la hoja (`#141414`), sin degradado ni vidrio. */
    val surface1 = Color(0xFF121212)
    val surface2 = Color(0xFF1C1C1C)
    val surface3 = Color(0xFF2A2A2A)
    val sheet = Color(0xFF141414)
    /** `--np-hair`: filetes entre bloques. */
    val hair = Color.White.copy(alpha = 0.06f)
    /** `--np-track`: pista de las barras finas. */
    val track = Color.White.copy(alpha = 0.08f)
    /** Macros en tres tonos de tinta (`--np-p`, `--np-c`, `--np-f`). */
    val macroP = Color(0xFFF2EEE6)
    val macroC = Color(0xFFA29D94)
    val macroF = Color(0xFF5F5C57)
    /** `--np-acc`: el único acento, solo para una duda que espera respuesta. */
    val accent = Color(0xFFF49A6E)
}

/** Tipos del lienzo, con el mínimo de 13 sp de la app. */
internal object LoggerText {
    /** `.sheet h3`: Syne Bold 21 con −0,4. */
    val sheetTitle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.4).sp,
    )
    /** `.sheet .sub`: Inter 13, gris. */
    val sub = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    /** `.cap`: Inter 13 (12 en el lienzo). */
    val cap = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)
    /** `.mi strong`, `.sres strong`, `.rowx strong`: Inter SemiBold 14,5. */
    val rowTitle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 20.sp)
    /** `.mgroup` y `.group-l`: Inter SemiBold 13 en mayúsculas con 1,3 de espaciado. */
    val group = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.3.sp,
    )
    /** `.pill`: Inter SemiBold 15. */
    val pill = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    /** `.pill.sm`: Inter SemiBold 13. */
    val pillSmall = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    /** `.chip`: Inter Medium 13 (12 en el lienzo). */
    val chip = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
    /** `.act`: Inter SemiBold 13. */
    val act = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    /** `.body`: Inter 14, gris. */
    val body = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    /** `.ey`: Inter SemiBold 13 (11 en el lienzo) en mayúsculas con 1,2. */
    val eyebrow = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.2.sp,
    )
    /** Cifras en Syne con números tabulares. */
    fun figure(size: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size + 4).sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )
    /** Arreglos v1 · toda cifra fuera del total: Inter SemiBold con dígitos tabulares. */
    fun number(size: Float, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )
    /** `.np-k b`: el total de la comida, el único número en Syne (Bold 30, −0,6). */
    val total = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "'lnum' 1, 'tnum' 1",
    )
}

/** `.sheet h3` + `.sub`: título de una hoja y su frase. */
@Composable
internal fun LoggerSheetTitle(title: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = LoggerText.sheetTitle,
            color = LoggerColors.ink,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { heading() },
        )
        sub?.let { Text(text = it, style = LoggerText.sub, color = LoggerColors.muted, modifier = Modifier.padding(bottom = 12.dp)) }
    }
}

/** `.mgroup`: etiqueta de grupo dentro de una hoja. */
@Composable
internal fun LoggerSheetGroup(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(Locale("es")),
        style = LoggerText.group,
        color = LoggerColors.faint,
        modifier = modifier.padding(start = 2.dp, end = 2.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** `.group-l`: etiqueta de grupo de la página (Repetir, Favoritos, Recientes, Atajos), con su acción opcional a la derecha. */
@Composable
internal fun LoggerPageGroup(label: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label.uppercase(Locale("es")), style = LoggerText.group, color = LoggerColors.faint, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** `.act`: acción de texto del lienzo (36 dp de alto visible, 48 dp de zona táctil). */
@Composable
internal fun LoggerAct(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LoggerColors.ink,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = color) }
        Text(text = text, style = LoggerText.act, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = color) }
    }
}

/** Tono de una `.pill` del lienzo. */
internal enum class LoggerPillTone {
    /** `.pill.pri`: tinta con texto negro. */
    PRIMARY,
    /** `.pill.sec`: solo filete de tinta al 24 %. */
    SECONDARY,
    /** «Olvidar» y «Descartar»: rojo al 15 % con texto rojo. */
    DANGER_SOFT,
}

/** `.pill` del lienzo: 52 dp (40 dp con [small]), redondeo completo, icono opcional delante. */
@Composable
internal fun LoggerPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LoggerPillTone = LoggerPillTone.PRIMARY,
    small: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when (tone) {
        LoggerPillTone.PRIMARY -> if (enabled) LoggerColors.ink else Color.White.copy(alpha = 0.10f)
        LoggerPillTone.SECONDARY -> Color.Transparent
        LoggerPillTone.DANGER_SOFT -> LoggerColors.danger.copy(alpha = 0.15f)
    }
    val ink = contentColor ?: when (tone) {
        LoggerPillTone.PRIMARY -> if (enabled) Color(0xFF0C0C0C) else LoggerColors.faint
        LoggerPillTone.SECONDARY -> LoggerColors.ink
        LoggerPillTone.DANGER_SOFT -> LoggerColors.danger
    }
    val height = if (small) 40.dp else 52.dp
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                val s = if (pressed && enabled) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .then(
                    if (tone == LoggerPillTone.SECONDARY) Modifier.border(1.dp, LoggerColors.ink.copy(alpha = 0.24f), KpknShape.pill)
                    else Modifier,
                )
                .padding(horizontal = if (small) 16.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
                if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = if (small) LoggerText.pillSmall else LoggerText.pill,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** `.chip` del lienzo: relleno `inset` y texto gris; elegida, tinta con texto negro. Zona táctil de 48 dp. */
@Composable
internal fun LoggerChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    enabled: Boolean = true,
) {
    val fill = if (selected) LoggerColors.ink else KpknSurface.inset
    val ink = if (selected) LoggerColors.onInk else LoggerColors.muted
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Checkbox, enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = text + if (selected) ", elegido" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = LoggerText.chip, color = ink, maxLines = 1)
        }
    }
}

/** Una fila `.mi` de una hoja: icono en círculo tonal de 38 dp, título y subtítulo. [danger] la pinta en rojo. */
@Composable
internal fun LoggerMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    danger: Boolean = false,
    divider: Boolean = true,
) {
    val tint = if (danger) LoggerColors.danger else LoggerColors.muted
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.047f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = tint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = LoggerText.rowTitle, color = if (danger) LoggerColors.danger else LoggerColors.ink)
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(text = it, style = LoggerText.cap, color = LoggerColors.faint)
                }
            }
        }
    }
}

/**
 * `.stepper` del lienzo: −, la cifra en Syne 30 con su unidad en gris y +. Con [onValueClick], tocar la cifra la deja escribir (lo resuelve
 * quien llama).
 */
@Composable
internal fun LoggerStepper(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    onValueClick: (() -> Unit)? = null,
    spokenName: String = unit,
    valueContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.23f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoggerStepButton(symbol = "−", description = "Menos $spokenName", enabled = minusEnabled, onClick = onMinus)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(if (onValueClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Escribir la cantidad", onClick = onValueClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (valueContent != null) valueContent() else Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = LoggerText.figure(30, FontWeight.ExtraBold), color = LoggerColors.ink)
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
                        color = LoggerColors.muted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        LoggerStepButton(symbol = "+", description = "Más $spokenName", enabled = true, onClick = onPlus)
    }
}

@Composable
private fun LoggerStepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.063f), CircleShape)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 22.sp, lineHeight = 24.sp), color = LoggerColors.ink)
    }
}

/** `.field` del lienzo: caja oscura con la etiqueta pequeña encima del valor. El contenido lo pone quien llama. */
@Composable
internal fun LoggerFieldBox(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.23f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = label, style = LoggerText.cap, color = LoggerColors.faint)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

/** `.ib` del lienzo: botón redondo de 44 dp; con [fill], relleno `inset`. */
@Composable
internal fun LoggerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    tint: Color = LoggerColors.muted,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(maxOf(size, 48.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (fill) KpknSurface.inset else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
        }
    }
}

/**
 * `.toast` del lienzo: píldora de vidrio de 54 dp con el check, el mensaje y una acción a la derecha en color acento («Deshacer»,
 * «Reintentar»). Sin acción, solo el mensaje.
 */
@Composable
internal fun LoggerToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = KpknLineIcons.Check,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val haze = LocalHazeState.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .kpknGlassOrFallback(haze, RoundedCornerShape(27.dp), variant = KpknGlassVariant.ISLAND)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = LoggerColors.ink) }
        Text(
            text = message,
            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.5.sp, lineHeight = 18.sp),
            color = LoggerColors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            LoggerAct(text = actionLabel, onClick = onAction, color = LoggerColors.mus, modifier = Modifier.padding(horizontal = 14.dp))
        }
    }
}

/** Filete de 1 dp entre filas (`border-top: var(--div)`). */
@Composable
internal fun LoggerRowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
