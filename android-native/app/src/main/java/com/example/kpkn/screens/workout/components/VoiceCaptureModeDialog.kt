package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Diálogo obligatorio de primera elección del modo de captura de voz.
 * La primera vez no se puede cerrar (onDismissRequest vacío): el usuario debe elegir.
 * LR1b: lo de dentro lleva la piel nueva; las dos opciones son módulos y la elegida va en tinta.
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
                style = KpknType.dialogTitle,
                color = WizardColors.text,
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
                description = "Si eres de esos usuarios que quieren la máxima concentración en sus entrenamientos y olvidarse del celular durante la sesión, puedes registrar tus entrenamientos usando directamente el micrófono de tus auriculares, sin necesidad de sacar tu teléfono. Esto degradará la calidad de tu música, pero será tu forma favorita de registrar tus marcas si quieres máxima libertad y cero distracciones.",
                selected = selected == VoiceCaptureMode.HANDS_FREE,
                onClick = { selected = VoiceCaptureMode.HANDS_FREE },
            )

            Spacer(Modifier.height(20.dp))

            KpknPrimaryPill(
                text = "Empezar",
                onClick = { selected?.let(onChosen) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
            KpknTextAction(
                text = "Ahora no",
                onClick = onDismissRequest,
            )
        }
    }
}

/**
 * Opción del modo de voz: módulo de la piel nueva y tocable. La elegida se pinta en tinta, con su punto y el teñido de tinta al 8 %;
 * la otra, en `textMuted`.
 */
@Composable
private fun VoiceModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        accent = if (selected) WizardColors.text else null,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (selected) WizardColors.text else WizardColors.textFaint),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                title,
                style = KpknType.bodyStrong,
                color = if (selected) WizardColors.text else WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Start,
        )
    }
}
