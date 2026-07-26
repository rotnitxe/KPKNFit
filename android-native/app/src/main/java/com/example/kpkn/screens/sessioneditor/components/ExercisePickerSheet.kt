package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.data.repository.CustomExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.VariantFlowSheet
import com.example.kpkn.screens.sessioneditor.VariantFlowResultCache
import com.example.kpkn.screens.sessioneditor.getMuscleEmphasisEducationalText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.ui.components.KpknDropdownMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    query: String,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    selectedExercisesIds: Set<String> = emptySet(),
    onToggleExerciseSelection: (String) -> Unit = {},
    onClearExerciseSelection: () -> Unit = {},
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenExerciseCreator: () -> Unit,
    onDismiss: () -> Unit,
    highlightedExerciseId: String? = null,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit = {},
) {
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val fullCatalog = remember(catalog, customExercises) {
        (customExercises + catalog)
            .distinctBy { it.id.lowercase() }
    }
    var selectedRegion by rememberSaveable { mutableStateOf<ExerciseCatalogRegion?>(null) }
    var selectedTrait by rememberSaveable { mutableStateOf<ExerciseCatalogTrait?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.RELEVANCE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var infoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeadName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRegionMenu by remember { mutableStateOf(false) }
    var showMuscleMenu by remember { mutableStateOf(false) }
    var showHeadMenu by remember { mutableStateOf(false) }

    var showEmphasisCard by remember { mutableStateOf(true) }

    LaunchedEffect(selectedMuscle) {
        selectedHeadName = null
        showEmphasisCard = true
    }
    LaunchedEffect(selectedHeadName) {
        showEmphasisCard = true
    }

    var selectionOrder by rememberSaveable { mutableStateOf(listOf<String>()) }
    LaunchedEffect(selectedExercisesIds) {
        selectionOrder = selectionOrder.filter { it in selectedExercisesIds } +
            selectedExercisesIds.filterNot { it in selectionOrder }
    }
    val selectedExercises = remember(selectedExercisesIds, fullCatalog, selectionOrder) {
        val byId = fullCatalog.associateBy { it.id }
        selectionOrder.mapNotNull(byId::get).filter { it.id in selectedExercisesIds }
    }

    val normalizedQuery = query.trim()
    val activeRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
    val showGroupBrowser = false

    val exercisesByRegion = remember(fullCatalog) {
        fullCatalog.groupBy { resolveExerciseRegion(it) }
    }
    val exercisesByMuscle = remember(fullCatalog) {
        val map = mutableMapOf<String, MutableList<ExerciseMuscleInfo>>()
        fullCatalog.forEach { ex ->
            ex.involvedMuscles.forEach { m ->
                map.getOrPut(m.muscle.lowercase()) { mutableListOf() }.add(ex)
            }
        }
        map
    }

    val filteredMuscles = remember(activeRegion, fullCatalog, exercisesByRegion) {
        if (activeRegion == ExerciseCatalogRegion.ALL) {
            ALL_MUSCLES
        } else {
            val regionExs = exercisesByRegion[activeRegion].orEmpty()
            val presentMuscles = regionExs.flatMap { ex -> ex.involvedMuscles.map { it.muscle.lowercase() } }.toSet()
            ALL_MUSCLES.filter { it.canonicalName.lowercase() in presentMuscles }
        }
    }

    LaunchedEffect(filteredMuscles) {
        if (selectedMuscle != null && filteredMuscles.none { it.canonicalName.equals(selectedMuscle, ignoreCase = true) }) {
            selectedMuscle = null
        }
    }

    val filteredRegions = remember(selectedMuscle, fullCatalog, exercisesByMuscle) {
        val allRegions = ExerciseCatalogRegion.values().toList()
        if (selectedMuscle == null) {
            allRegions
        } else {
            val muscleExs = exercisesByMuscle[selectedMuscle!!.lowercase()].orEmpty()
            val presentRegions = muscleExs.map { resolveExerciseRegion(it) }.toSet()
            allRegions.filter { it == ExerciseCatalogRegion.ALL || it in presentRegions }
        }
    }

    LaunchedEffect(filteredRegions) {
        val currentRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
        if (currentRegion != ExerciseCatalogRegion.ALL && filteredRegions.none { it == currentRegion }) {
            selectedRegion = null
        }
    }

    val filteredSortModes = remember(selectedRegion, selectedMuscle) {
        ExerciseCatalogSort.values().filter { option ->
            when (option) {
                ExerciseCatalogSort.GROUP_BY_REGION -> selectedRegion == null
                ExerciseCatalogSort.GROUP_BY_MUSCLE -> selectedMuscle == null
                else -> true
            }
        }
    }

    LaunchedEffect(filteredSortModes) {
        if (sortMode !in filteredSortModes) {
            sortMode = ExerciseCatalogSort.RELEVANCE
        }
    }

    var variantFlowExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }

    fun handleSelect(info: ExerciseMuscleInfo) {
        if (editingExisting) {
            onSelect(info)
        } else {
            selectionOrder = if (info.id in selectedExercisesIds) {
                selectionOrder - info.id
            } else {
                selectionOrder + info.id
            }
            onToggleExerciseSelection(info.id)
        }
    }
    var results by remember { mutableStateOf<List<ExerciseMuscleInfo>>(emptyList()) }
    LaunchedEffect(query, fullCatalog, activeRegion, selectedTrait, sortMode, selectedMuscle, selectedHeadName) {
        results = withContext(Dispatchers.Default) {
            filterAndSortExerciseCatalog(
                fullCatalog = fullCatalog,
                normalizedQuery = normalizedQuery,
                activeRegion = activeRegion,
                selectedTrait = selectedTrait,
                sortMode = sortMode,
                selectedMuscle = selectedMuscle,
                selectedHeadName = selectedHeadName,
            )
        }
    }
    val resultListState = rememberLazyListState()
    LaunchedEffect(normalizedQuery, activeRegion, selectedTrait, sortMode, selectedMuscle, selectedHeadName) {
        resultListState.scrollToItem(0)
    }

    val infoExercise = remember(infoExerciseId, fullCatalog) { fullCatalog.firstOrNull { it.id == infoExerciseId } }
    val discomfortByExercise = remember(workoutLogs) {
        discomfortCountsByExercise(workoutLogs)
    }
    val createdCatalog = remember(customExercises) {
        customExercises.sortedBy { it.name.lowercase() }
    }
    val highlightedExercise = remember(highlightedExerciseId, fullCatalog) {
        highlightedExerciseId?.let { id -> fullCatalog.firstOrNull { it.id == id } }
    }
    val categorizedCatalog = remember(fullCatalog) {
        fullCatalog
            .filter { !it.category.isNullOrBlank() }
            .groupBy { it.category!!.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }
    val uncategorizedCatalog = remember(fullCatalog) {
        fullCatalog.filter { it.category.isNullOrBlank() }
    }

     Column(
         Modifier
             .fillMaxWidth()
             .fillMaxHeight()
             .padding(horizontal = 14.dp, vertical = 12.dp),
         verticalArrangement = Arrangement.spacedBy(10.dp),
     ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (editingExisting) "Cambiar ejercicio" else "Catálogo",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    "${fullCatalog.size} ejercicios",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onOpenExerciseCreator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Crear", style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    FilledTonalButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortMode.label, style = MaterialTheme.typography.labelSmall)
                    }
                    KpknDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        filteredSortModes.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortMode = option
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        CatalogSearchField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar por nombre, músculo o equipo",
        )

         val currentHeadMuscle = selectedMuscle?.let { MUSCLE_BY_CANONICAL[it] }
         val hasHeads = currentHeadMuscle != null && currentHeadMuscle.heads.isNotEmpty()
         Row(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.spacedBy(6.dp),
             verticalAlignment = Alignment.CenterVertically,
         ) {
             Box(modifier = Modifier.weight(1f)) {
                 FilledTonalButton(
                     onClick = { showRegionMenu = true },
                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                     shape = RoundedCornerShape(8.dp),
                     modifier = Modifier.fillMaxWidth(),
                 ) {
                     Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                     Spacer(Modifier.width(4.dp))
                     Text(activeRegion.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                     Spacer(Modifier.width(2.dp))
                     Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                 }
                 KpknDropdownMenu(expanded = showRegionMenu, onDismissRequest = { showRegionMenu = false }) {
                     filteredRegions.forEach { region ->
                         DropdownMenuItem(
                             text = { Text(region.label) },
                             onClick = { selectedRegion = if (region == ExerciseCatalogRegion.ALL) null else region; showRegionMenu = false },
                         )
                     }
                 }
             }
             Box(modifier = Modifier.weight(1f)) {
                 FilledTonalButton(
                     onClick = { showMuscleMenu = true },
                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                     shape = RoundedCornerShape(8.dp),
                     modifier = Modifier.fillMaxWidth(),
                 ) {
                     Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(14.dp))
                     Spacer(Modifier.width(4.dp))
                     Text(selectedMuscle?.let { MUSCLE_BY_CANONICAL[it]?.displayName } ?: "Músculo", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                     Spacer(Modifier.width(2.dp))
                     Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                 }
                 KpknDropdownMenu(expanded = showMuscleMenu, onDismissRequest = { showMuscleMenu = false }) {
                     DropdownMenuItem(text = { Text("Todos") }, onClick = { selectedMuscle = null; showMuscleMenu = false })
                     filteredMuscles.forEach { muscle ->
                         DropdownMenuItem(
                             text = { Text(muscle.displayName) },
                             onClick = { selectedMuscle = muscle.canonicalName; showMuscleMenu = false },
                         )
                     }
                 }
             }
             if (hasHeads) {
                 Box(modifier = Modifier.weight(1f)) {
                     FilledTonalButton(
                         onClick = { showHeadMenu = true },
                         contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                         shape = RoundedCornerShape(8.dp),
                         modifier = Modifier.fillMaxWidth(),
                     ) {
                         Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                         Spacer(Modifier.width(4.dp))
                         Text(selectedHeadName ?: "Zona", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                         Spacer(Modifier.width(2.dp))
                         Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                     }
                     KpknDropdownMenu(expanded = showHeadMenu, onDismissRequest = { showHeadMenu = false }) {
                         DropdownMenuItem(text = { Text("Completo") }, onClick = { selectedHeadName = null; showHeadMenu = false })
                         currentHeadMuscle!!.heads.forEach { head ->
                             DropdownMenuItem(
                                 text = { Text(head.name) },
                                 onClick = { selectedHeadName = head.name; showHeadMenu = false },
                             )
                         }
                     }
                 }
             }
         }

         AnimatedVisibility(visible = hasHeads && showEmphasisCard) {
             val emphasisTitle = if (selectedHeadName != null) {
                 "Énfasis: $selectedHeadName"
             } else {
                 "Énfasis en porciones de ${currentHeadMuscle?.displayName}"
             }
             val emphasisBody = getMuscleEmphasisEducationalText(selectedMuscle ?: "", selectedHeadName)

             Card(
                 modifier = Modifier
                     .fillMaxWidth()
                     .padding(vertical = 6.dp),
                 colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                 ),
                 shape = RoundedCornerShape(12.dp),
                 border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
             ) {
                 Column(modifier = Modifier.padding(12.dp)) {
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier.weight(1f)
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Info,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(16.dp)
                             )
                             Spacer(Modifier.width(6.dp))
                             Text(
                                 text = emphasisTitle,
                                 style = MaterialTheme.typography.titleSmall,
                                 color = MaterialTheme.colorScheme.primary,
                                 fontWeight = FontWeight.SemiBold,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                         }
                         IconButton(
                             onClick = { showEmphasisCard = false },
                             modifier = Modifier.size(24.dp)
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Close,
                                 contentDescription = "Cerrar",
                                 tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                 modifier = Modifier.size(16.dp)
                             )
                         }
                     }
                     if (emphasisBody.isNotEmpty()) {
                         Spacer(Modifier.height(4.dp))
                         Text(
                             text = emphasisBody,
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                         )
                         Spacer(Modifier.height(4.dp))
                         Text(
                             text = "*El énfasis desplaza el estímulo relativo, pero no aísla por completo el músculo del resto de sus cabezas.",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                             fontStyle = FontStyle.Italic
                         )
                     }
                 }
             }
         }

     if (showGroupBrowser && selectedRegion == null && normalizedQuery.isBlank()) {
         Text(
             "Grupos",
             style = MaterialTheme.typography.labelLarge,
             fontWeight = FontWeight.Bold,
             color = Color.White
         )
         LazyColumn(
             modifier = Modifier.weight(1f),
             verticalArrangement = Arrangement.spacedBy(8.dp),
         ) {
             item {
                 Text(
                     "Explorar por grupo muscular",
                     style = MaterialTheme.typography.labelMedium,
                     color = Color.White.copy(alpha = 0.7f),
                     fontWeight = FontWeight.SemiBold,
                 )
             }
             items(ExerciseCatalogRegion.values(), key = { it.name }) { region ->
                 val count = fullCatalog.count { region == ExerciseCatalogRegion.ALL || resolveExerciseRegion(it) == region }
                 Surface(
                     modifier = Modifier
                         .fillMaxWidth()
                         .clickable { selectedRegion = region }
                         .padding(horizontal = 2.dp),
                     shape = RoundedCornerShape(14.dp),
                     color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                 ) {
                     Row(
                         modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically,
                     ) {
                         Column(modifier = Modifier.weight(1f)) {
                             Text(region.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                             Text("$count ejercicios", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                         }
                         Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                 }
             }

             if (categorizedCatalog.isNotEmpty()) {
                 item {
                     Spacer(Modifier.height(8.dp))
                     Text(
                         "Por grupo muscular",
                         style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         fontWeight = FontWeight.SemiBold,
                     )
                 }
                 items(categorizedCatalog, key = { it.first }) { (category, exercisesInCategory) ->
                     Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                         Text(
                             category,
                             style = MaterialTheme.typography.titleSmall,
                             fontWeight = FontWeight.Black,
                             color = Color.White
                         )
                          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                              items(exercisesInCategory, key = { it.id }) { info ->
                                  ExercisePickerCompactCard(
                                      info = info,
                                      isSelected = info.id in selectedExercisesIds,
                                      onSelect = { handleSelect(info) },
                                      onInfo = { infoExerciseId = info.id },
                                      onOpenVariantFlow = { variantFlowExercise = info },
                                  )
                              }
                          }
                     }
                 }
             }

             if (uncategorizedCatalog.isNotEmpty()) {
                 item {
                     Spacer(Modifier.height(8.dp))
                     Text(
                         "Sin grupo",
                         style = MaterialTheme.typography.titleSmall,
                         fontWeight = FontWeight.Black,
                         color = Color.White
                     )
                 }
                  items(uncategorizedCatalog, key = { it.id }) { info ->
                      ExercisePickerDetailedCard(
                          info = info,
                          isSelected = info.id in selectedExercisesIds,
                          onSelect = { handleSelect(info) },
                          onInfo = { infoExerciseId = info.id },
                      )
                  }
             }
         }
     } else {
         if (selectedRegion != null) {
             Text(activeRegion.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
         }

         LazyColumn(
             state = resultListState,
             modifier = Modifier.weight(1f),
             verticalArrangement = Arrangement.spacedBy(10.dp),
         ) {
             item {
                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                     Text(
                         "Filtros rápidos",
                         style = MaterialTheme.typography.labelLarge,
                         fontWeight = FontWeight.Bold,
                         color = Color.White
                     )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ExerciseCatalogTrait.values().toList(), key = { it.name }) { trait ->
                            CompactCatalogFilterChip(
                                selected = selectedTrait == trait,
                                onClick = { selectedTrait = if (selectedTrait == trait) null else trait },
                                label = trait.label,
                            )
                        }
                    }
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically,
                     ) {
                         Text(
                             if (results.isEmpty()) "Sin resultados" else "${results.size} resultados",
                             style = MaterialTheme.typography.labelMedium,
                             fontWeight = FontWeight.Bold,
                             color = Color.White
                         )
                         if (selectedTrait != null || activeRegion != ExerciseCatalogRegion.ALL || normalizedQuery.isNotBlank() || selectedMuscle != null || selectedHeadName != null) {
                             TextButton(
                                 onClick = {
                                     selectedRegion = null
                                     selectedTrait = null
                                     selectedMuscle = null
                                     selectedHeadName = null
                                     onSearch("")
                                     sortMode = ExerciseCatalogSort.RELEVANCE
                                 }
                             ) { Text("Limpiar") }
                         }
                     }
                     if (highlightedExercise != null || (createdCatalog.isNotEmpty() && normalizedQuery.isBlank() && selectedTrait == null && activeRegion == ExerciseCatalogRegion.ALL)) {
                         Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             Text(
                                 "Creados por ti",
                                 style = MaterialTheme.typography.labelLarge,
                                 fontWeight = FontWeight.Black,
                                 color = Color.White,
                             )
                             highlightedExercise?.let { info ->
                                 ExercisePickerDetailedCard(
                                     info = info,
                                     isSelected = info.id in selectedExercisesIds,
                                     onSelect = { handleSelect(info) },
                                     onInfo = { infoExerciseId = info.id },
                                 )
                             }
                             createdCatalog
                                 .filterNot { it.id == highlightedExercise?.id }
                                 .take(4)
                                 .forEach { info ->
                                     ExercisePickerDetailedCard(
                                         info = info,
                                         isSelected = info.id in selectedExercisesIds,
                                         onSelect = { handleSelect(info) },
                                         onInfo = { infoExerciseId = info.id },
                                     )
                                 }
                         }
                     }
                 }
             }
              items(results, key = { it.id }) { info ->
                  ExercisePickerDetailedCard(
                      info = info,
                      isSelected = info.id in selectedExercisesIds,
                      onSelect = { handleSelect(info) },
                      onInfo = { infoExerciseId = info.id },
                      onOpenVariantFlow = { variantFlowExercise = info },
                  )
              }
          }

        if (!editingExisting && selectedExercises.isNotEmpty()) {
            ExercisePickerSelectionDock(
                selectedExercises = selectedExercises,
                onRemove = { id ->
                    selectionOrder = selectionOrder - id
                    onToggleExerciseSelection(id)
                },
                onCreateSuperset = onCreateSuperset,
                onClearExerciseSelection = onClearExerciseSelection,
                onMultiSelect = onMultiSelect,
            )
        }
        }
    }

    infoExercise?.let { selected ->
        ExerciseCatalogInfoDialog(
            exercise = selected,
            catalog = fullCatalog,
            associatedDiscomforts = discomfortByExercise[selected.id].orEmpty(),
            onOpenExercise = onOpenExerciseDetail,
            onDismiss = { infoExerciseId = null },
            onOpenVariantFlow = { ex -> variantFlowExercise = ex },
        )
    }

    variantFlowExercise?.let { exercise ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        VariantFlowSheet(
            initialExercise = exercise,
            sheetState = sheetState,
            onConfirm = { selectedVariant, selectedAspects ->
                VariantFlowResultCache.store(
                    exerciseDbId = selectedVariant.id,
                    variantName = selectedVariant.variantName,
                    variantGroupId = selectedVariant.variantGroupId,
                    variantGroupName = selectedVariant.variantGroupName,
                    selectedAspects = selectedAspects,
                )
                if (editingExisting) {
                    onSelect(selectedVariant)
                } else {
                    if (selectedVariant.id !in selectedExercisesIds) {
                        selectionOrder = selectionOrder + selectedVariant.id
                        onToggleExerciseSelection(selectedVariant.id)
                    }
                }
                variantFlowExercise = null
            },
            onDismiss = { variantFlowExercise = null },
        )
    }
}
