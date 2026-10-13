package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.catalogv2.muscleLabel
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.components.catalogTitleLabel
import com.example.kpkn.screens.sessioneditor.components.jointLabel
import com.example.kpkn.screens.sessioneditor.components.jointRoleLabel
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * La ficha de un ejercicio: se abre al tocarlo en el detalle del programa y enseña lo que el CATÁLOGO dice de él (su imagen
 * técnica, la descripción de su definición y sus músculos y articulaciones). Aquí no se escribe ni se resume ningún texto: lo
 * que el catálogo no trae se dice que falta.
 */

/** Marcas de prueba de la ficha: la hoja, su cierre, la imagen, el aviso «Sin imagen» y sus bloques de texto. */
internal const val EXERCISE_INFO_TAG = "setup-exercise-info"
internal const val EXERCISE_INFO_CLOSE_TAG = "setup-exercise-info-close"
internal const val EXERCISE_INFO_IMAGE_TAG = "setup-exercise-info-image"
internal const val EXERCISE_INFO_NOIMAGE_TAG = "setup-exercise-info-noimage"
internal const val EXERCISE_INFO_DESCRIPTION_TAG = "setup-exercise-info-description"
internal const val EXERCISE_INFO_MUSCLES_TAG = "setup-exercise-info-muscles"
internal const val EXERCISE_INFO_JOINTS_TAG = "setup-exercise-info-joints"

/** Rótulos de la ficha y avisos de dato ausente. Lo que viene del catálogo no pasa por aquí. */
internal object ExerciseInfoCopy {
    const val SECTION_HOW = "Cómo se hace"
    const val SECTION_MUSCLES = "Músculos"
    const val SECTION_JOINTS = "Articulaciones"
    const val IMAGE_MISSING = "Sin imagen"
    const val DESCRIPTION_MISSING = "Sin descripción en el catálogo."
    const val NOT_IN_CATALOG = "Este ejercicio no está en el catálogo."
    const val MUSCLES_MISSING = "El catálogo no indica músculos para este ejercicio."
    const val JOINTS_MISSING = "El catálogo no indica articulaciones para este ejercicio."
    const val CATALOG_FAILED = "No se pudo cargar el catálogo."
    const val IN_YOUR_PROGRAM = "En tu programa: "
    const val MUSCLES_PRIMARY = "Principales"
    const val MUSCLES_SECONDARY = "Secundarios"
    const val MUSCLES_STABILIZERS = "Estabilizadores"
    const val SEE_EXERCISE = "Ver ficha de "
}

/**
 * Lo que la ficha enseña de un ejercicio, leído del catálogo. Las listas van con sus etiquetas de la app; una vacía no se pinta.
 */
internal data class ExerciseInfo(
    /** El nombre de la definición del catálogo, tal cual (sin recortar). */
    val catalogName: String,
    /** La descripción de la definición, sin tocar; puede estar vacía. */
    val description: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val stabilizerMuscles: List<String>,
    /** Las articulaciones de la configuración exacta, en el orden del catálogo. */
    val joints: List<JointInvolvementV2>,
    /** El recurso de la imagen técnica de la configuración, o null si no tiene. */
    val imageResId: Int?,
)

/** La definición del catálogo que contiene la configuración [configurationId], o null si ninguna la tiene. */
internal fun definitionIdOfConfiguration(catalog: ExerciseCatalogV2, configurationId: String): String? =
    catalog.families.asSequence()
        .flatMap { it.definitions.asSequence() }
        .firstOrNull { definition -> definition.configurations.any { it.id == configurationId } }
        ?.id

/**
 * La ficha de [exercise] según [catalog]. Un ejercicio cambiado enseña la de su sustituto (`swappedConfigId`). Null si no tiene
 * identidad de catálogo o el catálogo no resuelve esa configuración exacta: la ficha lo dice y no inventa nada.
 */
