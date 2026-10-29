package com.example.kpkn.screens.competitions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.domain.competitions.CompetitionAttempts
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionDraft
import com.example.kpkn.domain.competitions.CompetitionExtra
import com.example.kpkn.domain.competitions.CompetitionFeeling
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionInsights
import com.example.kpkn.domain.competitions.CompetitionMedalHonor
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.screens.competitions.design.CompBackRow
import com.example.kpkn.screens.competitions.design.CompBig
import com.example.kpkn.screens.competitions.design.CompCelebrationBackdrop
import com.example.kpkn.screens.competitions.design.CompCenteredAction
import com.example.kpkn.screens.competitions.design.CompChip
import com.example.kpkn.screens.competitions.design.CompChips
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompFeelButton
import com.example.kpkn.screens.competitions.design.CompField
import com.example.kpkn.screens.competitions.design.CompInputField
import com.example.kpkn.screens.competitions.design.CompLabel
import com.example.kpkn.screens.competitions.design.CompMGroup
import com.example.kpkn.screens.competitions.design.CompMedal
import com.example.kpkn.screens.competitions.design.CompMedalChoice
import com.example.kpkn.screens.competitions.design.CompPhotoGrid
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompScreen
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.CompStepTitle
import com.example.kpkn.screens.competitions.design.CompType
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.screens.competitions.design.CountUpValue
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.rememberStableUriImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── c-place: «¿Subiste al podio?» ────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CompetitionPlaceStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val medal = CompetitionPlaceHonors.medalOf(record)
    val place = CompetitionPlaceHonors.parsePlace(record.placement)
    var placeSheet by remember { mutableStateOf(false) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        FlowResultSteps(ui, viewModel)
        CompStepTitle("¿Subiste al podio?")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompetitionMedalHonor.entries.forEach { m ->
                CompMedalChoice(m, selected = medal == m, onClick = { viewModel.choosePlace(if (medal == m) null else m.place) }, modifier = Modifier.weight(1f))
            }
        }
        CompMGroup("Sin podio")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompField("Puesto", place?.let { CompetitionFormat.ordinal(it) } ?: "Escribe…", { placeSheet = true }, Modifier.weight(1f).offset(y = (-4).dp),
                placeholder = place == null)
            CompChip("Sin puesto", selected = false, onClick = { viewModel.choosePlace(null) }, height = 52.dp)
        }
        CompMGroup("Algo más (opcional)")
        val extras = CompetitionExtra.entries
        CompChips(extras.map { it.label }, { extras[it].id in record.extras }) { viewModel.toggleExtra(extras[it]) }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 20.dp))
        CompCenteredAction("Saltar", viewModel::next)
    }
    if (placeSheet) CompTextSheet("Puesto", "Puesto", place?.toString().orEmpty(), { viewModel.choosePlace(it.trim().toIntOrNull()) },
        { placeSheet = false }, numeric = true)
}

// ─── c-feel: «Para recordarlo» ────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CompetitionFeelStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<CompetitionPhoto?>(null) }
    val room = (CompetitionDraft.MAX_MEDIA - record.photos.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(CompetitionDraft.MAX_MEDIA)) { uris ->
        viewModel.addMedia(context, uris)
    }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        FlowResultSteps(ui, viewModel)
        CompStepTitle("Para recordarlo")
        CompMGroup("Cómo te sentiste")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CompetitionFeeling.entries.forEach { feeling ->
                CompFeelButton(CompetitionIcons.byKey(feeling.icon), feeling.label, record.journal?.overallFeeling == feeling.id) { viewModel.setFeeling(feeling.id) }
            }
        }
        CompMGroup("Fotos y vídeos")
        CompPhotoGrid(record.photos.size, cell = { i ->
            MediaThumb(record.photos[i]) { viewing = record.photos[i] }
        }, onAdd = if (room > 0) ({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) else null)
        CompMGroup("Una línea (opcional)")
        CompInputField("Lo mejor del día", record.journal?.whatWentWell.orEmpty(), viewModel::setBest)
        CompInputField("Para la próxima", record.journal?.learnings.orEmpty(), viewModel::setNext)
        val edit = ui.mode == CompetitionFlowMode.EDIT
        CompPill(if (edit) "Guardar" else "Guardar y celebrar", viewModel::next, Modifier.fillMaxWidth().padding(top = 18.dp),
            icon = if (edit) null else CompetitionIcons.Confetti)
    }
    viewing?.let { photo ->
        CompSheet({ viewing = null }) {
            CompSheetTitle(if (photo.kind == CompetitionMediaKind.VIDEO) "Vídeo" else "Foto")
            AsyncImage(rememberStableUriImageRequest(photo.uri), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop)
            CompPill("Quitar", { viewModel.removeMedia(context, photo); viewing = null }, Modifier.fillMaxWidth().padding(top = 16.dp), primary = false,
                content = CompColors.danger, border = Color(0x66FF9B92))
            CompCenteredAction("Cerrar") { viewing = null }
        }
    }
}

