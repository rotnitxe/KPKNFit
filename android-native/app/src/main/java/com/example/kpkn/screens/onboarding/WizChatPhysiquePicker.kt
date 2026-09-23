package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.physiqueLabelForSliderPos
import com.example.kpkn.screens.nutrition.WizardFemaleFrames
import com.example.kpkn.screens.nutrition.WizardMaleFrames
import com.example.kpkn.screens.nutrition.VerticalPhysiqueSlider
import kotlin.math.roundToInt

/** Reuses the nutrition wizard's 122 physique frames and interpolation, without
 * treating the picture as a measurement of the user's current body. */
@Composable
internal fun WizChatPhysiquePicker(initialSex: EerSex?, onChooseTarget: (Double) -> Unit) {
    var example by remember(initialSex) { mutableStateOf(initialSex) }
    var position by remember { mutableFloatStateOf(4f) }
    val percent = bodyFatForSliderPos(position)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Referencia visual para tu meta", color = WizChatTokens.text, fontWeight = FontWeight.SemiBold)
        Text("Elige qué ejemplos ver; esto no cambia tu género ni mide tu composición.", color = WizChatTokens.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EerSex.FEMALE to "Ejemplos ♀", EerSex.MALE to "Ejemplos ♂").forEach { (sex, label) ->
                TextButton(onClick = { example = sex }, modifier = Modifier.background(if (example == sex) WizChatTokens.optionSelected else WizChatTokens.option, RoundedCornerShape(15.dp))) {
                    Text(label, color = WizChatTokens.text)
                }
            }
        }
        if (example != null) {
            val frames = if (example == EerSex.FEMALE) WizardFemaleFrames else WizardMaleFrames
            val frame = (((position - 1f) / 6f) * (frames.size - 1)).roundToInt().coerceIn(0, frames.lastIndex)
            Row(Modifier.fillMaxWidth().height(265.dp).clip(RoundedCornerShape(18.dp)).background(WizChatTokens.option),
                verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(frames[frame]), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxSize().padding(8.dp))
                VerticalPhysiqueSlider(position, onPosChange = { position = it }, height = 232.dp,
                    hitWidth = 46.dp, tint = WizChatTokens.blue, withBorder = false)
            }
            Text(physiqueLabelForSliderPos(position), color = WizChatTokens.muted)
            Button(onClick = { onChooseTarget(percent) }, colors = ButtonDefaults.buttonColors(containerColor = WizChatTokens.blue,
                contentColor = Color(0xFF061725)), shape = RoundedCornerShape(15.dp)) {
                Text("Usar ~${percent.roundToInt()} % como meta")
            }
        }
    }
}
