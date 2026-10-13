package com.example.kpkn.screens.onboarding.rings

/*
 * Intro de RINGS («Tus RINGS, tu recuperación de un vistazo»): un overlay a pantalla completa que el anfitrión pinta encima
 * del paso `RINGS_INTRO` mientras el cursor está en él. La ruta no cambia. Tres actos: el umbral («Un último paso para
 * comenzar»), los tres anillos y qué mide cada uno, y «Pruébalo»: tres invitaciones para calibrarlos con la demostración real
 * (`RingsIntroSimulator`). No escribe en el borrador: «Empezar» confirma el paso y atrás vuelve al hito de Nutrición.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.auge.RingChannelId
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.domain.auge.RingsIntroState
import com.example.kpkn.domain.auge.RingsIntroValues
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.entreno.pingPong
import com.example.kpkn.screens.onboarding.design.entreno.plan.BlurOverlayDialog
import com.example.kpkn.screens.onboarding.design.entreno.plan.OverlayScrim
import com.example.kpkn.screens.onboarding.design.entreno.plan.plainClickable
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.PagerDots
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Actos del overlay, en orden. */
private const val ACT_THRESHOLD = 0
private const val ACT_RINGS = 1
private const val ACT_TRY = 2
private const val ACT_COUNT = 3

/** Invitaciones de «Pruébalo»: simular, recorrer la línea de tiempo y ajustar Energía. */
private const val INVITE_COUNT = 3

/** Horas que hay que pasar en la línea de tiempo para contar la segunda invitación como hecha. */
private const val TIMELINE_DONE_HOURS = 24f

/** Altura de la torre: el umbral, y el mismo tamaño que el acto de los anillos en los actos 2 y 3. */
private val ThresholdTowerHeight = 200.dp
private val RingsActTowerHeight = 220.dp
private val TryTowerHeight = RingsActTowerHeight

/** Ancho común de los botones de los actos («Siguiente», «Ver cómo se mueven» y «Empezar»). */
private val RingsCtaWidth = 256.dp

/** «Siguiente» del umbral se activa a los 1,2 s (con movimiento reducido, al instante). */
private const val NEXT_ENABLED_MILLIS = 1_200L

/** Caída de cada anillo de abajo arriba: 300 ms y un anillo tras otro cada 120 ms; cae desde 36 dp. */
private const val RING_DROP_MILLIS = 300
private const val RING_STAGGER_MILLIS = 120L
private val RingDropDistance = 36.dp

/** El recorrido enciende cada anillo 1,6 s, en el orden de la lista de anillos de la intro. */
private const val WALK_STEP_MILLIS = 1_600L
private val WalkOrder = listOf(RingChannelId.MUSCULOS, RingChannelId.ENERGIA, RingChannelId.COLUMNA)

/** Fundido de la línea que cambia al encender un anillo. */
private const val HIGHLIGHT_FADE_MILLIS = 160

/** Ritmo de las pistas: el aro late en 1,2 s; la mano tarda 1,2 s en tocar y 1,6 s en ir y volver. */
private const val HALO_MILLIS = 1_200
private const val TAP_HAND_MILLIS = 1_200
private const val DRAG_HAND_MILLIS = 1_600

/** La mano de cada pista: 24 dp de lienzo. */
private val HandSize = 24.dp

/** Torre completa (demostración sin sesión): todos los anillos a 100. */
private val FullTower = TowerRingValues(columna = 100, musculos = 100, energia = 100)

/** Nota bajo el deslizador cuando la invitación de Energía está hecha: la frase exacta de la intro de siempre. */
private const val ENERGY_NOTE =
    "Si no coincide con cómo te sientes, ajústalo: KPKN usa tus ajustes para afinar tu ritmo de recuperación con cada entreno."

/**
 * Asentamiento de la caída: easeOutBack suave (un sobrepaso de unos 5 %) antes de quedarse en su sitio.
 */
private val SettleEasing = object : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction - 1f
        return 1f + 2.2f * t * t * t + 1.2f * t * t
    }
}

/**
 * Lo que se ve del overlay en un instante: el acto, la invitación de turno (0–3, 3 = todas hechas), si se ha saltado, la
 * demostración, el anillo encendido, si «Siguiente» ya está habilitado, si hay movimiento reducido y si la persona arrastra.
 */
