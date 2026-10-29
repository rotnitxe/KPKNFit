package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetSectionLabel
import com.example.kpkn.screens.workout.components.LiveSheetShortCopy
import com.example.kpkn.screens.workout.components.LiveSheetSubtitle
import com.example.kpkn.screens.workout.components.liveContentSwap
import com.example.kpkn.screens.workout.components.liveSpec
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Piezas de la vista Lista de la sesión en vivo (pedido del usuario del 29-oct): la superficie plana de cada ejercicio, las filas de
 * movilidad y de aproximación DENTRO del ejercicio, el editor en línea de la aproximación (kg y reps reales) y el descanso minimizado.
 * Todo es plano (sin vidrio): gris muy oscuro sobre el negro de la sesión, con más aire entre ejercicios que entre filas.
 */

/** Superficie de cada ejercicio: gris muy oscuro plano sobre el negro de la sesión. */
internal val ListCardFill = Color(0xFF141414)
private val ListCardRadius = 20.dp
internal val ListCardGap = 14.dp
internal val ListRowDivider = Color(0x0FFFFFFF)
private val ListPanelFill = Color(0xFF1C1C1C)
private val ListStepFill = Color(0xFF242424)
private val ListBody = TextStyle(fontFamily = WizardFonts.body)
private val ListSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/** Dónde cae un ítem de la LazyColumn dentro de la superficie de su ejercicio: arriba redondea arriba y el cierre redondea abajo. */
internal enum class ListCardSegment { TOP, MIDDLE, BOTTOM }

/** La superficie del ejercicio por trozos: cada ítem pinta su parte y el conjunto se lee como una sola tarjeta. */
internal fun Modifier.listCardSegment(segment: ListCardSegment): Modifier {
    val shape = when (segment) {
        ListCardSegment.TOP -> RoundedCornerShape(topStart = ListCardRadius, topEnd = ListCardRadius)
        ListCardSegment.MIDDLE -> RoundedCornerShape(0.dp)
        ListCardSegment.BOTTOM -> RoundedCornerShape(bottomStart = ListCardRadius, bottomEnd = ListCardRadius)
    }
    return this.clip(shape).background(ListCardFill, shape)
}

/**
 * Cada fila y cada editor se quedan con sus propios toques (bug del 29-oct: una zona vacía de una fila abría el campo de otra). Un toque
 * que no cae en un control lo absorbe la propia fila; arrastrar sigue desplazando la lista.
 */
internal fun Modifier.listTapGuard(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }

/**
 * Dentro de la Lista, la zona táctil de un control no se agranda más allá de lo que dibuja: así un toque en el hueco de una fila nunca
 * se lo lleva el campo o el botón pequeño de la fila vecina. Los controles de la Lista ya miden 36–48 dp.
 */
@Composable
internal fun LiveListStrictTouch(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val strict = remember(base) {
        object : ViewConfiguration by base {
            override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides strict, content = content)
}

/** Número con coma decimal (es-CL) para todo lo que se lee en la Lista: «12,5», «2,5». */
internal fun listNumber(value: Double): String = formatDecimal(value).replace('.', ',')

/** «40 %» a partir de 0,4 (editor nuevo) o de 40,0 (sesiones antiguas). */
internal fun listWarmupPercent(raw: Double): String {
    val pct = if (raw <= 1.0) raw * 100.0 else raw
    return "${pct.roundToInt()} %"
}

/** «40 kg × 8» con la unidad en pequeño: la carga siempre dice que son kilos. Sin carga conocida, «— kg × 8». */
internal fun listDose(kg: Double?, value: String, unit: String? = null): AnnotatedString = buildAnnotatedString {
    append(kg?.takeIf { it > 0.0 }?.let(::listNumber) ?: "—")
    withStyle(SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = WizardColors.textFaint)) {
        append(" kg")
    }
    append(" × ")
    append(value)
    if (unit != null) {
        withStyle(SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = WizardColors.textFaint)) {
            append(" $unit")
        }
    }
}

/** Rótulo de un bloque dentro del ejercicio: «MOVILIDAD», «APROXIMACIONES», «SERIES EFECTIVAS» y su avance a la derecha. */
@Composable
internal fun LiveListSubLabel(label: String, count: String, detail: String? = null, tag: String) {
    Row(
        Modifier.fillMaxWidth().listTapGuard().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = ListBody.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium), color = WizardColors.textFaint)
        if (detail != null) {
            Text(detail, Modifier.weight(1f), style = ListBody.copy(fontSize = 11.sp), color = WizardColors.textFaint, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(count, style = ListBody.copy(fontSize = 11.sp, letterSpacing = .4.sp), color = WizardColors.textMuted)
    }
}

