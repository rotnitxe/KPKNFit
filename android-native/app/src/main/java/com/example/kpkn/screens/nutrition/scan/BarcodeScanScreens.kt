package com.example.kpkn.screens.nutrition.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.screens.nutrition.components.GramsEditor
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
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

/**
 * El escáner entero, sin estado propio: pantalla negra con cabecera y, según [state], la explicación del permiso, el visor con la
 * cámara ([preview]), la entrada manual, la espera, el producto encontrado o «No tengo este producto». [preview] es la vista de la
 * cámara (o un marcador en las pruebas y la vista previa de depuración).
 */
@Composable
fun BarcodeScanContent(
    state: BarcodeScanUiState,
    torchAvailable: Boolean,
    actions: BarcodeScanActions,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(KpknSurface.canvas).testTag("scan-root")) {
        ScanHeader(onClose = actions.onClose)
        Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
            val result = state.result
            when {
                state.phase == ScanPhase.SEARCHING -> ScanMessage("Buscando en tu teléfono…")
                state.phase == ScanPhase.RESULT && result is BarcodeLookup.Found ->
                    ScannedFoodConfirm(result.food, result.origin, actions)
                state.phase == ScanPhase.RESULT -> NotFoundContent(state.code, actions)
                state.showManual -> ManualEntry(state, actions)
                state.access == CameraAccess.ASK -> PermissionExplanation(actions)
                else -> CameraStage(state, torchAvailable, actions, preview)
            }
        }
    }
}

@Composable
private fun ScanHeader(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        KpknIconButton(KpknLineIcons.Close, "Cerrar el escáner", onClose, Modifier.testTag("scan-close"))
        Spacer(Modifier.width(8.dp))
        Text("Escanear código", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun ScanMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = KpknType.body, color = WizardColors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.testTag("scan-searching"))
    }
}

