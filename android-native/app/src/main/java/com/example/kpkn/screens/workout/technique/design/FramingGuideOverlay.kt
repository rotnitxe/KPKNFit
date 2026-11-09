package com.example.kpkn.screens.workout.technique.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Capa de guía con figura humana aprobada en vista frontal o lateral y texto de orientación.
 * La figura usa una opacidad de 25 % para mantener visible el vídeo detrás.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FramingGuideOverlay(
    preferredView: ViewKind = ViewKind.SIDE,
    hasBarbell: Boolean = false,
    modifier: Modifier = Modifier,
    profileId: AnalysisProfileId? = null,
    expandedScene: Boolean = false,
    showSilhouette: Boolean = true,
    showGuideText: Boolean = true,
) {
    val isFront = preferredView == ViewKind.FRONT

    Column(
        modifier = modifier.fillMaxSize().padding(bottom = if (expandedScene) 0.dp else 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSilhouette) {
            Image(
                painter = painterResource(
                    id = framingGuideDrawableId(preferredView),
                ),
                contentDescription = null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = if (expandedScene) 0.dp else 24.dp)
                    .testTag("technique_silhouette"),
                contentScale = ContentScale.Fit,
                alpha = 0.25f,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (showGuideText) {
            // El texto reserva su altura real: ni al 130 % puede rozar los pies.
            Spacer(Modifier.height(12.dp))
            FramingGuideCaption(
                preferredView = preferredView,
                hasBarbell = hasBarbell,
                modifier = Modifier.padding(horizontal = 16.dp),
                backgroundAlpha = if (expandedScene) 0.55f else 0.70f,
            )
        }
    }
}

/** Texto de colocación de cámara; fuera del área flexible para que nunca se recorte. */
@Composable
internal fun FramingGuideCaption(
    preferredView: ViewKind,
    hasBarbell: Boolean,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.55f,
) {
    val isFront = preferredView == ViewKind.FRONT
    Column(
        modifier = modifier
            .testTag("technique_guide_text")
            .background(Color.Black.copy(alpha = backgroundAlpha), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val mainGuide = if (isFront) {
            "De frente · cuerpo entero con margen · cara visible · a 2–4 m"
        } else {
            "De lado · cuerpo entero con margen · cara visible · a 2–4 m"
        }
        Text(
            text = mainGuide,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
        )

        if (hasBarbell) {
            Text(
                text = "Móvil a media altura: en el suelo para peso muerto; disco siempre en cuadro",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun framingGuideDrawableId(view: ViewKind): Int = when (view) {
    ViewKind.FRONT -> R.drawable.technique_framing_front
    else -> R.drawable.technique_framing_side
}