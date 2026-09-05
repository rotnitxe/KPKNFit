package com.example.kpkn.screens.competitions

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.competitions.wizard.CompetitionWizardScreen

@Composable
fun CompetitionLogScreen(
    competitionId: String?,
    onBack: () -> Unit,
) {
    CompetitionWizardScreen(
        competitionId = competitionId,
        onBack = onBack,
    )
}
