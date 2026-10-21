package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.analysis.PlateModuleState
import com.example.kpkn.screens.technique.analysis.PlateModuleUi
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.min
import kotlin.math.roundToInt

/** Etiquetas de prueba del módulo del disco y de sus botones. */
const val ANALYSIS_PLATE_TAG = "analysis-plate"
const val ANALYSIS_PLATE_CONFIRM_TAG = "analysis-plate-confirm"
const val ANALYSIS_PLATE_OPEN_TAG = "analysis-plate-open"

/** Desde esta escala de letra la etiqueta del módulo pasa a dos líneas. */
private const val PLATE_LARGE_FONT_SCALE = 1.15f

/** Los diámetros de disco habituales, en mm: 45, 40 y 35 cm. El resto va en «Otro». */
internal val STANDARD_PLATE_DIAMETERS_MM: List<Int> = listOf(450, 400, 350)

/** Un diámetro en centímetros, sin «,0» si es redondo: 450 → «45», 425 → «42,5». */
internal fun plateDiameterCm(mm: Int): String = AnalysisFormat.decimal(mm / 10.0, if (mm % 10 == 0) 0 else 1)

/**
 * El módulo «Velocidad real con el disco» (sin estado), solo para los perfiles que usan el disco. Tres estados:
 * - **Disco hallado sin confirmar:** una miniatura del fotograma con su círculo, «¿Es este el disco?», los diámetros «45 · 40 · 35 ·
 *   Otro» (cm), «Confirmar» y «Ajustar» (que abre el diálogo para moverlo).
 * - **Confirmado:** «Disco de 45 cm» y, debajo, «Velocidad en m/s» (en dos líneas, no con el punto medio del brief: en 360 dp y con la
 *   letra grande se partía a media frase), y «Cambiar». Si con ese disco no salió la velocidad en m/s, no lo dice.
 * - **Sin disco:** «No encontramos el disco» y «Marcarlo».
 *
 * Los avisos de la escala (disco ovalado, descartado, sin paralaje o que no cuadra con el atleta) van debajo, uno por línea y con su
 * causa. [frame] es el fotograma de análisis del instante [PlateModuleUi.frameUs] (`null` mientras carga).
 */
