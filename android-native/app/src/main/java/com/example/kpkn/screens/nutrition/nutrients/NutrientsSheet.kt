package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*
import dev.chrisbanes.haze.HazeState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class NutrientsSheetState(val date: String = "", val rows: List<NutrientDayTotal> = emptyList(), val profile: DriProfile? = null, val energyKcal: Double? = null)
fun buildNutrientsSheetState(date: String, logs: List<NutritionLog>, profile: DriProfile?): NutrientsSheetState {
    val foods = logs.filter { it.status != NutritionStatus.PLANNED && it.date.take(10) == date }.flatMap { it.foods }
    return NutrientsSheetState(date, NutrientCoverage.summarize(foods), profile, foods.takeIf { it.isNotEmpty() }?.sumOf { it.calories })
}

@Composable
fun NutrientsSheet(state: NutrientsSheetState, onDismiss: () -> Unit, hazeState: HazeState? = null) {
    var expanded by remember(state.date) { mutableStateOf(emptySet<NutrientKey>()) }
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        NutrientsContent(state, expanded, { key -> expanded = if (key in expanded) expanded - key else expanded + key }, onDismiss,
            Modifier.fillMaxHeight(0.9f))
    }
}

/** La lista y sus detalles comparten exactamente los totales del motor, también en las vistas de prueba. */
@Composable
fun NutrientsContent(state: NutrientsSheetState, expanded: Set<NutrientKey>, onToggle: (NutrientKey) -> Unit,
    onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Nutrientes del día", style = KpknType.title, color = WizardColors.text, modifier = Modifier.weight(1f))
            KpknIconButton(KpknLineIcons.Close, "Cerrar nutrientes", onDismiss)
        }
        LazyColumn(Modifier.weight(1f).testTag("nutrients-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(runCatching { java.time.LocalDate.parse(state.date).format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))) }.getOrDefault(state.date), style = KpknType.caption, color = WizardColors.textMuted)
                Text("El total solo cuenta los alimentos con dato; puede ser mayor.", style = KpknType.body, color = WizardColors.textMuted)
                Text(state.profile?.let { "Referencia adulta según la ecuación ${if (it.sex == DriSex.FEMALE) "femenina" else "masculina"} declarada en el plan · ${it.age} años." }
                    ?: "Sin referencia DRI: falta un perfil adulto compatible y una ecuación declarada en el plan.", style = KpknType.caption, color = WizardColors.textMuted)
            }
            if (state.rows.isEmpty()) item { Text("Calculando nutrientes…", style = KpknType.body, color = WizardColors.textMuted) }
            val rowsByKey = state.rows.associateBy { it.nutrient }
            groupedNutrientKeys().forEach { (group, keys) ->
                val groupRows = keys.mapNotNull { rowsByKey[it] }
                if (groupRows.isEmpty()) return@forEach
                item(key = "group-${group.name}") { Text(group.label, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.testTag("nutrient-group-${group.name}")) }
                items(groupRows, key = { it.nutrient.name }) { row ->
                    NutrientRow(row, DriTargets.lookup(row.nutrient, state.profile), GuideLimits.lookup(row.nutrient, state.profile), state.energyKcal, row.nutrient in expanded) { onToggle(row.nutrient) }
                }
            }
            item { Text("RDA: ingesta recomendada; AI: ingesta adecuada. Las vitaminas sin forma comparable quedan sin sumar. Referencia general de las Academias Nacionales de EE. UU.; no sustituye el consejo de un profesional.",
                style = KpknType.caption, color = WizardColors.textMuted) }
            item { Text("Datos de alimentos: USDA FoodData Central (dominio público), ANSES-Ciqual (Licence Ouverte 2.0) y Open Food Facts (ODbL). Detalle en Ajustes › Fuentes y licencias.",
                style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.testTag("nutrients-sources")) }
        }
    }
}

internal fun nutrientNumber(value: Double): String = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("es"))).format(value)
internal fun nutrientStatus(row: NutrientDayTotal): String = when (row.state) {
    NutrientCoverageState.COMPLETE -> if (row.estimatedCount + row.missingCount == 0) "Completo" else "Casi completo"
    NutrientCoverageState.PARTIAL -> "Parcial"
    NutrientCoverageState.NO_DATA -> "Sin datos"
}

