package com.example.kpkn.screens.sessioneditor

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.kpkn.data.models.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.lerp
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheetTokens

@Composable
internal fun EditorMiniField(
    label: String,
    value: String,
    stateKey: String = label,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var localValue by rememberSaveable(stateKey) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(stateKey, value, isFocused) {
        if (!isFocused && value != localValue) {
            localValue = value
        }
    }
    OutlinedTextField(
        value = localValue,
        onValueChange = {
            localValue = it
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (localValue != value) onCommit(localValue)
        }),
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (wasFocused && !focusState.isFocused && localValue != value) {
                onCommit(localValue)
            }
        },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.92f),
        ),
        colors = kpknEditorFieldColors(accentColor),
    )
}

@Composable
internal fun kpknEditorFieldColors(accentColor: Color? = null) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = accentColor?.copy(alpha = 0.20f) ?: Color.White.copy(alpha = 0.16f),
    unfocusedContainerColor = accentColor?.copy(alpha = 0.12f) ?: Color.White.copy(alpha = 0.12f),
    disabledContainerColor = Color.White.copy(alpha = 0.07f),
    focusedTextColor = Color.White.copy(alpha = 0.96f),
    unfocusedTextColor = Color.White.copy(alpha = 0.92f),
    disabledTextColor = Color.White.copy(alpha = 0.45f),
    focusedLabelColor = Color.White.copy(alpha = 0.78f),
    unfocusedLabelColor = Color.White.copy(alpha = 0.62f),
    disabledLabelColor = Color.White.copy(alpha = 0.35f),
    cursorColor = accentColor ?: Color.White.copy(alpha = 0.9f),
    focusedBorderColor = accentColor?.copy(alpha = 0.82f) ?: Color.White.copy(alpha = 0.30f),
    unfocusedBorderColor = accentColor?.copy(alpha = 0.42f) ?: Color.White.copy(alpha = 0.10f),
    disabledBorderColor = Color.Transparent,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
)

@Composable
internal fun DurationPickerField(
    label: String,
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = formatRestSummary(totalSeconds),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = accentColor) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF3A3A42),
                unfocusedContainerColor = Color(0xFF2E2E35),
                disabledContainerColor = Color(0xFF27272D),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedLabelColor = accentColor,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = accentColor,
            ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        DurationPickerDialog(
            initialTotalSeconds = totalSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = {
                onConfirm(it)
                showPicker = false
            },
        )
    }
}

@Composable
internal fun DurationPickerDialog(
    initialTotalSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by rememberSaveable(initialTotalSeconds) { mutableStateOf((initialTotalSeconds / 60).coerceIn(0, 59)) }
    var seconds by rememberSaveable(initialTotalSeconds) { mutableStateOf((initialTotalSeconds % 60).coerceIn(0, 59)) }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir descanso", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Ajusta el descanso con un selector visual nativo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NativeWheelPicker(
                        label = "Min",
                        value = minutes,
                        range = 0..59,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { minutes = it }
                    NativeWheelPicker(
                        label = "Seg",
                        value = seconds,
                        range = 0..59,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { seconds = it }
                }
                Text(
                    "Descanso seleccionado: ${minutes}:${seconds.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(minutes * 60 + seconds) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun NativeWheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accentColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = range.first
                        maxValue = range.last
                        wrapSelectorWheel = true
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFormatter { it.toString().padStart(2, '0') }
                        setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
                    }
                },
                update = { picker ->
                    if (picker.minValue != range.first) picker.minValue = range.first
                    if (picker.maxValue != range.last) picker.maxValue = range.last
                    if (picker.value != value) picker.value = value
                },
            )
        }
    }
}

@Composable
internal fun ToggleToken(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    )
}

@Composable
internal fun ExerciseFactChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Text(
            text = "$label · $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
internal fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = KpknSheetTokens.GlassControlLabelMuted)
        },
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = KpknSheetTokens.GlassControlPlaceholder,
            )
        },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.GlassControlLabel),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = KpknSheetTokens.GlassControlFillStrong,
            unfocusedContainerColor = KpknSheetTokens.GlassControlFill,
            disabledContainerColor = KpknSheetTokens.GlassControlFill,
            focusedTextColor = KpknSheetTokens.GlassControlLabel,
            unfocusedTextColor = KpknSheetTokens.GlassControlLabel,
            disabledTextColor = KpknSheetTokens.GlassControlLabelMuted,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = KpknSheetTokens.GlassControlLabel,
            focusedLeadingIconColor = KpknSheetTokens.GlassControlLabel,
            unfocusedLeadingIconColor = KpknSheetTokens.GlassControlLabelMuted,
        ),
    )
}

@Composable
internal fun CompactCatalogFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    glassDark: Boolean = false,
) {
    val idle = if (glassDark) Color.White.copy(alpha = 0.08f) else KpknSheetTokens.ChipIdle
    val selectedBg = if (glassDark) Color.White.copy(alpha = 0.16f) else KpknSheetTokens.ChipSelected
    val labelIdle = if (glassDark) Color.White.copy(alpha = 0.72f) else KpknSheetTokens.ChipLabel
    val labelSelected = if (glassDark) Color.White.copy(alpha = 0.92f) else KpknSheetTokens.ChipLabel
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(28.dp),
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        border = null,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = idle,
            selectedContainerColor = selectedBg,
            labelColor = labelIdle,
            selectedLabelColor = labelSelected,
            selectedLeadingIconColor = labelSelected,
            iconColor = labelIdle,
        ),
    )
}

@Composable
internal fun EditorSectionCard(
    title: String,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = accentColor?.let { lerp(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), it, 0.12f) }
            ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                content()
            },
        )
    }
}

