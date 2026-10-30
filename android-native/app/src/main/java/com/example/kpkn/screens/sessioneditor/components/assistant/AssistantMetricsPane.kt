package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknRing

/** Un aporte de un ejercicio a un músculo: «4 × 0,5 = 2». */
internal data class AsContribution(val exercise: String, val sets: Int, val weight: Double) {
    val total: Double get() = sets * weight
}

/** Un músculo de Métricas: su cifra, intensidad media, el aporte de cada ejercicio y lo que lleva la semana. */
internal data class AsMuscle(
    val name: String,
    val value: Double,
    val intensity: String?,
    val contributions: List<AsContribution>,
    val weekSets: Double?,
)

/** Un tramo de la barra del tiempo de sesión. */
internal data class AsTimeSegment(val label: String, val minutes: Int, val color: Color)

internal data class AsCardioCard(val name: String, val position: String, val minutes: Int)

/** Todo lo que pinta la pestaña Métricas. */
internal data class AsMetricsModel(
    val exercises: Int,
    val sets: Int,
    val minutes: Int,
    val cardioCount: Int,
    val cardios: List<AsCardioCard>,
    val muscles: List<AsMuscle>,
    val unworked: List<String>,
    /** Límite de series por músculo en la sesión; null = sin límite. */
    val sessionLimit: Double?,
    /** Límite de series por músculo en la semana; null = sin límite. */
    val weeklyLimit: Double?,
    /** Límite de tiempo de la sesión en minutos; null = sin límite. */
    val timeLimit: Int?,
    val time: List<AsTimeSegment>,
) {
    val totalWorked: Int get() = muscles.size
    val isEmpty: Boolean get() = exercises == 0
    val allMuscleCount: Int get() = muscles.size + unworked.size
}

private fun fm(value: Double) = AssistantSuggestionPreview.number(value)

/**
 * Pestaña Métricas (`as-met-media`, `as-met-completa`, `as-met-todos`, `as-met-cardio`, `as-met-vacia`): las tres cifras en un módulo, la
 * tarjeta de cardio si la hay, el volumen por músculo con su detalle al tocar y el reparto del tiempo con tu límite.
 */
@Composable
internal fun AssistantMetricsPane(
    model: AsMetricsModel,
    onGoTemplates: () -> Unit,
    onGoTimeRules: () -> Unit,
    modifier: Modifier = Modifier,
    initialOpenMuscle: String? = null,
    initialShowAll: Boolean = false,
) {
    var showAll by rememberSaveable { mutableStateOf(initialShowAll) }
    var openMuscle by rememberSaveable { mutableStateOf(initialOpenMuscle) }
    AsScrollPane(modifier) {
        if (model.isEmpty) {
            MetricsEmpty(onGoTemplates)
            return@AsScrollPane
        }
        MetricsFigures(model)
        if (model.cardios.isNotEmpty()) CardioCard(model.cardios)
        MuscleVolumeModule(
            model = model,
            showAll = showAll,
            onShowAll = { showAll = it },
            openMuscle = openMuscle,
            onToggle = { name -> openMuscle = if (openMuscle == name) null else name },
        )
        TimeModule(model, onGoTimeRules)
    }
}

@Composable
private fun MetricsEmpty(onGoTemplates: () -> Unit) {
    AsFigures(
        listOf(
            AsFigureCell("0", null, "Ejercicios", null, null),
            AsFigureCell("0", null, "Series", null, null),
            AsFigureCell("—", null, "Duración", null, null),
        ),
    )
    AsModule {
        AsModuleHeader("Volumen por músculo")
        AsEmpty(
            icon = AsIcons.Train,
            title = "Aún no hay nada que medir",
            body = "Con ejercicios en la sesión verás aquí las series de cada músculo y el reparto del tiempo.",
            iconSize = 48.dp,
            topPadding = 10.dp,
            action = {
                AsButton("Empezar con una plantilla", Modifier.padding(top = 6.dp), small = true, leading = AsIcons.Layers, onClick = onGoTemplates)
            },
        )
    }
}

internal data class AsFigureCell(val value: String, val unit: String?, val label: String, val note: String?, val noteColor: Color?)