internal data class RingsIntroScene(
    val act: Int,
    val invite: Int,
    val skipped: Boolean,
    val demo: RingsIntroState,
    val highlighted: RingChannelId?,
    val nextReady: Boolean,
    val reduced: Boolean,
    val dragging: Boolean,
) {
    /** Se acabó el recorrido: todas las invitaciones hechas o saltado. Aparece «Empezar». */
    val closing: Boolean get() = skipped || invite >= INVITE_COUNT
}

/** Las acciones del overlay. Quien tiene el estado las compone (el overlay vivo, o la hoja de render, que no hace nada). */
internal data class RingsIntroActions(
    val onSkip: () -> Unit,
    val onNext: () -> Unit,
    val onStart: () -> Unit,
    val onSimulate: () -> Unit,
    val onHours: (Float) -> Unit,
    val onDragChange: (Boolean) -> Unit,
    val onEnergy: (Int) -> Unit,
    val onHighlight: (RingChannelId) -> Unit,
)

/**
 * La intro de RINGS a pantalla completa, sobre la página del alta. «Saltar» lleva al final del tercer acto con «Empezar»
 * activo; «Empezar» confirma el paso; atrás del sistema ([onBack]) vuelve al hito de Nutrición.
 */
@Composable
internal fun RingsIntroOverlay(onContinue: () -> Unit, onBack: () -> Unit) {
    val reduced = wizardReducedMotion()
    val scope = rememberCoroutineScope()
    val shownAnim = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    // Un solo aviso por apertura, como el prólogo: si la acción no avanza, el overlay vuelve a mostrarse.
    val leave: (() -> Unit) -> Unit = { action ->
        if (!leaving) {
            leaving = true
            scope.launch {
                shownAnim.animateTo(0f, tween(200))
                action()
                delay(700)
                leaving = false
                shownAnim.animateTo(1f, tween(200))
            }
        }
    }
    var act by remember { mutableIntStateOf(ACT_THRESHOLD) }
    var invite by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(RingsIntroState()) }
    var highlighted by remember { mutableStateOf<RingChannelId?>(null) }
    var walkStopped by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var nextReady by remember { mutableStateOf(reduced) }

    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(NEXT_ENABLED_MILLIS)
            nextReady = true
        }
    }
    // El recorrido solo: cada anillo se enciende por turno y al acabar no queda ninguno encendido. Tocar un anillo
    // cambia `walkStopped` y lo cancela.
    LaunchedEffect(act, walkStopped) {
        if (act != ACT_RINGS || reduced || walkStopped) return@LaunchedEffect
        for (ring in WalkOrder) {
            highlighted = ring
            delay(WALK_STEP_MILLIS)
        }
        highlighted = null
    }

    val scene = RingsIntroScene(
        act = act,
        invite = invite,
        skipped = skipped,
        demo = demo,
        highlighted = highlighted,
        nextReady = nextReady,
        reduced = reduced,
        dragging = dragging,
    )
    val actions = RingsIntroActions(
        onSkip = {
            skipped = true
            act = ACT_TRY
        },
        onNext = { act = (act + 1).coerceAtMost(ACT_TRY) },
        onStart = { leave(onContinue) },
        onSimulate = {
            if (invite == 0) {
                demo = RingsIntroSimulator.toggleSession(demo)
                invite = 1
            }
        },
        onHours = { hours ->
            if (invite == 1) {
                demo = RingsIntroSimulator.withHours(demo, hours)
                if (demo.hoursAfter > TIMELINE_DONE_HOURS) {
                    dragging = false
                    invite = 2
                }
            }
        },
        onDragChange = { dragging = it },
        onEnergy = { value ->
            if (invite == 2) {
                demo = RingsIntroSimulator.withAdjustment(demo, RingChannelId.ENERGIA, value)
                invite = 3
            }
        },
        onHighlight = { ring ->
            walkStopped = true
            highlighted = ring
        },
    )

    BlurOverlayDialog(
        onDismissRequest = { leave(onBack) },
        dismissOnBack = true,
        shown = { shownAnim.value },
    ) { blur ->
        LaunchedEffect(Unit) { shownAnim.animateTo(1f, tween(280)) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shownAnim.value }
                // El mismo velo que el prólogo: sin desenfoque del sistema, debe tapar casi toda la página de atrás.
                .background(OverlayScrim.copy(alpha = if (blur) 0.52f else 0.96f)),
        ) {
            RingsIntroContent(scene = scene, actions = actions, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * El cuerpo del overlay sin estado propio: lo que se ve con [scene] y las acciones de [actions]. Lo usan el overlay y la hoja
 * de render.
 */
@Composable
internal fun RingsIntroContent(scene: RingsIntroScene, actions: RingsIntroActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag("rings-intro-overlay")
            .semantics { paneTitle = "Tus RINGS" }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = WizardSpacing.gutter, vertical = 16.dp),
    ) {
        RingsIntroTopBar(act = scene.act, showSkip = !scene.closing, onSkip = actions.onSkip)
        Crossfade(
            targetState = scene.act,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "ringsIntroAct",
        ) { act ->
            when (act) {
                ACT_THRESHOLD -> ThresholdAct(scene, actions)
                ACT_RINGS -> RingsAct(scene, actions)
                else -> TryAct(scene, actions)
            }
        }
    }
}

