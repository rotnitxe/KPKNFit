package com.example.kpkn.screens.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.discomfortKnowledge
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.auge.AugeTtcEngine
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.components.DiscomfortNotice
import com.example.kpkn.screens.workout.components.DiscomfortPicker
import com.example.kpkn.screens.workout.components.DiscomfortSheet
import com.example.kpkn.screens.workout.components.FeedbackActions
import com.example.kpkn.screens.workout.components.fadeBottomWhenScrollable
import com.example.kpkn.screens.workout.components.RestFeedbackCard
import com.example.kpkn.screens.workout.components.TechniqueCard
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

internal fun buildFeedbackExercisesForTarget(
    postExerciseTarget: Exercise?,
    visibleExercises: List<Exercise>,
): List<Exercise> {
    val supersetId = postExerciseTarget?.supersetGroupRefOrLegacyId()
    return if (!supersetId.isNullOrBlank()) {
        visibleExercises.filter { it.supersetGroupRefOrLegacyId() == supersetId }
    } else {
        listOfNotNull(postExerciseTarget)
    }
}

/**
 * Lo que el panel del feedback puede hacer con el descanso que sigue corriendo (LV6, F2): saltarlo. La pantalla completa del descanso
 * lo da mientras corre; sin descanso (o con el panel suelto) es null y solo queda «Guardar».
 */
internal val LocalFeedbackRestSkip = compositionLocalOf<(() -> Unit)?> { null }

/**
 * El panel va en la pantalla completa con el alto acotado: reparte el sitio (lo de en medio se desplaza) y deja «Saltar descanso» y
 * «Guardar» fijos abajo. Suelto (hojas de render, pruebas) mide lo que mide su contenido y los botones van al final.
 */
internal val LocalFeedbackBounded = compositionLocalOf { false }

/**
 * Qué decir tras «después,» en la cabecera del feedback: el nombre del ejercicio que sigue, «la superserie» o «el cierre de la sesión».
 * [pendingPostExerciseIdx] es el índice del ejercicio siguiente, o −2 si el feedback es el del último ejercicio. Null si no se sabe.
 */
internal fun feedbackAfterText(
    pendingPostExerciseIdx: Int,
    exercises: List<Exercise>,
    nameOf: (Exercise) -> String,
): String? = when {
    pendingPostExerciseIdx == -2 -> "el cierre de la sesión"
    pendingPostExerciseIdx >= 0 -> exercises.getOrNull(pendingPostExerciseIdx)?.let { next ->
        if (next.supersetGroupRefOrLegacyId().isNullOrBlank()) nameOf(next) else "la superserie"
    }
    else -> null
}

/**
 * Dónde vive el feedback pendiente (B3): en la tarjeta del descanso si el descanso de trabajo corre y está minimizado; en cualquier otro
 * caso, a pantalla completa (sin descanso en marcha no hay tarjeta que lo lleve, y con el descanso a pantalla completa ya está a la vista).
 */
internal fun feedbackLivesOnCard(
    feedbackShowing: Boolean,
    isRestTimerRunning: Boolean,
    isRestMinimized: Boolean,
    restKind: RestTimerKind?,
): Boolean = feedbackShowing && isRestTimerRunning && isRestMinimized && restKind != null && restKind != RestTimerKind.WARMUP

/**
 * «Guardar»: UNA función para la pantalla completa y la tarjeta, así guardan exactamente lo mismo (la técnica y las molestias del borrador
 * compartido, y las molestias previas que siguen o se resolvieron).
 */
internal fun feedbackSave(
    feedbackExercises: List<Exercise>,
    draft: PostExerciseFeedbackDraft,
    previousFeedback: Map<String, PostExerciseFeedback>,
    linked: List<LinkedDiscomfort>,
    canonicalKeyOf: (Exercise) -> String,
    onSave: (List<PostExerciseFeedback>) -> Unit,
): () -> Unit = {
    onSave(
        buildPostExerciseFeedbackPayloads(
            feedbackExercises = feedbackExercises,
            draft = draft,
            previousFeedback = previousFeedback,
            linked = linked,
            canonicalKeyOf = canonicalKeyOf,
            nameOf = ::displayWorkoutExerciseName,
        ),
    )
}

/** Las articulaciones que carga un ejercicio (las de sus músculos efectivos): sirven para relacionar molestias de otros ejercicios. */
internal fun feedbackArticulationsOf(exercise: Exercise): List<ArticularBattery> {
    val dbInfo = resolveCatalogExerciseInfo(
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
    )
    return (exercise.effectiveMuscles?.takeIf { it.isNotEmpty() } ?: dbInfo?.involvedMuscles.orEmpty())
        .flatMap { im -> AugeTtcEngine.articularBatteriesFor(im.muscle, im.emphasis) }
}

/** El motor de molestias para un feedback: los chips y el aviso, y la explicación de una ficha. */
internal class FeedbackInsightsHandle(
    val insights: FeedbackInsights,
    val explain: suspend (String) -> DiscomfortInsight?,
)

