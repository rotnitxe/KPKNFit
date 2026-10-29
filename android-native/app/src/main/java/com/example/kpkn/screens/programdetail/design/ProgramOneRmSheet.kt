package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba de «Guardar y continuar» en la hoja de máximos. */
internal const val ONE_RM_SAVE_TAG = "program-one-rm-save"

/** Los tres levantamientos del test de 1RM, en el orden de la regla. */
private val OneRmLifts = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT)

/** Alto de la franja que desvanece el borde de abajo de una zona con scroll. */
private val ScrollFadeHeight = 28.dp

/**
 * Franja de desvanecido sobre el borde de abajo de una zona con scroll: una fila cortada se lee como «hay más» y no como un fallo.
 * Quien la pone decide si hace falta (solo cuando queda contenido por ver).
 */
@Composable
internal fun ScrollEdgeFade(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScrollFadeHeight)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, WizardColors.background),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            },
    )
}

/** El nombre de cada levantamiento en la hoja (m-onerm: «Sentadilla», «Press de banca», «Peso muerto»). */
internal fun oneRmLiftLabel(lift: LiftMark): String = when (lift) {
    LiftMark.BENCH -> "Press de banca"
    else -> lift.label
}

/** Un valor en kg como se escribe en su campo, en la unidad elegida: «150», «182,5»; vacío sin valor. */
internal fun oneRmFieldText(kg: Double?, unit: String): String =
    kg?.takeIf { it > 0 }?.let { MarksMath.displayValue(it, unit) }?.let { value ->
        String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", value).removeSuffix(",0")
    }.orEmpty()

/** Lo escrito en un campo, en kg; null si no es un número positivo. */
internal fun oneRmFieldKg(text: String, unit: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let { MarksMath.toKg(it, unit) }

/**
 * «Registrar tus máximos» (lienzo v6, m-onerm): «Test de fin de bloque», el título, la explicación y una fila `.ratio` por levantamiento con su
 * campo («150 kg»). [valuesKg] son las marcas en kg; [unit] es «kg» o «lb» y solo cambia cómo se muestran (la de Ajustes). «Guardar y
 * continuar» se activa con todos los levantamientos mayores que cero; «Omitir el test» ([onSkip]) va dentro de la hoja. Cerrar la hoja es
 * «más tarde»: el aviso sigue en «Pendiente».
 */
@Composable
fun ProgramOneRmSheet(
    valuesKg: Map<LiftMark, Double>,
    unit: String,
    onValueKg: (LiftMark, Double?) -> Unit,
    onSave: (squatKg: Double, benchKg: Double, deadliftKg: Double) -> Unit,
    onDismiss: () -> Unit,
    lifts: List<LiftMark> = OneRmLifts,
    prefilled: Boolean = false,
    onSaveMarks: ((Map<LiftMark, Double>) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    ProgramLienzoSheet(onDismiss = onDismiss) {
        OneRmSheetBody(
            valuesKg = valuesKg,
            unit = unit,
            onValueKg = onValueKg,
            onSave = onSave,
            lifts = lifts,
            prefilled = prefilled,
            onSaveMarks = onSaveMarks,
            onSkip = onSkip,
        )
    }
}

/**
 * El cuerpo de la hoja sin su contenedor: lo monta [ProgramOneRmSheet] y lo pintan las pruebas y la hoja de render (dentro de `KpknSheet`
 * los toques no llegan en Robolectric).
 */
@Composable
internal fun OneRmSheetBody(
    valuesKg: Map<LiftMark, Double>,
    unit: String,
    onValueKg: (LiftMark, Double?) -> Unit,
    onSave: (squatKg: Double, benchKg: Double, deadliftKg: Double) -> Unit,
    lifts: List<LiftMark> = OneRmLifts,
    prefilled: Boolean = false,
    onSaveMarks: ((Map<LiftMark, Double>) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val squat = valuesKg[LiftMark.SQUAT]?.takeIf { it > 0 }
    val bench = valuesKg[LiftMark.BENCH]?.takeIf { it > 0 }
    val deadlift = valuesKg[LiftMark.DEADLIFT]?.takeIf { it > 0 }
    // CAL5: solo los levantamientos del programa; guardar exige un valor mayor que cero en cada uno de ellos.
    val marks = lifts.mapNotNull { lift -> valuesKg[lift]?.takeIf { it > 0 }?.let { lift to it } }.toMap()
    val complete = lifts.isNotEmpty() && marks.size == lifts.size
    val unitLabel = if (MarksMath.isLb(unit)) MarksMath.UNIT_LB else MarksMath.UNIT_KG
    Column(modifier = Modifier.fillMaxWidth()) {
        LienzoSheetTitle(
            title = "Registrar tus máximos",
            eyebrow = "Test de fin de bloque",
            sub = if (prefilled) {
                "Los de tus series de test ya están puestos: confírmalos o corrígelos."
            } else {
                "Escribe los máximos válidos de tu sesión de test."
            },
        )
        lifts.forEach { lift ->
            // Lo escrito vive en la fila: así «182,» no se borra mientras se escribe; los kg suben a la pantalla en cada cambio.
            var text by remember(lift, unit) { mutableStateOf(oneRmFieldText(valuesKg[lift], unit)) }
            LienzoRatioRow(label = oneRmLiftLabel(lift), labelStyle = LienzoBodyStyle) {
                LienzoTextField(
                    label = null,
                    value = text,
                    onValueChange = { typed ->
                        text = typed
                        onValueKg(lift, oneRmFieldKg(typed, unit))
                    },
                    modifier = Modifier.width(110.dp),
                    placeholder = unitLabel,
                    numeric = true,
                    suffix = unitLabel,
                    tag = markTagOf(lift),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    topGap = 0.dp,
                    valueStyle = LienzoFieldValueStyle.copy(fontFeatureSettings = "'tnum' 1"),
                )
            }
        }
        ProgramLienzoPill(
            text = "Guardar y continuar",
            onClick = {
                if (onSaveMarks != null) {
                    if (complete) onSaveMarks(marks)
                } else if (squat != null && bench != null && deadlift != null) {
                    onSave(squat, bench, deadlift)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            tag = ONE_RM_SAVE_TAG,
            enabled = if (onSaveMarks != null) complete else squat != null && bench != null && deadlift != null,
        )
        if (onSkip != null) {
            LienzoTextAction(text = "Omitir el test", onClick = onSkip, centered = true, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** La etiqueta de prueba del campo de un levantamiento: la misma de las marcas del asistente. */
internal fun markTagOf(lift: LiftMark): String = com.example.kpkn.screens.onboarding.design.entreno.markTag(lift)
