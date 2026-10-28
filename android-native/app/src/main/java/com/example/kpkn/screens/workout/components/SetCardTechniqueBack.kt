package com.example.kpkn.screens.workout.components

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.role
import com.example.kpkn.screens.onboarding.design.WizardFonts
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.training.NativeProgressionText
import com.example.kpkn.domain.workout.isMyoReps
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.LiveSetPrPolicy
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

internal data class TechniqueCheckRow(
    val weightText: String = "",
    val repsText: String = RestPausePlanDefaults.Reps.toString(),
    val restText: String = RestPausePlanDefaults.PauseSeconds.toString(),
    val done: Boolean = false,
)

private const val LiveDropSetMax = 5

/** Etiqueta de prueba del círculo «Hecho» del eslabón abierto del drop-set o rest-pause (LR0). */
internal const val TechniqueDoneToggleTag = "technique_done_toggle"

/** Etiqueta de prueba de la cadena de la serie (A2) y de cada eslabón. */
internal const val TechniqueChainTag = "technique_chain"
internal const val TechniqueLinkTag = "technique_link"

/** Segmentos de la técnica (A2, sin «Myo-reps»: no existe en la app) en el orden en que se muestran. */
private val TechniqueSegments = listOf("Normal", "Drop-set", "Rest-pause", "Myo-reps")

/** Alto de la zona táctil de los botones −/+ y de cada celda del eslabón abierto. */
private val StepCellHeight = 44.dp
private val StepButtonSize = 32.dp
private val ChainLinkWidth = 72.dp
private val ChainLinkHeight = 58.dp

