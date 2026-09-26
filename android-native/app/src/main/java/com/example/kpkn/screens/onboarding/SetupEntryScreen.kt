package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puerta de entrada a la configuración, con la paleta neutra del wizard
 * (`WizardColors.background` `#1F1F1F`, tarjetas y CTA del sistema de diseño).
 *
 * Se mantiene la composición existente: bienvenida con carrusel y sus copys,
 * lista de borradores recuperables solo lectura y estados de carga/error con el
 * fallo honesto (sin ocultarlo ni inventar datos). No se añade ningún elemento
 * visual nuevo.
 */
@Composable
fun SetupEntryScreen(
    onStart: (mode: String, draftId: String?) -> Unit,
    onCompleted: () -> Unit,
    onOpenVisualGate: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    var candidates by remember { mutableStateOf<List<SetupDraftCandidate>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showSavedList by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(settings.onboardingCompleted, retry) {
        if (settings.onboardingCompleted) { onCompleted(); return@LaunchedEffect }
        loading = true
        val recovered = runCatching { withContext(Dispatchers.IO) { SetupDraftResolver(persistenceFactory(context).database).listRecoverable() } }
        loadError = recovered.isFailure
        candidates = recovered.getOrDefault(emptyList())
        selectedId = candidates.firstOrNull()?.draftId
        loading = false
    }
    if (settings.onboardingCompleted) return
    if (loading) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = WizardColors.background) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WizardColors.text)
            }
        }
        return
    }
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
    if (candidates.isEmpty()) {
        SetupWelcomeScreen(
            onStart = { onStart("FULL", "setup-wizard:full:${UUID.randomUUID()}") },
            onOpenVisualGate = onOpenVisualGate,
        )
        return
    }
    if (!showSavedList) {
        SetupWelcomeScreen(
            actionLabel = "Continuar configuración",
            onStart = { onStart("RESUME", candidates.first().draftId) },
            secondaryLabel = "Comenzar una configuración nueva",
            onSecondary = { onStart("FULL", "setup-wizard:full:${UUID.randomUUID()}") },
            onDetails = if (candidates.size > 1) ({ showSavedList = true }) else null,
            onOpenVisualGate = onOpenVisualGate,
        )
        return
    }
    // Lista de borradores recuperables: solo lectura, con la misma paleta que
    // la bienvenida y el CTA blanco del sistema de diseño.
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = WizardColors.background) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TextButton(onClick = { showSavedList = false }) {
                    Text("Volver a bienvenida", color = WizardColors.textMuted)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KPKN", color = WizardColors.text, style = WizardTypography.cardTitle)
                    Text(
                        "Un solo punto de partida para tu perfil, tu entrenamiento, tu alimentación y tus RINGS.",
                        color = WizardColors.textMuted,
                        style = WizardTypography.bodySmall,
                    )
                }
            }
            if (candidates.isNotEmpty()) {
                item {
                    Text(
                        "Configuraciones guardadas",
                        color = WizardColors.textMuted,
                        style = WizardTypography.cardTitle,
                    )
                }
                items(candidates, key = { it.draftId }) { candidate ->
                    WizardChoiceCard(
                        title = scopeLabel(candidate.scope),
                        subtitle = "Revisión ${candidate.revision} · ${draftDate(candidate.updatedAtEpochMs)}",
                        selected = candidate.draftId == selectedId,
                        onClick = { selectedId = candidate.draftId },
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WizardSpacing.ctaHeight)
                        .clip(WizardShapes.cta)
                        .background(WizardColors.cta)
                        .clickable {
                            val selected = candidates.firstOrNull { it.draftId == selectedId }
                            if (selectedId == null) onStart("FULL", "setup-wizard:full:${UUID.randomUUID()}")
                            else if (selected != null) onStart("RESUME", selected.draftId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (selectedId == null) "Comenzar de nuevo" else "Continuar configuración",
                        style = WizardTypography.cta,
                        color = WizardColors.ctaContent,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    )
                }
            }
            if (selectedId != null) item {
                TextButton(
                    onClick = { selectedId = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Comenzar una configuración nueva", color = WizardColors.textMuted)
                }
            }
        }
    }
}

private fun draftDate(epochMs: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))

private fun scopeLabel(scope: SetupDraftScope): String = when (scope) {
    SetupDraftScope.FULL -> "Configuración completa"
    SetupDraftScope.TRAINING_ONLY -> "Solo entrenamiento"
    SetupDraftScope.NUTRITION_ONLY -> "Solo nutrición"
    SetupDraftScope.RINGS_ONLY -> "Solo RINGS"
    SetupDraftScope.LEGACY_RESUME -> "Configuración recuperable"
    SetupDraftScope.UNKNOWN -> "Borrador compatible"
}
