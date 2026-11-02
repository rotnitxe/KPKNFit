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
import androidx.compose.foundation.layout.Spacer
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
import com.example.kpkn.domain.nutrition.supplements.SUPPLEMENT_MOMENTS
import com.example.kpkn.domain.nutrition.supplements.SupplementTake
import com.example.kpkn.domain.nutrition.supplements.SupplementWhenOption
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
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
    now: java.time.LocalDateTime,
    onRegister: (dose: Double, option: SupplementWhenOption) -> Unit,
    onDismiss: () -> Unit,
    initialDose: Double = item.dose,
    initialWhen: Int = 0,
    /** Total conocido del día de la opción elegida; por defecto, el del día ya calculado. */
    dayTotalFor: (String) -> Double = { dayTotalNow },
) {
    var dose by remember { mutableDoubleStateOf(initialDose) }
    var whenIndex by remember { mutableIntStateOf(initialWhen) }
    val options = remember(now) { SupplementEngine.whenOptions(now) }
    val selectedDay = options[whenIndex].date
    val dayTotal = dayTotalFor(selectedDay)
    val ceiling = SupplementEngine.ceiling(def, dayTotal, dose)
    val after = dayTotal + dose
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
            options.forEachIndexed { i, option ->
                SpChoice(option.label, selected = whenIndex == i, small = true, trailing = option.time, onClick = { whenIndex = i })
            }
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
                onRegister(dose, options[whenIndex])
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
    now: java.time.LocalDateTime,
    onRegister: (dose: Double, option: SupplementWhenOption) -> Unit,
    onDismiss: () -> Unit,
    dayTotalFor: (String) -> Double = { dayTotalNow },
) = SpSheet(onDismiss = onDismiss) {
    SupplementAdjustContent(def, item, dayTotalNow, now, onRegister, onDismiss, dayTotalFor = dayTotalFor)
}

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
    onOpenCustom: () -> Unit = {},
    onOpenConfig: (String) -> Unit = {},
) = SpSheet(onDismiss = onDismiss, maxHeightFraction = 0.86f, scroll = false) { SupplementAddContent(pilaIds, onToggle, onDismiss) }

/** Modo de la hoja de configuración de un suplemento. */
enum class SupplementConfigMode { NEW, EDIT, CUSTOM }

/**
 * Construye lo que se guarda al confirmar la hoja de configuración. Es una función pura para poder probar que editar
 * un suplemento propio conserva su id, nombre, unidad y notas, y que editar uno del catálogo conserva su id sin
 * convertirlo en propio. Solo crear un propio genera un id nuevo (`propio-…`).
 */
internal fun supplementConfigItem(
    initialItem: SupplementPilaItem?,
    def: SupplementDef?,
    isCustom: Boolean,
    dose: Double,
    moment: Int,
    doses: Int,
    reminderEnabled: Boolean,
    reminderTime: String,
    customName: String,
    customUnit: String,
    notes: String,
): SupplementPilaItem {
    val id = initialItem?.id
        ?: if (isCustom) "propio-${java.util.UUID.randomUUID().toString().take(8)}" else (def?.id ?: "custom")
    return SupplementPilaItem(
        id = id,
        dose = dose,
        moment = moment,
        doses = doses,
        reminder = if (reminderEnabled) reminderTime else null,
        customName = if (isCustom) customName.trim() else null,
        customUnit = if (isCustom) customUnit else null,
        notes = notes.trim(),
    )
}

/**
 * Hoja de detalle de un suplemento (sp-detalle, sp-detalle-cafeina): resumen del día, racha, tomas de hoy con «Quitar»,
 * aporte a la huella y botón para registrar la dosis habitual.
 */
