package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatStage

object WizChatTokens {
    val background = Color.Black
    val text = Color(0xFFF4F6F8)
    val muted = Color(0xFFAEB7C4)
    val botBubble = Color(0xFF191D23)
    val userBubble = Color(0xFF14334F)
    val replyZone = Color(0xFF151A21)
    val option = Color(0xFF282F39)
    val optionSelected = Color(0xFF203E5D)
    val blue = Color(0xFF74B5FF)
    val orange = Color(0xFFFFA15C)
    val green = Color(0xFF6EDB9A)
    val yellow = Color(0xFFF0D36A)
    val danger = Color(0xFFFF9B92)
    val optionShape = RoundedCornerShape(18.dp)
    val replyShape = RoundedCornerShape(20.dp)

    /** Stage accent: orange profile, blue training, green nutrition, yellow rings, blue review. */
    fun stageAccent(stage: WizChatStage): Color = when (stage) {
        WizChatStage.PROFILE -> orange
        WizChatStage.TRAINING -> blue
        WizChatStage.NUTRITION -> green
        WizChatStage.RINGS -> yellow
        WizChatStage.REVIEW -> blue
    }
}
