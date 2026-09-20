package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton

@Composable
fun CreateProgramTemplateSheet(
    onDismiss: () -> Unit,
    onCreateBlank: (() -> Unit)?,
    onCreateFromTemplate: (ProgramTemplateOption) -> Unit,
    onSelectProtocol: (Protocol) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val entries = remember {
        PersonalizedPlanCatalog.entries().filter {
            it.publication == PublicationState.PUBLISHED && it.source != CatalogSource.NATIVE
        }
    }
    val visible = entries.filter { entry ->
        val advanced = entry.template?.type == ProgramStructure.COMPLEX ||
            (entry.recipe?.distinctBlockCount ?: 1) > 1
        advanced == (selectedTab == 1)
    }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Planes personalizados", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                "Elige una estructura y revisa su método antes de aplicarla. Simple describe la estructura, no la dificultad.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Simple", "Avanzado").forEachIndexed { index, title ->
                    KpknSheetLightChip(
                        label = title,
                        selected = selectedTab == index,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = index },
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visible.forEach { entry ->
                    Surface(
                        onClick = {
                            entry.template?.let(onCreateFromTemplate)
                                ?: PROTOCOL_LIBRARY.firstOrNull { it.id == entry.sourceId }?.let(onSelectProtocol)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = KpknSheetTokens.Panel,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.title, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(entry.description, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(entry.technicalSubtitle, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            onCreateBlank?.let { create ->
                KpknSheetWhiteButton(text = "Crear desde cero", onClick = create)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White)
            }
        }
    }
}
