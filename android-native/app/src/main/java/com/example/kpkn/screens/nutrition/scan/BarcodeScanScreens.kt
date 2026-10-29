package com.example.kpkn.screens.nutrition.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.screens.nutrition.design.logger.LoggerAct
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerFieldBox
import com.example.kpkn.screens.nutrition.design.logger.LoggerIcons
import com.example.kpkn.screens.nutrition.design.logger.LoggerPill
import com.example.kpkn.screens.nutrition.design.logger.LoggerPillTone
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetTitle
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.screens.nutrition.design.logger.LoggerToast
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import java.util.Locale
import kotlin.math.roundToInt

/** Lo que el escáner puede pedir a quien lo aloja. Todo con valores por defecto para que una vista previa solo conecte lo que usa. */
class BarcodeScanActions(
    val onRequestPermission: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onShowManual: () -> Unit = {},
    val onShowCamera: () -> Unit = {},
    val onManualTextChange: (String) -> Unit = {},
    val onSubmitManual: () -> Unit = {},
    val onToggleTorch: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onScanAnother: () -> Unit = {},
    val onCreateOwn: () -> Unit = {},
    /** El producto se confirma con los gramos elegidos. */
    val onConfirm: (FoodItem, Double) -> Unit = { _, _ -> },
)

// ─── Medidas del lienzo s-scan (`.scan`, `.frame`, `.laser`) ─────────────────────────────────────────

private val FrameSideInset = 44.dp
private val FrameTop = 250.dp
private val FrameHeight = 170.dp
private val FrameRadius = 24.dp
private val ButtonsTop = 44.dp
private val PromptTop = 200.dp
private val LaserFrom = 20.dp
private val LaserTo = 148.dp
private const val LASER_MS = 1800

/**
 * El escáner entero, sin estado propio (lienzo s-scan): el visor a pantalla completa con su recuadro y la línea láser, cerrar y linterna
 * arriba y, según [state], una hoja de vidrio que sube desde abajo: el permiso, la entrada manual, la espera, el producto encontrado
 * o «No tengo este producto». El resultado sale SOBRE la cámara, sin salir de ella, para escanear varios seguidos. [preview] es la
 * vista de la cámara (o un marcador en las pruebas y la vista previa de depuración).
 */
@Composable
fun BarcodeScanContent(
    state: BarcodeScanUiState,
    torchAvailable: Boolean,
    actions: BarcodeScanActions,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showStage = state.access == CameraAccess.GRANTED && !state.showManual
    val scanning = state.phase == ScanPhase.SCANNING
    val result = state.result
    Box(modifier.drawBehind { drawRect(scanBackdrop(size)) }.testTag("scan-root")) {
        if (showStage) {
            Box(Modifier.fillMaxSize().testTag("scan-viewfinder")) { preview(Modifier.fillMaxSize()) }
            ScanFrame()
            Text(
                text = "Centra el código en el recuadro",
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                color = LoggerColors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = PromptTop),
            )
        }
        // Cerrar y linterna (`.fb glass`, a 44 dp del borde de arriba).
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = ButtonsTop),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassRoundButton(KpknLineIcons.Close, "Cerrar el escáner", actions.onClose, Modifier.testTag("scan-close"))
            if (showStage && scanning && torchAvailable) {
                GlassRoundButton(
                    icon = LoggerIcons.Bolt,
                    description = if (state.torchOn) "Apagar la linterna" else "Encender la linterna",
                    onClick = actions.onToggleTorch,
                    modifier = Modifier.testTag("scan-torch"),
                    tint = if (state.torchOn) LoggerColors.mus else LoggerColors.ink,
                )
            }
        }
        state.notice?.let {
            LoggerToast(
                message = it,
                icon = null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = ButtonsTop + 64.dp).testTag("scan-notice"),
            )
        }
        when {
            state.phase == ScanPhase.SEARCHING -> ScanSheet(Modifier.align(Alignment.BottomCenter)) {
                Text(
                    "Buscando en tu teléfono…",
                    style = LoggerText.rowTitle,
                    color = LoggerColors.muted,
                    modifier = Modifier.padding(vertical = 12.dp).testTag("scan-searching"),
                )
            }
            state.phase == ScanPhase.RESULT && result is BarcodeLookup.Found ->
                ScannedFoodConfirm(result.food, result.origin, actions, Modifier.align(Alignment.BottomCenter))
            state.phase == ScanPhase.RESULT -> NotFoundContent(state.code, actions, Modifier.align(Alignment.BottomCenter))
            state.showManual -> ManualEntry(state, actions, Modifier.align(Alignment.BottomCenter))
            state.access == CameraAccess.ASK -> PermissionExplanation(actions, Modifier.align(Alignment.BottomCenter))
            else -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) {
                // «Escribe el código» sigue a mano mientras la cámara lee (el lienzo no lo pinta; la función no se pierde).
                Box(Modifier.width(220.dp).kpknGlassOrFallback(LocalHazeState.current, KpknShape.pill, variant = KpknGlassVariant.ISLAND)) {
                    LoggerPill(
                        text = "Escribir el código",
                        onClick = actions.onShowManual,
                        modifier = Modifier.fillMaxWidth().testTag("scan-type-code"),
                        tone = LoggerPillTone.SECONDARY,
                        small = true,
                    )
                }
            }
        }
    }
}

