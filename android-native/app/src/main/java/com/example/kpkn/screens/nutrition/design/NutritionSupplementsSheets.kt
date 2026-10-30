package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.domain.nutrition.supplements.SupplementCatalog
import com.example.kpkn.domain.nutrition.supplements.SupplementDef
import com.example.kpkn.domain.nutrition.supplements.SupplementEngine
import com.example.kpkn.domain.nutrition.supplements.SupplementGroup
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetSpec
import com.example.kpkn.ui.components.LocalKpknSheetDismiss
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState

/**
 * `.pl-sheet` de Suplementos: #1B1B1D plana, radio 28, velo `#000000a8` y la sombra `hoja` (sp-S02). Los tiempos de entrada y salida son los
 * de las demás hojas planas (el lienzo de Suplementos no define unos propios).
 */
internal val SpSheetSpec = KpknSheetSpec(
    shadow = KpknShadow.Hoja.spec,
    scrimInMs = 220,
    scrimEasing = KpknMotion.Estandar,
    sheetInMs = 340,
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
internal fun SpSheet(onDismiss: () -> Unit, maxHeightFraction: Float = 0.9f, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    KpknSheet(
        onDismissRequest = onDismiss,
        surfaceColor = Color(0xFF1B1B1D),
        backdropScrim = Color(0xA8000000),
        maxHeightFraction = maxHeightFraction,
        lienzo = SpSheetSpec,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetHeader(def: SupplementDef?, title: String, subtitle: String, danger: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (def != null || danger) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (danger) Color(0x14FF9B92) else Color(0x0DFFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (danger) KpknLineIcons.Alert else supplementIcon(def), null, tint = if (danger) SpColors.danger else SpColors.muted, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = KpknType.title.copy(fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.4).sp), color = SpColors.ink)
            Text(subtitle, style = KpknType.caption.copy(fontSize = 12.5.sp), color = SpColors.faint)
        }
        trailing?.invoke()
    }
}

/** `.sp-proj`: el día con esta toma, en una caja de `#ffffff08` con la barra de total frente al máximo. */
@Composable
private fun Projection(name: String, value: Double, unit: String, limit: Double, band: Pair<Double, Double>?, over: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x08FFFFFF)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(name, style = KpknType.caption.copy(fontSize = 12.5.sp), color = SpColors.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    SupplementEngine.formatNumberEs(value),
                    style = KpknType.bodyStrong.copy(fontSize = 15.sp),
                    color = if (over) SpColors.danger else SpColors.ink,
                )
                Text(" de ${SupplementEngine.formatNumberEs(limit)} $unit", style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = SpColors.faint)
            }
        }
        SpBar(value, limit, Modifier.padding(top = 9.dp), band = band, over = over)
    }
}

@Composable
private fun Warn(text: String, bold: String? = null, icon: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        if (icon) Icon(KpknLineIcons.Alert, null, tint = SpColors.danger, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                if (bold != null) {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = SpColors.ink, fontWeight = FontWeight.SemiBold))
                    append(bold)
                    pop()
                    append(" ")
                }
                append(text)
            },
            style = KpknType.caption.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = SpColors.muted,
        )
    }
}

@Composable
private fun SpChoice(text: String, selected: Boolean, small: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    val fill by animateKpknColorAsState(if (selected) SpColors.ink else Color(0x0CFFFFFF), KpknMotion.D160)
    val tint by animateKpknColorAsState(if (selected) SpColors.onInk else SpColors.muted, KpknMotion.D160)
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .height(if (small) 38.dp else 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(fill)
                .then(if (selected) Modifier else Modifier.border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(22.dp)))
                .padding(horizontal = if (small) 12.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text, style = KpknType.caption.copy(fontSize = if (small) 12.5.sp else 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = tint)
            if (trailing != null) Text(trailing, style = KpknType.caption.copy(fontSize = if (small) 12.5.sp else 13.sp), color = tint.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun RoundStep(minus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0x10FFFFFF))
            .border(1.dp, Color(0x1CFFFFFF), CircleShape)
            .semantics { contentDescription = if (minus) "Menos" else "Más" }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (minus) KpknLineIcons.Minus else KpknLineIcons.Plus, null, tint = SpColors.ink.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(22.dp))
    }
}

