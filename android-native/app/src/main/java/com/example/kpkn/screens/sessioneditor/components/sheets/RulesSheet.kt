package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import java.util.Locale
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.DarkEditorSurface
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun RestTimeField(
label: String,
seconds: Int,
modifier: Modifier = Modifier,
onClick: () -> Unit
) {
val minutes = seconds / 60
val secs = seconds % 60
val displayValue = String.format(java.util.Locale.US, "%d:%02d", minutes, secs)

Box(
modifier = modifier
.clickable { onClick() }
) {
OutlinedTextField(
value = displayValue,
onValueChange = {},
readOnly = true,
label = { Text(label) },
singleLine = true,
modifier = Modifier.fillMaxWidth(),
enabled = false,
shape = RoundedCornerShape(14.dp),
textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
colors = OutlinedTextFieldDefaults.colors(
disabledTextColor = Color.White,
disabledBorderColor = Color.White.copy(alpha = 0.35f),
disabledLabelColor = Color.White.copy(alpha = 0.5f)
)
)
Box(
modifier = Modifier
.matchParentSize()
.background(Color.Transparent)
.clickable { onClick() }
)
}
}

@Composable
internal fun RestTimePickerDialog(
title: String,
initialSeconds: Int,
onConfirm: (Int) -> Unit,
onDismiss: () -> Unit
) {
var minInput by remember { mutableStateOf((initialSeconds / 60).toString()) }
var secInput by remember { mutableStateOf((initialSeconds % 60).toString()) }

AlertDialog(
onDismissRequest = onDismiss,
title = { Text(title, fontWeight = FontWeight.Bold) },
text = {
Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
Text("Ingresa los minutos y segundos para el descanso.", style = MaterialTheme.typography.bodyMedium)
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.spacedBy(16.dp)
) {
OutlinedTextField(
value = minInput,
onValueChange = { minInput = it.filter { char -> char.isDigit() }.take(2) },
label = { Text("Minutos") },
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.weight(1f),
singleLine = true,
shape = RoundedCornerShape(12.dp)
)
OutlinedTextField(
value = secInput,
onValueChange = { secInput = it.filter { char -> char.isDigit() }.take(2) },
label = { Text("Segundos") },
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.weight(1f),
singleLine = true,
shape = RoundedCornerShape(12.dp)
)
}
}
},
confirmButton = {
TextButton(
onClick = {
val m = minInput.toIntOrNull() ?: 0
val s = secInput.toIntOrNull() ?: 0
onConfirm(m * 60 + s)
}
) {
Text("Aceptar", fontWeight = FontWeight.Bold)
}
},
dismissButton = {
TextButton(onClick = onDismiss) {
Text("Cancelar")
}
}
)
}

@Composable
internal fun RulesSheet(
uiState: SessionEditorUiState,
onApplyRules: (String?) -> Unit,
onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
onRuleLimitsChange: (Double?, Int?) -> Unit,
onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
setTargetDuration: (Int?) -> Unit,
setPartTargetDuration: (String, Int?) -> Unit,
setExerciseTargetDuration: (String, Int?) -> Unit,
onSave: () -> Unit = {},
onDismiss: () -> Unit = {},
) {
var activeTab by remember { mutableIntStateOf(0) }
var scopePartId by remember { mutableStateOf<String?>(null) }

val defaults = remember(scopePartId, uiState.ruleDefaults, uiState.partRuleDefaults) {
if (scopePartId == null) uiState.ruleDefaults
else (uiState.partRuleDefaults[scopePartId] ?: uiState.ruleDefaults)
}

var activeRestDialog by remember { mutableStateOf<String?>(null) }

if (activeRestDialog != null) {
val (title, currentSecs, onConfirmCallback) = when (activeRestDialog) {
"normal" -> Triple(
"Descanso de series",
defaults.normalRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, secs, null, null, null, null, null) }
)
"sides" -> Triple(
"Descanso entre lados",
defaults.betweenSidesRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, secs, null, null, null, null) }
)
"between" -> Triple(
"Descanso entre ejercicios",
defaults.supersetBetweenRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, secs, null, null, null) }
)
"round" -> Triple(
"Descanso de rondas",
defaults.supersetRoundRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, secs, null, null) }
)
else -> Triple("", 0, { _: Int -> })
}
RestTimePickerDialog(
title = title,
initialSeconds = currentSecs,
onConfirm = {
onConfirmCallback(it)
activeRestDialog = null
},
onDismiss = { activeRestDialog = null }
)
}

