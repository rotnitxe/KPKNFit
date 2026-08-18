package com.example.kpkn.screens.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.R
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.physiqueDescForSliderPos
import com.example.kpkn.domain.nutrition.physiqueLabelForSliderPos
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val BgBlack = Color(0xFF000000)
private val WizardYellow = Color(0xFFF4D35E)
private val WizardTeal = Color(0xFF58C7C1)
private val GlassBorder = Color.White.copy(alpha = 0.10f)
private val InputBg = Color.White.copy(alpha = 0.07f)
private val CalColor = Color(0xFF42A5F5)
private val ProColor = Color(0xFFEF5350)
private val CarbColor = Color(0xFF7E57C2)
private val FatColor = Color(0xFF26A69A)

@Composable
fun NutritionWizardScreen(
    mode: String = "create",
    planId: String? = null,
    viewModel: NutritionWizardViewModel = viewModel(),
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(mode, planId) { viewModel.initialize(mode, planId) }
    val requestExit = {
        if (state.isDirty) showDiscard = true else onCancel()
    }
    BackHandler { requestExit() }
    Box(Modifier.fillMaxSize().background(BgBlack).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (mode == "create") "Creando plan nutricional" else "Ajustando plan nutricional", color = WizardTeal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(wizardStepTitle(state.step), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth((state.stepIndex + 1) / NutritionWizardStep.entries.size.toFloat()).height(3.dp).clip(RoundedCornerShape(999.dp)).background(Color.White))
                }
            }
            AnimatedContent(
                targetState = state.step, modifier = Modifier.weight(1f),
                transitionSpec = { (slideInHorizontally { it / 5 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut(tween(180))) }, label = "wizardStep",
            ) { step ->
                when (step) {
                    NutritionWizardStep.GOAL -> GoalStep(state, viewModel)
                    NutritionWizardStep.DATA -> DataStep(state, viewModel)
                    NutritionWizardStep.GOALS -> GoalsStep(state, viewModel)
                    NutritionWizardStep.REVIEW -> ReviewStep(state, viewModel)
                }
            }
            if (state.errors.isNotEmpty() && (state.draft.ageText.isNotBlank() || state.draft.heightText.isNotBlank() || state.draft.weightText.isNotBlank() || state.step != NutritionWizardStep.DATA)) {
                Surface(color = Color(0xFF3A1A1A), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) { state.errors.values.forEach { Text(it, color = Color(0xFFFFD5D5), style = MaterialTheme.typography.bodySmall) } }
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.canGoBack) TextButton(onClick = viewModel::back, modifier = Modifier.height(52.dp)) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.size(6.dp)); Text("Atrás", color = Color.White) }
                Button(
                    onClick = { if (state.step == NutritionWizardStep.REVIEW) { if (viewModel.save() != null) onDone() } else viewModel.next() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WizardYellow, contentColor = Color(0xFF151719)), enabled = state.canContinue,
                ) { Text(if (state.step == NutritionWizardStep.REVIEW) "Guardar plan" else "Continuar", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = requestExit,
                    modifier = Modifier.size(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD64545), contentColor = Color.White),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Icon(Icons.Default.Close, "Salir") }
            }
        }
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false }, title = { Text("¿Descartar cambios?") },
        text = { Text("El borrador se conservará mientras esta pantalla siga abierta, pero cerrar ahora descarta sus cambios.") },
        confirmButton = { TextButton(onClick = { viewModel.markDiscarded(); showDiscard = false; onCancel() }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Seguir editando") } },
    )
}

