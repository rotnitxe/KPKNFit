package com.example.kpkn.screens.sessioneditor.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import com.example.kpkn.domain.exercises.exerciseHistorySummary
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.screens.sessioneditor.design.customExerciseCoverModel
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.ExerciseAlbumSheet
import com.example.kpkn.screens.workout.components.WorkoutMediaThumb
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ExerciseFichaContext { CATALOG, REPLACEMENT, SESSION, WIZARD }

internal fun exerciseFichaFooter(context: ExerciseFichaContext, selected: Boolean, show: Boolean = true): String? = when {
    !show || context == ExerciseFichaContext.SESSION || context == ExerciseFichaContext.WIZARD -> null
    context == ExerciseFichaContext.REPLACEMENT -> "Elegir"
    selected -> "Quitar"
    else -> "Añadir"
}

/** Una ficha para catálogo, editor, sesión y wizard. El origen conserva sus acciones y su selección exacta. */
@Composable
internal fun ExerciseFicha(
    model: CatalogDetailModel,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit = {},
    onSelectOption: (String, String) -> Unit = { _, _ -> },
    onImplementationSettled: (String) -> Unit = {},
    showFooter: Boolean = true,
    onShowHistory: (() -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val scope = rememberCoroutineScope()
    val entry = remember { Animatable(if (reduced) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (!reduced) entry.animateTo(1f, tween(220))
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!reduced) entry.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
    }
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    BackHandler { dismiss() }
    val repository = remember(model.context) { if (model.context == ExerciseFichaContext.WIZARD) null else runCatching { ProgramRepository.getInstance() }.getOrNull() }
    val empty = remember { MutableStateFlow<List<WorkoutLog>>(emptyList()) }
    val history by (repository?.history ?: empty).collectAsStateWithLifecycle()
    val mediaRepository = remember(model.context) { if (model.context == ExerciseFichaContext.WIZARD) null else runCatching { WorkoutMediaRepository.getInstance() }.getOrNull() }
    var resolved by remember(model.identityKeys) { mutableStateOf(model.historySummary) }
    var media by remember(model.identityKeys) { mutableStateOf(model.media) }
    var album by remember { mutableStateOf(false) }
    var mediaVersion by remember { mutableStateOf(0) }
    LaunchedEffect(history, model.identityKeys, model.context) {
        resolved = if (model.context == ExerciseFichaContext.WIZARD) null else withContext(Dispatchers.Default) {
            exerciseHistorySummary(history, model.identityKeys) ?: model.historySummary
        }
    }
    LaunchedEffect(model.identityKeys, model.context, mediaVersion) {
        if (model.context != ExerciseFichaContext.WIZARD && mediaRepository != null && model.identityKeys.isNotEmpty()) {
            try {
                media = mediaRepository.listForExerciseKeys(model.identityKeys.toList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { /* Conserva la última lectura. */ }
        }
    }
    val coverContext = LocalContext.current
    var customCover by remember(model.muscleInfo, model.customCover) {
        mutableStateOf(model.customCover ?: model.muscleInfo?.takeIf { it.isCustom }?.let { com.example.kpkn.screens.sessioneditor.design.CustomCoverModel() })
    }
    LaunchedEffect(model.muscleInfo, mediaVersion) {
        model.muscleInfo?.takeIf { it.isCustom || it.id.startsWith("custom:") }?.let {
            customCover = customExerciseCoverModel(coverContext, it)
        }
    }
    KpknPortal {
        ExerciseFichaContent(
            model = model.copy(historySummary = resolved, media = media, customCover = customCover),
            onDismiss = dismiss,
            onToggleSelection = onToggleSelection,
            onSelectOption = onSelectOption,
            onImplementationSettled = onImplementationSettled,
            showFooter = showFooter,
            onShowHistory = onShowHistory?.let { action -> { onDismiss(); action() } },
            onOpenAlbum = if (mediaRepository != null) ({ album = true }) else null,
            modifier = Modifier.graphicsLayer { translationY = entry.value * size.height },
        )
    }
    if (album) {
        ExerciseAlbumSheet(
            exerciseName = model.name,
            keys = model.identityKeys.toList(),
            onDismiss = { album = false; mediaVersion += 1 },
        )
    }
}

/** Cuerpo real, también utilizable en render e instrumentación sin repositorios. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseFichaContent(
    model: CatalogDetailModel,
    onDismiss: () -> Unit,
    onToggleSelection: () -> Unit,
    onSelectOption: (String, String) -> Unit,
    onImplementationSettled: (String) -> Unit,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
    onShowHistory: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    initialScrollDp: Int = 0,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { initialScrollDp.dp.roundToPx() })
    val reduced = wizardReducedMotion()
    val effects = exerciseFichaEffects(scroll.value / density.density, reduced, Build.VERSION.SDK_INT >= 31)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    // Reserva fuera del viewport el espacio del título fijo para evitar que el cuerpo pase debajo.
    val fixedHeadingSpace = (insets.calculateTopPadding() + 60.dp) * effects.headerAlpha
    val implement = model.axes.firstOrNull { it.axis == "implement" }?.options.orEmpty()
    val config = model.axes.filter { it.axis != "implement" }
    val footer = exerciseFichaFooter(model.context, model.isSelected, showFooter)?.let { default -> model.selectionLabel ?: default }
    val wizard = model.context == ExerciseFichaContext.WIZARD
    val catalogHero = selectedCatalogImageVariantIndex(model.imageVariants, model.selectedImplementation) != null
    val hasHero = model.customCover != null || catalogHero
    val heroHeight = 400.dp
    var knowledge by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    Box(
        modifier = modifier.fillMaxSize().zIndex(350f).background(Color.Black)
            .semantics { paneTitle = model.name }
            .testTag(if (wizard) "setup-exercise-info" else "exercise-ficha"),
    ) {
        Column(
            Modifier.fillMaxSize()
                .padding(top = fixedHeadingSpace)
                .verticalScroll(scroll)
                .testTag("exercise-ficha-scroll"),
        ) {
            if (hasHero) {
                Box(Modifier.fillMaxWidth().height(heroHeight - 80.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(heroHeight)
                            .graphicsLayer {
                                translationY = scroll.value.toFloat()
                                scaleX = effects.scale
                                scaleY = effects.scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.4f)
                            }
                            .then(if (effects.blurDp > 0f) Modifier.blur(effects.blurDp.dp) else Modifier)
                            .testTag(if (wizard) "setup-exercise-info-image" else "exercise-ficha-photo"),
                    ) {
                        val cover = model.customCover
                        if (cover != null) Box(Modifier.fillMaxWidth().height(heroHeight), contentAlignment = Alignment.Center) {
                            when {
                                cover.photoFile != null -> LocalMediaImage(rememberLocalMediaImageSource(cover.photoFile), "Foto privada del ejercicio",
                                    Modifier.fillMaxSize(), ContentScale.Crop)
                                cover.imageResId != null -> Image(painterResource(cover.imageResId), "Imagen de referencia", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                else -> PatternGlyph(exercisePatternOf(model.name), SymbolPalette.musculo, Modifier.size(96.dp))
                            }
                        } else CatalogExerciseImageCarousel(
                            variants = model.imageVariants,
                            selectedImplementation = model.selectedImplementation,
                            onImplementationSettled = onImplementationSettled,
                            implementOptions = implement,
                            onSelectImplementation = { onSelectOption("implement", it) },
                            modifier = Modifier.fillMaxWidth(), photoHeight = heroHeight,
                            showImplementPills = false,
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = effects.darkness)))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                    }
                }
            } else Spacer(Modifier.height(insets.calculateTopPadding() + 56.dp))
            if (implement.size > 1) {
                CatalogImplementPills(
                    options = implement, shownValue = model.selectedImplementation, haze = remember { HazeState() },
                    onSelect = { onSelectOption("implement", it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF0E0E0E))
                    .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = if (footer != null) 112.dp else 32.dp)
                    .padding(bottom = insets.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.graphicsLayer { alpha = effects.titleAlpha }.testTag("exercise-ficha-large-heading"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.name, style = KpknType.title, color = WizardColors.text,
                        modifier = Modifier.testTag("exercise-ficha-large-title"))
                    model.caption?.takeIf { it.isNotBlank() }?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                    model.contextLine?.takeIf { it.isNotBlank() }?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                }
                if (!wizard) {
                    Text(model.customCover?.provenance ?: if (catalogHero) "Imagen técnica del catálogo" else "Sin imagen revisada para esta configuración.",
                        style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.testTag("exercise-ficha-image-provenance"))
                    model.customCover?.unavailableReason?.let {
                        Text(it, style = KpknType.caption, color = SymbolPalette.energia, modifier = Modifier.testTag("exercise-ficha-image-abstention"))
                    }
                    if (model.muscleInfo?.isCustom == true) {
                        Text(if (model.muscleInfo.creationReference?.confirmed == true)
                            "Referencia de creación confirmada; puede incluir ajustes tuyos."
                            else "Datos propios sin una configuración de referencia confirmada.",
                            style = KpknType.caption, color = WizardColors.textMuted,
                            modifier = Modifier.testTag("exercise-ficha-data-provenance"))
                    }
                }
                model.patternId?.let(::canonicalPatternKnowledge)?.let { pattern ->
                    KpknChip(text = "Patrón · ${pattern.name}", selected = false, onClick = { knowledge = pattern })
                }
                if (!wizard && (model.historySummary != null || model.media.isNotEmpty())) {
                    FichaHistory(model, onShowHistory, onOpenAlbum)
                }
                model.muscleInfo?.takeIf { it.involvedMuscles.isNotEmpty() }?.let { info ->
                    Box(Modifier.testTag(if (wizard) "setup-exercise-info-muscles" else "exercise-ficha-muscles")) {
                        CatalogMusclesModule(exercise = info, onKnowledge = { knowledge = it })
                    }
                }
                model.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Box(Modifier.testTag(if (wizard) "setup-exercise-info-description" else "exercise-ficha-description")) {
                        CatalogDescription(description)
                    }
                }
                if (model.setupCues.isNotEmpty()) {
                    Column(Modifier.testTag("exercise-ficha-setup"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Preparación", style = KpknType.bodyStrong, color = WizardColors.text)
                        FichaCues(model.setupCues)
                    }
                }
                if (model.executionCues.isNotEmpty()) {
                    Column(Modifier.testTag("exercise-ficha-execution"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ejecución", style = KpknType.bodyStrong, color = WizardColors.text)
                        FichaCues(model.executionCues)
                    }
                }
                if (config.isNotEmpty() || !model.configurationLine.isNullOrBlank()) {
                    KpknModule {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Configuración", style = KpknType.bodyStrong, color = WizardColors.text)
                            config.forEach { axis ->
                                Text(axis.label, style = KpknType.caption, color = WizardColors.textMuted)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    axis.options.forEach { option ->
                                        KpknChip(text = option.label, selected = option.selected, enabled = option.enabled, onClick = { onSelectOption(axis.axis, option.value) })
                                    }
                                }
                            }
                            model.configurationLine?.let { Text(it, style = KpknType.caption, color = if (model.configurationIncomplete) SymbolPalette.energia else WizardColors.textMuted) }
                        }
                    }
                }
                if (model.joints.isNotEmpty()) {
                    KpknModule(modifier = Modifier.testTag(if (wizard) "setup-exercise-info-joints" else "exercise-ficha-joints")) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Articulaciones", style = KpknType.bodyStrong, color = WizardColors.text)
                            JointInvolvementSection(model.joints, onKnowledge = { knowledge = it })
                        }
                    }
                }
            }
        }
        if (effects.headerAlpha > 0f) {
            Box(Modifier.fillMaxWidth().testTag("exercise-ficha-fixed-heading").background(Color(0xB80E0E0E)).graphicsLayer { alpha = effects.headerAlpha }
                .padding(top = insets.calculateTopPadding()).height(60.dp).padding(start = 20.dp, end = 76.dp), contentAlignment = Alignment.CenterStart) {
                Text(model.name, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        KpknIconButton(
            icon = KpknLineIcons.Close, contentDescription = "Cerrar definición", onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = insets.calculateTopPadding() + 8.dp, end = 12.dp)
                .testTag(if (wizard) "setup-exercise-info-close" else "exercise-ficha-close"),
        )
        if (footer != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0E0E0E))))
                .padding(horizontal = 16.dp).padding(top = 22.dp, bottom = insets.calculateBottomPadding() + 16.dp)) {
                KpknPrimaryPill(text = footer, onClick = onToggleSelection, enabled = model.canSelect, modifier = Modifier.fillMaxWidth().testTag("exercise-ficha-select"))
            }
        }
    }
    knowledge?.let { CanonicalKnowledgeOverlay(knowledge = it, onDismiss = { knowledge = null }) }
}

@Composable
private fun FichaCues(cues: List<String>) {
    cues.forEachIndexed { index, cue ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${index + 1}", style = KpknType.chip, color = SymbolPalette.musculo, modifier = Modifier.size(26.dp))
            Text(cue, style = KpknType.body, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FichaHistory(model: CatalogDetailModel, onShowHistory: (() -> Unit)?, onOpenAlbum: (() -> Unit)?) {
    KpknModule {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu historial", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
                if (onShowHistory != null) KpknTextAction("Ver historial", onClick = onShowHistory)
            }
            model.historySummary?.let { history ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.bestSet?.let { Text("Mejor serie · ${NativeProgressionText.formatKg(it.weight)} kg × ${it.reps}", style = KpknType.body, color = WizardColors.text) }
                    history.estimatedRmKg?.let { Text("1RM estimado · ${NativeProgressionText.formatKg(it)} kg", style = KpknType.caption, color = WizardColors.textMuted) }
                    val days = ChronoUnit.DAYS.between(history.lastDate, LocalDate.now()).coerceAtLeast(0)
                    Text("Última vez · " + when (days) { 0L -> "hoy"; 1L -> "ayer"; else -> "hace $days días" }, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            val mediaItems = model.media
            if (mediaItems.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    mediaItems.take(3).forEach { media ->
                        WorkoutMediaThumb(media = media, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), onClick = onOpenAlbum ?: {})
                    }
                    val label = when {
                        mediaItems.all { it.kind == com.example.kpkn.data.models.WorkoutMediaKind.PHOTO } -> "Tus fotos"
                        mediaItems.all { it.kind == com.example.kpkn.data.models.WorkoutMediaKind.VIDEO } -> "Tus vídeos"
                        else -> "Tus archivos"
                    }
                    if (onOpenAlbum != null) KpknTextAction("$label · ${mediaItems.size}", onClick = onOpenAlbum, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