/** `.scan`: fondo radial oscuro (`radial-gradient(60% 40% at 50% 45%, #2a2622, #050505 75%)`), aproximado con un círculo centrado al 45 %. */
private fun scanBackdrop(size: Size): Brush = Brush.radialGradient(
    0f to Color(0xFF2A2622),
    0.75f to Color(0xFF050505),
    1f to Color(0xFF050505),
    center = Offset(size.width * 0.5f, size.height * 0.45f),
    radius = maxOf(size.width * 0.6f, size.height * 0.4f),
)

/**
 * `.frame` + `.laser`: el recuadro con el resto de la pantalla oscurecido al 67 %, las esquinas en tinta (el primer 22 % de cada lado) y la
 * línea láser que sube y baja en 1,8 s. Con movimiento reducido la línea queda quieta en medio.
 */
@Composable
private fun ScanFrame() {
    val reduced = wizardReducedMotion()
    val laserY = if (reduced) {
        (LaserFrom + LaserTo) / 2
    } else {
        val transition = rememberInfiniteTransition(label = "laser")
        val y by transition.animateFloat(
            initialValue = LaserFrom.value,
            targetValue = LaserTo.value,
            animationSpec = infiniteRepeatable(tween(LASER_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "laserY",
        )
        y.dp
    }
    Canvas(Modifier.fillMaxSize()) {
        val left = FrameSideInset.toPx()
        val right = size.width - left
        val top = FrameTop.toPx()
        val bottom = top + FrameHeight.toPx()
        val radius = FrameRadius.toPx()
        val hole = Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius, radius))) }
        clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.67f)) }
        // Esquinas: borde de 2 dp por fuera del recuadro (inset −2), solo arriba y abajo, en el 22 % de cada extremo.
        val outset = 1.dp.toPx()
        val l = left - outset
        val r = right + outset
        val t = top - outset
        val b = bottom + outset
        val rr = radius + 2.dp.toPx() - outset
        val reach = (r - l) * 0.22f
        val corners = Path().apply {
            moveTo(l + reach, t); lineTo(l + rr, t); arcTo(Rect(l, t, l + 2 * rr, t + 2 * rr), 270f, -45f, false)
            moveTo(r - reach, t); lineTo(r - rr, t); arcTo(Rect(r - 2 * rr, t, r, t + 2 * rr), 270f, 45f, false)
            moveTo(l + reach, b); lineTo(l + rr, b); arcTo(Rect(l, b - 2 * rr, l + 2 * rr, b), 90f, 45f, false)
            moveTo(r - reach, b); lineTo(r - rr, b); arcTo(Rect(r - 2 * rr, b - 2 * rr, r, b), 90f, -45f, false)
        }
        drawPath(corners, color = LoggerColors.ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt))
    }
    Box(Modifier.fillMaxSize().padding(top = FrameTop, start = FrameSideInset, end = FrameSideInset)) {
        Box(Modifier.fillMaxWidth().height(FrameHeight)) {
            // Resplandor (`box-shadow: 0 0 14px`) y línea de 2 dp.
            Box(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().offset(y = laserY - 6.dp).height(14.dp)
                    .background(LoggerColors.mus.copy(alpha = 0.25f), RoundedCornerShape(50)),
            )
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().offset(y = laserY).height(2.dp).background(LoggerColors.mus))
        }
    }
}

