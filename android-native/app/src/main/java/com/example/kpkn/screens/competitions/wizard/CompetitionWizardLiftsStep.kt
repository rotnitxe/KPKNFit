package com.example.kpkn.screens.competitions.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.domain.competitions.CompetitionExerciseTypeahead
import com.example.kpkn.domain.competitions.CompetitionScoring
import com.example.kpkn.ui.components.KpknSheetTokens

@Composable
fun CompetitionWizardLiftsStep(
    record: CompetitionRecord,
    viewModel: CompetitionWizardViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WizardFieldShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(viewModel.livePointsLabel(), color = WizardInk, fontWeight = FontWeight.Bold)
        }
        record.technicalBlocks.forEach { block ->
            LiftCard(
                block = block,
                onBind = { viewModel.bindExercise(block.id, it) },
                onCustom = { viewModel.useCustomExerciseName(block.id, it) },
                suggestions = { query -> viewModel.suggestions(query) },
                onCycle = { attempt -> viewModel.cycleAttemptResult(block.id, attempt.attemptNumber) },
                onMinus = { attempt -> viewModel.nudgeAttemptWeight(block.id, attempt.attemptNumber, -2.5) },
                onPlus = { attempt -> viewModel.nudgeAttemptWeight(block.id, attempt.attemptNumber, 2.5) },
            )
        }
        TextButton(onClick = viewModel::addExtraLift) {
            Text("Añadir otro movimiento", color = WizardInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiftCard(
    block: CompetitionTechnicalBlock,
    onBind: (com.example.kpkn.data.models.ExerciseMuscleInfo) -> Unit,
    onCustom: (String) -> Unit,
    suggestions: (String) -> List<com.example.kpkn.domain.competitions.CompetitionExerciseSuggestion>,
    onCycle: (CompetitionAttempt) -> Unit,
    onMinus: (CompetitionAttempt) -> Unit,
    onPlus: (CompetitionAttempt) -> Unit,
) {
    var query by remember(block.id, block.exerciseName) { mutableStateOf(block.exerciseName.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    var selectedAttempt by remember(block.id) { mutableIntStateOf(1) }
    val hits = remember(query, focused) {
        if (query.isBlank()) emptyList() else suggestions(query)
    }
    val attempts = block.attempts.sortedBy { it.attemptNumber }
    val current = attempts.firstOrNull { it.attemptNumber == selectedAttempt } ?: attempts.firstOrNull()

    WizardPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(block.title, color = WizardInk, fontWeight = FontWeight.Black, fontSize = 18.sp)
            WizardPillField(
                value = query,
                onValueChange = {
                    query = it
                    focused = true
                },
                placeholder = "Nombre del movimiento",
            )
            if (focused && query.isNotBlank()) {
                hits.forEach { hit ->
                    Text(
                        hit.exercise.name,
                        color = WizardInk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WizardFieldShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                onBind(hit.exercise)
                                query = hit.exercise.name
                                focused = false
                            }
                            .padding(12.dp),
                    )
                }
                if (!CompetitionExerciseTypeahead.hasExactCatalogMatch(query, hits)) {
                    Text(
                        "Usar “${query.trim()}”",
                        color = KpknSheetTokens.ControlLabel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(WizardFieldShape)
                            .background(KpknSheetTokens.ControlFill)
                            .clickable {
                                onCustom(query.trim())
                                focused = false
                            }
                            .padding(12.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                attempts.forEach { attempt ->
                    AttemptChip(
                        attempt = attempt,
                        selected = attempt.attemptNumber == selectedAttempt,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (attempt.attemptNumber == selectedAttempt) {
                                onCycle(attempt)
                            } else {
                                selectedAttempt = attempt.attemptNumber
                            }
                        },
                    )
                }
            }
            if (current != null) {
                WizardStepper(
                    valueLabel = current.weightKg?.let { "${CompetitionScoring.formatKg(it)} kg" } ?: "Peso",
                    onMinus = { onMinus(current) },
                    onPlus = { onPlus(current) },
                )
            }
            val best = CompetitionScoring.bestValidKg(block)
            if (best != null) {
                Text("Mejor válido ${CompetitionScoring.formatKg(best)} kg", color = WizardMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AttemptChip(
    attempt: CompetitionAttempt,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ring = attemptLight(attempt.resultType)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(3.dp, ring, CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                attempt.weightKg?.let { CompetitionScoring.formatKg(it) } ?: "—",
                color = WizardInk,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
            )
        }
        Text("${attempt.attemptNumber}", color = WizardMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(attemptLabel(attempt.resultType), color = WizardMuted, fontSize = 11.sp)
    }
}

private fun attemptLight(result: CompetitionAttemptResult): Color = when (result) {
    CompetitionAttemptResult.GOOD_LIFT -> Color(0xFF3D8F6A)
    CompetitionAttemptResult.NO_LIFT -> Color(0xFF9A4444)
    CompetitionAttemptResult.SKIPPED -> Color.White.copy(alpha = 0.16f)
    CompetitionAttemptResult.PENDING -> Color.White.copy(alpha = 0.40f)
}

private fun attemptLabel(result: CompetitionAttemptResult): String = when (result) {
    CompetitionAttemptResult.GOOD_LIFT -> "Válido"
    CompetitionAttemptResult.NO_LIFT -> "Nulo"
    CompetitionAttemptResult.SKIPPED -> "Saltado"
    CompetitionAttemptResult.PENDING -> "Pendiente"
}