/**
 * Lo del motor de molestias para [feedbackExercises] con la técnica del [draft]. Se calcula fuera del hilo principal y se guarda por
 * ejercicio: la pantalla completa y la tarjeta piden lo mismo y el motor corre una vez. Mientras llega, no hay chips ni aviso.
 */
@Composable
internal fun rememberFeedbackInsights(
    viewModel: WorkoutViewModel,
    feedbackExercises: List<Exercise>,
    draft: PostExerciseFeedbackDraft,
): FeedbackInsightsHandle {
    val request = FeedbackInsightsRequest.of(
        exerciseIds = feedbackExercises.map { it.id },
        technical = draft.technicalSnapshot(),
        completedSetsCount = viewModel.uiState.value.completedSets.size,
    )
    val source = viewModel.feedbackInsightSource
    val insights by produceState(initialValue = source.peek(request) ?: FeedbackInsights.Empty, request) {
        value = source.load(request)
    }
    val explain: suspend (String) -> DiscomfortInsight? = remember(request, source) {
        val ask: suspend (String) -> DiscomfortInsight? = { id -> source.explain(request, id) }
        ask
    }
    return FeedbackInsightsHandle(insights, explain)
}

@Composable
internal fun WorkoutPostExerciseFeedbackContent(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    sessionAccentColor: Color,
    viewModel: WorkoutViewModel,
) {
    val draft = viewModel.feedbackDraftFor(feedbackExercises, postExerciseFeedbackByExerciseId)
    val handle = rememberFeedbackInsights(viewModel, feedbackExercises, draft)
    PostExerciseFeedbackPanel(
        feedbackExercises = feedbackExercises,
        postExerciseFeedbackByExerciseId = postExerciseFeedbackByExerciseId,
        sessionAccentColor = sessionAccentColor,
        canonicalKeyOf = { exercise -> viewModel.canonicalExerciseKey(exercise) },
        onSave = { payloads -> viewModel.savePostExerciseFeedbacks(payloads) },
        draft = draft,
        insights = handle.insights,
        explainDiscomfort = handle.explain,
    )
}

/**
 * El feedback tras el ejercicio dentro de la tarjeta del descanso (LV6, B3, F4): lo mismo que la pantalla completa, en compacto.
 * Comparte el borrador y la función de guardado con [WorkoutPostExerciseFeedbackContent].
 */
@Composable
internal fun WorkoutPostExerciseFeedbackCard(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    viewModel: WorkoutViewModel,
    remainingSeconds: Int,
    totalSeconds: Int,
    shape: RestClockShape,
    clockActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val draft = viewModel.feedbackDraftFor(feedbackExercises, postExerciseFeedbackByExerciseId)
    val handle = rememberFeedbackInsights(viewModel, feedbackExercises, draft)
    val linked = remember(feedbackExercises, postExerciseFeedbackByExerciseId) {
        linkedDiscomfortsFor(feedbackExercises, postExerciseFeedbackByExerciseId, ::feedbackArticulationsOf)
    }
    RestFeedbackCard(
        remainingSeconds = remainingSeconds,
        totalSeconds = totalSeconds,
        shape = shape,
        feedbackExercises = feedbackExercises,
        draft = draft,
        insights = handle.insights,
        explainDiscomfort = handle.explain,
        linked = linked,
        onSave = feedbackSave(
            feedbackExercises = feedbackExercises,
            draft = draft,
            previousFeedback = postExerciseFeedbackByExerciseId,
            linked = linked,
            canonicalKeyOf = { exercise -> viewModel.canonicalExerciseKey(exercise) },
            onSave = { payloads -> viewModel.savePostExerciseFeedbacks(payloads) },
        ),
        onSkipRest = { viewModel.stopRestTimer() },
        onExpand = { viewModel.toggleRestMinimized() },
        modifier = modifier,
        clockActive = clockActive,
    )
}

/**
 * Panel del feedback tras el ejercicio (LV6, B1, F2): «¿Cómo te salió?» con la calidad técnica de cada ejercicio (figura, nota y deslizador
 * de color), las molestias (buscador amable, chips destacados y la ficha de cada una), el aviso del motor y «Saltar descanso» / «Guardar».
 * Ya no pregunta «Qué tan intenso fue»: la intensidad se anota en cada serie. [draft] es el borrador compartido con la tarjeta; sin él, el
 * panel lleva el suyo. La clave canónica y el guardado llegan como lambdas para poder pintarlo sin el ViewModel. [insights] y
 * [explainDiscomfort] son lo del motor; sin ellos no hay chips destacados ni aviso (la interfaz no inventa).
 */
