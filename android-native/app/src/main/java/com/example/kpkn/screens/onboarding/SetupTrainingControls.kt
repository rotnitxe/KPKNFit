package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Controles compartidos del bloque Entreno: el aviso en línea de los pasos de opción y el abridor de «Conceptos clave».
 * Los demás (campo numérico, carga, fila de resumen del hito, avisos con botones) salieron con las pantallas antiguas del
 * inventario y del plan; los avisos con botones viven en `entreno/EntrenoPlanNotices.kt`.
 */

/** Tono de los avisos en línea del contenido (informativo o bloqueante). */
internal enum class TrainingNoticeTone { INFO, ERROR }

/**
 * Abre un concepto de «Conceptos clave» desde las hojas «Cómo funciona» del asistente (revisión del programa, C.P6). Lo
 * provee `SetupWizardScreen` con el callback que le pasa la navegación; sin proveedor (las pruebas y las vistas previas)
 * vale null y el glosario de la hoja no ofrece el enlace.
 */
internal val LocalOpenConcept = compositionLocalOf<((String) -> Unit)?> { null }

/** Aviso en línea del contenido: una restricción o un paso sin opciones. Solo texto: sin botones. */
@Composable
internal fun TrainingNotice(
    text: String,
    tone: TrainingNoticeTone = TrainingNoticeTone.INFO,
) {
    val error = tone == TrainingNoticeTone.ERROR
    Text(
        text = text,
        style = WizardTypography.bodySmall,
        color = if (error) WizardColors.danger else WizardColors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = WizardColors.unselectedBorderWidth,
                color = if (error) WizardColors.danger else WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
