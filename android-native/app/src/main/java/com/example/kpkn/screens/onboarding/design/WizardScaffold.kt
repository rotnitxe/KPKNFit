package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Altura real bajo progreso y sobre el CTA. `Unspecified` fuera de esa ruta. */
val LocalWizardControlViewport = compositionLocalOf { Dp.Unspecified }

enum class WizardAnthropometryLayout { Pending, Combined, Separate }

/** Decisión de fusionar altura y peso según el hueco medido y la escala de fuente. */
@Composable
fun currentAnthropometryLayout(): WizardAnthropometryLayout {
    val viewport = LocalWizardControlViewport.current
    if (viewport == Dp.Unspecified) return WizardAnthropometryLayout.Pending
    val fits = WizardAnthropometryFit.fits(viewport.value, LocalDensity.current.fontScale)
    return if (fits) WizardAnthropometryLayout.Combined else WizardAnthropometryLayout.Separate
}

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
    /**
     * Ruta de **control centrado** (altura/peso): `header` queda fijo arriba y
     * `content` ocupa el espacio restante real de la viewport. `false`
     * conserva el layout clásico con scroll de todas las demás pantallas.
     */
    centerControl: Boolean = false,
    /** Cabecera fija superior; solo se usa con [centerControl]. */
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    WizardDarkSystemBars()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardColors.background)
            .statusBarsPadding(),
    ) {
        WizardHeader(
            title = title,
            onBack = onBack,
            onExit = onExit,
            exitLabel = exitLabel,
            measurementScale = centerControl,
        )
        WizardProgress(progress = progress)
        if (centerControl && header != null) {
            CenteredControlArea(header = header, content = content)
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WizardSpacing.gutter, vertical = WizardSpacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
                content = content,
            )
        }
        WizardCta(
            label = ctaLabel,
            enabled = ctaEnabled,
            onClick = onCta,
            measurementScale = centerControl,
        )
    }
}

/**
 * Contenedor de viewport para los pasos de control centrado.
 *
 * `BoxWithConstraints` da la altura **real** que queda bajo la cabecera, el
 * progreso y el CTA; el contenedor fija ese mínimo (`heightIn`) y la región de
 * control se lleva el resto con `weight`, así que no hay alturas mágicas por
 * teléfono. La cabecera no se desplaza: el control se centra por debajo.
 */
@Composable
private fun ColumnScope.CenteredControlArea(
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        val viewportHeight = maxHeight
        CompositionLocalProvider(LocalWizardControlViewport provides viewportHeight) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = viewportHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = WizardSpacing.gutter,
                        end = WizardSpacing.gutter,
                        top = WizardSpacing.sectionGap,
                        bottom = WizardSpacing.cardGap,
                    ),
                verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
            ) { header() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = WizardSpacing.gutter,
                        end = WizardSpacing.gutter,
                        bottom = WizardSpacing.sectionGap,
                    ),
                // El control se centra en el espacio restante; si no cabe, su
                // columna interna hace scroll (escala de fuente o pantalla corta).
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
            }
        }
        }
    }
}

@Composable
private fun WizardHeader(
    title: String,
    onBack: (() -> Unit)?,
    onExit: (() -> Unit)?,
    exitLabel: String,
    measurementScale: Boolean,
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
            style = if (measurementScale) WizardTypography.wizardTopBar else WizardTypography.header,
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

/**
 * Progreso fino bajo la cabecera, relleno claro neutral proporcional al avance.
 * El color lo fija la paleta (`progressFill`), no el acento del bloque: las
 * referencias lo muestran neutro.
 */
@Composable
private fun WizardProgress(progress: Float) {
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
                .background(WizardColors.progressFill),
        )
    }
}

/**
 * CTA blanco fijo del pie, con el estado deshabilitado de las referencias.
 *
 * Estable para pruebas y para TalkBack: `setup-continue` identifica el único
 * CTA de la pantalla y el nodo publica `Role.Button` junto al estado
 * deshabilitado real (`clickable(enabled = …)` emite `Disabled`), de modo que
 * «ocupado/inválido» se anuncia como botón deshabilitado y no como texto.
 */
@Composable
private fun WizardCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    measurementScale: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = if (measurementScale) WizardSpacing.gutterCompact else WizardSpacing.gutter,
                vertical = 12.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (measurementScale) WizardSpacing.wizardCtaHeight else WizardSpacing.ctaHeight)
                .clip(WizardShapes.cta)
                .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag("setup-continue")
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = if (measurementScale) WizardTypography.wizardCta else WizardTypography.cta,
                color = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
            )
        }
    }
}
