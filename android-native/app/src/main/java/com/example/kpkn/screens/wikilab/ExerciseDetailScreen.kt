package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.screens.sessioneditor.components.SmartExerciseEditorDialog
import com.example.kpkn.screens.wikilab.components.CaupolicanSquatInteractiveViewer
import com.example.kpkn.screens.wikilab.components.SquatVariant
import com.example.kpkn.ui.components.KpknAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseMuscleInfo,
    onNavigateToMuscle: ((String) -> Unit)? = null,
    onNavigateToJoint: ((String) -> Unit)? = null,
    onNavigateToPattern: ((String) -> Unit)? = null,
    onNavigateToExercise: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val metadata = remember(exercise.id, exercise.catalogRichMetadataJson) {
        exercise.decodeCatalogRichMetadata()
    }
    val legacyMuscles = remember(exercise.id, exercise.involvedMuscles) {
        collapseInvolvedMusclesToCanonical(exercise.involvedMuscles)
    }
    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val catalog = remember(customExercises) {
        (com.example.kpkn.data.exercises.catalogExerciseIndex().values + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }
    val relations = remember(exercise.id, catalog) {
        buildAprendeExerciseRelations(exercise, catalog)
    }
    val isSquatExercise = remember(metadata?.identity?.definitionId) {
        // This visual is editorially curated for two v2 definitions. Do not
        // infer it from a custom exercise's visible name or equipment.
        metadata?.identity?.definitionId?.lowercase() in setOf(
            "high_bar_back_squat",
            "low_bar_back_squat",
        )
    }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = APRENDE_BACKGROUND,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aprende",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                actions = {
                    if (exercise.isCustom) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Edit, "Editar ejercicio", tint = Color.White)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, "Eliminar ejercicio", tint = Color(0xFFCF8F8F))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = APRENDE_BACKGROUND,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(APRENDE_BACKGROUND).padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = metadata?.display?.displayName ?: exercise.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        ),
                    )
                    if (metadata == null) {
                        exercise.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                            Text(
                                text = "También conocido como: $alias",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                    val description = metadata?.editorial?.description?.takeIf { it.isNotBlank() }
                        ?: exercise.description
                    description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 22.sp,
                            ),
                        )
                    }
                }
            }

            if (metadata != null && (exercise.catalogVariantChips.isNotEmpty() || metadata.display.displaySummary.isNotBlank())) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WikiSectionHeader("Configuración seleccionada")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(exercise.catalogVariantChips.ifEmpty { listOf(metadata.display.displaySummary) }) { chip ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = APRENDE_PANEL,
                                ) {
                                    Text(
                                        chip,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif),
                                        color = APRENDE_TEXT_SECONDARY,
                                    )
                                }
                            }
                        }
                        metadata.editorial.variantRationale.takeIf { it.isNotBlank() }?.let { rationale ->
                            Text(
                                rationale,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.72f),
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            if (isSquatExercise) {
                item {
                    val defaultVariant = if (metadata?.identity?.definitionId == "low_bar_back_squat") {
                        SquatVariant.LOW_BAR
                    } else {
                        SquatVariant.HIGH_BAR
                    }
                    CaupolicanSquatInteractiveViewer(
                        initialVariant = defaultVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            item { WikiInfobox(exercise, metadata) }

            if (metadata != null) {
                item {
                    Column {
                        WikiSectionHeader("Músculos involucrados")
                        Spacer(Modifier.height(8.dp))
                        WikiCatalogMuscles(metadata, onNavigateToMuscle)
                    }
                }
                if (metadata.anatomy.jointInvolvement.isNotEmpty()) {
                    item {
                        Column {
                            WikiSectionHeader("Articulaciones y acciones")
                            Spacer(Modifier.height(8.dp))
                            WikiCatalogJoints(metadata.anatomy.jointInvolvement, onNavigateToJoint)
                        }
                    }
                }
                val patternId = canonicalWikiLabPatternId(metadata.biomechanics.movementPatternId)
                val pattern = patternId?.let(WikiLabRepository::getPatternById)
                if (pattern != null) {
                    item {
                        Column {
                            WikiSectionHeader("Patrón de movimiento")
                            Spacer(Modifier.height(8.dp))
                            WikiLinkedLine(
                                label = pattern.name,
                                detail = "${metadata.biomechanics.movementPatternId} · ${bodyRegionLabel(metadata.biomechanics.bodyRegion)}",
                                onClick = { onNavigateToPattern?.invoke(pattern.id) },
                                clickable = onNavigateToPattern != null,
                            )
                        }
                    }
                }
                if (metadata.editorial.benefits.isNotEmpty()) {
                    item { WikiCatalogBulletSection("Beneficios", metadata.editorial.benefits) }
                }
                if (metadata.editorial.technique.isNotBlank()) {
                    item {
                        Column {
                            WikiSectionHeader("Técnica")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                metadata.editorial.technique,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                                color = Color.White.copy(alpha = 0.88f),
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
                if (metadata.coaching.setup.isNotEmpty()) {
                    item { WikiCatalogBulletSection("Preparación", metadata.coaching.setup) }
                }
                if (metadata.coaching.execution.isNotEmpty()) {
                    item { WikiCatalogBulletSection("Ejecución", metadata.coaching.execution) }
                }
                if (metadata.coaching.cues.isNotEmpty()) {
                    item { WikiCatalogBulletSection("Claves técnicas", metadata.coaching.cues) }
                }
                if (metadata.coaching.commonMistakes.isNotEmpty()) {
                    item { WikiCatalogBulletSection("Errores comunes", metadata.coaching.commonMistakes) }
                }
            } else {
                if (legacyMuscles.isNotEmpty()) {
                    item {
                        Column {
                            WikiSectionHeader("Músculos involucrados")
                            Spacer(Modifier.height(8.dp))
                            WikiMuscleInvolvement(legacyMuscles, onNavigateToMuscle)
                        }
                    }
                }
                exercise.anatomicalConsiderations?.let { considerations ->
                    item { WikiAnatomicalSectionBlock(considerations) }
                }
                exercise.commonMistakes?.let { mistakes ->
                    item { WikiLegacyMistakesBlock(mistakes) }
                }
            }

            item {
                Column {
                    WikiSectionHeader("Ejercicios relacionados")
                    Spacer(Modifier.height(8.dp))
                    ExerciseSimilarThreeBand(
                        info = exercise,
                        catalog = catalog,
                        relations = relations,
                        onOpenExercise = { id -> onNavigateToExercise?.invoke(id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showEditor) {
        SmartExerciseEditorDialog(
            initial = exercise,
            onSave = {
                CustomExerciseRepository.upsert(it)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
    if (confirmDelete) {
        KpknAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = "Eliminar ejercicio",
            text = "¿Eliminar «${exercise.name}»? Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {
                CustomExerciseRepository.delete(exercise.id)
                confirmDelete = false
                onBack()
            },
            dismissLabel = "Cancelar",
        )
    }
}

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = APRENDE_DIVIDER, thickness = 1.dp)
    }
}

@Composable
private fun WikiInfobox(
    exercise: ExerciseMuscleInfo,
    metadata: com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = APRENDE_PANEL_ELEVATED),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ficha técnica", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
            HorizontalDivider(color = APRENDE_DIVIDER)
            if (metadata != null) {
                InfoboxRow("Catálogo", metadata.identity.catalogRevision)
                InfoboxRow("Variante", metadata.display.displaySummary)
                InfoboxRow("Equipamiento", exercise.equipment ?: metadata.biomechanics.equipmentId)
                InfoboxRow("Región", bodyRegionLabel(metadata.biomechanics.bodyRegion))
                InfoboxRow("Cadena", kineticChainLabel(metadata.biomechanics.kineticChain))
                InfoboxRow("Lateralidad", lateralityLabel(metadata.biomechanics.laterality))
                InfoboxRow("Articulación", exercise.articulationType ?: "—")
            } else {
                exercise.category?.let { InfoboxRow("Categoría", it) }
                exercise.equipment?.let { InfoboxRow("Equipamiento", it) }
                exercise.type?.let { InfoboxRow("Mecánica", it) }
                exercise.tier?.let { InfoboxRow("Nivel", it) }
                exercise.force?.let { InfoboxRow("Fuerza", it) }
            }
        }
    }
}

@Composable
private fun InfoboxRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Medium, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun WikiCatalogMuscles(
    metadata: com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2,
    onNavigate: ((String) -> Unit)?,
) {
    val notes = metadata.anatomy.muscleNotes.associateBy { it.muscleId }
    val ids = buildList {
        addAll(metadata.anatomy.primaryMuscles)
        addAll(metadata.anatomy.secondaryMuscles)
        addAll(metadata.anatomy.stabilizerMuscles)
    }.distinct()
    ids.forEach { catalogId ->
        val wikiId = canonicalWikiLabMuscleIdFromCatalogId(catalogId)
        val entity = wikiId?.let(WikiLabRepository::getMuscleById)
        val label = entity?.name ?: catalogId
        val role = when {
            catalogId in metadata.anatomy.primaryMuscles -> "Principal"
            catalogId in metadata.anatomy.secondaryMuscles -> "Secundario"
            else -> "Estabilizador"
        }
        WikiLinkedLine(label, role + (notes[catalogId]?.note?.let { " · $it" } ?: ""), { if (wikiId != null) onNavigate?.invoke(wikiId) }, wikiId != null && onNavigate != null)
    }
}

@Composable
private fun WikiCatalogJoints(joints: List<JointInvolvementV2>, onNavigate: ((String) -> Unit)?) {
    joints.forEach { involvement ->
        val joint = WikiLabRepository.getJointById(involvement.jointId)
        WikiLinkedLine(
            label = joint?.name ?: involvement.jointId,
            detail = "${jointRoleLabel(involvement.role)} · ${involvement.actions.joinToString(", ")} · ${involvement.note}",
            onClick = { onNavigate?.invoke(involvement.jointId) },
            clickable = joint != null && onNavigate != null,
        )
    }
}

@Composable
private fun WikiLinkedLine(label: String, detail: String, onClick: () -> Unit, clickable: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif, color = if (clickable) APRENDE_LINK_COLOR else Color.White), fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.68f), lineHeight = 17.sp)
        HorizontalDivider(color = APRENDE_DIVIDER)
    }
}

@Composable
private fun WikiCatalogBulletSection(title: String, bullets: List<String>) {
    Column {
        WikiSectionHeader(title)
        Spacer(Modifier.height(8.dp))
        bullets.forEach { bullet ->
            Text("• $bullet", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.86f), lineHeight = 20.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun WikiMuscleInvolvement(muscles: List<InvolvedMuscle>, onNavigateToMuscle: ((String) -> Unit)?) {
    muscles.forEach { muscle ->
        val canonicalId = canonicalWikiLabMuscleId(muscle.muscle, muscle.emphasis)
        WikiLinkedLine(
            label = muscle.muscle,
            detail = when (muscle.role) {
                MuscleRole.PRIMARY -> "Principal"
                MuscleRole.SECONDARY -> "Secundario"
                MuscleRole.STABILIZER -> "Estabilizador"
                MuscleRole.NEUTRALIZER -> "Neutralizador"
            } + (muscle.biomechanicalReason?.let { " · $it" } ?: ""),
            onClick = { if (canonicalId != null) onNavigateToMuscle?.invoke(canonicalId) },
            clickable = canonicalId != null && onNavigateToMuscle != null,
        )
    }
}

@Composable
private fun WikiAnatomicalSectionBlock(considerations: List<AnatomicalConsideration>) {
    Column {
        WikiSectionHeader("Consideraciones anatómicas")
        Spacer(Modifier.height(8.dp))
        considerations.forEach { c ->
            Text("• ${c.trait}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White)
            Text(c.advice, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.78f), lineHeight = 18.sp, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun WikiLegacyMistakesBlock(mistakes: List<CommonMistake>) {
    Column {
        WikiSectionHeader("Errores comunes")
        Spacer(Modifier.height(8.dp))
        mistakes.forEach { mistake ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color(0xFFCF8F8F))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(mistake.mistake, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif), fontWeight = FontWeight.Bold, color = Color.White)
                    Text(mistake.correction, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), color = Color.White.copy(alpha = 0.76f))
                }
            }
        }
    }
}

private fun bodyRegionLabel(value: ExerciseBodyRegionV2): String = when (value) {
    ExerciseBodyRegionV2.UPPER -> "Tren superior"
    ExerciseBodyRegionV2.LOWER -> "Tren inferior"
    ExerciseBodyRegionV2.CORE -> "Core"
    ExerciseBodyRegionV2.FULL -> "Cuerpo completo"
}

private fun kineticChainLabel(value: ExerciseKineticChainV2): String = when (value) {
    ExerciseKineticChainV2.ANTERIOR -> "Anterior"
    ExerciseKineticChainV2.POSTERIOR -> "Posterior"
    ExerciseKineticChainV2.FULL -> "Completa"
}

private fun lateralityLabel(value: ExerciseLateralityV2): String = when (value) {
    ExerciseLateralityV2.BILATERAL -> "Bilateral"
    ExerciseLateralityV2.UNILATERAL -> "Unilateral"
    ExerciseLateralityV2.ALTERNATING -> "Alterna"
    ExerciseLateralityV2.NOT_APPLICABLE -> "No aplica"
}

private fun jointRoleLabel(value: JointRoleV2): String = when (value) {
    JointRoleV2.PRIMARY -> "Principal"
    JointRoleV2.SECONDARY -> "Secundaria"
    JointRoleV2.STABILIZER -> "Estabilizadora"
}
