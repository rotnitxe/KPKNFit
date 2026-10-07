package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Aire entre el símbolo del paso y su nota. */
private val NOTE_GAP = 12.dp

/**
 * La nota breve bajo el símbolo de un paso (la ayuda o el aviso de `docs/entreno-v2/COPY.md`): rol `note`, gris cálido,
 * a todo el ancho. Con [text] nulo no ocupa nada (ni siquiera el aire de encima); al aparecer, cambiar o irse se funde y
 * la altura del paso se acomoda sin salto, así la página no brinca cuando una elección trae o retira la nota.
 * Con «reducir movimiento» el cambio es inmediato.
 */
@Composable
internal fun EntrenoStepNote(text: String?, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    AnimatedContent(
        targetState = text,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(220)) togetherWith fadeOut(tween(140))).using(SizeTransform(clip = false))
            }
        },
        label = "entrenoStepNote",
    ) { note ->
        if (note != null) {
            Text(
                text = note,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.fillMaxWidth().padding(top = NOTE_GAP),
            )
        }
    }
}