/**
 * Reverso de «Avanzado» de la tarjeta de la serie (LV8, A2): el segmentado «Normal · Drop-set · Rest-pause», la CADENA de la serie en
 * horizontal (Principal → Drop 1 → Drop 2 → +) con solo el eslabón activo abierto en una fila (kg y reps con −/+), la explicación del
 * rest-pause o del drop-set en una línea de bloques y la nota de la serie. Conserva lo que ya hacía el reverso: el fallo («Error de
 * ejecución»), la nota, los drops y el rest-pause (los mismos estados y el mismo registro) y el atajo de la foto del récord.
 *
 * El contenido va en una zona que se desplaza dentro de la tarjeta: lo que no cabe no la alarga (ley 1: nunca cambia de tamaño).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SetCardTechniqueBack(
    currentSet: ExerciseSet,
    coverWeightLabel: String,
    coverValueLabel: String,
    coverIntensityLabel: String?,
    mainWeight: Double,
    mainReps: Int,
    sessionAccentColor: Color,
    isFailedSet: Boolean,
    onFailedSetChange: (Boolean) -> Unit,
    onFlipBack: () -> Unit,
    onCompleteTechniques: (dropSets: List<DropSetData>, restPauses: List<RestPauseData>) -> Unit,
    noteText: String = "",
    onNoteChange: (String) -> Unit = {},
    initialDropEnabled: Boolean = false,
    initialRestPauseEnabled: Boolean = false,
    onDropEnabledChange: (Boolean) -> Unit = {},
    onRestPauseEnabledChange: (Boolean) -> Unit = {},
    showPrMediaShortcut: Boolean = false,
    onOpenPrMedia: (() -> Unit)? = null,
    /** «Serie 3» en el botón de volver; sin número, «Serie». */
    setNumber: Int? = null,
    /** Paso de los botones −/+ del peso del drop: el de la carga de la tarjeta (2,5 kg; 1 en mancuernas; 5 en máquinas). */
    loadStepKg: Double = 2.5,
    loadMode: com.example.kpkn.data.models.LoadModeV2 = com.example.kpkn.data.models.LoadModeV2.LOAD,
    modifier: Modifier = Modifier,
) {
    val plannedGuide = remember(currentSet) {
        // Volume-replaced editor plans run inside the front card controls.
        // Never arm the manual back-face editors for them: the back card is
        // a flip replacement, not an overlay, and must not resurrect a
        // second technique surface on top of the scheduled flow.
        if (currentSet.techniqueScope() == SetTechniqueScope.VOLUME_REPLACED) null
        else currentSet.resolvePlannedTechniqueGuide()
    }
    var myo by remember(currentSet.id) { mutableStateOf(currentSet.isMyoReps()) }
    val restParams = currentSet.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }?.params.orEmpty()
    var dropEnabled by remember(currentSet.id) {
        mutableStateOf(initialDropEnabled || plannedGuide?.kind == TechniqueType.DROP_SET)
    }
    var restPauseEnabled by remember(currentSet.id) {
        mutableStateOf(initialRestPauseEnabled || plannedGuide?.kind == TechniqueType.REST_PAUSE)
    }
    var dropRows by remember(currentSet.id, mainWeight, mainReps) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.DROP_SET }
            ?: DropSetPlanDefaults.DefaultDrops
        val suggestions = suggestedDropLoadsForMainSet(mainWeight, mainReps, count, loadMode)
        mutableStateOf(
            suggestions.map { load ->
                TechniqueCheckRow(
                    weightText = if (load > 0) load.toTrimmedNumberString() else "",
                    repsText = RestPausePlanDefaults.Reps.toString(),
                )
            }.ifEmpty {
                listOf(TechniqueCheckRow())
            },
        )
    }
    var restPauseRows by remember(currentSet.id) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.REST_PAUSE }
            ?: if(myo) 4 else RestPausePlanDefaults.DefaultCount
        mutableStateOf(
            List(count.coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)) {
                TechniqueCheckRow(repsText = (restParams["reps"]?.toIntOrNull() ?: if(myo) 4 else RestPausePlanDefaults.Reps).toString(),
                    restText = (restParams["pauseSeconds"]?.toIntOrNull() ?: RestPausePlanDefaults.PauseSeconds).toString())
            },
        )
    }
    var committed by remember(currentSet.id) { mutableStateOf(false) }
    // El eslabón abierto de la cadena: el primero al empezar y el nuevo al añadir.
    var activeDrop by remember(currentSet.id) { mutableIntStateOf(0) }
    var activePause by remember(currentSet.id) { mutableIntStateOf(0) }

    LaunchedEffect(dropEnabled) { onDropEnabledChange(dropEnabled) }
    LaunchedEffect(restPauseEnabled) { onRestPauseEnabledChange(restPauseEnabled) }
    // Con todas las filas marcadas como hechas, la técnica se registra en la serie (LR0).
    LaunchedEffect(dropEnabled, restPauseEnabled, dropRows, restPauseRows, committed, myo) {
        if (committed) return@LaunchedEffect
        val dropsReady = !dropEnabled || shouldAutoCommitTechniqueRows(dropRows.map { it.done })
        val pausesReady = !restPauseEnabled || (shouldAutoCommitTechniqueRows(restPauseRows.map { it.done }) && (!myo || restPauseRows.all { (it.repsText.toIntOrNull() ?: 0) in 3..5 && (it.restText.toIntOrNull() ?: 0) in 10..30 }))
        val hasTechnique = dropEnabled || restPauseEnabled
        if (hasTechnique && dropsReady && pausesReady &&
            ((dropEnabled && dropRows.isNotEmpty()) || (restPauseEnabled && restPauseRows.isNotEmpty()))
        ) {
            val submitted = commit(dropRows, restPauseRows, dropEnabled, restPauseEnabled) { drops, pauses ->
                onCompleteTechniques(drops, if(myo) pauses.map { it.copy(style="myo") } else pauses)
            }
            if (submitted) committed = true
        }
    }

    val segment = when {
        dropEnabled -> 1
        restPauseEnabled && myo -> 3
        restPauseEnabled -> 2
        else -> 0
    }
    val mainLabel = techniqueBlock(mainWeight, mainReps.toString())

    // Compacto según el alto real de la cara (la variante `.compact` del prototipo en el marco de 394).
    val densityNow = LocalDensity.current
    val frameCompact = com.example.kpkn.screens.workout.liveSessionCardHeight() < 440.dp
    var compactCard by remember(frameCompact) { mutableStateOf(frameCompact) }
    val inset = if (compactCard) 15.dp else 18.dp
    // El tramo que enseña el conmutador: 0 es la serie principal; 1…n, los drops o las pausas.
    var segmentIndex by remember(currentSet.id) { mutableIntStateOf(0) }
    var chainSheet by remember { mutableStateOf(false) }
    var noteSheet by remember { mutableStateOf(false) }
    var failureSheet by remember { mutableStateOf(false) }
    val rows = when { dropEnabled -> dropRows; restPauseEnabled -> restPauseRows; else -> emptyList() }
    val safeSegment = segmentIndex.coerceIn(0, rows.size)
    // «Volver al registro» guarda la técnica en la serie (con sus tramos) y vuelve al frente; sin técnica, solo vuelve.
    fun leave() {
        val submitted = (dropEnabled || restPauseEnabled) && commit(dropRows, restPauseRows, dropEnabled, restPauseEnabled) { drops, pauses ->
            onCompleteTechniques(drops, if (myo) pauses.map { it.copy(style = "myo") } else pauses)
        }
        if (!submitted) onFlipBack()
    }
    fun addSegment() {
        if (dropEnabled && dropRows.size < LiveDropSetMax) {
            val nextIndex = dropRows.size
            val suggested = nextDropWeightKg(mainWeight, mainReps, nextIndex, dropRows.lastOrNull()?.weightText, loadMode)
            dropRows = dropRows + TechniqueCheckRow(weightText = suggested?.takeIf { it > 0 }?.toTrimmedNumberString().orEmpty(),
                repsText = DropSetPlanDefaults.DropReps.toString())
            segmentIndex = dropRows.size
        } else if (restPauseEnabled && restPauseRows.size < RestPausePlanDefaults.MaxCount) {
            restPauseRows = restPauseRows + TechniqueCheckRow(repsText = (if (myo) 4 else RestPausePlanDefaults.Reps).toString(),
                restText = (if (myo) 15 else RestPausePlanDefaults.PauseSeconds).toString())
            segmentIndex = restPauseRows.size
        }
    }
    val canAdd = (dropEnabled && dropRows.size < LiveDropSetMax) || (restPauseEnabled && restPauseRows.size < RestPausePlanDefaults.MaxCount)

    // `advancedCard` del prototipo 02 en el marco de la tarjeta (radio 25, #111).
    Column(
        modifier.fillMaxSize().onSizeChanged { compactCard = with(densityNow) { it.height.toDp() } < 440.dp }
            .clip(RoundedCornerShape(25.dp)).background(Color(0xFF111111))
            .border(1.dp, if (isFailedSet) WizardColors.danger.copy(alpha = .5f) else Color(0x0BFFFFFF), RoundedCornerShape(25.dp))
            .testTag("technique-body"),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalAdvancedCompact provides compactCard) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = inset, end = inset, top = if (compactCard) 9.dp else 13.dp)) {
            Row(Modifier.fillMaxWidth().height(if (compactCard) 27.dp else 29.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.heightIn(min = 30.dp).plainClickable(::leave).semantics { contentDescription = "Volver a la serie" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(14.dp), tint = AdvancedInk)
                    Text(if (setNumber != null) "Serie $setNumber" else "Serie", style = advancedBody(11.sp), color = AdvancedInk)
                }
                Spacer(Modifier.weight(1f))
                Text("AVANZADO", style = advancedBody(9.sp, letterSpacing = .65.sp), color = AdvancedMuted)
            }
            // El bloque central cede alto: los enlaces de abajo se miden antes y siempre caben.
            Column(Modifier.weight(1f, fill = true).fillMaxWidth()) {
            // `.v2-tech-tabs`: Normal · Drop · Rest-pause · Myo.
            Row(Modifier.fillMaxWidth().heightIn(min = if (compactCard) 32.dp else 36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Normal", "Drop", "Rest-pause", "Myo").forEachIndexed { index, label ->
                    val selected = segment == index
                    Box(
                        Modifier.weight(1f).heightIn(min = if (compactCard) 32.dp else 36.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AdvancedInk else Color(0x05FFFFFF))
                            .plainClickable {
                                when (index) {
                                    1 -> { dropEnabled = true; restPauseEnabled = false }
                                    2 -> {
                                        if (myo) restPauseRows = List(RestPausePlanDefaults.DefaultCount) { TechniqueCheckRow() }
                                        myo = false; restPauseEnabled = true; dropEnabled = false
                                    }
                                    3 -> {
                                        if (!myo) restPauseRows = List(4) { TechniqueCheckRow(repsText = "4", restText = "15") }
                                        myo = true; restPauseEnabled = true; dropEnabled = false; committed = false
                                    }
                                    else -> { dropEnabled = false; restPauseEnabled = false }
                                }
                                segmentIndex = 0
                            }
                            .semantics { this.selected = selected; role = Role.Tab }
                            .padding(horizontal = 5.dp, vertical = 6.dp)
                            .testTag("technique-tab-$index"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = advancedBody(if (compactCard) 10.sp else 11.sp, lineHeight = 13.sp), color = if (selected) Color(0xFF111111) else AdvancedInk,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            // `.v2-chain-head`: «CADENA DE LA SERIE» y «N tramos ›» (abre la cadena entera).
            Row(Modifier.fillMaxWidth().heightIn(min = if (compactCard) 36.dp else 43.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CADENA DE LA SERIE", Modifier.weight(1f), style = advancedBody(9.sp, letterSpacing = .6.sp), color = AdvancedFaint)
                Row(
                    Modifier.heightIn(min = 32.dp).plainClickable { chainSheet = true }.testTag(TechniqueChainTag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("${rows.size + 1} tramos", style = advancedBody(11.sp), color = AdvancedMuted)
                    Icon(KpknLineIcons.ChevronRight, null, Modifier.size(12.dp), tint = AdvancedMuted)
                }
            }
            // `.v2-segment-switch`: ‹ PRINCIPAL 100 × 8 ›.
            val active = rows.getOrNull(safeSegment - 1)
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (compactCard) 58.dp else 71.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A1A))
                    .padding(horizontal = 4.dp, vertical = if (compactCard) 5.dp else 9.dp).testTag(TechniqueLinkTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SegmentArrow(KpknLineIcons.ChevronLeft, "Tramo anterior", safeSegment > 0) { segmentIndex = safeSegment - 1 }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            safeSegment == 0 -> "PRINCIPAL"
                            dropEnabled -> "DROP $safeSegment"
                            else -> "TRAMO $safeSegment"
                        },
                        Modifier.padding(bottom = if (compactCard) 2.dp else 5.dp), style = advancedBody(8.sp, letterSpacing = 1.sp), color = AdvancedFaint,
                    )
                    Text(
                        when {
                            active == null -> mainLabel
                            dropEnabled -> techniqueBlock(active.weightText, active.repsText)
                            else -> techniqueBlock(mainWeight, active.repsText)
                        },
                        style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = if (compactCard) 19.sp else 23.sp,
                            fontFeatureSettings = "'tnum' 1"),
                        color = AdvancedInk,
                    )
                }
                SegmentArrow(KpknLineIcons.ChevronRight, "Tramo siguiente", safeSegment < rows.size) { segmentIndex = safeSegment + 1 }
            }
            if (active != null) {
                val index = safeSegment - 1
                Row(Modifier.fillMaxWidth().padding(top = if (compactCard) 8.dp else 13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (dropEnabled) {
                        AdvancedField("Carga · kg", active.weightText, KeyboardType.Decimal, Modifier.weight(1f)) { text ->
                            dropRows = dropRows.updatedAt(index) { it.copy(weightText = text) }
                        }
                        AdvancedField("Repeticiones", active.repsText, KeyboardType.Number, Modifier.weight(1f)) { text ->
                            dropRows = dropRows.updatedAt(index) { it.copy(repsText = text) }
                        }
                    } else {
                        AdvancedField("Pausa · s", active.restText, KeyboardType.Number, Modifier.weight(1f)) { text ->
                            restPauseRows = restPauseRows.updatedAt(index) { it.copy(restText = text) }
                        }
                        AdvancedField("Repeticiones", active.repsText, KeyboardType.Number, Modifier.weight(1f)) { text ->
                            restPauseRows = restPauseRows.updatedAt(index) { it.copy(repsText = text) }
                        }
                    }
                }
            } else {
                Text("Carga, repeticiones y esfuerzo de la serie principal se conservan.", Modifier.padding(vertical = 12.dp),
                    style = advancedBody(11.sp, lineHeight = 17.sp), color = AdvancedMuted)
            }
            }
            // `.v2-advanced-links`: nota, añadir tramo y error de ejecución, cada uno con su filete.
            AdvancedLink(if (noteText.isBlank()) "Añadir nota" else "Ver nota", KpknLineIcons.Edit, chevron = true) { noteSheet = true }
            if (dropEnabled || restPauseEnabled) {
                AdvancedLink("Añadir tramo", KpknLineIcons.Plus, enabled = canAdd, onClick = ::addSegment)
            }
            AdvancedLink(if (isFailedSet) "Error de ejecución · marcado" else "Error de ejecución", null, danger = true) { failureSheet = true }
        }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = if (compactCard) 51.dp else 57.dp)
                .padding(start = inset, end = inset, top = if (compactCard) 4.dp else 6.dp, bottom = if (compactCard) 9.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 39.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
                    .plainClickable(::leave).testTag("technique-back-to-record"),
                contentAlignment = Alignment.Center,
            ) { Text("Volver al registro", style = advancedBody(12.sp, FontWeight.SemiBold), color = AdvancedInk) }
        }
    }

    if (chainSheet) {
        // `tech-chain`: la cadena entera; tocar un tramo lo abre en el conmutador.
        LiveSheet(title = "Cadena de la serie", onDismiss = { chainSheet = false }, tag = "technique-chain-sheet", footer = null) { close ->
            LiveSheetSubtitle("Principal $mainLabel. Selecciona un tramo para editarlo.")
            rows.forEachIndexed { index, row ->
                LiveSheetMenuRow(
                    "${if (dropEnabled) "Drop" else "Tramo"} ${index + 1} · " +
                        (if (dropEnabled) techniqueBlock(row.weightText, row.repsText) else techniqueBlock(mainWeight, row.repsText)),
                    { segmentIndex = index + 1; close() },
                )
            }
            if (dropEnabled || restPauseEnabled) {
                LiveSheetSecondary("Añadir tramo", { addSegment(); close() }, Modifier.padding(top = 12.dp), enabled = canAdd, icon = KpknLineIcons.Plus)
            }
        }
    }
    if (noteSheet) {
        LiveSheet(title = "Nota de la serie", onDismiss = { noteSheet = false }, tag = "technique-note-sheet") {
            LiveSheetSubtitle(if (setNumber != null) "Serie $setNumber" else "Serie")
            LiveSheetLabeledField("Observación", noteText, onNoteChange, multiline = true, placeholder = "Escribe una observación…")
        }
    }
    if (failureSheet) {
        LiveSheet(title = "Error de ejecución", onDismiss = { failureSheet = false }, tag = "technique-failure-sheet", footer = null) { close ->
            LiveSheetSubtitle("Señala una incidencia de ejecución. Llegar al fallo muscular se informa en esfuerzo.")
            LiveSheetSecondary(if (isFailedSet) "Quitar error de ejecución" else "Marcar error de ejecución",
                { onFailedSetChange(!isFailedSet); close() }, Modifier.padding(top = 12.dp), tag = "technique-mark-failed")
        }
    }
}

