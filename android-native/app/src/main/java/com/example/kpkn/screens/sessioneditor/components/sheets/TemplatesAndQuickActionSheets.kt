package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.design.CardioTypeIcon
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.CatalogInvolvementAccordions
import com.example.kpkn.screens.sessioneditor.components.Thumbnail
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                Text(
                    text = "Plantillas de sesión",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f),
                )
                KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar", onClick = onDismiss)
            }
            KpknTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Buscar plantilla…",
                leadingIcon = KpknLineIcons.Search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    glassDark = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (applyDecision != null) {
        TemplateApplyDialog(
            decision = applyDecision,
            onDismiss = onCancelApply,
            onApply = onConfirmApplyTemplate,
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
    onDuplicate: () -> Unit = {},
) {
    if (exercise == null) {
        Text(
            text = "No encontramos el ejercicio seleccionado.",
            modifier = Modifier.padding(20.dp),
            style = KpknType.body,
            color = WizardColors.textMuted,
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
    // D1: la misma línea que la fila de la sesión («4 × 4–6 · RIR 2 · 3:00»); los huecos de ronda no son series.
    val workingSets = exercise.sets.filterNot { it.isEmptySlot }
    val firstSet = workingSets.firstOrNull()
    val quickSummary = buildList {
        if (workingSets.isNotEmpty()) add(exerciseDoseSummary(exercise.copy(sets = workingSets)))
        firstSet?.targetRIR?.let { add("RIR $it") }
        if (firstSet?.targetRIR == null) firstSet?.targetRPE?.let { add("RPE ${formatDecimalComma(it)}") }
        exercise.restTime?.takeIf { it > 0 }?.let { add(com.example.kpkn.ui.design.KpknSetFormat.rest(it)) }
    }.joinToString(" · ")

    var descriptionExpanded by remember(exercise.id) { mutableStateOf(false) }
    val selectedImplementation = exercise.selectedExecutionOption ?: exercise.selectedAspects?.values?.firstOrNull()
    val thumbnailRes = remember(
        exercise.id,
        exercise.catalogDefinitionId,
        exercise.catalogConfigurationId,
        exercise.exerciseDbId,
        exercise.exerciseId,
        selectedImplementation,
        resolvedV2?.definition?.id,
        resolvedV2?.configuration?.id,
    ) {
        quickActionThumbnailRes(exercise, resolvedV2)
    }
    val thumbnailPattern = remember(exercise.name) { exercisePatternOf(exercise.name) }
    val supersetLabel = if (exercise.isInSuperset()) "Gestionar superserie" else "Superserie"
    val heroActions = listOf(
        QuickAction(KpknLineIcons.Info, "Ficha", "quick-action-ficha") { onOpenExerciseDetail(exercise.id) },
        QuickAction(KpknLineIcons.Swap, "Cambiar", "quick-action-cambiar", onOpenPicker),
        QuickAction(KpknLineIcons.Flame, "Aproximación", "quick-action-aproximacion", onOpenWarmup),
        QuickAction(KpknLineIcons.Joint, "Movilidad", "quick-action-movilidad", onOpenMobility),
        QuickAction(KpknLineIcons.Link, supersetLabel, "quick-action-superserie", onManageSuperset),
        QuickAction(KpknLineIcons.Layers, "Duplicar", "quick-action-duplicar", onDuplicate),
    )

    val heroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val heroGradient = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.25f),
            0.45f to Color(0xFF141414).copy(alpha = 0.58f),
            1.0f to Color(0xFF141414),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // #4 · Hero edge-to-edge con min 236 dp, alto adaptable por contenido, esquinas superiores de 28 dp, degradado multicapa y chips glass
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414), heroShape),
        ) {
            if (thumbnailRes != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(thumbnailRes),
                    contentDescription = exercise.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(heroShape),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 236.dp)
                    .background(heroGradient, heroShape)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Drag handle translúcido encima del hero
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                )
                if (thumbnailRes == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PatternGlyph(thumbnailPattern, SymbolPalette.musculo, Modifier.size(56.dp))
                    }
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = exercise.name,
                            style = KpknType.title.copy(
                                fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                lineHeight = 25.sp,
                            ),
                            color = WizardColors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (quickSummary.isNotEmpty()) {
                            Text(quickSummary, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f) 2 else 3
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        heroActions.chunked(columns).forEach { rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowActions.forEach { action ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag(action.tag)
                                            .clip(KpknShape.pill)
                                            .background(Color(0xCC161616), KpknShape.pill)
                                            .border(1.dp, Color.White.copy(alpha = 0.16f), KpknShape.pill)
                                            .clickable(role = Role.Button, onClick = action.onClick)
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        androidx.compose.material3.Icon(action.icon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(action.label, style = KpknType.chip, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Descripción exacta del catálogo (o del catálogo de cardio) con filete vertical cálido
            val exactDescription = if (exercise.cardioDetails != null) {
                com.example.kpkn.data.models.CardioCatalog.items.firstOrNull { it.type == exercise.cardioDetails.type || it.id == exercise.exerciseDbId }?.description
            } else {
                resolvedV2?.definition?.description?.takeIf { it.isNotBlank() }
                    ?: selectedInfo?.description?.takeIf { it.isNotBlank() }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(SymbolPalette.energia),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "SOBRE EL EJERCICIO",
                        style = KpknType.eyebrow,
                        color = SymbolPalette.energia,
                    )
                    Text(
                        text = exactDescription ?: "No hay una descripción editorial disponible para este ejercicio.",
                        style = KpknType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                        color = WizardColors.text,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (exactDescription != null && exactDescription.length > 160) {
                        KpknTextAction(
                            text = if (descriptionExpanded) "Ver menos" else "Leer completa",
                            onClick = { descriptionExpanded = !descriptionExpanded },
                        )
                    }
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("quick-action-eliminar")
                    .clip(KpknShape.pill)
                    .border(1.dp, WizardColors.danger.copy(alpha = 0.4f), KpknShape.pill)
                    .clickable(role = Role.Button, onClick = onDelete),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(KpknLineIcons.Trash, contentDescription = null, tint = WizardColors.danger)
                Spacer(Modifier.width(8.dp))
                Text("Eliminar", style = KpknType.bodyStrong, color = WizardColors.danger)
            }
        }
    }
}

private data class QuickAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val tag: String,
    val onClick: () -> Unit,
)

@Composable
internal fun MobilityPickerSheet(
    activeExercise: Exercise? = null,
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
    val mobilityRecommendations = remember(activeExercise?.id, activeExercise?.catalogConfigurationId, activeExercise?.exerciseDbId, selectedMobilityIds) {
        val allDefinitions = approvedExerciseCatalogV2()?.families.orEmpty().asSequence()
            .flatMap { it.definitions.asSequence() }
        val configurationId = activeExercise?.catalogConfigurationId?.takeIf(String::isNotBlank)
        val directConfig = configurationId?.let { id ->
            allDefinitions.flatMap { it.configurations.asSequence() }.firstOrNull { it.id == id }
        }
        val fallbackConfig = if (directConfig?.profile != null || activeExercise == null) {
            directConfig
        } else {
            val lookupIds = listOfNotNull(activeExercise.exerciseDbId, activeExercise.exerciseId)
                .mapNotNull { id -> id.trim().takeIf(String::isNotEmpty)?.lowercase() }
            allDefinitions.firstOrNull { def ->
                def.id.lowercase() in lookupIds ||
                    def.canonicalName.equals(activeExercise.name.trim(), ignoreCase = true)
            }?.configurations?.firstOrNull { it.profile != null }
        }
        val profile = fallbackConfig?.profile
        if (profile != null) {
            com.example.kpkn.domain.training.approach.JointMobility.recommendationsFor(
                primaryJointIds = profile.jointInvolvement.filter { it.role == com.example.kpkn.domain.exercises.catalogv2.JointRoleV2.PRIMARY }.map { it.jointId },
                secondaryJointIds = profile.jointInvolvement.filter { it.role == com.example.kpkn.domain.exercises.catalogv2.JointRoleV2.SECONDARY }.map { it.jointId },
                primaryMuscleIds = profile.primaryMuscles,
                secondaryMuscleIds = profile.secondaryMuscles,
                excludedMovementIds = selectedMobilityIds,
                limit = 4,
            )
        } else if (activeExercise != null) {
            val muscles = activeExercise.effectiveMuscles.orEmpty().ifEmpty {
                com.example.kpkn.data.exercises.resolveCatalogExerciseInfo(
                    catalogConfigurationId = activeExercise.catalogConfigurationId,
                    exerciseDbId = activeExercise.exerciseDbId,
                    exerciseId = activeExercise.exerciseId,
                    exerciseName = activeExercise.name,
                )?.involvedMuscles.orEmpty()
            }
            val primaryMuscles = muscles
                .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                .map { it.muscle }
            val secondaryMuscles = muscles
                .filter { it.role != com.example.kpkn.data.models.MuscleRole.PRIMARY }
                .map { it.muscle }
            if (primaryMuscles.isEmpty() && secondaryMuscles.isEmpty()) emptyList()
            else com.example.kpkn.domain.training.approach.JointMobility.recommendationsFor(
                primaryJointIds = emptyList(),
                secondaryJointIds = emptyList(),
                primaryMuscleIds = primaryMuscles,
                secondaryMuscleIds = secondaryMuscles,
                excludedMovementIds = selectedMobilityIds,
                limit = 4,
            )
        } else {
            emptyList()
        }
    }
    val recommendationById = remember(mobilityRecommendations) { mobilityRecommendations.associateBy { it.movement.id } }
    val results = remember(query, selectedRegion, allMobility) {
        val byQuery = if (query.isBlank()) allMobility else MobilityExerciseCatalog.searchMobility(query)
        if (selectedRegion.isBlank()) byQuery else byQuery.filter { it.bodyRegion == selectedRegion }
    }
    val orderedResults = remember(results, mobilityRecommendations, query, selectedRegion) {
        if (query.isNotBlank() || selectedRegion.isNotBlank()) results
        else mobilityRecommendations.map { it.movement } + results.filterNot { it.id in recommendationById }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Catálogo de movilidad", style = KpknType.dialogTitle, color = WizardColors.text)
                Text(
                    "${allMobility.size} movimientos en español, por articulación y objetivo",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Text(
                    "Orientación práctica: no sustituye una evaluación clínica.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar", onClick = onDismiss)
        }
        KpknTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar movilidad, zona o molestia",
            leadingIcon = KpknLineIcons.Search,
            modifier = Modifier.fillMaxWidth().testTag("mobility-picker-search"),
        )
        // Body region filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                KpknChip(
                    text = "Todas",
                    selected = selectedRegion.isBlank(),
                    onClick = { selectedRegion = "" },
                )
            }
            items(uniqueRegions) { region ->
                KpknChip(
                    text = mobilityBodyRegionLabel(region),
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (mobilityRecommendations.isNotEmpty() && query.isBlank() && selectedRegion.isBlank()) {
                item(key = "mobility-picker-suggested-heading") {
                    KpknModuleHeader(label = "Sugeridas para ${activeExercise?.name.orEmpty()}")
                }
            }
            if (results.isEmpty()) {
                item(key = "mobility-picker-empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Sin resultados", style = KpknType.bodyStrong, color = WizardColors.text,
                            modifier = Modifier.testTag("mobility-picker-empty"))
                        Text("Prueba con otra palabra o elige otra zona.",
                            style = KpknType.caption, color = WizardColors.textMuted)
                    }
                }
            }
            items(orderedResults, key = { it.id }) { mobility ->
                val alreadyAdded = mobility.id in selectedMobilityIds
                val expanded = expandedMobilityId == mobility.id
                KpknModule(modifier = Modifier.testTag("mobility-picker-item-${mobility.id}"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(mobility.name, style = KpknType.bodyStrong, color = WizardColors.text)
                    val details = listOfNotNull(
                        mobilityBodyRegionLabel(mobility.bodyRegion).takeIf { it.isNotBlank() },
                        mobility.joints.firstOrNull()?.replaceFirstChar { it.uppercase() },
                        mobility.discomfortIds.map { discomfortLabel(it) }.filter { it.isNotBlank() }.joinToString().takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(
                            details,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    if (mobility.id in recommendationById) {
                        Text(
                            recommendationById.getValue(mobility.id).reason,
                            style = KpknType.caption,
                            color = WizardColors.done,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        mobility.description,
                        style = KpknType.caption,
                        color = WizardColors.text,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expanded) {
                        com.example.kpkn.ui.components.MobilityTechniqueImage(
                            movementId = mobility.id,
                            contentDescription = "Técnica de ${mobility.name}",
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                        )
                        Text(
                            "Objetivo: ${mobility.objective}",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        Text(
                            "Material: ${mobility.equipment} · Nivel: ${mobility.level}",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        Text(
                            "Precaución: ${mobility.precautions}",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MobilityPickerActions(
                        expanded = expanded,
                        alreadyAdded = alreadyAdded,
                        onToggleDetails = { expandedMobilityId = if (expanded) null else mobility.id },
                        onToggleMembership = { if (alreadyAdded) onRemove(mobility) else onAdd(mobility) },
                    )
                }
            }
        }
    }
}


/** H3: con letra grande las dos acciones conservan su texto completo y su objetivo táctil. */
@Composable
private fun MobilityPickerActions(
    expanded: Boolean,
    alreadyAdded: Boolean,
    onToggleDetails: () -> Unit,
    onToggleMembership: () -> Unit,
) {
    val details: @Composable () -> Unit = {
        KpknTextAction(
            text = if (expanded) "Ocultar ficha" else "Ver ficha",
            onClick = onToggleDetails,
        )
    }
    val membership: @Composable (Modifier) -> Unit = { modifier ->
        if (alreadyAdded) {
            KpknPrimaryPill(text = "Añadido · Quitar", onClick = onToggleMembership, modifier = modifier.heightIn(min = 48.dp))
        } else {
            KpknSecondaryPill(text = "Añadir", onClick = onToggleMembership, modifier = modifier.heightIn(min = 48.dp))
        }
    }
    if (LocalDensity.current.fontScale >= 1.2f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            details()
            membership(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            details()
            Spacer(Modifier.weight(1f))
            membership(Modifier)
        }
    }
}

internal fun quickActionThumbnailRes(
    exercise: Exercise,
    resolvedV2: com.example.kpkn.screens.sessioneditor.components.ResolvedCatalogExerciseV2? = null,
): Int? {
    val selectedImplementation = exercise.selectedExecutionOption ?: exercise.selectedAspects?.values?.firstOrNull()
    return ExerciseTechniqueImageLookup.resolveImageResId(
        catalogDefinitionId = exercise.catalogDefinitionId ?: resolvedV2?.definition?.id,
        catalogConfigurationId = exercise.catalogConfigurationId ?: resolvedV2?.configuration?.id,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId ?: exercise.id,
        selectedImplementation = selectedImplementation,
    ) ?: resolvedV2?.let {
        ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = it.definition,
            resolvedConfigurationId = exercise.catalogConfigurationId ?: it.configuration.id,
            selectedOptions = it.configuration.selectedOptions,
        )
    }
}
