package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.domain.nutrition.supplements.SUPPLEMENT_MOMENTS
import com.example.kpkn.domain.nutrition.supplements.SupplementCatalog
import com.example.kpkn.domain.nutrition.supplements.SupplementDef
import com.example.kpkn.domain.nutrition.supplements.SupplementEngine
import com.example.kpkn.domain.nutrition.supplements.SupplementTake
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknCountUp
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Tinta, grises y verdes del lienzo de Suplementos (`--ink`, `--muted`, `--faint`, `--done`, `--danger`). */
internal object SpColors {
    val ink = WizardColors.text
    val muted = WizardColors.textMuted
    val faint = WizardColors.textFaint
    val dim = Color(0xFF6E6A63)
    val done = WizardColors.done
    val danger = WizardColors.danger
    val onInk = Color(0xFF111111)
    val track = Color(0x14FFFFFF)
}

/** `@keyframes sppop` (sp-A02): el círculo del suplemento rebota al registrar. 0,6 s, `cubic-bezier(.3,1.6,.5,1)`, sin relleno. */
internal val SpPop = KpknAnimation(
    keyframes = kpknKeyframes("sppop", "@keyframes sppop { 0% { transform: scale(0.7); } 50% { transform: scale(1.18); } 100% { transform: scale(1); } }") {
        at(0f) { scale(0.7f) }
        at(0.5f) { scale(1.18f) }
        at(1f) { scale(1f) }
    },
    duration = KpknMotion.D600,
    easing = KpknMotion.ResorteFuerte,
    fill = KpknFill.None,
)

/** El icono del catálogo: el más cercano de `KpknLineIcons` (los dibujos propios del lienzo no existen en el set; ver DESV-1300). */
internal fun supplementIcon(def: SupplementDef?): ImageVector = when (def?.iconKey) {
    "cube" -> KpknLineIcons.Layers
    "bolt" -> KpknLineIcons.Flame
    "shake" -> KpknLineIcons.Apple
    "caps" -> KpknLineIcons.Tag
    "citrus" -> KpknLineIcons.Target
    "lyte" -> KpknLineIcons.Pulse
    "sun" -> KpknLineIcons.Sun
    "fish" -> KpknLineIcons.Star
    "atom" -> KpknLineIcons.Moon
    "hex" -> KpknLineIcons.Joint
    "multi" -> KpknLineIcons.Layers
    else -> KpknLineIcons.Tag
}

/** Una fila de la pila ya resuelta: su definición del catálogo, su configuración y las tomas de hoy. */
data class SupplementRowState(val def: SupplementDef, val item: SupplementPilaItem, val takes: List<SupplementTake>) {
    val done: Boolean get() = SupplementEngine.isDone(item, takes)
}

/**
 * «Suplementos» (Pulido v1 · SUPLEMENTOS, escenas sp-modulo / sp-vacio / sp-hora / sp-completo / sp-techo): dos totales finos (cafeína frente
 * a su máximo con la franja útil, creatina con su racha), la pila ordenada por momento con un botón que lleva la dosis visible (un toque
 * registra; mantener 0,5 s ajusta), la semana de la pila y «Añadir suplemento». Sin pila muestra el estado vacío con sugerencias.
 * Sin [loaded] es un esqueleto. Es un composable sin estado: todo llega por parámetros. [week] trae 7 fracciones (6 días y hoy).
 */
@Composable
fun NutritionSupplementsModule(
    loaded: Boolean,
    rows: List<SupplementRowState>,
    caffeineTotalMg: Double,
    foodCaffeineMg: Double,
    creatineStreakDays: Int,
    week: List<Float>,
    today: LocalDate,
    nowId: String?,
    showHint: Boolean,
    suggestionIds: List<String>,
    onTake: (String) -> Unit,
    onHold: (String) -> Unit,
    onAdd: () -> Unit,
    onToggleSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    openCaffeine: () -> Unit = {},
    openCreatine: () -> Unit = {},
) {
    if (!loaded) {
        KpknSkeletonModule(lines = 2, modifier = modifier)
        return
    }
    val total = rows.size
    val done = rows.count { it.done }
    val all = total > 0 && done == total
    val hasCaffeine = rows.any { it.def.id == "cafeina" }
    val creatine = rows.firstOrNull { it.def.id == "creatina" }
    KpknModule(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 10.dp),
    ) {
        SpModuleHeader(total = total, done = done, all = all, onAdd = onAdd)
        if (hasCaffeine || foodCaffeineMg > 0.0) {
            SpCaffeineTotal(totalMg = caffeineTotalMg, foodMg = foodCaffeineMg, onClick = openCaffeine)
        }
        if (total == 0) {
            SpSeparator()
            SpEmpty(suggestionIds = suggestionIds, onToggle = onToggleSuggestion, onCta = onAdd)
        } else {
            if (creatine != null) SpCreatineTotal(creatine = creatine, streakDays = creatineStreakDays, onClick = openCreatine)
            SpSeparator()
            rows.forEachIndexed { index, row ->
                SpRow(
                    row = row,
                    first = index == 0,
                    now = nowId == row.def.id && !row.done,
                    onTake = { onTake(row.def.id) },
                    onHold = { onHold(row.def.id) },
                )
            }
            if (showHint && !all) SpHint()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ÚLTIMOS 7 DÍAS", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
                Text("tu pila por día", style = KpknType.caption.copy(fontSize = 12.sp), color = SpColors.faint)
            }
            SpWeekStrip(week = week, today = today)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            SpLink(text = "Añadir suplemento", icon = KpknLineIcons.Plus, onClick = onAdd)
        }
    }
}