private val AdvancedInk = Color(0xFFF2EEE6)
private val AdvancedMuted = Color(0xFFB9B5AE)
private val AdvancedFaint = Color(0xFF92908A)

private fun advancedBody(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) =
    TextStyle(fontFamily = WizardFonts.body, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = letterSpacing)

private val LocalAdvancedCompact = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun compactAdvanced(): Boolean = LocalAdvancedCompact.current

@Composable
private fun SegmentArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 34.dp, height = 44.dp).then(if (enabled) Modifier.plainClickable(onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(16.dp), tint = AdvancedInk.copy(alpha = if (enabled) 1f else .3f)) }
}

@Composable
private fun AdvancedField(label: String, value: String, keyboard: KeyboardType, modifier: Modifier, onChange: (String) -> Unit) {
    val compact = compactAdvanced()
    Column(modifier) {
        if (!compact) Text(label, style = advancedBody(10.sp), color = AdvancedMuted)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = advancedBody(14.sp, FontWeight.SemiBold).copy(color = AdvancedInk),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.padding(top = if (compactAdvanced()) 3.dp else 6.dp).fillMaxWidth()
                .height(if (compactAdvanced()) 32.dp else 38.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x0DFFFFFF)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp).wrapContentHeight(Alignment.CenterVertically)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // En compacto la etiqueta va dentro del campo, a la izquierda del valor.
                    if (compact) Text(label, Modifier.padding(end = 8.dp), style = advancedBody(9.sp), color = AdvancedFaint)
                    Box(Modifier.weight(1f)) { inner() }
                }
            },
        )
    }
}