Column(
Modifier
.fillMaxWidth()
.verticalScroll(rememberScrollState())
.imePadding()
.padding(horizontal = 18.dp, vertical = 14.dp),
verticalArrangement = Arrangement.spacedBy(14.dp),
) {
SheetHeader(title = "Reglas y tiempo", subtitle = "Configura límites de tiempo y reglas base de la sesión.")

TabRow(
selectedTabIndex = activeTab,
containerColor = Color.Transparent,
contentColor = MaterialTheme.colorScheme.primary
) {
Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
Text("Reglas", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}
Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
Text("Límites de tiempo", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}
}

if (activeTab == 0) {
// Scope Selector UI (for different rules per category/session)
Text("Configurar reglas por grupo:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
Row(
horizontalArrangement = Arrangement.spacedBy(8.dp),
modifier = Modifier.horizontalScroll(rememberScrollState())
) {
DarkChoiceChip("Toda la sesión", scopePartId == null) { scopePartId = null }
uiState.session?.parts?.forEach { part ->
DarkChoiceChip(part.name, scopePartId == part.id) { scopePartId = part.id }
}
}
Spacer(Modifier.height(2.dp))

Surface(
shape = RoundedCornerShape(18.dp),
color = DarkEditorSurface,
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
Text("Valores de serie", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)

Row(
horizontalArrangement = Arrangement.spacedBy(12.dp),
modifier = Modifier.fillMaxWidth(),
verticalAlignment = Alignment.CenterVertically
) {
Text("Intensidad:", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
listOf(DefaultIntensityType.RPE, DefaultIntensityType.RIR, DefaultIntensityType.FALLO).forEach { type ->
val selected = defaults.intensityType == type
val label = when (type) {
DefaultIntensityType.RPE -> "RPE"
DefaultIntensityType.RIR -> "RIR"
DefaultIntensityType.FALLO -> "Fallo"
}
Box(
modifier = Modifier
.clip(RoundedCornerShape(8.dp))
.background(if (selected) MaterialTheme.colorScheme.primary else DarkEditorChip)
.clickable { onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, null, type) }
.padding(horizontal = 12.dp, vertical = 6.dp)
) {
Text(
text = label,
color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
fontWeight = FontWeight.Bold,
style = MaterialTheme.typography.bodySmall
)
}
}
}
}

Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
EditorMiniField("Series", defaults.setCount.toString(), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, it.safeIntOrNull(), null, null, null, null, null, null, null, null)
}
EditorMiniField("Reps", defaults.reps.toString(), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, null, it.safeIntOrNull(), null, null, null, null, null, null, null)
}
if (defaults.intensityType != DefaultIntensityType.FALLO) {
val label = if (defaults.intensityType == DefaultIntensityType.RPE) "RPE" else "RIR"
EditorMiniField(label, formatEditableNumber(defaults.rpe), keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, null, null, it.safeDoubleOrNull(), null, null, null, null, null, null)
}
}
}

Text("Descansos (Min:Seg)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
RestTimeField("Normal", defaults.normalRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "normal"
}
RestTimeField("Lados", defaults.betweenSidesRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "sides"
}
}
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
RestTimeField("Entre ej.", defaults.supersetBetweenRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "between"
}
RestTimeField("Rondas", defaults.supersetRoundRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "round"
}
}