@Composable
private fun SpModuleHeader(total: Int, done: Int, all: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SUPLEMENTOS", style = KpknType.eyebrow, color = WizardColors.textMuted, maxLines = 1)
            if (total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (all) Icon(KpknLineIcons.Check, null, tint = SpColors.done, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (all) "Todo tomado" else "$done de $total tomados",
                        style = KpknType.caption.copy(fontSize = 12.sp),
                        color = if (all) SpColors.done else SpColors.faint,
                    )
                }
            }
        }
        // sp-T01 .sp-addb: el fondo cambia en 0,16 s `ease` al pulsar. Área táctil de 48 dp; el círculo visible es de 44.
        var pressed by remember { mutableStateOf(false) }
        val fill by animateKpknColorAsState(if (pressed) Color(0x1FFFFFFF) else Color(0x0FFFFFFF), KpknMotion.D160)
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "Añadir suplemento"
                    role = Role.Button
                    onClick { onAdd(); true }
                }
                .pointerInput(onAdd) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null) onAdd()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(fill).border(1.dp, Color(0x1AFFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Plus, null, tint = SpColors.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** `.sp-bar` (sp-T02): 4 dp, la anchura crece en 0,52 s `cubic-bezier(.2,.8,.2,1)`; [band] es la franja útil (clara, 2 dp más alta por cada lado). */
@Composable
internal fun SpBar(value: Double, max: Double, modifier: Modifier = Modifier, band: Pair<Double, Double>? = null, over: Boolean = false) {
    val target = if (max > 0) (value / max).toFloat().coerceIn(0f, 1f) else 0f
    val fraction by animateKpknFloatAsState(target, KpknMotion.D520, KpknMotion.Estandar, label = "spBar")
    BoxWithConstraints(modifier.fillMaxWidth().height(4.dp)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(SpColors.track))
        if (band != null && max > 0) {
            Box(
                Modifier
                    .offset(x = maxWidth * (band.first / max).toFloat(), y = (-2).dp)
                    .width(maxWidth * ((band.second - band.first) / max).toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SpColors.track),
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(maxWidth * fraction)
                .clip(RoundedCornerShape(2.dp))
                .background(if (over) SpColors.danger else SpColors.ink),
        )
    }
}

@Composable
private fun SpTotalLine(icon: ImageVector, name: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = SpColors.muted, modifier = Modifier.size(15.dp))
            Text(name, style = KpknType.caption.copy(fontSize = 13.sp), color = SpColors.muted)
        }
        content()
    }
}