@Composable
private fun AdvancedLink(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    chevron: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = (if (danger) Color(0xFFFF9B92) else AdvancedMuted).copy(alpha = if (enabled) 1f else .4f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compactAdvanced()) 24.dp else 31.dp)
            .drawBehind { drawLine(Color(0x08FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .then(if (enabled) Modifier.plainClickable(onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = tint) }
        Text(text, Modifier.weight(1f), style = advancedBody(10.sp), color = tint)
        if (chevron) Icon(KpknLineIcons.ChevronRight, null, Modifier.size(12.dp), tint = tint)
    }
}

/** Toque sin ripple de Material (LR1a): el mismo `onClick`, con `Role.Button` y sin indicación. */
@Composable
private fun Modifier.plainClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** «95 × 6»: la carga y las reps de un bloque (la carga con coma decimal; vacía, «—»). */
private fun techniqueBlock(weight: Double, reps: String): String =
    "${NativeProgressionText.formatKg(weight)} × ${reps.ifBlank { "—" }}"

private fun techniqueBlock(weightText: String, repsText: String): String =
    "${weightText.ifBlank { "—" }} × ${repsText.ifBlank { "—" }}"

private fun List<TechniqueCheckRow>.updatedAt(index: Int, change: (TechniqueCheckRow) -> TechniqueCheckRow): List<TechniqueCheckRow> =
    mapIndexed { i, row -> if (i == index) change(row) else row }

/** Botón «‹ Serie N» de la cabecera: píldora `inset` de 32 dp con la flecha de volver; la zona táctil mide 48 dp. */
@Composable
private fun BackToSetPill(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Volver a la serie" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                KpknLineIcons.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.text,
            )
            Text(label, style = KpknType.chip, color = WizardColors.text, maxLines = 1)
        }
    }
}