/** `.as-fig`: tres cifras en un módulo con separadores (1 : 1 : 1,25). */
@Composable
private fun MetricsFigures(model: AsMetricsModel) {
    val over = model.timeLimit?.let { model.minutes - it }
    val timeNote: Pair<String, Color>? = when {
        over == null -> null
        over > 0 -> "+$over sobre límite" to AsColors.energy
        else -> "dentro del límite" to AsColors.done
    }
    AsFigures(
        listOf(
            AsFigureCell("${model.exercises}", null, "Ejercicios", if (model.cardioCount > 0) "${model.cardioCount} de cardio" else null, AsColors.faint),
            AsFigureCell("${model.sets}", null, "Series", if (model.cardioCount > 0) "solo fuerza" else null, AsColors.faint),
            AsFigureCell("${model.minutes}", "min", "Duración", timeNote?.first, timeNote?.second),
        ),
    )
}

@Composable
private fun AsFigures(cells: List<AsFigureCell>) {
    AsFrRow(
        weights = cells.indices.map { if (it == 2) 1.25f else 1f },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 1.dp, vertical = 15.dp),
    ) {
        cells.forEachIndexed { index, cell ->
            Column(
                Modifier
                    .then(
                        if (index == 0) Modifier else Modifier.drawBehind {
                            drawLine(AsColors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                        },
                    )
                    .padding(horizontal = 16.dp),
            ) {
                Row {
                    AsFigure(cell.value, 32f, Modifier.alignByBaseline(), cap = 1.1f, letterSpacing = -0.8f)
                    if (cell.unit != null) {
                        AsText(cell.unit, 12f, Modifier.padding(start = 3.dp).alignByBaseline(), color = AsColors.muted, weight = FontWeight.SemiBold, cap = 1.15f, noWrap = true)
                    }
                }
                AsText(cell.label, 10.5f, Modifier.padding(top = 6.dp), color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.12f, letterSpacing = 1f, noWrap = true, uppercase = true)
                if (cell.note != null) {
                    AsText(cell.note, 11.5f, Modifier.padding(top = 3.dp), color = cell.noteColor ?: AsColors.faint, cap = 1.12f, noWrap = true)
                }
            }
        }
    }
}

@Composable
private fun CardioCard(cards: List<AsCardioCard>) {
    AsModule {
        cards.forEachIndexed { index, card ->
            Row(Modifier.padding(top = if (index == 0) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(AsColors.tint(AsColors.cardio, 0x1a)),
                    contentAlignment = Alignment.Center,
                ) { AsIcon(AsIcons.Pulse, 22.dp, AsColors.cardio) }
                Column(Modifier.weight(1f)) {
                    AsText(card.name, 14f, weight = FontWeight.SemiBold)
                    AsText(card.position, 12f, Modifier.padding(top = 2.dp), color = AsColors.faint)
                }
                AsText("${card.minutes} min", 15f, weight = FontWeight.SemiBold, tabular = true, maxLines = 1)
            }
        }
        AsCaption("El cardio suma tiempo pero no series de músculo.", Modifier.padding(top = 10.dp))
    }
}

