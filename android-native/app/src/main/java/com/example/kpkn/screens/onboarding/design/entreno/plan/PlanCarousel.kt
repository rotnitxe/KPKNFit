package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.example.kpkn.screens.onboarding.design.entreno.rememberProgressiveCount

/*
 * «Elige tu programa»: el carrusel de los programas de autor de una disciplina. Un `HorizontalPager` con la portada
 * central grande (3:4) y las vecinas asomando, más pequeñas y atenuadas; la ilustración de cada portada se desplaza un
 * poco al deslizar (parallax suave). Debajo del centro van el blurb y dos acciones de texto, sin caja.
 */

/** Ancho de la tarjeta central respecto al ancho disponible con letra normal, con letra al 130 % y su tope. */
private const val CARD_WIDTH_FRACTION = 0.66f
private const val CARD_WIDTH_FRACTION_LARGE_TEXT = 0.74f
private val CARD_MAX_WIDTH = 300.dp

/**
 * El ancho de la portada central en un carrusel de [availableDp] de ancho: dos tercios con letra normal, que crece hasta tres
 * cuartos con la letra al 130 % (las vecinas siguen asomando) para que las palabras largas de un título («Powerbuilding»,
 * «Complemento») quepan enteras en su portada, con un tope de 300 dp.
 */
internal fun carouselCardWidthDp(availableDp: Float, fontScale: Float): Float {
    val t = ((fontScale - 1f) / 0.3f).coerceIn(0f, 1f)
    return minOf(availableDp * lerpF(CARD_WIDTH_FRACTION, CARD_WIDTH_FRACTION_LARGE_TEXT, t), CARD_MAX_WIDTH.value)
}

/** Aire entre tarjetas, escala y opacidad de las vecinas. */
private val PAGE_SPACING = 14.dp
private const val SIDE_SCALE = 0.88f
private const val SIDE_ALPHA = 0.5f

/** Lado de la marca de «hecho» sobre la portada elegida y su margen. */
private val DONE_BOX = 32.dp

/** Marca de prueba de las acciones del bloque de debajo: `setup-plan-open-<id>` y `setup-plan-choose-<id>`. */
internal fun planOpenTag(id: String): String = "setup-plan-open-$id"
internal fun planChooseTag(id: String): String = "setup-plan-choose-$id"

/**
 * El carrusel de programas.
 *
 * - [cards]: las tarjetas, en orden; sus `id` deben ser únicos. Con una sola se muestra centrada, sin pager.
 * - [selectedId]: el programa elegido (lleva una marca de «hecho» sobre su portada); el carrusel arranca en él.
 * - [onSelect]: «Elegir» (con el id de la tarjeta central). [onOpen]: «Ver detalles», o tocar la tarjeta central.
 *   Tocar una vecina la trae al centro.
 *
 * Cada tarjeta es un botón con la marca de prueba `setup-plan-card-<id>`, anunciado con su título, kicker y datos, y
 * con el estado de selección real.
 */
@Composable
fun PlanCarousel(
    cards: List<PlanCardModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlanCarousel(cards, selectedId, onSelect, onOpen, wizardReducedMotion(), modifier)
}