/** La barra en tinta de la fila que toca ahora, dentro de su borde izquierdo. */
internal fun Modifier.listActiveMark(active: Boolean): Modifier = this.drawBehind {
    if (active) {
        drawRect(Color(0x08FFFFFF))
        drawLine(WizardColors.text, Offset(1.dp.toPx(), 10.dp.toPx()), Offset(1.dp.toPx(), size.height - 10.dp.toPx()), 2.dp.toPx())
    }
}

/** El círculo de hecho/pendiente de las filas de preparación: hecho, relleno en tinta con ✓; pendiente, «+» tenue. */
@Composable
private fun ListDoneMark(done: Boolean) {
    val reduced = wizardReducedMotion()
    val fill by animateColorAsState(if (done) WizardColors.text else ListStepFill, liveSpec(reduced), label = "list-done-fill")
    Box(Modifier.size(26.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        AnimatedContent(done, transitionSpec = { liveContentSwap(reduced) }, label = "list-done-icon") { shown ->
            Icon(if (shown) KpknLineIcons.Check else KpknLineIcons.Plus, null, Modifier.size(13.dp),
                tint = if (shown) WizardColors.ctaContent else WizardColors.textMuted)
        }
    }
}

/** Fila de movilidad dentro del ejercicio: número, nombre, volumen y la marca. Tocar el nombre abre su ficha. */
@Composable
internal fun LiveListMobilityRow(
    index: Int,
    mobility: MobilitySeries,
    volume: String,
    done: Boolean,
    active: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).listActiveMark(active)
            .drawBehind { drawLine(ListRowDivider, Offset(14.dp.toPx(), size.height), Offset(size.width - 14.dp.toPx(), size.height), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp)
            .testTag("live-list-mobility-${mobility.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(Locale.US, index + 1), Modifier.width(25.dp), style = ListBody.copy(fontSize = 11.sp), color = WizardColors.textFaint)
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(mobility.name, style = ListBody.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                color = if (done) WizardColors.textMuted else WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(volume, Modifier.padding(top = 2.dp), style = ListBody.copy(fontSize = 11.5.sp), color = WizardColors.textFaint, maxLines = 1)
        }
        Box(
            Modifier.size(width = 48.dp, height = 46.dp).clickable(role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = if (done) "Marcar pendiente: ${mobility.name}" else "Completar ${mobility.name}" }
                .testTag("live-list-mobility-check-${mobility.id}"),
            contentAlignment = Alignment.Center,
        ) { ListDoneMark(done) }
    }
}

/** Fila de aproximación: «A1», «40 %», «40 kg × 8» (lo registrado si ya está hecha) y la marca. Tocar la fila abre su editor. */
@Composable
internal fun LiveListWarmupRow(
    label: String,
    percent: String,
    dose: AnnotatedString,
    done: Boolean,
    active: Boolean,
    expanded: Boolean,
    tag: String,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).listActiveMark(active || expanded)
            .drawBehind {
                if (!expanded) drawLine(ListRowDivider, Offset(14.dp.toPx(), size.height), Offset(size.width - 14.dp.toPx(), size.height), 1.dp.toPx())
            }
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Aproximación $label, $percent" }
            .padding(start = 14.dp, end = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(25.dp), style = ListBody.copy(fontSize = 12.sp), color = WizardColors.textFaint)
        Text(percent, Modifier.width(46.dp), style = ListBody.copy(fontSize = 12.sp), color = WizardColors.textMuted)
        Text(dose, Modifier.weight(1f), style = ListSyne.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = if (done) WizardColors.textMuted else WizardColors.text, textAlign = TextAlign.End, maxLines = 1)
        Box(
            Modifier.size(width = 48.dp, height = 46.dp).clickable(role = Role.Checkbox, onClick = onCheck)
                .semantics { contentDescription = if (done) "Editar aproximación $label" else "Completar aproximación $label" }
                .testTag("$tag-check"),
            contentAlignment = Alignment.Center,
        ) { ListDoneMark(done) }
    }
}

/**
 * Editor en línea de una aproximación: los kg que se levantaron de verdad (por defecto, la carga sugerida ya redondeada al material) y
 * las repeticiones. Registrar guarda con la API de la tarjeta; ya hecha, «Actualizar» corrige lo guardado y «Marcar pendiente» la desmarca.
 */