/**
 * Hoja «Ajustar esta toma» (sp-ajustar): la dosis con − / + y ½ · Habitual · ×2, «Cuándo» (ahora, −30 min, −1 h, esta mañana), el día con
 * esta toma frente al máximo y, si lo pasa, la frase del techo. [dayTotalNow] es lo que hay hoy en la unidad del suplemento (la cafeína
 * incluye comidas). [onRegister] recibe la dosis y la hora `HH:mm`; el techo se avisa luego con [CeilingSheet] si lo pasa.
 */
@Composable
fun ColumnScope.SupplementAdjustContent(
    def: SupplementDef,
    item: SupplementPilaItem,
    dayTotalNow: Double,
    nowHhmm: String,
    onRegister: (dose: Double, time: String) -> Unit,
    onDismiss: () -> Unit,
    initialDose: Double = item.dose,
    initialWhen: Int = 0,
) {
    var dose by remember { mutableDoubleStateOf(initialDose) }
    var whenIndex by remember { mutableIntStateOf(initialWhen) }
    val options = remember(nowHhmm) { SupplementEngine.whenOptions(nowHhmm) }
    val ceiling = SupplementEngine.ceiling(def, dayTotalNow, dose)
    val after = dayTotalNow + dose
    val limit = def.cap ?: (item.dose * item.doses)
    val over = ceiling?.exceeded == true
    run {
        val close = LocalKpknSheetDismiss.current
        SheetHeader(def, def.shortName, "Ajustar esta toma · hoy")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            RoundStep(minus = true, enabled = dose > def.min + 1e-9) { dose = SupplementEngine.stepDose(def, dose, -1) }
            Column(Modifier.widthIn(min = 130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SupplementEngine.formatNumberEs(dose), style = KpknType.metricXL.copy(fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-1).sp), color = SpColors.ink, textAlign = TextAlign.Center)
                Text(
                    if (def.noun) (if (dose == 1.0) def.unit else def.unitPlural ?: def.unit) else def.unit,
                    style = KpknType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = SpColors.faint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            RoundStep(minus = false, enabled = true) { dose = SupplementEngine.stepDose(def, dose, 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            listOf("½" to item.dose / 2, "Habitual" to item.dose, "×2" to item.dose * 2).forEach { (label, value) ->
                SpChoice(label, selected = kotlin.math.abs(dose - value) < 1e-9, onClick = { dose = value.coerceIn(def.min, def.max) })
            }
        }
        Text("CUÁNDO", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint, modifier = Modifier.padding(top = 4.dp))
        FlowRowOf {
            options.forEachIndexed { i, (label, time) -> SpChoice(label, selected = whenIndex == i, small = true, trailing = time, onClick = { whenIndex = i }) }
        }
        Projection(
            name = if (def.id == "cafeina") "Cafeína del día" else "Hoy con esta toma",
            value = after,
            unit = if (def.noun) def.unitPlural ?: def.unit else def.unit,
            limit = limit,
            band = if (def.id == "cafeina") SupplementEngine.CAFFEINE_BAND_MIN_MG to SupplementEngine.CAFFEINE_BAND_MAX_MG else null,
            over = over,
        )
        if (over) Warn(text = def.capText, bold = "Pasas el máximo diario.", icon = true)
        KpknPrimaryPill(
            text = "Registrar ${SupplementEngine.doseLabel(def, dose)}",
            onClick = {
                onRegister(dose, options[whenIndex].second)
                if (close != null) close() else onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOf(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
}

/** Hoja «Esta toma pasa tu máximo» (sp-aviso): es un aviso, no un bloqueo; «Registrar igual» siempre está. */
@Composable
fun ColumnScope.SupplementCeilingContent(
    def: SupplementDef,
    dose: Double,
    projected: Double,
    onRegisterAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    run {
        val close = LocalKpknSheetDismiss.current
        SheetHeader(null, "Esta toma pasa tu máximo", "${def.shortName} · ${SupplementEngine.doseLabel(def, dose)}", danger = true)
        Projection("${def.shortName} del día", projected, def.unit, def.cap ?: projected, band = null, over = true)
        Warn(text = "${def.capText} Puedes registrarla igual: tú decides.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpknSecondaryPill(text = "No registrar", onClick = { if (close != null) close() else onDismiss() }, modifier = Modifier.weight(1f))
            KpknPrimaryPill(
                text = "Registrar igual",
                onClick = {
                    onRegisterAnyway()
                    if (close != null) close() else onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Hoja «Añadir suplemento» (sp-anadir): el catálogo de 11 por grupos con su dosis típica y su etiqueta de evidencia; el botón de la derecha
 * suma o quita de la pila con la dosis y el momento típicos. Buscador por nombre y filtro por grupo.
 */
@Composable
fun ColumnScope.SupplementAddContent(
    pilaIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<SupplementGroup?>(null) }
    val list = SupplementCatalog.entries.filter { (group == null || it.group == group) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    run {
        SheetHeader(
            null,
            "Añadir suplemento",
            "${pilaIds.size} en tu pila · toca + para sumarlo con su dosis típica",
            trailing = {
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = "Cerrar", onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
                        Icon(KpknLineIcons.Close, "Cerrar", tint = SpColors.ink, modifier = Modifier.size(20.dp))
                    }
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x0CFFFFFF)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(KpknLineIcons.Search, null, tint = SpColors.faint, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Buscar suplemento", style = KpknType.body.copy(fontSize = 14.sp), color = SpColors.faint)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = KpknType.body.copy(fontSize = 14.sp, color = SpColors.ink),
                    cursorBrush = SolidColor(SpColors.ink),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar suplemento" },
                )
            }
        }
        FlowRowOf {
            SpChoice("Todos", selected = group == null, small = true, onClick = { group = null })
            listOf(SupplementGroup.PERFORMANCE to "Rendimiento", SupplementGroup.HEALTH to "Salud").forEach { (g, label) ->
                SpChoice(label, selected = group == g, small = true, onClick = { group = g })
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            SupplementGroup.entries.forEach { g ->
                val inGroup = list.filter { it.group == g }
                if (inGroup.isNotEmpty()) {
                    item(key = "gh-${g.name}") {
                        Text(g.label.uppercase(), style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                    }
                    items(inGroup, key = { it.id }) { def -> CatalogRow(def, first = def == inGroup.first(), on = def.id in pilaIds, onToggle = { onToggle(def.id) }) }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(def: SupplementDef, first: Boolean, on: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
                Icon(supplementIcon(def), null, tint = SpColors.muted, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(def.name, style = KpknType.body.copy(fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = SpColors.ink)
                Text(def.typical, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = SpColors.faint, modifier = Modifier.padding(top = 2.dp))
                Text(def.evidence.label, style = KpknType.caption.copy(fontSize = 11.sp), color = SpColors.dim, modifier = Modifier.padding(top = 1.dp))
            }
            val fill by animateKpknColorAsState(if (on) SpColors.ink else Color.Transparent, KpknMotion.D200)
            val tint by animateKpknColorAsState(if (on) SpColors.onInk else SpColors.ink, KpknMotion.D200)
            Box(Modifier.size(48.dp).clickable(role = Role.Checkbox, onClickLabel = if (on) "Quitar de mi pila" else "Añadir a mi pila", onClick = onToggle), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(fill).then(if (on) Modifier else Modifier.border(1.dp, Color(0x22FFFFFF), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (on) KpknLineIcons.Check else KpknLineIcons.Plus, null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Hoja «Ajustar esta toma»: [SupplementAdjustContent] dentro de la hoja plana. */
@Composable
fun SupplementAdjustSheet(
    def: SupplementDef,
    item: SupplementPilaItem,
    dayTotalNow: Double,
    nowHhmm: String,
    onRegister: (dose: Double, time: String) -> Unit,
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss) { SupplementAdjustContent(def, item, dayTotalNow, nowHhmm, onRegister, onDismiss) }

/** Hoja «Esta toma pasa tu máximo»: [SupplementCeilingContent] dentro de la hoja plana. */
@Composable
fun SupplementCeilingSheet(
    def: SupplementDef,
    dose: Double,
    projected: Double,
    onRegisterAnyway: () -> Unit,
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss) { SupplementCeilingContent(def, dose, projected, onRegisterAnyway, onDismiss) }

/** Hoja «Añadir suplemento»: [SupplementAddContent] dentro de la hoja plana (86 % de alto como máximo, la lista se desplaza sola). */
@Composable
fun SupplementAddSheet(
    pilaIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss, maxHeightFraction = 0.86f, scroll = false) { SupplementAddContent(pilaIds, onToggle, onDismiss) }