internal fun exerciseInfoOf(exercise: SetupPlanRevealExercise, catalog: ExerciseCatalogV2): ExerciseInfo? {
    val swapped = exercise.swappedConfigId
    val configurationId = swapped ?: exercise.catalogConfigurationId
    val definitionId = if (swapped != null) definitionIdOfConfiguration(catalog, swapped) else exercise.catalogDefinitionId
    // Sin identidad no se busca por nombre: un ejercicio propio con el nombre de uno del catálogo no es ese ejercicio.
    if (definitionId == null) return null
    val resolved = resolveCatalogExerciseV2(
        exercise = Exercise(
            id = exercise.instanceId,
            name = exercise.name,
            catalogRevision = exercise.catalogRevision,
            catalogDefinitionId = definitionId,
            catalogConfigurationId = configurationId,
        ),
        catalog = catalog,
        legacyInfo = null,
    ) ?: return null
    // El resolutor cae a la configuración predeterminada si no encuentra la pedida: aquí eso sería enseñar otro ejercicio.
    if (configurationId != null && resolved.configuration.id != configurationId) return null
    val profile = resolved.configuration.profile
    return ExerciseInfo(
        catalogName = resolved.definition.canonicalName.trim(),
        description = resolved.definition.description,
        primaryMuscles = profile.primaryMuscles.map(::muscleNameOf),
        secondaryMuscles = profile.secondaryMuscles.map(::muscleNameOf),
        stabilizerMuscles = profile.stabilizerMuscles.map(::muscleNameOf),
        joints = profile.jointInvolvement,
        imageResId = ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = resolved.definition.id,
            exerciseDbId = null,
            exerciseId = exercise.instanceId,
            catalogConfigurationId = resolved.configuration.id,
        ),
    )
}

/** El nombre de un músculo con la etiqueta de la app; sin ella, su identificador tal cual (como hace el adaptador del catálogo). */
private fun muscleNameOf(id: String): String = muscleLabel(id) ?: id

/** La ficha del detalle: el ejercicio que enseña y si está abierta. Conserva el último mientras se cierra. */
internal class ExerciseInfoState {
    var exercise by mutableStateOf<SetupPlanRevealExercise?>(null)
        private set
    var open by mutableStateOf(false)
        private set

    fun show(exercise: SetupPlanRevealExercise) {
        this.exercise = exercise
        open = true
    }

    fun close() {
        open = false
    }
}

private val InfoSheetFill = Color(0xFF0B0B0B)
private val InfoSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val InfoImageFill = Color(0xFF141414)
private val InfoImageShape = RoundedCornerShape(20.dp)

/**
 * La ficha sobre el detalle: un velo oscuro (tocarlo cierra) y, bajo él, una hoja de vidrio que sube con la cabecera fija y el
 * contenido desplazable. Atrás la cierra. Entra en 220 ms (escala de 0,96 a 1 y fundido) y sale en 160 ms; con «reducir
 * movimiento», sin animación. El catálogo se pide solo al abrirla y después sale de la caché del proceso.
 */
@Composable
internal fun ExerciseInfoLayer(state: ExerciseInfoState, reduced: Boolean, topInset: Dp) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf(CatalogV2ProcessCache.peek()?.catalog) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(state.open) {
        if (state.open && catalog == null) {
            failed = false
            val loaded = runCatching {
                withContext(Dispatchers.IO) { CatalogV2ProcessCache.getOrLoad(context).catalog }
            }.getOrNull()
            catalog = loaded
            failed = loaded == null
        }
    }
    BackHandler(enabled = state.open) { state.close() }
    val exercise = state.exercise
    AnimatedVisibility(
        visible = state.open && exercise != null,
        modifier = Modifier.fillMaxSize(),
        enter = if (reduced) {
            EnterTransition.None
        } else {
            scaleIn(animationSpec = tween(220), initialScale = 0.96f) + fadeIn(tween(220))
        },
        exit = if (reduced) {
            ExitTransition.None
        } else {
            scaleOut(animationSpec = tween(160), targetScale = 0.96f) + fadeOut(tween(160))
        },
    ) {
        if (exercise != null) {
            Box(Modifier.fillMaxSize()) {
                // El velo: tocarlo cierra la ficha. Sin semántica propia; la ficha se cierra con su botón «Cerrar» o con atrás.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(OverlayScrim.copy(alpha = 0.96f))
                        .clearAndSetSemantics {}
                        .pointerInput(state) { detectTapGestures(onTap = { state.close() }) },
                ) {}
                ExerciseInfoSheet(
                    exercise = exercise,
                    catalog = catalog,
                    failed = failed,
                    reduced = reduced,
                    topInset = topInset,
                    onClose = state::close,
                )
            }
        }
    }
}

