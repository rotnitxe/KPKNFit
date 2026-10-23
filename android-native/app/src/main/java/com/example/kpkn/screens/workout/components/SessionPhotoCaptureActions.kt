package com.example.kpkn.screens.workout.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.launch

/** Camera/gallery and pending-capture recovery moved from the cockpit to the existing album. */
@Composable
internal fun SessionPhotoCaptureActions(
    onAddPhoto: (Uri) -> Unit,
    onBeginCameraCapture: suspend () -> WorkoutMediaCaptureController.ExternalPhotoCapture?,
    onCompleteCameraCapture: (String, Boolean) -> Unit,
    onCameraLaunchFailed: (String) -> Unit,
    captureError: String?,
    onRetryPendingCaptures: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCaptureId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onAddPhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingCaptureId?.let { onCompleteCameraCapture(it, ok) }
        pendingCaptureId = null
    }
    val startCamera: () -> Unit = {
        if (!preparing && pendingCaptureId == null) {
            preparing = true
            scope.launch {
                val capture = try { onBeginCameraCapture() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                finally { preparing = false }
                if (capture != null) {
                    pendingCaptureId = capture.id
                    try { camera.launch(capture.uri) }
                    catch (_: Exception) { pendingCaptureId = null; onCameraLaunchFailed(capture.id) }
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startCamera() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("live-session-photo-actions")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderSheetAction("Cámara", {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }, Modifier.weight(1f), enabled = !preparing && pendingCaptureId == null)
            HeaderSheetAction("Galería", { gallery.launch(arrayOf("image/*")) }, Modifier.weight(1f))
        }
        if (!captureError.isNullOrBlank()) {
            Text(captureError, style = KpknType.caption, color = WizardColors.textMuted)
            HeaderSheetAction("Reintentar", onRetryPendingCaptures, Modifier.fillMaxWidth())
        }
    }
}
