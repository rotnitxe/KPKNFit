package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.NutritionInterpretationBridge
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.screens.onboarding.design.WizardFonts
import java.util.Locale
import kotlin.math.roundToInt

/** Composiciones de la tortilla del lienzo (f-clar), en su orden: la respuesta se declara y se vuelve a resolver. */
internal val TORTILLA_COMPOSITIONS = listOf("papas" to "De papas", "huevo" to "De huevo", "trigo" to "Trigo (wrap)", "maíz" to "De maíz")

/** Niveles de aceite del motor con su nombre del lienzo y las gotas que se dibujan (f-clar: Nada, Poco, Medio, Mucho). */
internal val OIL_CHOICES = listOf(
    Triple("sin aceite", "Nada", 0),
    Triple("poco", "Poco", 1),
    Triple("medio", "Medio", 2),
    Triple("abundante", "Mucho", 3),
)

/** Prefijos de la clave de una respuesta: dicen a la hoja qué hacer con ella. */
object LoggerChoiceKeys {
    const val IDENTITY = "identity:"
    const val COMPOSITION = "compose:"
    const val COOKED = "cook:cooked"
    const val RAW = "cook:raw"
    const val OIL = "oil:"
    const val PORTION = "portion:"
    const val MORE = "more"
    const val ADJUST = "adjust"
}

/** Una respuesta de una duda: su clave ([LoggerChoiceKeys]), su texto y, en el aceite, cuántas gotas lleva. */
data class LoggerQuestionChoice(val key: String, val label: String, val drops: Int? = null)

/**
 * Una duda tal como se pinta en su tarjeta (f-clar): la pregunta corta, las respuestas (chips, o gotas en el aceite), la respuesta por
 * defecto («Seguir con «nada»», null si no hay estimación que aceptar), la elegida y si tocarla abre el ajuste de cantidad (cantidad
 * declarada que no se pudo leer).
 */
data class LoggerQuestion(
    val text: String,
    val choices: List<LoggerQuestionChoice>,
    val skipLabel: String?,
    val selectedKey: String? = null,
    val opensAdjust: Boolean = false,
) {
    val drops: Boolean get() = choices.isNotEmpty() && choices.all { it.drops != null }
}

/**
 * Los candidatos de una pregunta de identidad, sin repetir la respuesta corta (N6): en la tortilla, «Tortilla de maíz» y «De maíz» llevan
 * al mismo alimento, así que solo queda «De maíz».
 */
internal fun clarificationCandidateChoices(question: ClarificationRequest.Identity): List<Pair<String, String>> {
    val choices = question.candidateIds.zip(question.candidateLabels).take(4)
    if (question.requestId != "tortilla_composition") return choices
    return choices.filterNot { (_, label) ->
        TORTILLA_COMPOSITIONS.any { (composition, _) -> label.equals("Tortilla de $composition", ignoreCase = true) }
    }
}

/** El nombre del lienzo de un nivel de aceite del motor («sin aceite» → «nada»). */
internal fun loggerOilLabel(level: String): String =
    OIL_CHOICES.firstOrNull { it.first.equals(level, ignoreCase = true) }?.second ?: level

/**
 * La duda [question] de [tag] con los textos del lienzo (f-clar, f-result): «¿Qué llevaba la ensalada?», «¿De qué era la tortilla?»
 * (De papas, De huevo, Trigo (wrap), De maíz), «¿Qué corte era?», «¿La cantidad era en crudo o cocida?» (En crudo, Cocida), «¿Cuánto
 * aceite tenía?» con gotas, «¿Comiste todo el envase?» (Todo (180 g), Una porción (30 g)) y la cantidad declarada que abre el ajuste.
 */