@Composable
private fun GoalStep(state: NutritionWizardUiState, vm: NutritionWizardViewModel) {
    val haze = LocalHazeState.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¿Cuál es tu objetivo?", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Elige la dirección de tu plan. Podrás afinar el detalle más adelante.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        KpknGlassPill(haze, listOf("Definir" to PlanDirection.DEFICIT, "Mantener" to PlanDirection.MAINTENANCE, "Volumen" to PlanDirection.SURPLUS), state.draft.direction, vm::updateDirection)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(targetState = state.draft.direction, label = "goalHint") { dir ->
            val title: String; val body: String
            when (dir) {
                PlanDirection.DEFICIT -> { title = "Definir · perder grasa"; body = "Pequeño déficit para bajar grasa manteniendo músculo. Baja ~0,4–0,6% de tu peso por semana si eres constante. Proteína algo más alta y control del hambre." }
                PlanDirection.MAINTENANCE -> { title = "Mantener · estabilizar"; body = "Calorías cercanas a tu gasto. Ideal para mantener peso, recomponer y consolidar hábitos. Ajustaremos según tu evolución real." }
                PlanDirection.SURPLUS -> { title = "Volumen · ganar masa"; body = "Pequeño superávit para construir músculo sin ganar grasa de más. Subida lenta ~0,2–0,3%/sem. Entreno y descanso mandan." }
                else -> { title = ""; body = "Toca una opción: te explicamos qué cambia en calorías y ritmo." }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                if (title.isNotEmpty()) Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text(body, color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DataStep(state: NutritionWizardUiState, vm: NutritionWizardViewModel) {
    val pos = state.draft.physiqueSliderPos
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompactField(state.draft.ageText, vm::updateAge, "Edad", "25", KeyboardType.Number, Modifier.weight(1f))
            CompactField(state.draft.heightText, vm::updateHeight, "Altura cm", "175", KeyboardType.Decimal, Modifier.weight(1f))
            CompactField(state.draft.weightText, vm::updateWeight, "Peso ${state.draft.weightUnit}", "72", KeyboardType.Decimal, Modifier.weight(1f))
        }
        Text("¿Cuál de estas opciones te representa actualmente?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state.draft.equationSex == null) {
            SexPill(selected = null, onSelect = vm::updateEquationSex)
            Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Selecciona tu sexo para ver ejemplos visuales", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Usamos ♀/♂ solo para elegir la referencia correcta.", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Text("Selecciona tu sexo", color = WizardTeal, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.04f))) {
                val p = pos.coerceIn(1f, 7f)
                val lo = kotlin.math.floor(p.toDouble()).toInt().coerceIn(1, 7)
                val hi = kotlin.math.ceil(p.toDouble()).toInt().coerceIn(1, 7)
                val frac = (p - lo).coerceIn(0f, 1f)
                val sex = state.draft.equationSex
                val loId = wizardPhysiqueDrawableId(lo, sex)
                val hiId = wizardPhysiqueDrawableId(hi, sex)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = loId), contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                    Image(painter = painterResource(id = hiId), contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit, alpha = frac)
                }
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    SexPillCompact(selected = state.draft.equationSex, onSelect = vm::updateEquationSex)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VerticalPhysiqueSlider(pos = pos, onPosChange = vm::updatePhysiqueSliderPos)
                        Text("${bodyFatForSliderPos(pos).toInt()}%", color = WizardTeal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val label = remember(pos) { physiqueLabelForSliderPos(pos) }
            val desc = remember(pos) { physiqueDescForSliderPos(pos) }
            Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(desc, color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("¿Conoces tu % de grasa?", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                val haze = LocalHazeState.current
                MiniGlassChip(selected = state.draft.knowsBodyFat, onClick = { vm.updateKnowsBodyFat(!state.draft.knowsBodyFat) }, label = if (state.draft.knowsBodyFat) "Ocultar" else "Añadir", haze = haze)
            }
            AnimatedVisibility(visible = state.draft.knowsBodyFat, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactField(state.draft.bodyFatText, vm::updateBodyFat, "% grasa", "${bodyFatForSliderPos(pos).toInt()}", KeyboardType.Decimal, Modifier.weight(1f))
                    CompactField(state.draft.muscleText, vm::updateMuscle, "% músculo", "39", KeyboardType.Decimal, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SexPill(selected: EerSex?, onSelect: (EerSex) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val haze = LocalHazeState.current
    Row(
        Modifier.fillMaxWidth().clip(shape).then(if (haze != null) Modifier.kpknGlassOrFallback(haze, shape) else Modifier.background(Color.White.copy(alpha = 0.08f), shape))
            .border(1.dp, GlassBorder, shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("♀ Mujer" to EerSex.FEMALE, "♂ Hombre" to EerSex.MALE).forEach { (lbl, sex) ->
            val sel = selected == sex
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(if (sel) Color.White else Color.Transparent).clickable { onSelect(sex) }
                    .padding(vertical = 18.dp), contentAlignment = Alignment.Center,
            ) { Text(lbl, color = if (sel) Color.Black else Color.White.copy(alpha = 0.92f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun SexPillCompact(selected: EerSex?, onSelect: (EerSex) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(shape).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, GlassBorder, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("♀" to EerSex.FEMALE, "♂" to EerSex.MALE).forEach { (lbl, sex) ->
            val sel = selected == sex
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (sel) Color.White else Color.Transparent).clickable { onSelect(sex) },
                contentAlignment = Alignment.Center,
            ) { Text(lbl, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun VerticalPhysiqueSlider(pos: Float, onPosChange: (Float) -> Unit) {
    var hPx by remember { mutableStateOf(1f) }
    val frac = ((pos - 1f) / 6f).coerceIn(0f, 1f)
    Box(
        Modifier.width(22.dp).height(360.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, GlassBorder, RoundedCornerShape(999.dp)).onSizeChanged { hPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y.coerceIn(0f, hPx)
                    val p = 1f + (y / hPx) * 6f
                    onPosChange(p.coerceIn(1f, 7f))
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            val y = ev.changes.firstOrNull()?.position?.y ?: continue
                            val p = 1f + (y.coerceIn(0f, hPx) / hPx) * 6f
                            onPosChange(p.coerceIn(1f, 7f))
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxSize().padding(vertical = 8.dp).width(2.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.14f)))
        val thumbPad = with(LocalDensity.current) { (frac * (hPx - 18.dp.toPx())).coerceAtLeast(0f).toDp() }
        Box(Modifier.padding(top = thumbPad).size(18.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.10f), CircleShape))
    }
}

@Composable
private fun GoalsStep(state: NutritionWizardUiState, vm: NutritionWizardViewModel) {
    val haze = LocalHazeState.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Cuéntanos cómo es tu día a día y hacia dónde quieres ir. Usamos lenguaje simple.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        Text("¿Cómo es tu día a día?", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        val actLabels = mapOf(EerActivity.INACTIVE to "Tranquilo", EerActivity.LOW_ACTIVE to "Algo activo", EerActivity.ACTIVE to "Activo", EerActivity.VERY_ACTIVE to "Muy activo")
        val actDescs = mapOf(
            EerActivity.INACTIVE to "Pasas la mayor parte del día sentado y te mueves poco. Ideal si tu trabajo es de oficina.",
            EerActivity.LOW_ACTIVE to "Caminas, haces recados y te mueves ligero varios días a la semana.",
            EerActivity.ACTIVE to "Te mueves a diario y entrenas 3–5 veces por semana. Buen nivel de actividad.",
            EerActivity.VERY_ACTIVE to "Trabajo físico o entreno exigente casi todos los días. Gasto alto.",
        )
        EerActivity.entries.forEach { act ->
            val sel = state.draft.activity == act
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).clickable { vm.updateActivity(act) }
                    .then(if (haze != null) Modifier.kpknGlassOrFallback(haze, RoundedCornerShape(999.dp)) else Modifier.background(if (sel) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp)))
                    .border(1.dp, if (sel) Color.White.copy(alpha = 0.18f) else GlassBorder, RoundedCornerShape(999.dp)),
                color = Color.Transparent, shape = RoundedCornerShape(999.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (sel) Color.White else Color.White.copy(alpha = 0.35f)))
                    Text(actLabels[act] ?: act.name, color = if (sel) Color.White else Color.White.copy(alpha = 0.85f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        AnimatedContent(targetState = state.draft.activity, label = "actDesc") { act ->
            Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(actDescs[act] ?: "", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        Text("Tu meta", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        CompactField(state.draft.targetWeightText, vm::updateTargetWeight, "Peso meta (${state.draft.weightUnit})", state.draft.weightText.ifBlank { "70" }, KeyboardType.Decimal, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompactField(state.draft.targetBodyFatText, vm::updateTargetBodyFat, "% grasa objetivo (opcional)", "15", KeyboardType.Decimal, Modifier.weight(1f))
            CompactField(state.draft.targetMuscleText, vm::updateTargetMuscle, "% músculo objetivo (opcional)", "42", KeyboardType.Decimal, Modifier.weight(1f))
        }
        Text("Opcional — si lo completas afinamos la fecha estimada.", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReviewStep(state: NutritionWizardUiState, vm: NutritionWizardViewModel) {
    val haze = LocalHazeState.current
    val rec = state.recommendation
    val bounds = state.calorieBounds
    val kcal = state.effectiveKcal
    val macros = state.effectiveMacros
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(148.dp)) { AnimatedMacroRing(kcal, macros, state) } }
        if (rec != null) Text("EER ${rec.eerKcal?.toInt() ?: "—"} kcal · Objetivo $kcal kcal" + (bounds?.let { "  ·  ${it.first}–${it.last}" } ?: ""), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        GlassSlider(label = "Calorías", value = kcal.toFloat(), range = (bounds?.first?.toFloat() ?: 1200f)..(bounds?.last?.toFloat() ?: 3500f), display = "$kcal kcal", onValueChange = { vm.updateCaloriesSlider(it.roundToInt()) }, color = CalColor, height = 14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Macros", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::toggleAdvancedMacros) {
                Text(if (state.draft.showAdvancedMacros) "Ocultar avanzado" else "Avanzado", color = Color.White)
                Icon(if (state.draft.showAdvancedMacros) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.White)
            }
        }
        AnimatedVisibility(visible = state.draft.showAdvancedMacros, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSlider(label = "Proteína", value = (macros?.proteinG ?: 0.0).toFloat(), range = 0f..400f, display = "${macros?.proteinG?.roundToInt() ?: 0} g", onValueChange = { vm.updateMacroSlider(protein = it.toDouble(), carbs = null, fat = null) }, color = ProColor)
                GlassSlider(label = "Carbohidratos", value = (macros?.carbsG ?: 0.0).toFloat(), range = 0f..600f, display = "${macros?.carbsG?.roundToInt() ?: 0} g", onValueChange = { vm.updateMacroSlider(protein = null, carbs = it.toDouble(), fat = null) }, color = CarbColor)
                GlassSlider(label = "Grasas", value = (macros?.fatG ?: 0.0).toFloat(), range = 0f..300f, display = "${macros?.fatG?.roundToInt() ?: 0} g", onValueChange = { vm.updateMacroSlider(protein = null, carbs = null, fat = it.toDouble()) }, color = FatColor)
            }
        }
        if (bounds != null) Text("Rango permitido ${bounds.first}–${bounds.last} kcal", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state.isContradictoryDeficit) {
            Surface(color = Color(0xFF3A1A1A), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Con estas calorías estás por encima de tu gasto: no perderás grasa. Baja las calorías por debajo de ${rec?.eerKcal?.toInt() ?: "tu EER"} o cambia a Mantener.", color = Color(0xFFFFD5D5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
        Text("¿A qué ritmo?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(wizardPaceDesc(state.draft.pacePreset, state.draft.direction), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
        KpknGlassPill(haze, listOf("Lento" to WizardPacePreset.SLOW, "Medio" to WizardPacePreset.MEDIUM, "Rápido" to WizardPacePreset.FAST), state.draft.pacePreset, vm::updatePacePreset)
        RoadmapTimeline(startDate = LocalDate.now(), endDateStr = state.estimatedEndDate)
    }
}

private fun wizardStepTitle(step: NutritionWizardStep): String = when (step) {
    NutritionWizardStep.GOAL -> "Objetivo"
    NutritionWizardStep.DATA -> "Datos"
    NutritionWizardStep.GOALS -> "Actividad y meta"
    NutritionWizardStep.REVIEW -> "Calorías sugeridas"
}

private fun wizardPaceDesc(preset: WizardPacePreset, dir: PlanDirection?): String = when (dir) {
    PlanDirection.DEFICIT -> when (preset) {
        WizardPacePreset.SLOW -> "Lento: ~0,25% por semana. Más sostenible, menos hambre."
        WizardPacePreset.MEDIUM -> "Medio: ~0,5% por semana. Equilibrio entre velocidad y adherencia."
        WizardPacePreset.FAST -> "Rápido: ~0,8% por semana. Más exigente, vigila energía y descanso."
    }
    PlanDirection.SURPLUS -> when (preset) {
        WizardPacePreset.SLOW -> "Lento: ~0,15% por semana. Ganancia muy magra."
        WizardPacePreset.MEDIUM -> "Medio: ~0,25% por semana. Recomendado."
        WizardPacePreset.FAST -> "Rápido: ~0,4% por semana. Puede sumar más grasa."
    }
    else -> "Mantener: sin ritmo semanal objetivo."
}

@Composable
private fun RoadmapTimeline(startDate: LocalDate, endDateStr: String?) {
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    val endDate = remember(endDateStr) { try { endDateStr?.let { LocalDate.parse(it) } } catch (_: Exception) { null } }
    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fecha estimada de la meta", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            if (endDate != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Hoy", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.labelSmall)
                        Text(startDate.format(fmt), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(1)
                        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(startDate, endDate).coerceAtLeast(1)
                        Text(if (days < 7) "Menos de 1 semana" else "$weeks semanas", color = WizardTeal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f)))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Meta", color = Color.White.copy(alpha = 0.70f), style = MaterialTheme.typography.labelSmall)
                        Text(endDate.format(fmt), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text("Completa peso y meta para estimar la fecha.", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun <T> KpknGlassPill(haze: dev.chrisbanes.haze.HazeState?, options: List<Pair<String, T>>, selected: T?, onSelect: (T) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(Modifier.fillMaxWidth().clip(shape).then(if (haze != null) Modifier.kpknGlassOrFallback(haze, shape) else Modifier.background(Color.White.copy(alpha = 0.06f), shape)).border(1.dp, GlassBorder, shape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (label, value) ->
            val sel = value == selected
            Box(Modifier.weight(1f).clip(shape).background(if (sel) Color.White else Color.Transparent, shape).clickable { onSelect(value) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (sel) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Black))
                    Text(label, color = if (sel) Color.Black else Color.White.copy(alpha = 0.92f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MiniGlassChip(selected: Boolean, onClick: () -> Unit, label: String, haze: dev.chrisbanes.haze.HazeState?) {
    val shape = RoundedCornerShape(999.dp)
    Box(Modifier.clip(shape).then(if (haze != null) Modifier.kpknGlassOrFallback(haze, shape) else Modifier.background(if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f), shape)).border(1.dp, if (selected) Color.White.copy(alpha = 0.18f) else GlassBorder, shape).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompactField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, keyboardType: KeyboardType, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
        Surface(color = InputBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))) {
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.35f), style = MaterialTheme.typography.bodyMedium); inner() },
            )
        }
    }
}

@Composable
private fun GlassSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValueChange: (Float) -> Unit, color: Color, height: androidx.compose.ui.unit.Dp = 6.dp) {
    var widthPx by remember { mutableStateOf(1f) }
    val fraction = if (range.endInclusive > range.start) ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text(display, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.12f))
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(range, color) {
                    detectHorizontalDragGestures { change, _ ->
                        val x = change.position.x.coerceIn(0f, widthPx)
                        val v = range.start + (x / widthPx) * (range.endInclusive - range.start)
                        onValueChange(v.coerceIn(range.start, range.endInclusive))
                    }
                }
                .pointerInput(range, color) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                                val x = ev.changes.firstOrNull()?.position?.x ?: continue
                                val v = range.start + (x.coerceIn(0f, widthPx) / widthPx) * (range.endInclusive - range.start)
                                onValueChange(v.coerceIn(range.start, range.endInclusive))
                            }
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(height).clip(RoundedCornerShape(999.dp)).background(color))
            val thumbOffset = with(LocalDensity.current) { (fraction * (widthPx - 18.dp.toPx())).coerceAtLeast(0f).toDp() }
            Box(Modifier.padding(start = thumbOffset).size(if (height > 10.dp) 22.dp else 18.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape))
        }
    }
}

@Composable
private fun AnimatedMacroRing(kcal: Int, macros: com.example.kpkn.domain.nutrition.NutritionMacroTargets?, state: NutritionWizardUiState) {
    val weightKg = com.example.kpkn.domain.nutrition.parseLocalizedNumber(state.draft.weightText)
        ?.let { com.example.kpkn.domain.nutrition.kilogramsFromInput(it, state.draft.weightUnit) }
    val proMax = ((weightKg ?: 70.0) * 2.6).coerceAtLeast(140.0)
    val calPct by animateFloatAsState((kcal / 3500f).coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "calPct")
    val proPct by animateFloatAsState(((macros?.proteinG ?: 0.0) / proMax).toFloat().coerceIn(0f, 1f), tween(700), label = "proPct")
    val carbPct by animateFloatAsState(((macros?.carbsG ?: 0.0) / 450.0).toFloat().coerceIn(0f, 1f), tween(700), label = "carbPct")
    val fatPct by animateFloatAsState(((macros?.fatG ?: 0.0) / 160.0).toFloat().coerceIn(0f, 1f), tween(700), label = "fatPct")
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 10.dp.toPx(); val gap = 3.dp.toPx()
        val cx = size.width / 2; val cy = size.height / 2
        val r1 = size.minDimension / 2 - stroke / 2; val r2 = r1 - stroke - gap; val r3 = r2 - stroke - gap; val r4 = r3 - stroke - gap
        fun ring(r: Float, pct: Float, c: Color) {
            val d = r * 2
            drawArc(c.copy(alpha = 0.12f), 0f, 360f, false, Offset(cx - r, cy - r), Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            if (pct > 0f) drawArc(c, -90f, 360f * pct.coerceAtMost(1f), false, Offset(cx - r, cy - r), Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        ring(r1, calPct, CalColor); ring(r2, proPct, ProColor); ring(r3, carbPct, CarbColor); ring(r4, fatPct, FatColor)
    }
}

private fun wizardPhysiqueDrawableId(group: Int, sex: EerSex?): Int {
    val g = group.coerceIn(1, 7)
    val female = sex == EerSex.FEMALE
    return when (g) {
        1 -> if (female) R.drawable.wizard_m_08_12 else R.drawable.wizard_h_08_12
        2 -> if (female) R.drawable.wizard_m_13_17 else R.drawable.wizard_h_13_17
        3 -> if (female) R.drawable.wizard_m_18_22 else R.drawable.wizard_h_18_22
        4 -> if (female) R.drawable.wizard_m_23_27 else R.drawable.wizard_h_23_27
        5 -> if (female) R.drawable.wizard_m_28_32 else R.drawable.wizard_h_28_32
        6 -> if (female) R.drawable.wizard_m_33_37 else R.drawable.wizard_h_33_37
        else -> if (female) R.drawable.wizard_m_38p else R.drawable.wizard_h_38p
    }
}
