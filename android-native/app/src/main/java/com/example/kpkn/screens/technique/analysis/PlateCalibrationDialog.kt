package com.example.kpkn.screens.technique.analysis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.design.PlateDiameterChips
import com.example.kpkn.screens.technique.analysis.design.plateDiameterCm
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/** Etiquetas de prueba del diálogo. */
const val PLATE_DIALOG_TAG = "plate-calibration-dialog"
const val PLATE_DIALOG_SAVE_TAG = "plate-calibration-save"
const val PLATE_DIALOG_CANVAS_TAG = "plate-calibration-canvas"

/** El disco como lo mueve el usuario: centro y radio como fracciones del fotograma (el radio, del ancho). */
private data class NormalizedCircle(val cx: Float, val cy: Float, val r: Float) {
    fun clamped(): NormalizedCircle = NormalizedCircle(
        cx = cx.coerceIn(0f, 1f),
        cy = cy.coerceIn(0f, 1f),
        r = r.coerceIn(MIN_RADIUS, MAX_RADIUS),
    )

    fun moved(dx: Float, dy: Float): NormalizedCircle = copy(cx = cx + dx, cy = cy + dy).clamped()

    fun scaled(factor: Float): NormalizedCircle = copy(r = r * factor).clamped()

    companion object {
        const val MIN_RADIUS = 0.015f
        const val MAX_RADIUS = 0.45f
    }
}

private fun PlateModuleUi.initialCircle(): NormalizedCircle {
    val seed = circle
    return if (seed != null && frameWidth > 0 && frameHeight > 0) {
        NormalizedCircle(seed.cx / frameWidth, seed.cy / frameHeight, seed.r / frameWidth).clamped()
    } else {
        NormalizedCircle(0.5f, 0.5f, 0.12f)
    }
}

/**
 * «Marca el disco»: el fotograma a pantalla completa sobre negro (el del fondo de la 1.ª repetición o el del disco hallado) con un
 * círculo que se arrastra con un dedo y se agranda o encoge pellizcando. Un toque lo lleva a ese punto. Mientras se toca, una lupa
 * (`Modifier.magnifier`, API 28+) enseña el punto de más cerca. Debajo, los diámetros «45 · 40 · 35 · Otro» y «Guardar»: [onSave]
 * recibe el disco en píxeles del fotograma de análisis (ya confirmado por el usuario) y su diámetro en mm.
 *
 * Es una capa de [KpknPortal] (se pinta sobre toda la app) y se cierra con atrás o con la cruz. [magnifierEnabled] apaga la lupa
 * (las hojas de render no tienen ventana real).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlateCalibrationDialog(
    plate: PlateModuleUi,
    frame: ImageBitmap?,
    initialDiameterMm: Int,
    onSave: (seed: PlateSeed, diameterMm: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    magnifierEnabled: Boolean = true,
) {
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    KpknPortal {
        BackHandler(onBack = onDismiss)
        var circle by remember { mutableStateOf(plate.initialCircle()) }
        var diameterMm by remember { mutableIntStateOf(initialDiameterMm) }
        var showOther by remember { mutableStateOf(false) }
        var touching by remember { mutableStateOf(false) }
        var boxSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(380f)
                .background(KpknSurface.canvas)
                // El diálogo se traga los toques: nada de lo que hay debajo debe enterarse.
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag(PLATE_DIALOG_TAG),
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar sin guardar", onClick = onDismiss)
                    Text(
                        text = "Marca el disco",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                    )
                    Spacer(Modifier.width(48.dp))
                }

                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val aspect = when {
                        frame != null -> frame.width.toFloat() / frame.height
                        plate.frameWidth > 0 && plate.frameHeight > 0 -> plate.frameWidth.toFloat() / plate.frameHeight
                        else -> 9f / 16f
                    }
                    val (fitWidth, fitHeight) = fitInside(maxWidth, maxHeight, aspect)
                    Box(
                        modifier = Modifier
                            .size(fitWidth, fitHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(KpknSurface.inset)
                            .onSizeChanged { boxSize = it }
                            .then(
                                if (magnifierEnabled) {
                                    Modifier.magnifier(
                                        sourceCenter = {
                                            if (touching && boxSize.width > 0) Offset(circle.cx * boxSize.width, circle.cy * boxSize.height) else Offset.Unspecified
                                        },
                                        magnifierCenter = {
                                            val x = circle.cx * boxSize.width
                                            val y = circle.cy * boxSize.height
                                            val above = y - 96.dp.toPx()
                                            // Sobre el disco; si no cabe arriba, debajo.
                                            Offset(x, if (above < 56.dp.toPx()) y + 96.dp.toPx() else above)
                                        },
                                        zoom = 2.4f,
                                        size = DpSize(104.dp, 104.dp),
                                        cornerRadius = 52.dp,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            // Cuándo hay un dedo encima (para la lupa), sin consumir nada.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    touching = true
                                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                    touching = false
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    circle = circle.copy(cx = offset.x / size.width, cy = offset.y / size.height).clamped()
                                }
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    circle = circle.moved(pan.x / size.width, pan.y / size.height).scaled(zoom)
                                }
                            }
                            .testTag(PLATE_DIALOG_CANVAS_TAG)
                            .semantics {
                                contentDescription = "Fotograma del vídeo con un círculo sobre el disco. Arrástralo para colocarlo."
                                customActions = listOf(
                                    CustomAccessibilityAction("Agrandar el círculo") { circle = circle.scaled(1.08f); true },
                                    CustomAccessibilityAction("Reducir el círculo") { circle = circle.scaled(1f / 1.08f); true },
                                    CustomAccessibilityAction("Mover el círculo arriba") { circle = circle.moved(0f, -0.02f); true },
                                    CustomAccessibilityAction("Mover el círculo abajo") { circle = circle.moved(0f, 0.02f); true },
                                    CustomAccessibilityAction("Mover el círculo a la izquierda") { circle = circle.moved(-0.02f, 0f); true },
                                    CustomAccessibilityAction("Mover el círculo a la derecha") { circle = circle.moved(0.02f, 0f); true },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (frame != null) {
                            Image(
                                bitmap = frame,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds,
                            )
                        } else {
                            Icon(imageVector = KpknLineIcons.Target, contentDescription = null, modifier = Modifier.size(40.dp), tint = WizardColors.textFaint)
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val center = Offset(circle.cx * size.width, circle.cy * size.height)
                            val radius = circle.r * size.width
                            // Un contorno oscuro bajo el trazo ámbar, para que se lea sobre cualquier fondo.
                            drawCircle(Color.Black.copy(alpha = 0.55f), radius, center, style = Stroke(width = 5.dp.toPx()))
                            drawCircle(SymbolPalette.energia, radius, center, style = Stroke(width = 2.5.dp.toPx()))
                            val tick = 7.dp.toPx()
                            drawLine(SymbolPalette.energia, Offset(center.x - tick, center.y), Offset(center.x + tick, center.y), strokeWidth = 1.5.dp.toPx())
                            drawLine(SymbolPalette.energia, Offset(center.x, center.y - tick), Offset(center.x, center.y + tick), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                }

                Text(
                    text = "Arrastra el círculo hasta el disco y pellizca para ajustar su tamaño.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(text = "Diámetro del disco, en cm", style = KpknType.caption, color = WizardColors.textMuted)
                    PlateDiameterChips(selectedMm = diameterMm, onSelect = { diameterMm = it }, onOther = { showOther = true })
                }
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = {
                        val width = plate.frameWidth.takeIf { it > 0 } ?: frame?.width ?: 0
                        val height = plate.frameHeight.takeIf { it > 0 } ?: frame?.height ?: 0
                        if (width > 0 && height > 0) {
                            onSave(
                                PlateSeed(
                                    tUs = plate.frameUs,
                                    cx = circle.cx * width,
                                    cy = circle.cy * height,
                                    r = circle.r * width,
                                    userConfirmed = true,
                                ),
                                diameterMm,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    tag = PLATE_DIALOG_SAVE_TAG,
                    enabled = frame != null,
                )
            }
            if (showOther) {
                PlateOtherDiameterDialog(
                    initialMm = diameterMm,
                    onDismiss = { showOther = false },
                    onAccept = { mm ->
                        diameterMm = mm
                        showOther = false
                    },
                )
            }
        }
    }
}

/** El mayor rectángulo de proporción [aspect] (ancho/alto) que cabe en [maxWidth] × [maxHeight]. */
private fun fitInside(maxWidth: Dp, maxHeight: Dp, aspect: Float): Pair<Dp, Dp> {
    val byWidth = maxHeight * aspect
    return if (byWidth <= maxWidth) byWidth to maxHeight else maxWidth to (maxWidth / aspect)
}

