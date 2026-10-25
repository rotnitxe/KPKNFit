package com.example.kpkn.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.screens.habits.design.HabitCalendarModule
import com.example.kpkn.screens.habits.design.HabitStreakModule
import com.example.kpkn.screens.habits.design.habitEmptyBody
import com.example.kpkn.screens.habits.design.habitEmptyTitle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/** Ruta de Hábitos: crea el ViewModel con los repositorios de la app y pinta [HabitsScreen]. */
@Composable
fun HabitsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HabitsViewModel = viewModel { habitsViewModelFor(context) }
    val state by viewModel.uiState.collectAsState()
    HabitsScreen(state = state, onSelectHabit = viewModel::select, onBack = onBack)
}

/** Pantalla de Hábitos, sin estado propio: rachas, calendario anual y selector del hábito. */
@Composable
fun HabitsScreen(
    state: HabitsUiState,
    onSelectHabit: (HabitKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.snapshot?.summary(state.selected)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        KpknTopRow(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Hábitos",
                style = KpknType.display,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Rachas y calendario del último año.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            KpknSegmentedControl(
                options = listOf("Comidas", "Pesaje", "Entreno"),
                selectedIndex = state.selected.ordinal,
                onSelect = { onSelectHabit(HabitKind.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
                descriptions = listOf("Registro de comidas", "Pesaje", "Entrenamiento"),
            )
            HabitStreakModule(kind = state.selected, summary = summary)
            if (summary != null) {
                if (summary.recordedInWindow == 0) {
                    KpknEmptyState(
                        icon = KpknLineIcons.Calendar,
                        title = habitEmptyTitle(state.selected),
                        body = habitEmptyBody(state.selected),
                    )
                } else {
                    HabitCalendarModule(kind = state.selected, summary = summary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