/** `.fb glass`: botón redondo de 48 dp de vidrio, con el icono en tinta. */
@Composable
private fun GlassRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LoggerColors.ink,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .kpknGlassOrFallback(LocalHazeState.current, CircleShape, variant = KpknGlassVariant.ISLAND)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
    }
}

/**
 * `.sheet` del lienzo sobre la cámara: vidrio con las esquinas de arriba a 28 dp, el asa y el contenido con desplazamiento si no cabe.
 * No es una [com.example.kpkn.ui.components.KpknSheet]: esa sale por detrás de la capa a pantalla completa del escáner.
 */
@Composable
private fun ScanSheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .kpknGlassOrFallback(LocalHazeState.current, shape, variant = KpknGlassVariant.SHEET)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 30.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
                .size(width = 42.dp, height = 5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(KpknSheetTokens.Handle),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    }
}

// ─── Permiso ─────────────────────────────────────────────────────────────────

@Composable
private fun PermissionExplanation(actions: BarcodeScanActions, modifier: Modifier = Modifier) {
    ScanSheet(modifier.testTag("scan-permission")) {
        LoggerSheetTitle(
            title = "Usa la cámara para leer el código",
            sub = "KPKN solo mira el código de barras para buscarlo en tu teléfono. La imagen no se guarda ni sale del aparato.",
        )
        LoggerPill(
            text = "Permitir cámara",
            onClick = actions.onRequestPermission,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("scan-allow-camera"),
            icon = KpknLineIcons.Camera,
        )
        LoggerPill(
            text = "Escribir el código",
            onClick = actions.onShowManual,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("scan-type-code"),
            tone = LoggerPillTone.SECONDARY,
        )
    }
}

// ─── Entrada manual ──────────────────────────────────────────────────────────

/** `.field div`: Inter 15 con números tabulares. */
private val FieldValueStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

@Composable
private fun ManualEntry(state: BarcodeScanUiState, actions: BarcodeScanActions, modifier: Modifier = Modifier) {
    ScanSheet(modifier.testTag("scan-manual")) {
        LoggerSheetTitle(title = "Escribe el código")
        if (state.access == CameraAccess.DENIED) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("scan-denied")) {
                Text("Sin permiso de cámara", style = LoggerText.rowTitle, color = LoggerColors.ink)
                Text(
                    "No puedo leer el código con la lente. Escríbelo aquí, o activa el permiso de cámara en Ajustes para escanearlo.",
                    style = LoggerText.sub,
                    color = LoggerColors.muted,
                )
                LoggerAct("Abrir ajustes", actions.onOpenSettings, Modifier.testTag("scan-open-settings"), color = LoggerColors.mus)
            }
        }
        LoggerFieldBox(label = "Código de barras", modifier = Modifier.testTag("scan-manual-field")) {
            BasicTextField(
                value = state.manualText,
                onValueChange = actions.onManualTextChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Código de barras" },
                singleLine = true,
                textStyle = FieldValueStyle.copy(color = LoggerColors.ink),
                cursorBrush = SolidColor(LoggerColors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onSubmitManual() }),
                decorationBox = { inner ->
                    Box {
                        if (state.manualText.isEmpty()) {
                            Text("Los 8, 12 o 13 dígitos bajo las barras", style = FieldValueStyle, color = LoggerColors.faint)
                        }
                        inner()
                    }
                },
            )
        }
        state.manualError?.let {
            Text(it, style = LoggerText.cap, color = LoggerColors.danger, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
        }
        LoggerPill(
            text = "Buscar",
            onClick = actions.onSubmitManual,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("scan-manual-submit"),
            icon = KpknLineIcons.Search,
        )
        if (!state.manualOnly) {
            LoggerAct(
                text = "Usar la cámara",
                onClick = actions.onShowCamera,
                modifier = Modifier.padding(top = 4.dp).testTag("scan-use-camera"),
                leadingIcon = KpknLineIcons.Camera,
            )
        }
    }
}

