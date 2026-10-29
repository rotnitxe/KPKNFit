package com.example.kpkn.screens.nutrition.design.logger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.ui.components.KpknDialogTheme
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal

/**
 * Tarjeta de confirmación centrada (lienzo s-forget y s-discard): velo detrás, 20 dp de margen a los lados, 28 dp de radio y el fondo plano de
 * las hojas (`#141414`, sin vidrio). Título, frase y dos píldoras pequeñas del mismo ancho: [cancelLabel] con filete y [confirmLabel] en rojo suave. Tocar el
 * velo o volver atrás es cancelar.
 */
@Composable
fun LoggerConfirmCard(
    title: String,
    sub: String,
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Mientras la tarjeta está en composición, la isla superior se oculta (como con hojas y diálogos).
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    KpknPortal {
        BackHandler(onBack = onCancel)
        Box(modifier = Modifier.fillMaxSize().zIndex(360f), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.67f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .background(LoggerColors.sheet, RoundedCornerShape(28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                KpknDialogTheme {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 30.dp)) {
                        LoggerSheetTitle(title = title, sub = sub)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LoggerPill(
                                text = cancelLabel,
                                onClick = onCancel,
                                tone = LoggerPillTone.SECONDARY,
                                small = true,
                                modifier = Modifier.weight(1f),
                            )
                            LoggerPill(
                                text = confirmLabel,
                                onClick = onConfirm,
                                tone = LoggerPillTone.DANGER_SOFT,
                                small = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «¿Olvidar lo aprendido?» (lienzo s-forget): dice la consecuencia en una frase; las comidas registradas no cambian. */
@Composable
fun LoggerForgetDialog(onCancel: () -> Unit, onForget: () -> Unit) {
    LoggerConfirmCard(
        title = "¿Olvidar lo aprendido?",
        sub = "KPKN volverá a preguntarte cómo cocinas, qué leche usas y tus porciones habituales. Tus comidas registradas no cambian.",
        cancelLabel = "Cancelar",
        confirmLabel = "Olvidar",
        onCancel = onCancel,
        onConfirm = onForget,
    )
}
