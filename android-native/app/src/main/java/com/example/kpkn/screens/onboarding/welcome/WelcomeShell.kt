package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.ui.theme.Syne
import kotlin.math.abs

/*
 * Estructura de la bienvenida: logo discreto, teléfono héroe dentro de un carrusel, el mensaje de la página,
 * el indicador y el botón. Todo aquí es SIN ESTADO propio: el anfitrión (SetupWelcomeScreen) eleva el pager y el
 * reloj de las escenas y los pasa; este archivo solo dibuja y reparte el espacio.
 */

/** Colores de marca de la bienvenida (tinta y crema del kit; ver brand/README.md). */
internal object WelcomeShellColors {
    val ink = Color(0xFF121212)
    val cream = Color(0xFFF2EEE6)

    /** Segmento inactivo del indicador: contraste de al menos 3:1 sobre el negro (WCAG 1.4.11). */
    val line = Color.White.copy(alpha = 0.38f)
}

/** Inclinación máxima del teléfono al deslizar, en grados de rotationY. */
internal const val WelcomeMaxTiltDegrees = 14f

/** rotationY de un teléfono que está a [offsetInPages] del centro (positivo = a la derecha): nunca pasa de ±14°. */
internal fun welcomeTiltDegrees(offsetInPages: Float): Float = offsetInPages.coerceIn(-1f, 1f) * WelcomeMaxTiltDegrees

/** Los vecinos se ven un poco más pequeños que el teléfono del centro. */
internal fun welcomePhoneScale(offsetInPages: Float): Float = 1f - 0.07f * abs(offsetInPages).coerceAtMost(1f)

/** Y más tenues: el del centro manda y los vecinos solo asoman. */
internal fun welcomePhoneAlpha(offsetInPages: Float): Float = 1f - 0.55f * abs(offsetInPages).coerceAtMost(1f)

/**
 * Alto del teléfono en dp. El ideal es el 60 % del alto útil; si lo que va debajo (texto, indicador, botón y los
 * botones extra) no cabe, el teléfono cede lo necesario. Syne ExtraBold es una tipografía muy ancha: con anchos
 * menores de 380 dp (o letra grande) el título pasa de dos a tres líneas y el mensaje de tres a cuatro, y se reserva
 * ese alto. Con muy poco alto no baja de 190 dp: la página se desplaza.
 */
internal fun welcomePhoneHeight(
    usableHeight: Float,
    usableWidth: Float,
    fontScale: Float,
    extraButtons: Int,
    showLogo: Boolean = true,
): Float {
    val fs = fontScale.coerceIn(1f, 2f)
    val roomy = usableWidth >= 380f && fs <= 1.15f
    val titleLines = if (roomy) 2 else 3
    val messageLines = if (roomy) 3 else 4
    val logo = if (showLogo) 40f else 8f
    val pagerMargin = 20f
    val gapToText = 14f
    val text = 16f + 8f + (titleLines * 27f + 8f + messageLines * 22f) * fs // etiqueta + título + mensaje
    val indicator = 54f
    val cta = 78f + 48f * extraButtons
    val room = usableHeight - (logo + pagerMargin + gapToText + text + indicator + cta)
    return minOf(usableHeight * 0.60f, room).coerceIn(190f, 620f)
}

private val TitleStyle = TextStyle(
    fontFamily = Syne,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp,
    lineHeight = 27.sp,
    letterSpacing = (-0.01).em,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Heading,
    color = WizardColors.text,
)

private val MessageStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    textAlign = TextAlign.Center,
    lineBreak = LineBreak.Paragraph,
    color = WizardColors.textMuted,
)

private const val ContentMaxWidthDp = 420

/** Por debajo de este alto útil no se pinta el logo: el teléfono lo necesita más. */
private val WelcomeLogoMinHeight = 700.dp

