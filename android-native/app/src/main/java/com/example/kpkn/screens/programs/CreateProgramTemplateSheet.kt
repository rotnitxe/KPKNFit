package com.example.kpkn.screens.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.KpknSheetWhiteButton

@Composable
fun CreateProgramTemplateSheet(
    onDismiss: () -> Unit,
    onCreateBlank: () -> Unit,
    onCreateFromTemplate: (ProgramTemplateOption) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val simpleTemplates = remember { PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.SIMPLE } }
    val advancedTemplates = remember { PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX } }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nuevo programa", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                "Elige una plantilla Simple o Avanzada, o crea un programa vacío.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KpknSheetLightChip(
                    label = "SIMPLE",
                    selected = selectedTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 0 },
                )
                KpknSheetLightChip(
                    label = "AVANZADO",
                    selected = selectedTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = 1 },
                )
            }
            val templates = if (selectedTab == 0) simpleTemplates else advancedTemplates
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                templates.forEach { template ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCreateFromTemplate(template) },
                        shape = RoundedCornerShape(16.dp),
                        color = KpknSheetTokens.Panel,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${template.emoji} ${template.name}",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Text(
                                "${template.weeks} semanas · ${template.blockNames.size} bloque(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            KpknSheetWhiteButton(text = "Programa vacío", onClick = onCreateBlank)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
