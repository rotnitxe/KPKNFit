package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * El detalle de un programa: una hoja a pantalla completa (un Dialog con desenfoque) con, en este orden y todo sobre la
 * página negra, sin cajas: la portada (a sangre, con parallax al hacer scroll), la descripción, los ejercicios
 * principales, la estructura en bloques, la semana tipo, por qué este programa, las notas honestas y la atribución. Fijo
 * abajo: la frase «Podrás modificarlo libremente después.» y el botón de elegir.
 */

/** Marcas de prueba: la hoja, su botón principal y su botón de cerrar. */
const val PLAN_DETAIL_TAG = "setup-plan-detail"
internal const val PLAN_DETAIL_CHOOSE_TAG = "setup-plan-detail-choose"
internal const val PLAN_DETAIL_CLOSE_TAG = "setup-plan-detail-close"

/** Alto del cuerpo de la cabecera (sin la barra de estado) y franja superior reservada al botón de cerrar. */
private val HEADER_BODY = 232.dp
private val HEADER_STRIP = 44.dp

/** Líneas de la descripción antes de «Ver más». */
private const val DESCRIPTION_LINES = 4

/** Título de cada sección: Syne ExtraBold. */
private val SECTION_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.1).sp,
)

/**
 * La hoja de detalle de un programa.
 *
 * - [selected]: si es el programa elegido; el botón pasa a «Programa elegido» (en verde).
 * - [onChoose]: «Elegir este programa» (quien llama elige y suele cerrar la hoja).
 * - [onClose]: la X de arriba y «atrás»; la hoja se desvanece antes de avisar.
 */
@Composable
fun PlanDetailOverlay(model: PlanDetailModel, selected: Boolean, onChoose: () -> Unit, onClose: () -> Unit) {
    PlanDetailOverlay(model, selected, onChoose, onClose, wizardReducedMotion())
}

/** La hoja con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun PlanDetailOverlay(
    model: PlanDetailModel,
    selected: Boolean,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    reduced: Boolean,
) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    val closeNow by rememberUpdatedState(onClose)
    // Un solo aviso por cierre: el desvanecido tarda unos milisegundos y un segundo toque no debe repetirlo.
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                if (!reduced) shown.animateTo(0f, tween(200))
                closeNow()
                // Si la hoja sigue aquí, el aviso no la retiró: vuelve a mostrarse en lugar de quedar invisible y bloqueando.
                delay(700)
                leaving = false
                shown.animateTo(1f, tween(200))
            }
        }
    }
    BlurOverlayDialog(onDismissRequest = leave, dismissOnBack = true, shown = { shown.value }) { blur ->
        LaunchedEffect(Unit) { if (!reduced) shown.animateTo(1f, tween(460)) }
        DetailSheet(model, selected, reduced, blur, { shown.value }, onChoose, leave)
    }
}

/**
 * El contenido de la hoja (todo menos la ventana). Está aparte de [PlanDetailOverlay] para poder pintarlo sin la ventana (la
 * vista previa de depuración lo enseña a 360 dp y con la letra al 130 % dentro de la página).
 */