fun loggerQuestionFor(tag: ResolvedTag, question: ClarificationRequest, selectedKey: String? = null): LoggerQuestion {
    val canSkip = NutritionInterpretationBridge.canAcceptEstimate(tag)
    val currentName = (tag.interpretationV2?.canonicalIdentity ?: tag.foodItem?.name)?.lowercase(Locale.forLanguageTag("es"))
    val defaultSkip = if (canSkip) "Seguir con la estimación" else null
    val dryVsCooked = tag.clarificationKind == CookingStateResolver.ClarificationKind.DRY_VS_COOKED
    return when (question) {
        is ClarificationRequest.Identity -> when (question.requestId) {
            "composition" -> LoggerQuestion(
                text = "¿Qué llevaba la ensalada?",
                choices = clarificationCandidateChoices(question).map { (id, label) ->
                    LoggerQuestionChoice(LoggerChoiceKeys.IDENTITY + id, label)
                } + LoggerQuestionChoice(LoggerChoiceKeys.MORE, "Otra"),
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
            "tortilla_composition" -> LoggerQuestion(
                text = "¿De qué era la tortilla?",
                choices = TORTILLA_COMPOSITIONS.map { (composition, label) -> LoggerQuestionChoice(LoggerChoiceKeys.COMPOSITION + composition, label) },
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
            "cut" -> LoggerQuestion(
                text = "¿Qué corte era?",
                choices = clarificationCandidateChoices(question).map { (id, label) -> LoggerQuestionChoice(LoggerChoiceKeys.IDENTITY + id, label) },
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
            else -> LoggerQuestion(
                text = "¿Cuál se parece más?",
                choices = clarificationCandidateChoices(question).take(2).map { (id, label) ->
                    LoggerQuestionChoice(LoggerChoiceKeys.IDENTITY + id, label)
                } + LoggerQuestionChoice(LoggerChoiceKeys.MORE, "Ver más"),
                skipLabel = if (canSkip && !currentName.isNullOrBlank()) "Seguir con «$currentName»" else defaultSkip,
                selectedKey = selectedKey,
            )
        }
        is ClarificationRequest.WeightState -> LoggerQuestion(
            text = if (dryVsCooked) "¿La cantidad era en seco o cocida?" else "¿La cantidad era en crudo o cocida?",
            choices = listOf(
                LoggerQuestionChoice(LoggerChoiceKeys.RAW, if (dryVsCooked) "En seco" else "En crudo"),
                LoggerQuestionChoice(LoggerChoiceKeys.COOKED, "Cocida"),
            ),
            skipLabel = defaultSkip,
            selectedKey = selectedKey,
        )
        is ClarificationRequest.Oil -> LoggerQuestion(
            text = "¿Cuánto aceite tenía?",
            choices = OIL_CHOICES.filter { (level, _, _) -> question.options.any { it.equals(level, ignoreCase = true) } }
                .map { (level, label, drops) -> LoggerQuestionChoice(LoggerChoiceKeys.OIL + level, label, drops) },
            skipLabel = if (canSkip) "Seguir con «${loggerOilLabel(tag.oilLevel).lowercase(Locale.ROOT)}»" else null,
            selectedKey = selectedKey,
        )
        is ClarificationRequest.Portion -> when {
            question.options.isEmpty() || question.requestId == "declared_amount" -> LoggerQuestion(
                text = "No pude leer la cantidad: tócala para ajustarla",
                choices = emptyList(),
                skipLabel = null,
                selectedKey = selectedKey,
                opensAdjust = true,
            )
            question.requestId == "package_portion" -> LoggerQuestion(
                text = "¿Comiste todo el envase?",
                choices = question.options.mapIndexed { index, option ->
                    val amount = Regex("\\(([^)]*)\\)").find(option.label)?.groupValues?.get(1) ?: "${option.grams.roundToInt()} g"
                    LoggerQuestionChoice(
                        LoggerChoiceKeys.PORTION + index,
                        if (index == 0) "Todo ($amount)" else "Una porción (${option.grams.roundToInt()} g)",
                    )
                },
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
            else -> LoggerQuestion(
                text = "¿Qué porción fue?",
                choices = question.options.mapIndexed { index, option -> LoggerQuestionChoice(LoggerChoiceKeys.PORTION + index, option.label) },
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
        }
    }
}

private val QuestionStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 19.sp)
private val OptStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)
private val DropStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)

/**
 * El bloque de la duda dentro de su tarjeta (lienzo `.q`): filete superior cálido, el punto de energía con la pregunta, las respuestas
 * (`.qopts`: chips de 38 dp; la elegida en tinta) o las gotas del aceite (`.drop3`), y «Seguir con …» (`.qskip`). [answered] apaga el
 * tono cálido del filete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggerQuestionBlock(
    question: LoggerQuestion,
    onChoice: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    answered: Boolean = question.selectedKey != null,
) {
    val rule by animateColorAsState(
        if (answered) Color.White.copy(alpha = 0.06f) else LoggerColors.ene.copy(alpha = 0.133f),
        tween(400),
        label = "loggerQuestionRule",
    )
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
        Row(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .then(if (question.opensAdjust) Modifier.clickable(role = Role.Button) { onChoice(LoggerChoiceKeys.ADJUST) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (answered) LoggerColors.faint else LoggerColors.ene))
            Text(text = question.text, style = QuestionStyle, color = LoggerColors.ink)
        }
        if (question.drops) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                question.choices.forEach { choice ->
                    LoggerDropChoice(
                        choice = choice,
                        selected = choice.key == question.selectedKey,
                        onClick = { onChoice(choice.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (question.choices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                question.choices.forEach { choice ->
                    LoggerQuestionChip(choice.label, choice.key == question.selectedKey) { onChoice(choice.key) }
                }
            }
        }
        if (question.skipLabel != null && !answered) {
            Text(
                text = question.skipLabel,
                style = LoggerText.cap,
                color = LoggerColors.faint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(top = 10.dp),
            )
        }
    }
}

/** `.qopts span`: 38 dp, radio 19, blanco al 5 % con borde al 8 %; elegida, en tinta con texto negro. */
@Composable
private fun LoggerQuestionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    val fill by animateColorAsState(if (selected) LoggerColors.ink else Color.White.copy(alpha = 0.047f), tween(300), label = "loggerQChip")
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.078f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = OptStyle, color = if (selected) LoggerColors.onInk else LoggerColors.ink, maxLines = 1)
    }
}

/** `.drop3 button` del aceite: las gotas (una como mínimo; «Nada» con su nombre atenuado) y el nombre; elegida, borde de tinta de 2 dp. */
@Composable
private fun LoggerDropChoice(choice: LoggerQuestionChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.031f), shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) LoggerColors.ink else Color.White.copy(alpha = 0.07f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 2.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(maxOf(1, choice.drops ?: 1)) {
                Icon(LoggerIcons.Drop, contentDescription = null, modifier = Modifier.size(14.dp), tint = LoggerColors.ink)
            }
        }
        Text(
            text = choice.label,
            style = DropStyle,
            color = LoggerColors.ink,
            modifier = Modifier.alpha(if ((choice.drops ?: 0) == 0) 0.4f else 1f),
        )
    }
}
