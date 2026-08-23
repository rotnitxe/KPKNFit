package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.concepts.findConceptoClave
import com.example.kpkn.domain.concepts.projectConceptoClave
import com.example.kpkn.domain.concepts.searchConceptosClave
import com.example.kpkn.ui.components.CONCEPTS_LINK_COLOR
import com.example.kpkn.ui.theme.HomeCardSurfaceAlt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptosClaveScreen(
    expandedConceptId: String? = null,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val concepts = remember(query) { searchConceptosClave(query) }
    val initialExpandedId = remember(expandedConceptId) {
        expandedConceptId?.trim()?.takeIf { findConceptoClave(it) != null }
    }
    var expandedId by remember(initialExpandedId) { mutableStateOf(initialExpandedId) }
    val listState = rememberLazyListState()

    LaunchedEffect(concepts, expandedId) {
        if (expandedId != null && concepts.none { it.id == expandedId }) {
            expandedId = null
        }
    }

    LaunchedEffect(initialExpandedId, concepts) {
        val targetIndex = initialExpandedId
            ?.let { id -> concepts.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        if (targetIndex != null) {
            // The search field occupies the first item in the list.
            listState.scrollToItem(targetIndex + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Conceptos Clave", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Aprende lo que cambia tu entrenamiento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "concept-search") {
                ConceptSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                )
            }
            itemsIndexed(concepts, key = { _, it -> it.id }) { index, concept ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index == 0 || concepts[index - 1].category != concept.category) {
                        Text(
                            text = concept.category.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                        )
                    }
                    ConceptoClaveAccordion(
                        concept = projectConceptoClave(concept),
                        expanded = expandedId == concept.id,
                        onToggle = {
                            expandedId = if (expandedId == concept.id) null else concept.id
                        },
                    )
                }
            }
            if (concepts.isEmpty()) {
                item(key = "concept-empty") {
                    Text(
                        text = "No hay conceptos que coincidan.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConceptSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("concept_search_field"),
        shape = RoundedCornerShape(18.dp),
        color = HomeCardSurfaceAlt,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CONCEPTS_LINK_COLOR,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 15.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(CONCEPTS_LINK_COLOR),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text(
                            text = "Buscar conceptos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar búsqueda",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
