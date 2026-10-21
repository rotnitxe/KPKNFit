package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.kpkn.screens.onboarding.design.entreno.LiftMarksPicker
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

/**
 * «Registrar tus máximos» (P6): sustituye al diálogo de 1RM con la regla de marcas del wizard, sin la opción de repeticiones (aquí
 * se registra un 1RM). [valuesKg] son las marcas en kg; un levantamiento ausente es «No la sé». [unit] es «kg» o «lb» y solo cambia
 * cómo se muestra (la unidad es la de Ajustes: la hoja no tiene conmutador). [onSave] recibe los tres 1RM en kg y solo se activa con los tres mayores que cero.
 *
 * Sin estado: los valores y la unidad viven en la pantalla. Se cierra con el asa o atrás; no hay «Cancelar».
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
) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.9f) {
        OneRmSheetBody(
            valuesKg = valuesKg,
            unit = unit,
            onValueKg = onValueKg,
            onSave = onSave,
            lifts = lifts,
            prefilled = prefilled,
            onSaveMarks = onSaveMarks,
        )
    }
}

/**
 * El cuerpo de la hoja sin su contenedor: título, regla con scroll y la píldora fija abajo. Lo monta [ProgramOneRmSheet] y lo pintan
 * las pruebas y la hoja de render (dentro de `KpknSheet` los toques no llegan en Robolectric).
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
) {
    val squat = valuesKg[LiftMark.SQUAT]?.takeIf { it > 0 }
    val bench = valuesKg[LiftMark.BENCH]?.takeIf { it > 0 }
    val deadlift = valuesKg[LiftMark.DEADLIFT]?.takeIf { it > 0 }
    // CAL5: solo los levantamientos del programa; guardar exige un valor mayor que cero en cada uno de ellos.
    val marks = lifts.mapNotNull { lift -> valuesKg[lift]?.takeIf { it > 0 }?.let { lift to it } }.toMap()
    val complete = lifts.isNotEmpty() && marks.size == lifts.size
    val scroll = rememberScrollState()
    // Solo cambia el booleano: la franja aparece o se va sin recomponer la hoja en cada tick del scroll.
    val canScrollMore by remember { derivedStateOf { scroll.canScrollForward } }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Registrar tus máximos",
                    style = KpknType.title,
                    color = WizardColors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (prefilled) {
                        "Los máximos válidos de tu sesión de test. Los de tus series de test ya están puestos: confírmalos o corrígelos."
                    } else {
                        "Los máximos válidos de tu sesión de test."
                    },
                    style = KpknType.caption.copy(lineBreak = LineBreak.Paragraph),
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(16.dp))
                LiftMarksPicker(
                    lifts = lifts,
                    valuesKg = valuesKg,
                    unit = unit,
                    onValueKg = onValueKg,
                    showUnitSwitch = false,
                )
            }
            if (canScrollMore) ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            KpknPrimaryPill(
                text = "Guardar y continuar",
                onClick = {
                    if (onSaveMarks != null) {
                        if (complete) onSaveMarks(marks)
                    } else if (squat != null && bench != null && deadlift != null) {
                        onSave(squat, bench, deadlift)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                tag = ONE_RM_SAVE_TAG,
                enabled = if (onSaveMarks != null) complete else squat != null && bench != null && deadlift != null,
            )
        }
    }
}