@Composable
internal fun LiveListWarmupEditor(
    label: String,
    tag: String,
    suggestedKg: Double?,
    targetReps: Int,
    recorded: CompletedSet?,
    done: Boolean,
    hint: String,
    onRegister: (kg: Double?, reps: Int) -> Unit,
    onMarkPending: () -> Unit,
) {
    val startKg = recorded?.weight?.takeIf { done && it > 0.0 } ?: suggestedKg
    var kgText by rememberSaveable(tag, done) { mutableStateOf(startKg?.takeIf { it > 0.0 }?.let(::listNumber).orEmpty()) }
    var repsText by rememberSaveable(tag, done) { mutableStateOf(((recorded?.reps?.takeIf { done && it > 0 }) ?: targetReps).toString()) }
    var error by remember(tag) { mutableStateOf<String?>(null) }
    val metric = ListSyne.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, fontFeatureSettings = "'tnum' 1", color = WizardColors.text)

    fun stepKg(delta: Double) {
        val current = kgText.trim().replace(',', '.').toDoubleOrNull() ?: suggestedKg ?: 0.0
        kgText = listNumber((current + delta).coerceAtLeast(0.0))
        error = null
    }
    fun submit() {
        val reps = repsText.trim().toIntOrNull()
        if (reps == null || reps < 0) { error = "Escribe las repeticiones que hiciste."; return }
        val kg = if (kgText.isBlank()) null else kgText.trim().replace(',', '.').toDoubleOrNull()
        if (kgText.isNotBlank() && (kg == null || kg < 0.0)) { error = "Revisa la carga: escribe un número válido."; return }
        error = null
        onRegister(kg, reps)
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(ListPanelFill)
            .listTapGuard().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp).testTag("$tag-editor"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                LiveListNumberField(kgText, { kgText = it; error = null }, metric.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    Modifier.width(IntrinsicSize.Max).widthIn(min = 40.dp), "$tag-kg", "Kilos levantados en la aproximación $label",
                    KeyboardType.Decimal, placeholder = "—")
                Text("kg", Modifier.padding(start = 4.dp, top = 6.dp), style = ListBody.copy(fontSize = 13.sp), color = WizardColors.textFaint)
            }
            LiveListStep(KpknLineIcons.Minus, "Restar 2,5 kg", "$tag-kg-minus") { stepKg(-2.5) }
            LiveListStep(KpknLineIcons.Plus, "Sumar 2,5 kg", "$tag-kg-plus") { stepKg(2.5) }
            Row(Modifier.weight(.8f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                LiveListNumberField(repsText, { next -> repsText = next.filter(Char::isDigit); error = null },
                    metric.copy(fontSize = 22.sp, lineHeight = 28.sp, textAlign = TextAlign.End), Modifier.width(44.dp), "$tag-reps",
                    "Repeticiones de la aproximación $label", KeyboardType.Number)
                Text("reps", Modifier.padding(start = 3.dp, top = 5.dp), style = ListBody.copy(fontSize = 13.sp), color = WizardColors.textFaint)
            }
        }
        Text(error ?: hint, Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), style = ListBody.copy(fontSize = 12.sp),
            color = if (error != null) WizardColors.danger else WizardColors.textFaint)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (done) {
                Box(
                    Modifier.heightIn(min = 43.dp).clip(RoundedCornerShape(13.dp)).clickable(role = Role.Button, onClick = onMarkPending)
                        .padding(horizontal = 10.dp).testTag("$tag-pending"),
                    contentAlignment = Alignment.Center,
                ) { Text("Marcar pendiente", style = ListBody.copy(fontSize = 13.sp), color = WizardColors.textMuted) }
            }
            Row(
                Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(13.dp)).background(WizardColors.cta)
                    .clickable(role = Role.Button) { submit() }.testTag("$tag-register"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KpknLineIcons.Check, null, Modifier.size(15.dp), tint = WizardColors.ctaContent)
                Text((if (done) "  Actualizar " else "  Registrar ") + label, style = ListBody.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = WizardColors.ctaContent)
            }
        }
    }
}

/** Campo numérico de la Lista: su zona táctil ocupa todo su alto (48 dp) para que no quede hueco que se lleve otro control. */
@Composable
internal fun LiveListNumberField(
    value: String,
    onChange: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier,
    tag: String,
    description: String,
    keyboardType: KeyboardType,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() || it == ',' || it == '.' }) },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).testTag(tag).semantics { contentDescription = description },
        textStyle = style,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(WizardColors.text),
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = WizardColors.textFaint))
                inner()
            }
        },
    )
}