@Composable
internal fun DetailSheet(
    model: PlanDetailModel,
    selected: Boolean,
    reduced: Boolean,
    blur: Boolean,
    shown: () -> Float,
    onChoose: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = model.card
    val accent = goalAccent(card.profile)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barPx by remember { mutableIntStateOf(0) }
    val headerH = statusTop + HEADER_STRIP + HEADER_BODY
    val headerPx = with(density) { headerH.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .testTag(PLAN_DETAIL_TAG)
            .semantics { paneTitle = card.title }
            .graphicsLayer { alpha = shown() }
            // Sin desenfoque del sistema el velo es del todo opaco (con 0,985 el texto de la página de atrás asomaba unos niveles
            // de gris); con él, 0,84: aquí hay mucho texto.
            .background(OverlayScrim.copy(alpha = if (blur) 0.84f else 1f)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            // La cabecera: la portada a sangre. Al abrir crece desde una tarjeta; al hacer scroll sube más despacio que
            // el contenido (parallax) y su ilustración se desplaza un poco más.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerH)
                    .clipToBounds(),
            ) {
                PlanCover(
                    model = card,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scroll.value * 0.5f
                            val e = shown()
                            val s = lerpF(0.88f, 1f, e)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            clip = true
                            shape = RoundedCornerShape((28f * (1f - e)).dp)
                        },
                    animate = true,
                    reduced = reduced,
                    parallax = { -(scroll.value / headerPx).coerceIn(0f, 1f) * 0.7f },
                    cornerRadius = 0.dp,
                    topInset = statusTop + HEADER_STRIP,
                    layout = CoverLayout.BANNER,
                )
            }
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val e = seg(shown(), 0.3f, 0.85f)
                        alpha = e
                        translationY = (1f - e) * 36.dp.toPx()
                    },
            ) {
                Spacer(Modifier.height(20.dp))
                DescriptionBlock(model.description, accent)
                if (model.mainExercises.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_EXERCISES)
                    ExerciseList(model.mainExercises.take(MAX_MAIN_EXERCISES), accent)
                }
                if (model.blocks.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_STRUCTURE)
                    BlockTimeline(model.blocks, accent)
                }
                if (model.week.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_WEEK)
                    WeekStrip(model.week, accent)
                }
                if (model.reasons.isNotEmpty()) {
                    SectionTitle(PlanCopy.SECTION_REASONS)
                    ReasonList(model.reasons, accent)
                }
                if (model.notes.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    NoteList(model.notes)
                }
                if (!model.attribution.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(model.attribution, style = WizardTypography.note, color = WizardColors.textFaint)
                }
            }
            Spacer(Modifier.height(with(density) { barPx.toDp() } + 24.dp))
        }
        // Un velo bajo la barra de estado: el contenido que sube no se mezcla con la hora ni con los iconos del sistema.
        // Sólido por toda la barra y solo después se funde (antes se fundía desde arriba y, a la altura de la hora, dejaba
        // pasar el 65 % del texto: «principales» se leía debajo de los iconos).
        val statusFraction = statusTop.value / (statusTop.value + 18f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop + 18.dp)
                .graphicsLayer { alpha = (scroll.value / 120f).coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        0f to OverlayScrim.copy(alpha = 0.97f),
                        statusFraction to OverlayScrim.copy(alpha = 0.97f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        CloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 2.dp, end = 10.dp),
        )
        BottomBar(
            selected = selected,
            navBottom = navBottom,
            onChoose = onChoose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barPx = it.height },
        )
    }
}

// ---------------------------------------------------------------- piezas fijas

/** La X de arriba a la derecha: un disco de vidrio neutro (blanco muy tenue y un filete uniforme) con su objetivo táctil de 48 dp. */
@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(PLAN_DETAIL_CLOSE_TAG)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = PlanCopy.CLOSE },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
                .background(WizardColors.glassFill, CircleShape)
                .border(1.dp, WizardColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val w = 2.dp.toPx()
                drawLine(WizardColors.text, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
                drawLine(WizardColors.text, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
            }
        }
    }
}

/** El pie fijo: una frase amable y el botón principal, sobre un velo que funde el contenido que pasa por debajo. */
@Composable
private fun BottomBar(selected: Boolean, navBottom: Dp, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.22f to OverlayScrim,
                    1f to OverlayScrim,
                ),
            )
            .padding(start = 24.dp, end = 24.dp, top = 46.dp, bottom = navBottom + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(PlanCopy.EDIT_FREELY, style = WizardTypography.note, color = WizardColors.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        PillButton(
            label = if (selected) PlanCopy.PROGRAM_CHOSEN else PlanCopy.CHOOSE_PROGRAM,
            container = if (selected) WizardColors.done else WizardColors.cta,
            content = if (selected) SymbolPalette.onOk else WizardColors.ctaContent,
            leadingCheck = selected,
            onClick = onChoose,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PLAN_DETAIL_CHOOSE_TAG),
        )
    }
}

/** El botón principal: una píldora de tinta (o verde si ya está elegido), sin ondas de Material. */
@Composable
private fun PillButton(
    label: String,
    container: Color,
    content: Color,
    leadingCheck: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(100), label = "pillPress")
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingCheck) {
            CheckGlyph(content, Modifier.size(16.dp), 2.4.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = WizardTypography.cta, color = content)
    }
}