@Composable
private fun SpCaffeineTotal(totalMg: Double, foodMg: Double, onClick: () -> Unit) {
    val over = totalMg > 400.0
    val supplementMg = (totalMg - foodMg).coerceAtLeast(0.0)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).clickable(onClickLabel = "Ver cafeína del día", role = Role.Button, onClick = onClick),
    ) {
        SpTotalLine(KpknLineIcons.Flame, "Cafeína") {
            Row(verticalAlignment = Alignment.Bottom) {
                KpknCountUp(target = totalMg, style = KpknType.bodyStrong.copy(fontSize = 15.sp, color = if (over) SpColors.danger else SpColors.ink))
                Text(" de 400 mg", style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = SpColors.faint)
            }
        }
        SpBar(totalMg, 400.0, Modifier.padding(top = 9.dp), band = SupplementEngine.CAFFEINE_BAND_MIN_MG to SupplementEngine.CAFFEINE_BAND_MAX_MG, over = over)
        val sub = when {
            over -> "Pasas el máximo diario en ${SupplementEngine.formatNumberEs(totalMg - 400.0)} mg. Es una referencia, no una orden."
            supplementMg > 0.0 -> "Tus comidas ${SupplementEngine.formatNumberEs(foodMg)} mg · suplementos ${SupplementEngine.formatNumberEs(supplementMg)} mg"
            else -> "De tus comidas y bebidas · la franja clara es la útil (160–250 mg)"
        }
        Text(sub, style = KpknType.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = if (over) SpColors.danger else SpColors.faint, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun SpCreatineTotal(creatine: SupplementRowState, streakDays: Int, onClick: () -> Unit) {
    val goal = (creatine.item.dose * creatine.item.doses).coerceAtLeast(1.0)
    val taken = SupplementEngine.dayTotal(creatine.takes)
    val streak = when (streakDays) {
        0 -> "sin racha"
        1 -> "1 día seguido"
        else -> "$streakDays días seguidos"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).clickable(onClickLabel = "Ver seguimiento de creatina", role = Role.Button, onClick = onClick),
    ) {
        SpTotalLine(KpknLineIcons.Layers, "Creatina") {
            Row(verticalAlignment = Alignment.Bottom) {
                KpknCountUp(target = taken, decimals = if (taken % 1.0 == 0.0) 0 else 1, style = KpknType.bodyStrong.copy(fontSize = 15.sp, color = SpColors.ink))
                Text(" de ${SupplementEngine.formatNumberEs(goal)} g", style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = SpColors.faint)
            }
        }
        SpBar(taken, goal, Modifier.padding(top = 9.dp))
        Text(
            text = if (taken >= goal) "Dosis de hoy hecha · $streak" else "$streak · hoy suma la dosis",
            style = KpknType.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
            color = SpColors.faint,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun SpSeparator() {
    Box(Modifier.padding(top = 14.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

@Composable
private fun SpRow(row: SupplementRowState, first: Boolean, now: Boolean, onTake: () -> Unit, onHold: () -> Unit) {
    val def = row.def
    val done = row.done
    val n = row.takes.size
    val last = row.takes.lastOrNull()
    val sub = when {
        done -> SupplementEngine.doseLabel(def, SupplementEngine.dayTotal(row.takes)) + if (row.item.doses > 1) " · $n tomas" else ""
        now -> "Toca ahora"
        n > 0 -> "$n de ${row.item.doses} tomas · última ${last?.time.orEmpty()}"
        else -> SUPPLEMENT_MOMENTS[row.item.moment.coerceIn(0, 6)]
    }
    val baseline = remember { n }
    // sp-A02 .sp-row.pop .sp-ic: el círculo rebota cuando entra una toma nueva.
    val popModifier = Modifier.kpknAnimate(SpPop, enabled = n > baseline, restartKey = n)
    // sp-T05 .sp-ic: fondo y color en 0,2 s `ease`.
    val circleFill by animateKpknColorAsState(
        when {
            done -> Color(0x1F43D18C)
            now -> Color(0x1AF2EEE6)
            else -> Color(0x0DFFFFFF)
        },
        KpknMotion.D200,
    )
    val circleTint by animateKpknColorAsState(if (done) SpColors.done else if (now) SpColors.ink else SpColors.muted, KpknMotion.D200)
    Column(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = popModifier.size(40.dp).clip(CircleShape).background(circleFill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (done) KpknLineIcons.Check else supplementIcon(def), null, tint = circleTint, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClickLabel = "Detalle ${def.shortName}", role = Role.Button, onClick = onOpenDetail),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = def.shortName,
                        style = KpknType.body.copy(fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = if (done) FontWeight.Medium else FontWeight.SemiBold),
                        color = if (done) SpColors.muted else SpColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val reminder = row.item.reminder
                    if (reminder != null && !done) {
                        Icon(KpknLineIcons.Bell, null, tint = SpColors.faint, modifier = Modifier.size(12.dp).semantics { contentDescription = "Recordatorio $reminder" })
                    }
                }
                Text(sub, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = if (now) SpColors.ink else SpColors.faint, modifier = Modifier.padding(top = 2.dp))
            }
            if (done) {
                Text(
                    text = last?.time.orEmpty(),
                    style = KpknType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = SpColors.faint,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
            } else {
                SpTakeButton(def = def, dose = row.item.dose, now = now, onTake = onTake, onHold = onHold)
            }
        }
    }
}

/**
 * `.sp-take` (sp-T11, sp-X02, sp-X03, sp-J03): el botón con la dosis visible. Un toque registra; mantenerlo 480 ms abre el ajuste (J03,
 * un `setTimeout` de 480 ms, no el umbral del sistema). Al pulsar o mantener: escala 0,96 y fondo `#ffffff26` en 0,16 s `ease`.
 */
@Composable
internal fun SpTakeButton(def: SupplementDef, dose: Double, now: Boolean, onTake: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    val scale by animateKpknFloatAsState(if (held) 0.96f else 1f, KpknMotion.D160, KpknMotion.Suave)
    val rest = if (now) SpColors.ink else Color(0x0FFFFFFF)
    val fill by animateKpknColorAsState(if (held && !now) Color(0x26FFFFFF) else rest, KpknMotion.D160)
    val label = "Registrar ${SupplementEngine.doseLabel(def, dose)} de ${def.shortName}"
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick { onTake(); true }
            }
            .pointerInput(onTake, onHold) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    held = true
                    var timedOut = true
                    val up = withTimeoutOrNull(480L) {
                        val r = waitForUpOrCancellation()
                        timedOut = false
                        r
                    }
                    when {
                        timedOut -> {
                            onHold()
                            waitForUpOrCancellation()
                        }
                        up != null -> onTake()
                    }
                    held = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .height(44.dp)
                .widthIn(min = 84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(fill)
                .then(if (now) Modifier else Modifier.border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(22.dp)))
                .padding(start = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KpknLineIcons.Plus, null, tint = if (now) SpColors.onInk else SpColors.ink, modifier = Modifier.size(16.dp))
            Text(
                SupplementEngine.doseLabel(def, dose),
                style = KpknType.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                color = if (now) SpColors.onInk else SpColors.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SpHint() {
    Column(Modifier.padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(Modifier.padding(top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KpknLineIcons.Info, null, tint = SpColors.faint, modifier = Modifier.size(14.dp))
            Text("Mantén pulsado un botón para ajustar la dosis o la hora.", style = KpknType.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = SpColors.faint)
        }
    }
}

/** La semana de la pila: 6 días anteriores + hoy (7 fracciones); hoy lleva `anillo-foco` (sp-S04). */
@Composable
private fun SpWeekStrip(week: List<Float>, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, day ->
            val isToday = i == 6
            val v = week.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .widthIn(max = 24.dp)
                        .fillMaxWidth()
                        .height(30.dp)
                        .then(if (isToday) Modifier.kpknShadow(KpknShadow.AnilloFoco, shape) else Modifier)
                        .clip(shape)
                        .background(Color(0x10FFFFFF))
                        .drawBehind {
                            val h = size.height * v
                            drawRoundRect(SpColors.ink.copy(alpha = 0.7f), Offset(0f, size.height - h), Size(size.width, h), CornerRadius(8.dp.toPx()))
                        },
                )
                val letter = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es")).uppercase(Locale("es"))
                Text(letter, style = KpknType.caption.copy(fontSize = 10.5.sp), color = if (isToday) SpColors.ink else SpColors.faint)
            }
        }
    }
}

@Composable
internal fun SpLink(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = SpColors.ink, modifier = Modifier.size(16.dp))
        Text(text, style = KpknType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = SpColors.ink)
    }
}

/** Estado vacío (sp-vacio): título, una frase, cinco sugerencias en píldoras (sp-T08: fondo y color en 0,2 s) y «Armar mi pila». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpEmpty(suggestionIds: List<String>, onToggle: (String) -> Unit, onCta: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        Text("Tu pila, en un toque", style = KpknType.title.copy(fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp), color = SpColors.ink)
        Text(
            "Elige lo que tomas cada día y regístralo con un toque desde aquí o desde Inicio. Puedes cambiar la lista cuando quieras.",
            style = KpknType.caption.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
            color = SpColors.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            suggestionIds.mapNotNull { SupplementCatalog.byId(it) }.forEach { def ->
                val fill by animateKpknColorAsState(Color(0x08FFFFFF), KpknMotion.D200)
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = { onToggle(def.id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(fill)
                            .border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(22.dp))
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(supplementIcon(def), null, tint = SpColors.ink.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                        Text(def.shortName, style = KpknType.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium), color = SpColors.ink)
                    }
                }
            }
        }
        KpknPrimaryPill(text = "Armar mi pila", onClick = onCta, icon = KpknLineIcons.Plus, modifier = Modifier.fillMaxWidth())
    }
}
