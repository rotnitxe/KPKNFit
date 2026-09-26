package com.example.kpkn.screens.onboarding

import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.WizardColors
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The white display is an intentional slot for real in-app screenshots. */
@Composable
internal fun SetupWelcomeScreen(
    onStart: () -> Unit,
    actionLabel: String = "Comenzar",
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onOpenVisualGate: (() -> Unit)? = null,
) {
    WizardDarkSystemBars()
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val reducedMotion = wizChatReducedMotion()
    BoxWithConstraints(Modifier.fillMaxSize().background(WizardColors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val compact = maxHeight < 620.dp
        val deviceHeight = (maxHeight * if (compact) .38f else if (secondaryLabel == null) .53f else .44f)
            .coerceAtMost(490.dp).coerceAtLeast(if (compact) 145.dp else 195.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (compact) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(.35f))
            Text("KPKN", color = WizardColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(deviceHeight + 16.dp),
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val turn = (page - 1) * 24f
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WelcomePhone(
                        modifier = Modifier.height(deviceHeight).aspectRatio(.49f)
                            .graphicsLayer {
                                rotationY = turn
                                cameraDistance = 18f * density
                                scaleX = if (page == pager.currentPage) 1f else .94f
                                scaleY = if (page == pager.currentPage) 1f else .94f
                            },
                        angle = turn,
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { index ->
                    Box(Modifier.size(42.dp).semantics { contentDescription = "Vista ${index + 1} de 3"; selected = index == pager.currentPage }
                        .clickable { scope.launch { if (reducedMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (index == pager.currentPage) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape).background(if (index == pager.currentPage) WizardColors.cta else WizardColors.text.copy(alpha = .28f)))
                    }
                }
            }
            if (compact) Spacer(Modifier.height(16.dp)) else Spacer(Modifier.weight(.65f))
            Text("Tu punto de partida", color = WizardColors.text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Vamos a preparar KPKN contigo.", color = WizardColors.textMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(25.dp))
            Surface(
                color = WizardColors.cta,
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onStart),
            ) { Box(contentAlignment = Alignment.Center) { Text(actionLabel, color = WizardColors.ctaContent, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(secondaryLabel, color = WizardColors.textMuted, fontSize = 14.sp)
                }
            }
            if (onDetails != null) {
                TextButton(onClick = onDetails) { Text("Elegir otra configuración", color = WizardColors.textMuted) }
            }
            // ENTRADA TEMPORAL (Fase 1): permite revisar el prototipo de la puerta
            // de aprobación visual. Se retira en la Fase 2, cuando el wizard real
            // ocupe su sitio. No cambia la composición ni el carrusel de bienvenida.
            if (onOpenVisualGate != null) {
                TextButton(onClick = onOpenVisualGate) { Text("Prototipo visual · revisión de diseño", color = WizardColors.textFaint) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun WelcomePhone(modifier: Modifier, angle: Float,
    screenContent: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.semantics { contentDescription = "Vista de un teléfono KPKN con pantalla blanca" }, contentAlignment = Alignment.Center) {
        // A single transformed chassis keeps the white screenshot slot aligned
        // with its metal edge across all three carousel perspectives.
        Box(Modifier.fillMaxSize().padding(start = if (angle > 0f) 2.dp else 0.dp, end = if (angle < 0f) 2.dp else 0.dp)
            .shadow(28.dp, RoundedCornerShape(39.dp))
            .clip(RoundedCornerShape(39.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2E2E2E), Color(0xFF8A8A8A),
                Color(0xFF3E3E3E), Color(0xFF6E6E6E), Color(0xFF262626)))))
        Box(Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(37.dp)).background(Color(0xFF171717)))
        Box(Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(33.dp)).background(Color(0xFF101010)))
        Box(Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(31.dp)).background(Color.White),
            content = screenContent)
        Box(Modifier.align(Alignment.TopCenter).padding(top = 13.dp).size(12.dp).clip(CircleShape).background(Color(0xFF1C1C1C)))
        Box(Modifier.align(Alignment.TopCenter).padding(top = 17.dp).size(4.dp).clip(CircleShape).background(Color(0xFF6B6B6B)))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 2.dp, y = (-42).dp).width(3.dp).height(36.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFF797979)))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 2.dp, y = (-94).dp).width(3.dp).height(25.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFF6A6A6A)))
    }
}
