package com.example.kpkn.screens.workout.technique

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.screens.workout.technique.design.RecorderTopBar
import kotlinx.coroutines.launch

/** Envoltorio de depuración; la sesión en vivo monta directamente [TechniqueRecorderStage]. */
@Composable
fun TechniqueRecorderScreen(
    request: TechniqueRecorderRequest,
    onDismiss: () -> Unit,
    mediaCapture: WorkoutMediaCaptureController?,
    modifier: Modifier = Modifier,
    prefer60FpsSetting: Boolean = true,
    maxRecordSeconds: Int = 180,
    defaultPlateDiameterMm: Int = 450,
) {
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                mediaCapture?.let { stopTechniqueRecorderBeforeExit(it::stopIfRecording, it.isRecording) }
                onDismiss()
            }
        }
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            RecorderTopBar(
                exerciseName = request.exercise?.name ?: request.captureRequest.exerciseName ?: "Ejercicio",
                setLabel = request.setLabel, is60FpsGranted = false, showsPlateBadge = false,
                plateDiameterMm = null, plateBadgeExpanded = false, onTogglePlateExpanded = {},
                onSelectPlateDiameterMm = {}, onClose = close,
            )
            TechniqueRecorderStage(
                request = request, mediaCapture = mediaCapture, prefer60FpsSetting = prefer60FpsSetting,
                maxRecordSeconds = maxRecordSeconds, defaultPlateDiameterMm = defaultPlateDiameterMm,
                onFinished = close, onSwitchToPhoto = close, onOpenFicha = null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