/** Una miniatura del álbum (foto o vídeo) que se toca para verla o quitarla. */
@Composable
internal fun MediaThumb(photo: CompetitionPhoto, onClick: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Ver", onClick = onClick) else Modifier)) {
        AsyncImage(rememberStableUriImageRequest(photo.uri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (photo.kind == CompetitionMediaKind.VIDEO) {
            Text("Vídeo", Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000))
                .padding(horizontal = 6.dp, vertical = 2.dp), style = CompType.inter(13f, FontWeight.SemiBold, 1.1f), color = Color.White)
        }
    }
}

// ─── c-cele: la celebración ───────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CompetitionCelebration(ui: CompetitionFlowUi, onShare: () -> Unit, onOpen: () -> Unit) {
    val record = ui.record
    val discipline = ui.discipline
    val medal = CompetitionPlaceHonors.medalOf(record)
    val resolver: (com.example.kpkn.data.models.CompetitionRecord) -> CompetitionDiscipline = { r -> if (r.id == record.id) discipline else CompetitionDiscipline.of(r) }
    val lines = remember(record) { CompetitionInsights.celebrationLines(ui.records, record, resolver) }
    val headline = CompetitionInsights.headline(record, discipline)
    val reduced = wizardReducedMotion()
    val spin = remember { Animatable(if (reduced) 1f else 0f) }
    val label = remember { Animatable(if (reduced) 1f else 0f) }
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    val body = remember { Animatable(if (reduced) 1f else 0f) }
    var counting by remember { mutableStateOf(reduced) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch { spin.animateTo(1f, tween(1400, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
            launch { delay(600); label.animateTo(1f, tween(700)) }
            launch { delay(800); counting = true; pop.animateTo(1f, tween(900, easing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f))) }
            launch { delay(1100); body.animateTo(1f, tween(700)) }
        }
    }
    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        CompCelebrationBackdrop()
        val h = maxHeight
        Box(Modifier.fillMaxWidth().offset(y = h * 0.30f - 66.dp), contentAlignment = Alignment.Center) {
            CompMedal(medal, 132.dp, modifier = Modifier.graphicsLayer {
                val p = spin.value
                rotationY = 540f * (1 - p)
                val s = 0.4f + 0.6f * p
                scaleX = s; scaleY = s
                alpha = p
                cameraDistance = 8f * density
            }, iconSize = 60.dp)
        }
        Column(Modifier.fillMaxWidth().offset(y = h * 0.52f).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val title = record.title.ifBlank { discipline.label }
            CompLabel(listOfNotNull(medal?.label, title).joinToString(" · "), Modifier.graphicsLayer { alpha = label.value; translationY = (1 - label.value) * 12f * density },
                color = CompColors.ene)
            Box(Modifier.padding(top = 10.dp).graphicsLayer {
                val p = pop.value
                val s = 0.2f + 0.8f * p
                scaleX = s; scaleY = s; rotationZ = -40f * (1 - p); alpha = p.coerceIn(0f, 1f)
            }) {
                val total = if (discipline.form == CompetitionResultForm.LOAD) CompetitionAttempts.total(record, discipline) else null
                when {
                    total != null && counting -> CountUpValue(total, decimals = 1, size = 70, unit = "kg")
                    total != null -> CompBig("0", 70, "kg")
                    headline != null -> CompBig(headline.value, 70)
                    else -> Unit
                }
            }
            if (lines.isNotEmpty()) Text(lines.joinToString("\n"), Modifier.padding(top = 8.dp).graphicsLayer { alpha = body.value; translationY = (1 - body.value) * 12f * density },
                style = CompType.body, color = CompColors.ink, textAlign = TextAlign.Center)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompPill("Compartir", onShare, Modifier.weight(1f), icon = CompetitionIcons.Share)
            CompPill("Ver ficha", onOpen, Modifier.weight(1f), primary = false)
        }
    }
}

