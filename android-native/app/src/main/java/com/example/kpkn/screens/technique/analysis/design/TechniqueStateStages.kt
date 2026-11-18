package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.analysis.AnalysisStatusUi
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_NOTICE_TAG
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_TRENDS_TAG
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisUiState
import com.example.kpkn.screens.technique.player.design.TechniqueOverlayOptions
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

const val TECHNIQUE_SKELETON_STAGE_TAG = "analysis-skeleton-stage"
const val TECHNIQUE_STILL_STAGE_TAG = "analysis-still-stage"
const val TECHNIQUE_PROFILE_MODAL_TAG = "analysis-profile-modal"

/** Esqueleto `.tc-sk` sobre silueta de isla flotante, vídeo atenuado y hoja inferior en peek. */
@Composable
fun TechniqueLoadingSkeletonStage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .testTag(TECHNIQUE_SKELETON_STAGE_TAG),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 86.dp, bottom = 150.dp, start = 16.dp, end = 16.dp),
        ) {
            KpknSkeleton(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp))
        }
        TechniqueFloatingIsland(
            exerciseName = "Cargando técnica…",
            setInfo = null,
            onBack = onBack,
            hazeState = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xCC111116))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x40FFFFFF)),
            )
            KpknSkeletonModule(lines = 2)
        }
    }
}

/** Pantalla limpia cuando el vídeo fue borrado de la galería. */
@Composable
fun TechniqueMediaMissingStage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TechniqueFloatingIsland(
            exerciseName = "Técnica",
            setInfo = null,
            onBack = onBack,
            hazeState = null,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            KpknEmptyState(
                icon = KpknLineIcons.Info,
                title = "No encontramos este vídeo",
                body = "Puede que se haya borrado de la galería de la serie.",
            )
        }
    }
}

/**
 * Pantalla estática `.tc-still` + tarjeta flotante de vidrio `.tc-card`:
 * se usa para `NotAnalyzed`, `Failed`, `Unsupported` y `ReportMissing` con el vídeo atenuado de fondo
 * e isla flotante `.tc-isl` arriba.
 */
@Composable
fun TechniqueStillStateStage(
    state: TechniqueAnalysisUiState,
    status: AnalysisStatusUi,
    videoSlot: @Composable (Modifier, TechniqueOverlayOptions) -> Unit,
    hazeState: HazeState,
    onBack: () -> Unit,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    onOpenTrends: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onPlayPreviewToggle: (() -> Unit)? = null,
    isPlayingPreview: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .testTag(TECHNIQUE_STILL_STAGE_TAG),
    ) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            videoSlot(
                Modifier.fillMaxSize(),
                TechniqueOverlayOptions(skeleton = false, trail = false, guide = false),
            )
            Box(Modifier.fillMaxSize().background(Color(0x99050507)))
        }

        if (onPlayPreviewToggle != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x73141418))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable(role = Role.Button, onClick = onPlayPreviewToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlayingPreview) KpknLineIcons.Pause else KpknLineIcons.PlayFilled,
                    contentDescription = if (isPlayingPreview) "Pausar vista previa" else "Reproducir vídeo",
                    tint = Color(0xFFF2EEE6),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        TechniqueFloatingIsland(
            exerciseName = state.exerciseName,
            setInfo = state.setInfo,
            onBack = onBack,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        val (icon, title, caption, actionText, actionTag, actionClick) = stillDescriptor(
            status = status,
            onAnalyze = onAnalyze,
            onRetry = onRetry,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.notice?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioHoja, RoundedCornerShape(18.dp), withBorder = true, Color(0x55E05252))
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                        .testTag(TECHNIQUE_ANALYSIS_NOTICE_TAG),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, style = KpknType.caption, color = WizardColors.text, modifier = Modifier.weight(1f))
                    KpknIconButton(KpknLineIcons.Close, "Cerrar el aviso", onDismissNotice)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kpknGlassPreset(
                        hazeState = hazeState,
                        preset = KpknGlassPreset.VidrioHoja,
                        shape = RoundedCornerShape(24.dp),
                        withBorder = true,
                        additionalScrim = Color(0xCC0E0E12),
                    )
                    .padding(18.dp)
                    .testTag(ANALYSIS_STATUS_TAG),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    AnalysisIconCircle(icon)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = title, style = KpknType.bodyStrong.copy(fontSize = 15.5.sp), color = WizardColors.text)
                        Spacer(Modifier.height(4.dp))
                        Text(text = caption, style = KpknType.caption.copy(fontSize = 13.sp, lineHeight = 19.sp), color = WizardColors.textMuted)
                    }
                }
                if (actionText != null && actionTag != null) {
                    Spacer(Modifier.height(16.dp))
                    KpknPrimaryPill(
                        text = actionText,
                        onClick = actionClick,
                        modifier = Modifier.fillMaxWidth(),
                        tag = actionTag,
                    )
                }
                val exerciseKey = state.exerciseKey
                if (state.canOpenTrends && exerciseKey != null) {
                    Spacer(Modifier.height(10.dp))
                    KpknSecondaryPill(
                        text = "Ver tendencia",
                        onClick = { onOpenTrends(exerciseKey) },
                        modifier = Modifier.fillMaxWidth(),
                        tag = TECHNIQUE_ANALYSIS_TRENDS_TAG,
                    )
                }
            }
        }
    }
}