/** Una marca de verificación de línea. */
@Composable
private fun CheckGlyph(color: Color, modifier: Modifier = Modifier, stroke: Dp = 2.dp) {
    Canvas(modifier) {
        val w = stroke.toPx()
        drawLine(color, Offset(size.width * 0.10f, size.height * 0.54f), Offset(size.width * 0.40f, size.height * 0.84f), w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.84f), Offset(size.width * 0.92f, size.height * 0.18f), w, StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- secciones

@Composable
private fun SectionTitle(text: String) {
    FittedDisplayText(
        text = text,
        maxSp = 20f,
        minSp = 15f,
        styleOf = { sp -> SECTION_STYLE.copy(fontSize = sp.sp, lineHeight = (sp * 1.3f).sp) },
        color = WizardColors.text,
        maxLines = 3,
        modifier = Modifier
            .padding(top = 34.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

/** La descripción: hasta cuatro líneas y, si hay más, «Ver más» que la despliega. */
@Composable
private fun DescriptionBlock(text: String, accent: Color) {
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Text(
            text = text,
            style = WizardTypography.body,
            color = WizardColors.text.copy(alpha = 0.88f),
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .plainClickable { expanded = !expanded },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (expanded) PlanCopy.SEE_LESS else PlanCopy.SEE_MORE,
                    style = WizardTypography.controlLabel,
                    color = accent,
                )
            }
        }
    }
}

/** Los ejercicios principales: una lista de línea, cada uno con el pictograma de su patrón y un filete debajo. */
@Composable
private fun ExerciseList(names: List<String>, accent: Color) {
    val divider = WizardColors.divider
    Column {
        for (name in names) {
            val pattern = remember(name) { exercisePatternOf(name) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PatternGlyph(pattern, accent, Modifier.size(42.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    text = name,
                    style = WizardTypography.cardTitle,
                    color = WizardColors.text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** Ancho mínimo y máximo de la columna de un bloque en la línea de tiempo horizontal. */
private val BLOCK_MIN_COL = 112.dp
private val BLOCK_MAX_COL = 210.dp

/** Aire a la derecha de cada bloque de la línea de tiempo horizontal. */
private val BLOCK_GAP = 14.dp

/** Cada texto de un bloque tiene su estilo: el nombre, las semanas y la frase. */
internal enum class BlockText { LABEL, WEEKS, DETAIL }

/**
 * ¿Cabe cada palabra de [block] entera en [widthPx]? [widthOf] da el ancho de un texto con el estilo de cada parte. Si alguna no
 * cabe, la columna partiría una palabra a media palabra y la estructura pasa a la disposición vertical.
 */
internal fun blockWordsFit(block: PlanBlockModel, widthPx: Float, widthOf: (String, BlockText) -> Float): Boolean {
    fun fits(text: String, kind: BlockText) = text.split(' ').filter { it.isNotEmpty() }.all { widthOf(it, kind) <= widthPx }
    return fits(block.label, BlockText.LABEL) && fits(block.weeksLabel, BlockText.WEEKS) && fits(block.detail, BlockText.DETAIL)
}

/**
 * La estructura: una línea de tiempo con un nodo por bloque, su nombre, sus semanas y una frase. Lo normal es horizontal: con
 * pocos bloques se reparten el ancho; con más, la línea se desliza de lado. Si alguna palabra no cabe en su columna (letra
 * grande, teléfono estrecho) pasa a vertical, con el riel a la izquierda, y no parte nada.
 */
@Composable
private fun BlockTimeline(blocks: List<PlanBlockModel>, accent: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val colW = maxOf(BLOCK_MIN_COL, minOf(BLOCK_MAX_COL, maxWidth / blocks.size))
        val fill = colW * blocks.size <= maxWidth
        // Con pocos bloques se reparten el ancho; con más, cada columna mide lo suyo y la línea se desliza.
        val columnWidth = if (fill) maxWidth / blocks.size else colW
        val innerPx = with(density) { (columnWidth - BLOCK_GAP).toPx() }
        val horizontal = remember(blocks, innerPx, density.fontScale, density.density) {
            blocks.all { block ->
                blockWordsFit(block, innerPx) { text, kind ->
                    val style = when (kind) {
                        BlockText.LABEL -> WizardTypography.cardTitle
                        BlockText.WEEKS -> BLOCK_WEEKS_STYLE
                        BlockText.DETAIL -> WizardTypography.note
                    }
                    measurer.measure(text = text, style = style, maxLines = 1, softWrap = false).size.width.toFloat()
                }
            }
        }
        if (horizontal) HorizontalBlocks(blocks, accent, columnWidth) else VerticalBlocks(blocks, accent)
    }
}

private val BLOCK_WEEKS_STYLE = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun HorizontalBlocks(blocks: List<PlanBlockModel>, accent: Color, columnWidth: Dp) {
    val scrollState = rememberScrollState()
    Row(
        Modifier
            // Si la línea se desliza, el borde derecho se funde: así el último bloque no parece cortado a media palabra.
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = 36.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, OverlayScrim),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState),
    ) {
        blocks.forEachIndexed { i, block ->
            val last = i == blocks.lastIndex
            Column(
                Modifier
                    .width(columnWidth)
                    .padding(end = BLOCK_GAP),
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                ) {
                    val cy = size.height / 2f
                    val r = 5.dp.toPx()
                    if (!last) drawLine(accent.copy(alpha = 0.35f), Offset(r * 2f, cy), Offset(size.width + BLOCK_GAP.toPx(), cy), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent, r, Offset(r, cy))
                }
                BlockTexts(block, accent)
            }
        }
    }
}

/** La estructura en vertical: el riel a la izquierda (un nodo por bloque) y los textos de cada bloque a su derecha. */
@Composable
private fun VerticalBlocks(blocks: List<PlanBlockModel>, accent: Color) {
    Column {
        blocks.forEachIndexed { i, block ->
            val last = i == blocks.lastIndex
            Row(Modifier.height(IntrinsicSize.Min)) {
                Canvas(
                    Modifier
                        .width(22.dp)
                        .fillMaxHeight(),
                ) {
                    val x = 5.dp.toPx()
                    val top = 11.dp.toPx()
                    if (!last) drawLine(accent.copy(alpha = 0.35f), Offset(x, top), Offset(x, size.height + top), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(accent, 5.dp.toPx(), Offset(x, top))
                }
                Column(Modifier.padding(bottom = if (last) 0.dp else 22.dp)) { BlockTexts(block, accent) }
            }
        }
    }
}

/** El nombre, las semanas y la frase de un bloque. */
@Composable
private fun BlockTexts(block: PlanBlockModel, accent: Color) {
    Text(block.label, style = WizardTypography.cardTitle, color = WizardColors.text)
    Text(
        text = block.weeksLabel,
        style = BLOCK_WEEKS_STYLE,
        color = accent,
        modifier = Modifier.padding(top = 2.dp),
    )
    if (block.detail.isNotBlank()) {
        Text(
            text = block.detail,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Por qué este programa: una marca de verificación del acento y una frase por razón. */
@Composable
private fun ReasonList(reasons: List<String>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (reason in reasons) {
            Row(verticalAlignment = Alignment.Top) {
                CheckGlyph(accent, Modifier.padding(top = 4.dp).size(16.dp), 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = reason,
                    style = WizardTypography.bodySmall,
                    color = WizardColors.text.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Las notas honestas del programa, con un punto de tinta tenue. */
@Composable
private fun NoteList(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (note in notes) {
            Row(verticalAlignment = Alignment.Top) {
                Canvas(Modifier.padding(top = 8.dp).size(10.dp)) {
                    drawCircle(WizardColors.textFaint, 2.5.dp.toPx(), Offset(size.width / 2f, size.height / 2f))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = note,
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- la semana

/**
 * Tu semana: los siete días como círculos con su inicial. Los de entreno van en el acento (el principal, con un punto).
 * Si los títulos caben bajo su círculo, cada día de entreno lleva debajo su título corto y sus minutos; si no (pantalla
 * estrecha o letra grande), los círculos van solos y las sesiones se enumeran debajo, una por fila.
 */
@Composable
private fun WeekStrip(week: List<PlanDayModel>, accent: Color) {
    val byDay = remember(week) { week.filter { it.day in 1..7 }.associateBy { it.day } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val colW = (maxWidth - gap * 6) / 7
        val colPx = with(density) { colW.toPx() }
        val captions = remember(byDay, colPx, density.fontScale) {
            widestTitlePx(byDay.values.map { it.title }) { text ->
                measurer.measure(text = text, style = DAY_CAPTION, maxLines = 1, softWrap = false).size.width.toFloat()
            } <= colPx
        }
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Sin pies de texto la fila es solo un dibujo: los datos los anuncian las filas de debajo.
                    .then(if (captions) Modifier else Modifier.clearAndSetSemantics { }),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (d in 1..7) DayColumn(d, byDay[d], accent, captions, Modifier.weight(1f))
            }
            if (!captions) {
                Spacer(Modifier.height(14.dp))
                for (day in byDay.values.sortedBy { it.day }) SessionRow(day, accent)
            }
            if (byDay.values.any { it.isMain }) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
                    Spacer(Modifier.width(8.dp))
                    Text("Sesión principal", style = WizardTypography.note, color = WizardColors.textMuted)
                }
            }
        }
    }
}

private val DAY_CAPTION = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold)

/** El ancho (píxeles) del título más ancho de los días de entreno: lo que decide si caben bajo su círculo. */
internal fun widestTitlePx(titles: List<String>, widthOf: (String) -> Float): Float =
    titles.maxOfOrNull { widthOf(it) } ?: 0f

/** El círculo de un día: contorno del acento si se entrena, tenue si se descansa; la inicial dentro y, si es el principal, un punto. */
@Composable
private fun DayCircle(day: Int, trained: Boolean, main: Boolean, accent: Color, diameter: Dp) {
    val initial = PlanCopy.WEEKDAY_INITIALS[day - 1]
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = if (trained) 2.dp.toPx() else 1.dp.toPx()
            val color = if (trained) accent else WizardColors.text.copy(alpha = 0.18f)
            drawCircle(color, size.minDimension / 2f - w / 2f, style = Stroke(w))
            if (main) drawCircle(accent, 4.dp.toPx(), Offset(size.width * 0.86f, size.height * 0.14f))
        }
        Text(
            text = initial,
            style = WizardTypography.controlLabel,
            color = if (trained) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** Un día de la fila de círculos; con [captions], los de entreno llevan debajo su título corto y sus minutos. */
@Composable
private fun DayColumn(day: Int, model: PlanDayModel?, accent: Color, captions: Boolean, modifier: Modifier = Modifier) {
    val description = weekDayDescription(day, model)
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DayCircle(day, model != null, model?.isMain == true, accent, 38.dp)
        if (captions && model != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = model.title,
                style = DAY_CAPTION,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = "${model.minutes} min",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Una sesión de la semana, en una fila: su círculo, el título y «75 min · 6 ejercicios» (más un punto si es la principal). */
@Composable
private fun SessionRow(model: PlanDayModel, accent: Color) {
    val description = weekDayDescription(model.day, model)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircle(model.day, trained = true, main = false, accent = accent, diameter = 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(model.title, style = WizardTypography.cardTitle, color = WizardColors.text)
            Text(
                text = "${model.minutes} min · ${model.exerciseCount} ejercicios",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
        }
        if (model.isMain) Canvas(Modifier.size(10.dp)) { drawCircle(accent) }
    }
}

/** Lo que anuncia TalkBack de un día: «Lunes: Torso, 60 min, 5 ejercicios, sesión principal» o «Martes: descanso». */
internal fun weekDayDescription(day: Int, model: PlanDayModel?): String {
    val name = PlanCopy.WEEKDAY_NAMES[day - 1].replaceFirstChar { it.uppercase() }
    if (model == null) return "$name: descanso"
    return buildString {
        append(name).append(": ").append(model.title).append(", ").append(model.minutes).append(" min, ")
        append(model.exerciseCount).append(" ejercicios")
        if (model.isMain) append(", sesión principal")
    }
}