@Composable
internal fun WelcomeShell(
    pagerState: PagerState,
    clock: WelcomeSceneClock,
    reducedMotion: Boolean,
    actionLabel: String,
    onStart: () -> Unit,
    onSelectPage: (Int) -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    onDetails: (() -> Unit)?,
) {
    val hasSecondary = secondaryLabel != null && onSecondary != null
    val extraButtons = (if (hasSecondary) 1 else 0) + (if (onDetails != null) 1 else 0)
    val fontScale = LocalDensity.current.fontScale

    // Entrada: el teléfono sube y se enciende; el texto y el botón llegan un instante después.
    val enter = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) enter.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(WizardColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // En pantallas bajas el logo cede su sitio al teléfono.
        val showLogo = maxHeight >= WelcomeLogoMinHeight
        val phoneHeight = welcomePhoneHeight(maxHeight.value, maxWidth.value, fontScale, extraButtons, showLogo).dp
        val bodyWidth = welcomePhoneGeometry(phoneHeight.value).bodyWidth.dp
        val sidePadding = ((maxWidth - bodyWidth) / 2).coerceAtLeast(0.dp)
        val pagerHeight = phoneHeight + 20.dp

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showLogo) {
                    Spacer(Modifier.height(12.dp))
                    WelcomeLogo(Modifier.graphicsLayer { alpha = seg(enter.value, 0f, .5f) })
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.weight(.5f))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pagerHeight)
                        .drawBehind { drawStagePool() }
                        .graphicsLayer {
                            val k = seg(enter.value, 0f, .75f)
                            alpha = k
                            translationY = (1f - k) * 18.dp.toPx()
                        },
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    pageSize = PageSize.Fixed(bodyWidth),
                    pageSpacing = 14.dp,
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    WelcomePageItem(page, pagerState, clock, phoneHeight)
                }
                Spacer(Modifier.height(14.dp))
                Spacer(Modifier.weight(.3f))
                WelcomeTextBlock(
                    selectedPage = pagerState.currentPage,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .graphicsLayer { alpha = seg(enter.value, .25f, 1f) },
                )
                Spacer(Modifier.weight(.15f))
                WelcomePageIndicator(
                    pageCount = WelcomePages.size,
                    selectedPage = pagerState.currentPage,
                    reducedMotion = reducedMotion,
                    onSelect = onSelectPage,
                    modifier = Modifier.graphicsLayer { alpha = seg(enter.value, .25f, 1f) },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 12.dp)
                    .graphicsLayer { alpha = seg(enter.value, .4f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WelcomeCta(actionLabel, onStart)
                if (hasSecondary) {
                    TextButton(
                        onClick = onSecondary!!,
                        modifier = Modifier.widthIn(max = ContentMaxWidthDp.dp).fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(secondaryLabel!!, color = WizardColors.textMuted, fontSize = 14.sp)
                    }
                }
                if (onDetails != null) {
                    TextButton(
                        onClick = onDetails,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Elegir otra configuración", color = WizardColors.textMuted)
                    }
                }
            }
        }
    }
}

/** Un halo gris muy tenue detrás del teléfono: sin él, el cuerpo oscuro se pierde en el negro y la sombra no se ve. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStagePool() {
    val r = size.height * 0.56f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF26282B).copy(alpha = 0.70f), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = r,
        ),
        radius = r,
        center = Offset(size.width / 2f, size.height / 2f),
    )
}

@Composable
private fun WelcomeLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kpkn_logo_horizontal),
        contentDescription = "KPKN",
        modifier = modifier.height(16.dp),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(WelcomeShellColors.cream),
        alpha = 0.92f,
    )
}

/** Una página del carrusel: el teléfono, inclinado y atenuado según lo lejos que esté del centro. */
@Composable
private fun WelcomePageItem(page: Int, pagerState: PagerState, clock: WelcomeSceneClock, phoneHeight: Dp) {
    val isCurrent = page == pagerState.currentPage
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WelcomePhone(
            bodyHeight = phoneHeight,
            description = if (isCurrent) WelcomePages[page].demoDescription else null,
            modifier = Modifier
                .testTag("welcome-phone-${WelcomePages[page].id}")
                .graphicsLayer {
                    // Distancia con signo al centro, en páginas: + a la derecha, − a la izquierda.
                    val offset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    rotationY = welcomeTiltDegrees(offset)
                    // `cameraDistance` se mide en «puntos» de 72 por pulgada (el 8 por defecto son 576 px): ~2,4 anchos de
                    // teléfono dan una perspectiva suave a 14° sin que ningún borde pase cerca de la cámara.
                    cameraDistance = 2.4f * size.width / 72f
                    val s = welcomePhoneScale(offset)
                    scaleX = s
                    scaleY = s
                    alpha = welcomePhoneAlpha(offset)
                },
        ) {
            WelcomeScenePane(page, clock)
        }
    }
}