private data class StillDescriptor(
    val icon: ImageVector,
    val title: String,
    val caption: String,
    val actionText: String?,
    val actionTag: String?,
    val onAction: () -> Unit,
)

private fun stillDescriptor(
    status: AnalysisStatusUi,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
): StillDescriptor = when (status) {
    is AnalysisStatusUi.Failed -> StillDescriptor(
        icon = KpknLineIcons.Warning,
        title = "No pudimos analizar este vídeo",
        caption = status.message,
        actionText = "Reintentar",
        actionTag = ANALYSIS_RETRY_TAG,
        onAction = onRetry,
    )
    AnalysisStatusUi.ReportMissing -> StillDescriptor(
        icon = KpknLineIcons.Warning,
        title = "Falta el informe del análisis",
        caption = "El cálculo anterior no dejó un informe válido. Vuelve a analizar para regenerarlo.",
        actionText = "Volver a analizar",
        actionTag = ANALYSIS_RETRY_TAG,
        onAction = onRetry,
    )
    is AnalysisStatusUi.Unsupported -> StillDescriptor(
        icon = KpknLineIcons.Info,
        title = "Movimiento sin análisis automático",
        caption = status.reason,
        actionText = null,
        actionTag = null,
        onAction = {},
    )
    else -> StillDescriptor(
        icon = KpknLineIcons.Play,
        title = "Vídeo listo para analizar",
        caption = "Detectamos pose, trayectoria de barra, repeticiones y ROM fotograma a fotograma.",
        actionText = "Analizar vídeo",
        actionTag = ANALYSIS_ANALYZE_TAG,
        onAction = onAnalyze,
    )
}

/**
 * Selector modal `.tc-fs` para «¿Qué movimiento es?»: reutiliza [ProfilePickerContent] sobre tarjeta de vidrio inferior.
 */
@Composable
fun TechniqueProfileModalStage(
    guess: AnalysisProfileId?,
    selectedProfile: AnalysisProfileId?,
    profilesExpanded: Boolean,
    onSelectProfile: (AnalysisProfileId) -> Unit,
    onToggleProfiles: () -> Unit,
    onConfirmProfile: () -> Unit,
    hazeState: HazeState,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x9E050507))
            .then(if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier)
            .testTag(TECHNIQUE_PROFILE_MODAL_TAG),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .testTag(ANALYSIS_STATUS_TAG),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .kpknGlassPreset(
                        hazeState = hazeState,
                        preset = KpknGlassPreset.VidrioHoja,
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                        withBorder = true,
                        additionalScrim = Color(0xE00E0E12),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 22.dp),
            ) {
                if (onDismiss != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar selector", onClick = onDismiss)
                    }
                }
                ProfilePickerContent(
                    guess = guess,
                    selected = selectedProfile,
                    expanded = profilesExpanded,
                    onSelect = onSelectProfile,
                    onToggle = onToggleProfiles,
                    onConfirm = onConfirmProfile,
                )
            }
        }
    }
}
