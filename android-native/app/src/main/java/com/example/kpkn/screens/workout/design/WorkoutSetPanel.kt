package com.example.kpkn.screens.workout.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknStepperMetric
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Panel de la serie (KPKN LS2b): la cara de la tarjeta de serie en la página normal, sobre el negro y sin caja.
 *
 * - [eyebrow] («Serie 2 de 4») va en mayúsculas por estilo. [summary] es lo que lee TalkBack de todo el panel.
 * - Las cifras: «KG» con [loadValue] y «REPS» (o «TIEMPO» con [timeMode]) con [valueText], cada una con sus ± de [loadStep] y
 *   [valueStep]. Al sumar o restar, la pieza llama a [onLoadStep] o [onValueStep] con el paso con signo.
 * - [modeLabels] (si hay) es el selector compacto de modo de carga, encima de la cifra de kg.
 * - La intensidad (RIR o RPE, [intensityScale]) con «Fallo»; sin [intensityScale], no hay fila.
 * - La sugerencia de carga (LS1) con «Usar».
 * - «Opciones avanzadas» despliega [advanced] debajo, plegado por defecto. Con [advanced] nulo no hay botón.
 *
 * No guarda estado: [advancedOpen] lo lleva quien la monta.
 */
@Composable
fun WorkoutSetPanel(
    eyebrow: String,
    summary: String,
    loadValue: String,
    loadStep: Double,
    onLoadStep: (Double) -> Unit,
    onLoadCommit: (String) -> Unit,
    valueLabel: String,
    valueText: String,
    valueStep: Double,
    onValueStep: (Double) -> Unit,
    onValueCommit: (String) -> Unit,
    timeMode: Boolean,
    intensityScale: WorkoutIntensityScale?,
    intensitySelected: Double?,
    intensityFailure: Boolean,
    onIntensitySelect: (Double) -> Unit,
    onToggleFailure: () -> Unit,
    suggestion: String?,
    suggestionApplied: Boolean,
    suggestionReason: String?,
    onUseSuggestion: () -> Unit,
    modeLabels: List<String>?,
    modeSelected: Int,
    onModeSelected: (Int) -> Unit,
    advancedOpen: Boolean,
    onToggleAdvanced: () -> Unit,
    advanced: (@Composable () -> Unit)?,
    timerRunning: Boolean = false,
    onTimerToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val loadDescription = if (loadValue == "—") "Sin carga" else "$loadValue kilos"
    val valueDescription = if (timeMode) valueText else "$valueText repeticiones"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = eyebrow.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (modeLabels != null) {
                    KpknSegmentedControl(
                        options = modeLabels,
                        selectedIndex = modeSelected,
                        onSelect = onModeSelected,
                        size = KpknSegmentedSize.Compact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                KpknStepperMetric(
                    label = "KG",
                    value = loadValue,
                    onMinus = { onLoadStep(-loadStep) },
                    onPlus = { onLoadStep(loadStep) },
                    contentDescription = loadDescription,
                    onCommit = onLoadCommit,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpknStepperMetric(
                    label = valueLabel,
                    value = valueText,
                    onMinus = { onValueStep(-valueStep) },
                    onPlus = { onValueStep(valueStep) },
                    contentDescription = valueDescription,
                    onCommit = onValueCommit,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // El cronómetro de la serie por tiempo: una píldora a todo el ancho, bajo las dos cifras (no deja hueco bajo KG).
        if (timeMode && onTimerToggle != null) {
            KpknSecondaryPill(
                text = if (timerRunning) "Pausar" else "Empezar",
                onClick = onTimerToggle,
                modifier = Modifier.fillMaxWidth(),
                icon = if (timerRunning) KpknLineIcons.Pause else KpknLineIcons.Play,
            )
        }
        if (intensityScale != null) {
            WorkoutIntensityRow(
                scale = intensityScale,
                selected = intensitySelected,
                failure = intensityFailure,
                onSelect = onIntensitySelect,
                onToggleFailure = onToggleFailure,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // «Sugerida» y «Opciones avanzadas» van juntos, a 8 dp; el bloque plegado se separa con el ritmo de 20 dp.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutLoadSuggestion(
                suggestion = suggestion,
                applied = suggestionApplied,
                onUse = onUseSuggestion,
                reason = suggestionReason,
                modifier = Modifier.fillMaxWidth(),
            )
            if (advanced != null) {
                AdvancedToggle(open = advancedOpen, onToggle = onToggleAdvanced)
            }
        }
        if (advanced != null) {
            AnimatedVisibility(
                visible = advancedOpen,
                enter = expandVertically() + fadeIn(tween(durationMillis = 160)),
                exit = shrinkVertically() + fadeOut(tween(durationMillis = 120)),
            ) {
                advanced()
            }
        }
    }
}

/**
 * «Opciones avanzadas» (KPKN LS2b): el mismo aspecto que `KpknTextAction`, con el chevron que gira 180° al abrir. Sin giro con
 * movimiento reducido. Zona táctil de 48 dp, con el estado («Desplegado» o «Plegado») para TalkBack.
 */
@Composable
private fun AdvancedToggle(open: Boolean, onToggle: () -> Unit) {
    val reduced = wizardReducedMotion()
    val angle by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(durationMillis = 220),
        label = "advancedChevron",
    )
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                role = Role.Button
                stateDescription = if (open) "Desplegado" else "Plegado"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Opciones avanzadas",
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = KpknLineIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(angle),
            tint = WizardColors.text,
        )
    }
}