/** El carrusel con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun PlanCarousel(
    cards: List<PlanCardModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardW = carouselCardWidthDp(maxWidth.value, LocalDensity.current.fontScale).dp
        val cardH = cardW * (4f / 3f)
        // Lo que sobra a cada lado de la tarjeta central: ahí asoman las vecinas.
        val sidePad = (maxWidth - cardW) / 2
        if (cards.size == 1) {
            val card = cards[0]
            val isSelected = card.id == selectedId
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlanCardFace(
                    card = card,
                    isSelected = isSelected,
                    animate = !reduced,
                    reduced = reduced,
                    parallax = { 0f },
                    onClick = { onOpen(card.id) },
                    modifier = Modifier.size(cardW, cardH),
                )
                Spacer(Modifier.height(12.dp))
                PlanCardInfo(card, isSelected, onOpen, onSelect)
            }
        } else {
            val initial = remember { cards.indexOfFirst { it.id == selectedId }.coerceAtLeast(0) }
            val pager = rememberPagerState(initialPage = initial) { cards.size }
            val scope = rememberCoroutineScope()
            // Las vecinas se componen un cuadro después de la central (cada portada es pesada de componer): ver `rememberProgressiveCount`.
            val neighboursReady = rememberProgressiveCount(total = 1, first = 0) > 0
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = sidePad),
                    pageSpacing = PAGE_SPACING,
                    beyondViewportPageCount = 1,
                    key = { cards[it].id },
                ) { page ->
                    val card = cards[page]
                    if (page == pager.currentPage || neighboursReady) {
                        PlanCardFace(
                            card = card,
                            isSelected = card.id == selectedId,
                            animate = page == pager.currentPage && !reduced,
                            reduced = reduced,
                            parallax = { (pager.currentPage + pager.currentPageOffsetFraction - page).coerceIn(-1f, 1f) },
                            onClick = {
                                if (page == pager.currentPage) onOpen(card.id) else scope.launch { pager.animateScrollToPage(page) }
                            },
                            modifier = Modifier
                                .size(cardW, cardH)
                                .graphicsLayer {
                                    val f = abs(pager.currentPage + pager.currentPageOffsetFraction - page).coerceIn(0f, 1f)
                                    val s = lerpF(1f, SIDE_SCALE, f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = lerpF(1f, SIDE_ALPHA, f)
                                },
                        )
                    } else {
                        Spacer(Modifier.size(cardW, cardH))
                    }
                }
                Spacer(Modifier.height(14.dp))
                PageDots(
                    count = cards.size,
                    position = { pager.currentPage + pager.currentPageOffsetFraction },
                    selectedIndex = cards.indexOfFirst { it.id == selectedId },
                )
                Spacer(Modifier.height(6.dp))
                val current = cards[pager.currentPage.coerceIn(0, cards.lastIndex)]
                Crossfade(
                    targetState = current,
                    animationSpec = if (reduced) snap() else tween(180),
                    label = "planInfo",
                ) { card ->
                    PlanCardInfo(card, card.id == selectedId, onOpen, onSelect)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- la tarjeta

/**
 * Una portada tocable. Es un botón («Ver detalles») anunciado con los datos del programa y, si es el elegido, con su marca de
 * «hecho» en la esquina. No hay caja: la portada es la imagen.
 */
@Composable
private fun PlanCardFace(
    card: PlanCardModel,
    isSelected: Boolean,
    animate: Boolean,
    reduced: Boolean,
    parallax: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.98f else 1f,
        animationSpec = tween(120),
        label = "planPress",
    )
    val description = planCardDescription(card)
    Box(
        modifier = modifier
            .testTag(planCardTag(card.id))
            .graphicsLayer {
                val s = pressScale.value
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = PlanCopy.SEE_DETAILS,
                onClick = onClick,
            )
            // Va DESPUÉS de `clickable`: conserva su rol y su acción y descarta los textos sueltos de la portada.
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                if (isSelected) stateDescription = PlanCopy.PROGRAM_CHOSEN
            },
    ) {
        PlanCover(card, Modifier.fillMaxSize(), animate, reduced, parallax, 20.dp, 0.dp, CoverLayout.POSTER)
        DoneStamp(
            visible = isSelected,
            reduced = reduced,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(DONE_BOX),
        )
    }
}

/** Lo que anuncia TalkBack de una tarjeta: «Texas Method. Jim Wendler. 4 días · 60 min · Intermedio.» */
internal fun planCardDescription(card: PlanCardModel): String = buildString {
    append(card.title)
    if (card.kicker.isNotBlank()) append(". ").append(card.kicker)
    val facts = coverFacts(card)
    if (facts.isNotBlank()) append(". ").append(facts)
    if (!card.badge.isNullOrBlank()) append(". ").append(card.badge)
}

/** La marca de «hecho» de la portada elegida: un disco verde con una marca que se traza. */
@Composable
private fun DoneStamp(visible: Boolean, reduced: Boolean, modifier: Modifier = Modifier) {
    val pen = remember { SymbolPen() }
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(420),
        label = "planDone",
    )
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0.001f) return@Canvas
        pen.begin(this, 1f, SymbolPalette.ok)
        pen.doneBadge(this, size.width / 2f, size.height / 2f, min(size.width, size.height) / 2f - 2.dp.toPx(), p)
    }
}