@Composable
internal fun PostExerciseFeedbackPanel(
    feedbackExercises: List<Exercise>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    sessionAccentColor: Color,
    canonicalKeyOf: (Exercise) -> String,
    onSave: (List<PostExerciseFeedback>) -> Unit,
    initialDiscomfortQuery: String = "",
    draft: PostExerciseFeedbackDraft? = null,
    insights: FeedbackInsights = FeedbackInsights.Empty,
    explainDiscomfort: suspend (String) -> DiscomfortInsight? = { null },
    nowMs: Long = remember { System.currentTimeMillis() },
) {
    val liveDraft = draft ?: remember(feedbackExercises.map { it.id }) {
        PostExerciseFeedbackDraft.from(feedbackExercises, postExerciseFeedbackByExerciseId).also { it.query = initialDiscomfortQuery }
    }
    val onSkipRest = LocalFeedbackRestSkip.current
    val bounded = LocalFeedbackBounded.current
    val linked = remember(feedbackExercises, postExerciseFeedbackByExerciseId) {
        linkedDiscomfortsFor(feedbackExercises, postExerciseFeedbackByExerciseId, ::feedbackArticulationsOf)
    }
    var openDiscomfortId by remember { mutableStateOf<String?>(null) }
    val focusName = remember(feedbackExercises) {
        if (feedbackExercises.size == 1) displayWorkoutExerciseName(feedbackExercises.first()) else "esta superserie"
    }
    val save = feedbackSave(
        feedbackExercises = feedbackExercises,
        draft = liveDraft,
        previousFeedback = postExerciseFeedbackByExerciseId,
        linked = linked,
        canonicalKeyOf = canonicalKeyOf,
        onSave = onSave,
    )

    val body: @Composable () -> Unit = {
        Text(
            text = "¿Cómo te salió?",
            style = KpknType.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = WizardColors.text,
        )
        feedbackExercises.forEach { exercise ->
            TechniqueCard(
                value = liveDraft.technical(exercise.id),
                onValueChange = { liveDraft.setTechnical(exercise.id, it) },
                exerciseName = if (feedbackExercises.size > 1) displayWorkoutExerciseName(exercise) else null,
                animated = openDiscomfortId == null,
            )
        }
        if (linked.isNotEmpty()) {
            LinkedDiscomfortsModule(linked = linked, draft = liveDraft)
        }
        KpknModule(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            DiscomfortPicker(
                draft = liveDraft,
                insights = insights,
                focusName = focusName,
                nowMs = nowMs,
                onOpenDiscomfort = { openDiscomfortId = it },
            )
        }
        insights.notice?.headline?.let { DiscomfortNotice(text = it) }
    }

    if (bounded) {
        val bodyScroll = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadeBottomWhenScrollable(bodyScroll)
                    .verticalScroll(bodyScroll),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                body()
            }
            FeedbackActions(onSkipRest = onSkipRest, onSave = save, modifier = Modifier.padding(top = 12.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            body()
            FeedbackActions(onSkipRest = onSkipRest, onSave = save)
        }
    }

    openDiscomfortId?.let { id ->
        val knowledge = discomfortKnowledge(id)
        if (knowledge != null) {
            val insight by produceState<DiscomfortInsight?>(initialValue = null, id, explainDiscomfort) {
                value = explainDiscomfort(id)
            }
            DiscomfortSheet(
                knowledge = knowledge,
                selected = liveDraft.isSelected(id),
                insight = insight,
                onToggleReport = {
                    liveDraft.toggle(id)
                    openDiscomfortId = null
                },
                onDismiss = { openDiscomfortId = null },
            )
        }
    }
}

/**
 * «Molestias previas relacionadas» (LR1c1): las molestias reportadas en otros ejercicios que comparten articulación con el actual, cada
 * una con «Sigue» y «Resuelta». Aviso en `ok`: relleno del verde al 8 % y filete al 24 %.
 */
@Composable
private fun LinkedDiscomfortsModule(
    linked: List<LinkedDiscomfort>,
    draft: PostExerciseFeedbackDraft,
) {
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        accent = SymbolPalette.ok,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = SymbolPalette.ok, modifier = Modifier.size(18.dp))
                Text("Molestias previas relacionadas", style = KpknType.bodyStrong, color = WizardColors.text)
            }
            Text(
                "Reportaste estas molestias en otros ejercicios. Comparten articulación con el actual.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            linked.forEach { item ->
                val stillPresent = draft.stillPresent(item.id)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.label,
                            style = KpknType.bodyStrong,
                            color = if (stillPresent) WizardColors.text else WizardColors.textFaint,
                            textDecoration = if (stillPresent) TextDecoration.None else TextDecoration.LineThrough,
                        )
                        Text("Reportada en: ${item.reportedIn}", style = KpknType.caption, color = WizardColors.textFaint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KpknChip(
                            text = "Sigue",
                            selected = stillPresent,
                            onClick = { draft.setStillPresent(item.id, true) },
                        )
                        KpknChip(
                            text = "Resuelta",
                            selected = !stillPresent,
                            onClick = { draft.setStillPresent(item.id, false) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Molestia elegida (LR1c1): píldora de tinta con el nombre en negro y una X para quitarla. Solo la X quita la molestia, como antes.
 * La usan este panel y el cajón de «Reportar molestias».
 */
@Composable
internal fun DiscomfortSelectedTag(
    label: String,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(WizardColors.text, KpknShape.pill)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.chip,
            color = WizardColors.ctaContent,
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Close,
                contentDescription = "Quitar",
                modifier = Modifier.size(14.dp),
                tint = WizardColors.ctaContent,
            )
        }
    }
}
