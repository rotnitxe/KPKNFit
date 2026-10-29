package com.example.kpkn.screens.competitions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.screens.competitions.design.CompBig
import com.example.kpkn.screens.competitions.design.CompCenteredAction
import com.example.kpkn.screens.competitions.design.CompChips
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompInputField
import com.example.kpkn.screens.competitions.design.CompMGroup
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompSheetOption
import com.example.kpkn.screens.competitions.design.CompSheetSub
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.FederationMark
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknMonthCalendar
import java.time.LocalDate
import kotlin.math.roundToInt

private val SheetScrim = Color.Black.copy(alpha = 0.67f)

/** Hoja del lienzo (`.sheet`): 18 a los lados, 30 abajo y el velo negro del 67 %. */
@Composable
internal fun CompSheet(onDismiss: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = SheetScrim) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
            content = content,
        )
    }
}

/** El peso de un intento: teclado numérico y ±2,5 kg (c-plan, c-log). [extra] añade «Saltado» o «Cambiar movimiento». */
@Composable
internal fun CompWeightSheet(
    title: String,
    initialKg: Double?,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var text by remember { mutableStateOf(initialKg?.let { CompetitionFormat.kg(it) }.orEmpty()) }
    val kg = CompetitionFormat.parseKg(text)
    fun nudge(delta: Double) {
        val base = kg ?: initialKg ?: 0.0
        text = CompetitionFormat.kg(((base + delta).coerceAtLeast(0.0) / 2.5).roundToInt() * 2.5)
    }
    CompSheet(onDismiss) {
        CompSheetTitle(title)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompPill("−2,5", { nudge(-2.5) }, primary = false, small = true)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { CompBig(text.ifBlank { "—" }, 40, "kg") }
            CompPill("+2,5", { nudge(2.5) }, primary = false, small = true)
        }
        CompInputField("Peso (kg)", text, { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = "Escribe el peso")
        extra()
        CompPill("Listo", { onConfirm(kg); onDismiss() }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** Un texto o una cifra que se escribe en una hoja. */
@Composable
internal fun CompTextSheet(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    numeric: Boolean = false,
    sub: String? = null,
    secondLabel: String? = null,
    secondInitial: String = "",
    onConfirmBoth: ((String, String) -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    var second by remember { mutableStateOf(secondInitial) }
    val keyboard = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default
    CompSheet(onDismiss) {
        CompSheetTitle(title)
        sub?.let { CompSheetSub(it) }
        CompInputField(label, text, { text = it }, keyboard = keyboard)
        if (secondLabel != null) CompInputField(secondLabel, second, { second = it }, keyboard = keyboard)
        CompPill("Listo", {
            if (onConfirmBoth != null) onConfirmBoth(text, second) else onConfirm(text)
            onDismiss()
        }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** Un tiempo en horas, minutos y segundos (c-run «Tu tiempo», «Tu objetivo», el tiempo total de c-mixed). */
@Composable
internal fun CompDurationSheet(title: String, initialSeconds: Double?, onConfirm: (Double?) -> Unit, onDismiss: () -> Unit, withHundredths: Boolean = false) {
    val total = initialSeconds ?: 0.0
    var h by remember { mutableStateOf(if (initialSeconds == null) "" else (total.toLong() / 3600).toString()) }
    var m by remember { mutableStateOf(if (initialSeconds == null) "" else ((total.toLong() % 3600) / 60).toString()) }
    var s by remember {
        mutableStateOf(if (initialSeconds == null) "" else if (withHundredths) CompetitionFormat.kg(total % 60) else (total.toLong() % 60).toString())
    }
    val digits = KeyboardOptions(keyboardType = if (withHundredths) KeyboardType.Decimal else KeyboardType.Number)
    fun parsed(): Double? {
        val hh = h.toIntOrNull() ?: 0
        val mm = m.toIntOrNull() ?: 0
        val ss = s.replace(',', '.').toDoubleOrNull() ?: 0.0
        return (hh * 3600 + mm * 60 + ss).takeIf { it > 0 }
    }
    CompSheet(onDismiss) {
        CompSheetTitle(title)
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CompBig(parsed()?.let { if (withHundredths && it < 60) CompetitionFormat.kg(it) + " s" else CompetitionFormat.duration(it) } ?: "—", 40)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!withHundredths) CompInputField("Horas", h, { h = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), "0", digits)
            CompInputField("Minutos", m, { m = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), "0", digits)
            CompInputField("Segundos", s, { s = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, Modifier.weight(1f), "0", digits)
        }
        CompPill("Listo", { onConfirm(parsed()); onDismiss() }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** La fecha en el calendario de la app. */
@Composable
internal fun CompDateSheet(title: String, selected: LocalDate?, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit, min: LocalDate? = null, max: LocalDate? = null) {
    CompSheet(onDismiss) {
        CompSheetTitle(title)
        KpknMonthCalendar(selected = selected, onSelect = { onSelect(it); onDismiss() }, today = LocalDate.now(), minDate = min, maxDate = max,
            modifier = Modifier.padding(top = 8.dp))
    }
}

/** «Federación · puntos» (C-D7): la lista de federaciones de hoy con sus logotipos, por continente, y una propia. */
@Composable
internal fun CompFederationSheet(selectedId: String?, customName: String?, onSelect: (String?, String?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var continent by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf(customName.orEmpty()) }
    val continents = PowerliftingFederationCatalog.continents
    CompSheet(onDismiss) {
        CompSheetTitle("Federación")
        CompSheetSub("La federación decide la fórmula de puntos.")
        CompInputField("Buscar", query, { query = it }, placeholder = "Nombre o país")
        Spacer(Modifier.height(8.dp))
        CompChips(listOf("Todas") + continents.map { it.second }, { i -> if (i == 0) continent == null else continent == continents[i - 1].first }) { i ->
            continent = if (i == 0) null else continents[i - 1].first
        }
        CompMGroup("Federaciones")
        PowerliftingFederationCatalog.search(query, continent).forEach { fed ->
            CompSheetOption(
                title = fed.name,
                subtitle = "${fed.shortName} · ${fed.country} · ${com.example.kpkn.domain.competitions.PowerliftingPointsFormula.entries.firstOrNull { it.id == fed.pointsFormula }?.label ?: fed.pointsFormula}",
                selected = fed.id == selectedId,
                onClick = { onSelect(fed.id, null); onDismiss() },
                leading = { FederationMark(fed) },
            )
        }
        CompMGroup("Otra")
        CompInputField("Nombre de la federación", custom, { custom = it })
        CompPill("Usar esta", { if (custom.isNotBlank()) { onSelect(PowerliftingFederationCatalog.CUSTOM_ID, custom.trim()); onDismiss() } },
            Modifier.fillMaxWidth().padding(top = 12.dp), primary = false, enabled = custom.isNotBlank())
        CompCenteredAction("Cerrar", onDismiss)
    }
}

/** Confirmación simple con dos salidas en el lenguaje del lienzo. */
@Composable
internal fun CompConfirmSheet(title: String, sub: String, confirm: String, onConfirm: () -> Unit, dismiss: String, onDismiss: () -> Unit, danger: Boolean = false) {
    CompSheet(onDismiss, scroll = false) {
        CompSheetTitle(title)
        CompSheetSub(sub)
        CompPill(confirm, { onConfirm(); onDismiss() }, Modifier.fillMaxWidth().padding(top = 8.dp),
            container = if (danger) CompColors.danger else null, content = if (danger) Color(0xFF2A0F0C) else null)
        CompCenteredAction(dismiss, onDismiss)
    }
}
