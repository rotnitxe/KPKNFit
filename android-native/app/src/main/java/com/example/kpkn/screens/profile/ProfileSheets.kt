package com.example.kpkn.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.Settings
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.LiveSheet
import com.example.kpkn.screens.workout.components.LiveSheetPrimary
import com.example.kpkn.screens.workout.components.LiveSheetTextAction
import com.example.kpkn.ui.components.icons.KpknLineIcons
import androidx.compose.foundation.text.BasicTextField

/** Hoja «Foto de perfil» (con foto ya elegida): cambiarla desde la galería o quitarla. */
@Composable
internal fun ProfilePhotoSheet(onChoose: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    LiveSheet(title = "Foto de perfil", onDismiss = onDismiss, tag = "profile-photo-sheet", footer = null) { close ->
        ProfileSheetRow(KpknLineIcons.Camera, "Cambiar foto", "Elige una imagen de tu galería", onClick = { close(); onChoose() })
        ProfileSheetRow(KpknLineIcons.Trash, "Quitar foto", "Vuelven a verse tus iniciales", divider = true, danger = true,
            onClick = { onRemove(); close() })
    }
}

@Composable
private fun ProfileSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    divider: Boolean = false,
    danger: Boolean = false,
) {
    val color = if (danger) ProfileTokens.Danger else ProfileTokens.Ink
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (divider) drawLine(ProfileTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ProfileIconBubble(icon, tint = color)
        Column(Modifier.weight(1f)) {
            Text(title, style = ProfileType.rowTitle, color = color)
            Text(subtitle, Modifier.padding(top = 2.dp), style = ProfileType.rowSub, color = ProfileTokens.Muted)
        }
    }
}

private enum class ProfileField { NAME, HEIGHT, WEIGHT }

/** Qué campo señala cada mensaje de `ProfileViewModel.saveProfile`. */
private fun fieldOf(error: String?): ProfileField? = when {
    error == null -> null
    error.contains("nombre", ignoreCase = true) -> ProfileField.NAME
    error.contains("estatura", ignoreCase = true) -> ProfileField.HEIGHT
    else -> ProfileField.WEIGHT
}

/**
 * Hoja «Editar perfil»: nombre, sexo (cuatro opciones a la vista), estatura en cm y peso en la unidad de Ajustes. «Guardar» llama a
 * [onSave] (que devuelve el error o null); el error se muestra bajo su campo. ✕ y «Cancelar» descartan el borrador.
 */
@Composable
internal fun ProfileEditSheet(
    settings: Settings,
    onSave: (name: String, gender: Gender?, height: String, weight: String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(profileDisplayName(settings).orEmpty()) }
    var gender by rememberSaveable { mutableStateOf(settings.userVitals.gender) }
    var height by rememberSaveable { mutableStateOf(settings.userVitals.height?.formatInput().orEmpty()) }
    var weight by rememberSaveable {
        mutableStateOf(settings.userVitals.weight?.let { displayWeight(it, settings.weightUnit).formatInput() }.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }
    val errorField = fieldOf(error)

    LiveSheet(
        title = "Editar perfil",
        onDismiss = onDismiss,
        tag = "profile-edit-sheet",
        footer = { close ->
            LiveSheetPrimary("Guardar", onClick = {
                error = onSave(name, gender, height, weight)
                if (error == null) close()
            }, tag = "profile-edit-save")
            LiveSheetTextAction("Cancelar", onClick = close)
        },
    ) {
        ProfileFormField("Nombre", name, { name = it; error = null }, KeyboardType.Text, "Tu nombre", "profile-edit-name",
            isError = errorField == ProfileField.NAME)
        if (errorField == ProfileField.NAME) ProfileFieldError(error.orEmpty())

        ProfileFieldLabel("Sexo", Modifier.padding(top = 16.dp))
        val options: List<Pair<Gender?, String>> = listOf(null to "No especificar") + Gender.entries.map { it to it.label() }
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (value, label) ->
                    ProfileChoice(label, gender == value, { gender = value }, Modifier.weight(1f))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                ProfileFormField("Estatura", height, { height = it; error = null }, KeyboardType.Decimal, "—", "profile-edit-height",
                    unit = "cm", isError = errorField == ProfileField.HEIGHT)
            }
            Column(Modifier.weight(1f)) {
                ProfileFormField("Peso", weight, { weight = it; error = null }, KeyboardType.Decimal, "—", "profile-edit-weight",
                    unit = settings.weightUnit.label(), isError = errorField == ProfileField.WEIGHT)
            }
        }
        if (errorField == ProfileField.HEIGHT || errorField == ProfileField.WEIGHT) ProfileFieldError(error.orEmpty())
        Text(
            "La unidad de peso se cambia en Ajustes. Estatura y peso pueden quedar vacíos.",
            Modifier.padding(top = 12.dp), style = ProfileType.rowSub.copy(fontSize = 12.sp), color = ProfileTokens.Faint,
        )
    }
}

@Composable
private fun ProfileFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 2.dp, bottom = 7.dp), style = ProfileType.action.copy(fontSize = 12.sp), color = ProfileTokens.Muted)
}

/** Campo de 52 dp con etiqueta encima y unidad opcional a la derecha, como `.field` del prototipo. */
@Composable
private fun ProfileFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    placeholder: String,
    tag: String,
    unit: String? = null,
    isError: Boolean = false,
) {
    ProfileFieldLabel(label, Modifier.padding(top = 4.dp))
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = 16.sp, color = ProfileTokens.Ink),
        cursorBrush = SolidColor(ProfileTokens.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.testTag(tag),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape).background(Color(0x0DFFFFFF))
                    .border(1.dp, if (isError) ProfileTokens.Danger.copy(alpha = 0.55f) else Color(0x1AFFFFFF), shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 16.sp), color = Color(0xFF85817B))
                    inner()
                }
                unit?.let { Text(it, Modifier.padding(start = 8.dp), style = ProfileType.rowSub, color = ProfileTokens.Faint) }
            }
        },
    )
}

@Composable
private fun ProfileFieldError(message: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("profile-edit-error"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(KpknLineIcons.Alert, null, Modifier.size(16.dp), tint = ProfileTokens.Danger)
        Text(message, style = ProfileType.rowSub, color = ProfileTokens.Danger)
    }
}

@Composable
private fun ProfileChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (selected) ProfileTokens.Ink.copy(alpha = 0.08f) else Color(0x0AFFFFFF))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) ProfileTokens.Ink else Color(0x14FFFFFF), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ProfileType.action, color = if (selected) ProfileTokens.Ink else ProfileTokens.Muted, textAlign = TextAlign.Center)
    }
}
