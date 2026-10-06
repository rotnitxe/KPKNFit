package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.bodyFatDescriptor
import com.example.kpkn.domain.nutrition.physiqueSliderPositionForBodyFat
import com.example.kpkn.screens.nutrition.WizardFemaleFrames
import com.example.kpkn.screens.nutrition.WizardMaleFrames
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Marca de prueba del porcentaje grande bajo el escenario («25 %»). */
const val BODY_FAT_VALUE_TAG = "setup-bodyfat-value"

/** Marca de prueba de la frase que acompaña al porcentaje. */
const val BODY_FAT_DESCRIPTOR_TAG = "setup-bodyfat-descriptor"

/** Marca de prueba de cada botón de figura: `setup-bodyfat-model-female` / `setup-bodyfat-model-male`. */
fun bodyFatModelTag(model: String): String = "setup-bodyfat-model-$model"

// ─── Medidas ─────────────────────────────────────────────────────────────────
//
// En un teléfono de 390 dp de ancho (la página deja 24 dp a cada lado) el escenario mide 342 × 380 dp:
//   · la regla ocupa ≈ 54 dp a la derecha (la fija el número más ancho),
//   · la figura (2:3, 1024 × 1536 px) se centra en los ≈ 288 dp que quedan y, con `Fit`, llena los 380 dp de
//     alto: ≈ 253 dp de ancho, así que cabe con holgura y se pinta sin tarjeta ni marco, sobre el negro de la página,
//   · los dos botones de figura van pegados a la esquina superior izquierda, sobre el hueco vacío junto a la cabeza.

/** Alto máximo del escenario (figura y regla). */
private const val STAGE_MAX_HEIGHT_DP = 380f

/** Alto mínimo del escenario: por debajo la figura deja de ser útil. */
private const val STAGE_MIN_HEIGHT_DP = 220f

/**
 * Parte de la altura de pantalla que puede ocupar el escenario. Con 0,46 el tope de 380 dp se alcanza en pantallas de
 * 826 dp o más y en una de 640 dp el escenario baja a 294 dp, para que figura y regla sigan cabiendo sobre el botón.
 */
private const val STAGE_SCREEN_FRACTION = 0.46f

/** Alto del escenario, en dp, para una pantalla de [screenHeightDp]: [STAGE_MAX_HEIGHT_DP] salvo en pantallas bajas. */
fun bodyFatStageHeightDp(screenHeightDp: Float): Float =
    if (screenHeightDp.isNaN()) {
        STAGE_MAX_HEIGHT_DP
    } else {
        (screenHeightDp * STAGE_SCREEN_FRACTION).coerceIn(STAGE_MIN_HEIGHT_DP, STAGE_MAX_HEIGHT_DP)
    }

/**
 * Fotograma de la figura para un porcentaje: la posición del slider (inversa de `bodyFatForSliderPos`) repartida
 * entre los [frameCount] fotogramas, de 0 (el más definido, hasta 10 %) a `frameCount - 1` (el de más volumen, desde 40 %).
 */