@Composable
fun SupplementDetailSheet(
    def: SupplementDef,
    item: SupplementPilaItem,
    todayTakes: List<SupplementTake>,
    foodCaffeineMg: Double = 0.0,
    creatineStreakDays: Int = 0,
    onTakeHabitual: () -> Unit,
    onDeleteTake: (SupplementTake) -> Unit,
    onEditConfig: () -> Unit,
    openCreatine: () -> Unit = {},
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss) {
    val isCaffeine = def.id == "cafeina"
    val isCreatine = def.id == "creatina"
    val totalDose = todayTakes.sumOf { it.dose } + (if (isCaffeine) foodCaffeineMg else 0.0)

    SheetHeader(
        def = def,
        title = if (isCaffeine) "Cafeína de hoy" else def.name,
        subtitle = "${SupplementEngine.doseLabel(def, item.dose)} habitual · ${SUPPLEMENT_MOMENTS.getOrNull(item.moment) ?: ""}",
        trailing = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0x0DFFFFFF)).clickable(role = Role.Button, onClick = onEditConfig),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Sliders, "Configurar", tint = SpColors.ink, modifier = Modifier.size(20.dp))
            }
        },
    )

    // Resumen de hoy
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x08FFFFFF)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("TOTAL DE HOY", style = KpknType.caption.copy(fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold), color = SpColors.faint)
                Text(
                    text = "${SupplementEngine.formatNumberEs(totalDose)} ${def.unit}",
                    style = KpknType.title.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    color = if (isCaffeine && totalDose > 400.0) SpColors.danger else SpColors.ink,
                )
            }
            if (isCreatine && creatineStreakDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(KpknLineIcons.Flame, null, tint = Color(0xFFF49A6E), modifier = Modifier.size(16.dp))
                    Text("$creatineStreakDays días seguidos", style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = SpColors.ink)
                }
            }
        }
        if (def.cap != null) {
            SpBar(totalDose, def.cap, Modifier.padding(top = 4.dp), over = (def.cap != null && totalDose > def.cap))
        }
    }

    // Para la creatina: acceso a la saturación
    if (isCreatine) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0DFFFFFF)).clickable(role = Role.Button, onClick = openCreatine).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(KpknLineIcons.Layers, null, tint = SpColors.ink, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Seguimiento de saturación", style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = SpColors.ink)
                Text("Pauta de carga o gradual", style = KpknType.caption.copy(fontSize = 12.sp), color = SpColors.faint)
            }
            Icon(KpknLineIcons.ChevronRight, null, tint = SpColors.faint, modifier = Modifier.size(16.dp))
        }
    }

    // Lista de tomas de hoy
    Text("TOMAS DE HOY", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint, modifier = Modifier.padding(top = 6.dp))
    if (todayTakes.isEmpty() && (!isCaffeine || foodCaffeineMg <= 0.0)) {
        Text("Todavía no has registrado ninguna toma hoy.", style = KpknType.caption.copy(fontSize = 13.sp), color = SpColors.muted)
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            todayTakes.forEach { take ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x06FFFFFF)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(take.time, style = KpknType.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = SpColors.muted)
                    Column(Modifier.weight(1f)) {
                        Text("${SupplementEngine.formatNumberEs(take.dose)} ${def.unit}", style = KpknType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = SpColors.ink)
                        Text("Registrado con un toque", style = KpknType.caption.copy(fontSize = 11.5.sp), color = SpColors.faint)
                    }
                    Text(
                        "Quitar",
                        style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = SpColors.faint,
                        modifier = Modifier.clickable(role = Role.Button, onClick = { onDeleteTake(take) }).padding(4.dp),
                    )
                }
            }
            if (isCaffeine && foodCaffeineMg > 0.0) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x06FFFFFF)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KpknLineIcons.Apple, null, tint = SpColors.faint, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${SupplementEngine.formatNumberEs(foodCaffeineMg)} mg", style = KpknType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = SpColors.ink)
                        Text("Café, té o comidas registradas", style = KpknType.caption.copy(fontSize = 11.5.sp), color = SpColors.faint)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    KpknPrimaryPill(
        text = "Registrar ${SupplementEngine.doseLabel(def, item.dose)}",
        onClick = { onTakeHabitual(); onDismiss() },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
}

/**
 * Hoja para configurar / dar de alta / editar un suplemento (sp-alta, sp-propio, sp-edicion).
 */