/** La hoja: la cabecera con «Cerrar» fija y, debajo, el contenido de la ficha en una columna que se desplaza. */
@Composable
private fun ExerciseInfoSheet(
    exercise: SetupPlanRevealExercise,
    catalog: ExerciseCatalogV2?,
    failed: Boolean,
    reduced: Boolean,
    topInset: Dp,
    onClose: () -> Unit,
) {
    val info = remember(exercise, catalog) { catalog?.let { exerciseInfoOf(exercise, it) } }
    val loading = catalog == null && !failed
    val title = info?.catalogName ?: exercise.name
    // Si el programa la llama de otra manera se dice (un cambio ya enseña el nombre del catálogo, así que no se repite).
    val inProgram = info != null && exercise.swappedConfigId == null && exercise.name.trim() != info.catalogName
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topInset)
            .clip(InfoSheetShape)
            .background(InfoSheetFill)
            // Un toque sobre la hoja no llega al velo de detrás.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = title }
            .testTag(EXERCISE_INFO_TAG),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            CloseButton(onClick = onClose, tag = EXERCISE_INFO_CLOSE_TAG)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            // Sin imagen solo cuando se sabe que no la hay: con el catálogo sin cargar, el marco va en blanco.
            ExerciseImageFrame(imageResId = info?.imageResId, noImage = catalog != null, title = title)
            Text(
                text = title,
                style = WizardTypography.stepTitle,
                color = WizardColors.text,
                modifier = Modifier.padding(top = 20.dp).semantics { heading() },
            )
            if (inProgram) {
                Text(
                    text = ExerciseInfoCopy.IN_YOUR_PROGRAM + exercise.name,
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            when {
                info != null -> ExerciseInfoBody(info)
                loading -> ThinLoadingBar(reduced = reduced, modifier = Modifier.padding(top = 28.dp))
                failed -> Text(
                    text = ExerciseInfoCopy.CATALOG_FAILED,
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 28.dp),
                )
                else -> {
                    InfoSectionTitle(ExerciseInfoCopy.SECTION_HOW)
                    Text(ExerciseInfoCopy.NOT_IN_CATALOG, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                }
            }
            Spacer(Modifier.height(navBottom + 32.dp))
        }
    }
}

/** Lo que el catálogo dice del ejercicio: la descripción, los músculos y las articulaciones. */
@Composable
private fun ExerciseInfoBody(info: ExerciseInfo) {
    InfoSectionTitle(ExerciseInfoCopy.SECTION_HOW)
    if (info.description.isBlank()) {
        Text(
            text = ExerciseInfoCopy.DESCRIPTION_MISSING,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            modifier = Modifier.testTag(EXERCISE_INFO_DESCRIPTION_TAG),
        )
    } else {
        Text(
            text = info.description,
            style = WizardTypography.body,
            color = WizardColors.text,
            modifier = Modifier.testTag(EXERCISE_INFO_DESCRIPTION_TAG),
        )
    }
    InfoSectionTitle(ExerciseInfoCopy.SECTION_MUSCLES)
    MusclesBlock(info)
    InfoSectionTitle(ExerciseInfoCopy.SECTION_JOINTS)
    JointsBlock(info.joints)
}

@Composable
private fun InfoSectionTitle(text: String) {
    Text(
        text = text,
        style = SECTION_STYLE,
        color = WizardColors.text,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp).semantics { heading() },
    )
}

/** Los tres grupos de músculos, cada uno en su línea; un grupo vacío no sale y, si no hay ninguno, se dice. */
@Composable
private fun MusclesBlock(info: ExerciseInfo) {
    val groups = listOf(
        Triple(ExerciseInfoCopy.MUSCLES_PRIMARY, info.primaryMuscles, true),
        Triple(ExerciseInfoCopy.MUSCLES_SECONDARY, info.secondaryMuscles, false),
        Triple(ExerciseInfoCopy.MUSCLES_STABILIZERS, info.stabilizerMuscles, false),
    ).filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        Text(
            text = ExerciseInfoCopy.MUSCLES_MISSING,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            modifier = Modifier.testTag(EXERCISE_INFO_MUSCLES_TAG),
        )
        return
    }
    Column(
        Modifier
            .testTag(EXERCISE_INFO_MUSCLES_TAG)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((label, muscles, primary) in groups) MuscleGroupLine(label, muscles, primary)
    }
}

/** Un grupo en una línea: «Principales» en tinta con un punto de acento; los demás, en tenue. Sin cajas. */
@Composable
private fun MuscleGroupLine(label: String, muscles: List<String>, primary: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        if (primary) {
            Canvas(Modifier.padding(top = 6.dp).size(8.dp)) { drawCircle(SymbolPalette.musculo) }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
                append("  ")
                append(muscles.joinToString(" · "))
            },
            style = WizardTypography.bodySmall,
            color = if (primary) WizardColors.text else WizardColors.textMuted,
        )
    }
}

