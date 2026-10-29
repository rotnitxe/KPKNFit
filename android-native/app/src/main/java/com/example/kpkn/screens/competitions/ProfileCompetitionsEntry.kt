package com.example.kpkn.screens.competitions

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.screens.profile.ProfileIconBubble
import com.example.kpkn.screens.profile.ProfileModule
import com.example.kpkn.screens.profile.ProfileRow

/** La única fila de competencias en Perfil (C-D1): la próxima y su cuenta atrás, y abre la página «Competencias». */
@Composable
fun ProfileCompetitionsEntry(onOpen: () -> Unit) {
    val summary = rememberCompetitionsSummary()
    ProfileModule {
        ProfileRow(
            title = "Competencias",
            subtitle = summary.ifBlank { null },
            onClick = onOpen,
            leading = { ProfileIconBubble(CompetitionIcons.Trophy) },
        )
    }
}
