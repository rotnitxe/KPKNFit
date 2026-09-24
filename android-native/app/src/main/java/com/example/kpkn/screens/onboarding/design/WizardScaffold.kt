package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Andamiaje común de una pantalla del wizard.
 *
 * Estructura fija de las referencias: cabecera compacta con atrás, nombre del
 * bloque centrado y acción de salida discreta; progreso fino bajo la cabecera;
 * una pregunta grande; el control principal; y un CTA blanco anclado abajo que
 * respeta barras de navegación e IME.
 */
@Composable
fun WizardScaffold(
    block: WizardBlock,
    title: String,
    progress: Float,
    onBack: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    exitLabel: String = "Salir",
    ctaLabel: String,
    ctaEnabled: Boolean = true,
    onCta: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardColors.background)
            .statusBarsPadding(),
    ) {
        WizardHeader(title = title, onBack = onBack, onExit = onExit, exitLabel = exitLabel)
        WizardProgress(progress = progress, accent = block.accent)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WizardSpacing.gutter, vertical = WizardSpacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
            content = content,
        )
        WizardCta(
            label = ctaLabel,
            enabled = ctaEnabled,
            onClick = onCta,
        )
    }
}

@Composable
private fun WizardHeader(
    title: String,
    onBack: (() -> Unit)?,
    onExit: (() -> Unit)?,
    exitLabel: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = WizardTypography.header,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .semantics { heading() },
        )
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(WizardSpacing.touchTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Volver al paso anterior" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = WizardColors.text,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (onExit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(WizardSpacing.touchTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExit)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = WizardColors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(exitLabel, style = WizardTypography.caption, color = WizardColors.textMuted)
                }
            }
        }
    }
}

/** Progreso fino bajo la cabecera, relleno proporcional al avance del bloque. */
@Composable
private fun WizardProgress(progress: Float, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WizardSpacing.hairline)
            .background(WizardColors.progressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(accent),
        )
    }
}

/** CTA blanco fijo del pie, con el estado deshabilitado de las referencias. */
@Composable
private fun WizardCta(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = WizardSpacing.gutter, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WizardSpacing.ctaHeight)
                .clip(WizardShapes.cta)
                .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = WizardTypography.cta,
                color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
            )
        }
    }
}