@Composable
private fun JointsBlock(joints: List<JointInvolvementV2>) {
    if (joints.isEmpty()) {
        Text(
            text = ExerciseInfoCopy.JOINTS_MISSING,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            modifier = Modifier.testTag(EXERCISE_INFO_JOINTS_TAG),
        )
        return
    }
    Column(
        Modifier
            .testTag(EXERCISE_INFO_JOINTS_TAG)
            .semantics(mergeDescendants = true) {},
    ) {
        joints.forEachIndexed { index, joint -> JointLine(joint, ruled = index > 0) }
    }
}

/** Una articulación: su nombre en tinta, su rol en tenue y, debajo, sus acciones del catálogo. Un filete la separa de la anterior. */
@Composable
private fun JointLine(joint: JointInvolvementV2, ruled: Boolean) {
    val rule = WizardColors.divider
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (ruled) drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = jointLabel(joint.jointId),
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(jointRoleLabel(joint.role), style = WizardTypography.note, color = WizardColors.textMuted)
        }
        if (joint.actions.isNotEmpty()) {
            Text(
                text = joint.actions.joinToString(" · ") { catalogTitleLabel(it) },
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * El marco de la imagen técnica: 4:3, radio de 20 dp y fondo #141414. Con imagen, la del catálogo entera (encuadre `Fit`); sin
 * ella y si [noImage], el mismo marco con un trazo discontinuo, una cámara tachada y «Sin imagen»; en otro caso, en blanco.
 */
@Composable
private fun ExerciseImageFrame(imageResId: Int?, noImage: Boolean, title: String) {
    val frame = Modifier
        .fillMaxWidth()
        .aspectRatio(4f / 3f)
        .clip(InfoImageShape)
        .background(InfoImageFill)
    when {
        imageResId != null -> Image(
            painter = painterResource(imageResId),
            contentDescription = "Imagen de $title",
            contentScale = ContentScale.Fit,
            modifier = frame.testTag(EXERCISE_INFO_IMAGE_TAG),
        )
        noImage -> Column(
            frame
                .drawWithContent {
                    drawContent()
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = WizardColors.glassBorder,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(20.dp.toPx() - w / 2f),
                        style = Stroke(
                            width = w,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        ),
                    )
                }
                .testTag(EXERCISE_INFO_NOIMAGE_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NoImageGlyph()
            Spacer(Modifier.height(10.dp))
            Text(ExerciseInfoCopy.IMAGE_MISSING, style = WizardTypography.caption, color = WizardColors.textMuted)
        }
        else -> Box(frame) {}
    }
}

/** Una cámara de 28 dp tachada, en la tinta tenue de la página. */
@Composable
private fun NoImageGlyph() {
    val ink = WizardColors.textFaint
    Canvas(Modifier.size(28.dp)) {
        val w = 1.6.dp.toPx()
        val top = size.height * 0.24f
        drawRoundRect(
            color = ink,
            topLeft = Offset(w / 2f, top),
            size = Size(size.width - w, size.height - top - w / 2f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(w),
        )
        drawCircle(
            color = ink,
            radius = size.height * 0.17f,
            center = Offset(size.width / 2f, top + (size.height - top) / 2f),
            style = Stroke(w),
        )
        drawLine(ink, Offset(size.width * 0.04f, size.height * 0.04f), Offset(size.width * 0.96f, size.height * 0.96f), w, StrokeCap.Round)
    }
}

/** Un indicador fino (2 dp) mientras se carga el catálogo la primera vez. Con «reducir movimiento», fijo. */
@Composable
private fun ThinLoadingBar(reduced: Boolean, modifier: Modifier = Modifier) {
    if (reduced) {
        LoadingTrack(progress = null, modifier = modifier)
    } else {
        val phase by rememberInfiniteTransition(label = "exerciseInfoLoading").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "exerciseInfoLoadingPhase",
        )
        LoadingTrack(progress = phase, modifier = modifier)
    }
}

@Composable
private fun LoadingTrack(progress: Float?, modifier: Modifier) {
    val track = WizardColors.glassBorder
    val ink = WizardColors.text
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(track)
        val segment = size.width * 0.28f
        // Sin progreso (reducir movimiento) el tramo se queda al principio; con él, recorre la barra de lado a lado.
        val left = if (progress == null) 0f else (size.width + segment) * progress - segment
        clipRect { drawRect(ink, topLeft = Offset(left, 0f), size = Size(segment, size.height)) }
    }
}
