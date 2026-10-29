package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientDayTotal
import com.example.kpkn.domain.nutrition.NutrientFoodCoverage
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetTitle
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.ui.components.KpknSheet
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
 * El módulo «Vitaminas y minerales» del día (`npDay`), bajo el día de Nutrición: la huella radial del día con el recuento de objetivos
 * cubiertos al centro, la leyenda al lado y, abajo, la cobertura honesta del motor. Tocarlo abre la lista completa ([onOpen]).
 */
@Composable
fun NutrientsDayModule(state: NutrientsSheetState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = remember(state.date) { NutrientFootprints.dayContext(state.date, LocalDate.now()) }
    NutrientDayRadialModule(state.footprint, context, NutrientFootprints.dayModuleCoverageLine(state.coverage), onOpen, modifier)
}

/** La hoja de la lista completa del día. Tocar una fila abre la hoja de ese nutriente (n-nutrient) encima. */
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
 * Contenido de la lista (`npList`): título «Vitaminas y minerales», «Hoy · % de la referencia de tu perfil» y una fila por nutriente, grupo
 * tras grupo. Al pie, en letra pequeña, las fuentes y licencias y «No sustituye el consejo de un profesional» (M-D3: la atribución de
 * los datos es obligatoria).
 */
@Composable
fun NutrientsContent(state: NutrientsSheetState, onOpenNutrient: (NutrientKey) -> Unit, modifier: Modifier = Modifier) {
    val subtitle = remember(state.date, state.profile) { NutrientFootprints.daySheetSubtitle(state.date, state.profile, LocalDate.now()) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrients-list")) {
        LoggerSheetTitle("Vitaminas y minerales", Modifier.padding(horizontal = 18.dp), sub = subtitle)
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            NutrientGroupedRows(state.list, onOpenNutrient)
            Text(NutrientFootprints.SOURCES_FOOTER, style = LoggerText.cap, color = LoggerColors.faint,
                modifier = Modifier.padding(top = 16.dp, bottom = 30.dp).testTag("nutrients-sources"))
        }
    }
}