/** Etiquetas de prueba del diálogo del diámetro. */
const val PLATE_OTHER_FIELD_TAG = "plate-other-field"
const val PLATE_OTHER_ACCEPT_TAG = "plate-other-accept"

/** Un diámetro escrito en centímetros («42,5» o «42.5») en mm, o `null` si no es un número. */
internal fun parsePlateDiameterMm(text: String): Int? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let { (it * 10.0).roundToInt() }

/** Los diámetros de disco que se aceptan, en mm: de 10 a 70 cm (los mismos límites que `VideoAnalysisRepository.setPlateSeed`). */
private val ACCEPTED_DIAMETER_MM = 100..700

/**
 * «Otro» diámetro: un campo para escribir los centímetros del disco, de 10 a 70. [initialMm] es el valor de partida; [onAccept]
 * recibe los mm. Solo deja aceptar un valor válido.
 */
@Composable
internal fun PlateOtherDiameterDialog(
    initialMm: Int,
    onDismiss: () -> Unit,
    onAccept: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(plateDiameterCm(initialMm)) }
    val parsed = parsePlateDiameterMm(text)
    val valid = parsed != null && parsed in ACCEPTED_DIAMETER_MM
    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Diámetro del disco", style = KpknType.dialogTitle, color = WizardColors.text)
            Spacer(Modifier.height(6.dp))
            Text(text = "En centímetros, de 10 a 70.", style = KpknType.caption, color = WizardColors.textMuted)
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == ',' || it == '.' }.take(5) },
                modifier = Modifier.testTag(PLATE_OTHER_FIELD_TAG),
                placeholder = "45",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                error = if (text.isNotEmpty() && !valid) "Entre 10 y 70 cm" else null,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KpknTextAction(text = "Cancelar", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                KpknPrimaryPill(
                    text = "Aceptar",
                    onClick = { if (parsed != null && valid) onAccept(parsed) },
                    tag = PLATE_OTHER_ACCEPT_TAG,
                    enabled = valid,
                )
            }
        }
    }
}
