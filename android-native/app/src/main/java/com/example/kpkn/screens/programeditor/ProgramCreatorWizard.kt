package com.example.kpkn.screens.programeditor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramCreatorWizard(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
    onProgramCreated: (programId: String) -> Unit,
    onCancel: () -> Unit,
) {
    val step = uiState.wizardStep
    val draft = uiState.programDraft
    var showPreview by remember { mutableStateOf(false) }

    val canContinue = when (step) {
        WizardStep.COVER -> !draft?.name.isNullOrBlank()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Salir")
                    }
                    Text(
                        text = wizardStepTitle(step),
                        fontSize = 24.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                WizardStepIndicator(
                    currentStep = step,
                    onStepClick = { target ->
                        if (wizardStepIndex(target) <= wizardStepIndex(step)) {
                            viewModel.setWizardStep(target)
                        }
                    },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    },
                    label = "program-wizard-step",
                ) { currentStep ->
                    when (currentStep) {
                        WizardStep.COVER -> CoverStep(
                            uiState = uiState,
                            viewModel = viewModel,
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(bottom = 120.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showPreview = true }, enabled = draft != null) {
                        Text("Preview")
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    WizardBottomBar(
                        step = step,
                        canContinue = canContinue,
                        onBack = viewModel::prevWizardStep,
                        onNext = viewModel::nextWizardStep,
                        onSave = {
                            val id = viewModel.saveProgram()
                            if (id != null) onProgramCreated(id)
                        },
                    )
                }
            }
        }
    }

    if (showPreview && draft != null) {
        ModalBottomSheet(onDismissRequest = { showPreview = false }) {
            ProgramPreviewSheet(
                uiState = uiState,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }

}

@Composable
private fun WizardBottomBar(
    step: WizardStep,
    canContinue: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    val isFirst = step == WizardStep.COVER
    val isLast = step == WizardStep.COVER

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isFirst) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Atrás")
                }
            }

            Button(
                onClick = if (isLast) onSave else onNext,
                enabled = canContinue,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isLast) "Crear programa" else "Continuar")
            }
        }
    }
}

private fun wizardStepIndex(step: WizardStep): Int = when (step) {
    WizardStep.COVER -> 0
}

private fun wizardStepTitle(step: WizardStep): String = when (step) {
    WizardStep.COVER -> "Portada, nombre y descripción"
}