@Composable
private fun NutrientRow(row: NutrientDayTotal, target: DriTarget?, guide: GuideLimit?, energyKcal: Double?, expanded: Boolean, onToggle: () -> Unit) {
    val key = row.nutrient
    val status = nutrientStatus(row)
    val total = row.totalKnown
    val aboveGuide = guide?.exceeds(total, energyKcal) == true
    val above = target?.exceedsKnownTotal(total) == true
    KpknModule(Modifier.fillMaxWidth().testTag("nutrient-${key.name}")) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = "$status; ${if (expanded) "contribuyentes abiertos" else "ver contribuyentes"}" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(key.label, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(total?.let { "${nutrientNumber(it)} ${key.unit} conocidos" } ?: "Sin datos comparables",
                style = KpknType.body, color = if (above || aboveGuide) WizardColors.danger else WizardColors.text)
            Text("$status · ${row.declaredCount} de ${row.declaredCount + row.estimatedCount + row.missingCount} ${if (row.declaredCount + row.estimatedCount + row.missingCount == 1) "alimento" else "alimentos"} con dato declarado",
                style = KpknType.caption, color = WizardColors.textMuted)
            if (row.estimatedCount > 0) Text("${row.estimatedCount} con datos estimados", style = KpknType.caption, color = WizardColors.textMuted)
            Text("Cobertura: ${nutrientNumber(row.declaredFraction * 100)} % ${if (row.weightedByEnergy) "de las kcal" else "por alimentos (sin kcal)"}",
                style = KpknType.caption, color = WizardColors.textMuted)
            if (!key.inCatalog && total == null) Text("Sin dato en el catálogo; una ficha propia puede aportarlo.", style = KpknType.caption, color = WizardColors.textMuted)
            if (row.missingCount > 0) Text("${row.missingCount} sin dato comparable (puede ser 0).", style = KpknType.caption, color = WizardColors.textMuted)
            if (target != null) {
                Text(if (key == NutrientKey.SODIUM) "Máximo recomendado: 2300 mg · AI: ${nutrientNumber(target.value)} mg"
                    else "Referencia ${target.kind.name}: ${nutrientNumber(target.value)} ${key.unit}", style = KpknType.caption, color = WizardColors.textMuted)
                if (total != null) LinearProgressIndicator(progress = { (total / target.comparisonValue).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Total conocido respecto a la referencia de ${key.label}" },
                    trackColor = KpknSurface.divider, color = if (above) WizardColors.danger else WizardColors.text)
                if (above) Text(if (key == NutrientKey.SODIUM) "El total conocido supera el máximo recomendado." else "El total conocido supera el límite superior de ingesta total.",
                    style = KpknType.caption, color = WizardColors.danger)
                target.upperLimit?.let { ul ->
                    Text(when (target.scope) {
                        UpperLimitScope.TOTAL -> "Límite superior total: ${nutrientNumber(ul)} ${target.upperLimitUnit}"
                        UpperLimitScope.SUPPLEMENTS_ONLY -> "Límite para suplementos o formas añadidas: ${nutrientNumber(ul)} ${target.upperLimitUnit}; no se compara con el total."
                        UpperLimitScope.PREFORMED_ONLY -> "Límite de retinol: ${nutrientNumber(ul)} ${target.upperLimitUnit}; no se compara con vitamina A total."
                        UpperLimitScope.NONE -> "Sin límite superior establecido."
                    }, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            if (guide != null) {
                val ceiling = guide.ceiling(energyKcal)
                val source = guideSourceLabel(guide)
                val percent = guide.maxEnergyFraction?.let { "${nutrientNumber(it * 100)} %" }
                Text(when {
                    guide.maxAmount != null -> "Tope de la guía: ${nutrientNumber(guide.maxAmount)} ${key.unit} · $source"
                    ceiling != null && energyKcal != null -> "Tope de la guía: ${nutrientNumber(ceiling)} ${key.unit} ($percent de ${nutrientNumber(energyKcal)} kcal registradas) · $source"
                    else -> "Tope de la guía: $percent de la energía del día · $source"
                }, style = KpknType.caption, color = WizardColors.textMuted)
                if (aboveGuide) Text("El total conocido supera el tope de la guía.", style = KpknType.caption, color = WizardColors.danger)
            }
            if (key == NutrientKey.NIACIN) Text("Las fuentes dan niacina preformada; la referencia está en equivalentes (incluye la que el cuerpo forma del triptófano), así que tu total real suele ser mayor.",
                style = KpknType.caption, color = WizardColors.textMuted)
            Text(if (expanded) "Ocultar contribuyentes" else "Ver contribuyentes", style = KpknType.caption, color = WizardColors.text)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("contributors-${key.name}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.contributors.isEmpty()) Text("Ningún alimento aporta un dato comparable.", style = KpknType.body, color = WizardColors.textMuted)
            row.contributors.forEach { item ->
                Text("${item.name}${if (item.groupedFoods > 1) " (${item.groupedFoods})" else ""}: ${nutrientNumber(item.amount)} ${key.unit}${if (item.estimated) " · estimado" else ""}",
                    style = KpknType.body, color = WizardColors.text)
            }
        }
    }
}

private fun guideSourceLabel(guide: GuideLimit): String = when {
    "efsa" in guide.sourceUrl -> "EFSA"
    "fda.gov" in guide.sourceUrl -> "FDA"
    else -> "OMS"
}
