package com.example.kpkn.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.tour.KpknTourOverlay
import com.example.kpkn.ui.tour.SettingsTourSeenStore
import com.example.kpkn.ui.tour.TourAnchorRegistry
import com.example.kpkn.ui.tour.TourController
import com.example.kpkn.ui.tour.TourSeenStore
import com.example.kpkn.ui.tour.TourStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Clave del overlay del recorrido guiado dentro del anfitrión (solo puede haber uno). */
private val KpknTourOverlayKey = Any()

/** Clave del toast discreto de cierre al completar un recorrido. */
private val KpknTourToastKey = Any()

private const val TOUR_COMPLETED_TOAST_MS = 3_000L

/**
 * Host for glass sheets/dialogs rendered as siblings of MainActivity's [hazeSource].
 *
 * Mirrors the Home overlay registration pattern: entries live in observable state that
 * MainActivity reads directly, so presenting a sheet always triggers a visible recomposition.
 */
class KpknOverlayHostController(tourSeen: TourSeenStore = SettingsTourSeenStore) {
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var toastDismissJob: Job? = null

    var entries by mutableStateOf<Map<Any, @Composable () -> Unit>>(emptyMap())
        private set

    private var seenRevision by mutableIntStateOf(0)

    /** Anclas de los recorridos guiados: lo que `Modifier.tourAnchor` registra bajo este anfitrión. */
    val tourAnchors = TourAnchorRegistry()

    /** Recorrido guiado: su overlay se monta mientras hay uno activo (ver [startTour]). */
    val tour: TourController = TourController(
        scope = hostScope,
        seen = tourSeen,
        hasAnchor = { id -> tourAnchors.contains(id) },
        hasScroller = { id -> tourAnchors.hasScroller(id) },
        revealAnchor = { id -> tourAnchors.reveal(id) },
        onFinished = {
            seenRevision++
            dismiss(KpknTourOverlayKey)
        },
        onCompleted = { showCompletedToast() },
    )

    fun isTourSeen(tourId: String): Boolean {
        seenRevision
        return tourId in tour.seenIds()
    }

    fun markTourSeen(tourId: String) {
        tour.markSeen(tourId)
        seenRevision++
    }

    fun present(key: Any, content: @Composable () -> Unit) {
        entries = entries + (key to content)
    }

    fun dismiss(key: Any) {
        if (key in entries) {
            entries = entries - key
        }
    }

    /**
     * Arranca el recorrido [tourId] si no se ha visto y lo monta sobre el resto de overlays.
     * Devuelve false si no arrancó (ya visto, sin pasos o con otro recorrido activo).
     */
    fun startTour(tourId: String, steps: List<TourStep>): Boolean {
        if (!tour.start(tourId, steps)) return false
        toastDismissJob?.cancel()
        dismiss(KpknTourToastKey)
        present(KpknTourOverlayKey) { KpknTourOverlay(tour = tour, anchors = tourAnchors) }
        return true
    }

    private fun showCompletedToast() {
        toastDismissJob?.cancel()
        present(KpknTourToastKey) {
            KpknTourCompletedToast(onDismiss = {
                toastDismissJob?.cancel()
                dismiss(KpknTourToastKey)
            })
        }
        toastDismissJob = hostScope.launch {
            delay(TOUR_COMPLETED_TOAST_MS)
            dismiss(KpknTourToastKey)
        }
    }
}

@Composable
private fun KpknTourCompletedToast(onDismiss: () -> Unit) {
    val haze = LocalHazeState.current
    val shape = RoundedCornerShape(22.dp)
    val surface = if (haze != null) {
        Modifier.kpknGlass(haze, shape, withBorder = false)
    } else {
        Modifier.clip(shape).background(Color(0xFF141414).copy(alpha = 0.94f))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(500f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 96.dp)
                    .then(surface)
                    .border(1.dp, WizardColors.glassBorder, shape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("tour_completed_toast"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(WizardColors.done.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = null,
                        tint = WizardColors.done,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Guía completada · Repítela en Ajustes",
                    fontFamily = WizardFonts.body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = WizardColors.text,
                )
            }
        }
    }
}

val LocalKpknOverlayHost = staticCompositionLocalOf<KpknOverlayHostController?> { null }

@Composable
fun KpknOverlayHostContent(controller: KpknOverlayHostController) {
    val snapshot = controller.entries
    snapshot.forEach { (entryKey, content) ->
        key(entryKey) {
            content()
        }
    }
}

/**
 * Renders [content] in the root overlay host (sibling of hazeSource) when available.
 * Falls back to in-place full-screen rendering if no host is provided.
 */
@Composable
fun KpknPortal(
    content: @Composable () -> Unit,
) {
    val host = LocalKpknOverlayHost.current
    val key = remember { Any() }
    val latestContent by rememberUpdatedState(content)

    if (host == null) {
        Box(modifier = Modifier.fillMaxSize().zIndex(400f)) {
            content()
        }
        return
    }

    DisposableEffect(host, key) {
        host.present(key) { latestContent() }
        onDispose { host.dismiss(key) }
    }
    // Refresh content pointer after caller recompositions (state inside the sheet/dialog).
    SideEffect {
        host.present(key) { latestContent() }
    }
}

@Composable
fun rememberKpknOverlayHostController(): KpknOverlayHostController =
    remember { KpknOverlayHostController() }