@Composable
fun SupplementConfigSheet(
    mode: SupplementConfigMode,
    def: SupplementDef?,
    initialItem: SupplementPilaItem?,
    onSave: (SupplementPilaItem) -> Unit,
    onOpenBaja: () -> Unit = {},
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss) {
    // Un suplemento propio se reconoce por sus datos, no solo por el modo con que se abrió: editarlo conserva su
    // identidad (id `propio-…`, nombre, unidad y notas) y no lo convierte en una entrada del catálogo.
    val isCustom = mode == SupplementConfigMode.CUSTOM || initialItem?.customName != null
    val isEdit = mode == SupplementConfigMode.EDIT
    var customName by remember { mutableStateOf(initialItem?.customName ?: "") }
    var customUnit by remember { mutableStateOf(initialItem?.customUnit ?: "g") }
    var dose by remember { mutableDoubleStateOf(initialItem?.dose ?: def?.defaultDose ?: 5.0) }
    var tomas by remember { mutableIntStateOf(initialItem?.doses ?: 1) }
    var moment by remember { mutableIntStateOf(initialItem?.moment ?: def?.defaultMoment ?: 1) }
    var reminderEnabled by remember { mutableStateOf(initialItem?.reminder != null) }
    var reminderTime by remember { mutableStateOf(initialItem?.reminder ?: "09:00") }
    var showReminderPicker by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }

    val step = if (isCustom && customUnit == "mg") 50.0 else (def?.step ?: 1.0)
    val unit = if (isCustom) customUnit else (def?.unit ?: "g")
    val title = when {
        isCustom -> "Suplemento propio"
        isEdit -> "Editar ${def?.shortName ?: ""}"
        else -> "Configurar ${def?.shortName ?: ""}"
    }

    SheetHeader(
        def = def,
        title = title,
        subtitle = if (isCustom) "Nombre, unidad y dosis habitual" else (def?.typical ?: ""),
    )

    if (isCustom) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NOMBRE", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x0CFFFFFF)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    textStyle = KpknType.body.copy(fontSize = 14.5.sp, color = SpColors.ink),
                    cursorBrush = SolidColor(SpColors.ink),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nombre del suplemento" },
                )
            }
            Text("UNIDAD", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("g", "mg", "µg", "ud").forEach { u ->
                    SpChoice(u, selected = customUnit == u, small = true, onClick = { customUnit = u })
                }
            }
            Text("NOTAS", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint, modifier = Modifier.padding(top = 6.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x0CFFFFFF)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    textStyle = KpknType.body.copy(fontSize = 14.5.sp, color = SpColors.ink),
                    cursorBrush = SolidColor(SpColors.ink),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Notas del suplemento" },
                )
            }
        }
    } else if (initialItem?.notes?.isNotBlank() == true) {
        // Un suplemento del catálogo conserva sus notas al editarlo, sin convertirlo en propio.
        Text("NOTAS", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
        Text(initialItem.notes, style = KpknType.body.copy(fontSize = 14.sp), color = SpColors.muted)
    }

    // Dosis habitual
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DOSIS HABITUAL (UN TOQUE)", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundStep(minus = true, enabled = dose > step) { dose = (dose - step).coerceAtLeast(step) }
            Text(
                "${SupplementEngine.formatNumberEs(dose)} $unit",
                style = KpknType.title.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                color = SpColors.ink,
            )
            RoundStep(minus = false, enabled = dose < (def?.max ?: 9999.0)) { dose = dose + step }
        }
    }

    // Tomas al día
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TOMAS AL DÍA", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1 al día", 2 to "2 al día", 3 to "3 al día").forEach { (count, label) ->
                SpChoice(label, selected = tomas == count, small = true, onClick = { tomas = count })
            }
        }
    }

    // Momento habitual
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MOMENTO SUGERIDO", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
        FlowRowOf {
            for (idx in SUPPLEMENT_MOMENTS.indices) {
                val momLabel = SUPPLEMENT_MOMENTS[idx]
                SpChoice(momLabel, selected = moment == idx, small = true, onClick = { moment = idx })
            }
        }
    }

    // Recordatorio: activar/desactivar y elegir la hora real (la alarma se programa al guardar).
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RECORDATORIO", style = KpknType.eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp), color = SpColors.faint)
            SpChoice(if (reminderEnabled) "Activado" else "Desactivado", selected = reminderEnabled, small = true, onClick = { reminderEnabled = !reminderEnabled })
        }
        if (reminderEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpChoice(reminderTime, selected = true, small = true, onClick = { showReminderPicker = true })
                Text("Aviso diario a esa hora", style = KpknType.caption.copy(fontSize = 12.sp), color = SpColors.faint)
            }
        }
    }
    if (showReminderPicker) {
        KpknNativeTimePickerDialog(
            title = "Hora del recordatorio",
            initialHour = reminderTime.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 9,
            initialMinute = reminderTime.substringAfter(':', "00").toIntOrNull()?.coerceIn(0, 59) ?: 0,
            onConfirm = { hour, minute ->
                reminderTime = "%02d:%02d".format(hour, minute)
                reminderEnabled = true
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false },
        )
    }

    // Advertencia sobria / huella
    if (def != null && def.cap != null) {
        Warn("Techo diario: ${SupplementEngine.formatNumberEs(def.cap)} ${def.unit}. ${def.capText}", bold = "Advertencia:")
    }

    Spacer(Modifier.height(8.dp))
    val canSave = !isCustom || customName.isNotBlank()
    KpknPrimaryPill(
        text = if (isEdit) "Guardar cambios" else "Añadir a mi pila",
        enabled = canSave,
        onClick = {
            onSave(
                supplementConfigItem(
                    initialItem = initialItem,
                    def = def,
                    isCustom = isCustom,
                    dose = dose,
                    moment = moment,
                    doses = tomas,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    customName = customName,
                    customUnit = customUnit,
                    notes = notes,
                ),
            )
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )

    if (isEdit) {
        Text(
            "Dejar de tomar este suplemento",
            style = KpknType.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = SpColors.danger,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(role = Role.Button, onClick = onOpenBaja).padding(8.dp),
        )
    }
}

