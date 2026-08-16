package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionCalibrationScreen(
    onBack: () -> Unit,
    viewModel: NutritionCalibrationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibración conservadora", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val wizard = state.wizard
                var answer by remember(wizard.step, wizard.profile.lastWizardUpdatedAtEpochMs) {
                    mutableStateOf("")
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Calibrar tus hábitos", fontWeight = FontWeight.Black)
                        Text("Puedes omitirlo y reanudarlo después. Solo se guardan respuestas confirmadas; una estimación no entrena el perfil.")
                        Text(
                            "Paso ${wizard.step.index + 1} de ${NutritionCalibrationWizardStep.entries.size}: ${wizard.step.title}",
                            fontWeight = FontWeight.Bold,
                        )
                        when (wizard.step) {
                            NutritionCalibrationWizardStep.WEIGHING_CONVENTION -> {
                                Text("¿Cómo pesas normalmente los alimentos?")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("RAW" to "Crudo", "COOKED" to "Cocido", "DEPENDS" to "Depende").forEach { (value, label) ->
                                        FilterChip(
                                            selected = wizard.profile.weighingConvention == value,
                                            onClick = { viewModel.answerWizard(value) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                            }
                            NutritionCalibrationWizardStep.REVIEW -> {
                                Text("Revisa y guarda el perfil cuando estés conforme.")
                                Text("Pesaje: ${wizard.profile.weighingConvention ?: "sin definir"}")
                                Text("Porciones maduras: ${wizard.profile.maturePortionsGrams.size}")
                                Button(
                                    onClick = { viewModel.answerWizard("listo") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Guardar perfil") }
                            }
                            else -> {
                                Text(
                                    when (wizard.step) {
                                        NutritionCalibrationWizardStep.UTENSILS -> "Ejemplo: taza=240,vaso=250,bol=400,plato=300"
                                        NutritionCalibrationWizardStep.STAPLE_PORTIONS -> "Ejemplo: arroz=150,pasta=180,papas=200,legumbres=180"
                                        NutritionCalibrationWizardStep.PROTEIN_PORTIONS -> "Ejemplo: pollo=180,vacuno=160,pescado=170"
                                        NutritionCalibrationWizardStep.PREPARATION_OIL -> "Ejemplo: plancha=3,horno=2,fritura=10,air_fryer=0"
                                        else -> ""
                                    },
                                )
                                OutlinedTextField(
                                    value = answer,
                                    onValueChange = { answer = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Respuesta confirmada") },
                                    singleLine = true,
                                )
                                Button(
                                    onClick = {
                                        viewModel.answerWizard(answer.ifBlank { "confirmado" })
                                        answer = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = answer.isNotBlank(),
                                ) { Text("Confirmar y continuar") }
                            }
                        }
                        if (!wizard.isComplete) {
                            TextButton(onClick = viewModel::skipWizard, modifier = Modifier.fillMaxWidth()) {
                                Text("Omitir por ahora")
                            }
                        } else {
                            TextButton(onClick = viewModel::resumeWizard, modifier = Modifier.fillMaxWidth()) {
                                Text("Editar y reanudar")
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Ajusta el objetivo solo con tendencia real. Se requieren al menos 14 días, 7 pesajes y 10 días completos; cada ajuste queda limitado a ±150 kcal.",
                )
            }
            if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            profile?.let { current ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Estado: ${current.status}", fontWeight = FontWeight.Bold)
                            Text("Cobertura: ${current.observedDays}/${current.targetDays} días")
                            Text("Días completos: ${current.completeDays} · Pesajes: ${current.weightReadings}")
                            current.recommendedAdjustmentKcal?.let { adjustment ->
                                Text("Ajuste sugerido: ${if (adjustment >= 0) "+" else ""}$adjustment kcal")
                            } ?: Text("Aún no hay muestra suficiente para ajustar")
                        }
                    }
                }
            }
            state.error?.let { error -> item { Text("No se pudo calibrar: $error") } }
            item {
                Button(onClick = viewModel::evaluate, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) {
                    Text("Evaluar mis últimos datos")
                }
            }
            item {
                androidx.compose.material3.TextButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("Restablecer calibración")
                }
            }
        }
    }
}
