package com.example.kpkn.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.repository.ProgramRepository

/**
 * Editor de programa DIRECTO (post-alta): nombre, modo y portada en un solo
 * formulario, sin pasos ni wizard. Un alta nueva no persiste hasta [save]; el
 * guardado abre el detalle del programa (nunca auto-activa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(
    programId: String?,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProgramEditorViewModel = rememberProgramEditorViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(programId) { viewModel.initialize(programId) }
    LaunchedEffect(state.savedProgramId) {
        val savedId = state.savedProgramId
        if (savedId != null) onSaved(savedId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Text(
                        if (state.isEditMode) "Editar programa" else "Nuevo programa",
                        fontWeight = FontWeight.Black,
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && !state.isLoading && !state.missingProgram,
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }
            state.missingProgram -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("El programa ya no existe.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Volver") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() / 2 + 8.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        EditorSectionCard("Nombre y descripción") {
                            BasicInfoSection(state, viewModel)
                        }
                    }
                    item {
                        EditorSectionCard("Modalidad") {
                            ModeSection(state, viewModel)
                        }
                    }
                    item {
                        EditorSectionCard("Portada") {
                            CoverSection(state, viewModel)
                        }
                    }
                    state.error?.let { message ->
                        item {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { viewModel.save() },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(20.dp).height(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Guardar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberProgramEditorViewModel(): ProgramEditorViewModel {
    // El repository es singleton; la inyección manual es el estándar del proyecto.
    return viewModel {
        ProgramEditorViewModel(repository = ProgramRepository.getInstance())
    }
}

@Composable
private fun EditorSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun BasicInfoSection(state: ProgramEditorUiState, viewModel: ProgramEditorViewModel) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { viewModel.setName(it) },
        label = { Text("Nombre del programa") },
        singleLine = true,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = { viewModel.setDescription(it) },
        label = { Text("Descripción (opcional)") },
        minLines = 2,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeSection(state: ProgramEditorUiState, viewModel: ProgramEditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ProgramMode.POWERLIFTING to "Powerlifting",
            ProgramMode.HYPERTROPHY to "Hipertrofia",
            ProgramMode.POWERBUILDING to "Powerbuilding",
        ).forEach { (mode, label) ->
            FilterChip(
                selected = state.mode == mode,
                onClick = { viewModel.setMode(mode) },
                label = { Text(label) },
                enabled = !state.isSaving,
            )
        }
    }
}

private data class CoverOption(val cover: String, val label: String)

@Composable
private fun CoverSection(state: ProgramEditorUiState, viewModel: ProgramEditorViewModel) {
    val options = listOf(
        CoverOption("gradient://ember", "Ember"),
        CoverOption("gradient://lagoon", "Lagoon"),
        CoverOption("gradient://velvet", "Velvet"),
        CoverOption("gradient://forest", "Forest"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(programCoverBrush(option.cover), RoundedCornerShape(14.dp))
                        .then(
                            if (state.coverImage == option.cover) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            } else {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            }
                        )
                        .semantics {
                            contentDescription = option.label
                            role = Role.RadioButton
                        }
                        .clickable(enabled = !state.isSaving) { viewModel.setCoverImage(option.cover) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.coverImage == option.cover) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
                Text(option.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}