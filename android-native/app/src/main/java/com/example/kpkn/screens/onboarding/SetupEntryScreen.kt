package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SetupEntryScreen(
    onStart: (mode: String, draftId: String?) -> Unit,
    onCompleted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    var candidates by remember { mutableStateOf<List<SetupDraftCandidate>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(settings.onboardingCompleted) {
        if (settings.onboardingCompleted) { onCompleted(); return@LaunchedEffect }
        loading = true
        candidates = runCatching { withContext(Dispatchers.IO) { SetupDraftResolver(persistenceFactory(context).database).listRecoverable() } }.getOrDefault(emptyList())
        selectedId = candidates.firstOrNull()?.draftId
        loading = false
    }
    if (settings.onboardingCompleted) return
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = WizChatTokens.background) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KPKN", color = WizChatTokens.text, fontWeight = FontWeight.Black)
                    Text("Un solo punto de partida para tu perfil, tu entrenamiento, tu alimentación y tus RINGS.", color = WizChatTokens.muted)
                }
            }
            if (loading) item { CircularProgressIndicator(color = WizChatTokens.orange) }
            if (candidates.isNotEmpty()) {
                item { Text("Configuraciones guardadas", color = WizChatTokens.orange, fontWeight = FontWeight.Bold) }
                items(candidates, key = { it.draftId }) { candidate ->
                    DraftCandidateCard(candidate, candidate.draftId == selectedId) { selectedId = candidate.draftId }
                }
            }
            item {
                androidx.compose.material3.Surface(
                    color = WizChatTokens.yellow.copy(alpha = .18f),
                    shape = WizChatTokens.optionShape,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable {
                        val selected = candidates.firstOrNull { it.draftId == selectedId }
                        if (selected?.scope == SetupDraftScope.UNKNOWN) onStart("FULL", null) else onStart(if (selectedId == null) "FULL" else "RESUME", selectedId)
                    },
                ) { Text(if (selectedId == null || candidates.firstOrNull { it.draftId == selectedId }?.scope == SetupDraftScope.UNKNOWN) "Comenzar configuración" else "Continuar configuración", color = WizChatTokens.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp)) }
            }
            if (selectedId != null) item {
                TextButton(onClick = { selectedId = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Comenzar una configuración nueva", color = WizChatTokens.muted) }
            }
            item {
                Text("También puedes abrir un alcance específico desde Inicio. Los borradores se mantienen separados por alcance y nunca se mezclan.", color = WizChatTokens.muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DraftCandidateCard(candidate: SetupDraftCandidate, selected: Boolean, onClick: () -> Unit) {
    val accent = when (candidate.scope) { SetupDraftScope.FULL, SetupDraftScope.LEGACY_RESUME, SetupDraftScope.UNKNOWN -> WizChatTokens.orange; SetupDraftScope.TRAINING_ONLY -> WizChatTokens.blue; SetupDraftScope.NUTRITION_ONLY -> WizChatTokens.green; SetupDraftScope.RINGS_ONLY -> WizChatTokens.yellow }
    androidx.compose.material3.Surface(color = if (selected) accent.copy(alpha = .22f) else Color.White.copy(alpha = .08f), shape = WizChatTokens.optionShape, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(scopeLabel(candidate.scope), color = WizChatTokens.text, fontWeight = FontWeight.Bold)
                Text("Revisión ${candidate.revision} · ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(candidate.updatedAtEpochMs))}", color = WizChatTokens.muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            if (selected) Text("✓", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

private fun scopeLabel(scope: SetupDraftScope): String = when (scope) { SetupDraftScope.FULL -> "Configuración completa"; SetupDraftScope.TRAINING_ONLY -> "Solo entrenamiento"; SetupDraftScope.NUTRITION_ONLY -> "Solo nutrición"; SetupDraftScope.RINGS_ONLY -> "Solo RINGS"; SetupDraftScope.LEGACY_RESUME -> "Configuración recuperable"; SetupDraftScope.UNKNOWN -> "Borrador compatible" }