// ─── No encontrado ───────────────────────────────────────────────────────────

@Composable
private fun NotFoundContent(code: String?, actions: BarcodeScanActions, modifier: Modifier = Modifier) {
    ScanSheet(modifier.testTag("scan-not-found")) {
        LoggerSheetTitle(title = "No tengo este producto")
        if (code != null) {
            Text("Código $code", style = LoggerText.sub, color = LoggerColors.muted, modifier = Modifier.testTag("scan-code"))
        }
        LoggerPill(
            text = "Crear alimento",
            onClick = actions.onCreateOwn,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("scan-create-own"),
            icon = KpknLineIcons.Plus,
            contentDescription = "Crear alimento con este código",
        )
        LoggerPill(
            text = "Escanear otro",
            onClick = actions.onScanAnother,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("scan-another"),
            tone = LoggerPillTone.SECONDARY,
        )
    }
}

// ─── Encontrado ──────────────────────────────────────────────────────────────

/**
 * La hoja del producto encontrado (lienzo s-scan), sobre la cámara: «Encontrado · base global» (o «tus alimentos») con su check, el nombre,
 * «Marca · 42 g · 191 kcal» (la ración por defecto, [defaultGramsForScanned]) y «Escanear otro» / «Añadir». La cantidad se ajusta luego
 * en la tarjeta del registro. Si la ficha no es verificable, «Añadir» queda apagada, la línea dice por qué y se ofrece «Crear alimento».
 */
@Composable
fun ScannedFoodConfirm(food: FoodItem, origin: BarcodeOrigin, actions: BarcodeScanActions, modifier: Modifier = Modifier) {
    val verified = NutrientBasis.isVerified(food)
    val tag = remember(food) { scannedFoodTag(food, id = "scan-${food.id}") }
    val grams = tag.amountGrams ?: defaultGramsForScanned(food)
    val logged = tag.loggedFood
    val summary = if (verified && logged != null) {
        listOfNotNull(
            food.brand?.takeIf { it.isNotBlank() },
            "${grams.roundToInt()} g",
            "${logged.calories.roundToInt()} kcal",
        ).joinToString(" · ")
    } else {
        "Sin datos fiables: créalo con su etiqueta"
    }
    ScanSheet(modifier.testTag("scan-found")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("scan-origin")) {
            Icon(KpknLineIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = LoggerColors.done)
            Spacer(Modifier.width(4.dp))
            Text(
                text = (if (origin == BarcodeOrigin.PROPIO) "Encontrado · tus alimentos" else "Encontrado · base global").uppercase(Locale("es")),
                style = LoggerText.eyebrow,
                color = LoggerColors.done,
            )
        }
        Spacer(Modifier.height(6.dp))
        LoggerSheetTitle(
            title = food.name.trim().replaceFirstChar { it.uppercase() },
            modifier = Modifier.testTag("scan-food-name"),
        )
        Text(summary, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.testTag("scan-summary"))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoggerPill(
                text = "Escanear otro",
                onClick = actions.onScanAnother,
                modifier = Modifier.weight(1f).testTag("scan-another"),
                tone = LoggerPillTone.SECONDARY,
            )
            LoggerPill(
                text = "Añadir",
                onClick = { actions.onConfirm(food, grams) },
                modifier = Modifier.weight(1f).testTag("scan-confirm"),
                enabled = verified,
            )
        }
        if (!verified) {
            LoggerPill(
                text = "Crear alimento",
                onClick = actions.onCreateOwn,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("scan-create-own"),
                tone = LoggerPillTone.SECONDARY,
                icon = KpknLineIcons.Plus,
                contentDescription = "Crear alimento con este código",
            )
        }
    }
}

/** Marcador de la vista previa cuando no hay cámara (pruebas, hoja de render y vista previa de depuración). */
@Composable
fun ScanPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Color(0xFF0B0B0B)).semantics { contentDescription = "Vista previa simulada de la cámara" },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text("Vista previa simulada", style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.padding(top = 14.dp))
    }
}
