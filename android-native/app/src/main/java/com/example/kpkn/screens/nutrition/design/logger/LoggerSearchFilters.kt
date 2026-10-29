package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodCandidate

/** Los filtros rápidos de los resultados (lienzo s-results): todo, mis alimentos, con marca y sin marca. */
enum class LoggerSearchFilter(val label: String) {
    ALL("Todo"),
    MINE("Míos"),
    BRANDED("Con marca"),
    UNBRANDED("Sin marca"),
}

/**
 * Los [candidates] que pasan el [filter] (S-D5: filtra la lista que ya devolvió la búsqueda, la consulta no cambia). «Míos» son los
 * alimentos propios y las recetas; «Con marca» los que traen una marca no vacía.
 */
fun filterLoggerCandidates(candidates: List<FoodCandidate>, filter: LoggerSearchFilter): List<FoodCandidate> = when (filter) {
    LoggerSearchFilter.ALL -> candidates
    LoggerSearchFilter.MINE -> candidates.filter { it.food.isCustom || it.food.recipe != null }
    LoggerSearchFilter.BRANDED -> candidates.filter { hasBrand(it) }
    LoggerSearchFilter.UNBRANDED -> candidates.filter { !hasBrand(it) }
}

private fun hasBrand(candidate: FoodCandidate): Boolean = !candidate.food.brand.isNullOrBlank()

/**
 * Fila de chips de 32 dp con 6 dp de hueco bajo el campo (`margin:12px 0 4px`). La zona táctil de cada chip es de 48 dp, así que el margen
 * visible se consigue con 4 dp arriba y ninguno abajo.
 */
@Composable
fun LoggerSearchFilters(
    selected: LoggerSearchFilter,
    onSelect: (LoggerSearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LoggerSearchFilter.entries.forEach { filter ->
            LoggerChip(text = filter.label, selected = filter == selected, onClick = { onSelect(filter) }, height = 32.dp)
        }
    }
}