/** Atajo de la foto del récord: texto en el acento de la sesión sobre una píldora `inset`, sin ripple. */
@Composable
private fun PrMediaShortcut(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * La cadena de la serie (A2) en horizontal: «Principal» (la carga y las reps de la tarjeta) y un eslabón por drop o por pausa, y «+»
 * para añadir. Solo el eslabón activo va con el filete del acento; los hechos llevan una marca. Se desplaza si no cabe y sus orillas se
 * funden con una máscara, sin borde recto.
 */
@Composable
private fun <T> TechniqueChain(
    mainLabel: String,
    rows: List<T>,
    linkTitle: (Int) -> String,
    linkValue: (T) -> String,
    linkSub: (T) -> String,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    canAdd: Boolean,
    onAdd: () -> Unit,
    addDescription: String,
    accent: Color,
) {
    val scroll = rememberScrollState()
    // Solo al AÑADIR un eslabón la cadena se desplaza hasta él; al entrar se queda al principio, con «Principal» a la vista.
    var knownSize by remember { mutableIntStateOf(rows.size) }
    LaunchedEffect(rows.size) {
        if (rows.size > knownSize) scroll.animateScrollTo(scroll.maxValue)
        knownSize = rows.size
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "CADENA DE LA SERIE",
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag(TechniqueChainTag)
                    .fadeHorizontalWhenScrollable(scroll, fade = 24.dp)
                    .horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChainLink(title = "Principal", value = mainLabel.substringBefore(" × "), sub = "× " + mainLabel.substringAfter(" × "), active = false, done = false, onClick = null)
                rows.forEachIndexed { index, row ->
                    ChainArrow()
                    ChainLink(
                        title = linkTitle(index),
                        value = linkValue(row),
                        sub = linkSub(row),
                        active = index == activeIndex,
                        done = (row as? TechniqueCheckRow)?.done == true,
                        accent = accent,
                        onClick = { onSelect(index) },
                    )
                }
                if (canAdd) {
                    AddLinkButton(description = addDescription, onClick = onAdd)
                }
            }
            if (scroll.canScrollForward) {
                Icon(KpknLineIcons.ChevronRight, contentDescription = "Más eslabones",
                    modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
            }
        }
    }
}