// ---------------------------------------------------------------- debajo de la tarjeta central

/** El blurb de la tarjeta central y sus dos acciones de texto: «Ver detalles» y «Elegir» (o «Elegido»). */
@Composable
private fun PlanCardInfo(
    card: PlanCardModel,
    isSelected: Boolean,
    onOpen: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = card.blurb,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        PlanActions(card, isSelected, onOpen, onSelect)
    }
}

/**
 * «Ver detalles» y «Elegir» (o «Elegido»), uno junto al otro. Con letra grande o en un teléfono estrecho no caben en una
 * línea sin partir una palabra: entonces se apilan, centrados.
 */
@Composable
private fun PlanActions(card: PlanCardModel, isSelected: Boolean, onOpen: (String) -> Unit, onSelect: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chooseLabel = if (isSelected) PlanCopy.CHOSEN else PlanCopy.CHOOSE
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val stacked = remember(chooseLabel, widthPx, density.fontScale, density.density) {
            val style = WizardTypography.controlLabel
            val open = measurer.measure(text = PlanCopy.SEE_DETAILS, style = style, maxLines = 1, softWrap = false).size.width
            val choose = measurer.measure(text = chooseLabel, style = style, maxLines = 1, softWrap = false).size.width
            // Cada acción lleva 14 dp de aire a cada lado, y la elegida una marca de 14 dp más 6 dp de hueco; entre las dos, 4 dp.
            val extras = with(density) { (14.dp * 4 + 4.dp + (if (isSelected) 20.dp else 0.dp)).toPx() }
            open + choose + extras > widthPx
        }
        val openAction = @Composable {
            TextAction(
                label = PlanCopy.SEE_DETAILS,
                color = WizardColors.textMuted,
                tag = planOpenTag(card.id),
                onClick = { onOpen(card.id) },
            )
        }
        val chooseAction = @Composable {
            TextAction(
                label = chooseLabel,
                color = if (isSelected) WizardColors.done else WizardColors.text,
                tag = planChooseTag(card.id),
                leadingCheck = isSelected,
                onClick = { onSelect(card.id) },
            )
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                openAction()
                chooseAction()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                openAction()
                Spacer(Modifier.width(4.dp))
                chooseAction()
            }
        }
    }
}

/** Una acción de texto sin caja: objetivo táctil de 48 dp y un apagado leve mientras se mantiene el dedo encima. */
@Composable
private fun TextAction(
    label: String,
    color: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingCheck: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .graphicsLayer { alpha = if (pressed) 0.55f else 1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingCheck) {
            Canvas(Modifier.size(14.dp)) {
                val w = 2.dp.toPx()
                drawLine(color, Offset(size.width * 0.12f, size.height * 0.54f), Offset(size.width * 0.40f, size.height * 0.82f), w, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.40f, size.height * 0.82f), Offset(size.width * 0.90f, size.height * 0.2f), w, StrokeCap.Round)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = WizardTypography.controlLabel, color = color)
    }
}

// ---------------------------------------------------------------- indicador de páginas

/**
 * Los puntos del carrusel: el de la página actual se estira en píldora y sigue al dedo ([position] se lee al dibujar); el del
 * programa elegido es verde.
 */
@Composable
private fun PageDots(count: Int, position: () -> Float, selectedIndex: Int, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val dot = 6.dp.toPx()
        val longW = 18.dp.toPx()
        val gap = 7.dp.toPx()
        val pos = position()
        var total = gap * (count - 1)
        for (i in 0 until count) total += lerpF(longW, dot, abs(i - pos).coerceIn(0f, 1f))
        var x = (size.width - total) / 2f
        val y = (size.height - dot) / 2f
        for (i in 0 until count) {
            val near = 1f - abs(i - pos).coerceIn(0f, 1f)
            val w = lerpF(dot, longW, near)
            val base = if (i == selectedIndex) WizardColors.done else WizardColors.text
            val color = base.copy(alpha = lerpF(0.30f, 1f, near))
            drawRoundRect(color, Offset(x, y), Size(w, dot), CornerRadius(dot / 2f, dot / 2f))
            x += w + gap
        }
    }
}
