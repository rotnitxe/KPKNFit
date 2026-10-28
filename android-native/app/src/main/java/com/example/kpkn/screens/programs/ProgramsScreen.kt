package com.example.kpkn.screens.programs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.components.HeroCoverKind
import com.example.kpkn.screens.programdetail.components.heroCoverKind
import com.example.kpkn.screens.programdetail.design.ProgramPhaseRibbon
import com.example.kpkn.screens.programs.importer.ImportHandoff
import com.example.kpkn.screens.programs.importer.ImportRequest
import com.example.kpkn.screens.programs.importer.ImportSourceSheet
import com.example.kpkn.screens.stories.ProgramShareSheet
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rememberProgramCover
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/** Peticiones de la hoja «Nuevo programa» desde fuera de la pantalla (p. ej. desde el menú de Inicio). */
object ProgramCreationRequests {
    @Volatile var openSheet: Boolean = false
}

@Composable
fun ProgramsScreen(
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onImportProgram: () -> Unit,
    onEditProgram: (String) -> Unit,
    viewModel: ProgramsViewModel = viewModel(),
    openCreateSheetOnStart: Boolean = false,
    onCreateSheetOpened: () -> Unit = {},
    onSelectPlan: ((CatalogEntry) -> Unit)? = null,
    /** Abre un concepto de «Conceptos clave» desde la hoja «Cómo funciona» de la biblioteca; null = sin enlace. */
    onOpenConcept: ((String) -> Unit)? = null,
    onCreateTailoredProgram: (() -> Unit)? = null,
) {
    val programs by viewModel.programs.collectAsState()
    val archivedPrograms by viewModel.archivedPrograms.collectAsState()
    val activeProgram by viewModel.activeProgram.collectAsState()
    val activeSummary by viewModel.activeSummary.collectAsState()
    val inactivePrograms by viewModel.inactivePrograms.collectAsState()
    val programQueue by viewModel.programQueue.collectAsState()
    val isFeaturedPaused by viewModel.isFeaturedPaused.collectAsState()
    val lastTrainedOn by viewModel.lastTrainedOn.collectAsState()
    var moreProgram by remember { mutableStateOf<Program?>(null) }
    var shareProgram by remember { mutableStateOf<Program?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var deleteRequest by remember { mutableStateOf<ProgramDeleteRequest?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var showBlankSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var selectedProtocol by remember { mutableStateOf<com.example.kpkn.data.protocols.Protocol?>(null) }
    var protocolForTm by remember { mutableStateOf<com.example.kpkn.data.protocols.Protocol?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }
    val createScope = rememberCoroutineScope()

    LaunchedEffect(openCreateSheetOnStart, Unit) {
        if (openCreateSheetOnStart || ProgramCreationRequests.openSheet) {
            showCreateSheet = true
            ProgramCreationRequests.openSheet = false
            onCreateSheetOpened()
        }
    }

    val programsAdapt = LocalViewportAdapt.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp.adapt(programsAdapt),
            end = 20.dp.adapt(programsAdapt),
            bottom = 120.dp.adapt(programsAdapt),
        ),
    ) {
        item(key = "cabecera") {
            ProgramsHeader(
                onNew = { showCreateSheet = true },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp.adapt(programsAdapt), bottom = 4.dp),
            )
        }

        if (programs.isEmpty()) {
            // Sin programas sin archivar: el módulo de primer programa; los archivados siguen debajo (abajo del todo).
            item(key = "vacio") {
                ProgramsEmptyModule(
                    onCreateTailored = onCreateTailoredProgram ?: { showCreateSheet = true },
                    onSeeAll = { showCreateSheet = true },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            activeProgram?.let { program ->
                item(key = "en-curso-titulo") { ProgramGroupLabel(text = "En curso") }
                item(key = "en-curso") {
                    ActiveProgramCard(
                        program = program,
                        summary = activeSummary?.takeIf { it.programId == program.id },
                        isPaused = isFeaturedPaused,
                        viewModel = viewModel,
                        onNavigate = onNavigateToProgram,
                        onResume = { viewModel.resumeProgram() },
                        onLongPress = { moreProgram = program },
                    )
                }
            }

            if (programQueue.isNotEmpty()) {
                item(key = "en-cola") {
                    ProgramQueueSection(
                        queue = programQueue,
                        viewModel = viewModel,
                        onMove = viewModel::moveQueuedProgram,
                        onRemove = viewModel::removeFromQueue,
                    )
                }
            }

            if (inactivePrograms.isNotEmpty()) {
                item(key = "tus-programas-titulo") { ProgramGroupLabel(text = "Tus programas") }
                items(inactivePrograms, key = { it.id }) { program ->
                    InactiveProgramCard(
                        program = program,
                        viewModel = viewModel,
                        completedOn = if (programIsFinished(program)) lastTrainedOn[program.id] else null,
                        onNavigate = onNavigateToProgram,
                        onMore = { moreProgram = program },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }

        if (archivedPrograms.isNotEmpty()) {
            item(key = "archivados") {
                ArchivedProgramsRow(
                    count = archivedPrograms.size,
                    onClick = { showArchived = true },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item(key = "final") { Spacer(modifier = Modifier.height(16.dp)) }
    }

    moreProgram?.let { program ->
        val isActive = activeProgram?.id == program.id
        val isQueued = programQueue.any { it.id == program.id }
        val stats = viewModel.getProgramStats(program)
        val state = programStateWord(
            program = program,
            isActive = isActive,
            isPaused = isFeaturedPaused,
            lastTrained = lastTrainedOn[program.id],
        )
        ProgramMoreSheet(
            program = program,
            subtitle = "$state · ${pluralSemanas(stats.weeks)}",
            canActivate = !isActive,
            canQueue = !isActive && !isQueued,
            onActivate = {
                moreProgram = null
                viewModel.startProgram(program.id)
            },
            onEdit = {
                moreProgram = null
                onEditProgram(program.id)
            },
            onQueue = {
                moreProgram = null
                viewModel.addToQueue(program.id)
            },
            onShare = {
                moreProgram = null
                shareProgram = program
            },
            onArchive = {
                moreProgram = null
                viewModel.archiveProgram(program.id)
            },
            onDelete = {
                moreProgram = null
                deleteRequest = ProgramDeleteRequest(program = program, permanent = false)
            },
            onDismiss = { moreProgram = null },
        )
    }

    shareProgram?.let { prog ->
        val logs by viewModel.history.collectAsState()
        ProgramShareSheet(program = prog, logs = logs.filter { it.programId == prog.id }, onDismiss = { shareProgram = null })
    }

    // P-D3: la hoja de Archivados se cierra sola al quedarse sin programas (restaurar o borrar el último).
    LaunchedEffect(showArchived, archivedPrograms.isEmpty()) {
        if (showArchived && archivedPrograms.isEmpty()) showArchived = false
    }
    if (showArchived) {
        ArchivedProgramsSheet(
            programs = archivedPrograms,
            onRestore = { program -> viewModel.restoreArchivedProgram(program.id) },
            onDelete = { program -> deleteRequest = ProgramDeleteRequest(program = program, permanent = true) },
            onDismiss = { showArchived = false },
        )
    }

    // Va después de las hojas para que la confirmación quede encima de ellas.
    deleteRequest?.let { request ->
        ProgramDeleteDialog(
            program = request.program,
            onConfirm = {
                if (request.permanent) {
                    viewModel.permanentlyDeleteProgram(request.program.id)
                } else {
                    viewModel.deleteProgram(request.program.id)
                }
                deleteRequest = null
            },
            onDismiss = { deleteRequest = null },
        )
    }

    if (showCreateSheet) {
        NewProgramSheet(
            onDismiss = { showCreateSheet = false },
            onTailored = onCreateTailoredProgram?.let { tailored ->
                {
                    showCreateSheet = false
                    tailored()
                }
            },
            onCatalog = {
                showCreateSheet = false
                showCatalog = true
            },
            onImport = {
                showCreateSheet = false
                showImportSheet = true
            },
            onBlank = {
                showCreateSheet = false
                showBlankSheet = true
            },
        )
    }
    // «Importar» es una hoja con filas sobre la lista: elegir una fuente deja la petición y abre la lectura.
    if (showImportSheet) {
        val startImport: (ImportRequest) -> Unit = { request ->
            ImportHandoff.pending = request
            showImportSheet = false
            onImportProgram()
        }
        ImportSourceSheet(
            onDismiss = { showImportSheet = false },
            onFile = { uri -> startImport(ImportRequest.File(uri)) },
            onPhoto = { uri, taken -> startImport(ImportRequest.Photo(uri, taken)) },
            onPaste = { startImport(ImportRequest.PasteText) },
        )
    }
    // La página «Catálogo» va por encima de Programas (no es una ruta): lo que antes hacía la hoja de la biblioteca.
    if (showCatalog) {
        ProgramCatalogPage(
            onBack = { showCatalog = false },
            hasPlanAction = onSelectPlan != null,
            onCreateFromTemplate = { template ->
                createScope.launch {
                    when (val outcome = viewModel.createProgramFromTemplateGated(template.id)) {
                        is ProgramsViewModel.TemplateApplyOutcome.Created -> {
                            showCatalog = false
                            onNavigateToProgram(outcome.programId)
                        }
                    }
                }
            },
            onSelectProtocol = { protocol ->
                showCatalog = false
                selectedProtocol = protocol
            },
            onSelectPlan = onSelectPlan?.let { select ->
                { entry ->
                    showCatalog = false
                    select(entry)
                }
            },
            onOpenConcept = onOpenConcept?.let { open ->
                { conceptId ->
                    showCatalog = false
                    open(conceptId)
                }
            },
        )
    }
    if (showBlankSheet) {
        BlankProgramSheet(
            suggestedName = viewModel.suggestedBlankProgramName(),
            onDismiss = { showBlankSheet = false },
            onCreate = { name, mode, coverImage ->
                val id = viewModel.createBlankProgram(name = name, mode = mode, coverImage = coverImage)
                showBlankSheet = false
                onNavigateToProgram(id)
            },
        )
    }
    selectedProtocol?.let { protocol ->
        ProtocolDetailSheet(
            protocol = protocol,
            onDismiss = { selectedProtocol = null },
            onContinue = {
                selectedProtocol = null
                protocolForTm = protocol
            },
        )
    }
    // La unidad de los máximos es la de Ajustes, una sola en toda la app.
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    protocolForTm?.let { protocol ->
        TrainingMaxWizard(
            initial = viewModel.estimatedProfileFromHistory(),
            initialFromHistory = true,
            trainingMaxPercent = protocol.recipe?.trainingMaxPercent ?: 0.90,
            unit = if (settings.weightUnit == WeightUnit.LBS) MarksMath.UNIT_LB else MarksMath.UNIT_KG,
            // Cerrar (arrastrar, atrás) ya no crea el programa: solo lo crean «Crear sin TM» y «Aplicar y crear».
            onDismiss = { protocolForTm = null },
            skipLabel = "Crear sin TM",
            onSkip = {
                val id = viewModel.createProgramFromProtocol(protocol.id)
                protocolForTm = null
                if (id != null) {
                    onNavigateToProgram(id)
                } else {
                    templateError = "No se pudo crear el programa. Revisa la receta elegida."
                }
            },
            onConfirm = { profile ->
                val id = viewModel.createProgramFromProtocol(protocol.id, profile)
                protocolForTm = null
                if (id != null) {
                    onNavigateToProgram(id)
                } else {
                    templateError = "No se pudo crear el programa. Revisa la receta elegida."
                }
            },
        )
    }
    templateError?.let { message ->
        KpknAlertDialog(
            onDismissRequest = { templateError = null },
            title = "No se pudo aplicar la plantilla",
            text = message,
            confirmLabel = "Entendido",
            onConfirm = { templateError = null },
        )
    }
}

/**
 * La cabecera de Programas: el título y la píldora «Nuevo» con su plus. Va en una línea mientras cabe; con letra grande, «Nuevo» baja
 * a su propia fila. El título no se parte por dentro de la palabra.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramsHeader(onNew: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "Programas",
                style = ProgramsTitleStyle,
                color = WizardColors.text,
                maxLines = 1,
                softWrap = false,
            )
        }
        ProgramSmallPill(text = "Nuevo", onClick = onNew, icon = KpknLineIcons.Plus)
    }
}

/**
 * La píldora pequeña del lienzo (`.pill.sm`): 40 dp de alto, texto de 13 sp semibold y, si hay [icon], de 16 dp delante con 8 dp de
 * hueco. Rellena (`pri`) es de tinta con texto negro; sin rellenar (`sec`) lleva el filete de tinta al 24 %. Su zona táctil mide 48 dp.
 */
@Composable
internal fun ProgramSmallPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (filled) WizardColors.ctaContent else WizardColors.text
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .height(40.dp)
                .clip(KpknShape.pill)
                .background(if (filled) WizardColors.text else Color.Transparent, KpknShape.pill)
                .then(if (!filled) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill) else Modifier)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = KpknType.chip, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** El módulo de «Empieza tu primer programa»: sin programas sin archivar. */
@Composable
private fun ProgramsEmptyModule(
    onCreateTailored: () -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProgramModuleSurface(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(text = "Empieza tu primer programa", style = ProgramEmptyTitleStyle, color = WizardColors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "KPKN puede armarlo con tu material y tus días, o puedes elegir uno conocido.",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(18.dp))
        KpknPrimaryPill(
            text = "Crear a mi medida",
            onClick = onCreateTailored,
            modifier = Modifier.fillMaxWidth(),
            icon = ProgramLineIcons.Spark,
        )
        Spacer(Modifier.height(8.dp))
        KpknSecondaryPill(text = "Ver todas las opciones", onClick = onSeeAll, modifier = Modifier.fillMaxWidth())
    }
}

/** La tarjeta del programa en curso o pausado: portada arriba, nombre, semana y cinta, y sus dos acciones. */
@Composable
private fun ActiveProgramCard(
    program: Program,
    summary: ActiveProgramSummary?,
    isPaused: Boolean,
    viewModel: ProgramsViewModel,
    onNavigate: (String) -> Unit,
    onResume: () -> Unit,
    onLongPress: () -> Unit,
) {
    val stats = remember(program) { viewModel.getProgramStats(program) }
    // Portada propia (`assets/program_covers/`) si el programa la tiene: va en el arte de arriba.
    val coverWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val cover = rememberProgramCover(programCoverEntryIdOf(program.coverImage), coverWidthPx)
    val coverPainter = remember(cover) { cover?.let { BitmapPainter(it) } }
    // Portada de estudio (`kpkn-studio://`): la misma que pinta la cabecera de Program Detail, sin animación.
    val studioIdentity = remember(program.id, program.structureTemplateId, program.coverImage) {
        if (heroCoverKind(program.coverImage, hasBundledCover = false) !in PROGRAM_ART_COVERS) null else coverIdentityForProgram(program)
    }
    val todaySession = summary?.todaySessionName
    val ribbon = summary?.ribbon.orEmpty()

    ProgramModuleSurface(
        modifier = Modifier.padding(bottom = 12.dp),
        onClick = { onNavigate(program.id) },
        onLongClick = onLongPress,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(programCoverBrush(program.coverImage)),
        ) {
            studioIdentity?.let { identity ->
                CoverStudioArt(identity = identity, modifier = Modifier.matchParentSize(), animate = false, reduced = true)
            }
            coverPainter?.let { painter ->
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // El arte se funde con la tarjeta por abajo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, KpknSurface.module))),
            )
            ProgramStatusChip(
                label = if (isPaused) "Pausado" else "Activo",
                dot = if (isPaused) SymbolPalette.energia else WizardColors.done,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        ) {
            Text(
                text = program.name,
                style = ProgramActiveNameStyle,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = summary?.progressLabel ?: programListMeta(program, stats),
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            if (ribbon.size > 1) {
                Spacer(Modifier.height(12.dp))
                ProgramPhaseRibbon(weeks = ribbon)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (todaySession != null) {
                    // Igual que el play de hoy de antes: reanuda si está pausado; si no, abre el programa.
                    ProgramSmallPill(
                        text = "Hoy: $todaySession",
                        onClick = { if (isPaused) onResume() else onNavigate(program.id) },
                        modifier = Modifier.weight(1f),
                        icon = KpknLineIcons.PlayFilled,
                        fullWidth = true,
                    )
                    ProgramSmallPill(text = "Ver programa", onClick = { onNavigate(program.id) }, filled = false)
                } else {
                    // P-D2: un día sin sesión, «Ver programa» es la acción primaria y ocupa todo el ancho.
                    ProgramSmallPill(
                        text = "Ver programa",
                        onClick = { onNavigate(program.id) },
                        modifier = Modifier.weight(1f),
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

/** La píldora de estado sobre la portada: punto y palabra, en negro al 73 %. */
@Composable
private fun ProgramStatusChip(label: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(KpknShape.pill)
            .background(Color.Black.copy(alpha = 0.73f), KpknShape.pill)
            .height(30.dp)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = KpknType.chip, color = WizardColors.text)
    }
}

/** «En cola»: el orden de salida, que se cambia arrastrando el asa de cada programa. Se quita con la cruz. */
@Composable
private fun ProgramQueueSection(
    queue: List<Program>,
    viewModel: ProgramsViewModel,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    ProgramGroupLabel(text = "En cola", trailing = "Empieza al terminar")
    val spacing = 10.dp
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    // El alto de cada fila, para saber cuántos puestos se ha movido la que se arrastra.
    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        queue.forEachIndexed { index, program ->
            key(program.id) {
                val dragging = draggingId == program.id
                ProgramQueueRow(
                    index = index,
                    count = queue.size,
                    program = program,
                    stats = viewModel.getProgramStats(program),
                    dragging = dragging,
                    dragOffsetPx = if (dragging) dragOffset else 0f,
                    onHeight = { heights[program.id] = it },
                    onDragStart = {
                        draggingId = program.id
                        dragOffset = 0f
                    },
                    onDrag = { dy -> dragOffset += dy },
                    onDragEnd = {
                        val steps = queueDragSteps(
                            offsetPx = dragOffset,
                            index = index,
                            heightsPx = queue.map { (heights[it.id] ?: 0).toFloat() },
                            spacingPx = spacingPx,
                        )
                        draggingId = null
                        dragOffset = 0f
                        if (steps != 0) onMove(program.id, steps)
                    },
                    onMove = onMove,
                    onRemove = onRemove,
                )
            }
        }
    }
}

/**
 * Cuántos puestos se mueve una fila de la cola que se arrastra [offsetPx] hacia abajo (positivo) o arriba (negativo): cada fila que
 * cruza más de la mitad cuenta un puesto. Se queda dentro de la cola. [heightsPx] es el alto de cada fila, en el orden de la cola.
 */
internal fun queueDragSteps(offsetPx: Float, index: Int, heightsPx: List<Float>, spacingPx: Float): Int {
    if (heightsPx.isEmpty() || index !in heightsPx.indices) return 0
    var remaining = kotlin.math.abs(offsetPx)
    var steps = 0
    if (offsetPx > 0f) {
        var next = index + 1
        while (next < heightsPx.size) {
            val stride = heightsPx[next] + spacingPx
            if (remaining <= stride / 2f) break
            remaining -= stride
            steps++
            next++
        }
        return steps
    }
    var previous = index - 1
    while (previous >= 0) {
        val stride = heightsPx[previous] + spacingPx
        if (remaining <= stride / 2f) break
        remaining -= stride
        steps--
        previous--
    }
    return steps
}

@Composable
private fun ProgramQueueRow(
    index: Int,
    count: Int,
    program: Program,
    stats: ProgramStats,
    dragging: Boolean,
    dragOffsetPx: Float,
    onHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    // TalkBack no arrastra: las mismas dos acciones que antes eran flechas pasan a acciones de accesibilidad de la fila.
    val moveActions = buildList {
        if (index > 0) add(CustomAccessibilityAction("Subir ${program.name}") { onMove(program.id, -1); true })
        if (index < count - 1) add(CustomAccessibilityAction("Bajar ${program.name}") { onMove(program.id, 1); true })
    }
    ProgramModuleSurface(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetPx
                if (dragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .onSizeChanged { onHeight(it.height) }
            .semantics { customActions = moveActions },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El asa: se reordena arrastrándola. Zona táctil de 44 dp, como el `ib` del lienzo.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "Reordenar ${program.name}" }
                    .pointerInput(program.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Grip,
                    contentDescription = null,
                    tint = WizardColors.textFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            ProgramCoverThumb(program = program, width = 40.dp, height = 50.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1} · ${program.name}",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${pluralSemanas(stats.weeks)} · ${pluralSesiones(stats.sessions)}",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Quitar ${program.name} de la cola",
                onClick = { onRemove(program.id) },
            )
        }
    }
}

/** Una tarjeta de «Tus programas»: miniatura, nombre, meta, estado y su ⋯. Pulsarla abre el programa; mantenerla, su ⋯. */
@Composable
private fun InactiveProgramCard(
    program: Program,
    viewModel: ProgramsViewModel,
    completedOn: LocalDate?,
    onNavigate: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = remember(program) { viewModel.getProgramStats(program) }
    val completed = programIsFinished(program)
    val status = programListStatusLabel(completed = completed, completedOn = completedOn)
    ProgramModuleSurface(
        modifier = modifier,
        onClick = { onNavigate(program.id) },
        onLongClick = onMore,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgramCoverThumb(program = program, width = 64.dp, height = 80.dp, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = program.name,
                    style = ProgramRowNameStyle,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = programListMeta(program, stats),
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (completed) WizardColors.textFaint else SymbolPalette.energia),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = status,
                        style = KpknType.caption,
                        color = if (completed) WizardColors.textMuted else SymbolPalette.energia,
                    )
                }
            }
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones de ${program.name}",
                onClick = onMore,
            )
        }
    }
}

/** La fila «Archivados · N», que abre su hoja. Sale siempre que haya archivados, también sin programas activos. */
@Composable
private fun ArchivedProgramsRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ProgramModuleSurface(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Archivados · $count", style = KpknType.body, color = WizardColors.textMuted)
            Icon(
                imageVector = KpknLineIcons.ChevronRight,
                contentDescription = null,
                tint = WizardColors.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** El encabezado de sección: la etiqueta en mayúsculas, y a la derecha una aclaración opcional. */
@Composable
private fun ProgramGroupLabel(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text.uppercase(Locale("es")), style = KpknType.eyebrow, color = WizardColors.textFaint)
        if (trailing != null) {
            Text(text = trailing, style = KpknType.caption, color = WizardColors.textFaint)
        }
    }
}

/**
 * El módulo de la lista: relleno `module`, filete de 1 dp y radio 24. Con [onClick] es un botón entero, sin ripple; con
 * [onLongClick] responde a la pulsación larga, como hacía la lista de antes.
 */
@Composable
private fun ProgramModuleSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val interactive = onClick != null || onLongClick != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpknShape.module)
            .background(KpknSurface.module, KpknShape.module)
            .border(1.dp, KpknSurface.moduleBorder, KpknShape.module)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * La miniatura de un programa: su portada de estudio, su foto propia o, si no tiene, el degradado de siempre. [width] × [height]
 * es el tamaño de la miniatura en pantalla.
 */
@Composable
internal fun ProgramCoverThumb(
    program: Program,
    width: Dp,
    height: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val cover = rememberProgramCover(programCoverEntryIdOf(program.coverImage), widthPx)
    val coverPainter = remember(cover) { cover?.let { BitmapPainter(it) } }
    val studioIdentity = remember(program.id, program.structureTemplateId, program.coverImage) {
        if (heroCoverKind(program.coverImage, hasBundledCover = false) !in PROGRAM_ART_COVERS) null else coverIdentityForProgram(program)
    }
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(programCoverBrush(program.coverImage)),
    ) {
        studioIdentity?.let { identity ->
            CoverStudioArt(identity = identity, modifier = Modifier.matchParentSize(), animate = false, reduced = true)
        }
        coverPainter?.let { painter ->
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** La palabra de estado de la hoja ⋯: «Activo» o «Pausado» si es el de en curso; si no, «Borrador» o «Terminado · d MMM». */
private fun programStateWord(
    program: Program,
    isActive: Boolean,
    isPaused: Boolean,
    lastTrained: LocalDate?,
): String = when {
    isActive -> if (isPaused) "Pausado" else "Activo"
    else -> programListStatusLabel(completed = programIsFinished(program), completedOn = lastTrained)
}

/** Portadas que dibuja el motor en la lista: las de estudio y líneas, y sin portada la de líneas por defecto (como la cabecera). */
private val PROGRAM_ART_COVERS = setOf(HeroCoverKind.STUDIO, HeroCoverKind.GRADIENT)

internal fun programCoverBrush(coverImage: String?): Brush {
    val colors = when (coverImage) {
        "gradient://lagoon" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))
        "gradient://velvet" -> listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))
        "gradient://forest" -> listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))
        else -> listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))
    }
    return Brush.linearGradient(colors)
}

private val ProgramsTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.8).sp,
)

private val ProgramActiveNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.5).sp,
)

internal val ProgramRowNameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    lineHeight = 21.sp,
    letterSpacing = (-0.3).sp,
)

private val ProgramEmptyTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
)
