package com.example.kpkn.screens.onboarding.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
    showCta: Boolean = true,
    showHeader: Boolean = true,
    /**
     * Dentro de la página larga: el área mide su contenido. `fillMaxSize` + `weight`
     * dentro del scroll vertical colapsan el paso a alto cero y la pantalla queda negra.
     */
    embedded: Boolean = false,
    /** Título del paso que viene, asomado y desenfocado bajo esta vista. */
    nextPeekTitle: String? = null,
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
    if (embedded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WizardSpacing.gutter, vertical = WizardSpacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
        ) {
            if (centerControl && header != null) header()
            content()
            if (nextPeekTitle != null) NextStepPeek(title = nextPeekTitle)
        }
        return
    }
    val haze = remember { HazeState() }
    Box(Modifier.fillMaxSize().background(WizardColors.background)) {
    Row(Modifier.fillMaxSize()) {
        if (showCta) {
            WizardVerticalProgress(progress)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = haze)
            .statusBarsPadding()
            .padding(top = if (showHeader) 58.dp else 0.dp),
    ) {
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
        if (nextPeekTitle != null) NextStepPeek(title = nextPeekTitle)
        if (showCta) {
            WizardCta(
                label = ctaLabel,
                enabled = ctaEnabled,
                onClick = onCta,
                measurementScale = centerControl,
            )
        }
    }
        if (showHeader) {
            WizardGlassHeader(
                haze = haze,
                title = title,
                onBack = onBack,
                onExit = onExit,
                exitLabel = exitLabel,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(2f).statusBarsPadding(),
            )
        }
        }
    }
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

private fun liquidGlassStyle(): HazeStyle = HazeStyle(
    blurRadius = 36.dp,
    tint = HazeTint(Color.White.copy(alpha = 0.20f)),
    backgroundColor = Color.White.copy(alpha = 0.08f),
    noiseFactor = 0.14f,
)

@Composable
private fun WizardVerticalProgress(progress: Float) {
    Box(
        Modifier
            .padding(start = 10.dp, top = 86.dp, bottom = 108.dp)
            .width(5.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(99.dp))
            .background(WizardColors.progressTrack.copy(alpha = 0.45f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress.coerceIn(0.06f, 1f))
                .clip(RoundedCornerShape(99.dp))
                .align(Alignment.TopCenter)
                .background(WizardColors.progressFill.copy(alpha = 0.72f)),
        )
    }
}

@Composable
internal fun WizardGlassHeader(
    haze: HazeState,
    title: String,
    onBack: (() -> Unit)?,
    onExit: (() -> Unit)?,
    exitLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            GlassPill(
                haze = haze,
                modifier = Modifier.size(46.dp),
                onClick = onBack,
                description = "Volver al paso anterior",
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WizardColors.text)
            }
        }
        GlassPill(
            haze = haze,
            modifier = Modifier.weight(1f).height(46.dp),
            onClick = null,
            description = title,
        ) {
            Text(
                text = title,
                color = WizardColors.text,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
            )
        }
        if (onExit != null) {
            GlassPill(
                haze = haze,
                modifier = Modifier.size(46.dp),
                onClick = onExit,
                description = exitLabel,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFFF4D4D))
            }
        }
    }
}

@Composable
private fun GlassPill(
    haze: HazeState,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    description: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .hazeEffect(state = haze, style = liquidGlassStyle())
            .border(1.dp, Color.White.copy(alpha = 0.42f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * CTA blanco fijo del pie, con el estado deshabilitado de las referencias.
 *
 * Estable para pruebas y para TalkBack: `setup-continue` identifica el único
 * CTA de la pantalla y el nodo publica `Role.Button` junto al estado
 * deshabilitado real (`clickable(enabled = …)` emite `Disabled`), de modo que
 * «ocupado/inválido» se anuncia como botón deshabilitado y no como texto.
 */
/** Franja inferior desenfocada con el título de lo que viene. */
@Composable
private fun NextStepPeek(title: String) {
    val frame = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .padding(horizontal = WizardSpacing.gutter)
    Box(
        modifier = if (Build.VERSION.SDK_INT >= 31) frame.blur(16.dp) else frame,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = title,
            style = WizardTypography.question,
            color = WizardColors.text.copy(alpha = 0.42f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Check redondo, centrado abajo. El anuncio sigue siendo [label]
 * (`Continuar` o la activación) para TalkBack y las pruebas.
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
            .padding(top = 4.dp, bottom = if (measurementScale) 10.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (measurementScale) 68.dp else 64.dp)
                .clip(CircleShape)
                .background(if (enabled) WizardColors.cta else WizardColors.ctaDisabled)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag("setup-continue")
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
