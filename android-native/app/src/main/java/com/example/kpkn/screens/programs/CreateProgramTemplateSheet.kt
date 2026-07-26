package com.example.kpkn.screens.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.ProgramTemplateOption

@OptIn(ExperimentalMaterial3Api::class)
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
            Text("Nuevo programa", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Elige una plantilla Simple o Avanzada, o crea un programa vacío.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Simple") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Avanzado") })
            }
            val templates = if (selectedTab == 0) simpleTemplates else advancedTemplates
            templates.forEach { template ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreateFromTemplate(template) },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${template.emoji} ${template.name}", fontWeight = FontWeight.Black)
                        Text(template.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${template.weeks} semanas · ${template.blockNames.size} bloque(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Button(onClick = onCreateBlank, modifier = Modifier.fillMaxWidth()) {
                Text("Programa vacío")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