Row(
modifier = Modifier
.fillMaxWidth()
.clip(RoundedCornerShape(14.dp))
.background(DarkEditorChip)
.padding(horizontal = 12.dp, vertical = 8.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
Column(Modifier.weight(1f)) {
Text("Aplicar a nuevos elementos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
Text("Ejercicios, series, lados y supersets nuevos heredan estos valores.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
Switch(
checked = defaults.applyToNewItems,
onCheckedChange = { onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null) },
)
}
}
}

FilledTonalButton(onClick = { onApplyRules(scopePartId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
Text("Aplicar", fontWeight = FontWeight.Black)
}
} else {
// Tab 2: Límites de tiempo
val session = uiState.session
if (session != null) {
var timeInput by remember(session.targetDurationMinutes) {
val m = session.targetDurationMinutes ?: 0
mutableStateOf("%02d:%02d:%02d".format(m / 60, m % 60, 0))
}

fun updateGlobalFromText(raw: String) {
timeInput = raw
}

fun applyGlobalTimeBudget() {
val parts = timeInput.split(":").map { it.toIntOrNull() ?: 0 }
val hh = parts.getOrElse(0) { 0 }
val mm = parts.getOrElse(1) { 0 }
val ss = parts.getOrElse(2) { 0 }
val totalSecs = hh * 3600 + mm * 60 + ss
val totalMin = if (totalSecs == 0) null else totalSecs / 60
setTargetDuration(totalMin)
}

Surface(
shape = RoundedCornerShape(18.dp),
color = DarkEditorSurface,
modifier = Modifier.fillMaxWidth()
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Text("Límite de tiempo global (guía)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
Text(
"Establece un límite de tiempo objetivo para toda la sesión. Sirve de referencia de ritmo durante el entrenamiento.",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.onSurfaceVariant
)
OutlinedTextField(
value = timeInput,
onValueChange = { updateGlobalFromText(it) },
label = { Text("HH:MM:SS") },
placeholder = { Text("01:30:00", color = Color.White.copy(alpha = 0.4f)) },
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.fillMaxWidth(),
textStyle = MaterialTheme.typography.bodyMedium.copy(
fontFamily = FontFamily.Monospace,
textAlign = TextAlign.Center,
color = Color.White
),
colors = OutlinedTextFieldDefaults.colors(
focusedTextColor = Color.White,
unfocusedTextColor = Color.White,
focusedBorderColor = MaterialTheme.colorScheme.primary,
unfocusedBorderColor = Color.Gray,
cursorColor = Color.White,
)
)
val partsSum = session.parts.sumOf { it.targetDurationMinutes ?: 0 } +
session.exercises.sumOf { it.targetDurationMinutes ?: 0 }
val sessionBudget = session.targetDurationMinutes ?: 0
if (sessionBudget > 0) {
val isOverBudget = partsSum > sessionBudget
val remaining = sessionBudget - partsSum
Text(
text = if (isOverBudget) {
"⚠️ Excede el presupuesto global por ${partsSum - sessionBudget} min ($partsSum min asignados)"
} else {
"⏱️ $partsSum de $sessionBudget min asignados (${if (remaining >= 0) "$remaining min disponibles" else ""})"
},
style = MaterialTheme.typography.bodySmall,
color = if (isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
fontWeight = FontWeight.Bold,
modifier = Modifier.padding(top = 4.dp)
)
}
}
}

Text("Tiempos por Grupos y Ejercicios", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
Text(
"Define presupuestos específicos (en minutos) en función del global, por ejemplo, para ejercicios que demandan mucho tiempo de setup.",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.onSurfaceVariant
)

// Render parts (categories) and exercises inside them
session.parts.forEach { part ->
var partMinutesInput by remember(part.targetDurationMinutes) {
mutableStateOf(part.targetDurationMinutes?.toString() ?: "")
}
Card(
colors = CardDefaults.cardColors(containerColor = DarkEditorSurface),
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth()
) {
Row(
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.weight(1f)
) {
Text("📁", fontSize = 16.sp)
Spacer(Modifier.width(6.dp))
Text(
part.name,
fontWeight = FontWeight.Bold,
style = MaterialTheme.typography.bodyMedium,
color = Color.White
)
}
Box(Modifier.width(90.dp).clipToBounds()) {
BasicTextField(
value = partMinutesInput,
onValueChange = {
partMinutesInput = it
setPartTargetDuration(part.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
.padding(horizontal = 8.dp, vertical = 4.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (partMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (partMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodyMedium, color = Color.White)
}
}
}
)
}
}

val exercisesSum = part.exercises.sumOf { it.targetDurationMinutes ?: 0 }
val partBudget = part.targetDurationMinutes ?: 0
if (partBudget > 0) {
val isOverBudget = exercisesSum > partBudget
val remaining = partBudget - exercisesSum
Text(
text = if (isOverBudget) {
"⚠️ Excede el presupuesto del grupo por ${exercisesSum - partBudget} min ($exercisesSum min asignados)"
} else {
"⏱️ $exercisesSum de $partBudget min asignados (${if (remaining >= 0) "$remaining min disponibles" else ""})"
},
style = MaterialTheme.typography.bodySmall,
color = if (isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
fontWeight = FontWeight.Bold,
modifier = Modifier.padding(top = 2.dp)
)
}

if (part.exercises.isNotEmpty()) {
HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
part.exercises.forEach { ex ->
var exMinutesInput by remember(ex.targetDurationMinutes) {
mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
}
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
) {
Text(
ex.name,
style = MaterialTheme.typography.bodySmall,
color = Color.White.copy(alpha = 0.8f),
maxLines = 1,
overflow = TextOverflow.Ellipsis,
modifier = Modifier.weight(1f)
)
Spacer(Modifier.width(8.dp))
Box(Modifier.width(80.dp).clipToBounds()) {
BasicTextField(
value = exMinutesInput,
onValueChange = {
exMinutesInput = it
setExerciseTargetDuration(ex.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
.padding(horizontal = 6.dp, vertical = 2.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (exMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (exMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodySmall, color = Color.White)
}
}
}
)
}
}
}
}
}
}
}

// Render loose exercises (no part)
if (session.exercises.isNotEmpty()) {
Card(
colors = CardDefaults.cardColors(containerColor = DarkEditorSurface),
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Text("Otros Ejercicios", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
session.exercises.forEach { ex ->
var exMinutesInput by remember(ex.targetDurationMinutes) {
mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
}
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth()
) {
Text(
ex.name,
style = MaterialTheme.typography.bodySmall,
color = Color.White.copy(alpha = 0.8f),
maxLines = 1,
overflow = TextOverflow.Ellipsis,
modifier = Modifier.weight(1f)
)
Spacer(Modifier.width(8.dp))
Box(Modifier.width(80.dp).clipToBounds()) {
BasicTextField(
value = exMinutesInput,
onValueChange = {
exMinutesInput = it
setExerciseTargetDuration(ex.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
.padding(horizontal = 6.dp, vertical = 2.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (exMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (exMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodySmall, color = Color.White)
}
}
}
)
}
}
}
}
}
}
Spacer(Modifier.height(8.dp))
FilledTonalButton(
onClick = {
applyGlobalTimeBudget()
onSave()
onDismiss()
},
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
) {
Text("Guardar cambios", fontWeight = FontWeight.Black)
}
}
}
}
}