@Composable
private fun IconDisc(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(KpknSurface.inset), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Notice(text: String) {
    KpknModule(Modifier.fillMaxWidth().testTag("scan-notice"), accent = SymbolPalette.energia) {
        Text(text, style = KpknType.bodyStrong, color = WizardColors.text)
    }
}

// ─── Permiso ─────────────────────────────────────────────────────────────────

@Composable
private fun PermissionExplanation(actions: BarcodeScanActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        KpknModule(Modifier.fillMaxWidth().testTag("scan-permission")) {
            IconDisc(KpknLineIcons.Camera)
            Spacer(Modifier.height(14.dp))
            Text("Usa la cámara para leer el código", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                "KPKN solo mira el código de barras para buscarlo en tu teléfono. La imagen no se guarda ni sale del aparato.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(20.dp))
            KpknPrimaryPill("Permitir cámara", actions.onRequestPermission, Modifier.fillMaxWidth(), tag = "scan-allow-camera", icon = KpknLineIcons.Camera)
            Spacer(Modifier.height(10.dp))
            KpknSecondaryPill("Escribir el código", actions.onShowManual, Modifier.fillMaxWidth(), tag = "scan-type-code")
        }
    }
}

// ─── Visor ───────────────────────────────────────────────────────────────────

@Composable
private fun CameraStage(state: BarcodeScanUiState, torchAvailable: Boolean, actions: BarcodeScanActions, preview: @Composable (Modifier) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        state.notice?.let {
            Notice(it)
            Spacer(Modifier.height(12.dp))
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(KpknShape.module).background(Color.Black)
                .border(1.dp, KpknSurface.moduleBorder, KpknShape.module).testTag("scan-viewfinder"),
        ) {
            preview(Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(150.dp)
                    .border(2.dp, WizardColors.text.copy(alpha = 0.85f), RoundedCornerShape(20.dp)),
            )
            Text(
                "Centra el código en el recuadro",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
            if (torchAvailable) {
                KpknIconButton(
                    icon = KpknLineIcons.Sun,
                    contentDescription = if (state.torchOn) "Apagar la linterna" else "Encender la linterna",
                    onClick = actions.onToggleTorch,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("scan-torch"),
                    filled = state.torchOn,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        KpknSecondaryPill("Escribir el código", actions.onShowManual, Modifier.fillMaxWidth(), tag = "scan-type-code")
    }
}

// ─── Entrada manual ──────────────────────────────────────────────────────────

@Composable
private fun ManualEntry(state: BarcodeScanUiState, actions: BarcodeScanActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.notice?.let { Notice(it) }
        if (state.access == CameraAccess.DENIED) {
            KpknModule(Modifier.fillMaxWidth().testTag("scan-denied"), accent = SymbolPalette.energia) {
                Text("Sin permiso de cámara", style = KpknType.bodyStrong, color = WizardColors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "No puedo leer el código con la lente. Escríbelo aquí, o activa el permiso de cámara en Ajustes para escanearlo.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                KpknTextAction("Abrir ajustes", actions.onOpenSettings, Modifier.testTag("scan-open-settings"))
            }
        }
        KpknModule(Modifier.fillMaxWidth().testTag("scan-manual")) {
            KpknModuleHeader(label = "Escribe el código")
            Spacer(Modifier.height(8.dp))
            KpknTextField(
                value = state.manualText,
                onValueChange = actions.onManualTextChange,
                modifier = Modifier.testTag("scan-manual-field"),
                label = "Código de barras",
                placeholder = "Los 8, 12 o 13 dígitos bajo las barras",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onSubmitManual() }),
                error = state.manualError,
            )
            Spacer(Modifier.height(16.dp))
            KpknPrimaryPill("Buscar", actions.onSubmitManual, Modifier.fillMaxWidth(), tag = "scan-manual-submit", icon = KpknLineIcons.Search)
            if (!state.manualOnly) {
                KpknTextAction("Usar la cámara", actions.onShowCamera, Modifier.testTag("scan-use-camera"), leadingIcon = KpknLineIcons.Camera)
            }
        }
    }
}

// ─── No encontrado ───────────────────────────────────────────────────────────

@Composable
private fun NotFoundContent(code: String?, actions: BarcodeScanActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        KpknModule(Modifier.fillMaxWidth().testTag("scan-not-found")) {
            KpknModuleHeader(label = "Sin resultado")
            Spacer(Modifier.height(4.dp))
            Text("No tengo este producto", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            if (code != null) {
                Text("Código $code", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.testTag("scan-code"))
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "No está en tus alimentos ni en la base de este teléfono. Créalo con sus datos y la próxima vez que lo escanees lo reconozco.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(20.dp))
            KpknPrimaryPill(
                "Crear alimento propio", actions.onCreateOwn,
                Modifier.fillMaxWidth().semantics { contentDescription = "Crear alimento propio con este código" },
                tag = "scan-create-own", icon = KpknLineIcons.Plus,
            )
            Spacer(Modifier.height(10.dp))
            KpknSecondaryPill("Probar otro", actions.onScanAnother, Modifier.fillMaxWidth(), tag = "scan-another")
        }
    }
}

// ─── Encontrado: confirmación ────────────────────────────────────────────────

/** Qué dice cada bandera de calidad de una ficha, en lenguaje llano. */
internal fun qualityFlagMessage(flag: String): String = when (flag) {
    "INCOMPLETE" -> "Faltan nutrientes: la ficha no declara sus macros."
    "ENERGY_MISMATCH" -> "Las kcal no cuadran con los macros de la ficha."
    "LOW_QUALITY" -> "Datos de baja calidad: el nombre o los nutrientes pueden estar mal."
    "UNVERIFIED_NUTRIENT_BASIS" -> "No se sabe a qué cantidad se refieren los nutrientes."
    NutrientBasis.FLAG_ENERGY_ATWATER -> "Las kcal se calcularon a partir de los macros."
    NutrientBasis.FLAG_CARB_CLAMPED -> "Los carbohidratos se ajustaron a 0 (el dato venía algo negativo)."
    else -> "Aviso de calidad en la ficha."
}

/** De dónde sale el dato, para decirlo en la confirmación. */
internal fun originLine(food: FoodItem, origin: BarcodeOrigin): String = when (origin) {
    BarcodeOrigin.PROPIO -> "Dato propio: lo guardaste tú."
    BarcodeOrigin.CATALOGO -> "Dato de la base global" + (food.source?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + "."
}

@Composable
fun ScannedFoodConfirm(food: FoodItem, origin: BarcodeOrigin, actions: BarcodeScanActions) {
    val verified = NutrientBasis.isVerified(food)
    // Los gramos de la ración (los que el cajón propone al elegir una fila del buscador, editables con su mismo selector).
    var grams by rememberSaveable(food.id) { mutableStateOf<Double?>(null) }
    val tag = remember(food, grams) {
        val defaults = defaultGramsForScanned(food)
        scannedFoodTag(food, grams, id = "scan-${food.id}").copy(baseAmountGrams = defaults)
    }
    val logged = tag.loggedFood
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknModule(Modifier.fillMaxWidth().testTag("scan-found")) {
            KpknModuleHeader(label = if (origin == BarcodeOrigin.PROPIO) "Alimento propio" else "Base global")
            Spacer(Modifier.height(4.dp))
            Text(
                food.name.trim().replaceFirstChar { it.uppercase() },
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() }.testTag("scan-food-name"),
            )
            food.brand?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = KpknType.body, color = WizardColors.textMuted)
            }
            Spacer(Modifier.height(8.dp))
            Text(originLine(food, origin), style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.testTag("scan-origin"))
            food.sourceRecordId?.takeIf { it.isNotBlank() }?.let {
                Text("Código $it", style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        if (food.qualityFlags.isNotEmpty()) {
            KpknModule(Modifier.fillMaxWidth().testTag("scan-quality"), accent = if (verified) SymbolPalette.energia else WizardColors.danger) {
                KpknModuleHeader(label = "Calidad del dato")
                food.qualityFlags.distinct().forEach { flag ->
                    Text("• ${qualityFlagMessage(flag)}", style = KpknType.body, color = WizardColors.text)
                }
                if (!verified) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Con estos datos no se puede registrar. Crea un alimento propio con los números de la etiqueta.",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                }
            }
        }
        if (verified && logged != null) {
            KpknModule(Modifier.fillMaxWidth().testTag("scan-ration")) {
                KpknModuleHeader(label = "Ración")
                Spacer(Modifier.height(8.dp))
                GramsEditor(tag = tag, onGramsChange = { grams = it })
                Spacer(Modifier.height(12.dp))
                KpknMetric(logged.calories.roundToInt().toString(), "kcal", style = KpknType.metricL)
                Text(
                    "P ${logged.protein.roundToInt()} g · C ${logged.carbs.roundToInt()} g · G ${logged.fats.roundToInt()} g",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }
        KpknPrimaryPill(
            text = "Agregar a la comida",
            onClick = { actions.onConfirm(food, tag.amountGrams ?: defaultGramsForScanned(food)) },
            modifier = Modifier.fillMaxWidth(),
            tag = "scan-confirm",
            icon = KpknLineIcons.Check,
            enabled = verified,
        )
        if (!verified) {
            KpknTextAction(
                "Crear alimento propio", actions.onCreateOwn,
                Modifier.testTag("scan-create-own").semantics { contentDescription = "Crear alimento propio con este código" },
                leadingIcon = KpknLineIcons.Plus,
            )
        }
        KpknSecondaryPill("Probar otro", actions.onScanAnother, Modifier.fillMaxWidth(), tag = "scan-another")
    }
}

/** Marcador de la vista previa cuando no hay cámara (pruebas, hoja de render y vista previa de depuración). */
@Composable
fun ScanPreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(Color(0xFF0B0B0B)).semantics { contentDescription = "Vista previa simulada de la cámara" },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text("Vista previa simulada", style = KpknType.caption, color = WizardColors.textFaint, modifier = Modifier.padding(top = 14.dp))
    }
}
