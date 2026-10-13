package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

private val EditShape = RoundedCornerShape(16.dp)

/** Letra mínima del nombre antes de cortarlo (KPKN_UI_V2 §5: `display` se encoge hasta 24). */
private const val MinNameSp = 24f

/**
 * Cabecera de la sesión (SESSION_EDITOR_V1 §2, punto 2): el navegador como chip, el nombre en `display` (dos líneas como máximo), la
 * descripción y la línea de datos. Nombre y descripción se editan en sitio al tocarlos; «Hecho» o perder el foco guardan con
 * [onNameCommit] u [onDescriptionCommit], como antes, y mientras se escribe se guarda con un retardo corto.
 *
 * El único estado es el de edición en curso (qué campo está abierto y su borrador). Los datos llegan de fuera.
 *
 * @param navigatorLabel texto del chip, p. ej. «Lun · Torso A».
 * @param durationMinutes minutos estimados; si es nulo o cero, la línea de datos no los nombra.
 */
@Composable
fun SessionEditorHeader(
    sessionId: String,
    name: String,
    description: String?,
    navigatorLabel: String,
    exerciseCount: Int,
    setCount: Int,
    durationMinutes: Int?,
    onNameCommit: (String) -> Unit,
    onDescriptionCommit: (String) -> Unit,
    onOpenNavigator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        SessionNavigatorChip(label = navigatorLabel, onClick = onOpenNavigator)
        Spacer(Modifier.height(10.dp))
        EditableSessionText(
            sessionId = sessionId,
            value = name,
            placeholder = "Nueva sesión",
            placeholderColor = WizardColors.textFaint,
            textStyle = KpknType.display,
            textColor = WizardColors.text,
            maxLines = 2,
            clickLabel = "Editar el nombre",
            isHeading = true,
            onCommit = onNameCommit,
        )
        Spacer(Modifier.height(4.dp))
        EditableSessionText(
            sessionId = sessionId,
            value = description.orEmpty(),
            placeholder = "Añadir una descripción",
            placeholderColor = WizardColors.textFaint,
            textStyle = KpknType.body,
            textColor = WizardColors.textMuted,
            maxLines = 2,
            clickLabel = "Editar la descripción",
            isHeading = false,
            onCommit = onDescriptionCommit,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = sessionEditorDataLine(exercises = exerciseCount, sets = setCount, minutes = durationMinutes),
            style = KpknType.caption.copy(fontFeatureSettings = "tnum"),
            color = WizardColors.textMuted,
        )
    }
}

/**
 * Línea de datos de la cabecera: «6 ejercicios · 22 series · ≈ 58 min». Sin duración no nombra el tiempo; sin ejercicios, lo dice.
 */
internal fun sessionEditorDataLine(exercises: Int, sets: Int, minutes: Int?): String {
    if (exercises <= 0) return "Sin ejercicios"
    val exerciseWord = if (exercises == 1) "ejercicio" else "ejercicios"
    val setWord = if (sets == 1) "serie" else "series"
    val parts = mutableListOf("$exercises $exerciseWord", "$sets $setWord")
    if (minutes != null && minutes > 0) parts += "≈ $minutes min"
    return parts.joinToString(" · ")
}

/**
 * Texto editable en sitio: se muestra con [textStyle]; al tocarlo se cambia por un campo del mismo ancho, con el recuadro de
 * `KpknTextField` y la letra del texto. «Hecho» o perder el foco lo guarda. Mientras se escribe, guarda tras 150 ms sin teclear, y al salir
 * de la pantalla guarda lo que quede a medias.
 */
@Composable
private fun EditableSessionText(
    sessionId: String,
    value: String,
    placeholder: String,
    placeholderColor: Color,
    textStyle: TextStyle,
    textColor: Color,
    maxLines: Int,
    clickLabel: String,
    isHeading: Boolean,
    onCommit: (String) -> Unit,
) {
    var editing by remember(sessionId) { mutableStateOf(false) }
    var draft by remember(sessionId) { mutableStateOf(value) }
    // Compose avisa de «sin foco» al montar el campo: solo cuenta perder un foco que ya se ha ganado.
    var hadFocus by remember(sessionId) { mutableStateOf(false) }
    val latestCommit by rememberUpdatedState(onCommit)
    val latestValue by rememberUpdatedState(value)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun finish() {
        if (!editing) return
        editing = false
        if (draft != latestValue) latestCommit(draft)
    }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }
    LaunchedEffect(draft, editing) {
        if (editing && draft != latestValue) {
            delay(150)
            latestCommit(draft)
        }
    }
    DisposableEffect(sessionId) {
        onDispose {
            if (editing && draft != latestValue) latestCommit(draft)
        }
    }

    if (editing) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = textStyle.copy(color = WizardColors.text),
            cursorBrush = SolidColor(WizardColors.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    finish()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        finish()
                    }
                },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KpknSurface.inset, EditShape)
                        .border(1.dp, WizardColors.text.copy(alpha = 0.40f), EditShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    inner()
                }
            },
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(role = Role.Button, onClickLabel = clickLabel) {
                    draft = value
                    hadFocus = false
                    editing = true
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            FittedSessionText(
                text = value.ifBlank { placeholder },
                style = textStyle,
                color = if (value.isBlank()) placeholderColor else textColor,
                maxLines = maxLines,
                shrinkToFit = isHeading,
                modifier = if (isHeading) Modifier.semantics { heading() } else Modifier,
            )
        }
    }
}

/**
 * Texto de la cabecera que no parte palabras. Si el nombre ([shrinkToFit]) no cabe en [maxLines], la letra baja hasta 24 sp
 * (KPKN_UI_V2 §5); si aun así no cabe, se corta en el último espacio y se pone «…».
 */
@Composable
private fun FittedSessionText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    shrinkToFit: Boolean,
    modifier: Modifier = Modifier,
) {
    val startSp = if (style.fontSize != TextUnit.Unspecified) style.fontSize.value else 16f
    var sizeSp by remember(text) { mutableFloatStateOf(startSp) }
    var cutAt by remember(text) { mutableIntStateOf(-1) }
    val shown = if (cutAt >= 0) text.substring(0, cutAt).trimEnd() + "…" else text
    val fitted = if (style.lineHeight != TextUnit.Unspecified && style.fontSize != TextUnit.Unspecified && style.fontSize.value > 0f) {
        style.copy(fontSize = sizeSp.sp, lineHeight = (style.lineHeight.value * sizeSp / style.fontSize.value).sp)
    } else {
        style.copy(fontSize = sizeSp.sp)
    }
    Text(
        text = shown,
        style = fitted,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result: TextLayoutResult ->
            if (cutAt < 0 && result.hasVisualOverflow) {
                if (shrinkToFit && sizeSp > MinNameSp) {
                    sizeSp = maxOf(MinNameSp, sizeSp - 2f)
                } else {
                    val end = result.getLineEnd(maxLines - 1, visibleEnd = true).coerceIn(0, text.length)
                    if (end > 0) {
                        val space = text.lastIndexOf(' ', startIndex = end - 1)
                        cutAt = if (space > 0) space else end
                    }
                }
            }
        },
        modifier = modifier,
    )
}
