package com.example.kpkn.screens.competitions.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionTrophyHonor

@Composable
fun CompetitionWizardPlaceStep(
    record: CompetitionRecord,
    viewModel: CompetitionWizardViewModel,
    modifier: Modifier = Modifier,
) {
    val place = CompetitionPlaceHonors.parsePlace(record.placement)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MedalChoice("1", "Oro", GoldMetal, place == 1, Modifier.weight(1f)) { viewModel.setPlace(1) }
            MedalChoice("2", "Plata", SilverMetal, place == 2, Modifier.weight(1f)) { viewModel.setPlace(2) }
            MedalChoice("3", "Bronce", BronzeMetal, place == 3, Modifier.weight(1f)) { viewModel.setPlace(3) }
        }
        Text("Fuera del podio", color = WizardMuted, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompetitionPlaceHonors.trophies.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { trophy ->
                        TrophyTile(
                            trophy = trophy,
                            selected = place != null && place >= 4 && record.trophyId == trophy.id,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setPlace(4, trophy.id) },
                        )
                    }
                    if (row.size == 1) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MedalChoice(
    place: String,
    title: String,
    metal: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(WizardCardShape)
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(metal, metal.copy(alpha = 0.55f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(place, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Text(title, color = WizardInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun TrophyTile(
    trophy: CompetitionTrophyHonor,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(WizardCardShape)
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(trophy.label, color = WizardInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
