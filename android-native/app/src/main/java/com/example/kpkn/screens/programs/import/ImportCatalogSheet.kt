package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate

/** Compact importer selection surface. Scores are shown only for the import matcher's scored suggestions. */
@Composable
internal fun ImportCatalogSheetContent(
    rawName: String,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onSelect: (ExerciseNameCandidate) -> Unit,
    onOpenFullCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable(rawName) { mutableStateOf(rawName) }
    var candidates by remember(rawName) { mutableStateOf<List<ExerciseNameCandidate>>(emptyList()) }
    var scoredIds by remember(rawName) { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember(rawName) { mutableStateOf(true) }

    LaunchedEffect(query) {
        loading = true
        try {
            val ranked = suggest(query)
            val listed = search(query)
            scoredIds = ranked.filter { it.confidence > 0.0 }.map { it.configurationId }.toSet()
            candidates = (ranked + listed).distinctBy { it.configurationId }.take(6)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            candidates = emptyList()
            scoredIds = emptySet()
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Elegir ejercicio",
            style = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            color = Color(0xFFF2EEE6),
        )
        Text(
            "«$rawName» · busca en el catálogo compartido",
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
            color = Color(0xFFC4BFB6),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Nombre, material o músculo", fontSize = 12.sp, color = Color(0xFF77736D)) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF8E8980), modifier = Modifier.size(18.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0B0B0B),
                unfocusedContainerColor = Color(0xFF0B0B0B),
                focusedTextColor = Color(0xFFF2EEE6),
                unfocusedTextColor = Color(0xFFF2EEE6),
                focusedBorderColor = Color(0xFF817566),
                unfocusedBorderColor = Color(0xFF2C2A27),
                cursorColor = Color(0xFFF7CF73),
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("import-catalog-search"),
        )

        if (loading) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF7CF73), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (candidates.isEmpty()) {
            Text(
                "No encontramos variantes para esta búsqueda. Puedes revisar o crear una en el catálogo completo.",
                style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = Color(0xFF8E8980),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFF111111)),
            ) {
                candidates.forEachIndexed { index, candidate ->
                    if (index > 0) Spacer(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color(0xFF282725)))
                    ImportCatalogCandidateRow(
                        candidate = candidate,
                        showConfidence = candidate.configurationId in scoredIds && candidate.confidence > 0.0,
                        onClick = { onSelect(candidate) },
                    )
                }
            }
        }

        ImportCatalogSheetAction(
            label = query.trim().takeIf { it.isNotBlank() }?.let { "Crear «$it» en el catálogo" } ?: "Buscar en el catálogo",
            outlined = true,
            onClick = onOpenFullCatalog,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClose)
                .testTag("import-catalog-cancel"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancelar", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC4BFB6))
        }
    }
}

@Composable
private fun ImportCatalogCandidateRow(
    candidate: ExerciseNameCandidate,
    showConfidence: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("import-catalog-choice-${candidate.configurationId}")
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF292929)),
            contentAlignment = Alignment.Center,
        ) {
            Text("↗", fontSize = 14.sp, color = Color(0xFFC4BFB6))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                candidate.displayName,
                style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Color(0xFFF2EEE6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val equipment = importEquipmentLabelOf(candidate.equipmentId)
            val detail = when {
                showConfidence -> listOfNotNull(equipment.takeIf { it.isNotBlank() }, "${(candidate.confidence * 100).toInt().coerceIn(0, 100)} % de coincidencia").joinToString(" · ")
                equipment.isNotBlank() -> "$equipment · Búsqueda del catálogo"
                else -> "Búsqueda del catálogo"
            }
            Text(detail, style = androidx.compose.ui.text.TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp), color = Color(0xFF8E8980), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 23.sp, color = Color(0xFFC4BFB6))
    }
}

@Composable
private fun ImportCatalogSheetAction(
    label: String,
    outlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (outlined) Modifier.border(1.dp, Color.White.copy(alpha = 0.13f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF2EEE6), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