// ── Volumen por músculo ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun MuscleVolumeModule(
    model: AsMetricsModel,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
    openMuscle: String?,
    onToggle: (String) -> Unit,
) {
    val limit = model.sessionLimit
    val top = maxOf(model.muscles.firstOrNull()?.value ?: 0.0, limit ?: 0.0) * 1.06
    AsModule {
        AsModuleHeader("Volumen por músculo")
        AsSegmented(
            options = listOf("Trabajados · ${model.totalWorked}", "Todos · ${model.allMuscleCount}"),
            selected = if (showAll) 1 else 0,
            onSelect = { onShowAll(it == 1) },
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // .as-legend (margin: -2px 0 6px)
        Row(
            Modifier.padding(top = 0.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsText("Series efectivas", 11.5f, color = AsColors.faint, cap = 1.12f)
            if (limit != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(AsColors.ink.copy(alpha = 0.7f)))
                    AsText("Tu límite por sesión · ${fm(limit)}", 11.5f, color = AsColors.faint, cap = 1.12f)
                }
            }
        }
        model.muscles.forEachIndexed { index, muscle ->
            MuscleRow(
                muscle = muscle,
                first = index == 0,
                top = top,
                limit = limit,
                weeklyLimit = model.weeklyLimit,
                open = openMuscle == muscle.name,
                onToggle = { onToggle(muscle.name) },
            )
        }
        if (showAll) {
            // as-A16 as-root[data-mall=1] as-un — entrada de 0,22 s (asIn, dx 0)
            Column(
                Modifier.asEnter(AsAnim.UnworkedIn).fillMaxWidth().padding(top = 14.dp).drawBehind {
                    drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }.padding(top = 12.dp),
            ) {
                AsEyebrow("Sin trabajar en esta sesión · ${model.unworked.size}", Modifier.padding(bottom = 10.dp))
                AsFlow {
                    model.unworked.forEach { name ->
                        Box(
                            Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(AsColors.white(0x08)).padding(horizontal = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { AsText(name, 12f, color = AsColors.faint, cap = 1.12f, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleRow(
    muscle: AsMuscle,
    first: Boolean,
    top: Double,
    limit: Double?,
    weeklyLimit: Double?,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val over = limit != null && muscle.value > limit
    // as-A10 as-mrow.open as-mdet — la ficha entra en 0,22 s (asIn, dx 0) · as-T07 as-mtop .c — el chevron gira 0,22 s · as-X04 as-mrow.open .as-mtop .c — rotate(180°)
    val chevron = asFloatState(if (open) 180f else 0f, 220, AsAnim.Std)
    Column(
        Modifier
            .fillMaxWidth()
            // El `:first-of-type` del lienzo nunca alcanza a una fila (la cabecera es el primer div): todas llevan su filete.
            .drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(top = 11.dp, bottom = 12.dp)
            .semantics { stateDescription = if (open) "Desplegado" else "Plegado" }
            .asClick(role = Role.Button, onClick = onToggle),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AsText(muscle.name, 14.5f, Modifier.weight(1f), weight = FontWeight.Medium, cap = 1.15f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AsText(
                fm(muscle.value), 15.5f, Modifier.widthIn(min = 28.dp),
                color = if (over) AsColors.energy else AsColors.ink,
                weight = FontWeight.SemiBold, cap = 1.2f, tabular = true, align = TextAlign.End, maxLines = 1,
            )
            AsIcon(AsIcons.Down, 16.dp, AsColors.dim, Modifier.asRotation(chevron))
        }
        VolumeBar(value = muscle.value, top = top, limit = limit, modifier = Modifier.padding(top = 9.dp))
        if (open) MuscleDetail(muscle, over, limit, weeklyLimit)
    }
}

/** `.as-mbar`: pista de 6 dp; relleno en tinta y, pasado el límite, el exceso en ámbar; marca del límite. */
@Composable
private fun VolumeBar(value: Double, top: Double, limit: Double?, modifier: Modifier = Modifier) {
    val fraction = { v: Double -> if (top <= 0) 0f else (v / top).toFloat().coerceIn(0f, 1f) }
    // as-A08 as-mbar i — crece de izquierda a derecha (`transform-origin:left`) en 0,7 s cubic-bezier(.2,.8,.2,1), al aparecer la fila
    BoxWithConstraints(modifier.fillMaxWidth().height(6.dp)) {
        val w = maxWidth
        val over = limit != null && value > limit
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(3.dp)).background(AsColors.white(0x0d))) {
            if (over && limit != null) {
                Box(Modifier.asEnter(AsAnim.BarGrow, AsAnim.Left).width(w * fraction(limit)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(AsColors.ink))
                Box(
                    Modifier.offset(x = w * fraction(limit)).asEnter(AsAnim.BarGrow, AsAnim.Left).width(w * (fraction(value) - fraction(limit)))
                        .fillMaxHeight().clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(AsColors.energy),
                )
            } else {
                Box(Modifier.asEnter(AsAnim.BarGrow, AsAnim.Left).width(w * fraction(value)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(AsColors.ink))
            }
        }
        if (limit != null) {
            // La marca sobresale 4 dp por arriba y por abajo de la pista.
            Box(
                Modifier.offset(x = w * fraction(limit), y = (-4).dp).size(width = 2.dp, height = 14.dp)
                    .clip(RoundedCornerShape(1.dp)).background(AsColors.ink.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun MuscleDetail(muscle: AsMuscle, over: Boolean, limit: Double?, weeklyLimit: Double?) {
    val dash = androidx.compose.runtime.remember { PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) }
    Column(
        Modifier.asEnter(AsAnim.MuscleDetailIn).fillMaxWidth().padding(top = 12.dp)
            .drawBehind {
                drawLine(AsColors.white(0x18), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = dash)
            }
            .padding(top = 12.dp),
    ) {
        AsEyebrow("Qué aporta a ${muscle.name}", Modifier.padding(bottom = 8.dp))
        // .as-cl: rejilla de tres columnas (1fr auto auto), huecos 4 / 12
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            muscle.contributions.forEach { c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    AsText(c.exercise, 13f, Modifier.weight(1f), color = AsColors.muted, cap = 1.12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AsText("${c.sets} × ${fm(c.weight)}", 13f, color = AsColors.faint, cap = 1.12f, tabular = true, maxLines = 1)
                    AsText(fm(c.total), 13f, Modifier.widthIn(min = 28.dp), weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, align = TextAlign.End, maxLines = 1)
                }
            }
            AsDivider(Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                AsText("Total", 13f, Modifier.weight(1f), weight = FontWeight.SemiBold, cap = 1.12f)
                AsText(fm(muscle.value), 13f, Modifier.widthIn(min = 28.dp), weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, align = TextAlign.End, maxLines = 1)
            }
        }
        AsFlow(Modifier.padding(top = 12.dp)) {
            if (muscle.intensity != null) DetailChip("Intensidad ${muscle.intensity}", warn = false)
            val week = muscle.weekSets
            if (week != null) {
                val warn = weeklyLimit != null && week > weeklyLimit
                DetailChip("Semana ${fm(week)}${if (weeklyLimit != null) " de ${fm(weeklyLimit)}" else ""} series", warn)
            }
            if (over && limit != null) DetailChip("Supera tu límite de ${fm(limit)}", warn = true)
        }
    }
}

@Composable
private fun DetailChip(text: String, warn: Boolean) {
    Box(
        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp))
            .background(if (warn) AsColors.tint(AsColors.energy, 0x1a) else AsColors.white(0x0b)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { AsText(text, 11.5f, color = if (warn) AsColors.energy else AsColors.muted, cap = 1.12f, maxLines = 1) }
}

// ── Tiempo ───────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeModule(model: AsMetricsModel, onGoTimeRules: () -> Unit) {
    val total = model.minutes
    val limit = model.timeLimit
    val over = limit?.let { total - it }
    val scale = maxOf(total, limit ?: 0) * 1.06
    val pill: Pair<String, AsTone> = when {
        over == null -> "Sin límite" to AsTone.NEUTRAL
        over > 0 -> "+$over min sobre tu límite" to AsTone.OVER
        else -> "Dentro de tu límite" to AsTone.OK
    }
    AsModule {
        AsModuleHeader("Tiempo") { AsStatusPill(pill.first, pill.second) }
        // as-S06 as-tlim — `0 0 0 2px var(--as-mod)`: el anillo de 2 px del color del módulo que corta la barra alrededor de la marca del límite
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            val w = maxWidth
            Row(
                Modifier.width(w * (if (scale <= 0) 0f else (total / scale).toFloat().coerceIn(0f, 1f))).height(10.dp).clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                model.time.filter { it.minutes > 0 }.forEach { seg ->
                    Box(Modifier.weight(seg.minutes.toFloat()).fillMaxHeight().background(seg.color))
                }
            }
            if (limit != null && scale > 0) {
                Box(
                    Modifier.offset(x = w * (limit / scale).toFloat().coerceIn(0f, 1f), y = (-4).dp)
                        .size(width = 2.dp, height = 18.dp)
                        .kpknRing(KpknShadow.CorteHoja.spec.copyLayer(0) { it.copy(color = AsColors.module) }, RoundedCornerShape(1.dp))
                        .clip(RoundedCornerShape(1.dp)).background(AsColors.energy),
                )
            }
        }
        // .as-tleg: dos columnas con 8 / 14 de hueco
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.time.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { seg ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(seg.color))
                            Column(Modifier.weight(1f)) {
                                AsText(seg.label, 12f, color = AsColors.faint, cap = 1.12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                AsText("${seg.minutes} min", 14.5f, Modifier.padding(top = 1.dp), weight = FontWeight.SemiBold, cap = 1.15f, tabular = true, maxLines = 1)
                            }
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (limit != null) {
                    Box(Modifier.padding(end = 6.dp).size(7.dp).clip(CircleShape).background(AsColors.energy))
                    AsCaption("Tu límite · $limit min")
                } else {
                    AsCaption("Sin límite de tiempo")
                }
            }
            AsLink("Cambiar", trailing = AsIcons.Forward, minHeight = 24.dp, onClick = onGoTimeRules)
        }
    }
}