/** Botón −/+ de la Lista: 34 × 40, radio 10. */
@Composable
internal fun LiveListStep(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 34.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(ListStepFill)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(13.dp), tint = WizardColors.text) }
}

/**
 * Descanso minimizado de la Lista (`rest-mini` del prototipo): sigue a la vista como una fila compacta con el reloj, «+15 s»,
 * «Terminar» y desplegar. Sirve para el descanso de las series efectivas, de las aproximaciones y de la movilidad.
 */
@Composable
internal fun LiveListRestBar(
    kicker: String,
    remainingSeconds: Int?,
    progress: Float,
    tag: String,
    onExpand: (() -> Unit)?,
    onAddTime: (() -> Unit)?,
    onEnd: () -> Unit,
    endLabel: String = "Terminar",
    detail: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(15.dp)).background(ListPanelFill)
            .drawBehind {
                val h = 2.dp.toPx()
                drawRect(Color(0x14FFFFFF), Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
                drawRect(WizardColors.text, Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), h))
            }
            .then(if (onExpand != null) Modifier.clickable(role = Role.Button, onClick = onExpand) else Modifier.listTapGuard())
            .heightIn(min = 56.dp).padding(start = 14.dp, end = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(KpknLineIcons.Timer, null, Modifier.size(16.dp), tint = WizardColors.textMuted)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(kicker, style = ListBody.copy(fontSize = 10.sp, letterSpacing = .9.sp), color = WizardColors.textFaint, maxLines = 1,
                softWrap = false, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(remainingSeconds?.let(::listClock) ?: "Cumplido", style = ListSyne.copy(fontSize = 18.sp, lineHeight = 22.sp,
                    fontFeatureSettings = "'tnum' 1"), color = WizardColors.text, maxLines = 1, softWrap = false)
                // De qué es el descanso, junto al reloj: el rótulo de arriba queda corto para que quepan las acciones.
                if (detail != null) {
                    Text(" $detail", Modifier.padding(bottom = 2.dp), style = ListBody.copy(fontSize = 11.sp), color = WizardColors.textFaint,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (onAddTime != null) ListBarAction("+15 s", "$tag-plus", onAddTime)
        ListBarAction(endLabel, "$tag-end", onEnd)
        if (onExpand != null) {
            Box(Modifier.size(width = 40.dp, height = 48.dp).clickable(role = Role.Button, onClick = onExpand)
                .semantics { contentDescription = "Desplegar descanso" }.testTag("$tag-expand"), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.ChevronDown, null, Modifier.size(16.dp), tint = WizardColors.textMuted)
            }
        }
    }
}

@Composable
private fun ListBarAction(text: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(11.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ListBody.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = WizardColors.text, maxLines = 1) }
}

internal fun listClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, s / 60, s % 60)
}

/** Ficha de un movimiento de movilidad desde su fila: volumen, notas, articulaciones y marcar hecho o pendiente. */
@Composable
internal fun LiveListMovementSheet(mobility: MobilitySeries, volume: String, done: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    LiveSheet(title = mobility.name, onDismiss = onDismiss, tag = "live-list-movement-sheet", footer = { close ->
        LiveSheetPrimary(if (done) "Marcar pendiente" else "Marcar completado", { onToggle(); close() })
    }) {
        LiveSheetSubtitle("Explora el movimiento a tu ritmo antes de añadir carga.")
        Text(volume, style = ListBody.copy(fontSize = 14.sp), color = WizardColors.text)
        mobility.notes?.takeIf { it.isNotBlank() }?.let { LiveSheetShortCopy(it) }
        if (mobility.bodyZones.isNotEmpty()) {
            LiveSheetSectionLabel("Articulaciones")
            Text(mobility.bodyZones.joinToString(" · ") { com.example.kpkn.data.models.mobilityBodyRegionLabel(it.trim()) },
                style = ListBody.copy(fontSize = 14.sp), color = WizardColors.textMuted)
        }
    }
}

/** Contenido que crece o encoge suave (registros, editores): el alto se interpola salvo con el movimiento reducido. */
@Composable
internal fun Modifier.listAnimatedSize(): Modifier {
    val reduced = wizardReducedMotion()
    return if (reduced) this else this.animateContentSize(liveSpec(false, 260))
}
