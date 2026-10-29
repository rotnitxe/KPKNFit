package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientDayTotal
import com.example.kpkn.domain.nutrition.NutrientFoodCoverage
import com.example.kpkn.domain.nutrition.NutrientGroup
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

/** Nutrientes del día: totales del motor, referencia del plan, kcal registradas y cobertura por alimento. */
data class NutrientsSheetState(
    val date: String = "",
    val rows: List<NutrientDayTotal> = emptyList(),
    val profile: DriProfile? = null,
    val energyKcal: Double? = null,
    val coverage: NutrientFoodCoverage = NutrientFoodCoverage.EMPTY,
) {
    /** La huella del día (`day:true`, sin destacados). */
    val footprint: NutrientFootprintModel = NutrientFootprints.fromTotals(rows, profile, energyKcal, highlights = 0, day = true)
    val list: NutrientListModel = NutrientFootprints.list(rows, profile, energyKcal, NutrientScope.DAY)
}

fun buildNutrientsSheetState(date: String, logs: List<NutritionLog>, profile: DriProfile?): NutrientsSheetState {
    val foods = logs.filter { it.status != NutritionStatus.PLANNED && it.date.take(10) == date }.flatMap { it.foods }
    return NutrientsSheetState(date, NutrientCoverage.summarize(foods), profile, foods.takeIf { it.isNotEmpty() }?.sumOf { it.calories },
        NutrientCoverage.foodCoverage(foods))
}

/**
 * El módulo «Vitaminas y minerales» del día (n-day), bajo el día de Nutrición (M-D1): cabecera con «Ver todos ›», la huella del día
 * (lo que llega al 100 % en tinta plena, lo que falta en tenue, el sodio pasado en rojo), cuatro cifras —al día, cerca (70–99 %), por
 * debajo y sobre el máx.— y la cobertura honesta del motor.
 */
@Composable
fun NutrientsDayModule(state: NutrientsSheetState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    KpknModule(
        modifier.fillMaxWidth().testTag("nutrients-day-module"),
        contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
    ) {
        NutrientModuleHeader(onOpen)
        Spacer(Modifier.height(8.dp))
        NutrientFootprint(state.footprint, height = NutrientFootprintHeights.Day)
        val counts = NutrientFootprints.dayCounts(state.footprint)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(counts.onTrack to "al día", counts.near to "cerca", counts.below to "por debajo", counts.overMax to "sobre el máx.").forEachIndexed { i, (value, label) ->
                NutrientKpi(value.toString(), label, Modifier.weight(1f),
                    valueColor = if (i == 3 && value > 0) WizardColors.danger else WizardColors.text)
            }
        }
        NutrientFootprints.dayModuleCoverageLine(state.coverage)?.let { NutrientCoverageLine(it) }
    }
}

/** La hoja «Nutrientes del día» (n-day-sheet). Tocar una fila abre la hoja de ese nutriente (n-nutrient) encima. */
@Composable
fun NutrientsSheet(state: NutrientsSheetState, onDismiss: () -> Unit, hazeState: HazeState? = null) {
    var open by rememberSaveable(state.date) { mutableStateOf<String?>(null) }
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, maxHeightFraction = 0.88f, stableHeightFraction = 0.88f) {
        NutrientsContent(state, onOpenNutrient = { open = it.name })
    }
    val total = open?.let { name -> state.rows.firstOrNull { it.nutrient.name == name } }
    if (total != null) {
        val context = remember(state.date) { NutrientFootprints.dayContext(state.date, LocalDate.now()) }
        NutrientDetailSheet(NutrientFootprints.detail(total, state.profile, state.energyKcal, context), onDismiss = { open = null }, hazeState = hazeState)
    }
}

/**
 * Contenido de la hoja del día: título, fecha y referencia, la cobertura en una línea, «A vigilar» arriba, los chips de grupo y una fila
 * por nutriente. Al pie, en letra pequeña, las fuentes y licencias y «No sustituye el consejo de un profesional» (M-D3).
 */
@Composable
fun NutrientsContent(state: NutrientsSheetState, onOpenNutrient: (NutrientKey) -> Unit, modifier: Modifier = Modifier) {
    var group by rememberSaveable { mutableStateOf(NutrientGroup.VITAMINS) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrients-list")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Text("Nutrientes del día", style = NutrientSheetTitleStyle, color = WizardColors.text,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { heading() })
            Text(NutrientFootprints.daySubtitle(state.date, state.profile), style = KpknType.caption, color = WizardColors.textMuted,
                modifier = Modifier.padding(bottom = 12.dp))
            NutrientFootprints.daySheetCoverageLine(state.coverage)?.let {
                Text(it, style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.padding(bottom = 4.dp).testTag("nutrients-coverage"))
            }
            if (state.list.watch.isNotEmpty()) {
                NutrientGroupLabel("A vigilar", Modifier.testTag("nutrients-watch"), color = WizardColors.danger)
                NutrientRowsColumn(state.list.watch, onOpenNutrient)
            }
        }
        NutrientGroupChips(group, { group = it }, Modifier.padding(top = 8.dp, bottom = 4.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            NutrientRowsColumn(state.list.rowsOf(group), onOpenNutrient)
            Text(NutrientFootprints.SOURCES_FOOTER, style = KpknType.caption, color = WizardColors.textFaint,
                modifier = Modifier.padding(top = 16.dp, bottom = 30.dp).testTag("nutrients-sources"))
        }
    }
}
