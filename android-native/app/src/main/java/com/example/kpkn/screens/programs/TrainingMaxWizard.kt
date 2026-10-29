package com.example.kpkn.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.training.TrainingMaxResolver
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.screens.programdetail.design.ScrollEdgeFade
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del botón de guardar del TM. */
internal const val TM_SAVE_TAG = "program-tm-save"

/** Etiqueta de prueba de la fila de un levantamiento; el campo de kg y el TM llevan `-field` y `-tm` detrás. */
internal fun tmRowTag(lift: LiftMark): String = "program-tm-row-${lift.name}"

/** Los levantamientos del TM: los tres de competición y el press militar, que es opcional. */
private val TmLifts = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT, LiftMark.OVERHEAD_PRESS)

@Composable
fun TrainingMaxWizard(
    initial: PowerliftingProfile? = null,
    autoEstimate: PowerliftingProfile? = null,
    trainingMaxPercent: Double = 0.90,
    confirmLabel: String = "Aplicar y crear",
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
    unit: String = MarksMath.UNIT_KG,
    /** Qué hace la acción de texto de abajo; null = lo mismo que cerrar ([onDismiss]). Solo «Crear sin TM» la usa. */
    onSkip: (() -> Unit)? = null,
    /** El texto de esa acción de abajo; «Más tarde» si no se cambia. */
    skipLabel: String = "Más tarde",
    /** [initial] sale del historial de la persona: la hoja lo dice con «Precargado con tu historial.». */
    initialFromHistory: Boolean = false,
) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.9f) {
        TrainingMaxContent(
            initial = initial,
            autoEstimate = autoEstimate,
            trainingMaxPercent = trainingMaxPercent,
            confirmLabel = confirmLabel,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            unit = unit,
            onSkip = onSkip,
            skipLabel = skipLabel,
            initialFromHistory = initialFromHistory,
        )
    }
}

/**
 * El contenido de la hoja de TM (escena pr-tm), sin su contenedor: una fila por levantamiento con su nombre, el campo del 1RM y el TM
 * a la derecha, la nota «Precargado con tu historial.» si viene del historial y, abajo, «Guardar» y «Más tarde». Sin 1RM propio, la
 * estimación del historial ([autoEstimate]) se precarga. La unidad es la de Ajustes ([unit]): la hoja no tiene conmutador, y lo
 * guardado es kg.
 */
@Composable
internal fun TrainingMaxContent(
    initial: PowerliftingProfile?,
    autoEstimate: PowerliftingProfile?,
    trainingMaxPercent: Double,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
    unit: String = MarksMath.UNIT_KG,
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "Más tarde",
    initialFromHistory: Boolean = false,
) {
    val u = MarksMath.normalizeUnit(unit)
    val seed = remember(initial, autoEstimate) {
        val own = tmMarksOf(initial)
        if (own.isEmpty() && autoEstimate != null) tmMarksOf(autoEstimate) to true else own to initialFromHistory
    }
    var marks by remember(seed) { mutableStateOf(seed.first) }
    // Lo que se ve en cada campo: se guarda aparte para no pisar «142,» mientras se escribe.
    val texts = remember(seed, u) {
        mutableStateMapOf<LiftMark, String>().apply {
            seed.first.forEach { (lift, kg) -> put(lift, MarksMath.formatNumber(MarksMath.displayValue(kg, u))) }
        }
    }
    val preview = TrainingMaxResolver.hydrateProfile(
        PowerliftingProfile(
            squat1RM = positiveKg(marks[LiftMark.SQUAT]),
            bench1RM = positiveKg(marks[LiftMark.BENCH]),
            deadlift1RM = positiveKg(marks[LiftMark.DEADLIFT]),
            overhead1RM = positiveKg(marks[LiftMark.OVERHEAD_PRESS]),
        ),
        trainingMaxPercent,
    )
    val tmOf = mapOf(
        LiftMark.SQUAT to preview.squatTM,
        LiftMark.BENCH to preview.benchTM,
        LiftMark.DEADLIFT to preview.deadliftTM,
        LiftMark.OVERHEAD_PRESS to preview.overheadTM,
    )
    val hasValidInput = TmLifts.any { positiveKg(marks[it]) != null }
    val showHistoryNote = seed.second && seed.first.isNotEmpty()
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
                    text = "Máximo de entrenamiento",
                    style = NewProgramTitleStyle,
                    color = WizardColors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = trainingMaxIntro(trainingMaxPercent),
                    style = KpknType.body.copy(lineBreak = LineBreak.Paragraph),
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                TmLifts.forEachIndexed { index, lift ->
                    TmLiftRow(
                        lift = lift,
                        text = texts[lift].orEmpty(),
                        unit = u,
                        tmKg = tmOf[lift],
                        divider = index > 0,
                        onText = { raw ->
                            texts[lift] = raw
                            val kg = parseMark(raw)?.let { MarksMath.clampKg(MarksMath.toKg(it, u)) }
                            marks = if (kg == null) marks - lift else marks + (lift to kg)
                        },
                    )
                }
                if (showHistoryNote) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = "Precargado con tu historial.", style = KpknType.caption, color = WizardColors.textFaint)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (canScrollMore) ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            KpknPrimaryPill(
                text = confirmLabel,
                onClick = { onConfirm(preview) },
                modifier = Modifier.fillMaxWidth(),
                tag = TM_SAVE_TAG,
                enabled = hasValidInput,
            )
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                KpknTextAction(text = skipLabel, onClick = onSkip ?: onDismiss, color = WizardColors.textMuted)
            }
        }
    }
}

