package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WizChatAdvancedHost(open: Boolean, onOpen: () -> Unit, content: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onOpen) { Text(if (open) "Ocultar ajuste avanzado" else "Abrir ajuste avanzado", color = WizChatTokens.blue) }
        if (open) content()
    }
}
