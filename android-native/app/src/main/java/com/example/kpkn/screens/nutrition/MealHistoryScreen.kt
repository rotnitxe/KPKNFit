package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.nutrition.design.NutritionSubpageHeader
import com.example.kpkn.screens.nutrition.design.NutritionTopIsland
import com.example.kpkn.screens.nutrition.design.history.MealHistoryMonthModule
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.rememberIslandVisible
import com.example.kpkn.ui.locale.LocaleManager

/** Filas de margen antes del final de la lista a partir de las que se piden más meses. */
private const val LOAD_MORE_MARGIN_ITEMS = 3

/**
 * Historial de comidas por meses (N5, NUTRITION_V1 §5): una página propia con «Atrás», «Historial» en `display` e isla al hacer scroll.
 * Los días llegan ya resueltos del [MealHistoryViewModel] y la lista pide más meses sola al llegar al final. Tocar un día abre su detalle;
 * «Ir a este día» vuelve a Nutrición con esa fecha ([onOpenDay]) y el estado vacío ofrece «Registrar comida» ([onRegister]).
 */
@Composable
fun MealHistoryScreen(
    onBack: () -> Unit,
    onOpenDay: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val locale = LocaleManager.getEffectiveLocale(LocalContext.current)
    val viewModel: MealHistoryViewModel = viewModel(factory = MealHistoryViewModelFactory(locale))
    val months by viewModel.months.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    MealHistoryContent(
        months = months,
        isLoading = isLoading,
        hasMore = hasMore,
        onBack = onBack,
        onOpenDay = onOpenDay,
        onRegister = onRegister,
        onLoadMore = viewModel::loadMore,
    )
}

/**
 * La página del historial sin su ViewModel: la lista, la cabecera, la isla y la hoja del día. Va separada para montarla con datos de
 * ejemplo en las pruebas y en la hoja de render.
 */
@Composable
internal fun MealHistoryContent(
    months: List<MealHistoryMonth>,
    isLoading: Boolean,
    hasMore: Boolean,
    onBack: () -> Unit,
    onOpenDay: (String) -> Unit,
    onRegister: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val islandVisible = rememberIslandVisible(listState, afterItemIndex = 0)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Día abierto en el detalle; null si no hay hoja.
    var openDay by remember { mutableStateOf<MealHistoryDay?>(null) }

    // La lista pide más meses sola: si la última fila visible está cerca del final, se suma una tanda.
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - LOAD_MORE_MARGIN_ITEMS
        }
    }
    // Al crecer la lista, la clave cambia y se vuelve a mirar si sigue faltando margen (la pantalla puede no llenarse con una tanda).
    val totalItems by remember(listState) { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    LaunchedEffect(nearEnd, totalItems, hasMore) {
        if (nearEnd && hasMore) onLoadMore()
    }

    Box(modifier = Modifier.fillMaxSize().background(KpknSurface.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "meal-history-header") {
                NutritionSubpageHeader(title = "Historial", topInset = topInset, onBack = onBack)
            }
            when {
                isLoading -> {
                    item(key = "meal-history-skeleton-1") {
                        KpknSkeletonModule(lines = 3, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    item(key = "meal-history-skeleton-2") {
                        KpknSkeletonModule(lines = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
                months.isEmpty() -> {
                    item(key = "meal-history-empty") {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
                            KpknEmptyState(
                                icon = KpknLineIcons.Nutrition,
                                title = "Aún no hay comidas registradas",
                                body = "Cada día con comidas aparece aquí, ordenado por mes.",
                                action = { KpknPrimaryPill(text = "Registrar comida", onClick = onRegister) },
                            )
                        }
                    }
                }
                else -> {
                    months.forEachIndexed { index, month ->
                        item(key = "meal-history-${month.month}") {
                            MealHistoryMonthModule(month = month, index = index, onOpen = { openDay = it })
                        }
                    }
                }
            }
        }

        NutritionTopIsland(visible = islandVisible, title = "Historial", subtitle = null)

        openDay?.let { day ->
            MealHistoryDaySheet(
                day = day,
                onDismiss = { openDay = null },
                onGoToDay = onOpenDay,
            )
        }
    }
}
