package com.example.kpkn.screens.workout.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import kotlinx.coroutines.delay

/** Tiempo que la píldora muestra el check tras registrar (KPKN LS2b). */
const val WORKOUT_RECORD_PILL_CHECK_MS = 300L

/**
 * «Registrar serie» (KPKN LS2b): la [KpknPrimaryPill] a todo el ancho, con [label] («Registrar serie» o, en corrección,
 * «Actualizar serie») como descripción. Sustituye al botón redondo de registrar.
 *
 * Un toque llama a [onRecord] una sola vez: da un háptico, la píldora se convierte en un check durante
 * [WORKOUT_RECORD_PILL_CHECK_MS] y no admite más toques hasta que vuelve. Con movimiento reducido, el cambio es de golpe. Apagada con
 * [enabled] en falso, no responde. Sin estado de negocio: lo que registra lo decide quien la monta.
 */
@Composable
fun WorkoutRecordPill(
    label: String,
    enabled: Boolean,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val haptics = LocalHapticFeedback.current
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (checked) {
            delay(WORKOUT_RECORD_PILL_CHECK_MS)
            checked = false
        }
    }
    Box(modifier = modifier.fillMaxWidth().height(56.dp)) {
        KpknPrimaryPill(
            text = label,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                checked = true
                onRecord()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            enabled = enabled && !checked,
        )
        // El check tapa el texto: la píldora cambia de contenido sin cambiar de forma ni de color.
        AnimatedVisibility(
            visible = checked,
            modifier = Modifier.matchParentSize(),
            enter = if (reduced) EnterTransition.None else fadeIn(tween(durationMillis = 80)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(KpknShape.pill)
                    .background(WizardColors.text, KpknShape.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
    }
}
