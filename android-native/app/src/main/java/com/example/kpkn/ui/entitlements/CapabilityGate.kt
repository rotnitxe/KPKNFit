package com.example.kpkn.ui.entitlements

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import com.example.kpkn.domain.entitlements.*

@Composable
fun CapabilityGate(capability: Capability, onPro: () -> Unit, onHistory: (() -> Unit)? = null,
    onBack: () -> Unit, content: @Composable () -> Unit) {
    if (CapabilityCatalog.permits(capability, LocalPlan.current)) {
        content()
    } else {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.capability_pro_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.capability_pro_body))
            Button(onClick = onPro) { Text(stringResource(R.string.capability_view_pro)) }
            onHistory?.let { TextButton(onClick = it) { Text(stringResource(R.string.capability_history)) } }
            TextButton(onClick = onBack) { Text(stringResource(R.string.capability_back)) }
        }
    }
}

fun capabilityErrorText(code: String): String {
    val resource = if (code in setOf("advanced_program_requires_pro", "free_weekly_session_limit"))
        R.string.capability_free_limit else R.string.capability_pro_body
    return if (com.example.kpkn.data.account.ProfileStorage.initialized())
        com.example.kpkn.data.account.ProfileStorage.platformContext().getString(resource) else "This change requires Pro. Your history is preserved."
}