@Composable
fun PlateCalibrationModule(
    plate: PlateModuleUi,
    frame: ImageBitmap?,
    selectedDiameterMm: Int,
    onSelectDiameter: (Int) -> Unit,
    onPickOtherDiameter: () -> Unit,
    onConfirm: () -> Unit,
    onOpenCalibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.testTag(ANALYSIS_PLATE_TAG)) {
        KpknModuleHeader("Velocidad real con el disco")
        // Con la letra grande la etiqueta pasa a dos líneas y se queda sin el aire que le sobra a una sola: se le devuelve.
        Spacer(Modifier.height(if (LocalDensity.current.fontScale >= PLATE_LARGE_FONT_SCALE) 14.dp else 6.dp))
        when (plate.state) {
            PlateModuleState.SUGGESTED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlateThumbnail(frame = frame, plate = plate, thumbSize = 92.dp, onClick = onOpenCalibration)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "¿Es este el disco?", style = KpknType.bodyStrong, color = WizardColors.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Elige su diámetro y confírmalo para medir la velocidad en m/s.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(text = "Diámetro del disco, en cm", style = KpknType.caption, color = WizardColors.textMuted)
                PlateDiameterChips(selectedMm = selectedDiameterMm, onSelect = onSelectDiameter, onOther = onPickOtherDiameter)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KpknPrimaryPill(
                        text = "Confirmar",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        tag = ANALYSIS_PLATE_CONFIRM_TAG,
                    )
                    Spacer(Modifier.width(8.dp))
                    KpknTextAction(
                        text = "Ajustar",
                        onClick = onOpenCalibration,
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp).testTag(ANALYSIS_PLATE_OPEN_TAG),
                    )
                }
            }
            PlateModuleState.CONFIRMED -> {
                val cm = plateDiameterCm(plate.diameterMm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnalysisIconCircle(KpknLineIcons.Target)
                    Spacer(Modifier.width(14.dp))
                    // «Disco de 45 cm · velocidad en m/s» en dos líneas (el título y lo que da): partido por el punto medio, en la
                    // columna que deja «Cambiar», queda a medias.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Disco de $cm cm", style = KpknType.bodyStrong, color = WizardColors.text)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (plate.inMetersPerSecond) "Velocidad en m/s" else "Con este vídeo no se pudo medir la velocidad en m/s.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    KpknTextAction(
                        text = "Cambiar",
                        onClick = onOpenCalibration,
                        modifier = Modifier.defaultMinSize(minWidth = 64.dp).testTag(ANALYSIS_PLATE_OPEN_TAG),
                    )
                }
            }
            PlateModuleState.NOT_FOUND -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnalysisIconCircle(KpknLineIcons.Target)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "No encontramos el disco", style = KpknType.bodyStrong, color = WizardColors.text)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Márcalo tú para medir la velocidad en m/s.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                KpknSecondaryPill(
                    text = "Marcarlo",
                    onClick = onOpenCalibration,
                    modifier = Modifier.fillMaxWidth(),
                    tag = ANALYSIS_PLATE_OPEN_TAG,
                )
            }
        }
        if (plate.warnings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AnalysisDivider()
            Spacer(Modifier.height(10.dp))
            plate.warnings.forEachIndexed { index, warning ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SymbolPalette.energia),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(text = warning, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Los diámetros del disco «45 · 40 · 35 · Otro» (sin estado). [selectedMm] marca el elegido; uno que no es de los tres habituales
 * marca «Otro» y lo enseña («42,5 cm»). «Otro» avisa con [onOther] para que quien lo usa pida la cifra.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlateDiameterChips(
    selectedMm: Int,
    onSelect: (Int) -> Unit,
    onOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOther = selectedMm !in STANDARD_PLATE_DIAMETERS_MM
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (mm in STANDARD_PLATE_DIAMETERS_MM) {
            KpknChip(
                text = plateDiameterCm(mm),
                selected = selectedMm == mm,
                onClick = { onSelect(mm) },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp),
            )
        }
        KpknChip(
            text = if (isOther) "${plateDiameterCm(selectedMm)} cm" else "Otro",
            selected = isOther,
            onClick = onOther,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp),
        )
    }
}

/**
 * Miniatura cuadrada del fotograma con el disco: el recorte de unas 2 veces su diámetro alrededor del círculo, que se dibuja encima en
 * ámbar. Mientras el fotograma carga (o si no hay círculo), el icono del disco sobre el fondo tonal. Tocarla es lo mismo que «Ajustar».
 */
@Composable
private fun PlateThumbnail(frame: ImageBitmap?, plate: PlateModuleUi, thumbSize: Dp, onClick: () -> Unit) {
    val circle = plate.circle
    Box(
        modifier = Modifier
            .size(thumbSize)
            .clip(RoundedCornerShape(16.dp))
            .background(KpknSurface.inset)
            .semantics { contentDescription = "Fotograma con el disco que hemos encontrado" }
            .clickable(role = Role.Button, onClickLabel = "Ajustar el disco", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null && circle != null && plate.frameWidth > 0 && plate.frameHeight > 0) {
            Canvas(Modifier.fillMaxSize()) {
                // Las coordenadas del disco son del fotograma de análisis; la imagen puede tener otro tamaño.
                val sx = frame.width.toFloat() / plate.frameWidth
                val sy = frame.height.toFloat() / plate.frameHeight
                val cx = circle.cx * sx
                val cy = circle.cy * sy
                val r = circle.r * sx
                val shortSide = min(frame.width, frame.height).toFloat()
                val side = (r * 2f * 2.1f).coerceIn(min(48f, shortSide), shortSide)
                val left = (cx - side / 2f).coerceIn(0f, frame.width - side)
                val top = (cy - side / 2f).coerceIn(0f, frame.height - side)
                drawImage(
                    image = frame,
                    srcOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    srcSize = IntSize(side.roundToInt(), side.roundToInt()),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                val k = size.width / side
                drawCircle(
                    color = SymbolPalette.energia,
                    radius = r * k,
                    center = Offset((cx - left) * k, (cy - top) * k),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        } else {
            Icon(imageVector = KpknLineIcons.Target, contentDescription = null, modifier = Modifier.size(28.dp), tint = WizardColors.textFaint)
        }
    }
}