/** El texto de los campos de kg y del TM: números tabulares. */
private val TmNumberStyle = TextStyle(
    fontFamily = KpknType.body.fontFamily,
    fontSize = KpknType.body.fontSize,
    lineHeight = KpknType.body.lineHeight,
    fontFeatureSettings = "'tnum' 1",
)

/**
 * Una fila de pr-tm (`.ratio` de 1fr · 92 · 64): el nombre del levantamiento, el campo del 1RM en [unit] y «TM 144» a la derecha, o «—»
 * sin 1RM. «Press militar» lleva «· opcional» detrás del nombre. [divider] pinta el filete de arriba.
 */
@Composable
private fun TmLiftRow(
    lift: LiftMark,
    text: String,
    unit: String,
    tmKg: Double?,
    divider: Boolean,
    onText: (String) -> Unit,
) {
    val name = if (lift == LiftMark.OVERHEAD_PRESS) "${lift.label} · opcional" else lift.label
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 4.dp)
                .testTag(tmRowTag(lift)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = KpknType.body,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            val fieldShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .heightIn(min = 44.dp)
                    .background(Color.Black.copy(alpha = 0.23f), fieldShape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), fieldShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = TmNumberStyle.copy(color = WizardColors.text),
                    cursorBrush = SolidColor(WizardColors.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tmRowTag(lift)}-field")
                        .semantics { contentDescription = "1RM de ${lift.label} en $unit" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) Text(text = unit, style = KpknType.caption, color = WizardColors.textFaint)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = tmKg?.let { "TM ${MarksMath.formatNumber(MarksMath.displayValue(it, unit))}" } ?: "—",
                style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1", textAlign = TextAlign.End),
                color = WizardColors.textFaint,
                modifier = Modifier.width(64.dp).testTag("${tmRowTag(lift)}-tm"),
            )
        }
    }
}

/** Lo escrito como número: admite coma o punto; null si no es un número finito mayor que cero. */
internal fun parseMark(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** Las marcas declaradas de un perfil (solo las mayores que cero): lo que la regla muestra como marca, por levantamiento. */
internal fun tmMarksOf(profile: PowerliftingProfile?): Map<LiftMark, Double> = buildMap {
    positiveKg(profile?.squat1RM)?.let { put(LiftMark.SQUAT, it) }
    positiveKg(profile?.bench1RM)?.let { put(LiftMark.BENCH, it) }
    positiveKg(profile?.deadlift1RM)?.let { put(LiftMark.DEADLIFT, it) }
    positiveKg(profile?.overhead1RM)?.let { put(LiftMark.OVERHEAD_PRESS, it) }
}

/** Un 1RM válido es mayor que cero; con 0 o sin dato, el levantamiento cuenta como «No la sé». */
private fun positiveKg(kg: Double?): Double? = kg?.takeIf { it > 0 }

/**
 * Explicación bajo el título (pr-tm); el porcentaje es el de la receta del plan que se va a crear: 90 % en la mayoría y 87 % en Texas
 * Method y Madcow.
 */
internal fun trainingMaxIntro(trainingMaxPercent: Double): String =
    "Escribe tu 1RM. El TM es el ${(trainingMaxPercent * 100).toInt()} % y con él se calculan las cargas."
