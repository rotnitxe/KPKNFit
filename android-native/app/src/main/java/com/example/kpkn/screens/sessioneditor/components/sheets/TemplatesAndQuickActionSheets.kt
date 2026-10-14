package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.CatalogInvolvementAccordions
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.kpkn.ui.components.KpknAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatesSheet(
    templates: List<SessionTemplate>,
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
    onDismiss: () -> Unit,
    onArchiveUserTemplate: (String) -> Unit = {},
    onRestoreUserTemplate: (String) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onEditUserTemplate: suspend (
        SessionTemplate,
        String,
        String,
        com.example.kpkn.data.splits.Difficulty,
        com.example.kpkn.data.sessions.SessionTemplateFocusCategory?,
        com.example.kpkn.data.sessions.SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit> = { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Plantillas de sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text("Buscar plantilla…", color = KpknSheetTokens.ControlPlaceholder)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = KpknSheetTokens.ControlLabel,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.ControlLabel),
                colors = kpknSheetWhiteFieldColors(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TemplateCatalogBrowser(
                    templates = templates,
                    searchQuery = searchQuery,
                    onSelectTemplate = onSelectTemplate,
                    archivedUserTemplates = archivedUserTemplates,
                    templatesReady = templatesReady,
                    corruptTemplateIds = corruptTemplateIds,
                    onArchiveUserTemplate = onArchiveUserTemplate,
                    onRestoreUserTemplate = onRestoreUserTemplate,
                    onDeleteUserTemplate = onDeleteUserTemplate,
                    onEditUserTemplate = onEditUserTemplate,
                    onSaveCurrentTemplate = onSaveCurrentTemplate,
                    // Las plantillas V3 guardan la configuración V2 completa
                    // (incluidos implemento, estación y lateralidad). El
                    // snapshot base solo contiene las definiciones padre y no
                    // puede resolver esos IDs ni sus chips.
                    exerciseIndex = catalogExerciseIndex(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (applyDecision != null) {
        KpknAlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Aplicar plantilla", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla \"${applyDecision.template.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                ) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
internal fun ExerciseQuickActionsSheet(
    exercise: Exercise?,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenWarmup: () -> Unit,
    onOpenMobility: () -> Unit,
    onDelete: () -> Unit,
    onManageSuperset: () -> Unit,
) {
    if (exercise == null) {
        Text(
            text = "No encontramos el ejercicio seleccionado.",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val catalogLookup = catalogExerciseIndex()
    val selectedInfo = remember(exercise.id, catalogLookup) {
        resolveCatalogExerciseInfo(exercise, catalogLookup)
    }
    val context = LocalContext.current
    var catalogEntry by remember { mutableStateOf(CatalogV2ProcessCache.peek()) }
    LaunchedEffect(context) {
        if (catalogEntry == null) {
            catalogEntry = runCatching {
                withContext(Dispatchers.IO) {
                    CatalogV2ProcessCache.getOrLoad(context)
                }
            }.getOrNull()
        }
    }
    val resolvedV2 = remember(exercise.id, selectedInfo, catalogEntry) {
        resolveCatalogExerciseV2(
            exercise = exercise,
            catalog = catalogEntry?.catalog,
            legacyInfo = selectedInfo,
        )
    }
    val involvedMuscles = remember(exercise.id, exercise.effectiveMuscles, selectedInfo) {
        (exercise.effectiveMuscles?.takeIf { it.isNotEmpty() }
            ?: selectedInfo?.involvedMuscles.orEmpty())
            .filter { it.muscle.isNotBlank() }
    }
    val muscleInvolvement = remember(involvedMuscles) {
        listOf(
            MuscleRole.PRIMARY to "Principales",
            MuscleRole.SECONDARY to "Secundarios",
            MuscleRole.STABILIZER to "Estabilizadores",
            MuscleRole.NEUTRALIZER to "Neutralizadores",
        ).mapNotNull { (role, label) ->
            val names = involvedMuscles
                .filter { it.role == role }
                .map { it.muscle.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
            names.takeIf { it.isNotEmpty() }?.let { "$label: ${it.joinToString(" · ")}" }
        }.joinToString("\n")
    }
    val articularProfile = remember(exercise.id, selectedInfo) {
        listOfNotNull(
            selectedInfo?.articulationType?.let { "Tipo: ${articulationLabel(it)}" },
            selectedInfo?.movementPattern?.takeIf { it.isNotBlank() }?.let { "Patrón: $it" },
            selectedInfo?.force?.takeIf { it.isNotBlank() }?.let { "Dirección: $it" },
            selectedInfo?.anatomicalConsiderations?.firstOrNull()?.trait?.takeIf { it.isNotBlank() }?.let { "Consideración: $it" },
        )
    }
    val tags = if (exercise.cardioDetails != null) {
        listOfNotNull(
            "Cardio",
            com.example.kpkn.data.models.CardioCatalog.items.firstOrNull { it.type == exercise.cardioDetails.type }?.name,
            if (exercise.cardioDetails.requiresGps) "GPS en vivo" else null,
        ).filter { it.isNotBlank() }.distinct()
    } else {
        listOfNotNull(
            selectedInfo?.equipment,
            selectedInfo?.category,
            selectedInfo?.type,
            selectedInfo?.variantName,
        ).filter { it.isNotBlank() }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Información del ejercicio", style = KpknType.title, color = WizardColors.text)
        Text(text = exercise.name, style = KpknType.bodyStrong, color = WizardColors.text)
        if (tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(KpknShape.pill)
                            .background(KpknSurface.inset)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(text = tag, style = KpknType.caption, color = WizardColors.textMuted)
                    }
                }
            }
        }

        // Descripción exacta del catálogo (o del catálogo de cardio)
        val exactDescription = if (exercise.cardioDetails != null) {
            com.example.kpkn.data.models.CardioCatalog.items.firstOrNull { it.type == exercise.cardioDetails.type || it.id == exercise.exerciseDbId }?.description
        } else {
            resolvedV2?.definition?.description?.takeIf { it.isNotBlank() }
                ?: selectedInfo?.description?.takeIf { it.isNotBlank() }
        }

        KpknModule {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknModuleHeader(label = "Descripción del ejercicio")
                Text(
                    text = exactDescription ?: "No hay una descripción editorial disponible para este ejercicio.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
        }

        // Involucramiento muscular y articular con jerarquía visual de alto contraste
        val infoForInvolvement = resolvedV2?.legacyInfo?.takeIf { it.involvedMuscles.isNotEmpty() }
            ?: selectedInfo?.takeIf { it.involvedMuscles.isNotEmpty() }
        val jointsForInvolvement = resolvedV2?.configuration?.profile?.jointInvolvement.orEmpty()

        if (infoForInvolvement != null || jointsForInvolvement.isNotEmpty()) {
            CatalogInvolvementAccordions(
                info = infoForInvolvement,
                joints = jointsForInvolvement,
            )
        }

        // La única acción de la hoja: cambiar el ejercicio por otro (abre el selector), en una fila de 56 dp.
        KpknModule(onClick = onOpenPicker, onClickLabel = "Cambiar ejercicio") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.text,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Cambiar ejercicio",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = KpknLineIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.textMuted,
                )
            }
        }
    }
}

private fun articulationLabel(value: String): String = when (value.uppercase()) {
    "MULTIARTICULAR" -> "Multiarticular"
    "AISLADO" -> "Aislado"
    else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}


@Composable
internal fun MobilityPickerSheet(
    selectedMobilityIds: Set<String> = emptySet(),
    onAdd: (MobilityExercise) -> Unit,
    onRemove: (MobilityExercise) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRegion by rememberSaveable { mutableStateOf("") }
    var expandedMobilityId by rememberSaveable { mutableStateOf<String?>(null) }
    val allMobility = remember { MobilityExerciseCatalog.getAllMobilityExercises() }
    val uniqueRegions = remember(allMobility) { allMobility.map { it.bodyRegion }.distinct().sorted() }
    val results = remember(query, selectedRegion, allMobility) {
        val byQuery = if (query.isBlank()) allMobility else MobilityExerciseCatalog.searchMobility(query)
        if (selectedRegion.isBlank()) byQuery else byQuery.filter { it.bodyRegion == selectedRegion }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Catálogo de movilidad", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "${allMobility.size} movimientos en español, por articulación y objetivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Orientación práctica: no sustituye una evaluación clínica.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
        CatalogSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar movilidad, zona o molestia",
        )
        // Body region filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                CompactCatalogFilterChip(
                    selected = selectedRegion.isBlank(),
                    onClick = { selectedRegion = "" },
                    label = "Todas",
                    glassDark = true,
                )
            }
            items(uniqueRegions) { region ->
                CompactCatalogFilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = mobilityBodyRegionLabel(region),
                    glassDark = true,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { mobility ->
                val alreadyAdded = mobility.id in selectedMobilityIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KpknSheetTokens.Panel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(mobility.name, fontWeight = FontWeight.Bold)
                            val details = listOfNotNull(
                                mobilityBodyRegionLabel(mobility.bodyRegion).takeIf { it.isNotBlank() },
                                mobility.joints.firstOrNull()?.replaceFirstChar { it.uppercase() },
                                mobility.discomfortIds.map { discomfortLabel(it) }.filter { it.isNotBlank() }.joinToString().takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                mobility.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (expandedMobilityId == mobility.id) 4 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = {
                                    expandedMobilityId =
                                        if (expandedMobilityId == mobility.id) null else mobility.id
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(if (expandedMobilityId == mobility.id) "Ocultar ficha" else "Ver ficha")
                            }
                            if (expandedMobilityId == mobility.id) {
                                Text(
                                    "Objetivo: ${mobility.objective}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Cómo hacerlo: ${mobility.instructions.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Material: ${mobility.equipment} · Nivel: ${mobility.level}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "Precaución: ${mobility.precautions}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { if (alreadyAdded) onRemove(mobility) else onAdd(mobility) },
                            colors = kpknSheetWhiteTonalButtonColors(),
                        ) {
                            Text(if (alreadyAdded) "Agregado (Quitar)" else "Agregar")
                        }
                    }
                }
            }
        }
    }
}
