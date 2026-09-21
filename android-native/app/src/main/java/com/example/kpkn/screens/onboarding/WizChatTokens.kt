package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatStage

object WizChatTokens {
    val background = Color(0xFF0F0F0F)
    val text = Color(0xFFF6F2EE)
    val muted = Color(0xFFB9B0A9)
    val orange = Color(0xFFFFAB66)
    val blue = Color(0xFF74B5FF)
    val green = Color(0xFF6CDBA4)
    val yellow = Color(0xFFF4D35E)
    val danger = Color(0xFFFF9B92)
    val bubbleShape = RoundedCornerShape(20.dp)
    val dockShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val optionShape = RoundedCornerShape(18.dp)
    const val bubbleAlpha = 0.16f
    const val selectedAlpha = 0.26f
}

fun WizChatStage.accent(): Color = when (this) {
    WizChatStage.PROFILE -> WizChatTokens.orange
    WizChatStage.TRAINING -> WizChatTokens.blue
    WizChatStage.NUTRITION -> WizChatTokens.green
    WizChatStage.RINGS -> WizChatTokens.yellow
    WizChatStage.REVIEW -> WizChatTokens.orange
}
