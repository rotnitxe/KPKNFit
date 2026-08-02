package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.ui.components.KpknGlassDialog

/**
 * Diálogo obligatorio de primera elección del modo de captura de voz.
 * La primera vez no se puede cerrar (onDismissRequest vacío): el usuario debe elegir.
 */
@Composable
fun VoiceCaptureModeDialog(
    onChosen: (VoiceCaptureMode) -> Unit,
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<VoiceCaptureMode?>(null) }

    KpknGlassDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(40.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "¿Cómo quieres entrenar hoy?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            VoiceModeOptionCard(
                title = "Modo Música",
                description = "Sabemos que entrenar con música te encanta, por eso diseñamos este modo donde tus canciones favoritas no se escucharán en baja calidad como si estuvieras en una llamada; solo necesitas acercar tu teléfono para registrar las series, sin necesidad de desbloquearlo. Así entrenas sin distracciones y disfrutando de tu playlist favorita.",
                selected = selected == VoiceCaptureMode.MUSIC,
                onClick = { selected = VoiceCaptureMode.MUSIC },
            )

            Spacer(Modifier.height(12.dp))

            VoiceModeOptionCard(
                title = "Modo Manos Libres",
                description = "Si eres de esos usuarios que quiere la máxima concentración en sus entrenamientos y olvidarse del celular durante la sesión, puedes registrar tus entrenamientos usando directamente el micrófono de tus auriculares, sin necesidad de sacar tu teléfono. Esto degradará la calidad de tu música, pero será tu forma favorita de registrar tus marcas si quieres máxima libertad y cero distracciones.",
                selected = selected == VoiceCaptureMode.HANDS_FREE,
                onClick = { selected = VoiceCaptureMode.HANDS_FREE },
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { selected?.let(onChosen) },
                enabled = selected != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    "Empezar",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun VoiceModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
}