/** Fila superior: los puntos de progreso a la izquierda y «Saltar» a la derecha, con 48 dp de área táctil. */
@Composable
private fun RingsIntroTopBar(act: Int, showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WizardSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PagerDots(count = ACT_COUNT, current = act)
        if (showSkip) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
                    .testTag("rings-intro-skip")
                    .plainClickable(onClickLabel = "Saltar") { onSkip() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(text = "Saltar", style = WizardTypography.controlLabel, color = WizardColors.textMuted)
            }
        }
    }
}

/** Primer acto: la torre se arma de abajo arriba y debajo, el umbral. «Siguiente» se activa a los 1,2 s. */
@Composable
private fun ThresholdAct(scene: RingsIntroScene, actions: RingsIntroActions) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RingsDropTower(
                reduced = scene.reduced,
                modifier = Modifier.fillMaxWidth().height(ThresholdTowerHeight).testTag("rings-intro-tower"),
            )
            Spacer(Modifier.height(24.dp))
            Text(text = "RINGS", style = WizardTypography.caption, color = KpknRingColors.energia)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Un último paso para comenzar",
                style = WizardTypography.stepTitle,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Necesitamos conocer tu nivel de fatiga y el estado de tus músculos para tus RINGS.",
                style = WizardTypography.body,
                color = WizardColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        RingsCta(
            label = "Siguiente",
            tag = "rings-intro-next",
            onClick = actions.onNext,
            modifier = Modifier.padding(top = 16.dp),
            enabled = scene.nextReady,
        )
    }
}

/**
 * La torre del logo armada de abajo arriba: cada anillo cae desde arriba y asienta con un pequeño sobrepaso. Cada anillo es
 * una torre con solo su canal visible, para moverlo por separado. Con movimiento reducido, la torre completa al instante.
 */
@Composable
private fun RingsDropTower(reduced: Boolean, modifier: Modifier = Modifier) {
    val falls = remember { List(RingChannel.entries.size) { Animatable(0f) } }
    LaunchedEffect(reduced) {
        if (reduced) {
            falls.forEach { it.snapTo(1f) }
        } else {
            falls.forEachIndexed { index, fall ->
                launch {
                    delay(RING_STAGGER_MILLIS * index)
                    fall.animateTo(1f, tween(durationMillis = RING_DROP_MILLIS, easing = SettleEasing))
                }
            }
        }
    }
    Box(modifier) {
        RingChannel.entries.forEachIndexed { index, channel ->
            KpknTowerRings(
                values = FullTower,
                visible = setOf(channel),
                animate = false,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val fall = falls[index].value
                    alpha = fall.coerceIn(0f, 1f)
                    translationY = (1f - fall) * -RingDropDistance.toPx()
                },
            )
        }
    }
}

/**
 * Segundo acto: la torre más grande y, debajo, el anillo encendido con su nombre en su color y lo que mide. Con movimiento
 * reducido no hay recorrido: los tres textos se leen a la vez.
 */
