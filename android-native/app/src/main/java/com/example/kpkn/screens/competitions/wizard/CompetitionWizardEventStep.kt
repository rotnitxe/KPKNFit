package com.example.kpkn.screens.competitions.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.domain.competitions.PowerliftingPointsFormula
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompetitionWizardEventStep(
    record: CompetitionRecord,
    viewModel: CompetitionWizardViewModel,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var pickingFed by remember { mutableStateOf(false) }
    val female = record.powerliftingDetails?.sexCategory.orEmpty().contains("fem", ignoreCase = true)
    val selectedFed = PowerliftingFederationCatalog.byId(record.federationId)
    val customSelected = record.federationId == PowerliftingFederationCatalog.CUSTOM_ID
    val parsedDate = record.eventDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    if (pickingFed) {
        FederationPicker(
            selectedId = record.federationId,
            customName = record.federation,
            onPick = { id, custom ->
                viewModel.setFederation(id, custom)
                pickingFed = false
            },
            onCustomName = viewModel::setCustomFederationName,
            onClose = { pickingFed = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WizardPanel(modifier = Modifier.heightIn(min = 108.dp), onClick = { showDate = true }) {
            if (parsedDate == null) {
                Column {
                    Text("Fecha", color = WizardMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Elegir en el calendario", color = WizardInk, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        parsedDate.dayOfMonth.toString(),
                        color = WizardInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 48.sp,
                    )
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            parsedDate.format(DateTimeFormatter.ofPattern("MMMM", Locale("es"))),
                            color = WizardInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(parsedDate.year.toString(), color = WizardMuted, fontSize = 14.sp)
                    }
                }
            }
        }

        WizardPillField(
            value = record.location.orEmpty(),
            onValueChange = viewModel::setVenue,
            placeholder = "Ciudad o recinto",
        )
        WizardPillField(
            value = record.title,
            onValueChange = viewModel::setMeetName,
            placeholder = "Nombre del meet (opcional)",
        )

        WizardPanel(onClick = { pickingFed = true }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FederationMark(
                    federation = selectedFed,
                    selected = selectedFed != null || customSelected,
                    size = 56.dp,
                    customLabel = if (customSelected) "P" else null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Federación", color = WizardMuted, fontSize = 12.sp)
                    Text(
                        when {
                            selectedFed != null -> selectedFed.shortName
                            customSelected -> record.federation?.ifBlank { "Personalizada" } ?: "Personalizada"
                            else -> "Elegir"
                        },
                        color = WizardInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    val formula = selectedFed?.pointsFormula
                        ?: if (customSelected) PowerliftingPointsFormula.DOTS.id else null
                    if (formula != null) {
                        Text(
                            PowerliftingPointsFormula.fromId(formula)?.label ?: formula,
                            color = WizardMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Text("Pesaje", color = WizardMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        WizardStepper(
            valueLabel = record.bodyweightKg?.let { String.format("%.1f kg", it) } ?: "Peso",
            onMinus = { viewModel.setBodyweight((record.bodyweightKg ?: 80.0) - 0.1) },
            onPlus = { viewModel.setBodyweight((record.bodyweightKg ?: 80.0) + 0.1) },
        )

        Text("Clase", color = WizardMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weightClasses(female).forEach { klass ->
                WizardChip(klass, record.powerliftingDetails?.weightClass == klass) {
                    viewModel.setWeightClass(klass)
                }
            }
        }

        Text("División y equipo", color = WizardMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Open", "Junior", "Sub-junior", "Master 1", "Master 2", "Master 3").forEach { division ->
                WizardChip(division, record.powerliftingDetails?.division == division) {
                    viewModel.setDivision(division)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardChip("Classic", record.powerliftingDetails?.equipment == CompetitionEquipment.RAW) {
                viewModel.setEquipment(CompetitionEquipment.RAW)
            }
            WizardChip("Wraps", record.powerliftingDetails?.equipment == CompetitionEquipment.WRAPS) {
                viewModel.setEquipment(CompetitionEquipment.WRAPS)
            }
            WizardChip("Equipped", record.powerliftingDetails?.equipment == CompetitionEquipment.EQUIPPED) {
                viewModel.setEquipment(CompetitionEquipment.EQUIPPED)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDate) {
        val initial = parsedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        val pickerScheme = MaterialTheme.colorScheme.copy(
            primary = Color.White,
            onPrimary = Color.Black,
            surface = Color(0xFF1A1A1F),
            onSurface = Color.White,
            surfaceContainerHigh = Color(0xFF24242A),
        )
        MaterialTheme(colorScheme = pickerScheme) {
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                viewModel.setDate(date.toString())
                            }
                            showDate = false
                        },
                    ) { Text("Listo", color = WizardInk) }
                },
                dismissButton = {
                    TextButton(onClick = { showDate = false }) { Text("Cancelar", color = WizardMuted) }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FederationPicker(
    selectedId: String?,
    customName: String?,
    onPick: (String?, String?) -> Unit,
    onCustomName: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var continent by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val federations = remember(query, continent) {
        PowerliftingFederationCatalog.search(query, continent)
    }
    val customSelected = selectedId == PowerliftingFederationCatalog.CUSTOM_ID

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Federación", color = WizardInk, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Listo", color = WizardInk, fontWeight = FontWeight.Bold) }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WizardChip("Todas", continent == null) { continent = null }
            PowerliftingFederationCatalog.continents.forEach { (id, label) ->
                WizardChip(label, continent == id) { continent = id }
            }
        }
        WizardPillField(value = query, onValueChange = { query = it }, placeholder = "Buscar")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            federations.forEach { fed ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onPick(fed.id, null) },
                ) {
                    FederationMark(federation = fed, selected = selectedId == fed.id, size = 56.dp)
                    Text(fed.shortName, color = WizardMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onPick(PowerliftingFederationCatalog.CUSTOM_ID, customName) },
            ) {
                FederationMark(federation = null, selected = customSelected, size = 56.dp, customLabel = "P")
                Text("Otra", color = WizardMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (customSelected) {
            WizardPillField(
                value = customName.orEmpty(),
                onValueChange = onCustomName,
                placeholder = "Nombre de la federación",
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun weightClasses(female: Boolean): List<String> =
    if (female) listOf("47", "52", "57", "63", "69", "76", "84", "84+")
    else listOf("59", "66", "74", "83", "93", "105", "120", "120+")
