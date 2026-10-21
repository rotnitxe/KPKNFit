package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknType
import kotlin.math.sin

@Composable
internal fun RestPlayHighlight(onClick: () -> Unit, animationsOn: Boolean, compact: Boolean = false) {
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(animationsOn && visibility.visible && !wizardReducedMotion())
    val glow = .24f + .12f * sin(clock.seconds * 1.8f)
    Row(
        Modifier.fillMaxWidth().height(if (compact) 44.dp else 50.dp)
            .then(visibility.modifier).clip(RoundedCornerShape(25.dp))
            .background(Color(0xFF1B1B1B))
            .border(1.dp, Color(0xFFF49A6E).copy(alpha = glow), RoundedCornerShape(25.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp).testTag("rest-play"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFF49A6E).copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Play, null, Modifier.size(15.dp), tint = Color(0xFFF49A6E))
        }
        Spacer(Modifier.width(10.dp))
        Text("Jugar mientras descansas", style = KpknType.action, color = Color(0xFFF2EEE6))
    }
}

/** The host persists the one-time acknowledgement; this surface never starts or resets rest. */
@Composable
internal fun RestFirstExperience(onDone: () -> Unit, animationsOn: Boolean) {
    val clock = rememberRestClock(animationsOn && !wizardReducedMotion())
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp).testTag("rest-first-experience"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RestRingsClock(
            shape = RestClockShape.TOWER, progress = .75f, runnerScale = 0f,
            animationsOn = animationsOn,
            modifier = Modifier.size(240.dp).graphicsLayer {
                val breath = 1f + .025f * sin(clock.seconds * 1.6f)
                scaleX = breath; scaleY = breath
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "El descanso es tan importante como la serie, así que lo dejamos en pantalla completa por defecto",
            style = KpknType.title.copy(fontSize = 23.sp, lineHeight = 31.sp),
            color = Color(0xFFF2EEE6), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        KpknCompactPill("Entendido", onClick = onDone, filled = true, height = 48.dp, modifier = Modifier.testTag("rest-intro-done"))
    }
}