/** La escena de una página con el reloj de [clock]: solo esta función se recompone en cada cuadro. */
@Composable
private fun WelcomeScenePane(page: Int, clock: WelcomeSceneClock) {
    val copy = WelcomePages[page]
    val frame = clock.frameOf(page, copy.period, copy.shellFadesLoop)
    WelcomeScaledScene(Modifier.fillMaxSize().graphicsLayer { alpha = frame.alpha }) {
        WelcomeSceneOf(page, frame.t)
    }
}

/**
 * Etiqueta, título y mensaje de cada página, apilados en el MISMO sitio: la altura es la del más alto de los tres,
 * así el indicador y el botón no se mueven al cambiar de página. El de la página elegida se funde con un
 * desplazamiento corto; con movimiento reducido el cambio es instantáneo. Lo que no está activo no se anuncia.
 */
@Composable
private fun WelcomeTextBlock(selectedPage: Int, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val millis = if (reducedMotion) 0 else 260
    val shiftPx = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        modifier
            .widthIn(max = ContentMaxWidthDp.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                heading()
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        WelcomePages.forEachIndexed { index, page ->
            val active = index == selectedPage
            val alpha by animateFloatAsState(if (active) 1f else 0f, tween(millis), label = "welcomeTextAlpha")
            val shift by animateFloatAsState(
                if (active) 0f else if (index < selectedPage) -1f else 1f,
                tween(millis, easing = FastOutSlowInEasing),
                label = "welcomeTextShift",
            )
            Column(
                Modifier
                    .testTag("welcome-text-${page.id}")
                    .graphicsLayer {
                        this.alpha = alpha
                        translationY = shift * shiftPx
                    }
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(page.label, style = WizardTypography.eyebrow.copy(color = page.accent, textAlign = TextAlign.Center))
                Spacer(Modifier.height(8.dp))
                Text(page.title, style = TitleStyle)
                Spacer(Modifier.height(8.dp))
                Text(page.message, style = MessageStyle)
            }
        }
    }
}

/**
 * Segmentos finos: el activo en crema y más largo, el resto en línea. Cada uno es un objetivo táctil de 48 dp de
 * alto que cambia de página; sigue anunciándose «Vista N de 3» y su estado de selección.
 */
@Composable
private fun WelcomePageIndicator(
    pageCount: Int,
    selectedPage: Int,
    reducedMotion: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val millis = if (reducedMotion) 0 else 240
    Row(modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(pageCount) { index ->
            val selected = index == selectedPage
            val color by animateColorAsState(
                if (selected) WelcomeShellColors.cream else WelcomeShellColors.line,
                tween(millis),
                label = "welcomeSegmentColor",
            )
            val width by animateDpAsState(if (selected) 36.dp else 22.dp, tween(millis), label = "welcomeSegmentWidth")
            Box(
                Modifier
                    .size(width = 52.dp, height = 48.dp)
                    .semantics { contentDescription = "Vista ${index + 1} de $pageCount" }
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width, 3.dp).clip(CircleShape).background(color))
            }
        }
    }
}

/** El botón pastilla crema: Syne SemiBold, al menos 56 dp de alto y como mucho 420 dp de ancho. */
@Composable
private fun WelcomeCta(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = WelcomeShellColors.cream,
        contentColor = WelcomeShellColors.ink,
        modifier = Modifier
            .widthIn(max = ContentMaxWidthDp.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("welcome-cta")
            .semantics { role = Role.Button },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = WelcomeShellColors.ink,
                fontFamily = Syne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
