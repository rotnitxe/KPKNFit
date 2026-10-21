package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.WorkoutShareService
import com.example.kpkn.ui.components.rememberStableUriImageRequest
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PhotoCream = Color(0xFFF2EEE6)

/** Exact 24-unit paths and 1.8-unit rounded strokes from the approved common.js. */
private fun photoReferenceIcon(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()

private object PhotoReferenceIcons {
    val Back = photoReferenceIcon("Photo.Back", "M15 6 9 12l6 6")
    val Next = photoReferenceIcon("Photo.Next", "m9 5 7 7-7 7")
    val Trash = photoReferenceIcon("Photo.Trash", "M3 6h18M8 6V3h8v3M6 6l1 15h10l1-15M10 10v7M14 10v7")
    val Expand = photoReferenceIcon("Photo.Expand", "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5M4 4l6 6M20 4l-6 6M20 20l-6-6M4 20l6-6")
    val Share = photoReferenceIcon("Photo.Share", "M12 16V2M7 7l5-5 5 5M5 11v9h14v-9")
}

/** One photo surface for album and finish; closing it never changes the session or its draft. */
@Composable
internal fun WorkoutPhotoViewer(
    path: String,
    title: String,
    caption: String?,
    onDismiss: () -> Unit,
    createdAtMs: Long? = null,
    fallbackDate: String? = null,
    positionLabel: String? = null,
    onDelete: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entered by remember(path) { mutableStateOf(false) }
    var zoom by remember(path) { mutableFloatStateOf(1f) }
    var directZoom by remember(path) { mutableStateOf(false) }
    var pan by remember(path) { mutableStateOf(Offset.Zero) }
    var viewport by remember(path) { mutableStateOf(IntSize.Zero) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember(path) { mutableStateOf<String?>(null) }
    val reduced = wizardReducedMotion()
    val easing = CubicBezierEasing(.2f, .8f, .2f, 1f)
    val entry by animateFloatAsState(if (entered) 1f else 0f, tween(if (reduced) 0 else 350, easing = easing), label = "workout-photo-expand")
    val density = LocalDensity.current
    val bottomSafe = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    LaunchedEffect(path) { entered = true }
    fun clampPan(value: Offset, scale: Float): Offset {
        val x = viewport.width * (scale - 1f) / 2f
        val y = viewport.height * (scale - 1f) / 2f
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val transform = rememberTransformableState { factor, delta, _ ->
        directZoom = true
        zoom = (zoom * factor).coerceIn(1f, 4f)
        pan = if (zoom <= 1f) Offset.Zero else clampPan(pan + delta, zoom)
    }
    val uri = remember(path) {
        if (path.startsWith("content:") || path.startsWith("file:")) path else android.net.Uri.fromFile(File(path)).toString()
    }
    val date = remember(createdAtMs, fallbackDate) {
        createdAtMs?.takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-CL")))
        } ?: fallbackDate
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
            scaleX = .92f + .08f * entry; scaleY = .92f + .08f * entry
            alpha = .6f + .4f * entry
        }.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = maxOf(24.dp, bottomSafe))
            .testTag("workout-photo-viewer"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoViewerIcon("Cerrar foto", onDismiss) { Icon(PhotoReferenceIcons.Back, null, Modifier.size(18.dp), tint = PhotoCream) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = KpknType.bodyStrong.copy(fontSize = 13.sp, lineHeight = 18.2f.sp, fontWeight = FontWeight.Bold), color = PhotoCream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    caption?.takeIf { it.isNotBlank() }?.let { Text(it, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 15.4f.sp), color = PhotoCream.copy(alpha = .6f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    date?.takeIf { it.isNotBlank() }?.let { Text(it, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 15.4f.sp), color = PhotoCream.copy(alpha = .6f), maxLines = 1) }
                }
                onDelete?.let { remove -> PhotoViewerIcon("Borrar foto", remove) { Icon(PhotoReferenceIcons.Trash, null, Modifier.size(18.dp), tint = PhotoCream) } }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Color(0xFF101010))
                .onSizeChanged { viewport = it },
                contentAlignment = Alignment.Center) {
                key(path) {
                    val imageZoom by animateFloatAsState(zoom,
                        animationSpec = if (reduced || directZoom) snap() else tween(450, easing = easing), label = "workout-photo-zoom")
                    AsyncImage(model = rememberStableUriImageRequest(uri), contentDescription = title,
                        modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                            scaleX = imageZoom; scaleY = imageZoom; translationX = pan.x; translationY = pan.y
                        }.testTag("workout-photo-image").semantics { stateDescription = if (zoom > 1.01f) "Ampliada" else "Ajustada" },
                        contentScale = ContentScale.Fit)
                }
                if (onPrevious != null && onNext != null) {
                    Row(Modifier.fillMaxWidth().align(Alignment.Center), horizontalArrangement = Arrangement.SpaceBetween) {
                        PhotoViewerIcon("Foto anterior", onPrevious) { Icon(PhotoReferenceIcons.Back, null, Modifier.size(18.dp), tint = PhotoCream) }
                        PhotoViewerIcon("Foto siguiente", onNext) { Icon(PhotoReferenceIcons.Next, null, Modifier.size(18.dp), tint = PhotoCream) }
                    }
                }
                positionLabel?.let { Text(it, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 15.4f.sp), color = PhotoCream.copy(alpha = .6f), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) }
            }
            shareError?.let { Text(it, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 15.4f.sp), color = PhotoCream.copy(alpha = .6f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                PhotoViewerAction(if (zoom > 1.01f) "Ajustar" else "Ampliar", "photo-viewer-zoom", onClick = {
                    directZoom = false
                    zoom = if (zoom > 1.01f) 1f else 1.5f
                    pan = Offset.Zero
                }) { Icon(PhotoReferenceIcons.Expand, null, Modifier.size(18.dp), tint = PhotoCream) }
                PhotoViewerAction("Compartir", "photo-viewer-share", enabled = !sharing, onClick = {
                    sharing = true; shareError = null
                    scope.launch {
                        try { WorkoutShareService.sharePhoto(context, path) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { shareError = "No se pudo abrir la pantalla para compartir." }
                        finally { sharing = false }
                    }
                }) { Icon(PhotoReferenceIcons.Share, null, Modifier.size(18.dp), tint = PhotoCream) }
            }
        }
    }
}

@Composable
private fun PhotoViewerIcon(label: String, action: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(44.dp).semantics { this.contentDescription = label }) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x22FFFFFF), CircleShape), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun PhotoViewerAction(label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(label, style = KpknType.action.copy(fontSize = 12.sp, lineHeight = 16.8f.sp, fontWeight = FontWeight.Medium), color = PhotoCream.copy(alpha = if (enabled) 1f else .5f))
        }
    }
}