@Composable
private fun ChainArrow() {
    Icon(
        KpknLineIcons.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = WizardColors.text.copy(alpha = 0.35f),
    )
}

@Composable
private fun ChainLink(
    title: String,
    value: String,
    sub: String,
    active: Boolean,
    done: Boolean,
    onClick: (() -> Unit)?,
    accent: Color = WizardColors.text,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .testTag(TechniqueLinkTag)
            .width(ChainLinkWidth)
            .height(ChainLinkHeight)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.10f) else KpknSurface.inset, shape)
            .then(if (active) Modifier.border(1.dp, accent, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.plainClickable(onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (done) "$title ✓" else title,
            style = KpknType.navLabel.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = if (active) accent else WizardColors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = KpknType.metricM.copy(fontSize = 15.sp, lineHeight = 18.sp),
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sub,
            style = KpknType.navLabel.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «+» de la cadena: píldora de filete discontinuo del prototipo, aquí con el filete al 24 %; su zona táctil mide 48 dp de alto. */
@Composable
private fun AddLinkButton(description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = ChainLinkHeight)
            .clip(shape)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), shape)
            .plainClickable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            KpknLineIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = WizardColors.text,
        )
    }
}

/**
 * El eslabón abierto de la cadena (A2): su título, el chip «Sugerido» si la app calcula la carga del drop y la marca «Hecho» (que registra la
 * técnica cuando todos los eslabones la tienen) en una fila, y debajo las dos cifras con −/+ (kg y reps del drop; reps y segundos de la pausa).
 */
