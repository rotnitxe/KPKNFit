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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.NutritionInterpretationBridge
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.screens.onboarding.design.WizardFonts
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
 * Una duda tal como se pinta en su tarjeta (f-clar): la pregunta corta, las respuestas (siempre chips; las gotas del aceite ya no se
 * dibujan), la respuesta por defecto ([skipLabel]: se conserva por si hace falta, pero la tarjeta ya no la muestra: guardar sigue con
 * lo estimado), la elegida y si tocarla abre el ajuste de cantidad (cantidad declarada que no se pudo leer).
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

/**
 * Las porciones de una pregunta sin las repetidas: la que vale lo mismo (los mismos gramos, redondeados) que otra anterior sale, así que
 * «porción (150 g)» desaparece junto a «Lo estimado (150 g)». Devuelve cada porción con su posición en [options], que es lo que lleva su
 * clave de respuesta ([LoggerChoiceKeys.PORTION]).
 */
internal fun loggerDistinctPortions(options: List<PortionOption>): List<Pair<Int, PortionOption>> {
    val seen = mutableSetOf<Long>()
    return options.withIndex().filter { (_, option) -> seen.add(option.grams.roundToLong()) }.map { it.index to it.value }
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
                choices = loggerDistinctPortions(question.options).map { (index, option) ->
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
                choices = loggerDistinctPortions(question.options).map { (index, option) ->
                    LoggerQuestionChoice(LoggerChoiceKeys.PORTION + index, option.label)
                },
                skipLabel = defaultSkip,
                selectedKey = selectedKey,
            )
        }
    }
}

private val QuestionStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
private val OptStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

/** La respuesta partida como la pinta el chip: el nombre con mayúscula inicial y, si lleva una cantidad entre paréntesis, esa cantidad aparte. */
data class LoggerChipParts(val name: String, val detail: String?)

private val QuantityInParens = Regex("""^(.*?)\s*\(\s*(\d[^)]*)\)\s*$""")

/**
 * «Lo estimado (150 g)» → «Lo estimado» + «150 g»; «plato (250 g)» → «Plato» + «250 g». Solo se separa un paréntesis que empieza por una cifra:
 * «Trigo (wrap)» se queda entero. El nombre sale con la primera letra en mayúscula.
 */
fun loggerChipParts(label: String): LoggerChipParts {
    val match = QuantityInParens.find(label.trim())
    val name = (match?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: label.trim())
    val detail = match?.takeIf { it.groupValues[1].isNotBlank() }?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }
    return LoggerChipParts(name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, detail)
}

/**
 * El bloque de la duda dentro de su tarjeta (lienzo `.np-q`): filete de 1 dp, 12 dp de aire, la pregunta (Inter SemiBold 14) con el punto
 * de acento de 7 dp SOLO mientras no se ha respondido, y las respuestas como chips de 40 dp con borde blanco al 11 % (`.np-opts`); la
 * elegida va en tinta con texto negro. Todas las preguntas (porción, aceite, leche…) tienen esta misma forma; «Seguir con la estimación»
 * ya no existe ([onSkip] se conserva sin uso). [answered] apaga el punto.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LoggerColors.hair))
        Row(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 10.dp)
                .then(if (question.opensAdjust) Modifier.clickable(role = Role.Button) { onChoice(LoggerChoiceKeys.ADJUST) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!answered) Box(Modifier.size(7.dp).clip(CircleShape).background(LoggerColors.accent))
            Text(text = question.text, style = QuestionStyle, color = LoggerColors.ink)
        }
        if (question.choices.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                question.choices.forEach { choice ->
                    LoggerQuestionChip(choice.label, choice.key == question.selectedKey) { onChoice(choice.key) }
                }
            }
        }
    }
}

/** `.np-opts button`: 40 dp, radio 20, borde blanco al 11 %; nombre en tinta y, tras « · », la cantidad en gris tenue; elegida, fondo de tinta y texto negro. */
@Composable
private fun LoggerQuestionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(if (selected) LoggerColors.ink else Color.Transparent, tween(200), label = "loggerQChip")
    val parts = remember(label) { loggerChipParts(label) }
    val nameInk = if (selected) LoggerColors.onInk else LoggerColors.ink
    val detailInk = if (selected) LoggerColors.onInk.copy(alpha = 0.73f) else LoggerColors.faint
    val text = remember(parts, nameInk, detailInk) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = nameInk)) { append(parts.name) }
            parts.detail?.let { withStyle(SpanStyle(color = detailInk, fontWeight = FontWeight.Normal)) { append(" · $it") } }
        }
    }
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(fill, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.11f), shape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = OptStyle)
    }
}