/** Hoja «Dejar de tomar» (sp-baja): confirmación sin borrar el historial. */
@Composable
fun SupplementBajaSheet(
    def: SupplementDef,
    onConfirmBaja: () -> Unit,
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss) {
    SheetHeader(
        def = def,
        title = "¿Dejar de tomar ${def.shortName}?",
        subtitle = "Confirmación",
        danger = true,
    )
    Text(
        "El historial de tomas de días anteriores no se toca: sus aportes se mantienen. Solo sale de tu pila y de la tarjeta de Inicio.",
        style = KpknType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = SpColors.muted,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            KpknSecondaryPill("Cancelar", onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp))
        }
        Box(Modifier.weight(1f)) {
            KpknPrimaryPill("Dejar de tomar", onClick = { onConfirmBaja(); onDismiss() }, modifier = Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

/** Hoja de registro rápido de hoy (sp-rapida): acceso desde la tarjeta de Inicio. */
@Composable
fun SupplementQuickSheet(
    rows: List<SupplementRowState>,
    caffeineTotalMg: Double,
    foodCaffeineMg: Double,
    nowId: String?,
    onTake: (id: String, dose: Double) -> Unit,
    onHold: (id: String) -> Unit,
    onAdd: () -> Unit,
    onOpenDetail: (String) -> Unit = {},
    onDismiss: () -> Unit,
) = SpSheet(onDismiss = onDismiss, maxHeightFraction = 0.85f, scroll = false) {
    val isDone: (SupplementRowState) -> Boolean = { row -> SupplementEngine.isDone(row.item, row.takes) }
    val done = rows.count(isDone)
    val pending = rows.filterNot(isDone)
    SheetHeader(
        def = null,
        title = "Suplementos de hoy",
        subtitle = if (pending.isEmpty()) "Todo tomado hoy ✓" else "$done de ${rows.size} tomados",
    )
    LazyColumn(Modifier.weight(1f, fill = false)) {
        items(rows, key = { it.def.id }) { row ->
            val rowDone = isDone(row)
            val target = row.item.doses.coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (rowDone) Color(0x1843D18C) else Color(0x0DFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (rowDone) KpknLineIcons.Check else supplementIcon(row.def), null, tint = if (rowDone) SpColors.done else SpColors.muted, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).clickable { onOpenDetail(row.def.id) }) {
                    Text(row.def.name, style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = SpColors.ink)
                    Text(
                        text = if (target > 1) "${SupplementEngine.doseLabel(row.def, row.item.dose)} · ${row.takes.size.coerceAtMost(target)} de $target" else SupplementEngine.doseLabel(row.def, row.item.dose),
                        style = KpknType.caption.copy(fontSize = 12.sp),
                        color = SpColors.faint,
                    )
                }
                if (!rowDone) {
                    Box(
                        Modifier.clip(RoundedCornerShape(18.dp)).background(if (nowId == row.def.id) SpColors.ink else Color(0x12FFFFFF)).clickable { onTake(row.def.id, row.item.dose) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Tomar", style = KpknType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = if (nowId == row.def.id) SpColors.onInk else SpColors.ink)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            KpknSecondaryPill("Añadir", onClick = { onAdd(); onDismiss() }, modifier = Modifier.fillMaxWidth().height(46.dp))
        }
        Box(Modifier.weight(1f)) {
            KpknPrimaryPill("Listo", onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(46.dp))
        }
    }
}