@Composable
private fun OpenTechniqueRow(
    title: String,
    suggestion: String?,
    suggestionSelected: Boolean,
    onSuggestion: (() -> Unit)?,
    firstValue: String,
    firstUnit: String,
    firstKeyboard: KeyboardType,
    onFirstChange: (String) -> Unit,
    onFirstStep: (Int) -> Unit,
    secondValue: String,
    secondUnit: String,
    onSecondChange: (String) -> Unit,
    onSecondStep: (Int) -> Unit,
    done: Boolean,
    onDoneChange: (Boolean) -> Unit,
    doneDescription: String,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KpknSurface.canvas, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = KpknType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp, end = 6.dp),
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (suggestion != null && onSuggestion != null) {
                    // Con la letra grande «Sugerido 90 kg» no cabe junto al título y a la marca: pasa a «90 kg» (la descripción accesible sigue completa).
                    var shortSuggestion by remember(suggestion) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .defaultMinSize(minHeight = 32.dp)
                            .clip(KpknShape.pill)
                            .border(
                                1.dp,
                                if (suggestionSelected) WizardColors.text else Color.White.copy(alpha = 0.12f),
                                KpknShape.pill,
                            )
                            .plainClickable(onSuggestion)
                            .semantics { contentDescription = suggestion }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (shortSuggestion) suggestion.removePrefix("Sugerido ") else suggestion,
                            style = KpknType.chip,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout -> if (!shortSuggestion && layout.hasVisualOverflow) shortSuggestion = true },
                        )
                    }
                }
                TechniqueDoneToggle(done = done, onToggle = { onDoneChange(!done) }, description = doneDescription)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepperCell(
                value = firstValue,
                unit = firstUnit,
                keyboardType = firstKeyboard,
                onValueChange = onFirstChange,
                onStep = onFirstStep,
                minusDescription = "Menos $firstUnit",
                plusDescription = "Más $firstUnit",
                modifier = Modifier.weight(1f),
            )
            StepperCell(
                value = secondValue,
                unit = secondUnit,
                keyboardType = KeyboardType.Number,
                onValueChange = onSecondChange,
                onStep = onSecondStep,
                minusDescription = "Menos $secondUnit",
                plusDescription = "Más $secondUnit",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Cifra editable con −/+ a los lados (A2): celda `inset` de 44 dp, botones de 32 dp, la cifra centrada y su unidad debajo. */
@Composable
private fun StepperCell(
    value: String,
    unit: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    minusDescription: String,
    plusDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(StepCellHeight)
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(symbol = "−", description = minusDescription, onClick = { onStep(-1) })
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = KpknType.chip.copy(
                    fontSize = if (value.length >= 5) 12.sp else 15.sp,
                    lineHeight = 18.sp,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "'tnum' 1",
                ),
                cursorBrush = SolidColor(WizardColors.text),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = unit,
                style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = WizardColors.textFaint,
                maxLines = 1,
            )
        }
        StepButton(symbol = "+", description = plusDescription, onClick = { onStep(1) })
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(StepButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f), CircleShape)
            .plainClickable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = KpknType.bodyStrong, color = WizardColors.text)
    }
}

/** Un bloque de la explicación: «95 × 6», una pausa «15 s» o el siguiente («95 × —») con el filete del acento. */
private data class TechniqueBlock(val text: String, val ghost: Boolean, val isPause: Boolean = false)

/**
 * «ASÍ SE VE UN REST-PAUSE» (A2): la técnica elegida en una línea de bloques con los números de la serie (la principal, las pausas y las
 * mini-series, o la principal y los drops). Se desplaza si no cabe.
 */
