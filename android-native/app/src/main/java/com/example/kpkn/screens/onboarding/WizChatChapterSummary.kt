package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatStage
import com.example.kpkn.domain.onboarding.WizChatCopyCatalog

@Composable
fun WizChatChapterSummary(stage: WizChatStage, completed: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(if (completed) "Completado" else "En curso", color = stage.accent(), fontWeight = FontWeight.Bold)
        Text(WizChatCopyCatalog.progressLabel(stage), color = WizChatTokens.muted)
    }
}
