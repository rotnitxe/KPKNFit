package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bienvenida de la configuración. Lo ya respondido se retoma solo al pulsar
 * Comenzar; no hay lista de borradores ni pantalla de carga intermedia.
 */
@Composable
fun SetupEntryScreen(
    onStart: (mode: String, draftId: String?) -> Unit,
    onCompleted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    var candidates by remember { mutableStateOf<List<SetupDraftCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var pendingStart by remember { mutableStateOf(false) }
    LaunchedEffect(settings.onboardingCompleted, retry) {
        if (settings.onboardingCompleted) { onCompleted(); return@LaunchedEffect }
        loading = true
        val recovered = runCatching { withContext(Dispatchers.IO) { SetupDraftResolver(persistenceFactory(context).database).listRecoverable() } }
        loadError = recovered.isFailure
        candidates = recovered.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(pendingStart, loading, loadError, candidates) {
        if (!pendingStart || loading || loadError) return@LaunchedEffect
        pendingStart = false
        val saved = candidates.maxByOrNull { it.updatedAtEpochMs }
        if (saved != null) onStart("RESUME", saved.draftId)
        else onStart("FULL", "setup-wizard:full:${UUID.randomUUID()}")
    }
    if (settings.onboardingCompleted) return
    if (loadError) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = WizardColors.background) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No pude cargar tu configuración", color = WizardColors.text, style = WizardTypography.cardTitle)
                TextButton(onClick = { retry++ }) { Text("Reintentar", color = WizardColors.text) }
            }
        }
        return
    }
    SetupWelcomeScreen(onStart = { pendingStart = true })
}
