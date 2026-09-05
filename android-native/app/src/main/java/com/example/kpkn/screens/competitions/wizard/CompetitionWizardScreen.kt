package com.example.kpkn.screens.competitions.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.ui.components.KpknSheetContentTheme
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknGlassOrFallback

private val WizardBg = Color(0xFF000000)

@Composable
fun CompetitionWizardScreen(
    competitionId: String?,
    onBack: () -> Unit,
    viewModel: CompetitionWizardViewModel = viewModel(key = competitionId ?: "new") {
        CompetitionWizardViewModel(competitionId)
    },
) {
    val ready by viewModel.ready.collectAsState()
    val record by viewModel.draft.collectAsState()
    val step by viewModel.step.collectAsState()

    BackHandler {
        if (!viewModel.goBack()) onBack()
    }

    KpknSheetContentTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WizardBg)
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "POWERLIFTING",
                        color = WizardMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stepQuestion(step),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((step.ordinal + 1) / CompetitionWizardStep.entries.size.toFloat())
                                .height(3.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White),
                        )
                    }
                }

                if (!ready) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    AnimatedContent(
                        targetState = step,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { -it / 5 } + fadeOut(tween(180)))
                        },
                        label = "plWizardStep",
                    ) { current ->
                        when (current) {
                            CompetitionWizardStep.EVENT -> CompetitionWizardEventStep(record, viewModel)
                            CompetitionWizardStep.LIFTS -> CompetitionWizardLiftsStep(record, viewModel)
                            CompetitionWizardStep.PLACE -> CompetitionWizardPlaceStep(record, viewModel)
                            CompetitionWizardStep.ALBUM -> CompetitionWizardAlbumStep(record, viewModel)
                        }
                    }
                }

                val last = step == CompetitionWizardStep.ALBUM
                val enabled = ready && viewModel.canContinue()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kpknGlassOrFallback(null, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step != CompetitionWizardStep.EVENT) {
                        TextButton(onClick = { viewModel.goBack() }, modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.size(6.dp))
                            Text("Atrás", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (last) {
                                viewModel.saveCompleted()
                                onBack()
                            } else {
                                viewModel.goNext()
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = enabled,
                        shape = WizardFieldShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KpknSheetTokens.ControlFill,
                            contentColor = KpknSheetTokens.ControlLabel,
                            disabledContainerColor = Color.White.copy(alpha = 0.14f),
                            disabledContentColor = WizardMuted,
                        ),
                    ) {
                        Text(
                            if (last) "Guardar" else "Continuar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.size(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD64545),
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = WizardFieldShape,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            }
        }
    }
}

private fun stepQuestion(step: CompetitionWizardStep): String = when (step) {
    CompetitionWizardStep.EVENT -> "¿Dónde levantaste?"
    CompetitionWizardStep.LIFTS -> "¿Cómo fueron los intentos?"
    CompetitionWizardStep.PLACE -> "¿Qué lugar ocupaste?"
    CompetitionWizardStep.ALBUM -> "¿Quieres guardar recuerdos?"
}