fun bodyFatFrameIndex(percent: Double, frameCount: Int): Int {
    if (frameCount <= 1) return 0
    val position = physiqueSliderPositionForBodyFat(percent)
    return (((position - 1f) / 6f) * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
}

/** Diámetro visible de los botones de figura. */
private val ModelButtonSize = 36.dp

/** Zona táctil de cada botón de figura: nunca menos de 48 dp aunque el círculo mida 36. */
private val ModelButtonTouchSize = WizardSpacing.touchTarget

/** Lado del símbolo dentro del círculo; el glifo ocupa el 78 % de su lienzo, así que queda en ≈ 19 dp de tinta. */
private val ModelGlyphSize = 24.dp

/** Borde del círculo no seleccionado: blanco al 24 %. */
private val ModelButtonBorderColor = Color.White.copy(alpha = 0.24f)

/** Opacidad de la lectura mientras el valor no está declarado. */
private const val UNDECLARED_READING_ALPHA = 0.5f

/** Cuánto se espera a que el borrador refleje el último valor soltado antes de volver a lo que diga el borrador. */
private const val PENDING_VALUE_GRACE_MILLIS = 1_000L

/**
 * Paso de grasa corporal: el escenario (figura grande, botones de figura y regla vertical) y, debajo, la lectura
 * (porcentaje grande y una frase). Sin tarjetas ni marcos: la página es negra.
 *
 * Stateless respecto al ViewModel: recibe lo que dice el borrador ([percent], [model], [declared]) y devuelve los
 * cambios por callbacks. Solo guarda lo que necesita para responder al dedo sin esperar al guardado: mientras se
 * arrastra, el valor «en vivo» manda sobre [percent] (la figura, la lectura y el cursor lo siguen al instante) y
 * [onPercentChange] solo se llama al soltar, con el entero definitivo, para no escribir el borrador a cada entero.
 *
 * - [percent]: lo que dice el borrador (el declarado, el de Ajustes o el de la figura de arranque). Puede traer un
 *   decimal o salirse de la regla (un dato antiguo): la lectura dice el número real y el cursor queda en el extremo.
 * - [model]: `"female"` o `"male"`; cualquier otro valor pinta la masculina. Cambiar de figura solo cambia la figura.
 * - [declared]: false mientras [percent] es solo la posición de arranque; atenúa la lectura (alfa 0,5) hasta que se
 *   mueve la regla. Mover la regla (arrastrar o tocar) cuenta como declarar desde el primer contacto.
 */
@Composable
fun WizardBodyFatPicker(
    percent: Double,
    model: String?,
    declared: Boolean,
    onPercentChange: (Int) -> Unit,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Valor «en vivo»: lo que el dedo ha fijado y el borrador todavía no refleja. Sigue mandando tras soltar hasta que
    // el borrador lo alcanza (o, si no llega, hasta pasado un segundo) para que el cursor no salte hacia atrás un instante.
    var live by remember { mutableStateOf<Int?>(null) }
    var interacting by remember { mutableStateOf(false) }
    LaunchedEffect(live, interacting, percent) {
        val pending = live ?: return@LaunchedEffect
        if (interacting) return@LaunchedEffect
        if (percent == pending.toDouble()) {
            live = null
        } else {
            delay(PENDING_VALUE_GRACE_MILLIS)
            live = null
        }
    }
    val shownPercent = live?.toDouble() ?: percent
    val shownDeclared = declared || live != null

    val isFemale = model?.trim()?.equals("female", ignoreCase = true) == true
    val frames = if (isFemale) WizardFemaleFrames else WizardMaleFrames
    val stageHeight = bodyFatStageHeightDp(LocalConfiguration.current.screenHeightDp.toFloat()).dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Fotograma decorativo (la lectura de debajo dice lo mismo con palabras). ContentScale.Fit: toda la
                // figura a la vista, sin recortes, llenando el alto del escenario.
                Image(
                    painter = painterResource(frames[bodyFatFrameIndex(shownPercent, frames.size)]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .selectableGroup(),
                ) {
                    BodyFatModelButton(
                        mark = WizardGenderMark.FEMALE,
                        model = "female",
                        label = "Figura femenina",
                        selected = isFemale,
                        onSelect = { onModelChange("female") },
                    )
                    BodyFatModelButton(
                        mark = WizardGenderMark.MALE,
                        model = "male",
                        label = "Figura masculina",
                        selected = !isFemale,
                        onSelect = { onModelChange("male") },
                    )
                }
            }
            WizardBodyFatRuler(
                percent = shownPercent,
                declared = shownDeclared,
                onPercentChange = { value ->
                    interacting = true
                    live = value
                },
                onPercentChangeFinished = { value ->
                    interacting = false
                    live = value
                    onPercentChange(value)
                },
                modifier = Modifier.fillMaxHeight(),
            )
        }

        BodyFatReading(percent = shownPercent, model = model, declared = shownDeclared)
    }
}

/**
 * Botón de figura: un círculo de [ModelButtonSize] con el símbolo ♀ o ♂ dentro de una zona táctil de 48 dp. Es un
 * botón de radio (`Role.RadioButton` + `selected`). Seleccionado, círculo blanco con el símbolo oscuro; si no,
 * borde de 1 dp en blanco al 24 % y símbolo gris. El círculo va centrado en su zona: el botón mide 48 dp y la
 * pareja, 96.
 */
@Composable
private fun BodyFatModelButton(
    mark: WizardGenderMark,
    model: String,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = wizardReducedMotion()
    val fill by animateColorAsState(
        targetValue = if (selected) WizardColors.markFill else Color.Transparent,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 140),
        label = "bodyfat-model-fill",
    )
    val glyph by animateColorAsState(
        targetValue = if (selected) WizardColors.ctaContent else WizardColors.textFaint,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 140),
        label = "bodyfat-model-glyph",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "bodyfat-model-press",
    )
    Box(
        modifier = Modifier
            .size(ModelButtonTouchSize)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .semantics { contentDescription = label }
            .testTag(bodyFatModelTag(model)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ModelButtonSize)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .clip(CircleShape)
                .background(fill, CircleShape)
                .then(if (selected) Modifier else Modifier.border(1.dp, ModelButtonBorderColor, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            WizardGenderGlyph(mark = mark, color = glyph, modifier = Modifier.size(ModelGlyphSize))
        }
    }
}

/**
 * Lectura bajo el escenario: el porcentaje grande y, debajo, una sola frase que cambia con el porcentaje y con la
 * figura. Atenuada (alfa 0,5) mientras el valor no está declarado. La frase reserva siempre dos líneas, así la página no
 * salta cuando pasa de una a dos mientras se arrastra.
 */
@Composable
private fun BodyFatReading(
    percent: Double,
    model: String?,
    declared: Boolean,
) {
    val reducedMotion = wizardReducedMotion()
    val readingAlpha by animateFloatAsState(
        targetValue = if (declared) 1f else UNDECLARED_READING_ALPHA,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
        label = "bodyfat-reading-alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = readingAlpha },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${wizardBodyFatNumber(percent)} %",
            style = WizardTypography.controlValue,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.testTag(BODY_FAT_VALUE_TAG),
        )
        Text(
            text = bodyFatDescriptor(percent, model),
            style = WizardTypography.stepSubtitle,
            color = WizardColors.textMuted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(BODY_FAT_DESCRIPTOR_TAG),
        )
    }
}