@Composable
private fun TechniqueExplanation(title: String, blocks: List<TechniqueBlock>, accent: Color) {
    val scroll = rememberScrollState()
    Column(Modifier.testTag("technique-explanation"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = KpknType.eyebrow,
            color = WizardColors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadeHorizontalWhenScrollable(scroll)
                .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                if (block.isPause) {
                    Text(block.text, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                } else {
                    val last = index == blocks.lastIndex
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(KpknShape.pill)
                            .then(
                                if (last) {
                                    Modifier.border(1.dp, accent, KpknShape.pill)
                                } else {
                                    Modifier.background(KpknSurface.inset, KpknShape.pill)
                                },
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(block.text, style = KpknType.chip, color = WizardColors.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Casilla «Hecho» (LR1c2, LV8): tinta cuando está marcada y `inset` con filete de 1 dp al 24 % mientras no, para que se vea sobre el
 * eslabón. Conserva la etiqueta de prueba y la descripción. Su zona táctil mide 48 dp.
 */
@Composable
private fun TechniqueDoneToggle(
    done: Boolean,
    onToggle: () -> Unit,
    description: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    // La zona táctil es la caja de 48 dp (la que lleva la etiqueta de prueba); el círculo de 28 dp es solo dibujo.
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(TechniqueDoneToggleTag)
            .toggleable(
                value = done,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(shape)
                .background(if (done) WizardColors.text else KpknSurface.inset, shape)
                .border(1.dp, Color.White.copy(alpha = 0.24f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
    }
}

/**
 * Píldora de texto tocable sin ripple de Material: zona táctil de 48 dp y píldora de 40 dp como mínimo, con el rótulo en `chip` (hasta
 * dos líneas antes que cortarse). Sirve para el «Error de ejecución».
 */
@Composable
private fun TechniquePill(
    text: String,
    onClick: () -> Unit,
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    edge: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .then(if (edge != null) Modifier.border(1.dp, edge, KpknShape.pill) else Modifier)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun commit(
    dropRows: List<TechniqueCheckRow>,
    restPauseRows: List<TechniqueCheckRow>,
    dropEnabled: Boolean,
    restPauseEnabled: Boolean,
    onCompleteTechniques: (List<DropSetData>, List<RestPauseData>) -> Unit,
): Boolean {
    val drops = if (dropEnabled) {
        dropRows.mapNotNull { row ->
            // El teclado decimal escribe la coma: se lee como punto antes de convertir (LR0).
            val weight = row.weightText.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (weight <= 0.0 || reps <= 0) null else DropSetData(weight, reps)
        }
    } else {
        emptyList()
    }
    val pauses = if (restPauseEnabled) {
        restPauseRows.mapNotNull { row ->
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (reps <= 0) null else RestPauseData(
                restTime = row.restText.toIntOrNull()?.coerceAtLeast(0) ?: RestPausePlanDefaults.PauseSeconds,
                reps = reps,
            )
        }
    } else {
        emptyList()
    }
    if (drops.isEmpty() && pauses.isEmpty()) return false
    onCompleteTechniques(drops, pauses)
    return true
}

/**
 * Peso del drop que se añade en [nextIndex]: la sugerencia del plan o, si el plan no llega (4.º y 5.º drop), el último peso menos un
 * paso. El último peso lo escribe la persona con el teclado decimal, así que «62,5» cuenta como 62,5 (LR1c2).
 */
internal fun nextDropWeightKg(
    mainWeight: Double,
    mainReps: Int,
    nextIndex: Int,
    lastWeightText: String?,
    loadMode: com.example.kpkn.data.models.LoadModeV2 = com.example.kpkn.data.models.LoadModeV2.LOAD,
): Double? {
    val suggested = suggestedDropLoadsForMainSet(
        mainWeight,
        mainReps,
        (nextIndex + 1).coerceAtMost(DropSetPlanDefaults.MaxDrops),
        loadMode,
    ).getOrNull(nextIndex)
    return suggested ?: lastWeightText?.replace(',', '.')?.toDoubleOrNull()?.let { com.example.kpkn.domain.workout.nextDropInputLoad(it, DropSetPlanDefaults.DropKg, loadMode) }
}

/** Mismo alto visual de 30 dp, con objetivo táctil de 48 dp y etiquetas completas desplazables. */
@Composable
private fun MyoTechniqueSegments(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(selectedIndex,scroll.maxValue) { scroll.scrollTo(if(selectedIndex == options.lastIndex) scroll.maxValue else 0) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index,label ->
            Box(Modifier.widthIn(min=if(label == "Rest-pause") 100.dp else 92.dp).height(48.dp)
                .semantics { selected = selectedIndex == index }.clickable(role=Role.Tab) { onSelect(index) },contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp))
                    .background(if(selectedIndex == index) WizardColors.text else Color.Black).padding(horizontal=8.dp),contentAlignment=Alignment.Center) {
                    Text(label,style=KpknType.caption.copy(fontSize=12.sp,lineHeight=16.sp),maxLines=1,softWrap=false,
                        color=if(selectedIndex == index) KpknSurface.canvas else WizardColors.textMuted)
                }
            }
        }
    }
}
