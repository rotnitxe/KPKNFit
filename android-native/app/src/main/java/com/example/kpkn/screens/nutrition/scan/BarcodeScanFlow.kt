package com.example.kpkn.screens.nutrition.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.nutrition.customfood.CustomFoodFormOverlay

/** La cámara real: el lector de ML Kit empaquetado y CameraX. Se libera al salir de la composición. */
@Composable
fun rememberCameraBarcodeSource(): CameraBarcodeSource {
    val context = LocalContext.current.applicationContext
    val decoder = remember { MlKitBarcodeDecoder() }
    val source = remember(decoder) { CameraBarcodeSource(context, decoder) }
    DisposableEffect(source) {
        onDispose {
            source.stop()
            decoder.close()
        }
    }
    return source
}

/** La vista previa de la cámara; une su superficie y el ciclo de vida de la pantalla con [source]. */
@Composable
fun CameraPreviewSurface(source: CameraBarcodeSource, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                view = this
            }
        },
    )
    DisposableEffect(view, owner) {
        view?.let { source.attach(it, owner) }
        onDispose { source.detach() }
    }
}

/**
 * El escáner a pantalla completa, con su modelo, el permiso de cámara y, si hace falta, el formulario de alimento propio con el
 * código ya puesto. Nada se guarda aquí: [lookup] consulta la base del teléfono, [saveCustomFood] crea el alimento propio (durable)
 * y [onConfirm] entrega el producto elegido con los gramos para que el registro de comidas lo añada por su camino de siempre.
 *
 * [initialAccess] nulo lee el permiso real de la cámara; las vistas previas fijan uno. [preview] es la vista de la cámara
 * (la superficie real o un marcador).
 */
@Composable
fun BarcodeScanFlow(
    source: BarcodeSource,
    lookup: suspend (String) -> BarcodeLookup,
    saveCustomFood: suspend (FoodItem) -> Unit,
    onConfirm: (FoodItem, Double) -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    initialAccess: CameraAccess? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val model = remember {
        BarcodeScanViewModel(
            source = source,
            lookup = lookup,
            scope = scope,
            initialAccess = initialAccess ?: if (hasCameraPermission(context)) CameraAccess.GRANTED else CameraAccess.ASK,
            onAccepted = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        )
    }
    DisposableEffect(model) { onDispose { model.close() } }
    // Al volver de Ajustes el permiso puede haberse concedido sin pasar por la petición: se vuelve a leer. Con [initialAccess] fijado
    // (vistas previas y harness) lo decide quien llama y no se toca.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model, initialAccess) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && initialAccess == null) {
                model.onPermissionRechecked(hasCameraPermission(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by model.state.collectAsState()
    val torchAvailable by model.torchAvailable.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.onPermissionResult(granted)
    }
    var creating by remember { mutableStateOf(false) }

    FullScreenOverlay(onBack = onDismiss) {
        BarcodeScanContent(
            state = state,
            torchAvailable = torchAvailable,
            actions = BarcodeScanActions(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { openAppSettings(context) },
                onShowManual = model::showManualEntry,
                onShowCamera = model::showCamera,
                onManualTextChange = model::onManualTextChange,
                onSubmitManual = model::submitManual,
                onToggleTorch = model::toggleTorch,
                onClose = onDismiss,
                onScanAnother = model::scanAnother,
                onCreateOwn = { creating = true },
                onConfirm = onConfirm,
            ),
            preview = preview,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (creating) {
        val found = (state.result as? BarcodeLookup.Found)?.food
        CustomFoodFormOverlay(
            initial = null,
            save = saveCustomFood,
            onSaved = { food ->
                creating = false
                model.showCreated(food)
            },
            onDismiss = { creating = false },
            barcode = state.code ?: found?.sourceRecordId,
            prefillName = found?.name,
            prefillBrand = found?.brand,
        )
    }
}

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