@Composable
private fun RingsAct(scene: RingsIntroScene, actions: RingsIntroActions) {
    val values = RingsIntroSimulator.valuesAt(scene.demo)
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // El bloque va centrado en el hueco entre la barra y el botón; con letra grande crece y se desplaza.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "TUS TRES ANILLOS", style = WizardTypography.caption, color = WizardColors.textFaint)
                Spacer(Modifier.height(16.dp))
                KpknTowerRings(
                    values = values.tower(),
                    highlighted = scene.highlighted?.towerChannel(),
                    onRingTap = { ring -> actions.onHighlight(ring.domainChannel()) },
                    modifier = Modifier.fillMaxWidth().height(RingsActTowerHeight).testTag("rings-intro-tower"),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = highlightText(null),
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                RingsLegend(highlighted = scene.highlighted, reduced = scene.reduced)
            }
        }
        RingsCta(
            label = "Ver cómo se mueven",
            tag = "rings-intro-next",
            onClick = actions.onNext,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** Orden de la leyenda, de arriba abajo, como la torre: Energía, Músculos y Columna. */
private val LegendOrder = listOf(RingChannelId.ENERGIA, RingChannelId.MUSCULOS, RingChannelId.COLUMNA)

/** Tinta de las filas de la leyenda que no están destacadas. */
private const val LEGEND_DIM_ALPHA = 0.55f

/**
 * Leyenda de los tres anillos bajo la torre, siempre visible: punto de color, nombre en su acento y su frase de una línea.
 * El anillo destacado (por toque o por el recorrido) va en tinta plena y los demás al 55 %; sin nada destacado, las tres
 * completas. Con movimiento reducido se ven las tres completas.
 */
@Composable
private fun RingsLegend(highlighted: RingChannelId?, reduced: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendOrder.forEach { ring ->
            val emphasized = reduced || highlighted == null || highlighted == ring
            val fade by animateFloatAsState(
                targetValue = if (emphasized) 1f else LEGEND_DIM_ALPHA,
                animationSpec = tween(durationMillis = HIGHLIGHT_FADE_MILLIS),
                label = "ringsIntroLegendFade",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade }
                    .testTag("rings-intro-legend-${ring.name}")
                    .semantics { if (highlighted == ring) stateDescription = "Destacado" },
                verticalAlignment = Alignment.Top,
            ) {
                // El punto se alinea con la línea del nombre (la altura de su interlineado).
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(8.dp)
                        .background(KpknRingColors.of(ring.towerChannel()), CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = ringName(ring),
                        style = WizardTypography.controlLabel,
                        color = KpknRingColors.of(ring.towerChannel()),
                    )
                    Text(
                        text = highlightText(ring),
                        style = WizardTypography.body,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

/** Tercer acto, «Pruébalo»: la torre con la demostración y las tres invitaciones en orden; al terminar o saltar, «Empezar». */
@Composable
private fun TryAct(scene: RingsIntroScene, actions: RingsIntroActions) {
    val values = RingsIntroSimulator.valuesAt(scene.demo)
    // Al calibrar Energía, la torre enciende ese anillo; en las demás invitaciones, los tres a la vez.
    val highlight = if (!scene.closing && scene.invite == 2) RingChannelId.ENERGIA else null
    var preview by remember { mutableStateOf<Int?>(null) }
    val energy = preview ?: values.energia
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // El bloque va centrado en el hueco entre la barra y «Empezar»; con letra grande crece y se desplaza.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "PRUÉBALO", style = WizardTypography.caption, color = WizardColors.textFaint)
                Spacer(Modifier.height(12.dp))
                KpknTowerRings(
                    values = values.tower(),
                    // Sin caída al entrar en este acto: la torre ya trae el estado de la demostración.
                    from = values.tower(),
                    highlighted = highlight?.towerChannel(),
                    animate = !scene.dragging,
                    modifier = Modifier.fillMaxWidth().height(TryTowerHeight).testTag("rings-intro-tower"),
                )
                Spacer(Modifier.height(20.dp))
                InviteBlock(index = 0, scene = scene, prompt = "Toca para simular un entreno pesado") {
                    SimulateControl(reduced = scene.reduced, onClick = actions.onSimulate)
                }
                Spacer(Modifier.height(20.dp))
                InviteBlock(index = 1, scene = scene, prompt = "Arrastra para ver cómo te recuperas") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Después del entreno", style = WizardTypography.controlLabel, color = WizardColors.text)
                        Text(
                            text = "${scene.demo.hoursAfter.roundToInt()} h",
                            style = WizardTypography.header,
                            color = WizardColors.text,
                            modifier = Modifier.testTag("rings-intro-hours"),
                        )
                    }
                    TimelineControl(
                        hours = scene.demo.hoursAfter,
                        reduced = scene.reduced,
                        onHours = actions.onHours,
                        onDragChange = actions.onDragChange,
                    )
                }
                Spacer(Modifier.height(20.dp))
                InviteBlock(index = 2, scene = scene, prompt = "¿Te sientes distinto? Ajústalo", note = ENERGY_NOTE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Energía", style = WizardTypography.controlLabel, color = KpknRingColors.energia)
                        Text(text = "$energy %", style = WizardTypography.header, color = WizardColors.text)
                    }
                    EnergySliderControl(
                        value = energy,
                        reduced = scene.reduced,
                        onPreview = { preview = it },
                        onCommit = { chosen ->
                            preview = null
                            actions.onEnergy(chosen)
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = scene.closing,
            enter = fadeIn(tween(320)),
            exit = fadeOut(tween(160)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Con unas preguntas rápidas calculamos cómo llegas hoy.",
                    style = WizardTypography.stepSubtitle,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                RingsCta(label = "Empezar", tag = "rings-intro-start", onClick = actions.onStart)
            }
        }
    }
}

/**
 * Una invitación: su línea (con la marca de hecha o pendiente) y, solo mientras es la de turno, su control. Hecha, deja su
 * nota si la tiene.
 */
@Composable
private fun InviteBlock(
    index: Int,
    scene: RingsIntroScene,
    prompt: String,
    note: String? = null,
    control: @Composable () -> Unit,
) {
    val done = scene.invite > index
    val active = !scene.closing && scene.invite == index
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InvitePrompt(text = prompt, done = done, active = active, reduced = scene.reduced)
        if (active) control()
        if (done && note != null) {
            Text(text = note, style = WizardTypography.note, color = WizardColors.textMuted)
        }
    }
}

/** La línea de una invitación: un aro (pendiente o de turno) o el check que se dibuja al hacerla, y el texto. */
@Composable
private fun InvitePrompt(text: String, done: Boolean, active: Boolean, reduced: Boolean) {
    val pen = remember { SymbolPen() }
    val mark = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        if (done && !reduced) mark.animateTo(1f, tween(380)) else mark.snapTo(if (done) 1f else 0f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = if (done) "Hecha" else if (active) "En curso" else "Pendiente"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) {
            if (done) {
                pen.doneBadge(this, size.width / 2f, size.height / 2f, size.minDimension / 2f, mark.value)
            } else {
                drawCircle(
                    color = if (active) WizardColors.text else WizardColors.textFaint,
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = WizardTypography.controlLabel,
            color = if (done || active) WizardColors.text else WizardColors.textFaint,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Invitación 1: un botón-símbolo con una mancuerna de contorno (48 dp). Tocarlo simula el entreno pesado. */
@Composable
private fun SimulateControl(reduced: Boolean, onClick: () -> Unit) {
    val pen = remember { SymbolPen() }
    Box(modifier = Modifier.size(WizardSpacing.touchTarget)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("rings-intro-simulate")
                .semantics { contentDescription = "Simular un entreno pesado" }
                .plainClickable(onClickLabel = "Simular un entreno pesado") { onClick() },
        ) {
            pen.begin(this, 1f, SymbolPalette.musculo)
            val k = size.width / 64f
            pen.scaled(k, k, 0f, 0f) {
                pen.dumbbell(32f, 32f, 24f, -24f, heads = pen.accent, handle = pen.accent, w = SymbolStroke.BODY)
            }
        }
        if (!reduced) {
            InviteHalo(shape = CircleShape, modifier = Modifier.matchParentSize())
            InviteHand(
                gesture = HandGesture.TAP,
                travel = 0.dp,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp),
            )
        }
    }
}

/** Invitación 2: la línea de tiempo de 0 a 72 h con su pista. */
@Composable
private fun TimelineControl(
    hours: Float,
    reduced: Boolean,
    onHours: (Float) -> Unit,
    onDragChange: (Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(RingsTimelineHeight)) {
        RingsIntroTimelineCanvas(
            hours = hours,
            onHours = onHours,
            onDragChange = onDragChange,
            modifier = Modifier.fillMaxWidth().height(RingsTimelineHeight),
        )
        if (!reduced) {
            InviteHalo(shape = RoundedCornerShape(12.dp), modifier = Modifier.matchParentSize())
            // Un poco por encima del eje: así la mano no tapa las etiquetas de 0 h y 72 h.
            InviteHand(
                gesture = HandGesture.DRAG,
                travel = maxWidth - HandSize,
                modifier = Modifier.align(Alignment.Center).offset(y = (-6).dp),
            )
        }
    }
}

/** Invitación 3: el deslizador de Energía, el de hoy, con su pista. Al soltar, el anillo salta a ese valor. */
@Composable
private fun EnergySliderControl(
    value: Int,
    reduced: Boolean,
    onPreview: (Int?) -> Unit,
    onCommit: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(WizardSpacing.touchTarget)) {
        RingsChannelSlider(
            accent = KpknRingColors.energia,
            name = "Energía",
            value = value,
            onPreview = onPreview,
            onCommit = onCommit,
            tag = "rings-intro-slider",
        )
        if (!reduced) {
            InviteHalo(shape = RoundedCornerShape(24.dp), modifier = Modifier.matchParentSize())
            InviteHand(
                gesture = HandGesture.DRAG,
                travel = maxWidth - HandSize,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** El aro que late alrededor del control: escala de 1 a 1,08 en 1,2 s, de ida y vuelta. */
@Composable
private fun InviteHalo(shape: Shape, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ringsIntroHalo")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HALO_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringsIntroHaloPulse",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .border(width = 1.dp, color = WizardColors.text.copy(alpha = 0.5f), shape = shape),
    )
}

/** Gesto de la mano de la pista: tocar (baja y sube en el sitio) o arrastrar (de un lado a otro, por [travel]). */
private enum class HandGesture { TAP, DRAG }

/** La mano de la pista: 24 dp dibujados con el pincel del kit de símbolos. Repite el gesto sin parar. */
@Composable
private fun InviteHand(gesture: HandGesture, travel: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ringsIntroHand")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (gesture == HandGesture.TAP) TAP_HAND_MILLIS else DRAG_HAND_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringsIntroHandPhase",
    )
    val pen = remember { SymbolPen() }
    Canvas(
        modifier = modifier
            .size(HandSize)
            .graphicsLayer {
                val p = pingPong(phase)
                translationY = if (gesture == HandGesture.TAP) p * 4.dp.toPx() else 0f
                translationX = if (gesture == HandGesture.DRAG) (p - 0.5f) * travel.toPx() else 0f
            },
    ) {
        pen.begin(this, 0f, SymbolPalette.ink)
        val k = size.width / 24f
        pen.scaled(k, k, 0f, 0f) { pen.pointingHand() }
    }
}

/** Un índice levantado sobre un puño, en el lienzo lógico de 24 unidades. */
private fun SymbolPen.pointingHand() {
    val c = ink
    line(12f, 2.5f, 12f, 13f, c, SymbolStroke.BODY)
    box(6f, 12f, 18f, 21.5f, 4f, c, SymbolStroke.LINE)
    line(6f, 15.5f, 3.5f, 13.5f, c, SymbolStroke.LINE)
}

/**
 * El botón de llamada a la acción de los actos: la píldora de tinta cálida del prólogo. Aparece con un fundido cuando se
 * habilita.
 */
@Composable
private fun RingsCta(
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fade by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "ringsIntroCtaFade",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = WizardColors.cta,
            contentColor = WizardColors.ctaContent,
            disabledContainerColor = WizardColors.cta,
            disabledContentColor = WizardColors.ctaContent,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .width(RingsCtaWidth)
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(tag)
            .graphicsLayer { alpha = fade },
    ) {
        Text(text = label, style = WizardTypography.cta)
    }
}

/** Los valores de la demostración como torre. */
private fun RingsIntroValues.tower(): TowerRingValues = TowerRingValues(columna = columna, musculos = musculos, energia = energia)
